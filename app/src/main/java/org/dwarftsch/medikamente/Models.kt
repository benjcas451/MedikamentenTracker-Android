package org.dwarftsch.medikamente

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Ein Medikamenten-Eintrag (Freitext-Name + Zeitpunkt). */
data class MedEntry(
    val id: Long?,
    val medikament: String,
    /** Null nur bei kaputten API-Antworten; lokale Einträge haben immer eine Zeit. */
    val time: Instant?,
)

/** Zählung eines Medikaments innerhalb eines Zeitraums. */
data class MedCount(
    val medikament: String,
    val anzahl: Int,
)

/**
 * Statistik eines Zeitraums: Gesamtzahl + Aufschlüsselung je Medikament
 * (so liefert es `GET api.php?action=stats`).
 */
data class PeriodStats(
    val total: Int,
    val medikamente: List<MedCount>,
) {
    companion object {
        val LEER = PeriodStats(total = 0, medikamente = emptyList())
    }
}

/**
 * Vollständige Statistik-Antwort (`action=stats`): Zeiträume heute / Woche /
 * 3 Wochen / Monat plus letzter Eintrag.
 */
data class MedStats(
    val today: PeriodStats,
    val week: PeriodStats,
    val threeWeeks: PeriodStats,
    val month: PeriodStats,
    val last: MedEntry?,
)

/**
 * Liest einen ISO-8601-Zeitstempel tolerant: mit Offset (`+02:00`), mit `Z`
 * (auch mit Dart-Mikrosekunden) oder – wie ihn ältere lokale Einträge
 * theoretisch enthalten könnten – ganz ohne Zeitzone (wird dann als lokale
 * Zeit interpretiert, wie in Dart).
 */
fun parseIsoZeit(text: String): Instant {
    runCatching { return Instant.parse(text) }
    runCatching { return OffsetDateTime.parse(text).toInstant() }
    return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant()
}
