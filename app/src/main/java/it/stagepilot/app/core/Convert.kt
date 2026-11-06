package it.stagepilot.app.core

/* Convertitore "accordi sopra il testo" → ChordPro. Porta fedele di
   src/core/convert.ts dell'app web.

   I siti di tab incollano gli accordi su una riga sopra il testo, allineati
   per colonna. Li spostiamo dentro le quadre nel punto esatto della sillaba,
   che è già il formato che l'app legge. Le etichette tra quadre tipo
   [Chorus] diventano righe con #; il resto passa invariato. */

/* Fra gli accordi si infilano annotazioni che accordi non sono: x2, N.C., le
   stanghette di battuta, le note fra parentesi tipo (let ring). Qui le si
   copre di spazi: la riga resta lunga uguale, quindi le colonne non si
   spostano. Le parentesi si coprono solo quando stanno per conto loro:
   quelle attaccate a un accordo, come C(add9), sono parte della sigla. */
private val ANNOT = Regex("""^(?:[xX]\d{1,2}|\d{1,2}[xX]|N\.?C\.?|[|:%]+)$""")
private val LONE_PAREN = Regex("""(^|\s)(\([^()]*\))(?=\s|$)""")
private val NONSPACE = Regex("""\S+""")

private fun blanks(s: String) = " ".repeat(s.length)

private fun maskAnnot(line: String?): String =
    (line ?: "")
        .replace(LONE_PAREN) { m -> m.groupValues[1] + blanks(m.groupValues[2]) }
        .replace(NONSPACE) { m -> if (ANNOT.matches(m.value)) blanks(m.value) else m.value }

/** Gli accordi di una riga con la colonna in cui stanno. Le posizioni valgono
 *  anche sulla riga vera: mascherare non cambia le lunghezze. */
private fun chordTokens(line: String?): List<MatchResult> =
    NONSPACE.findAll(maskAnnot(line)).filter { isChord(it.value) }.toList()

fun isChordLine(line: String?): Boolean {
    val toks = NONSPACE.findAll(maskAnnot(line)).toList()
    return toks.isNotEmpty() && toks.all { isChord(it.value) }
}

private fun countTokens(line: String?) = chordTokens(line).size

/** Chord line isolata (niente testo sotto): incornicia ogni accordo dove sta,
 *  mantenendo gli spazi. Le annotazioni restano: su una riga di soli accordi
 *  un "x2" in coda è un'indicazione che al musicista serve. */
fun wrapChordsInPlace(line: String): String {
    val toks = chordTokens(line)
    var out = line
    for (i in toks.indices.reversed()) {
        val m = toks[i]
        out = out.substring(0, m.range.first) + "[" + m.value + "]" + out.substring(m.range.last + 1)
    }
    return out
}

private data class Placed(val tok: String, val index: Int, val seq: Int)

/** Una o più chord line + la riga di testo sotto: inserisce ogni accordo nel
 *  testo alla stessa colonna in cui cadeva sopra. Si inserisce da destra a
 *  sinistra, se no ogni inserimento sposterebbe gli indici di quelli dopo. */
fun mergeChordLyric(chordLines: List<String>, lyricLine: String): String {
    var seq = 0
    // qui le annotazioni si scartano: infilare "x2" fra le parole sporcherebbe il testo
    val toks = chordLines.flatMap { chordTokens(it) }
        .map { Placed(it.value, it.range.first, seq++) }
        // a parità di colonna vince l'ordine con cui erano scritte
        .sortedWith(compareByDescending<Placed> { it.index }.thenByDescending { it.seq })
    var out = lyricLine
    for ((tok, index, _) in toks) {
        // colonna oltre la fine del verso: l'accordo entra DOPO le parole,
        // alla colonna in cui il trascrittore l'aveva messo
        if (index > out.length) out = out.padEnd(index, ' ')
        out = out.substring(0, index) + "[" + tok + "]" + out.substring(index)
    }
    return out
}

fun mergeChordLyric(chordLine: String, lyricLine: String): String = mergeChordLyric(listOf(chordLine), lyricLine)

/** Una riga `{direttiva: valore}` o `{direttiva}` del vero ChordPro. */
data class Directive(val name: String, val value: String)

// Android usa il motore ICU, che non accetta `}` e `]` isolate come fa la JVM: vanno scritte con `\`
private val DIRECTIVE = Regex("""^\s*\{([^:{}]+)(?::\s*([^{}]*))?\}\s*$""")

fun chordProDirective(line: String?): Directive? {
    val m = DIRECTIVE.find(line ?: "") ?: return null
    return Directive(m.groupValues[1].trim().lowercase(), m.groupValues[2].trim())
}

