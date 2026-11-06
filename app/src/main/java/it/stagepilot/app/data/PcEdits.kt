package it.stagepilot.app.data

import it.stagepilot.app.core.normalizeBody
import it.stagepilot.app.core.toEnglish
import kotlinx.serialization.Serializable

/* Le modifiche al repertorio fatte dalla pagina del PC (scheda «Repertorio»): correggere i dati di un brano,
   toglierlo, riordinare una scaletta. Arrivano come i brani nuovi (coda sul PC via Wi-Fi, oppure con adb) e si
   applicano solo dopo una conferma. Le modifiche sono pensate per essere prudenti: i brani si riconoscono per id,
   e una scaletta cambiata sul tablet dopo l'ultimo backup letto dal PC non si sovrascrive. */

@Serializable
data class PcEdit(
    /** "song" (cambia dei campi), "delete" (toglie il brano) o "items" (nuovo ordine di una scaletta) */
    val op: String = "",
    /** il gruppo a cui si riferisce (vuoto = quello in uso) */
    val band: String = "",
    /** il brano, oppure la scaletta per "items" */
    val id: String = "",
    val title: String? = null,
    val artist: String? = null,
    val key: String? = null,
    /** 0 = toglie il BPM */
    val bpm: Int? = null,
    val dur: String? = null,
    val tags: List<String>? = null,
    val body: String? = null,
    /** com'era la scaletta quando il PC l'ha letta (vedi `itemSignature`): se ora è diversa non si tocca */
    val base: List<String>? = null,
    val items: List<SetItem>? = null,
)

@Serializable
private data class EditsPackage(val backline: String = "", val edits: List<PcEdit> = emptyList())

@Serializable
private data class InboxEdits(val songs: List<PcSong> = emptyList(), val edits: List<PcEdit> = emptyList(), val editIds: List<String> = emptyList())

/** Quello che il PC ha in coda per il telefono. */
class PcInbox(val songs: List<PcSong>, val edits: List<PcEdit>, val editIds: List<String>)

fun parsePcInboxFull(json: String): PcInbox {
    val r = AppJson.decodeFromString(InboxEdits.serializer(), json)
    return PcInbox(r.songs.filter { it.title.isNotBlank() && it.body.isNotBlank() }, r.edits.filter { it.op.isNotBlank() }, r.editIds)
}

/** Il testo condiviso è un pacchetto di modifiche dalla pagina del PC? Se no, `null`. */
fun parseEditsPackage(text: String): List<PcEdit>? {
    val t = text.trim()
    if (!t.startsWith("{") || !t.contains("\"backline\"")) return null
    val pkg = runCatching { AppJson.decodeFromString(EditsPackage.serializer(), t) }.getOrNull() ?: return null
    return if (pkg.backline == "edits") pkg.edits.filter { it.op.isNotBlank() } else null
}

/** Come una voce di scaletta si riconosce: i brani per id, i segnaposto per nome e durata. */
fun itemSignature(i: SetItem): String =
    if (i.isMark) "mark:${i.label.orEmpty()}|${i.dur.orEmpty()}" else "song:${i.id.orEmpty()}"

class EditsResult(val db: DB, val applied: Int, val skipped: List<String>)

private fun Band.matches(e: PcEdit, db: DB) = if (e.band.isBlank()) id == db.curBand else id == e.band

/** Applica le modifiche. Quelle che non si possono fare (brano sparito, scaletta cambiata) si saltano e si dicono. */
fun applyEdits(db: DB, edits: List<PcEdit>): EditsResult {
    var cur = db
    var applied = 0
    val skipped = mutableListOf<String>()
    for (e in edits) {
        val band = cur.bands.firstOrNull { it.matches(e, cur) }
        if (band == null) { skipped += "gruppo non trovato"; continue }
        when (e.op) {
            "song" -> {
                val s = band.songs.firstOrNull { it.id == e.id }
                if (s == null) { skipped += "«${e.title ?: e.id}»: non è più sul tablet"; continue }
                val next = normalizeSong(
                    s.copy(
                        title = e.title?.trim()?.takeIf { it.isNotEmpty() } ?: s.title,
                        artist = e.artist?.trim() ?: s.artist,
                        key = e.key?.let { toEnglish(it.trim()) } ?: s.key,
                        bpm = if (e.bpm == null) s.bpm else e.bpm.takeIf { it in 20..400 },
                        dur = e.dur?.trim() ?: s.dur,
                        tags = e.tags ?: s.tags,
                        body = e.body?.takeIf { it.isNotBlank() }?.let { normalizeBody(it) } ?: s.body,
                    ),
                )
                cur = cur.copy(bands = cur.bands.map { b -> if (b.id != band.id) b else b.copy(songs = b.songs.map { x -> if (x.id == s.id) next else x }) })
                applied++
            }
            "delete" -> {
                if (band.songs.none { it.id == e.id }) { skipped += "«${e.title ?: e.id}»: già tolto"; continue }
                cur = cur.copy(
                    bands = cur.bands.map { b ->
                        if (b.id != band.id) b else b.copy(
                            songs = b.songs.filterNot { it.id == e.id },
                            sets = b.sets.map { st -> st.copy(items = st.items.filterNot { it.type == "song" && it.id == e.id }) },
                        )
                    },
                )
                applied++
            }
            "items" -> {
                val set = band.sets.firstOrNull { it.id == e.id }
                val items = e.items
                if (set == null || items == null) { skipped += "scaletta non trovata"; continue }
                if (e.base != null && set.items.map(::itemSignature) != e.base) {
                    skipped += "«${set.name}»: sul tablet è cambiata dopo l'ultimo backup letto dal PC, non la tocco"
                    continue
                }
                val have = band.songs.map { it.id }.toSet()
                val clean = items.filter { it.isMark || it.id in have }
                cur = cur.copy(bands = cur.bands.map { b -> if (b.id != band.id) b else b.copy(sets = b.sets.map { st -> if (st.id == set.id) st.copy(items = clean) else st }) })
                applied++
            }
            else -> skipped += "modifica sconosciuta (${e.op})"
        }
    }
    return EditsResult(normalizeDb(cur), applied, skipped)
}

/** Righe per la conferma: cosa cambierebbe. */
class EditsSummary(val changed: List<String>, val deleted: List<String>, val sets: List<String>)

fun summarizeEdits(db: DB, edits: List<PcEdit>): EditsSummary {
    fun band(e: PcEdit) = db.bands.firstOrNull { it.matches(e, db) }
    fun name(e: PcEdit) = band(e)?.songs?.firstOrNull { it.id == e.id }?.let { it.title } ?: e.title ?: e.id
    return EditsSummary(
        changed = edits.filter { it.op == "song" }.map(::name),
        deleted = edits.filter { it.op == "delete" }.map(::name),
        sets = edits.filter { it.op == "items" }.map { e -> band(e)?.sets?.firstOrNull { it.id == e.id }?.name ?: e.id },
    )
}
