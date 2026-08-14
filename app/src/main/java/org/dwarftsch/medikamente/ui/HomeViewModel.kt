package org.dwarftsch.medikamente.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.MedStats
import org.dwarftsch.medikamente.data.AppSettings
import org.dwarftsch.medikamente.data.CertSource
import org.dwarftsch.medikamente.data.MedService
import org.dwarftsch.medikamente.data.createConfiguredMedService
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

    /** Aktive Datenquelle: API (mTLS/API-Key) oder lokale SQLite-DB. */
    private var service: MedService? = null

    init {
        datenquelleNeuAufbauen()
    }

    override fun onCleared() {
        service?.dispose()
        super.onCleared()
    }

    /**
     * Baut die Datenquelle anhand der Einstellung neu auf (z. B. nach dem
     * Verlassen der Einstellungen) und lädt anschließend neu.
     */
    fun datenquelleNeuAufbauen() {
        service?.dispose()
        service = createConfiguredMedService(getApplication(), settings, certSource)
        aktualisieren()
    }

    fun aktualisieren() {
        val aktiverService = service ?: return
        state.value = state.value.copy(laedt = true, fehler = null)
        viewModelScope.launch {
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
}

/** Lesbare Meldung einer Exception (ApiException liefert Statuscode mit). */
internal fun Throwable.meldung(): String = when (this) {
    is org.dwarftsch.medikamente.data.ApiException -> toString()
    else -> message ?: toString()
}
