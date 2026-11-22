package it.stagepilot.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.stagepilot.app.R

/* Grafica StagePilot: tema Palco (grafite e azzurro per il buio) e tema Giorno
   (carta chiara ad alto contrasto per esterni e prove con tanta luce).
   I colori sono reattivi tramite mutableStateOf: cambiare tema aggiorna subito
   tutte le schermate. */
object Palco {
    var Pitch by mutableStateOf(Color(0xFF101114)); private set
    var Panel by mutableStateOf(Color(0xFF191B20)); private set
    var Raise by mutableStateOf(Color(0xFF22252B)); private set
    var Line by mutableStateOf(Color(0xFF2B2E35)); private set
    var Outline by mutableStateOf(Color(0xFF3B4452)); private set
    var Amber by mutableStateOf(Color(0xFF7FB7FF)); private set
    var OnAmber by mutableStateOf(Color(0xFF0B1B33)); private set
    var Ink by mutableStateOf(Color(0xFFF2F1ED)); private set
    var Dim by mutableStateOf(Color(0xFFA3A5AF)); private set
    var AmberDeep by mutableStateOf(Color(0xFF1B2B40)); private set
    var Green by mutableStateOf(Color(0xFFA2CBB3)); private set
    var Purple by mutableStateOf(Color(0xFFB5A5DD)); private set
    var Warn by mutableStateOf(Color(0xFFFF6B6B)); private set

    fun apply(light: Boolean) {
        if (light) {
            Pitch = Color(0xFFF4F5F8)
            Panel = Color(0xFFFFFFFF)
            Raise = Color(0xFFE7ECF3)
            Line = Color(0xFFD5DCE6)
            Outline = Color(0xFFB4C0D2)
            Amber = Color(0xFF1D5FCC)
            OnAmber = Color(0xFFFFFFFF)
            Ink = Color(0xFF12151C)
            Dim = Color(0xFF5A6270)
            AmberDeep = Color(0xFFDCE8FC)
            Green = Color(0xFF1E7A4D)
            Purple = Color(0xFF6649B8)
            Warn = Color(0xFFC62828)
        } else {
            Pitch = Color(0xFF101114)
            Panel = Color(0xFF191B20)
            Raise = Color(0xFF22252B)
            Line = Color(0xFF2B2E35)
            Outline = Color(0xFF3B4452)
            Amber = Color(0xFF7FB7FF)
            OnAmber = Color(0xFF0B1B33)
            Ink = Color(0xFFF2F1ED)
            Dim = Color(0xFFA3A5AF)
            AmberDeep = Color(0xFF1B2B40)
            Green = Color(0xFFA2CBB3)
            Purple = Color(0xFFB5A5DD)
            Warn = Color(0xFFFF6B6B)
        }
    }
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

@Composable
fun StagePilotTheme(light: Boolean = false, lang: String = "auto", content: @Composable () -> Unit) {
    Palco.apply(light)
    Str.apply(lang)
    val scheme = if (light) {
        lightColorScheme(
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
    } else {
        darkColorScheme(
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
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
