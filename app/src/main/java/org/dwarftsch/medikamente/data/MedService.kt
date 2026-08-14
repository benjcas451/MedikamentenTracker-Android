package org.dwarftsch.medikamente.data

import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.MedStats
import java.time.Instant

/** Fehler einer API-Anfrage (Statuscode + Meldung). */
class ApiException(message: String, val statusCode: Int? = null) : Exception(message) {
    override fun toString(): String =
        if (statusCode != null) "Fehler $statusCode: $message" else message.orEmpty()
}

/**
 * Gemeinsame Schnittstelle für Medikamenten-Quellen: die Server-API
 * ([ApiService], mTLS und/oder API-Key) oder die lokale SQLite-Datenbank
 * ([DemoService]).
 */
interface MedService {
    /** Vollständige Statistik (heute / Woche / 3 Wochen / Monat + letzter Eintrag). */
    suspend fun getStats(): MedStats

    /** Liste der Einträge, neueste zuerst (optional auf [limit] begrenzt). */
    suspend fun getEntries(limit: Int? = null): List<MedEntry>

    /** Neuen Eintrag anlegen ([time] optional, sonst „jetzt“). */
    suspend fun addEntry(medikament: String, time: Instant? = null): MedEntry

    /** Eintrag nach ID löschen. Liefert true, wenn etwas entfernt wurde. */
    suspend fun deleteEntry(id: Long): Boolean

    /**
     * Letzten Eintrag rückgängig machen.
     * Liefert true, wenn etwas entfernt wurde, false wenn es keinen gab.
     */
    suspend fun undoLast(): Boolean

    /** Gibt Ressourcen frei (HTTP-Client bzw. Datenbank-Handle). */
    fun dispose()
}
