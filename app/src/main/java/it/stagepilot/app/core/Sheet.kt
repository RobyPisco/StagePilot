package it.stagepilot.app.core

import it.stagepilot.app.data.Entry
import it.stagepilot.app.data.Song
/* Il chart: dal testo ChordPro a righe pronte da disegnare. Porta di
   src/core/sheet.ts — qui niente HTML, solo dati, e la UI li disegna. */

/** Un pezzo di riga: l'accordo (se c'è) e il testo che lo segue. */
data class Seg(val c: String?, val t: String)

private val BRACKET = Regex("""\[([^\]]+)]""")

fun segs(line: String): List<Seg> {
    val out = ArrayList<Seg>()
    var idx = 0
    for (m in BRACKET.findAll(line)) {
        val tx = line.substring(idx, m.range.first)
        if (tx.isNotEmpty()) {
            if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(t = out.last().t + tx)
            else out.add(Seg(null, tx))
        }
        out.add(Seg(m.groupValues[1], ""))
        idx = m.range.last + 1
    }
    val tail = line.substring(idx)
    if (tail.isNotEmpty()) {
        if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(t = out.last().t + tail)
        else out.add(Seg(null, tail))
    }
    return if (out.isEmpty()) listOf(Seg(null, "")) else out
}

/** Quello che arriva a schermo: accordo già trasposto e riscritto, più testo. */
data class ShownSeg(val chord: String, val text: String)

sealed interface SheetLine {
    data object Blank : SheetLine
    data class Section(val text: String) : SheetLine
    data class Lyric(val segs: List<ShownSeg>) : SheetLine
    /** Una riga di tablatura: si mostra a spaziatura fissa. */
    data class Tab(val text: String) : SheetLine
}

data class SheetOpts(
    val tr: Int = 0,
    val struct: Boolean = false,
    val ital: Boolean = false,
    val flat: Boolean = false,
    val roots: Boolean = false,
    val plain: Boolean = false,
)

/* struct = solo struttura: restano sezioni e accordi, il testo sparisce e
   con lui le righe che erano di solo testo. plain = lo specchio: spariscono
   gli accordi e le righe di soli accordi; le intestazioni restano. */
fun renderSheet(body: String?, o: SheetOpts = SheetOpts()): List<SheetLine> =
    (body ?: "").split("\n").mapNotNull { raw ->
        val line = raw.trimEnd('\r')
        when {
            line.isBlank() -> SheetLine.Blank
            line.trim().startsWith("#") -> SheetLine.Section(line.replace(Regex("""^\s*#\s?"""), ""))
            // la tablatura resta com'è (a spaziatura fissa); a chi canta non serve
            isTabLine(line) -> if (o.plain) null else SheetLine.Tab(line.trimEnd())
            else -> {
                val parts = segs(line)
                when {
                    o.struct -> {
                        val ch = parts.filter { !it.c.isNullOrEmpty() }
                        if (ch.isEmpty()) null
                        else SheetLine.Lyric(ch.map { ShownSeg(showChords(it.c!!, o.tr, o.ital, o.flat, o.roots), "") })
                    }
                    o.plain -> {
                        val testo = parts.joinToString("") { it.t }
                        if (testo.isBlank()) null else SheetLine.Lyric(listOf(ShownSeg("", testo)))
                    }
                    else -> SheetLine.Lyric(parts.map {
                        ShownSeg(
                            if (it.c.isNullOrEmpty()) "" else showChords(it.c, o.tr, o.ital, o.flat, o.roots),
                            it.t,
                        )
                    })
                }
            }
        }
    }

/** La mia nota prima di tutto, poi quella di arrangiamento che vale per la
 *  band. Se non ho niente di mio resta solo la seconda. */
fun notesFor(notes: Map<String, String>, me: String): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    if (me.isNotEmpty() && me != "Tutti") notes[me]?.takeIf { it.isNotBlank() }?.let { out.add(me to it) }
    notes["Tutti"]?.takeIf { it.isNotBlank() }?.let { out.add("Tutti" to it) }
    return out
}

/* ── scorrimento e scalette ─────────────────────────────── */

/** Il chart deve scorrere tutto nella durata del brano: la velocità discende
 *  dal pezzo, non va azzeccata a orecchio. `lead` è l'intro in cui non si
 *  legge ancora niente, quindi va tolta dal tempo utile. `factor` è la
 *  correzione registrata in prova. Senza durata si ripiega su 20 px/s. */
fun speedFor(scrollable: Float, durSecs: Int, factor: Double = 1.0, lead: Int = 0): Float {
    if (!(scrollable > 0f)) return 0f
    val tt = if (durSecs > 0) maxOf(1, durSecs - maxOf(0, lead)) else 0
    val base = if (tt > 0) scrollable / tt else 20f
    return maxOf(1f, base * (if (factor > 0) factor.toFloat() else 1f))
}

/** La velocità in px/s secondo la base scelta. Con i BPM l'attesa iniziale non si sottrae: il tempo
 *  stimato parte già dal primo accordo del chart, e `lead` serve solo a ritardare l'avvio. */
fun speedFor(scrollable: Float, basis: Pair<SpeedBasis, Double>, factor: Double = 1.0, lead: Int = 0): Float =
    when (basis.first) {
        SpeedBasis.BPM -> {
            if (!(scrollable > 0f)) 0f
            else maxOf(1f, scrollable / maxOf(1.0, basis.second).toFloat() * (if (factor > 0) factor.toFloat() else 1f))
        }
        SpeedBasis.DURATION -> speedFor(scrollable, basis.second.toInt(), factor, lead)
        SpeedBasis.FIXED -> speedFor(scrollable, 0, factor, lead)
    }

