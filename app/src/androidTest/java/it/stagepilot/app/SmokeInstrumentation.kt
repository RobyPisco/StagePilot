package it.stagepilot.app

import android.app.Instrumentation
import android.os.Bundle
import it.stagepilot.app.core.prepareSong
import it.stagepilot.app.data.*
import android.content.ContextWrapper
import java.io.File

/** Device checks deliberately use their own disposable database, never the library. */
class SmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val tests = listOf<Pair<String, () -> Unit>>(
            "dossierRoundTripAndIndependentFields" to {
                val name="stagepilot-dossier-smoke.sqlite"
                targetContext.deleteDatabase(name)
                val sql=LibraryDatabase(targetContext,name)
                try {
                    val original=seed();val gig=Gig("gig-test","Dossier test",address="Via esempio",arrival="17:30",soundcheck="18:00",contacts=listOf(GigContact("c1","Fonico","Audio")),checklist=listOf(GigCheck("t1","Cavi","Basso",true)),stagePlot=listOf(StagePosition("p1","Basso","Basso",20.0,80.0)),inputs=listOf(GigInput("i1","1","Basso","DI")))
                    val db=original.copy(bands=original.bands.map{it.copy(events=listOf(gig))})
                    sql.save(db,null);check(sql.load()==db)
                    val decoded=decodeBackup(encodeBackup(db));check(decoded.band.events.first()==gig)
                    val renamed=gig.copy(title="Serata aggiornata");check(renamed.stagePlot==gig.stagePlot&&renamed.checklist.first().done)
                }finally{sql.close();targetContext.deleteDatabase(name)}
            },
            "bandLiveWithoutPcAndOfflineCache" to {
                val folder=File(targetContext.cacheDir,"band-live-smoke").apply{mkdirs()}
                val isolated=object:ContextWrapper(targetContext){override fun getFilesDir():File=folder}
                val original=seed();val library=LiveRoom.snapshot(original.band,original.band.curSet!!)
                val room=LiveRoom(library,"123456");var server=LiveRoomServer(room,0);server.start(2000,false)
                val port=server.listeningPort
                val client=BandLiveController(isolated)
                fun awaitCondition(block:()->Boolean){val deadline=System.currentTimeMillis()+7000;while(!block()&&System.currentTimeMillis()<deadline)Thread.sleep(50);check(block()){"Timed out waiting for native Band Live"}}
                try {
                    client.join("127.0.0.1:$port","123456",original)
                    awaitCondition{client.status.value.cue!=null&&client.status.value.connected}
                    check(client.status.value.library!!.db.band.events.isEmpty())
                    room.publish(1,true,true,"Chorus",1)
                    awaitCondition{client.status.value.cue?.index==1&&client.status.value.cue?.playing==true}
                    val songId=library.db.band.songs.first().id
                    client.updateSong(songId){it.copy(tr=3)}
                    check(client.status.value.library!!.db.band.song(songId)!!.tr==3)
                    check(library.db.band.song(songId)!!.tr==0)
                    server.stop();awaitCondition{!client.status.value.connected}
                    check(client.status.value.library!=null)
                    server=LiveRoomServer(room,port);server.start(2000,false)
                    awaitCondition{client.status.value.connected}
                    client.reopenCache();check(client.status.value.library!=null&&!client.status.value.following)
                }finally{client.close();server.stop();File(folder,"band-live-cache.json").delete();File(folder,"band-live-cache.json.tmp").delete();folder.delete()}
            },
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
