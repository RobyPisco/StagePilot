package it.backline.app.data

import it.backline.app.core.normalizeBody
import it.backline.app.core.toEnglish
import kotlinx.serialization.Serializable

/* I brani preparati con la pagina del PC (tools/pagina-pc). Arrivano in due modi: scaricati dalla
   coda sul PC (Wi-Fi) oppure mandati con adb come testo condiviso. Il corpo è già ChordPro: la
   pagina ha fatto la conversione e l'anteprima. */

@Serializable
data class PcSong(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val key: String = "",
    @Serializable(with = BpmSerializer::class) val bpm: Int? = null,
    val dur: String = "",
    val capo: Int = 0,
    val lead: Int = 0,
    val tags: List<String> = emptyList(),
    val notes: Map<String, String> = emptyMap(),
    val body: String = "",
    /** metterlo anche in coda alla scaletta in uso */
    val inSet: Boolean = true,
)

@Serializable
private data class PcPackage(val backline: String = "", val songs: List<PcSong> = emptyList())

@Serializable
private data class PcInboxReply(val songs: List<PcSong> = emptyList())

private fun usable(list: List<PcSong>) = list.filter { it.title.isNotBlank() && it.body.isNotBlank() }

/** Il testo condiviso è un pacchetto di brani della pagina del PC? Se no, `null` (sarà un tab da importare). */
fun parsePcPackage(text: String): List<PcSong>? {
    val t = text.trim()
    if (!t.startsWith("{") || !t.contains("\"backline\"")) return null
    val pkg = runCatching { AppJson.decodeFromString(PcPackage.serializer(), t) }.getOrNull() ?: return null
    return if (pkg.backline == "songs") usable(pkg.songs) else null
}

/** La risposta della coda sul PC. */
fun parsePcInbox(json: String): List<PcSong> =
    usable(AppJson.decodeFromString(PcInboxReply.serializer(), json).songs)

fun PcSong.toSong(newId: String): Song = normalizeSong(
    Song(
        id = newId,
        title = title.trim(),
        artist = artist.trim(),
        key = toEnglish(key.trim()),
        bpm = bpm,
        dur = dur.trim(),
        tags = tags,
        notes = notes,
        capo = capo,
        lead = lead,
        body = normalizeBody(body),
    ),
)

/** Chiave per riconoscere lo stesso brano: titolo e artista senza maiuscole, accenti e punteggiatura. */
fun songKey(title: String, artist: String): String {
    fun n(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .filter { it.isLetterOrDigit() }
    return n(title) + "|" + n(artist)
}

/** I brani di `songs` che il gruppo in uso ha già (stesso titolo e artista). */
fun alreadyInBand(db: DB, songs: List<PcSong>): List<PcSong> {
    val have = db.band.songs.map { songKey(it.title, it.artist) }.toSet()
    return songs.filter { songKey(it.title, it.artist) in have }
}

/** Aggiunge i brani al gruppo in uso, e alla scaletta in uso quelli che lo chiedono.
 *  I brani già presenti (stesso titolo e artista) e i doppioni nel pacchetto si saltano. */
fun addPcSongs(db: DB, songs: List<PcSong>, newId: (String) -> String): DB {
    val seen = db.band.songs.map { songKey(it.title, it.artist) }.toMutableSet()
    val fresh = songs.filter { seen.add(songKey(it.title, it.artist)) }
    val made = fresh.map { it to it.toSong(newId("s")) }
    return db.copy(
        bands = db.bands.map { b ->
            if (b.id != db.curBand) b
            else {
                val cur = b.curSet
                val extra = made.filter { it.first.inSet }.map { SetItem("song", id = it.second.id) }
                b.copy(
                    songs = b.songs + made.map { it.second },
                    sets = b.sets.map { s -> if (cur != null && s.id == cur.id) s.copy(items = s.items + extra) else s },
                )
            }
        },
    )
}