/** Da dove viene la velocità di base: dai BPM (la musica), dalla durata (il brano intero) o da niente. */
enum class SpeedBasis { BPM, DURATION, FIXED }

/** Quanto "dura" il chart nella musica, dai BPM: si contano le battute scritte e si moltiplica per la
 *  durata di una battuta (a 4/4, 240 ÷ BPM secondi). Una riga di soli accordi vale un accordo per battuta;
 *  una riga cantata ne vale almeno due. È una stima: chi legge corregge con la velocità ×… sul brano.
 *  Restituisce null senza BPM o senza accordi su cui contare. */
fun estimateChartSeconds(body: String?, bpm: Int?): Double? {
    if (bpm == null || bpm <= 0) return null
    var bars = 0
    for (raw in (body ?: "").split("\n")) {
        val line = raw.trimEnd('\r')
        if (line.isBlank() || line.trim().startsWith("#")) continue
        val parts = segs(line)
        val chords = parts.count { !it.c.isNullOrEmpty() }
        val sung = parts.any { it.t.isNotBlank() }
        bars += when {
            chords == 0 -> if (sung) 2 else 0
            sung -> maxOf(chords, 2)
            else -> chords
        }
    }
    if (bars == 0) return null
    return bars * 240.0 / bpm
}

/** La base della velocità e i secondi su cui distribuire lo scorrimento. */
fun speedBasis(body: String?, bpm: Int?, durSecs: Int): Pair<SpeedBasis, Double> {
    estimateChartSeconds(body, bpm)?.let { return SpeedBasis.BPM to it }
    if (durSecs > 0) return SpeedBasis.DURATION to durSecs.toDouble()
    return SpeedBasis.FIXED to 0.0
}

/** I gradini della velocità di scorrimento. Il passo da 0,1 non basta: la velocità di base fa
 *  finire il chart insieme al brano, ma un chart scritto è quasi sempre più corto della
 *  canzone (i ritornelli non sono riscritti), quindi serve poter andare molto più veloci. */
val SPEED_STEPS = listOf(0.25, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0)

/** Il gradino successivo (+1) o precedente (-1), partendo dal più vicino al valore attuale. */
fun stepSpeed(current: Double, dir: Int): Double {
    val nearest = SPEED_STEPS.indices.minByOrNull { kotlin.math.abs(SPEED_STEPS[it] - current) } ?: 6
    return SPEED_STEPS[(nearest + dir).coerceIn(0, SPEED_STEPS.lastIndex)]
}

/** 1.0 → "1", 1.5 → "1.5", 0.25 → "0.25". */
fun speedLabel(v: Double): String = String.format(java.util.Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')

/** Numerazione: contano solo i brani, i segnaposto prendono 0. "02" deve voler
 *  dire il secondo pezzo, che è quello che si conta sul palco. */
fun ordinals(entries: List<Entry>): List<Int> {
    var n = 0
    return entries.map { if (it is Entry.Mark) 0 else ++n }
}

/** Somma le durate: i segnaposto contano come i brani, è tutto tempo che passa. */
fun setDuration(entries: List<Entry>): Int = entries.sumOf {
    when (it) {
        is Entry.Mark -> secs(it.dur)
        is Entry.Track -> secs(it.song.dur)
    }
}

/** La scaletta in chiaro, da mandare alla band la sera prima: numero, titolo,
 *  tonalità che suona davvero, durata. I segnaposto restano al loro posto e non
 *  prendono numero, come nella lista. */
fun setAsText(entries: List<Entry>, band: String, setName: String, keyOf: (Song) -> String): String {
    val ord = ordinals(entries)
    val righe = entries.mapIndexed { i, e ->
        when (e) {
            is Entry.Mark -> "--- ${e.label.ifBlank { "Pausa" }}" + if (e.dur.isNotBlank()) " (${e.dur})" else ""
            is Entry.Track -> listOf(
                ord[i].toString().padStart(2, '0') + ".", e.song.title,
                keyOf(e.song).takeIf { it.isNotEmpty() }?.let { "— $it" } ?: "",
                e.song.dur.takeIf { it.isNotBlank() }?.let { "— $it" } ?: "",
            ).filter { it.isNotEmpty() }.joinToString(" ")
        }
    }
    val songs = entries.count { it is Entry.Track }
    val marks = entries.size - songs
    val conto = "$songs ${if (songs == 1) "brano" else "brani"}" +
        (if (marks > 0) " · $marks ${if (marks == 1) "pausa" else "pause"}" else "") +
        " · " + mmss(setDuration(entries))
    return (listOf("$band — $setName", conto, "") + righe).joinToString("\n").trim()
}

/** Sposta un elemento da una posizione all'altra senza toccare l'originale. */
fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices) return list
    val a = list.toMutableList()
    val item = a.removeAt(from)
    a.add(to.coerceIn(0, a.size), item)
    return a
}

/** Tonalità come va mostrata: trasposta, nella notazione e con le alterazioni
 *  scelte da chi legge. */
fun keyLabel(song: Song, notation: String, accid: String, tr: Int = song.tr): String {
    if (song.key.isBlank()) return ""
    val k = transpose(song.key, tr)
    return spell(k, notation == "it", flatFor(k, accid))
}
