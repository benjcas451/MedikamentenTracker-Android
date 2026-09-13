package org.dwarftsch.medikamente.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.dwarftsch.medikamente.MedCount
import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.MedStats
import org.dwarftsch.medikamente.PeriodStats
import org.dwarftsch.medikamente.parseIsoZeit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ein Schreibzugriff, der offline erfasst wurde und noch zum Server muss.
 *
 * Löschungen beziehen sich immer auf eine **Server-ID**. Trifft eine Löschung
 * einen Eintrag, der selbst noch in der Warteschlange steht, wird dessen
 * [Anlegen] ersatzlos entfernt — beim Abarbeiten kann also keine noch
 * unbekannte ID auftauchen.
 */
sealed interface Warteaktion {

    /** Ein offline erfasster neuer Eintrag. */
    data class Anlegen(
        /**
         * Negative Kennung, unter der der Eintrag in der Liste auftaucht,
         * solange er nicht hochgeladen ist.
         */
        val lokaleId: Long,
        val medikament: String,
        /**
         * Zeitpunkt der Erfassung, nicht des Hochladens – sonst bekäme der
         * Eintrag beim Nachholen die falsche Uhrzeit.
         */
        val time: Instant,
    ) : Warteaktion

    /** Eine offline erfasste Löschung eines bereits hochgeladenen Eintrags. */
    data class Loeschen(val id: Long) : Warteaktion
}

/** Der Offline-Zustand, den die Oberfläche anzeigt. */
data class OfflineZustand(
    /** Grund der letzten gescheiterten Verbindung; null heisst „online“. */
    val grund: String? = null,
    /** Anzahl der Schreibzugriffe, die noch auf Übertragung warten. */
    val ausstehend: Int = 0,
    /** IDs, deren Stand noch nicht beim Server ist (lokale sind negativ). */
    val ausstehendeIds: Set<Long> = emptySet(),
)

/**
 * Legt Warteschlange und Lesestand je Zugang im app-privaten Verzeichnis ab.
 *
 * Der Schlüssel ist Modus plus Basis-URL: Wer zwischen zwei Servern wechselt,
 * bekommt nicht die Einträge des anderen zu sehen und lädt auch keine
 * Warteschlange dorthin hoch, wo sie nicht hingehört. Einträge und Statistik
 * liegen in getrennten Dateien, weil die Oberfläche beide nebenläufig lädt.
 */
class OfflineSpeicher(context: Context, zugang: String) {

    private val schluessel = zugang.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    private val ordner = File(context.applicationContext.filesDir, "offline").apply { mkdirs() }

    private fun datei(name: String) = File(ordner, "${name}_$schluessel.json")

    fun ladeWarteschlange(): Pair<List<Warteaktion>, Long> {
        val json = lies(datei("warteschlange")) ?: return emptyList<Warteaktion>() to -1L
        val liste = json.optJSONArray("aktionen") ?: JSONArray()
        val aktionen = (0 until liste.length()).mapNotNull { i ->
            val o = liste.optJSONObject(i) ?: return@mapNotNull null
            when (o.optString("art")) {
                "anlegen" -> Warteaktion.Anlegen(
                    lokaleId = o.optLong("lokale_id"),
                    medikament = o.optString("medikament"),
                    time = parseIsoZeit(o.optString("time")),
                )

                "loeschen" -> Warteaktion.Loeschen(o.optLong("id"))
                else -> null
            }
        }
        val naechste = if (json.has("naechste_lokale_id")) json.optLong("naechste_lokale_id") else -1L
        return aktionen to naechste
    }

    fun speichere(aktionen: List<Warteaktion>, naechsteLokaleId: Long) {
        val liste = JSONArray()
        for (aktion in aktionen) {
            val o = JSONObject()
            when (aktion) {
                is Warteaktion.Anlegen -> o
                    .put("art", "anlegen")
                    .put("lokale_id", aktion.lokaleId)
                    .put("medikament", aktion.medikament)
                    .put("time", aktion.time.toString())

                is Warteaktion.Loeschen -> o.put("art", "loeschen").put("id", aktion.id)
            }
            liste.put(o)
        }
        schreibe(
            datei("warteschlange"),
            JSONObject().put("naechste_lokale_id", naechsteLokaleId).put("aktionen", liste),
        )
    }

