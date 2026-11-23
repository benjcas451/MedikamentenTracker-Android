package org.dwarftsch.medikamente.wear.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.dwarftsch.medikamente.wear.R

// ============================================================================
// Minze & Honig 2.0 – Theme-Schicht der Uhr
// Quelle: DesignGuide/tokens/design-tokens.json (semantic.dark). Die Uhr ist
// immer dunkel; Komponenten benutzen nur diese Rollen, nie Skalenwerte.
// Gegenüber dem Bausatz (MhTheme.kt der Handy-App) ohne Material-3-Teil, weil
// Wear OS seine eigene MaterialTheme-Variante mitbringt.
// ============================================================================

/** Rollen-Tokens des Dunkelmodus. Namen wie in design-tokens.json. */
@Immutable
data class MhFarben(
    val bg: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val text: Color,
    val textStrong: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val link: Color,
    val primary: Color,
    val onPrimary: Color,
    val danger: Color,
    val onDanger: Color,
    val selectedBg: Color,
    val onSelected: Color,
    val tint: Color,
    val onTint: Color,
    val textGreen: Color,
)

val MhDunkel = MhFarben(
    bg = Color(0xFF1F2221),
    surface = Color(0xFF262A28),
    surfaceSubtle = Color(0xFF2B302D),
    border = Color(0xFF343A36),
    text = Color(0xFFE6EAE7),
    textStrong = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA9B0AB),
    textTertiary = Color(0xFF8A938D),
    textDisabled = Color(0xFF6B736E),
    link = Color(0xFFA8D5BA),
    primary = Color(0xFF82BD9B),
    onPrimary = Color(0xFF1F2221),
    danger = Color(0xFFF0B6B1),
    onDanger = Color(0xFF1F2221),
    selectedBg = Color(0xFF2C4F3B),
    onSelected = Color(0xFFC8E8D4),
    tint = Color(0xFF263B2F),
    onTint = Color(0xFFA8D5BA),
    textGreen = Color(0xFFA8D5BA),
)

/** Figtree, nur die Schnitte 400/500/600 (keine Kursive). */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
)
