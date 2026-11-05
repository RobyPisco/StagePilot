package it.backline.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/* Le icone dei comandi, disegnate come nella proposta Palco: tratto da 2,
   estremi arrotondati. Il colore lo decide chi le usa (tint). */
private fun icon(name: String, d: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        val nodes = PathParser().parsePathString(d).toNodes()
        if (filled) addPath(nodes, fill = SolidColor(Color.Black))
        else addPath(
            nodes,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

object Ic {
    val Play = icon("play", "M7 4l13 8-13 8z", filled = true)
    val Stop = icon("stop", "M6 6h12v12H6z", filled = true)
    val Prev = icon("prev", "M19 20L9 12l10-8z M5 4v16")
    val Next = icon("next", "M5 4l10 8-10 8z M19 4v16")
    val Struct = icon("struct", "M4 6h16M4 12h10M4 18h16")
    val Lock = icon("lock", "M7 11h10a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z M8 11V8a4 4 0 0 1 8 0v3")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Chevron = icon("chevron", "M5 9l7 7 7-7")
    val Pause = icon("pause", "M7 5h3v14H7z M14 5h3v14h-3z", filled = true)
    val Up = icon("up", "M5 15l7-7 7 7")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Sliders = icon("sliders", "M4 7h9M17 7h3M4 17h3M11 17h9M15 4v6M9 14v6")
    val Grip = icon("grip", "M5 9h14M5 15h14")
    val Note = icon("note", "M9 18V5l11-2v13 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z")
    val Plus = icon("plus", "M12 5v14M5 12h14")
}
