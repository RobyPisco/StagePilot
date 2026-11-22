package it.stagepilot.app.core

/* "3:40" → 220 secondi. Qualunque altra cosa → 0. */
fun secs(d: String?): Int {
    val m = Regex("""^(\d+):(\d{1,2})$""").find((d ?: "").trim()) ?: return 0
    val minutes = m.groupValues[1].toIntOrNull() ?: return 0
    val seconds = m.groupValues[2].toIntOrNull() ?: return 0
    if (seconds !in 0..59 || minutes > 10000) return 0
    return minutes * 60 + seconds
}

fun mmss(t: Int): String = "${t / 60}:${(t % 60).toString().padStart(2, '0')}"

fun signed(t: Int): String = (if (t < 0) "−" else "+") + mmss(kotlin.math.abs(t))

/** Tap tempo: calcola i BPM dalla media degli ultimi intervalli validi (max 8).
 *  Una pausa oltre 2,5 secondi azzera la sequenza precedente. */
fun bpmFromTaps(taps: List<Long>): Int? {
    if (taps.size < 2) return null
    val recent = ArrayDeque<Long>()
    recent.addFirst(taps.last())
    for (i in taps.lastIndex - 1 downTo 0) {
        if (recent.size >= 9) break
        if (recent.first() - taps[i] > 2500L) break
        recent.addFirst(taps[i])
    }
    if (recent.size < 2) return null
    val avgMs = (recent.last() - recent.first()).toDouble() / (recent.size - 1)
    if (avgMs <= 0.0) return null
    return kotlin.math.round(60_000.0 / avgMs).toInt().coerceIn(30, 300)
}
