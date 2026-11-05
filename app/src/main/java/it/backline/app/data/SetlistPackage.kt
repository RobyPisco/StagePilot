package it.backline.app.data

import kotlinx.serialization.Serializable

/* La scaletta da dare agli altri: un file JSON con i brani (chart compresi) e l'ordine, i segnaposto
   compresi. Chi lo riceve lo apre con Backline: i brani che ha già non si duplicano (stesso titolo e
   artista), gli altri si aggiungono, e la scaletta nasce nuova accanto alle sue. Non si porta dietro
   la trasposizione né la velocità di scorrimento, che sono gusti di chi suona. */

@Serializable
data class SharedItem(
    /** "song" oppure "mark" */
    val type: String,
    /** posizione del brano nell'elenco `songs` del pacchetto */
    val ref: Int? = null,
    val label: String = "",
    val dur: String = "",
)

@Serializable
data class SetlistPackage(
    val backline: String = "setlist",
    val v: Int = 1,
    val band: String = "",
    val name: String = "",
    val songs: List<PcSong> = emptyList(),
    val items: List<SharedItem> = emptyList(),
)

/** Il pacchetto della scaletta in uso del gruppo. */
fun buildSetlistPackage(band: Band, set: SetList): SetlistPackage {
    val songs = mutableListOf<PcSong>()
    val index = mutableMapOf<String, Int>()
    val items = set.items.mapNotNull { it ->
        if (it.isMark) SharedItem("mark", label = it.label.orEmpty(), dur = it.dur.orEmpty())
        else band.song(it.id)?.let { s ->
            val ref = index.getOrPut(s.id) {
                songs += PcSong(
                    title = s.title, artist = s.artist, key = s.key, bpm = s.bpm, dur = s.dur, capo = s.capo,
                    lead = s.lead, tags = s.tags, notes = s.notes, body = s.body, inSet = true,
                )
                songs.lastIndex
            }
            SharedItem("song", ref = ref)
        }
    }
    return SetlistPackage(band = band.name, name = set.name, songs = songs, items = items)
}

fun encodeSetlistPackage(p: SetlistPackage): String = AppJson.encodeToString(SetlistPackage.serializer(), p)

/** Il testo è una scaletta di Backline? Se no, `null`. */
fun parseSetlistPackage(text: String): SetlistPackage? {
    val t = text.trim()
    if (!t.startsWith("{") || !t.contains("\"setlist\"")) return null
    val p = runCatching { AppJson.decodeFromString(SetlistPackage.serializer(), t) }.getOrNull() ?: return null
    if (p.backline != "setlist") return null
    val songs = p.songs.filter { it.title.isNotBlank() && it.body.isNotBlank() }
    if (songs.isEmpty()) return null
    // i riferimenti restano validi solo se non si è tolto nessun brano: si rimappano
    val keep = p.songs.map { it.title.isNotBlank() && it.body.isNotBlank() }
    val newPos = mutableMapOf<Int, Int>()
    var n = 0
    keep.forEachIndexed { i, ok -> if (ok) newPos[i] = n++ }
    val items = p.items.mapNotNull { it ->
        if (it.type == "mark") it
        else newPos[it.ref ?: -1]?.let { r -> it.copy(ref = r) }
    }
    return p.copy(songs = songs, items = items)
}

/** Quanto verrà aggiunto: serve alla conferma. */
data class SetlistImportInfo(val newSongs: Int, val knownSongs: Int)

fun setlistImportInfo(db: DB, p: SetlistPackage): SetlistImportInfo {
    val have = db.band.songs.map { songKey(it.title, it.artist) }.toSet()
    val known = p.songs.count { songKey(it.title, it.artist) in have }
    return SetlistImportInfo(p.songs.size - known, known)
}

/** Aggiunge al gruppo in uso i brani che mancano e crea la scaletta, che diventa quella in uso. */
fun importSetlist(db: DB, p: SetlistPackage, newId: (String) -> String): DB {
    val band = db.band
    val byKey = band.songs.associateBy { songKey(it.title, it.artist) }.toMutableMap()
    val added = mutableListOf<Song>()
    val ids = p.songs.map { ps ->
        val k = songKey(ps.title, ps.artist)
        byKey[k]?.id ?: ps.toSong(newId("s")).also { added += it; byKey[k] = it }.id
    }
    val items = p.items.mapNotNull { it ->
        if (it.type == "mark") SetItem("mark", label = it.label, dur = it.dur)
        else it.ref?.let { r -> ids.getOrNull(r) }?.let { id -> SetItem("song", id = id) }
    }
    val base = p.name.ifBlank { "Scaletta ricevuta" }
    var name = base
    var i = 2
    while (band.sets.any { it.name == name }) name = "$base ($i)".also { i++ }
    val set = SetList(newId("set"), name, items)
    return db.copy(
        bands = db.bands.map { b ->
            if (b.id != band.id) b else b.copy(songs = b.songs + added, sets = b.sets + set, cur = set.id)
        },
    )
}
