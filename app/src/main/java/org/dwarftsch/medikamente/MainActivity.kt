package org.dwarftsch.medikamente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.dwarftsch.medikamente.ui.HomeScreen
import org.dwarftsch.medikamente.ui.HomeViewModel
import org.dwarftsch.medikamente.ui.MedikamenteTheme
import org.dwarftsch.medikamente.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedikamenteTheme {
                val viewModel: HomeViewModel = viewModel()
                var zeigeEinstellungen by remember { mutableStateOf(false) }

                if (zeigeEinstellungen) {
                    // Beim Verlassen der Einstellungen die (womöglich neue)
                    // Datenquelle übernehmen — wie in der Flutter-App.
                    BackHandler {
                        zeigeEinstellungen = false
                        viewModel.datenquelleNeuAufbauen()
                    }
                    SettingsScreen(
                        settings = viewModel.settings,
                        certSource = viewModel.certSource,
                        onZurueck = {
                            zeigeEinstellungen = false
                            viewModel.datenquelleNeuAufbauen()
                        },
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel,
                        onEinstellungen = { zeigeEinstellungen = true },
                    )
                }
            }
        }
    }
}
