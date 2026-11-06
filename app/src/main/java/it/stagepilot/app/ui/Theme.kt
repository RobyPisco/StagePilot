package it.stagepilot.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.stagepilot.app.R

/* Grafica "Palco": nero caldo e ambra, tutto condensato e grande. Pensata
   per il buio di un palco: contrasto massimo, niente decorazioni. I valori
   sono quelli della proposta A del restyling. */
object Palco {
    val Pitch = Color(0xFF101114)
    val Panel = Color(0xFF191B20)
    val Raise = Color(0xFF22252B)
    val Line = Color(0xFF2B2E35)
    val Outline = Color(0xFF3B4452)
    val Amber = Color(0xFF7FB7FF)
    val OnAmber = Color(0xFF0B1B33)
    val Ink = Color(0xFFF2F1ED)
    val Dim = Color(0xFFA3A5AF)
    val AmberDeep = Color(0xFF1B2B40)
    val Green = Color(0xFFA2CBB3)
    val Purple = Color(0xFFB5A5DD)
    val Warn = Color(0xFFFF6B6B)
}

val Condensed = FontFamily(
    Font(R.font.ibm_plex_sans_condensed_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_condensed_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_condensed_bold, FontWeight.Bold),
)

/** Il carattere di sistema: nelle schermate di gestione (scaletta, repertorio, impostazioni) si legge come
 *  nelle app di sistema. Il Live resta nel carattere condensato, pensato per leggere da lontano. */
val Sans = FontFamily.SansSerif

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
fun StagePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
