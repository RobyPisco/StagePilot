package it.stagepilot.app

import android.app.Instrumentation
import android.os.Bundle
import it.stagepilot.app.core.prepareSong
import it.stagepilot.app.data.*

/** Device checks deliberately use their own disposable database, never the library. */
class SmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val tests = listOf<Pair<String, () -> Unit>>(
            "sqliteTransactionAndReopen" to {
                val name = "stagepilot-smoke.sqlite"
                targetContext.deleteDatabase(name)
                try {
                    val original = seed()
                    var sql = LibraryDatabase(targetContext, name)
                    sql.save(original, null)
                    check(sql.load() == original)
                    val changed = original.copy(bands = original.bands.map { it.copy(name = "Test sul dispositivo") })
                    val base = encodeBackup(changed)
                    sql.save(changed, original, base)
                    sql.close()
                    sql = LibraryDatabase(targetContext, name)
                    check(sql.load() == changed)
                    check(sql.syncBase() == base)
                    check(sql.previous() == original)
                    // A failed write halfway through must roll back all tables AND the sync baseline.
                    val bad = changed.copy(bands = changed.bands.map { it.copy(songs = it.songs + it.songs.first()) })
                    check(runCatching { sql.save(bad, changed, "invalid") }.isFailure)
                    check(sql.load() == changed)
                    check(sql.syncBase() == base)
                    sql.close()
                } finally { targetContext.deleteDatabase(name) }
            },
            "pasteConversionOnAndroidICU" to {
                val song = prepareSong("Test chords by Example\nTuning: E A D G B E Key: Am Capo: 2\nDifficulty: easy\n[Verse]\nAm      F\nhello world\n{soc: Finale}\n[Am]hello\n{eoc}")
                check(song.meta.title == "Test" && song.meta.key == "Am" && song.meta.capo == 2)
                check(song.body.contains("[Am]hello wo[F]rld"))
                check(song.body.contains("# Finale"))
                check(!song.body.contains("Difficulty"))
            },
        )
        var failed = 0
        tests.forEachIndexed { index, (name, block) ->
            val status = Bundle().apply {
                putString("class", javaClass.name); putString("test", name)
                putInt("numtests", tests.size); putInt("current", index + 1)
            }
            sendStatus(1, status)
            try { block(); sendStatus(0, status) }
            catch (e: Throwable) {
                failed++
                status.putString("stack", e.stackTraceToString())
                sendStatus(-2, status)
            }
        }
        finish(if (failed == 0) -1 else 0, Bundle().apply { putString("stream", "\nStagePilot device checks: ${tests.size - failed}/${tests.size} passed\n") })
    }
}