private val CP_SECTIONS = mapOf(
    "soc" to "Chorus", "start_of_chorus" to "Chorus",
    "sov" to "Verse", "start_of_verse" to "Verse",
    "sob" to "Bridge", "start_of_bridge" to "Bridge",
    "sot" to "Tab", "start_of_tab" to "Tab",
)
private val CP_COMMENTS = setOf("c", "comment", "ci", "comment_italic", "cb", "comment_box")
private val SEC_ONLY = Regex("""^\s*\[([^\[\]]+)\]\s*$""")

fun tabToChordPro(text: String?): String {
    val lines = (text ?: "").replace(Regex("""\r\n?"""), "\n").split("\n")
    val out = ArrayList<String>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        // la riga di testata dei siti di tab (Tuning / Key / Capo) non è musica: i dati si leggono in chordProMeta
        if (HEADER_LINE.containsMatchIn(line)) { i++; continue }
        val dir = chordProDirective(line)
        if (dir != null) {
            if (dir.name in CP_COMMENTS && dir.value.isNotEmpty()) out.add("# " + dir.value)
            else CP_SECTIONS[dir.name]?.let { out.add("# " + dir.value.ifBlank { it }) }
            // le altre direttive (title, artist, key, capo, tempo, eoc…) si leggono e si scartano
            i++; continue
        }
        val sec = SEC_ONLY.find(line)
        if (sec != null && !isChord(toEnglish(sec.groupValues[1].trim()))) { out.add("# " + sec.groupValues[1].trim()); i++; continue }
        if (isChordLine(line)) {
            val run = arrayListOf(line)
            var j = i + 1
            while (j < lines.size && isChordLine(lines[j])) { run.add(lines[j]); j++ }
            val after = lines.getOrNull(j)
            val afterIsLyric = after != null && after.isNotBlank() && !isChordLine(after) && SEC_ONLY.find(after) == null
            if (afterIsLyric) {
                // L'ultima riga del blocco entra sempre nella fusione. Quelle
                // prima entrano solo finché sono un frammento (pochi accordi):
                // una riga con più accordi è un giro dichiarato per conto suo.
                var k = run.size - 1
                while (k > 0 && countTokens(run[k - 1]) <= 2) k--
                run.subList(0, k).forEach { out.add(wrapChordsInPlace(it)) }
                out.add(mergeChordLyric(run.subList(k, run.size), after!!))
                i = j + 1
            } else {
                run.forEach { out.add(wrapChordsInPlace(it)) }
                i = j
            }
            continue
        }
        out.add(line); i++
    }
    return out.joinToString("\n").replace(Regex("""\n{3,}"""), "\n\n").trim()
}

/** Un tab incollato grezzo nel campo sbagliato: righe di soli accordi senza
 *  quadre. Meglio dirlo al salvataggio che a brano aperto. */
fun looksRawTab(body: String?): Boolean =
    (body ?: "").split("\n").any { !it.contains('[') && isChordLine(it) }

/** Titolo, artista, tonalità e capotasto che si riescono a leggere dal testo incollato: dalle direttive
 *  ChordPro `{title: …}`, `{artist: …}`, `{key: …}` e dalla riga di testata che i siti di tab mettono in
 *  cima ("Tuning: E A D G B E Key: A Capo: No capo"). */
data class Meta(val title: String = "", val artist: String = "", val key: String = "", val capo: Int = 0)

private val HEADER_KEY = Regex("""Key:\s*([A-G][#b]?m?)""")
private val HEADER_CAPO = Regex("""Capo:\s*(\d{1,2})""")
private val HEADER_LINE = Regex("""^\s*(Tuning|Key|Capo)\s*:""", RegexOption.IGNORE_CASE)

fun chordProMeta(text: String?): Meta {
    var title = ""; var artist = ""; var key = ""; var capo = 0
    for (l in (text ?: "").split("\n")) {
        val d = chordProDirective(l)
        if (d != null) {
            when (d.name) {
                "title", "t" -> if (title.isEmpty()) title = d.value
                "artist", "subtitle", "st" -> if (artist.isEmpty()) artist = d.value
                "key" -> if (key.isEmpty()) key = d.value
                "capo" -> d.value.toIntOrNull()?.let { if (capo == 0) capo = it.coerceIn(0, 11) }
            }
        } else if (HEADER_LINE.containsMatchIn(l)) {
            if (key.isEmpty()) HEADER_KEY.find(l)?.let { key = it.groupValues[1] }
            if (capo == 0) HEADER_CAPO.find(l)?.let { capo = it.groupValues[1].toInt().coerceIn(0, 11) }
        }
    }
    return Meta(title, artist, toEnglish(key), capo)
}

/** Una riga di tablatura per chitarra: `e|---0---2---|`. Non è testo cantato né una riga di accordi:
 *  va mostrata a spaziatura fissa, altrimenti le note si disallineano. */
private val TAB_LINE = Regex("""^\s*[A-Ga-g][#b]?\|.*-.*$""")
fun isTabLine(line: String?): Boolean = TAB_LINE.matches(line ?: "")
