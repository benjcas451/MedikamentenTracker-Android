package org.dwarftsch.medikamente.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dwarftsch.medikamente.R

// ============================================================================
// Design-System „Minze & Honig" (v1.0) – Farb-Tokens
// Quelle: /DesignGuide/tokens/design-tokens.json
// ============================================================================

object Mh {
    // Minze (Primärfarbe)
    val gruen50 = Color(0xFFF2FAF5)
    val gruen100 = Color(0xFFE1F3E8)
    val gruen200 = Color(0xFFC8E8D4)
    val gruen300 = Color(0xFFA8D5BA)
    val gruen400 = Color(0xFF82BD9B)
    val gruen500 = Color(0xFF5FA07C)
    val gruen600 = Color(0xFF47825F)
    val gruen700 = Color(0xFF38664C)
    val gruen800 = Color(0xFF2C4F3B)
    val gruen900 = Color(0xFF22392C)

    // Honig (Sekundärfarbe)
    val gelb50 = Color(0xFFFDFAEC)
    val gelb100 = Color(0xFFFBF3D3)
    val gelb200 = Color(0xFFF9EDBB)
    val gelb300 = Color(0xFFF7E8A4)
    val gelb400 = Color(0xFFEDD374)
    val gelb700 = Color(0xFF8A6F26)
    val gelb900 = Color(0xFF473A17)

    // Flieder (Akzent)
    val lila100 = Color(0xFFF1E9F6)
    val lila200 = Color(0xFFE2D2EB)
    val lila300 = Color(0xFFCDB4DB)
    val lila700 = Color(0xFF634472)
    val lila900 = Color(0xFF37263F)

    // Grau (grünstichig)
    val grau50 = Color(0xFFF6F8F6)
    val grau100 = Color(0xFFEDF0EE)
    val grau200 = Color(0xFFDFE4E1)
    val grau300 = Color(0xFFC6CDC9)
    val grau400 = Color(0xFFA2ABA6)
    val grau500 = Color(0xFF7C857F)
    val grau600 = Color(0xFF5D655F)
    val grau700 = Color(0xFF454C47)
    val grau800 = Color(0xFF2E332F)
    val grau850 = Color(0xFF292D2B)
    val grau900 = Color(0xFF1F2221)

    // Rot (nur semantisch: Fehler, Löschen)
    val rot100 = Color(0xFFFAE3E1)
    val rot300 = Color(0xFFF0B6B1)
    val rot600 = Color(0xFFB3453E)
    val rot700 = Color(0xFF96362F)

    // Darkmode-Sonderwerte
    val dunkelRand = Color(0xFF3A403C)
    val dunkelText = Color(0xFFECEFED)
    val dunkelTextSekundaer = Color(0xFFA9B0AB)
    val dunkelGruenFlaeche = Color(0xFF263B2F)
    val dunkelGelbFlaeche = Color(0xFF3B3524)
    val dunkelLilaFlaeche = Color(0xFF352B3C)
    val dunkelRotFlaeche = Color(0xFF3F2523)
}

// ============================================================================
// Semantische Farben, die über die Material-Rollen hinausgehen
// (z. B. Text auf Weiß braucht 600/700, nie den Pastellton 300).
// ============================================================================

data class MhFarben(
    /** Grüner Text/Links auf dem Grund (Light: 700, Dark: 300). */
    val gruenText: Color,
    /** Überschriften-/Eyebrow-Farbe für Sektionen. */
    val sektionsTitel: Color,
    /** Erfolg-Icon (Zertifikate gefunden). */
    val erfolg: Color,
)

private val LocalMhFarben = staticCompositionLocalOf {
    MhFarben(gruenText = Mh.gruen700, sektionsTitel = Mh.gruen700, erfolg = Mh.gruen600)
}

/** Zugriff auf die erweiterten Design-System-Farben. */
object MinzeHonig {
    val farben: MhFarben
        @Composable get() = LocalMhFarben.current
}

// ============================================================================
// Medikamenten-Einträge: ein einheitliches Avatar-Muster in Minze
// (zarte Fläche 100 bzw. Dark-Äquivalent + Icon 700/300, Guide Chip-Muster).
// ============================================================================

/** Zarte Fläche (Stufe 100 bzw. Dark-Äquivalent) für Listen-Avatare. */
@Composable
fun avatarFlaeche(): Color =
    if (isSystemInDarkTheme()) Mh.dunkelGruenFlaeche else Mh.gruen100

/** Icon auf der zarten Fläche (Light: 700, Dark: 300). */
@Composable
fun avatarInhalt(): Color =
    if (isSystemInDarkTheme()) Mh.gruen300 else Mh.gruen700

/**
 * Einheitliche Eingabefeld-Farben: zarte Grau-50-Fläche (Dark: Grau-800),
 * damit sich das Feld sichtbar vom Grund abhebt; Rand Grau-200, Fokus in
 * Minze-500 (Guide 6: Inputs).
 */
@Composable
fun mhEingabefeldFarben(): androidx.compose.material3.TextFieldColors =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        focusedBorderColor = Mh.gruen500,
        cursorColor = Mh.gruen500,
        focusedLabelColor = MinzeHonig.farben.gruenText,
    )

// ============================================================================
// Material-3-Schemata aus den semantischen Tokens
// ============================================================================

