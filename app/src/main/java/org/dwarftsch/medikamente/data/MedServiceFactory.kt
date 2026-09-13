package org.dwarftsch.medikamente.data

import android.content.Context

/**
 * Erstellt die aktuell konfigurierte Datenquelle.
 *
 * [offlineFaehig] legt die Warteschlange darüber, die bei einem
 * Verbindungsabbruch einspringt.
 */
fun createConfiguredMedService(
    context: Context,
    settings: AppSettings,
    certSource: CertSource,
    offlineFaehig: Boolean = false,
): MedService {
    val dienst = createServerOderDemoService(context, settings, certSource)
    val zugang = aktuellerZugang(settings)
    if (!offlineFaehig || zugang == null) return dienst
    return OfflineService(dienst, OfflineSpeicher(context, zugang))
}

/**
 * Kennung des aktuellen Zugangs (Modus + Basis-URL); null im Demo-Modus, der
 * ohnehin lokal arbeitet und keine Warteschlange braucht.
 */
private fun aktuellerZugang(settings: AppSettings): String? = when (settings.mode) {
    DataSourceMode.API -> "api|${settings.apiBaseUrl}"
    DataSourceMode.API_KEY -> "apiKey|${settings.apiKeyBaseUrl}"
    DataSourceMode.CLOUDFLARE -> "cloudflare|${settings.cloudflareBaseUrl}"
    DataSourceMode.DEMO -> null
}

private fun createServerOderDemoService(
    context: Context,
    settings: AppSettings,
    certSource: CertSource,
): MedService =
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
