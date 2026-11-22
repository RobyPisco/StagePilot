package it.stagepilot.app.data

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import it.stagepilot.app.core.keyLabel
import it.stagepilot.app.core.mmss
import it.stagepilot.app.core.ordinals
import it.stagepilot.app.core.setDuration
import java.io.File

/** Genera un PDF A4 ad alto contrasto con caratteri grandi, pronto da stampare
 *  e fissare sul palco. */
fun writeSetlistPdf(file: File, band: Band, set: SetList, opt: Options) {
    val entries = band.copy(cur = set.id).entries()
    val ord = ordinals(entries)
    val songCount = entries.count { it is Entry.Track }
    val totalDur = setDuration(entries)

    val pageW = 595
    val pageH = 842
    val marginX = 42f
    val rightX = pageW - marginX

    val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 90, 90)
        textSize = 13f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        letterSpacing = 0.12f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 26f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(75, 75, 75)
        textSize = 12f
        typeface = Typeface.MONOSPACE
    }
    val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 80, 80)
        textSize = 16f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val songPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 19f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 70, 70)
        textSize = 17f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    }
    val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 40, 40)
        textSize = 14f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    val heavyLine = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2.5f
    }
    val thinLine = Paint().apply {
        color = Color.rgb(200, 200, 200)
        strokeWidth = 1f
    }
    val dashLine = Paint().apply {
        color = Color.rgb(160, 160, 160)
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 120, 120)
        textSize = 10f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }

    fun ellipsize(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxW) end--
        return text.substring(0, end) + "…"
    }

    val rowH = 38f
    val maxY = pageH - 46f
    val doc = PdfDocument()
    try {
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var canvas = page.canvas

        fun drawHeader(): Float {
            var y = 52f
            canvas.drawText(band.name.uppercase(), marginX, y, bandPaint)
            y += 28f
            canvas.drawText(ellipsize(set.name, titlePaint, rightX - marginX), marginX, y, titlePaint)
            y += 18f
            val sub = "$songCount ${if (songCount == 1) "brano" else "brani"} · durata ${mmss(totalDur)}"
            canvas.drawText(sub, marginX, y, metaPaint)
            y += 12f
            canvas.drawLine(marginX, y, rightX, y, heavyLine)
            return y + 28f
        }

        var y = drawHeader()
        entries.forEachIndexed { i, entry ->
            if (y > maxY) {
                canvas.drawText("Pag. $pageNum", rightX, pageH - 22f, footerPaint)
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                canvas = page.canvas
                canvas.drawText("${band.name.uppercase()} · ${set.name}", marginX, 42f, metaPaint)
                canvas.drawLine(marginX, 50f, rightX, 50f, heavyLine)
                y = 82f
            }
            when (entry) {
                is Entry.Mark -> {
                    canvas.drawText("—", marginX, y, numPaint)
                    val dur = entry.dur.trim()
                    val infoW = if (dur.isNotEmpty()) infoPaint.measureText(dur) + 14f else 0f
                    canvas.drawText(ellipsize(entry.label, markPaint, rightX - marginX - 42f - infoW), marginX + 42f, y, markPaint)
                    if (dur.isNotEmpty()) canvas.drawText(dur, rightX, y, infoPaint)
                    canvas.drawLine(marginX, y + 11f, rightX, y + 11f, dashLine)
                }
                is Entry.Track -> {
                    val s = entry.song
                    val num = ord[i].toString().padStart(2, '0')
                    canvas.drawText(num, marginX, y, numPaint)
                    val k = keyLabel(s, opt.notation, opt.accid)
                    val info = buildString {
                        if (k.isNotBlank() && k != "?") append(k)
                        if (s.bpm != null) {
                            if (isNotEmpty()) append(" · ")
                            append("${s.bpm}")
                        }
                    }
                    val infoW = if (info.isNotEmpty()) infoPaint.measureText(info) + 14f else 0f
                    canvas.drawText(ellipsize(s.title, songPaint, rightX - marginX - 42f - infoW), marginX + 42f, y, songPaint)
                    if (info.isNotEmpty()) canvas.drawText(info, rightX, y, infoPaint)
                    canvas.drawLine(marginX, y + 11f, rightX, y + 11f, thinLine)
                }
            }
            y += rowH
        }
        canvas.drawText("Pag. $pageNum", rightX, pageH - 22f, footerPaint)
        doc.finishPage(page)

        file.outputStream().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
}
