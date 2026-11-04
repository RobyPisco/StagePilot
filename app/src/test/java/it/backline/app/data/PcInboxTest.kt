package it.backline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcInboxTest {
    private val pkg = """
        {"backline":"songs","v":1,"songs":[
          {"title":"Who Knew","artist":"P!nk","key":"La","bpm":145,"dur":"3:28","capo":0,"lead":4,
           "tags":["cover"],"notes":{"Tutti":"Attacco secco"},"body":"# Strofa\n[Do]Ciao [Sol]mondo","inSet":true},
          {"title":"Senza bpm","bpm":null,"body":"[Am]x","inSet":false},
          {"title":"","body":"[Am]senza titolo"},
          {"title":"Senza testo","body":"  "}
        ]}
    """.trimIndent()

    @Test fun packageIsRecognisedAndFiltered() {
        val songs = parsePcPackage(pkg)!!
        // senza titolo o senza testo non servono a niente: si scartano
        assertEquals(listOf("Who Knew", "Senza bpm"), songs.map { it.title })
        assertEquals(145, songs[0].bpm)
        assertNull(songs[1].bpm)
    }

    @Test fun plainTabIsNotAPackage() {
        assertNull(parsePcPackage("Tuning: E A D G B E\n[Intro]\ne|---0---|"))
        assertNull(parsePcPackage("{title: Ciao}\n[Am]ciao"))
        assertNull(parsePcPackage("""{"altro":"json"}"""))
        assertNull(parsePcPackage("{non json"))
    }

    @Test fun italianKeyAndChordsAreStoredInEnglish() {
        val s = parsePcPackage(pkg)!![0].toSong("s1")
        assertEquals("A", s.key)
        assertEquals("# Strofa\n[C]Ciao [G]mondo", s.body)
        assertEquals(mapOf("Tutti" to "Attacco secco"), s.notes)
        assertEquals(145, s.bpm)
    }

    @Test fun inboxReplyFromTheServer() {
        val reply = """{"songs":[{"id":"ab12","title":"Prova PC","artist":"Io","key":"Am","bpm":100,"dur":"3:00","capo":0,"lead":2,
            "tags":["x"],"notes":{"Tutti":"ciao"},"body":"# Strofa\n[Am]Ciao [F]mondo","inSet":true}]}"""
        val songs = parsePcInbox(reply)
        assertEquals(1, songs.size)
        assertEquals("ab12", songs[0].id)
        assertEquals(2, songs[0].lead)
    }

    @Test fun addingPutsSongsInTheBandAndInTheSetOnlyIfAsked() {
        val db = seed()
        var n = 0
        val before = db.band.songs.size
        val setBefore = db.band.curSet!!.items.size
        val out = addPcSongs(db, parsePcPackage(pkg)!!) { "$it${n++}" }
        assertEquals(before + 2, out.band.songs.size)
        // solo il primo chiedeva la scaletta
        assertEquals(setBefore + 1, out.band.curSet!!.items.size)
        val added = out.band.song(out.band.curSet!!.items.last().id)!!
        assertEquals("Who Knew", added.title)
        // gli altri gruppi e le altre scalette non si toccano
        assertEquals(db.band.sets[1].items, out.band.sets[1].items)
    }

    @Test fun baseUrlAcceptsWhatPeopleType() {
        assertEquals("http://192.168.178.32:8765", PcClient.baseUrl("192.168.178.32:8765"))
        assertEquals("http://192.168.178.32:8765", PcClient.baseUrl(" http://192.168.178.32:8765/ "))
        assertEquals("http://192.168.178.32:8765", PcClient.baseUrl("192.168.178.32"))
        assertTrue(PcClient.baseUrl("pc-roberto:9000").endsWith(":9000"))
    }
}
