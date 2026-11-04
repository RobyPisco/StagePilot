package it.backline.app.core

/* Note e accordi. Porta fedele di src/core/chords.ts dell'app web.
   Dentro l'app gli accordi sono SEMPRE in notazione inglese: l'italiano
   entra solo in scrittura (toEnglish) ed esce solo a schermo (spell). */

val SHARP = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
val FLAT = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
private val IT_SHARP = listOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
private val IT_FLAT = listOf("Do", "Reb", "Re", "Mib", "Mi", "Fa", "Solb", "Sol", "Lab", "La", "Sib", "Si")
val PC = mapOf(
    "C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3, "E" to 4, "F" to 5,
    "F#" to 6, "Gb" to 6, "G" to 7, "G#" to 8, "Ab" to 8, "A" to 9, "A#" to 10, "Bb" to 10, "B" to 11,
)
private val IT_PC = mapOf("Do" to 0, "Re" to 2, "Mi" to 4, "Fa" to 5, "Sol" to 7, "La" to 9, "Si" to 11)

private val SUFFIX = Regex("""^(?:min|maj|dim|aug|sus|add|alt|m|M|°|ø|\+|-|\d|#|b|\(|\))*$""")
private val CHORD = Regex("""^([A-G][#b]?)(.*)$""")
private val NOTE = Regex("""[A-G][#b]?""")
private val NOTE_ONLY = Regex("""^[A-G][#b]?$""")
private val SEP = Regex("""(\s+|\|)""")

/* Un token è un accordo solo se parte da una fondamentale valida e tutto
   quello che segue è una sigla plausibile. Senza questo controllo "N.C."
   diventerebbe "N.D#." e "Break" diventerebbe "C#reak". */
fun isChord(tok: String): Boolean {
    val m = CHORD.find(tok) ?: return false
    var rest = m.groupValues[2]
    val sl = rest.indexOf('/')
    if (sl >= 0) {
        val bass = rest.substring(sl + 1)
        if (bass.isNotEmpty() && !NOTE_ONLY.matches(bass)) return false
        rest = rest.substring(0, sl)
    }
    return SUFFIX.matches(rest)
}

/* Spezza una riga in token conservando spazi e stanghette (alternando testo
   e separatore, come lo split con gruppo di cattura in JS), così la si può
   ricomporre identica dopo aver toccato solo gli accordi. */
fun tokens(s: String?): List<String> {
    val str = s ?: ""
    val out = ArrayList<String>()
    var idx = 0
    for (m in SEP.findAll(str)) {
        out.add(str.substring(idx, m.range.first))
        out.add(m.value)
        idx = m.range.last + 1
    }
    out.add(str.substring(idx))
    return out
}

fun transpose(str: String, n: Int): String {
    if (str.isEmpty() || n == 0) return str
    return tokens(str).joinToString("") { tok ->
        if (!isChord(tok)) tok
        else NOTE.replace(tok) { r ->
            val i = PC[r.value]
            if (i == null) r.value else SHARP[(((i + n) % 12) + 12) % 12]
        }
    }
}

/* Tonalità con bemolli in armatura: Fa, Si♭, Mi♭, La♭, Re♭ e le relative minori. */
private val FLAT_MAJ = listOf(5, 10, 3, 8, 1)
private val FLAT_MIN = listOf(2, 7, 0, 5, 10)

fun useFlats(key: String?): Boolean {
    val m = CHORD.find((key ?: "").trim()) ?: return false
    val root = m.groupValues[1]
    val pc = PC[root] ?: return false
    if (root.endsWith("b")) return true
    val minor = Regex("""^m(?!aj)""").containsMatchIn(m.groupValues[2])
    return (if (minor) FLAT_MIN else FLAT_MAJ).contains(pc)
}

fun flatFor(key: String?, mode: String): Boolean = when (mode) {
    "flat" -> true
    "sharp" -> false
    else -> useFlats(key)
}

/* Riscrive un accordo inglese nella notazione da mostrare. Cambia solo il
   nome della fondamentale e del basso: la sigla non si tocca. */
fun spell(tok: String, ital: Boolean, flat: Boolean): String {
    if (!isChord(tok)) return tok
    val names = if (ital) (if (flat) IT_FLAT else IT_SHARP) else (if (flat) FLAT else SHARP)
    return NOTE.replace(tok) { r ->
        val pc = PC[r.value]
        if (pc == null) r.value else names[pc]
    }
}

fun spellLine(str: String?, ital: Boolean, flat: Boolean): String =
    tokens(str).joinToString("") { tok -> spell(tok, ital, flat) }

/* Chi suona il basso legge la nota, non la sigla. Le rivolti sono
   l'eccezione: in C/G il basso suona SOL, quindi vince la nota dopo la barra. */
fun rootOf(tok: String): String {
    if (!isChord(tok)) return tok
    val sl = tok.lastIndexOf('/')
    if (sl >= 0) {
        val basso = tok.substring(sl + 1)
        if (NOTE_ONLY.matches(basso)) return basso
    }
    return CHORD.find(tok)?.groupValues?.get(1) ?: tok
}

fun rootsLine(str: String?): String = tokens(str).joinToString("") { rootOf(it) }

/* Trasposizione e riscrittura in un colpo solo. L'ordine conta: prima si
   traspone l'accordo intero, poi semmai lo si riduce alla nota, e solo alla
   fine si riscrive nella notazione da mostrare. */
fun showChords(str: String, tr: Int, ital: Boolean, flat: Boolean, roots: Boolean = false): String {
    val t = transpose(str, tr)
    return spellLine(if (roots) rootsLine(t) else t, ital, flat)
}

/* Do Re Mi → C D E, per quello che si scrive nell'editor. Prima si prova
   l'inglese: "Fadd9" è un accordo inglese valido e va lasciato stare, mentre
   "Fa" da solo non lo è e allora è italiano. */
fun toEnglish(tok: String): String {
    if (tok.isEmpty() || isChord(tok)) return tok
    val conv = { p: String ->
        val m = Regex("""^(Sol|Do|Re|Mi|Fa|La|Si)([#b]?)(.*)$""").find(p)
        if (m == null) null else {
            val acc = if (m.groupValues[2] == "#") 1 else if (m.groupValues[2] == "b") -1 else 0
            val pc = (((IT_PC[m.groupValues[1]]!! + acc) % 12) + 12) % 12
            (if (m.groupValues[2] == "b") FLAT[pc] else SHARP[pc]) + m.groupValues[3]
        }
    }
    val out = tok.split("/").joinToString("/") { conv(it) ?: it }
    return if (isChord(out)) out else tok
}

fun toEnglishLine(str: String?): String = tokens(str).joinToString("") { toEnglish(it) }

/* Normalizza solo dentro le parentesi quadre: il testo cantato non si tocca,
   se no "La sala chiude" diventerebbe un accordo. */
fun normalizeBody(body: String?): String =
    Regex("""\[([^\]]+)]""").replace(body ?: "") { m -> "[" + toEnglishLine(m.groupValues[1]) + "]" }

/* I chitarristi il capo lo contano in numeri romani: "CAPO III", non "CAPO 3". */
private val ROMAN = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")
fun roman(n: Int): String = ROMAN.getOrNull(n) ?: n.toString()
