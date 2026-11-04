package it.backline.app.data

/* Quello che arriva da fuori (un file di backup, il JSON dell'app web) non ha
   una forma garantita: si legge e si esce di qui con un database coerente.
   Porta di migrate()/migrateBand() dell'app web. */

private fun clampInt(v: Int, lo: Int, hi: Int) = v.coerceIn(lo, hi)

fun parseTags(s: String): List<String> =
    s.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

fun normalizeSong(s: Song): Song = s.copy(
    tags = s.tags.flatMap { parseTags(it) }.distinct(),
    tr = clampInt(s.tr, -11, 11),
    capo = clampInt(s.capo, 0, 11),
    scroll = if (s.scroll.isFinite()) s.scroll.coerceIn(0.5, 4.0) else 1.0,
    lead = clampInt(s.lead, 0, 60),
    notes = s.notes.filterValues { it.isNotBlank() },
)

fun normalizeBand(b: Band, index: Int = 0): Band {
    val songs = b.songs.filter { it.id.isNotEmpty() }.distinctBy { it.id }.map(::normalizeSong)
    val known = songs.map { it.id }.toSet()
    var sets = b.sets.filter { it.id.isNotEmpty() }.distinctBy { it.id }.mapIndexed { j, s ->
        s.copy(
            name = s.name.ifBlank { "Scaletta ${j + 1}" },
            // le voci con un brano che non c'è più (o un segnaposto senza nome) si perdono
            items = s.items.filter { if (it.isMark) !it.label.isNullOrBlank() else it.id in known },
        )
    }
    if (sets.isEmpty()) sets = listOf(SetList("set1", "Scaletta"))
    return b.copy(
        id = b.id.ifEmpty { "b" + System.currentTimeMillis().toString(36) + index },
        name = b.name.ifBlank { "Gruppo ${index + 1}" },
        me = if (b.me in ROLES) b.me else "Tutti",
        songs = songs,
        sets = sets,
        cur = if (sets.any { it.id == b.cur }) b.cur else sets.first().id,
    )
}

fun normalizeOptions(o: Options): Options = o.copy(
    notation = if (o.notation == "it") "it" else "en",
    accid = if (o.accid in listOf("auto", "sharp", "flat")) o.accid else "auto",
    fs = clampInt(o.fs, 14, 48),
)

/** Un database sempre utilizzabile: almeno un gruppo, gruppo corrente valido. */
fun normalizeDb(db: DB): DB {
    val bands = db.bands.mapIndexed { i, b -> normalizeBand(b, i) }
        .distinctBy { it.id }
        .ifEmpty { listOf(normalizeBand(Band(id = "b1", name = "Il mio gruppo"))) }
    return db.copy(
        v = 4,
        bands = bands,
        curBand = if (bands.any { it.id == db.curBand }) db.curBand else bands.first().id,
        opt = normalizeOptions(db.opt),
    )
}

/** Aggiunge i gruppi di `other` a `db` dando a tutto id nuovi: due backup dello
 *  stesso gruppo non si pestano i piedi. */
fun mergeAsNewBands(db: DB, other: DB, newId: (String) -> String): DB {
    val taken = db.bands.map { it.id }.toMutableSet()
    val names = db.bands.map { it.name }.toMutableSet()
    val added = other.bands.map { b ->
        var id = newId("b")
        while (id in taken) id = newId("b")
        taken += id
        // due gruppi con lo stesso nome si confondono: il secondo prende un numero
        var name = b.name
        var n = 2
        while (name in names) name = "${b.name} ($n)".also { n++ }
        names += name
        b.copy(id = id, name = name)
    }
    return db.copy(bands = db.bands + added)
}

fun decodeBackup(json: String): DB = normalizeDb(AppJson.decodeFromString(DB.serializer(), json))

fun encodeBackup(db: DB): String = AppJson.encodeToString(DB.serializer(), db)
