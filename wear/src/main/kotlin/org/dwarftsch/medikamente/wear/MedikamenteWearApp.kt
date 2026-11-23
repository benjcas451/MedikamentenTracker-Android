package org.dwarftsch.medikamente.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Typography
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.delay
import org.dwarftsch.medikamente.wear.ui.Figtree
import org.dwarftsch.medikamente.wear.ui.MhDunkel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- Design-System „Minze & Honig“ 2.0 --------------------------------------
// Uhr: nur Farben und Schrift aus 2.0, Aufbau unverändert. Immer dunkel
// (Rollen aus MhDunkel), Figtree 400/500/600. Farbe ist Signal: die
// Schnell-Anlegen-Kacheln sind neutrale Flächen, Minze trägt nur die eine
// Hauptaktion („Erneut versuchen“), das Häkchen und die Ladeanzeige.

private val F = MhDunkel

private val MedikamenteFarben = Colors(
    primary = F.primary,
    primaryVariant = F.selectedBg,
    secondary = F.link,
    secondaryVariant = F.tint,
    background = F.bg,
    surface = F.surface,
    error = F.danger,
    onPrimary = F.onPrimary,
    onSecondary = F.onPrimary,
    onBackground = F.textStrong,
    onSurface = F.text,
    onSurfaceVariant = F.textSecondary,
    onError = F.onDanger,
)

/** Figtree; Knöpfe in 500 statt des Wear-Standards Bold. */
private val MedikamenteTypografie = Typography(defaultFontFamily = Figtree).let {
    it.copy(button = it.button.copy(fontWeight = FontWeight.Medium))
}

private val UhrzeitFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun tagesLabel(datum: LocalDate): String {
    val heute = LocalDate.now()
    return when (heute.toEpochDay() - datum.toEpochDay()) {
        0L -> "Heute"
        1L -> "Gestern"
        else -> datum.format(DateTimeFormatter.ofPattern("dd.MM."))
    }
}

// --- App ----------------------------------------------------------------------

@Composable
fun MedikamenteWearApp(model: WatchModel) {
    MaterialTheme(colors = MedikamenteFarben, typography = MedikamenteTypografie) {
        val listState = rememberScalingLazyListState()

        // Bestätigung nach dem Anlegen kurz einblenden.
        val bestaetigung = model.bestaetigung
        LaunchedEffect(bestaetigung) {
            if (bestaetigung != null) {
                delay(1600)
                model.bestaetigungGesehen()
            }
        }

        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            when {
                bestaetigung != null -> BestaetigungsAnsicht(bestaetigung)
                model.dashboard == null && model.laedt -> LadeAnsicht()
                model.dashboard == null -> FehlerAnsicht(
                    meldung = model.fehler ?: "Noch keine Daten.",
                    onErneut = model::aktualisieren,
                )
                else -> Inhalt(model, listState)
            }
        }
    }
}

@Composable
private fun Inhalt(
    model: WatchModel,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
) {
    val dashboard = model.dashboard ?: return
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Kopf: Heute- und 7-Tage-Zähler, Zahlen dünn in Textfarbe.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Heute", fontSize = 11.sp, color = F.textSecondary)
                    Text(
                        "${dashboard.todayTotal}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Normal,
                        color = F.textStrong,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("7 Tage", fontSize = 11.sp, color = F.textSecondary)
                    Text(
                        "${dashboard.weekTotal}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = F.textStrong,
                    )
                }
            }
        }

        // Nicht-blockierender Hinweis, wenn das Handy gerade nicht antwortet
        // (der angezeigte Stand kommt dann aus dem lokalen Spiegel).
        model.fehler?.let { meldung ->
            item {
                Text(
                    meldung,
                    fontSize = 11.sp,
                    color = F.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 4.dp),
                )
            }
        }

        // Schnell-Anlegen: die zuletzt verwendeten Medikamente als Kacheln.
        if (dashboard.vorschlaege.isNotEmpty()) {
            item {
                Text(
                    "Anlegen",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = F.textSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(dashboard.vorschlaege) { name ->
                Chip(
                    onClick = { model.anlegen(name) },
                    enabled = model.legtAn == null,
                    label = {
                        Text(
                            name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                        )
                    },
                    icon = {
                        if (model.legtAn == name) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                indicatorColor = F.primary,
                            )
                        }
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = F.surface,
                        contentColor = F.textStrong,
                    ),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        // Letzte Einträge (reine Anzeige).
        if (dashboard.eintraege.isNotEmpty()) {
            item {
                Text(
                    "Zuletzt",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = F.textSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(dashboard.eintraege) { eintrag ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 3.dp),
                ) {
                    Text(
                        eintrag.medikament,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = F.textStrong,
                        maxLines = 2,
                    )
                    Text(
                        "${tagesLabel(eintrag.zeit.toLocalDate())} · " +
                            eintrag.zeit.format(UhrzeitFormat),
                        fontSize = 11.sp,
                        color = F.textSecondary,
                    )
                }
            }
        } else {
            item {
                Text(
                    "Noch keine Einträge.\nÖffne die App auf dem Handy oder tippe unten.",
                    fontSize = 12.sp,
                    color = F.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item {
            CompactChip(
                onClick = model::aktualisieren,
                label = { Text("Aktualisieren", fontSize = 12.sp) },
                colors = ChipDefaults.secondaryChipColors(
                    backgroundColor = F.surface,
                    contentColor = F.text,
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// --- Zustände -------------------------------------------------------------------

@Composable
private fun LadeAnsicht() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(indicatorColor = F.primary)
    }
}

@Composable
private fun BestaetigungsAnsicht(medikament: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_mh_check),
                contentDescription = null,
                tint = F.textGreen,
                modifier = Modifier.size(40.dp),
            )
            Text(
                medikament,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = F.textStrong,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("gespeichert", fontSize = 12.sp, color = F.textSecondary)
        }
    }
}

@Composable
private fun FehlerAnsicht(meldung: String, onErneut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            meldung,
            fontSize = 13.sp,
            color = F.text,
            textAlign = TextAlign.Center,
        )
        CompactChip(
            onClick = onErneut,
            label = { Text("Erneut versuchen", fontSize = 12.sp) },
            colors = ChipDefaults.primaryChipColors(
                backgroundColor = F.primary,
                contentColor = F.onPrimary,
            ),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