    fun ladeEintraege(): List<MedEntry>? {
        val json = lies(datei("eintraege")) ?: return null
        val liste = json.optJSONArray("eintraege") ?: return null
        return (0 until liste.length()).mapNotNull { eintragAusJson(liste.optJSONObject(it)) }
    }

    fun speichereEintraege(eintraege: List<MedEntry>) {
        val liste = JSONArray()
        for (e in eintraege) liste.put(eintragAlsJson(e))
        schreibe(datei("eintraege"), JSONObject().put("eintraege", liste))
    }

    fun ladeStats(): MedStats? {
        val json = lies(datei("stats")) ?: return null
        return MedStats(
            today = periode(json.optJSONObject("today")),
            week = periode(json.optJSONObject("week")),
            threeWeeks = periode(json.optJSONObject("three_weeks")),
            month = periode(json.optJSONObject("month")),
            last = eintragAusJson(json.optJSONObject("last")),
        )
    }

    fun speichereStats(stats: MedStats) {
        schreibe(
            datei("stats"),
            JSONObject()
                .put("today", periodeAlsJson(stats.today))
                .put("week", periodeAlsJson(stats.week))
                .put("three_weeks", periodeAlsJson(stats.threeWeeks))
                .put("month", periodeAlsJson(stats.month))
                .apply { stats.last?.let { put("last", eintragAlsJson(it)) } },
        )
    }

    private fun eintragAusJson(json: JSONObject?): MedEntry? {
        if (json == null) return null
        if (!json.has("medikament")) return null
        return MedEntry(
            id = if (json.isNull("id")) null else json.optLong("id"),
            medikament = json.optString("medikament"),
            time = json.optString("time").takeIf { it.isNotEmpty() }?.let { parseIsoZeit(it) },
        )
    }

    private fun eintragAlsJson(eintrag: MedEntry): JSONObject = JSONObject()
        .put("medikament", eintrag.medikament)
        .apply {
            eintrag.id?.let { put("id", it) }
            eintrag.time?.let { put("time", it.toString()) }
        }

    private fun periode(json: JSONObject?): PeriodStats {
        if (json == null) return PeriodStats.LEER
        val medikamente = json.optJSONArray("medikamente") ?: JSONArray()
        return PeriodStats(
            total = json.optInt("total"),
            medikamente = (0 until medikamente.length()).mapNotNull { i ->
                val o = medikamente.optJSONObject(i) ?: return@mapNotNull null
                MedCount(o.optString("medikament"), o.optInt("anzahl"))
            },
        )
    }

    private fun periodeAlsJson(werte: PeriodStats): JSONObject {
        val liste = JSONArray()
        for (m in werte.medikamente) {
            liste.put(JSONObject().put("medikament", m.medikament).put("anzahl", m.anzahl))
        }
        return JSONObject().put("total", werte.total).put("medikamente", liste)
    }

    private fun lies(datei: File): JSONObject? =
        runCatching { JSONObject(datei.readText()) }.getOrNull()

    /**
     * Erst in eine Nebendatei, dann umbenennen: ein Absturz mitten im
     * Schreiben hinterlässt sonst eine halbe Datei, und die Warteschlange wäre
     * verloren.
     */
    private fun schreibe(datei: File, json: JSONObject) {
        runCatching {
            val temp = File(datei.parentFile, "${datei.name}.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(datei)) {
                datei.writeText(json.toString())
                temp.delete()
            }
        }
    }
}

