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

    @Test fun duplicatesAreSkipped() {
        val db = seed()
        var n = 0
        val once = addPcSongs(db, parsePcPackage(pkg)!!) { "$it${n++}" }
        // stesso pacchetto una seconda volta, con maiuscole e accenti diversi: non si aggiunge niente
        val again = parsePcPackage(pkg)!!.map { it.copy(title = it.title.uppercase()) }
        assertEquals(2, alreadyInBand(once, again).size)
        val twice = addPcSongs(once, again) { "$it${n++}" }
        assertEquals(once.band.songs.size, twice.band.songs.size)
        assertEquals(once.band.curSet!!.items.size, twice.band.curSet!!.items.size)
        // doppioni dentro lo stesso pacchetto
        val dup = parsePcPackage(pkg)!!.let { listOf(it[0], it[0]) }
        assertEquals(db.band.songs.size + 1, addPcSongs(db, dup) { "$it${n++}" }.band.songs.size)
    }
}

class SetlistPackageTest {
    private fun song(id: String, title: String, artist: String = "Band") =
        Song(id = id, title = title, artist = artist, key = "Am", bpm = 120, dur = "3:00", capo = 2,
            tags = listOf("cover"), notes = mapOf("Tutti" to "ciao"), body = "# Strofa\n[Am]ciao [F]mondo", tr = 3, scroll = 2.0)

    private fun band() = Band(
        id = "b1", name = "Mia Band", songs = listOf(song("s1", "Uno"), song("s2", "Due")),
        sets = listOf(SetList("t1", "Serata", listOf(
            SetItem("song", id = "s2"), SetItem("mark", label = "Pausa", dur = "15:00"),
            SetItem("song", id = "s1"), SetItem("song", id = "s2"), SetItem("song", id = "perso"),
        ))), cur = "t1",
    )

    @Test fun roundTripKeepsOrderMarksAndSongContent() {
        val b = band()
        val text = encodeSetlistPackage(buildSetlistPackage(b, b.sets[0]))
        val p = parseSetlistPackage(text)!!
        assertEquals("Serata", p.name)
        assertEquals(listOf("Due", "Uno"), p.songs.map { it.title })      // senza ripetizioni
        assertEquals(listOf("song", "mark", "song", "song"), p.items.map { it.type })
        assertEquals(listOf(0, null, 1, 0), p.items.map { it.ref })        // il brano perso non c'è
        assertEquals(2, p.songs[0].capo)
        assertEquals("ciao", p.songs[0].notes["Tutti"])
    }

    @Test fun importAddsMissingSongsReusesKnownOnesAndNamesTheSetUniquely() {
        val b = band()
        val p = parseSetlistPackage(encodeSetlistPackage(buildSetlistPackage(b, b.sets[0])))!!
        // un altro gruppo: ha già "Uno", non ha "Due"
        val other = DB(bands = listOf(Band("x", "Altra", songs = listOf(song("o1", "UNO")), sets = listOf(SetList("ot", "Serata")), cur = "ot")), curBand = "x")
        val info = setlistImportInfo(other, p)
        assertEquals(1, info.newSongs); assertEquals(1, info.knownSongs)
        var n = 0
        val out = importSetlist(other, p) { "$it${n++}" }
        assertEquals(2, out.band.songs.size)                               // "Uno" non si duplica
        assertEquals(listOf("Serata", "Serata (2)"), out.band.sets.map { it.name })
        val set = out.band.curSet!!
        assertEquals("Serata (2)", set.name)
        assertEquals(listOf("Due", null, "UNO", "Due"), set.items.map { out.band.song(it.id)?.title })
        assertEquals("Pausa", set.items[1].label)
        assertEquals(0, out.band.sets[0].items.size)                       // le altre scalette non si toccano
    }

    @Test fun otherTextIsNotASetlist() {
        assertNull(parseSetlistPackage("{\"backline\":\"songs\",\"songs\":[]}"))
        assertNull(parseSetlistPackage("[Am]ciao"))
        assertNull(parseSetlistPackage("{\"backline\":\"setlist\",\"songs\":[]}"))
        assertNull(parsePcPackage(encodeSetlistPackage(buildSetlistPackage(band(), band().sets[0]))))
    }
}
