package it.stagepilot.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One transaction covers the library, its rollback snapshot and the sync baseline. */
class LibraryDatabase(context: Context) : SQLiteOpenHelper(context, "stagepilot.sqlite", null, 1) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE meta (id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL, sync_base TEXT)")
        db.execSQL("CREATE TABLE bands (id TEXT PRIMARY KEY, position INTEGER NOT NULL, data TEXT NOT NULL)")
        for (table in listOf("songs", "setlists", "events")) {
            db.execSQL("CREATE TABLE $table (band_id TEXT NOT NULL, id TEXT NOT NULL, position INTEGER NOT NULL, data TEXT NOT NULL, PRIMARY KEY(band_id,id))")
        }
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, saved_at INTEGER NOT NULL, data TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun load(): DB? {
        val db = readableDatabase
        val meta = db.rawQuery("SELECT data FROM meta WHERE id=1", null).use {
            if (it.moveToFirst()) AppJson.decodeFromString<DB>(it.getString(0)) else return null
        }
        fun records(table: String, band: String): List<String> = db.rawQuery(
            "SELECT data FROM $table WHERE band_id=? ORDER BY position", arrayOf(band),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        val bands = db.rawQuery("SELECT data FROM bands ORDER BY position", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val band = AppJson.decodeFromString<Band>(c.getString(0))
                    add(band.copy(
                        songs = records("songs", band.id).map { AppJson.decodeFromString<Song>(it) },
                        sets = records("setlists", band.id).map { AppJson.decodeFromString<SetList>(it) },
                        events = records("events", band.id).map { AppJson.decodeFromString<Gig>(it) },
                    ))
                }
            }
        }
        return normalizeDb(meta.copy(bands = bands))
    }

    fun syncBase(): String? = readableDatabase.rawQuery("SELECT sync_base FROM meta WHERE id=1", null).use {
        if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
    }

    @Synchronized
    fun save(next: DB, previous: DB?, syncBase: String? = syncBase()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (previous != null) {
                db.execSQL("INSERT INTO history(saved_at,data) VALUES(?,?)", arrayOf(System.currentTimeMillis(), encodeBackup(previous)))
                db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 30)")
            }
            db.execSQL("INSERT OR REPLACE INTO meta(id,data,sync_base) VALUES(1,?,?)", arrayOf(encodeBackup(next.copy(bands = emptyList())), syncBase))
            for (table in listOf("songs", "setlists", "events", "bands")) db.delete(table, null, null)
            next.bands.forEachIndexed { i, band ->
                db.execSQL("INSERT INTO bands VALUES(?,?,?)", arrayOf(band.id, i, AppJson.encodeToString(Band.serializer(), band.copy(songs = emptyList(), sets = emptyList(), events = emptyList()))))
                band.songs.forEachIndexed { n, song -> db.execSQL("INSERT INTO songs VALUES(?,?,?,?)", arrayOf(band.id, song.id, n, AppJson.encodeToString(Song.serializer(), song))) }
                band.sets.forEachIndexed { n, set -> db.execSQL("INSERT INTO setlists VALUES(?,?,?,?)", arrayOf(band.id, set.id, n, AppJson.encodeToString(SetList.serializer(), set))) }
                band.events.forEachIndexed { n, event -> db.execSQL("INSERT INTO events VALUES(?,?,?,?)", arrayOf(band.id, event.id, n, AppJson.encodeToString(Gig.serializer(), event))) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun previous(): DB? = readableDatabase.rawQuery("SELECT data FROM history ORDER BY id DESC LIMIT 1", null).use {
        if (it.moveToFirst()) decodeBackup(it.getString(0)) else null
    }
}
