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
