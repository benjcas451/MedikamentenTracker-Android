# Medikamenten-Tracker (Android + Wear OS)

Native Android-App zur Erfassung eingenommener Medikamente (Freitext +
Zeitpunkt), mit eigenständiger Wear-OS-App. Kotlin, Jetpack Compose,
AGP 9 mit Built-in Kotlin. Portiert von einer Flutter-App — Bestandsdaten
und -einstellungen werden beim Update nahtlos übernommen (Details unten).

Schwester-Repos: **MedikamentenTracker-XCode** (iOS + watchOS, gleicher
Funktionsumfang, gleiches Design) und **StillzeitTracker-Android/-XCode**
(gleiche Architektur- und Design-Familie).

---

## Module

| Modul | Was | applicationId |
|---|---|---|
| `:app` | Telefon-App (Compose, Material 3) | `org.dwarftsch.medikamente` |
| `:wear` | Wear-OS-App (Compose for Wear OS) | `org.dwarftsch.medikamente` (namespace `…medikamente.wear`) |

Beide Module tragen **dieselbe applicationId** — Voraussetzung dafür, dass
Play die Uhr-App als Wear-Variante derselben App ausliefert und die
Data-Layer-API Telefon und Uhr einander zuordnet. Deshalb niemals beide
Debug-Varianten wahllos installieren: `:wear:installDebug` würde auf einem
Telefon-Emulator die Telefon-App ersetzen. Immer gezielt per
`adb -s <gerät> install` arbeiten.

## Einrichtung auf einem neuen Gerät

1. Repo klonen, in **Android Studio** öffnen — fertig. Es gibt keine
   externen Abhängigkeiten außer Maven-Artefakten.