/**
 * Legt sich über die Server-Quelle und hält die App bei einem
 * Verbindungsabbruch benutzbar.
 *
 * Lesen: Bei jedem Netzwerkfehler wird der zuletzt erfolgreiche Stand
 * gezeigt — dabei ist gleich, ob die Anfrage ankam, denn ein Lesevorgang
 * verändert nichts.
 *
 * Schreiben: In die Warteschlange darf eine Aktion **nur**, wenn sie den
 * Server nachweislich nie erreicht hat ([Netzfehler.NIE_GESENDET]). Bei einer
 * Zeitüberschreitung oder einem Abbruch mitten in der Übertragung könnte der
 * Server sie bereits ausgeführt haben; ein zweiter Versuch legte dann einen
 * zweiten Eintrag an.
 */
class OfflineService(
    private val innen: MedService,
    private val speicher: OfflineSpeicher,
) : MedService {

    private val sperre = Mutex()
    private val warteschlange: MutableList<Warteaktion>
    private var naechsteLokaleId: Long

    init {
        val (aktionen, naechste) = speicher.ladeWarteschlange()
        warteschlange = aktionen.toMutableList()
        naechsteLokaleId = naechste
    }

    private val zustandFlow = MutableStateFlow(
        OfflineZustand(ausstehend = warteschlange.size, ausstehendeIds = ids(warteschlange)),
    )

    /** Der Zustand für die Oberfläche. */
    val zustand: StateFlow<OfflineZustand> = zustandFlow

    override fun dispose() = innen.dispose()

    // ── Lesen ───────────────────────────────────────────────────────────────

    override suspend fun getStats(): MedStats = try {
        val vomServer = innen.getStats()
        speicher.speichereStats(vomServer)
        melde(null)
        sperre.withLock { anwenden(vomServer, warteschlange) }
    } catch (fehler: Throwable) {
        val stand = speicher.ladeStats()
        if (Netzfehler.aus(fehler) == null || stand == null) throw fehler
        melde(fehler.meldung())
        sperre.withLock { anwenden(stand, warteschlange) }
    }

    override suspend fun getEntries(limit: Int?): List<MedEntry> = try {
        val vomServer = innen.getEntries(limit)
        speicher.speichereEintraege(vomServer)
        melde(null)
        sperre.withLock { begrenze(anwenden(vomServer, warteschlange), limit) }
    } catch (fehler: Throwable) {
        val stand = speicher.ladeEintraege()
        if (Netzfehler.aus(fehler) == null || stand == null) throw fehler
        melde(fehler.meldung())
        sperre.withLock { begrenze(anwenden(stand, warteschlange), limit) }
    }

    // ── Schreiben ───────────────────────────────────────────────────────────

    override suspend fun addEntry(medikament: String, time: Instant?): MedEntry {
        val zeit = time ?: Instant.now()
        // Reihenfolge wahren: Steht schon etwas an, gehört auch das Neue
        // hinten dran, statt es am Stau vorbeizuschicken.
        if (!istLeer()) return reiheEin(medikament, zeit)
        return try {
            innen.addEntry(medikament, time).also { melde(null) }
        } catch (fehler: Throwable) {
            if (Netzfehler.aus(fehler) != Netzfehler.NIE_GESENDET) throw fehler
            melde(fehler.meldung())
            reiheEin(medikament, zeit)
        }
    }

    override suspend fun deleteEntry(id: Long): Boolean {
        // Negative IDs kennt nur die App: der Eintrag wartet noch. Und solange
        // etwas ansteht, bleibt die Reihenfolge gewahrt. Beides meldet true –
        // für die Oberfläche ist der Eintrag weg.
        if (id < 0 || !istLeer()) {
            loescheVorgemerkt(id)
            return true
        }
        return try {
            innen.deleteEntry(id).also { melde(null) }
        } catch (fehler: Throwable) {
            if (Netzfehler.aus(fehler) != Netzfehler.NIE_GESENDET) throw fehler
            melde(fehler.meldung())
            loescheVorgemerkt(id)
            true
        }
    }

    override suspend fun undoLast(): Boolean {
        // Wartet noch ein Eintrag, ist das der zuletzt erfasste – den nimmt
        // die App direkt zurück, ohne den Server zu behelligen.
        val zurueckgenommen = sperre.withLock {
            val index = warteschlange.indexOfLast { it is Warteaktion.Anlegen }
            if (index < 0) {
                false
            } else {
                warteschlange.removeAt(index)
                true
            }
        }
        if (zurueckgenommen) {
            sichern()
            return true
        }
        // Sonst muss der Server ran. Offline lässt sich das **nicht**
        // vormerken: Die API kennt für undoLast keine ID, beim Nachholen
        // träfe es womöglich einen Eintrag, den jemand anders inzwischen
        // angelegt hat.
        return innen.undoLast()
    }

    // ── Nachholen ───────────────────────────────────────────────────────────

    /**
     * Arbeitet die Warteschlange von vorn ab.
     *
     * Bricht beim ersten Verbindungsfehler ab — der Rest bleibt in der
     * Reihenfolge stehen. Weist der Server eine Aktion inhaltlich zurück,
     * fliegt sie raus und wird gemeldet; sonst blockierte sie die
     * Warteschlange für immer.
     *
     * Liefert die Meldungen zu verworfenen Aktionen.
     */
    suspend fun nachholen(): List<String> {
        val verworfen = mutableListOf<String>()
        while (true) {
            val naechste = sperre.withLock { warteschlange.firstOrNull() } ?: break
            try {
                when (naechste) {
                    is Warteaktion.Anlegen -> innen.addEntry(naechste.medikament, naechste.time)
                    is Warteaktion.Loeschen -> innen.deleteEntry(naechste.id)
                }
                entferneErste()
            } catch (fehler: Throwable) {
                if (Netzfehler.aus(fehler) != null) {
                    melde(fehler.meldung())
                    return verworfen
                }
                entferneErste()
                verworfen.add(fehler.meldung())
            }
        }
        melde(null)
        return verworfen
    }

    // ── Innere Hilfen ───────────────────────────────────────────────────────

    private suspend fun istLeer(): Boolean = sperre.withLock { warteschlange.isEmpty() }

    private suspend fun reiheEin(medikament: String, time: Instant): MedEntry {
        val id = sperre.withLock {
            val neu = naechsteLokaleId
            naechsteLokaleId -= 1
            warteschlange.add(Warteaktion.Anlegen(neu, medikament, time))
            neu
        }
        sichern()
        return MedEntry(id, medikament, time)
    }

    private suspend fun loescheVorgemerkt(id: Long) {
        sperre.withLock {
            val index = warteschlange.indexOfFirst {
                it is Warteaktion.Anlegen && it.lokaleId == id
            }
            if (index >= 0) {
                warteschlange.removeAt(index)
            } else {
                warteschlange.add(Warteaktion.Loeschen(id))
            }
        }
        sichern()
    }

    private suspend fun entferneErste() {
        sperre.withLock { if (warteschlange.isNotEmpty()) warteschlange.removeAt(0) }
        sichern()
    }

    private suspend fun sichern() {
        val (stand, naechste) = sperre.withLock { warteschlange.toList() to naechsteLokaleId }
        speicher.speichere(stand, naechste)
        zustandFlow.value = zustandFlow.value.copy(
            ausstehend = stand.size,
            ausstehendeIds = ids(stand),
        )
    }

    private fun melde(grund: String?) {
        if (zustandFlow.value.grund != grund) {
            zustandFlow.value = zustandFlow.value.copy(grund = grund)
        }
    }

    private fun ids(aktionen: List<Warteaktion>): Set<Long> = aktionen.mapTo(mutableSetOf()) {
        when (it) {
            is Warteaktion.Anlegen -> it.lokaleId
            is Warteaktion.Loeschen -> it.id
        }
    }

    private fun begrenze(eintraege: List<MedEntry>, limit: Int?): List<MedEntry> =
        if (limit == null || eintraege.size <= limit) eintraege else eintraege.take(limit)

    /**
     * Legt die offenen Aktionen über eine Liste vom Server, damit die
     * Oberfläche den Stand zeigt, den der Nutzer erwartet: neueste zuerst.
     */
    private fun anwenden(eintraege: List<MedEntry>, aktionen: List<Warteaktion>): List<MedEntry> {
        if (aktionen.isEmpty()) return eintraege
        val ergebnis = eintraege.toMutableList()
        for (aktion in aktionen) {
            when (aktion) {
                is Warteaktion.Anlegen ->
                    ergebnis.add(MedEntry(aktion.lokaleId, aktion.medikament, aktion.time))

                is Warteaktion.Loeschen -> ergebnis.removeAll { it.id == aktion.id }
            }
        }
        return ergebnis.sortedByDescending { it.time ?: Instant.EPOCH }
    }

    /**
     * Rechnet die offenen Aktionen in die Statistik ein, damit Kacheln und
     * Liste nicht auseinanderlaufen. Anders als beim Wickel-Tracker sind das
     * hier reine Zählungen – die lassen sich vollständig nachführen.
     */
    private fun anwenden(stats: MedStats, aktionen: List<Warteaktion>): MedStats {
        if (aktionen.isEmpty()) return stats
        val jetzt = Instant.now()
        val heute = jetzt.atZone(ZoneId.systemDefault()).toLocalDate()
        var werte = stats
        for (aktion in aktionen) {
            if (aktion !is Warteaktion.Anlegen) continue
            if (aktion.time.atZone(ZoneId.systemDefault()).toLocalDate() == heute) {
                werte = werte.copy(today = werte.today.zaehle(aktion.medikament))
            }
            if (aktion.time > jetzt.minus(Duration.ofDays(7))) {
                werte = werte.copy(week = werte.week.zaehle(aktion.medikament))
            }
            if (aktion.time > jetzt.minus(Duration.ofDays(21))) {
                werte = werte.copy(threeWeeks = werte.threeWeeks.zaehle(aktion.medikament))
            }
            if (aktion.time > jetzt.minus(Duration.ofDays(30))) {
                werte = werte.copy(month = werte.month.zaehle(aktion.medikament))
            }
        }
        // Der jüngste wartende Eintrag ist der letzte – sofern er nicht älter
        // ist als der, den der Server kennt.
        val neuester = aktionen.filterIsInstance<Warteaktion.Anlegen>().maxByOrNull { it.time }
        val bisher = werte.last?.time
        if (neuester != null && (bisher == null || neuester.time > bisher)) {
            werte = werte.copy(
                last = MedEntry(neuester.lokaleId, neuester.medikament, neuester.time),
            )
        }
        return werte
    }
}

