package it.stagepilot.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/* Il database vive in un solo file JSON nella memoria privata dell'app. È
   piccolo (decine di brani, qualche KB) e si scrive in blocco: niente Room,
   niente migrazioni di schema, e il file è già il formato di backup. Si
   scrive su un file temporaneo e poi si rinomina, così un crash a metà
   scrittura non lascia un database tronco. */
class Store(context: Context) {
    private val file = File(context.filesDir, "db.json")
    private val sqlite = LibraryDatabase(context)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _db = MutableStateFlow(load())
    val db: StateFlow<DB> = _db.asStateFlow()

    private fun load(): DB {
        sqlite.load()?.let { return it }
        if (file.exists()) {
            // A failed migration must never silently replace the user's library.
            val migrated = decodeBackup(file.readText())
            sqlite.save(migrated, null)
            return migrated
        }
        return seed().also { sqlite.save(it, null) }
    }

    @Synchronized
    fun update(change: (DB) -> DB) {
        try {
            val next = normalizeDb(change(_db.value))
            if (next == _db.value) return
            sqlite.save(next, _db.value)
            _db.value = next
        } catch (e: Exception) {
            _error.value = "Salvataggio non riuscito. I dati precedenti sono al sicuro. Libera spazio e riprova."
        }
    }

    fun clearError() { _error.value = null }
    fun syncBase(): String? = sqlite.syncBase()
    fun clearSyncBase() { sqlite.save(_db.value, null, null) }
    @Synchronized fun acceptSync(next: DB, expected: DB? = null) {
        check(expected == null || _db.value == expected) { "La libreria sul tablet è cambiata durante il collegamento. Sincronizza di nuovo: le tue ultime modifiche sono al sicuro." }
        val clean = normalizeDb(next)
        sqlite.save(clean, _db.value, encodeBackup(clean))
        _db.value = clean
    }
    fun undoLast(): Boolean {
        val prev = sqlite.previous() ?: return false
        update { prev }
        return true
    }
    fun saveEvent(event: Gig) = updateBand { b -> b.copy(events = b.events.filterNot { it.id == event.id } + event) }
    fun deleteEvent(id: String) = updateBand { b -> b.copy(events = b.events.filterNot { it.id == id }) }
    fun logRehearsal(session: Rehearsal) = updateBand { b -> b.copy(rehearsals = (b.rehearsals + session).takeLast(100)) }
    fun saveTask(task: PracticeTask) = updateBand { b -> b.copy(practiceTasks = b.practiceTasks.filterNot { it.id == task.id } + task) }
    fun deleteTask(id: String) = updateBand { b -> b.copy(practiceTasks = b.practiceTasks.filterNot { it.id == id }) }

    fun updateSong(id: String, change: (Song) -> Song) = update { db ->
        db.copy(bands = db.bands.map { b ->
            if (b.id != db.curBand) b
            else b.copy(songs = b.songs.map { if (it.id == id) change(it) else it })
        })
    }

    fun updateOptions(change: (Options) -> Options) = update { it.copy(opt = change(it.opt)) }

    /** Cambia il gruppo corrente. */
    fun updateBand(change: (Band) -> Band) = update { db ->
        db.copy(bands = db.bands.map { if (it.id == db.curBand) change(it) else it })
    }

    /** Cambia le voci della scaletta corrente. */
    fun updateItems(change: (List<SetItem>) -> List<SetItem>) = updateBand { b ->
        val cur = b.curSet ?: return@updateBand b
        b.copy(sets = b.sets.map { if (it.id == cur.id) it.copy(items = change(it.items)) else it })
    }

    /** Salva un brano nuovo o già esistente; se `toSet` è vero lo mette anche in
     *  coda alla scaletta corrente (se non c'è già). */
    fun saveSong(song: Song, toSet: Boolean = false) {
        updateBand { b ->
            val exists = b.songs.any { it.id == song.id }
            b.copy(songs = if (exists) b.songs.map { if (it.id == song.id) song else it } else b.songs + song)
        }
        if (toSet) updateItems { items ->
            if (items.any { it.type == "song" && it.id == song.id }) items else items + SetItem("song", id = song.id)
        }
    }

