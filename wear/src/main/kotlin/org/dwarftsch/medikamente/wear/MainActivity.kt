package org.dwarftsch.medikamente.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    private lateinit var model: WatchModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = WatchModel(this)
        setContent { MedikamenteWearApp(model) }
    }

    /**
     * Beim Öffnen und nach jeder Rückkehr in den Vordergrund neu laden — bis
     * die Antwort da ist, zeigt die App den lokal gespiegelten letzten Stand.
     */
    override fun onResume() {
        super.onResume()
        model.aktualisieren()
    }

    override fun onDestroy() {
        model.schliessen()
        super.onDestroy()
    }
}
