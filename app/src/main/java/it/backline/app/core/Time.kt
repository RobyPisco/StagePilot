package it.backline.app.core

/* "3:40" → 220 secondi. Qualunque altra cosa → 0. */
fun secs(d: String?): Int {
    val m = Regex("""^(\d+):(\d{1,2})$""").find((d ?: "").trim()) ?: return 0
    return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
}

fun mmss(t: Int): String = "${t / 60}:${(t % 60).toString().padStart(2, '0')}"

fun signed(t: Int): String = (if (t < 0) "−" else "+") + mmss(kotlin.math.abs(t))