    /** Cancella il brano dal repertorio e da tutte le scalette. */
    /** Applica le modifiche arrivate dal PC. Ritorna quante sono andate a buon fine e quali si sono saltate. */
    fun applyPcEdits(edits: List<PcEdit>): EditsResult {
        var result: EditsResult? = null
        update { db -> applyEdits(db, edits).also { result = it }.db }
        return result!!
    }

    fun deleteSong(id: String) = updateBand { b ->
        b.copy(
            songs = b.songs.filterNot { it.id == id },
            practiceTasks = b.practiceTasks.filterNot { it.songId == id },
            sets = b.sets.map { s -> s.copy(items = s.items.filterNot { it.type == "song" && it.id == id }) },
        )
    }

    fun newId(prefix: String): String =
        prefix + java.util.UUID.randomUUID().toString()

    /* ── backup ───────────────────────────────────────────── */

    fun exportJson(): String = encodeBackup(_db.value)

    /** Sostituisce tutto il database con quello del backup. */
    @Synchronized fun replaceAll(other: DB) {
        val next = normalizeDb(other)
        sqlite.save(next, _db.value, null)
        _db.value = next
    }

    /** Aggiunge al gruppo in uso i brani preparati con la pagina del PC. */
    fun addPcSongs(songs: List<PcSong>) = update { normalizeDb(addPcSongs(it, songs, ::newId)) }

    /** Aggiunge la scaletta ricevuta (e i brani che mancano) al gruppo in uso. */
    fun importSetlist(p: SetlistPackage) = update { normalizeDb(importSetlist(it, p, ::newId)) }

    /** Aggiunge i gruppi del backup come gruppi nuovi, senza toccare i tuoi. */
    fun addBands(other: DB) = update { normalizeDb(mergeAsNewBands(it, other, ::newId)) }

    /* ── gruppi ───────────────────────────────────────────── */

    fun switchBand(id: String) = update { db -> if (db.bands.any { it.id == id }) db.copy(curBand = id) else db }

    fun newBand(name: String): String {
        val id = newId("b")
        update { db ->
            val band = normalizeBand(Band(id = id, name = name.trim().ifEmpty { "Nuovo gruppo" }))
            db.copy(bands = db.bands + band, curBand = id)
        }
        return id
    }

    fun renameBand(name: String) = updateBand { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun setRole(role: String) = updateBand { if (role in ROLES) it.copy(me = role) else it }

    /** L'ultimo gruppo non si elimina. */
    fun deleteBand(id: String) = update { db ->
        if (db.bands.size <= 1) db
        else {
            val left = db.bands.filterNot { it.id == id }
            db.copy(bands = left, curBand = if (db.curBand == id) left.first().id else db.curBand)
        }
    }

    /* ── scalette ─────────────────────────────────────────── */

    fun newSet(name: String) = updateBand { b ->
        val id = newId("set")
        b.copy(sets = b.sets + SetList(id, name.trim().ifEmpty { "Scaletta ${b.sets.size + 1}" }), cur = id)
    }

    fun renameSet(id: String, name: String) = updateBand { b ->
        b.copy(sets = b.sets.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    /** Duplica una scaletta con le stesse voci: comoda per variare una serata. */
    fun duplicateSet(id: String) = updateBand { b ->
        val src = b.sets.firstOrNull { it.id == id } ?: return@updateBand b
        val copy = src.copy(id = newId("set"), name = src.name + " (copia)")
        b.copy(sets = b.sets + copy, cur = copy.id)
    }

    /** L'ultima scaletta di un gruppo non si elimina. */
    fun deleteSet(id: String) = updateBand { b ->
        if (b.sets.size <= 1) b
        else {
            val left = b.sets.filterNot { it.id == id }
            b.copy(sets = left, cur = if (b.cur == id) left.first().id else b.cur)
        }
    }
}
