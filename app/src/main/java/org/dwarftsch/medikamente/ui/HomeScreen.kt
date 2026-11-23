package org.dwarftsch.medikamente.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.PeriodStats
import org.dwarftsch.medikamente.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Welcher Dialog gerade offen ist. */
private sealed interface DialogZustand {
    data object ZeitWahl : DialogZustand
    data class Loeschen(val eintrag: MedEntry) : DialogZustand
    data object LetztenLoeschen : DialogZustand
}

/** Zahlen in Listen: Tabellenziffern (Guide Abschnitt 3). */
private val Tabellenziffern = MhTypo.small.copy(fontFeatureSettings = "tnum")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onEinstellungen: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DialogZustand?>(null) }
    var medikamentText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.meldungen.collect { snackbar.showSnackbar(it) }
    }

    fun anlegen() {
        val name = medikamentText.trim()
        if (name.isEmpty()) {
            viewModel.hinweis("Bitte ein Medikament eingeben")
            return
        }
        viewModel.anlegen(name, beiErfolg = { medikamentText = "" })
    }

    Scaffold(
        topBar = {
            MhKopf(
                // Umbruch nur nach dem Bindestrich (große Schrift).
                titel = "Medikamenten-\u200BTracker",
                symbole = {
                    MhSymbolButton(R.drawable.ic_mh_refresh, "Aktualisieren", viewModel::aktualisieren)
                    MhSymbolButton(R.drawable.ic_mh_sliders, "Einstellungen", onEinstellungen)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Mh.farben.bg,
    ) { innenAbstand ->
        Box(modifier = Modifier.padding(innenAbstand).fillMaxSize()) {
            when {
                state.laedt && state.eintraege.isEmpty() && state.fehler == null -> MhLaden()

                state.fehler != null ->
                    FehlerAnsicht(
                        meldung = state.fehler.orEmpty(),
                        onErneut = viewModel::aktualisieren,
                    )

                else -> PullToRefreshBox(
                    isRefreshing = state.laedt,
                    onRefresh = viewModel::aktualisieren,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Inhalt(
                        state = state,
                        medikamentText = medikamentText,
                        onTextAendern = { medikamentText = it.take(200) },
                        onAnlegen = ::anlegen,
                        onZeitWaehlen = { dialog = DialogZustand.ZeitWahl },
                        onZeitZuruecksetzen = { viewModel.setzeEigeneZeit(null) },
                        onLetztenRueckgaengig = { dialog = DialogZustand.LetztenLoeschen },
                        onLoeschen = { dialog = DialogZustand.Loeschen(it) },
                    )
                }
            }
        }
    }

    // --- Dialoge -------------------------------------------------------------

    when (val aktuell = dialog) {
        null -> Unit

        DialogZustand.ZeitWahl -> ZeitpunktDialog(
            initial = state.eigeneZeit,
            onAbbrechen = { dialog = null },
            onUebernehmen = { zeit ->
                dialog = null
                viewModel.setzeEigeneZeit(zeit)
            },
        )

        is DialogZustand.Loeschen -> LoeschDialog(
            titel = "Eintrag löschen?",
            text = "„${aktuell.eintrag.medikament}“ wird entfernt.",
            onAbbrechen = { dialog = null },
            onLoeschen = {
                dialog = null
                viewModel.loeschen(aktuell.eintrag)
            },
        )

        DialogZustand.LetztenLoeschen -> LoeschDialog(
            titel = "Letzten Eintrag löschen?",
            text = "Der zuletzt angelegte Eintrag wird entfernt.",
            onAbbrechen = { dialog = null },
            onLoeschen = {
                dialog = null
                viewModel.letztenRueckgaengig()
            },
        )
    }
}

// --- Inhalt ------------------------------------------------------------------

@Composable
private fun Inhalt(
    state: HomeUiState,
    medikamentText: String,
    onTextAendern: (String) -> Unit,
    onAnlegen: () -> Unit,
    onZeitWaehlen: () -> Unit,
    onZeitZuruecksetzen: () -> Unit,
    onLetztenRueckgaengig: () -> Unit,
    onLoeschen: (MedEntry) -> Unit,
) {
    val f = Mh.farben
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Mh.rand, end = Mh.rand, top = Mh.abstand1, bottom = Mh.abstand8),
    ) {
        // Offline-Hinweis über allem: der Nutzer soll sofort sehen, dass er
        // zwar weiterarbeiten kann, der Stand aber noch nicht beim Server ist.
        if (state.offlineGrund != null || state.ausstehend > 0) {
            item(key = "offline") {
                OfflineHinweis(grund = state.offlineGrund, ausstehend = state.ausstehend)
                Spacer(Modifier.height(Mh.abstand5))
            }
        }
        item(key = "eingabe") {
            Eingabe(
                text = medikamentText,
                eigeneZeit = state.eigeneZeit,
                speichert = state.speichert,
                onTextAendern = onTextAendern,
                onAnlegen = onAnlegen,
                onZeitWaehlen = onZeitWaehlen,
                onZeitZuruecksetzen = onZeitZuruecksetzen,
            )
        }

        state.stats?.let { stats ->
            item(key = "zuletzt") {
                Spacer(Modifier.height(Mh.abstand6))
                LetzterEintrag(stats.last, onRueckgaengig = onLetztenRueckgaengig)
            }
            item(key = "stats") {
                Spacer(Modifier.height(Mh.abstand8))
                Abschnittstitel("Statistik")
                Spacer(Modifier.height(Mh.abstand2))
                PeriodenZeile("Heute", stats.today)
                MhTrennlinie()
                PeriodenZeile("Letzte 7 Tage", stats.week)
                MhTrennlinie()
                PeriodenZeile("Letzte 3 Wochen", stats.threeWeeks)
                MhTrennlinie()
                PeriodenZeile("Letzte 30 Tage", stats.month)
            }
        }

        item(key = "eintraege-titel") {
            Spacer(Modifier.height(Mh.abstand8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Abschnittstitel("Einträge", Modifier.weight(1f))
                val anzahl = state.eintraege.size
                if (anzahl > 0) {
                    Text(
                        if (anzahl == 1) "1 Eintrag" else "$anzahl Einträge",
                        style = Tabellenziffern,
                        color = f.textSecondary,
                    )
                }
            }
        }

        if (state.eintraege.isEmpty()) {
            item(key = "leer") { MhLeer("Noch keine Einträge") }
        } else {
            var letzterTag: String? = null
            for (eintrag in state.eintraege) {
                val tag = eintrag.time?.let(::tagesUeberschrift) ?: "Ohne Datum"
                if (tag != letzterTag) {
                    letzterTag = tag
                    item(key = "tag-$tag") { MhGruppenLabel(tag) }
                }
                item(key = "eintrag-${eintrag.id}") {
                    MhTrennlinie()
                    EintragsZeile(
                        eintrag = eintrag,
                        ausstehend = eintrag.id != null && eintrag.id in state.ausstehendeIds,
                        onLoeschen = { onLoeschen(eintrag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Abschnittstitel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MhTypo.h2,
        color = Mh.farben.textStrong,
        modifier = modifier.semantics { heading() },
    )
}

// --- Eingabe ---------------------------------------------------------------------

/**
 * Neuer Eintrag wie im Web: Feld „Medikament“, Uhrzeit als Segmentschalter
 * (Jetzt | Andere Zeit) und „Anlegen“ als einzige Hauptaktion der Ansicht.
 */
@Composable
private fun Eingabe(
    text: String,
    eigeneZeit: LocalDateTime?,
    speichert: Boolean,
    onTextAendern: (String) -> Unit,
    onAnlegen: () -> Unit,
    onZeitWaehlen: () -> Unit,
    onZeitZuruecksetzen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Mh.abstand5)) {
        MhFeld("Medikament") {
            OutlinedTextField(
                value = text,
                onValueChange = onTextAendern,
                placeholder = { Text("z. B. Ibuprofen 400 mg", style = MhTypo.body) },
                singleLine = true,
                textStyle = MhTypo.body,
                colors = mhFeldFarben(),
                shape = MhFeldForm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MhFeld(
            "Uhrzeit",
            hinweis = eigeneZeit?.let {
                val zeitpunkt = it.atZone(ZoneId.systemDefault()).toInstant()
                "Gewählt: ${tagesLabel(zeitpunkt)}, ${hhmm(zeitpunkt)}\u00A0Uhr"
            },
        ) {
            // Ein Tipp auf „Andere Zeit“ öffnet die Auswahl – auch erneut,
            // wenn schon eine eigene Zeit gewählt ist.
            MhSegmente(
                optionen = listOf("Jetzt", "Andere Zeit"),
                gewaehlt = if (eigeneZeit == null) 0 else 1,
                onWahl = { if (it == 0) onZeitZuruecksetzen() else onZeitWaehlen() },
            )
        }
        MhHauptButton(
            if (speichert) "Wird angelegt …" else "Anlegen",
            onClick = onAnlegen,
            enabled = !speichert,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --- Letzter Eintrag ---------------------------------------------------------------

/** Letzter Eintrag zwischen zwei Haarlinien, „Rückgängig“ als leise Löschaktion. */
@Composable
private fun LetzterEintrag(last: MedEntry?, onRueckgaengig: () -> Unit) {
    val f = Mh.farben
    Column(Modifier.fillMaxWidth()) {
        MhTrennlinie()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Mh.abstand4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Letzter Eintrag", style = MhTypo.label, color = f.textSecondary)
                Spacer(Modifier.height(Mh.abstand1))
                if (last == null) {
                    Text("Noch kein Eintrag", style = MhTypo.body, color = f.textSecondary)
                } else {
                    Text(
                        last.medikament,
                        style = MhTypo.body.copy(fontWeight = FontWeight.Medium),
                        color = f.textStrong,
                    )
                    last.time?.let { zeit ->
                        Text(
                            "${tagesLabel(zeit)}, ${hhmm(zeit)}\u00A0Uhr · ${relativ(zeit)}",
                            style = Tabellenziffern,
                            color = f.textSecondary,
                        )
                    }
                }
            }
            if (last != null) {
                // Text bündig mit dem rechten Rand wie im Web.
                MhLoeschenButton(
                    "Rückgängig",
                    onClick = onRueckgaengig,
                    symbol = R.drawable.ic_mh_undo,
                    modifier = Modifier.offset(x = Mh.abstand3),
                )
            }
        }
        MhTrennlinie()
    }
}

// --- Statistik ---------------------------------------------------------------------

/**
 * Ein Zeitraum als Listenzeile: Titel und Aufschlüsselung je Medikament
 * links, Gesamtzahl groß und dünn rechts.
 */
@Composable
private fun PeriodenZeile(titel: String, periode: PeriodStats) {
    val f = Mh.farben
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = Mh.abstand3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MhTypo.body.copy(fontWeight = FontWeight.Medium), color = f.textStrong)
            Text(
                if (periode.medikamente.isEmpty()) {
                    "Keine Einträge"
                } else {
                    // Zahl bleibt beim Namen, der Punkt am Zeilenende.
                    periode.medikamente.joinToString("\u00A0· ") { "${it.medikament}\u00A0${it.anzahl}×" }
                },
                style = Tabellenziffern,
                color = f.textSecondary,
            )
        }
        Spacer(Modifier.width(Mh.abstand4))
        Text("${periode.total}", style = MhTypo.number, color = f.textStrong)
    }
}

// --- Eintragsliste -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EintragsZeile(
    eintrag: MedEntry,
    /** Der Eintrag wartet noch auf die Übertragung zum Server. */
    ausstehend: Boolean,
    onLoeschen: () -> Unit,
) {
    val f = Mh.farben
    // Wischen nach links fragt – wie in der Flutter-App – erst per Dialog nach;
    // die Zeile springt deshalb immer zurück (confirmValueChange = false).
    val wischZustand = rememberSwipeToDismissBoxState(
        confirmValueChange = { wert ->
            if (wert == SwipeToDismissBoxValue.EndToStart) onLoeschen()
            false
        },
    )
    SwipeToDismissBox(
        state = wischZustand,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(f.errorBg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                MhSymbol(
                    R.drawable.ic_mh_trash,
                    null,
                    farbe = f.danger,
                    modifier = Modifier.padding(end = Mh.abstand5),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(f.bg)
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Mh.abstand3),
        ) {
            Text(
                eintrag.time?.let { "${hhmm(it)}\u00A0Uhr" } ?: "–",
                style = Tabellenziffern,
                color = f.textSecondary,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            Text(
                eintrag.medikament,
                style = MhTypo.body,
                color = f.textStrong,
                modifier = Modifier.weight(1f).padding(vertical = Mh.abstand2),
            )
            if (ausstehend) {
                MhSymbol(
                    R.drawable.ic_mh_clock,
                    "Wartet auf Übertragung",
                    groesse = Mh.symbolKlein,
                    farbe = f.warningFg,
                )
            }
            // Text bündig mit dem rechten Rand wie im Web.
            MhLoeschenButton(
                "Löschen",
                onClick = onLoeschen,
                modifier = Modifier.offset(x = Mh.abstand3),
            )
        }
    }
}

// --- Fehleransicht --------------------------------------------------------------

@Composable
private fun FehlerAnsicht(meldung: String, onErneut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Mh.abstand8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MhSymbol(R.drawable.ic_mh_alert, null, groesse = 32.dp, farbe = Mh.farben.danger)
        Spacer(Modifier.height(Mh.abstand4))
        Text(meldung, style = MhTypo.body, color = Mh.farben.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Mh.abstand6))
        MhHauptButton("Erneut versuchen", onClick = onErneut, symbol = R.drawable.ic_mh_refresh)
    }
}

// --- Datums-Helfer ---------------------------------------------------------------

private val Wochentag: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy", Locale.GERMAN)

internal fun hhmm(zeit: Instant): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(zeit)

internal fun tagesLabel(zeit: Instant): String {
    val heute = LocalDate.now()
    val tag = zeit.atZone(ZoneId.systemDefault()).toLocalDate()
    return when (heute.toEpochDay() - tag.toEpochDay()) {
        0L -> "Heute"
        1L -> "Gestern"
        else -> tag.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }
}

/** Überschrift einer Tagesgruppe: „Heute“, „Gestern“ oder „Sa., 21.11.2026“. */
private fun tagesUeberschrift(zeit: Instant): String {
    val heute = LocalDate.now()
    val tag = zeit.atZone(ZoneId.systemDefault()).toLocalDate()
    return when (heute.toEpochDay() - tag.toEpochDay()) {
        0L -> "Heute"
        1L -> "Gestern"
        else -> tag.format(Wochentag)
    }
}

/** „vor 5 Min.“, „vor 2 Std.“, „vor 3 Tagen“ – wie im Web. */
internal fun relativ(zeit: Instant): String {
    val differenz = Duration.between(zeit, Instant.now())
    return when {
        differenz.toMinutes() < 1 -> "gerade eben"
        differenz.toMinutes() < 60 -> "vor ${differenz.toMinutes()} Min."
        differenz.toHours() < 24 -> "vor ${differenz.toHours()} Std."
        differenz.toDays() == 1L -> "vor 1 Tag"
        else -> "vor ${differenz.toDays()} Tagen"
    }
}

/**
 * Hinweis über dem Inhalt: Verbindung weg, App weiter benutzbar.
 *
 * [grund] null bedeutet „wieder online, aber es wartet noch etwas auf die
 * Übertragung“.
 */
@Composable
private fun OfflineHinweis(grund: String?, ausstehend: Int) {
    val titel = if (grund == null) "Übertragung läuft" else "Offline-Modus – $grund"
    val text = when (ausstehend) {
        0 -> "Angezeigt wird der zuletzt geladene Stand."
        1 ->
            "Eine Änderung wartet auf die Übertragung und geht raus, " +
                "sobald die Verbindung steht."
        else ->
            "$ausstehend Änderungen warten auf die Übertragung und gehen raus, " +
                "sobald die Verbindung steht."
    }
    MhHinweis(
        text = text,
        titel = titel,
        art = MhHinweisArt.Warnung,
        symbol = if (grund == null) R.drawable.ic_mh_upload else R.drawable.ic_mh_cloud_off,
    )
}
