package org.dwarftsch.medikamente.wear

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.data.AppSettings
import org.dwarftsch.medikamente.data.CertSource
import org.dwarftsch.medikamente.data.MedService
import org.dwarftsch.medikamente.data.createConfiguredMedService
import org.dwarftsch.medikamente.parseIsoZeit
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter

/**
 * Nimmt RPC-Anfragen der Wear-OS-App entgegen (`MessageClient.sendRequest`)
 * und führt sie direkt gegen die konfigurierte Datenquelle aus. Die Uhr hat
 * bewusst keine eigene Datenquelle — Lesen und Schreiben laufen immer über
 * die Handy-App (wie bei der Apple-Watch-Variante).
 *
 * Protokoll (JSON, UTF-8, gleiche Hülle wie bei Stillzeit):
 *   Anfrage: {"action": "...", "arguments": { ... }}
 *   Antwort: {"ok": true, "data": { ... }} bzw. {"ok": false, "error": "..."}
 *
 * Aktionen:
 *   getDashboard          -> {today_total, week_total, entries: [...]}
 *   createEntry {medikament, time?} -> Dashboard nach dem Anlegen
 *   undoLast              -> Dashboard nach dem Löschen (+ removed: true/false)
 */
class WearRequestService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onRequest(nodeId: String, path: String, data: ByteArray): Task<ByteArray>? {
        if (path != REQUEST_PATH) return null

        val antwort = TaskCompletionSource<ByteArray>()
        val anfrage = runCatching { JSONObject(String(data, Charsets.UTF_8)) }.getOrNull()
        val action = anfrage?.optString("action").orEmpty()
        if (action.isEmpty()) {
            antwort.setResult(fehler("Ungültige Anfrage der Uhr."))
            return antwort.task
        }
        val argumente = anfrage?.optJSONObject("arguments") ?: JSONObject()

        scope.launch {
            val ergebnis = runCatching { fuehreAus(action, argumente) }
            antwort.setResult(
                ergebnis.fold(
                    onSuccess = ::erfolg,
                    onFailure = { fehler(it.message ?: "Unbekannter Fehler.") },
                ),
            )
        }
        return antwort.task
    }

    private suspend fun fuehreAus(action: String, argumente: JSONObject): JSONObject =
        when (action) {
            "getDashboard" -> mitService { service -> dashboard(service) }

            "createEntry" -> mitService { service ->
                val medikament = argumente.optString("medikament").trim()
                if (medikament.isEmpty()) {
                    throw IllegalArgumentException("Medikament fehlt.")
                }
                service.addEntry(
                    medikament = medikament,
                    time = argumente.optString("time").takeIf { it.isNotEmpty() }
                        ?.let { runCatching { parseIsoZeit(it) }.getOrNull() },
                )
                WatchChangeBus.melden()
                dashboard(service)
            }

            "undoLast" -> mitService { service ->
                val entfernt = service.undoLast()
                WatchChangeBus.melden()
                dashboard(service).put("removed", entfernt)
            }

            else -> throw IllegalArgumentException("Unbekannte Watch-Anfrage: $action")
        }

    private suspend fun <T> mitService(aktion: suspend (MedService) -> T): T {
        val settings = AppSettings(this)
        val certSource = CertSource(this, settings)
        val service = createConfiguredMedService(this, settings, certSource)
        return try {
            aktion(service)
        } finally {
            service.dispose()
        }
    }

    private suspend fun dashboard(service: MedService): JSONObject {
        val stats = service.getStats()
        val eintraege = service.getEntries(limit = 12)
        return JSONObject()
            .put("today_total", stats.today.total)
            .put("week_total", stats.week.total)
            .put("entries", JSONArray().apply { eintraege.forEach { put(alsJson(it)) } })
    }

    private fun alsJson(entry: MedEntry): JSONObject = JSONObject().apply {
        entry.id?.let { put("id", it) }
        put("medikament", entry.medikament)
        // java.time auf der Uhr erwartet eine explizite Zeitzone; UTC mit "Z".
        entry.time?.let { put("time", DateTimeFormatter.ISO_INSTANT.format(it)) }
    }

    private fun erfolg(daten: JSONObject): ByteArray =
        JSONObject()
            .put("ok", true)
            .put("data", daten)
            .toString()
            .toByteArray(Charsets.UTF_8)

    private fun fehler(meldung: String): ByteArray =
        JSONObject()
            .put("ok", false)
            .put("error", meldung)
            .toString()
            .toByteArray(Charsets.UTF_8)

    private companion object {
        /** Muss zu `PhoneConnection.REQUEST_PATH` im :wear-Modul passen. */
        const val REQUEST_PATH = "/medikamente/request"
    }
}