/** Zählt einen wartenden Eintrag mit: Gesamtzahl plus Aufschlüsselung. */
private fun PeriodStats.zaehle(medikament: String): PeriodStats {
    val liste = medikamente.toMutableList()
    val index = liste.indexOfFirst { it.medikament == medikament }
    if (index >= 0) {
        liste[index] = liste[index].copy(anzahl = liste[index].anzahl + 1)
    } else {
        liste.add(MedCount(medikament, 1))
    }
    return copy(total = total + 1, medikamente = liste.sortedByDescending { it.anzahl })
}

/** Lesbare Meldung einer Exception (ApiException liefert den Statuscode mit). */
fun Throwable.meldung(): String = when (this) {
    is ApiException -> toString()
    else -> message?.takeIf { it.isNotBlank() } ?: toString()
}

/**
 * Meldet, sobald wieder ein Netzwerk da ist — damit die Warteschlange nicht
 * erst beim nächsten Antippen abgearbeitet wird.
 */
class Verbindungswache(context: Context) {

    private val wiederVerbundenFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Feuert bei jedem Wechsel von „kein Netz“ zu „Netz da“. */
    val wiederVerbunden: SharedFlow<Unit> = wiederVerbundenFlow

    private val manager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var warOffline = false

    private val rueckruf = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (warOffline) wiederVerbundenFlow.tryEmit(Unit)
            warOffline = false
        }

        override fun onLost(network: Network) {
            warOffline = true
        }
    }

    fun starten() {
        runCatching {
            manager?.registerNetworkCallback(NetworkRequest.Builder().build(), rueckruf)
        }
    }

    fun beenden() {
        runCatching { manager?.unregisterNetworkCallback(rueckruf) }
    }
}
