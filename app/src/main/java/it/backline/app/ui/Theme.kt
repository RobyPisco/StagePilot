package it.backline.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.backline.app.R

/* Grafica "Palco": nero caldo e ambra, tutto condensato e grande. Pensata
   per il buio di un palco: contrasto massimo, niente decorazioni. I valori
   sono quelli della proposta A del restyling. */
object Palco {
    val Pitch = Color(0xFF0E0D0B)
    val Panel = Color(0xFF171512)
    val Raise = Color(0xFF211E19)
    val Line = Color(0xFF2E2A23)
    val Outline = Color(0xFF3F392F)
    val Amber = Color(0xFFFFB000)
    val OnAmber = Color(0xFF1A1200)
    val Ink = Color(0xFFEDE7DA)
    val Dim = Color(0xFFA39A8B)
    val AmberDeep = Color(0xFF3A2D00)
    val Warn = Color(0xFFFF6B6B)
}

val Condensed = FontFamily(
    Font(R.font.ibm_plex_sans_condensed_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_condensed_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_condensed_bold, FontWeight.Bold),
)

val Mono = FontFamily(
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.Bold),
)

private val scheme = darkColorScheme(
    primary = Palco.Amber,
    onPrimary = Palco.OnAmber,
    background = Palco.Pitch,
    onBackground = Palco.Ink,
    surface = Palco.Panel,
    onSurface = Palco.Ink,
    surfaceVariant = Palco.Raise,
    onSurfaceVariant = Palco.Dim,
    outline = Palco.Outline,
    outlineVariant = Palco.Line,
)

/** Sempre scuro: è un'app da palco, non segue il tema del telefono. */
@Composable
fun BacklineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
