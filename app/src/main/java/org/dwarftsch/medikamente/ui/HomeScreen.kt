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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.dwarftsch.medikamente.MedEntry
import org.dwarftsch.medikamente.PeriodStats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Welcher Dialog gerade offen ist. */
private sealed interface DialogZustand {
    data object ZeitWahl : DialogZustand
    data class Loeschen(val eintrag: MedEntry) : DialogZustand
    data object LetztenLoeschen : DialogZustand
}

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
            TopAppBar(
                title = { Text("💊 Medikamente") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = viewModel::aktualisieren) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                    IconButton(onClick = onEinstellungen) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbar) { daten -> Snackbar(daten) }
        },
    ) { innenAbstand ->
        Box(modifier = Modifier.padding(innenAbstand).fillMaxSize()) {
            when {
                state.laedt && state.eintraege.isEmpty() && state.fehler == null ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "eingabe") {
            EingabeKarte(
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
                Spacer(Modifier.height(16.dp))
                LetzterEintragKarte(stats.last)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLetztenRueckgaengig,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Letzten rückgängig")
                }
            }
            item(key = "stats") {
                Spacer(Modifier.height(16.dp))
                Text("Statistik", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                PeriodenKarte("Heute", stats.today, hervorgehoben = true)
                Spacer(Modifier.height(8.dp))
                PeriodenKarte("Letzte 7 Tage", stats.week)
                Spacer(Modifier.height(8.dp))
                PeriodenKarte("Letzte 3 Wochen", stats.threeWeeks)
                Spacer(Modifier.height(8.dp))
                PeriodenKarte("Letzte 30 Tage", stats.month)
            }
        }

        item(key = "eintraege-titel") {
            Spacer(Modifier.height(16.dp))
            Text(
                if (state.eintraege.isEmpty()) "Einträge" else "Einträge (${state.eintraege.size})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (state.eintraege.isEmpty()) {
            item(key = "leer") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                    Text(
                        "Noch keine Einträge vorhanden",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        } else {
            var letzterTag: String? = null
            for (eintrag in state.eintraege) {
                val tag = eintrag.time?.let(::tagesLabel) ?: "Ohne Datum"
                if (tag != letzterTag) {
                    letzterTag = tag
                    item(key = "tag-$tag") { TagesUeberschrift(tag) }
                }
                item(key = "eintrag-${eintrag.id}") {
                    EintragsKachel(
                        eintrag = eintrag,
                        onLoeschen = { onLoeschen(eintrag) },
                    )
                }
            }
        }
    }
}

// --- Eingabe-Karte -------------------------------------------------------------

@Composable
private fun EingabeKarte(
    text: String,
    eigeneZeit: LocalDateTime?,
    speichert: Boolean,
    onTextAendern: (String) -> Unit,
    onAnlegen: () -> Unit,
    onZeitWaehlen: () -> Unit,
    onZeitZuruecksetzen: () -> Unit,
) {
    // Karte: weiß (Dark: Grau-850) mit weichem Schatten, Radius 16 (Guide 6).
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Neuer Eintrag", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextAendern,
                shape = MaterialTheme.shapes.medium,
                colors = mhEingabefeldFarben(),
                label = { Text("Medikament") },
                placeholder = { Text("z. B. Ibuprofen 400mg") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            // Chips als Pills (radius-full); aktiv = Minze-300-Fläche mit 900er-Text.
            val chipFarben = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = eigeneZeit == null,
                    onClick = onZeitZuruecksetzen,
                    shape = RoundedCornerShape(50),
                    colors = chipFarben,
                    label = { Text("Jetzt") },
                )
                FilterChip(
                    selected = eigeneZeit != null,
                    onClick = onZeitWaehlen,
                    shape = RoundedCornerShape(50),
                    colors = chipFarben,
                    leadingIcon = if (eigeneZeit == null) {
                        { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    label = {
                        Text(
                            eigeneZeit?.let {
                                "${tagesLabel(it.atZone(ZoneId.systemDefault()).toInstant())} " +
                                    it.format(DateTimeFormatter.ofPattern("HH:mm"))
                            } ?: "Andere Zeit",
                        )
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAnlegen,
                enabled = !speichert,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                if (speichert) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Anlegen")
            }
        }
    }
}

// --- Letzter Eintrag -------------------------------------------------------------

@Composable
private fun LetzterEintragKarte(last: MedEntry?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar nach dem Hinweis-Muster: zarte 100er-Fläche,
            // Icon in der text-tauglichen 700er-Stufe (Dark: 300er).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(avatarFlaeche(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (last == null) Icons.Filled.History else Icons.Filled.Medication,
                    contentDescription = null,
                    tint = avatarInhalt(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                if (last == null) {
                    Text("Noch kein Eintrag", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Lege oben den ersten Eintrag an.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Zuletzt: ${last.medikament}", style = MaterialTheme.typography.bodyLarge)
                    last.time?.let { zeit ->
                        Text(
                            "${tagesLabel(zeit)} um ${hhmm(zeit)} · ${relativ(zeit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// --- Statistik ---------------------------------------------------------------------

@Composable
private fun PeriodenKarte(titel: String, periode: PeriodStats, hervorgehoben: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            // "Heute" hervorgehoben als zarte Minze-100-Fläche (Dark-Äquivalent).
            containerColor = if (hervorgehoben) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hervorgehoben) 0.dp else 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(titel, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${periode.total} gesamt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (periode.medikamente.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Keine Einträge",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                for (m in periode.medikamente) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (hervorgehoben) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                avatarInhalt()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(m.medikament, modifier = Modifier.weight(1f))
                        Text("${m.anzahl}×", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Eintragsliste -------------------------------------------------------------

@Composable
private fun TagesUeberschrift(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        // Auf Weiß braucht Grün die 700er-Stufe, der Pastellton 300 wäre
        // nicht lesbar (Guide 2.7).
        color = MinzeHonig.farben.sektionsTitel,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EintragsKachel(
    eintrag: MedEntry,
    onLoeschen: () -> Unit,
) {
    // Wischen nach links fragt – wie in der Flutter-App – erst per Dialog nach;
    // die Kachel springt deshalb immer zurück (confirmValueChange = false).
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
                    .padding(vertical = 4.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(avatarFlaeche(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Medication, contentDescription = null, tint = avatarInhalt())
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(eintrag.medikament, style = MaterialTheme.typography.bodyLarge)
                    eintrag.time?.let { zeit ->
                        Text(
                            "${hhmm(zeit)} Uhr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onLoeschen) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Eintrag löschen",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// --- Fehleransicht --------------------------------------------------------------

@Composable
private fun FehlerAnsicht(meldung: String, onErneut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(meldung, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onErneut, shape = MaterialTheme.shapes.medium) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Erneut versuchen")
        }
    }
}

// --- Datums-Helfer ---------------------------------------------------------------

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

/** „vor X min/h/d“ – wie in der Flutter-App. */
internal fun relativ(zeit: Instant): String {
    val differenz = Duration.between(zeit, Instant.now())
    return when {
        differenz.toMinutes() < 1 -> "gerade eben"
        differenz.toMinutes() < 60 -> "vor ${differenz.toMinutes()} min"
        differenz.toHours() < 24 -> "vor ${differenz.toHours()} h"
        else -> "vor ${differenz.toDays()} d"
    }
}
