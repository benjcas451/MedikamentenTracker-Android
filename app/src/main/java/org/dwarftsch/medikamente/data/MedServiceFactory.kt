package org.dwarftsch.medikamente.data

import android.content.Context

/** Erstellt die aktuell konfigurierte Datenquelle. */
fun createConfiguredMedService(context: Context, settings: AppSettings, certSource: CertSource): MedService =
    when (settings.mode) {
        // Der API-Key ist im mTLS-Modus optional und wird nur mitgesendet,
        // wenn hinterlegt (manche Instanzen verlangen beides).
        DataSourceMode.API -> ApiService(
            certSource = certSource,
            baseUrl = settings.apiBaseUrl,
            apiKey = settings.apiKey,
        )
        DataSourceMode.API_KEY -> ApiService(
            baseUrl = settings.apiKeyBaseUrl,
            apiKey = settings.apiKey,
        )
        // Cloudflare Access sichert den Zugang am Rand; der API-Key ist wie im
        // mTLS-Modus optional und geht nur raus, wenn er hinterlegt ist.
        DataSourceMode.CLOUDFLARE -> ApiService(
            baseUrl = settings.cloudflareBaseUrl,
            apiKey = settings.apiKey,
            cfToken = settings.cfServiceToken(),
        )
        DataSourceMode.DEMO -> DemoService(context)
    }