2. **JDK:** Der Gradle-Daemon provisioniert sich sein JDK (Version 25)
   selbst über `gradle/gradle-daemon-jvm.properties` (Foojay-Resolver).
   Zum *Starten* des Gradle-Launchers genügt irgendein JDK 17–25 —
   Achtung: ein zu neues JDK im PATH (z. B. 26) kann den Launcher brechen;
   dann `JAVA_HOME` z. B. auf das JBR von Android Studio setzen
   (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`).
3. **Signing (nur für Release-Builds nötig):** `key.properties` nach dem
   Muster von `key.properties.example` im Repo-Root anlegen. Die Datei und
   der Keystore (`*.jks`) sind gitignored und dürfen **nie** eingecheckt
   werden. Ohne `key.properties` signieren Release-Builds automatisch mit
   dem Debug-Key (lokal baubar, aber nicht Play-tauglich). Es gilt derselbe
   Upload-Key wie bei der abgelösten Flutter-App (Play verlangt für Updates
   derselben applicationId denselben Schlüssel).

## Bauen & Testen

```bash
# Debug-APKs
./gradlew :app:assembleDebug :wear:assembleDebug

# Release (signiert, falls key.properties vorhanden)
./gradlew :app:assembleRelease :wear:assembleRelease

# Play-Bundles; buildNumber steuert den versionCode (siehe Versionierung)
./gradlew :app:bundleRelease :wear:bundleRelease -PbuildNumber=123

# Wear-App gezielt auf dem Uhr-Emulator installieren und starten
adb -s <wear-emulator> install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s <wear-emulator> shell am start \
  -n org.dwarftsch.medikamente/org.dwarftsch.medikamente.wear.MainActivity
```

Die Wear-App verlangt keine gekoppelte Uhr zum Starten — ohne erreichbares
Telefon zeigt sie den zuletzt gespiegelten Stand bzw. „Handy nicht
erreichbar“ mit Reconnect-Button.

## Versionierung

- `versionName`: manuell in `app/` und `wear/build.gradle.kts`.
  **Konvention:** Major/Minor (1.x.x, x.1.x) sind über alle Plattformen
  (Android **und** iOS) identisch; die Patch-Stelle darf pro Plattform
  divergieren.
- `versionCode`: `-PbuildNumber=<n>` (lokaler Fallback im Buildfile).
  Die CI übergibt `100 + github.run_number`; die Uhr addiert fest `+1000`.
  Play verlangt strikt steigende Codes **pro Formfaktor-Track**; die
  Flutter-App nutzte `run_number` direkt (zuletzt ≤ 6), der 100er-Versatz
  liegt sicher darüber.

## CI / Releases (`.github/workflows/build-aab.yml`)

Manuell per *workflow_dispatch*. Ein Lauf:

1. baut signierte **APKs** (Telefon + Wear) und hängt sie an ein
   GitHub-Release (`v<version>-<run_number>`) — direkt installierbar,
2. baut zusätzlich **App Bundles** und lädt sie in die Play-Tracks
   (`alpha` bzw. `wear:alpha`) — nur wenn der Schalter `play_upload`
   (Default: an) gesetzt ist.

**Play-Besonderheiten (bei Stillzeit hart erarbeitet):**
- Wear OS braucht einen **eigenen Formfaktor-Track**, der in der Console
  einmalig aktiviert werden muss. Telefon- und Wear-Bundle werden in
  **getrennten Schritten** (= getrennten Play-Edits) hochgeladen.
- Track-Namen sind **case-sensitiv** — schlägt der Wear-Upload mit
  „track not found“ fehl, listet die Fehlermeldung die verfügbaren Namen
  (bei Stillzeit hieß der Track `wear:Alpha`).
- Play verteilt aus dem Wear-Track automatisch an gekoppelte Uhren.

Benötigte **Repository-Secrets** (nur Namen, Werte niemals dokumentieren;
identische Namen wie im abgelösten Flutter-Repo — von dort übernehmen):
`PLAY_KEYSTORE_BASE64`, `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`,
`PLAY_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`.

## Herkunft & Datenmigration (Flutter → nativ)

Die App ersetzt eine Flutter-App unter derselben applicationId
(`org.dwarftsch.medikamente`). Beim Update bleiben alle Nutzerdaten
erhalten:

- **SQLite:** identische Datei `medikamenten_demo.db`
  (Standard-Datenbankpfad), identisches Schema, `user_version 1`.
  Zeitstempel als ISO 8601 UTC (lexikalisch sortierbar); der Parser
  toleriert auch die Mikrosekunden-Präzision alter Dart-Einträge.
- **Einstellungen:** einmalige Migration aus `FlutterSharedPreferences`
  (Keys mit Präfix `flutter.`) in die nativen Prefs, siehe `AppSettings`.
- **SAF-Berechtigung** des Zertifikats-Ordners überlebt das Update.

## Architektur (`:app`)

```
Models.kt                    MedEntry/MedCount/PeriodStats/MedStats, ISO-Parser
data/MedService.kt           gemeinsames Interface der Datenquellen
data/DemoService.kt          lokale SQLite (sqflite-kompatibel)
data/ApiService.kt           REST-Client (OkHttp; api.php-Actions + mTLS)
data/ClientCertificates.kt   PEM (crt/key) -> SSLSocketFactory, inkl. PKCS#1->#8
data/CertSource.kt           SAF-Ordner mit client.crt/client.key
data/AppSettings.kt          Prefs + Flutter-Migration
data/LocalBackupService.kt   JSON-Backup (Format kompatibel zu iOS/Flutter)
wear/WearRequestService.kt   Data-Layer-RPC-Endpunkt für die Uhr
ui/…                         Compose-UI (Theme, Home, Settings, Dialoge)
```

**Datenquellen (vom Nutzer wählbar):** Server per mTLS-Client-Zertifikat
(API-Key optional zusätzlich), Server per API-Key (`X-API-Key`-Header)
oder lokale SQLite ohne Sync.

## Watch-Protokoll (Data-Layer-API)

Die Uhr sendet `MessageClient.sendRequest` an den Pfad
`/medikamente/request`; `WearRequestService` antwortet. JSON, UTF-8,
gleiche Hülle wie bei Stillzeit:

```
Anfrage:  {"action": "...", "arguments": { ... }}
Antwort:  {"ok": true, "data": { ... }}  bzw.  {"ok": false, "error": "..."}
```

Aktionen: `getDashboard` (Heute-/7-Tage-Zähler + letzte 12 Einträge),
`createEntry` (`{"medikament": "...", "time"?: ISO8601}`), `undoLast`.
Die Uhr hat **keine eigene Datenquelle** — alles läuft über die auf dem
Telefon gewählte Quelle (wie bei der Apple-Watch-Variante). Sie spiegelt
das letzte Dashboard lokal und bietet die zuletzt verwendeten Medikamente
als Ein-Tipp-Kacheln an. Das Telefon meldet die Capability
`medikamente_phone_app` (res/values/wear.xml).

## REST-API & Datenmodell

Basis-URL konfiguriert der Nutzer in den Einstellungen; alle Endpunkte
liegen unter `<Basis-URL>api.php`. Alle Antworten JSON.

| Endpunkt | Zweck |
|---|---|
| `GET api.php?action=stats` | Statistik je Zeitraum (today, week, threeWeeks, month) mit total + Zählung je Medikament, plus `last` |
| `GET api.php?action=list[&limit=N]` | Einträge, neueste zuerst |
| `GET api.php?action=last` | letzter Eintrag |
| `POST api.php?action=add` | Eintrag anlegen: `{"medikament": "...", "time": "<ISO8601, optional>"}` |
| `POST api.php?action=delete` | Eintrag löschen: `{"id": 42}` |
| `POST api.php?action=undo_last` | letzten Eintrag löschen (404 = keiner vorhanden) |

Eintrag: `id`, `medikament` (Freitext), `time` (ISO 8601). Fehler:
`{"error": "..."}` mit passendem HTTP-Status. Die lokale Tabelle
`entries(id, medikament, time)` spiegelt exakt dieses Modell.

## Design-System „Minze & Honig“ (v1.0)

Quelle der Wahrheit im Code: `app/…/ui/Theme.kt`. Kernregeln:

- **Grundregel:** Weiß dominiert (~80 %), Farbe liegt *auf* dem Grund —
  nie als Seitenhintergrund. Dark: Grund `#1F2221`, Karten `#292D2B`,
  Ränder `#3A403C` — kein reines Schwarz.
- **Skalen 50–900** je Markenfarbe. 300 = Markenton (Flächen/Buttons),
  100 = zarte Hinweisfläche, 600/700 = text-/icontauglich auf Weiß,
  900 = Text auf 300er-Flächen. **Pastell (300) nie als Text auf Weiß.**
- **Markenfarben:** Minze (Primär) `#A8D5BA`/300, Honig (Sekundär)
  `#F7E8A4`/300, Flieder (Akzent, sparsam) `#CDB4DB`/300; Grau leicht
  grünstichig; Rot nur semantisch (Fehler/Löschen).
- **Medikamenten-Einträge:** einheitliches Avatar-Muster in Minze
  (zarte 100er-Fläche + Icon 700, Dark: `#263B2F` + 300).
- **Dark Mode:** Pastellflächen (300) bleiben unverändert mit 900er-Text;
  100er-Flächen werden zu den abgedunkelten Äquivalenten.
- **Typografie:** ausschließlich **Nunito** (eingebettet, OFL — Lizenz in
  `app/src/main/assets/OFL_NUNITO.txt`); Persönlichkeit über das Gewicht
  (400/600/700/800), keine Schriftmischung.
- **Form:** Radius 8 (klein) / 12 (Buttons, Inputs) / 16 (Karten) /
  24 (Dialoge) / Pill (Chips). Buttons min. 44 dp Höhe.
- Kontraste sind WCAG-AA-geprüft; Farbe nie als einziger Informationsträger.

## Sicherheit / was nie ins Repo darf

`key.properties`, `*.jks`, Play-Service-Account-JSON, API-Keys,
Server-URLs von Nutzern. Die `.gitignore` deckt das ab — bei neuen
Secrets zuerst dort eintragen, dann anlegen.
