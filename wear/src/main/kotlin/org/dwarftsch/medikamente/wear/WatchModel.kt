package org.dwarftsch.medikamente.wear

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Das Dashboard, wie es die Handy-App liefert. */
data class Dashboard(
    val todayTotal: Int,
    val weekTotal: Int,
    val eintraege: List<WatchEntry>,
) {
    /**
     * Schnell-Anlegen-Vorschläge: die zuletzt verwendeten Medikamentennamen,
     * dedupliziert in der Reihenfolge der Einträge (neueste zuerst).
     */
    val vorschlaege: List<String>
        get() {
            val gesehen = LinkedHashSet<String>()
            eintraege.forEach { gesehen.add(it.medikament) }
            return gesehen.toList()
        }
}

/**
 * Zustand der Uhr-Oberfläche: lädt das Dashboard von der Handy-App und legt
 * Einträge über sie an. Der letzte Stand wird lokal gespiegelt, damit die App
 * auch ohne erreichbares Handy sofort etwas anzeigen kann.
 */
class WatchModel(context: Context) {

    private val phone = PhoneConnection(context)
    private val cache =
        context.applicationContext.getSharedPreferences("wear_cache", Context.MODE_PRIVATE)

    var laedt by mutableStateOf(false)
        private set

    /** Fehlermeldung, wenn das Handy nicht erreichbar war o. Ä. */
    var fehler by mutableStateOf<String?>(null)
        private set

    var dashboard by mutableStateOf<Dashboard?>(null)
        private set

    /** Kurzbestätigung nach dem Anlegen („Ibuprofen ✓“). */
    var bestaetigung by mutableStateOf<String?>(null)
        private set

    /** Name des Medikaments, das gerade angelegt wird (deaktiviert die Kachel). */
    var legtAn by mutableStateOf<String?>(null)
        private set

    init {
        // Letzten bekannten Stand sofort zeigen; frische Daten kommen gleich.
        cache.getString(CACHE_KEY, null)?.let { gespeichert ->
            runCatching { JSONObject(gespeichert) }.getOrNull()?.let { json ->
                dashboard = alsDashboard(json)
            }
        }
    }

    fun aktualisieren() {
        if (laedt) return
        laedt = true
        fehler = null
        phone.request(
            action = "getDashboard",
            arguments = null,
            onSuccess = { daten ->
                uebernehmen(daten)
                laedt = false
            },
            onError = { meldung ->
                laedt = false
                // Cache-Stand stehen lassen; nur den Fehler einblenden.
                fehler = meldung
            },
        )
    }

    /** Legt [medikament] mit „jetzt“ an und übernimmt das frische Dashboard. */
    fun anlegen(medikament: String) {
        if (legtAn != null) return
        legtAn = medikament
        fehler = null
        phone.request(
            action = "createEntry",
            arguments = JSONObject().put("medikament", medikament),
            onSuccess = { daten ->
                uebernehmen(daten)
                legtAn = null
                bestaetigung = medikament
            },
            onError = { meldung ->
                legtAn = null
                fehler = meldung
            },
        )
    }

    fun bestaetigungGesehen() {
        bestaetigung = null
    }

    fun schliessen() = Unit

    private fun uebernehmen(daten: JSONObject) {
        dashboard = alsDashboard(daten)
        cache.edit().putString(CACHE_KEY, daten.toString()).apply()
    }

    private fun alsDashboard(json: JSONObject) = Dashboard(
        todayTotal = json.optInt("today_total", 0),
        weekTotal = json.optInt("week_total", 0),
        eintraege = WatchEntry.listeAusJson(json.optJSONArray("entries")),
    )

    private companion object {
        const val CACHE_KEY = "last_dashboard"
    }
}
