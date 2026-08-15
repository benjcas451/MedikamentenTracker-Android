package org.dwarftsch.medikamente.wear

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Ein Eintrag, wie ihn die Handy-App an die Uhr liefert. */
data class WatchEntry(
    val id: Int,
    val medikament: String,
    val zeit: LocalDateTime,
) {
    companion object {
        fun listeAusJson(array: JSONArray?): List<WatchEntry> {
            if (array == null) return emptyList()
            val eintraege = ArrayList<WatchEntry>(array.length())
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                ausJson(json)?.let(eintraege::add)
            }
            return eintraege
        }

        private fun ausJson(json: JSONObject): WatchEntry? {
            val id = if (json.has("id")) json.optInt("id", -1) else -1
            if (id < 0) return null
            val medikament = json.optString("medikament").takeIf { it.isNotEmpty() } ?: return null
            val zeit = zeitAusText(json.optString("time")) ?: return null
            return WatchEntry(id = id, medikament = medikament, zeit = zeit)
        }

        /**
         * Die Handy-App schickt UTC-Zeitstempel mit "Z". Zusätzlich werden
         * Zeitstempel mit explizitem Offset akzeptiert.
         */
        private fun zeitAusText(text: String): LocalDateTime? {
            if (text.isEmpty()) return null
            return runCatching {
                OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime()
            }.recoverCatching {
                Instant.parse(text).atZone(ZoneId.systemDefault()).toLocalDateTime()
            }.getOrNull()
        }
    }
}
