package org.dwarftsch.medikamente.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dwarftsch.medikamente.MedCount
import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.MedStats
import org.dwarftsch.medikamente.PeriodStats
import org.dwarftsch.medikamente.parseIsoZeit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Eine Roh-Zeile der Tabelle `entries` (für Backup-Export/-Restore). */
data class EntryRow(
    val id: Long,
    val medikament: String,
    val time: String,
)

/**
 * Lokaler Modus: speichert Einträge in derselben SQLite-Datenbank, die schon
 * die Flutter-App (sqflite) verwendet hat – gleicher Dateiname, gleiches
 * Schema, gleiche Version. Bestehende Daten werden dadurch beim Umstieg auf
 * die native App nahtlos übernommen.
 */
class DemoService(context: Context) : MedService {

    private val helper = Helper(context.applicationContext)

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE entries(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  medikament TEXT NOT NULL,
                  time TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        // Version 1 ist die einzige – die Flutter-App hat nie migriert.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    override suspend fun getStats(): MedStats = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val last = db.query("entries", null, null, null, null, null, "id DESC", "1").use { cursor ->
            if (cursor.moveToNext()) eintragAusCursor(cursor) else null
        }
        MedStats(
            today = periode(db, tagesbeginn()),
            week = periode(db, tagesbeginn(tageZurueck = 7)),
            threeWeeks = periode(db, tagesbeginn(tageZurueck = 21)),
            month = periode(db, tagesbeginn(tageZurueck = 30)),
            last = last,
        )
    }

    /** Statistik für alle Einträge ab [seit]: Gesamtzahl + Zählung je Medikament. */
    private fun periode(db: SQLiteDatabase, seit: Instant): PeriodStats =
        db.query(
            "entries", arrayOf("medikament"),
            "time >= ?", arrayOf(zuDb(seit)),
            null, null, null,
        ).use { cursor ->
            var total = 0
            val zaehler = linkedMapOf<String, Int>()
            while (cursor.moveToNext()) {
                total++
                val name = cursor.getString(0)
                zaehler[name] = (zaehler[name] ?: 0) + 1
            }
            val medikamente = zaehler.entries
                .map { MedCount(medikament = it.key, anzahl = it.value) }
                .sortedWith(compareByDescending<MedCount> { it.anzahl }.thenBy { it.medikament })
            PeriodStats(total = total, medikamente = medikamente)
        }

    override suspend fun getEntries(limit: Int?): List<MedEntry> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            "entries", null, null, null, null, null,
            "time DESC, id DESC", limit?.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(eintragAusCursor(cursor))
            }
        }
    }

    override suspend fun addEntry(medikament: String, time: Instant?): MedEntry =
        withContext(Dispatchers.IO) {
            val zeit = time ?: Instant.now()
            val werte = ContentValues().apply {
                put("medikament", medikament)
                put("time", zuDb(zeit))
            }
            val id = helper.writableDatabase.insertOrThrow("entries", null, werte)
            MedEntry(id = id, medikament = medikament, time = zeit)
        }

    override suspend fun deleteEntry(id: Long): Boolean = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("entries", "id = ?", arrayOf(id.toString())) > 0
    }

    override suspend fun undoLast(): Boolean = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val letzteId = db.query("entries", arrayOf("id"), null, null, null, null, "id DESC", "1")
            .use { cursor -> if (cursor.moveToNext()) cursor.getLong(0) else null }
            ?: return@withContext false
        db.delete("entries", "id = ?", arrayOf(letzteId.toString())) > 0
    }

    override fun dispose() {
        // Der SQLiteOpenHelper cached die Verbindung prozessweit; bewusst
        // offen lassen (UI und Backup teilen sich die DB).
    }

    /** Alle Roh-Zeilen der lokalen Tabelle (für den Backup-Export). */
    suspend fun exportRows(): List<EntryRow> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query("entries", null, null, null, null, null, "id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        EntryRow(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            medikament = cursor.getString(cursor.getColumnIndexOrThrow("medikament")),
                            time = cursor.getString(cursor.getColumnIndexOrThrow("time")),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Ersetzt den gesamten lokalen Bestand durch [rows] (Backup-Restore).
     * Löschen und Einfügen laufen in einer Transaktion, damit bei einem Fehler
     * der bisherige Stand erhalten bleibt.
     */
    suspend fun replaceAll(rows: List<EntryRow>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("entries", null, null)
            for (row in rows) {
                val werte = ContentValues().apply {
                    put("id", row.id)
                    put("medikament", row.medikament)
                    put("time", row.time)
                }
                db.insertOrThrow("entries", null, werte)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        /** Muss zum sqflite-Bestand der Flutter-App passen. */
        const val DB_NAME = "medikamenten_demo.db"
        const val DB_VERSION = 1

        /**
         * Zeitpunkte werden – wie von der Flutter-App – als ISO 8601 in UTC
         * inklusive Sekundenbruchteilen gespeichert, damit die lexikalische
         * Sortierung der Strings der zeitlichen entspricht.
         */
        val DB_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        fun zuDb(zeit: Instant): String = DB_FORMAT.format(zeit)

        /** Heutiger Tagesbeginn (lokale Zeit), optional um Tage zurückversetzt. */
        fun tagesbeginn(tageZurueck: Long = 0): Instant =
            LocalDate.now().minusDays(tageZurueck).atStartOfDay(ZoneId.systemDefault()).toInstant()

        fun eintragAusCursor(cursor: android.database.Cursor): MedEntry = MedEntry(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            medikament = cursor.getString(cursor.getColumnIndexOrThrow("medikament")),
            time = parseIsoZeit(cursor.getString(cursor.getColumnIndexOrThrow("time"))),
        )
    }
}
