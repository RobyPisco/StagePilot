package it.stagepilot.app.core

/** Cleans presentation debris, never rewrites lyrics or invents chords. */
data class PreparedSong(val body: String, val meta: Meta, val removed: Int, val bpm: Int? = null, val duration: String = "")

private val SITE_JUNK = Regex("""^\s*(?:Last update\s*:|(?:Chords|Tabs?|Transcribed|Tabbed)\s+by\b|(?:Rating|Difficulty|Views|Favorites)\s*[:\d]|X\s*$)""", RegexOption.IGNORE_CASE)
private val SITE_TITLE = Regex("""^\s*(.+?)\s+(?:chords?|tab)\s+by\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
private val SITE_META = Regex("""^\s*(?:Tuning|Key|Capo)\s*:""", RegexOption.IGNORE_CASE)
private val BAR_LINE = Regex("""^\s*\|.*\|\s*$""")

fun prepareSong(raw: String, keepTab: Boolean = false): PreparedSong {
    val original = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ')
    var meta = chordProMeta(original)
    var removed = 0
    var bpm: Int? = null
    var duration = ""
    val lines = original.split('\n').mapNotNull { line ->
        SITE_TITLE.matchEntire(line)?.let {
            if (meta.title.isBlank()) meta = meta.copy(title = it.groupValues[1].trim(), artist = meta.artist.ifBlank { it.groupValues[2].trim() })
            removed++
            return@mapNotNull null
        }
        chordProDirective(line)?.let { d ->
            if (d.name in listOf("tempo", "bpm")) bpm = d.value.toIntOrNull()?.takeIf { it in 20..400 }
            if (d.name == "duration" && secs(d.value) > 0) duration = d.value
        }
        if (SITE_JUNK.containsMatchIn(line) || SITE_META.containsMatchIn(line) || (!keepTab && isTabLine(line))) {
            removed++
            return@mapNotNull null
        }
        if (BAR_LINE.matches(line) && '%' in line) {
            var previous = ""
            "| " + line.trim().removePrefix("|").removeSuffix("|").split('|').joinToString(" | ") { cell ->
                val value = cell.trim()
                if (value == "%" && previous.isNotBlank()) previous else value.also { if (it.isNotBlank() && it != "%") previous = it }
            } + " |"
        } else line.trimEnd()
    }
    val converted = normalizeBody(tabToChordPro(lines.joinToString("\n")))
    val parts = converted.split('\n')
    val clean = parts.filterIndexed { i, s ->
        if (!s.startsWith('#')) true
        else {
            val next = parts.drop(i + 1).firstOrNull { it.isNotBlank() }
            (next != null && !next.startsWith('#')).also { if (!it) removed++ }
        }
    }.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    return PreparedSong(clean, meta, removed, bpm, duration)
}
