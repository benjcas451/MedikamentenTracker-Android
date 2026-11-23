package org.dwarftsch.medikamente.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Zeitpunkt-Auswahl für „Andere Zeit“: erst Datum (bis ein Jahr zurück,
 * nicht in der Zukunft – wie in der Flutter-App), dann Uhrzeit.
 * Dialoge tragen nur Text-Aktionen; die Hauptaktion der Ansicht bleibt
 * „Anlegen“.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeitpunktDialog(
    initial: LocalDateTime?,
    onAbbrechen: () -> Unit,
    onUebernehmen: (LocalDateTime) -> Unit,
) {
    val start = initial ?: LocalDateTime.now()
    var gewaehltesDatum by remember { mutableStateOf<LocalDate?>(null) }

    if (gewaehltesDatum == null) {
        val heute = LocalDate.now()
        val datumState = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val tag = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !tag.isAfter(heute) && !tag.isBefore(heute.minusYears(1))
                }
            },
        )
        DatePickerDialog(
            shape = MaterialTheme.shapes.extraLarge,
            onDismissRequest = onAbbrechen,
            confirmButton = {
                MhTextButton("Weiter", {
                    val millis = datumState.selectedDateMillis ?: return@MhTextButton
                    gewaehltesDatum = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                })
            },
            dismissButton = { MhTextButton("Abbrechen", onAbbrechen) },
        ) {
            DatePicker(state = datumState, showModeToggle = false)
        }
    } else {
        val zeitState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = true,
        )
        AlertDialog(
            shape = MaterialTheme.shapes.extraLarge,
            onDismissRequest = onAbbrechen,
            title = { Text("Uhrzeit wählen", style = MhTypo.h2) },
            text = { TimePicker(state = zeitState) },
            confirmButton = {
                MhTextButton("Übernehmen", {
                    onUebernehmen(
                        LocalDateTime.of(
                            gewaehltesDatum ?: LocalDate.now(),
                            LocalTime.of(zeitState.hour, zeitState.minute),
                        ),
                    )
                })
            },
            dismissButton = { MhTextButton("Abbrechen", onAbbrechen) },
        )
    }
}

/** Bestätigung vor dem Löschen (einzelner oder letzter Eintrag). */
@Composable
fun LoeschDialog(
    titel: String,
    text: String,
    onAbbrechen: () -> Unit,
    onLoeschen: () -> Unit,
) {
    AlertDialog(
        shape = MaterialTheme.shapes.extraLarge,
        onDismissRequest = onAbbrechen,
        title = { Text(titel, style = MhTypo.h2) },
        text = { Text(text, style = MhTypo.body) },
        confirmButton = { MhLoeschenButton("Löschen", onLoeschen) },
        dismissButton = { MhTextButton("Abbrechen", onAbbrechen) },
    )
}

/** Scrollbarer Info-Dialog (Aufbau API / Aufbau Datenbank). */
@Composable
fun InfoDialog(
    titel: String,
    text: String,
    onSchliessen: () -> Unit,
) {
    AlertDialog(
        shape = MaterialTheme.shapes.extraLarge,
        onDismissRequest = onSchliessen,
        title = { Text(titel, style = MhTypo.h2) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text, style = MhTypo.small)
            }
        },
        confirmButton = { MhTextButton("Schließen", onSchliessen) },
    )
}
