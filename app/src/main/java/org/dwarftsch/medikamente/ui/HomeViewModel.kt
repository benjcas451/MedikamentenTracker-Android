package org.dwarftsch.medikamente.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.MedStats
import org.dwarftsch.medikamente.data.AppSettings
import org.dwarftsch.medikamente.data.CertSource
import org.dwarftsch.medikamente.data.MedService
import org.dwarftsch.medikamente.data.OfflineService
import org.dwarftsch.medikamente.data.Verbindungswache
import org.dwarftsch.medikamente.data.createConfiguredMedService
import org.dwarftsch.medikamente.data.meldung
import org.dwarftsch.medikamente.wear.WatchChangeBus
import java.time.LocalDateTime
import java.time.ZoneId

data class HomeUiState(
    val laedt: Boolean = true,
    val fehler: String? = null,
    val stats: MedStats? = null,
    val eintraege: List<MedEntry> = emptyList(),
    /** Für „Andere Zeit“ gewählter Zeitpunkt; null = „Jetzt“. */
    val eigeneZeit: LocalDateTime? = null,
    /** Während ein neuer Eintrag gespeichert wird. */
    val speichert: Boolean = false,
    /** Grund der abgebrochenen Verbindung; null heisst „online“. */
    val offlineGrund: String? = null,
    /** Anzahl der Schreibzugriffe, die noch auf Übertragung warten. */
    val ausstehend: Int = 0,
    /** IDs, deren Stand noch nicht beim Server ist – die Liste markiert sie. */
    val ausstehendeIds: Set<Long> = emptySet(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val settings = AppSettings(application)
    val certSource = CertSource(application, settings)

    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state

    private val meldungenFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Snackbar-Meldungen (Fehler etc.). */
    val meldungen: SharedFlow<String> = meldungenFlow

    /** Zeigt einen Hinweis über denselben Snackbar-Kanal wie Fehler. */
    fun hinweis(text: String) {
        meldungenFlow.tryEmit(text)
    }

    /** Aktive Datenquelle: API (mTLS/API-Key/Cloudflare) oder lokale SQLite-DB. */
    private var service: MedService? = null

    /** Beobachtet den Zustand der aktuellen Offline-Hülle; null im Demo. */
    private var zustandBeobachter: Job? = null

    private val wache = Verbindungswache(application)

    init {
        // Schreibzugriffe der Uhr lösen ein Neuladen aus.
        viewModelScope.launch {
            WatchChangeBus.aenderungen.drop(1).collect { aktualisieren() }
        }
        // Sobald wieder ein Netz da ist, die Warteschlange abarbeiten – ohne
        // dass der Nutzer etwas antippen muss.
        wache.starten()
        viewModelScope.launch {
            wache.wiederVerbunden.collect { aktualisieren() }
        }
        datenquelleNeuAufbauen()
    }

    override fun onCleared() {
        wache.beenden()
        service?.dispose()
        super.onCleared()
    }

    /**
     * Baut die Datenquelle anhand der Einstellung neu auf (z. B. nach dem
     * Verlassen der Einstellungen) und lädt anschließend neu.
     */
    fun datenquelleNeuAufbauen() {
        service?.dispose()
        zustandBeobachter?.cancel()
        val neu = createConfiguredMedService(
            getApplication(), settings, certSource, offlineFaehig = true,
        )
        service = neu
        // Der Offline-Hinweis des alten Zugangs darf nicht über dem neuen
        // stehen bleiben.
        state.value = state.value.copy(
            offlineGrund = null,
            ausstehend = 0,
            ausstehendeIds = emptySet(),
        )
        if (neu is OfflineService) {
            zustandBeobachter = viewModelScope.launch {
                neu.zustand.collect { zustand ->
                    state.value = state.value.copy(
                        offlineGrund = zustand.grund,
                        ausstehend = zustand.ausstehend,
                        ausstehendeIds = zustand.ausstehendeIds,
                    )
                }
            }
        }
        aktualisieren()
    }

    fun aktualisieren() {
        val aktiverService = service ?: return
        state.value = state.value.copy(laedt = true, fehler = null)
        viewModelScope.launch {
            // Erst das Liegengebliebene loswerden, dann laden: sonst zeigte die
            // Liste einen Serverstand ohne die eigenen Einträge.
            warteschlangeAbarbeiten(aktiverService)
            runCatching {
                coroutineScope {
                    val stats = async { aktiverService.getStats() }
                    val eintraege = async { aktiverService.getEntries(limit = 100) }
                    stats.await() to eintraege.await()
                }
            }.fold(
                onSuccess = { (stats, eintraege) ->
                    state.value = state.value.copy(
                        laedt = false,
                        stats = stats,
                        eintraege = eintraege,
                    )
                },
                onFailure = { fehler ->
                    state.value = state.value.copy(laedt = false, fehler = fehler.meldung())
                },
            )
        }
    }

    /** Wählt den Zeitpunkt der Eingabe („Andere Zeit“); null = „Jetzt“. */
    fun setzeEigeneZeit(zeit: LocalDateTime?) {
        state.value = state.value.copy(eigeneZeit = zeit)
    }

    /**
     * Legt einen Eintrag an. [beiErfolg] läuft nach erfolgreichem Speichern
     * (z. B. Eingabefeld leeren), bevor neu geladen wird.
     */
    fun anlegen(medikament: String, beiErfolg: () -> Unit = {}) {
        val aktiverService = service ?: return
        val zeit = state.value.eigeneZeit?.atZone(ZoneId.systemDefault())?.toInstant()
        state.value = state.value.copy(speichert = true)
        viewModelScope.launch {
            runCatching { aktiverService.addEntry(medikament, time = zeit) }.fold(
                onSuccess = {
                    beiErfolg()
                    meldungenFlow.tryEmit("„$medikament“ gespeichert")
                    state.value = state.value.copy(speichert = false, eigeneZeit = null)
                    aktualisieren()
                },
                onFailure = {
                    state.value = state.value.copy(speichert = false)
                    meldungenFlow.tryEmit("Fehler: ${it.meldung()}")
                },
            )
        }
    }

    fun loeschen(eintrag: MedEntry) {
        val id = eintrag.id ?: return
        fuehreAktionAus(meldung = "Eintrag gelöscht") { it.deleteEntry(id) }
    }

    fun letztenRueckgaengig() {
        fuehreAktionAus(meldung = null) {
            val entfernt = it.undoLast()
            meldungenFlow.tryEmit(if (entfernt) "Letzter Eintrag gelöscht" else "Kein Eintrag vorhanden")
        }
    }

    /** Führt eine schreibende Aktion aus und lädt danach neu. */
    private fun fuehreAktionAus(meldung: String?, aktion: suspend (MedService) -> Any?) {
        val aktiverService = service ?: return
        viewModelScope.launch {
            runCatching { aktion(aktiverService) }.fold(
                onSuccess = {
                    if (meldung != null) meldungenFlow.tryEmit(meldung)
                    aktualisieren()
                },
                onFailure = { meldungenFlow.tryEmit("Fehler: ${it.meldung()}") },
            )
        }
    }

    /**
     * Schickt die offenen Schreibzugriffe zum Server. Verworfene Aktionen
     * (vom Server inhaltlich zurückgewiesene) meldet sie einmal gesammelt.
     */
    private suspend fun warteschlangeAbarbeiten(dienst: MedService) {
        if (dienst !is OfflineService) return
        val verworfen = dienst.nachholen()
        if (verworfen.isEmpty()) return
        meldungenFlow.tryEmit(
            if (verworfen.size == 1) {
                "Eine wartende Änderung wurde vom Server abgelehnt: ${verworfen.first()}"
            } else {
                "${verworfen.size} wartende Änderungen wurden vom Server abgelehnt."
            },
        )
    }
}
