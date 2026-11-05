package it.backline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcEditsTest {
    private fun song(id: String, title: String) =
        Song(id = id, title = title, artist = "Band", key = "Am", bpm = 120, dur = "3:00", body = "# Strofa\n[Am]ciao [F]mondo")

    private val items = listOf(SetItem("song", id = "s1"), SetItem("mark", label = "Pausa", dur = "10:00"), SetItem("song", id = "s2"))

    private fun db() = DB(
        bands = listOf(Band(id = "b1", name = "Mia Band", songs = listOf(song("s1", "Uno"), song("s2", "Due"), song("s3", "Tre")),
            sets = listOf(SetList("t1", "Serata", items)), cur = "t1")),
        curBand = "b1",
    )

    private val base get() = items.map(::itemSignature)

    @Test fun songFieldsChangeAndTheRestStays() {
        val r = applyEdits(db(), listOf(PcEdit(op = "song", id = "s1", title = "Uno bis", key = "Do", bpm = 99, tags = listOf("cover"))))
        val s = r.db.band.songs.first { it.id == "s1" }
        assertEquals(1, r.applied)
        assertEquals("Uno bis", s.title)
        assertEquals("C", s.key)                      // la tonalità italiana si salva in inglese
        assertEquals(99, s.bpm)
        assertEquals("3:00", s.dur)                   // non nominato: invariato
        assertEquals("# Strofa\n[Am]ciao [F]mondo", s.body)
    }

    @Test fun bpmZeroClearsItAndOutOfRangeIsIgnored() {
        assertNull(applyEdits(db(), listOf(PcEdit(op = "song", id = "s1", bpm = 0))).db.band.songs[0].bpm)
        assertNull(applyEdits(db(), listOf(PcEdit(op = "song", id = "s1", bpm = 9999))).db.band.songs[0].bpm)
    }

    @Test fun deleteRemovesTheSongFromEverySet() {
        val r = applyEdits(db(), listOf(PcEdit(op = "delete", id = "s2")))
        assertEquals(listOf("s1", "s3"), r.db.band.songs.map { it.id })
        assertEquals(listOf("song", "mark"), r.db.band.sets[0].items.map { it.type })
    }

    @Test fun unknownSongIsSkippedAndSaid() {
        val r = applyEdits(db(), listOf(PcEdit(op = "song", id = "zzz", title = "Fantasma")))
        assertEquals(0, r.applied)
        assertEquals(1, r.skipped.size)
        assertTrue(r.skipped[0].contains("Fantasma"))
    }

    @Test fun setIsReorderedWhenItIsAsThePcSawIt() {
        val nuovo = listOf(SetItem("song", id = "s2"), SetItem("mark", label = "Pausa", dur = "10:00"), SetItem("song", id = "s1"), SetItem("song", id = "s3"))
        val r = applyEdits(db(), listOf(PcEdit(op = "items", id = "t1", base = base, items = nuovo)))
        assertEquals(1, r.applied)
        assertEquals(listOf("s2", null, "s1", "s3"), r.db.band.sets[0].items.map { it.id })
    }

    @Test fun setChangedOnTheTabletIsNotOverwritten() {
        val vecchia = listOf("song:s1", "song:s2")          // il PC la vedeva senza la pausa
        val r = applyEdits(db(), listOf(PcEdit(op = "items", id = "t1", base = vecchia, items = listOf(SetItem("song", id = "s2")))))
        assertEquals(0, r.applied)
        assertEquals(3, r.db.band.sets[0].items.size)
        assertTrue(r.skipped[0].contains("Serata"))
    }

    @Test fun songsThatNoLongerExistDropOutOfTheNewOrder() {
        val nuovo = listOf(SetItem("song", id = "s1"), SetItem("song", id = "perso"))
        val r = applyEdits(db(), listOf(PcEdit(op = "items", id = "t1", base = base, items = nuovo)))
        assertEquals(listOf("s1"), r.db.band.sets[0].items.map { it.id })
    }

    @Test fun packageAndInboxAreRead() {
        val txt = """{"backline":"edits","v":1,"edits":[{"op":"delete","id":"s1"},{"op":""},{"op":"song","id":"s2","bpm":0,"title":"X"}]}"""
        assertEquals(listOf("delete", "song"), parseEditsPackage(txt)!!.map { it.op })
        assertNull(parseEditsPackage("""{"backline":"songs","songs":[]}"""))
        assertNull(parseEditsPackage("testo qualunque"))
        val inbox = parsePcInboxFull("""{"songs":[],"edits":[{"op":"delete","id":"s1"}],"editIds":["a1"]}""")
        assertEquals(listOf("a1"), inbox.editIds)
        assertEquals(1, inbox.edits.size)
    }

    @Test fun summaryNamesTheSongs() {
        val s = summarizeEdits(db(), listOf(PcEdit(op = "song", id = "s1"), PcEdit(op = "delete", id = "s3"), PcEdit(op = "items", id = "t1")))
        assertEquals(listOf("Uno"), s.changed)
        assertEquals(listOf("Tre"), s.deleted)
        assertEquals(listOf("Serata"), s.sets)
    }
}
