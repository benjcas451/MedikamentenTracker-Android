package org.dwarftsch.medikamente.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.dwarftsch.medikamente.MedCount
import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.MedStats
import org.dwarftsch.medikamente.PeriodStats
import org.dwarftsch.medikamente.parseIsoZeit
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Spricht die Medikamenten-Tracker-API an (`<baseUrl>api.php?action=...`).
 *
 * Authentifizierung:
 *  - mTLS-Client-Zertifikat über [certSource] (Transport-Ebene), und/oder
 *  - API-Key über den Header `X-API-Key` ([apiKey]).
 *
 * Der API-Key ist optional: manche Instanzen verlangen ihn, andere sichern
 * nur über mTLS ab. Endpunkte und JSON-Felder identisch zur Flutter-App.
 */
class ApiService(
    /** Quelle für client.crt/client.key; null bei reiner API-Key-Auth. */
    private val certSource: CertSource? = null,
    /** Basis-URL inkl. abschließendem Slash, z. B. `https://host/medikamenten-tracker/`. */
    private val baseUrl: String,
    /** Wird als `X-API-Key`-Header mitgesendet, falls gesetzt. */
    private val apiKey: String? = null,
) : MedService {

    private var client: OkHttpClient? = null

    private suspend fun httpClient(): OkHttpClient {
        client?.let { return it }
        val builder = OkHttpClient.Builder()
        val source = certSource
        if (source != null) {
            val (cert, key) = source.readCredentials()
            val (factory, trust) = ClientCertificates.socketFactoryMitTrust(cert, key)
            builder.sslSocketFactory(factory, trust)
        }
        return builder.build().also { client = it }
    }

    override fun dispose() {
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
    }

    private fun apiUrl(action: String, limit: Int? = null): HttpUrl {
        if (baseUrl.isBlank()) {
            throw ApiException(
                "Keine API-URL konfiguriert. Bitte in den Einstellungen die " +
                    "Basis-URL des Servers hinterlegen.",
            )
        }
        val root = "${baseUrl}api.php".toHttpUrlOrNull()
            ?: throw ApiException("Ungültige API-URL: $baseUrl")
        return root.newBuilder()
            .addQueryParameter("action", action)
            .apply { if (limit != null) addQueryParameter("limit", limit.toString()) }
            .build()
    }

    private suspend fun send(method: String, url: HttpUrl, body: JSONObject? = null): Any? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .apply {
                    if (!apiKey.isNullOrEmpty()) header("X-API-Key", apiKey)
                }
                .method(method, body?.let { anfrageKoerper(it) } ?: if (method == "POST") ByteArray(0).toRequestBody() else null)
                .build()

            httpClient().newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val ok = response.code in 200..299

                val decoded: Any? = if (text.isEmpty()) {
                    null
                } else {
                    runCatching { JSONObject(text) as Any }
                        .recoverCatching { JSONArray(text) as Any }
                        .getOrElse {
                            // Antwort ist kein JSON (z. B. HTML-Fehlerseite).
                            if (ok) return@use null
                            throw ApiException(
                                "Unerwartete Antwort (kein JSON): ${snippet(text)}",
                                statusCode = response.code,
                            )
                        }
                }

                if (ok) return@use decoded

                val meldung = (decoded as? JSONObject)?.optString("error")?.takeIf { it.isNotEmpty() }
                    ?: text.ifEmpty { "Anfrage fehlgeschlagen" }.let(::snippet)
                throw ApiException(meldung, statusCode = response.code)
            }
        }

    private fun anfrageKoerper(json: JSONObject): RequestBody =
        json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    override suspend fun getStats(): MedStats {
        val data = send("GET", apiUrl("stats")) as? JSONObject
            ?: throw ApiException("Unerwartete Antwort der API.")
        fun periode(key: String): PeriodStats {
            val json = data.optJSONObject(key) ?: return PeriodStats.LEER
            val liste = json.optJSONArray("medikamente") ?: JSONArray()
            return PeriodStats(
                total = json.optInt("total", 0),
                medikamente = (0 until liste.length()).map {
                    val m = liste.getJSONObject(it)
                    MedCount(
                        medikament = m.optString("medikament"),
                        anzahl = m.optInt("anzahl", 0),
                    )
                },
            )
        }
        val last = data.optJSONObject("last")
            ?.let(::eintragAusJson)
            ?.takeIf { it.medikament.isNotEmpty() }
        return MedStats(
            today = periode("today"),
            week = periode("week"),
            threeWeeks = periode("threeWeeks"),
            month = periode("month"),
            last = last,
        )
    }

    override suspend fun getEntries(limit: Int?): List<MedEntry> {
        val data = send("GET", apiUrl("list", limit)) as? JSONObject
            ?: throw ApiException("Unerwartete Antwort der API.")
        val liste = data.optJSONArray("entries") ?: JSONArray()
        return (0 until liste.length()).map { eintragAusJson(liste.getJSONObject(it)) }
    }

    override suspend fun addEntry(medikament: String, time: Instant?): MedEntry {
        val body = JSONObject().put("medikament", medikament)
        if (time != null) {
            body.put("time", isoUtc(time))
        }
        val data = send("POST", apiUrl("add"), body)
        return (data as? JSONObject)?.let(::eintragAusJson)
            ?: MedEntry(id = null, medikament = medikament, time = time)
    }

    override suspend fun deleteEntry(id: Long): Boolean {
        val data = send("POST", apiUrl("delete"), JSONObject().put("id", id))
        return (data as? JSONObject)?.optBoolean("ok") == true
    }

    override suspend fun undoLast(): Boolean {
        return try {
            val data = send("POST", apiUrl("undo_last"))
            (data as? JSONObject)?.optBoolean("ok") == true
        } catch (e: ApiException) {
            if (e.statusCode == 404) false else throw e // kein Eintrag vorhanden
        }
    }

    private companion object {
        fun eintragAusJson(json: JSONObject): MedEntry = MedEntry(
            id = if (json.isNull("id")) null else json.optLong("id"),
            medikament = json.optString("medikament"),
            time = json.stringOderNull("time")?.let(::parseIsoZeit),
        )

        /**
         * Formatiert einen Zeitpunkt als ISO 8601 in UTC – so sendet ihn auch
         * die Flutter-App (`toUtc().toIso8601String()`).
         */
        fun isoUtc(zeit: Instant): String =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneOffset.UTC)
                .format(zeit)

        /** Kürzt eine (Fehler-)Antwort für die Anzeige. */
        fun snippet(s: String): String {
            val clean = s.replace(Regex("\\s+"), " ").trim()
            return if (clean.length > 200) clean.take(200) + "…" else clean
        }

        fun JSONObject.stringOderNull(key: String): String? =
            if (isNull(key)) null else optString(key)
    }
}