private val HellesSchema = lightColorScheme(
    primary = Mh.gruen300,             // Primärbuttons: Fläche 300 …
    onPrimary = Mh.gruen900,           // … Text 900 derselben Farbe
    primaryContainer = Mh.gruen100,
    onPrimaryContainer = Mh.gruen700,
    secondary = Mh.gelb300,
    onSecondary = Mh.gelb900,
    secondaryContainer = Mh.gelb100,
    onSecondaryContainer = Mh.gelb700,
    tertiary = Mh.lila300,
    onTertiary = Mh.lila900,
    tertiaryContainer = Mh.lila100,
    onTertiaryContainer = Mh.lila700,
    background = Color.White,
    onBackground = Mh.grau800,
    surface = Color.White,
    onSurface = Mh.grau800,
    surfaceVariant = Mh.grau50,
    onSurfaceVariant = Mh.grau600,
    outline = Mh.grau200,
    outlineVariant = Mh.grau100,
    error = Mh.rot600,
    onError = Color.White,
    errorContainer = Mh.rot100,
    onErrorContainer = Mh.rot700,
    // Ohne diese Werte nutzt M3 seine lila-getönten Baseline-Flächen –
    // Dialoge und Menüs sollen aber weiß bzw. zart grau sein (Guide 6).
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Mh.grau50,
    surfaceContainer = Mh.grau50,
    surfaceContainerHigh = Color.White,      // Modals: weiß mit Schatten
    surfaceContainerHighest = Mh.grau100,
)

private val DunklesSchema = darkColorScheme(
    // Pastelltöne funktionieren auf dunklem Grau direkt als Akzent;
    // Buttons behalten Füllung 300 + Text 900 (Guide 2.8).
    primary = Mh.gruen300,
    onPrimary = Mh.gruen900,
    primaryContainer = Mh.dunkelGruenFlaeche,
    onPrimaryContainer = Mh.gruen200,
    secondary = Mh.gelb300,
    onSecondary = Mh.gelb900,
    secondaryContainer = Mh.dunkelGelbFlaeche,
    onSecondaryContainer = Mh.gelb200,
    tertiary = Mh.lila300,
    onTertiary = Mh.lila900,
    tertiaryContainer = Mh.dunkelLilaFlaeche,
    onTertiaryContainer = Mh.lila200,
    background = Mh.grau900,
    onBackground = Mh.dunkelText,
    surface = Mh.grau850,
    onSurface = Mh.dunkelText,
    surfaceVariant = Mh.grau800,
    onSurfaceVariant = Mh.dunkelTextSekundaer,
    outline = Mh.dunkelRand,
    outlineVariant = Mh.grau800,
    error = Mh.rot300,
    onError = Mh.grau900,
    errorContainer = Mh.dunkelRotFlaeche,
    onErrorContainer = Mh.rot300,
    // Dunkle Flächenabstufung statt Schatten (Guide 2.8).
    surfaceContainerLowest = Mh.grau900,
    surfaceContainerLow = Mh.grau850,
    surfaceContainer = Mh.grau850,
    surfaceContainerHigh = Mh.grau850,       // Modals: Karte #292D2B
    surfaceContainerHighest = Mh.grau800,
)

// ============================================================================
// Typografie: Nunito, Persönlichkeit über das Gewicht (Guide Abschnitt 3)
// ============================================================================

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private val MhTypografie = Typography(
    displayLarge = TextStyle(fontFamily = Nunito, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = Nunito, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Nunito, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Nunito, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    // AppBar-Titel
    titleLarge = TextStyle(fontFamily = Nunito, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp),
    // Kartentitel
    titleMedium = TextStyle(fontFamily = Nunito, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
    // Sektions-Überschriften
    titleSmall = TextStyle(fontFamily = Nunito, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Nunito, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp),
    // Buttons
    labelLarge = TextStyle(fontFamily = Nunito, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    // Caption/Labels (Versalien setzt die jeweilige Stelle selbst)
    labelSmall = TextStyle(fontFamily = Nunito, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, letterSpacing = 0.72.sp),
)

// ============================================================================
// Form: 4-px-Radius-Skala (Guide Abschnitt 5)
// ============================================================================

private val MhFormen = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Chips klein
    small = RoundedCornerShape(8.dp),        // Inputs klein
    medium = RoundedCornerShape(12.dp),      // Buttons, Inputs
    large = RoundedCornerShape(16.dp),       // Karten
    extraLarge = RoundedCornerShape(24.dp),  // Modals, Bottom Sheets
)

@Composable
fun MedikamenteTheme(content: @Composable () -> Unit) {
    val dunkel = isSystemInDarkTheme()
    val erweitert = if (dunkel) {
        MhFarben(gruenText = Mh.gruen300, sektionsTitel = Mh.gruen300, erfolg = Mh.gruen300)
    } else {
        MhFarben(gruenText = Mh.gruen700, sektionsTitel = Mh.gruen700, erfolg = Mh.gruen600)
    }
    CompositionLocalProvider(LocalMhFarben provides erweitert) {
        MaterialTheme(
            colorScheme = if (dunkel) DunklesSchema else HellesSchema,
            typography = MhTypografie,
            shapes = MhFormen,
            content = content,
        )
    }
}
