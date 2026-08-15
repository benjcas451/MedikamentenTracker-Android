import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Compose-Compiler; Kotlin selbst kommt ueber AGPs Built-in Kotlin.
    alias(libs.plugins.kotlin.compose)
}

// Gleiche Signing-Daten wie das :app-Modul: die Uhr-App MUSS mit demselben
// Schluessel signiert sein wie die Handy-App, sonst verweigert die
// Data-Layer-API die Kommunikation zwischen beiden.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

// Play verlangt fuer jedes Bundle einer Veroeffentlichung einen eigenen
// versionCode. Die Uhr-Variante bekommt denselben buildNumber-Mechanismus
// wie :app, plus festen Versatz (die Flutter-App hatte keinen Wear-Track;
// CI liefert 1000 + 100 + run_number, lokaler Fallback 1007).
val wearVersionCodeOffset = 1000

android {
    namespace = "org.dwarftsch.medikamente.wear"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // Gleiche Application-ID wie die Handy-App: Voraussetzung dafuer, dass
        // Play die Uhr-App als Wear-Variante derselben App ausliefert und dass
        // die Data-Layer-API beide Seiten einander zuordnet.
        applicationId = "org.dwarftsch.medikamente"
        // Wear OS 3 (API 30) ist die aelteste Version mit aktuellem Play-Support.
        minSdk = 30
        targetSdk = 36
        versionCode = wearVersionCodeOffset +
            ((findProperty("buildNumber") as String?)?.toIntOrNull() ?: 7)
        versionName = "2.0.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                val configured = File(keystoreProperties["storeFile"] as String)
                storeFile = if (configured.isAbsolute) configured else rootProject.file(configured.path)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // R8: verkleinert das Bundle deutlich; Keep-Regeln sind nicht
            // noetig, siehe src/main/keepRules/rules.keep.
            optimization {
                enable = true
            }
            // Ohne key.properties mit dem Debug-Key signieren, damit sich ein
            // Release-Build lokal auch ohne Keystore erzeugen laesst.
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.play.services.wearable)
}
