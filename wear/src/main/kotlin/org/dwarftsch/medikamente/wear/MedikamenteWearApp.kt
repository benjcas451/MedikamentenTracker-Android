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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Typography
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- Design-System „Minze & Honig“ (v1.0, Dark-Regeln aus Guide 2.8) ---------
// Pastelltöne (300) dienen auf dunklem Grund direkt als Akzent; Buttons
// tragen Füllung 300 mit 900er-Text. Medikamente nutzen durchgehend Minze.

private val Minze300 = Color(0xFFA8D5BA)
private val Minze900 = Color(0xFF22392C)
private val MinzeDunkel = Color(0xFF263B2F)
private val Honig300 = Color(0xFFF7E8A4)
private val Honig900 = Color(0xFF473A17)
private val Rot300 = Color(0xFFF0B6B1)
private val Grund = Color(0xFF1F2221)
private val Karte = Color(0xFF292D2B)
private val TextHell = Color(0xFFECEFED)
private val TextSekundaer = Color(0xFFA9B0AB)

private val MedikamenteFarben = Colors(
    primary = Minze300,
    primaryVariant = Color(0xFF82BD9B),
    secondary = Honig300,
    secondaryVariant = Color(0xFFEDD374),
    background = Grund,
    surface = Karte,
    error = Rot300,
    onPrimary = Minze900,
    onSecondary = Honig900,
    onBackground = TextHell,
    onSurface = TextHell,
    onSurfaceVariant = TextSekundaer,
    onError = Color(0xFF4A211E),
)

/** Nunito – Persönlichkeit über das Gewicht (Guide Abschnitt 3). */
private val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private val MedikamenteTypografie = Typography(defaultFontFamily = Nunito)

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
        // Kopf: Heute- und 7-Tage-Zähler auf zarter Minze-Fläche.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Heute", fontSize = 11.sp, color = TextSekundaer)
                    Text(
                        "${dashboard.todayTotal}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Minze300,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("7 Tage", fontSize = 11.sp, color = TextSekundaer)
                    Text(
                        "${dashboard.weekTotal}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHell,
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
                    color = Rot300,
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
                    fontWeight = FontWeight.Bold,
                    color = Minze300,
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
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                    },
                    icon = {
                        if (model.legtAn == name) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                indicatorColor = Minze900,
                            )
                        }
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Minze300,
                        contentColor = Minze900,
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
                    fontWeight = FontWeight.Bold,
                    color = Minze300,
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
                        fontWeight = FontWeight.Bold,
                        color = TextHell,
                        maxLines = 2,
                    )
                    Text(
                        "${tagesLabel(eintrag.zeit.toLocalDate())} · " +
                            eintrag.zeit.format(UhrzeitFormat),
                        fontSize = 11.sp,
                        color = TextSekundaer,
                    )
                }
            }
        } else {
            item {
                Text(
                    "Noch keine Einträge.\nÖffne die App auf dem Handy oder tippe unten.",
                    fontSize = 12.sp,
                    color = TextSekundaer,
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
                    backgroundColor = Karte,
                    contentColor = TextHell,
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
        CircularProgressIndicator(indicatorColor = Minze300)
    }
}

@Composable
private fun BestaetigungsAnsicht(medikament: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✓", fontSize = 40.sp, color = Minze300, fontWeight = FontWeight.ExtraBold)
            Text(
                medikament,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextHell,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("gespeichert", fontSize = 12.sp, color = TextSekundaer)
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
            color = TextHell,
            textAlign = TextAlign.Center,
        )
        CompactChip(
            onClick = onErneut,
            label = { Text("Erneut versuchen", fontSize = 12.sp) },
            colors = ChipDefaults.primaryChipColors(
                backgroundColor = Minze300,
                contentColor = Minze900,
            ),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
