package org.dwarftsch.medikamente.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                Button(
                    onClick = {
                        val millis = datumState.selectedDateMillis ?: return@Button
                        gewaehltesDatum = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                    },
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Weiter") }
            },
            dismissButton = {
                TextButton(
                    onClick = onAbbrechen,
                    colors = ButtonDefaults.textButtonColors(contentColor = MinzeHonig.farben.gruenText),
                ) { Text("Abbrechen") }
            },
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
            title = { Text("Uhrzeit wählen") },
            text = { TimePicker(state = zeitState) },
            confirmButton = {
                Button(
                    onClick = {
                        onUebernehmen(
                            LocalDateTime.of(
                                gewaehltesDatum ?: LocalDate.now(),
                                LocalTime.of(zeitState.hour, zeitState.minute),
                            ),
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(
                    onClick = onAbbrechen,
                    colors = ButtonDefaults.textButtonColors(contentColor = MinzeHonig.farben.gruenText),
                ) { Text("Abbrechen") }
            },
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
        title = { Text(titel) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onLoeschen,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) { Text("Löschen") }
        },
        dismissButton = {
            TextButton(
                onClick = onAbbrechen,
                colors = ButtonDefaults.textButtonColors(contentColor = MinzeHonig.farben.gruenText),
            ) { Text("Abbrechen") }
        },
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
        title = { Text(titel) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSchliessen,
                colors = ButtonDefaults.textButtonColors(contentColor = MinzeHonig.farben.gruenText),
            ) { Text("Schließen") }
        },
    )
}
