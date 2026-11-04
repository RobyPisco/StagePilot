package it.backline.app.data

import it.backline.app.core.setAsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTest {
    @Test fun backupRoundTrip() {
        val db = seed()
        val back = decodeBackup(encodeBackup(db))
        assertEquals(db.bands[0].songs.map { it.title }, back.bands[0].songs.map { it.title })
        assertEquals(db.bands[0].sets.map { it.items.size }, back.bands[0].sets.map { it.items.size })
        assertEquals(db.bands[0].songs[0].bpm, back.bands[0].songs[0].bpm)
    }

    /** Un database v4 come lo scrive l'app web: bpm "" o assente, campi mancanti, null. */
    @Test fun readsWhatTheWebAppWrites() {
        val web = """
        {"v":4,"curBand":"b1","opt":{"notation":"it","accid":"auto","fs":22,"bar":true,"timer":true,"caps":false,"roots":false,"plain":false,"sort":"add"},
         "bands":[{"id":"b1","name":"COPYCUT","me":"Basso","cur":"set1",
           "songs":[{"id":"s1","title":"Ultimo Giro","key":"Am","bpm":142,"dur":"3:20","tags":["x"],"notes":{"Tutti":"ciao"},"tr":0,"capo":0,"scroll":1,"lead":8,"body":"[Am]ciao"},
                    {"id":"s2","title":"Senza bpm","bpm":"","tags":[],"notes":{}}],
           "sets":[{"id":"set1","name":"Serata","items":[{"type":"song","id":"s1"},{"type":"mark","label":"Pausa","dur":"15:00"},{"type":"song","id":"fantasma"}]}]}]}
        """.trimIndent()
        val db = decodeBackup(web)
        assertEquals(142, db.bands[0].songs[0].bpm)
        assertNull(db.bands[0].songs[1].bpm)
        assertEquals("it", db.opt.notation)
        // il brano che non esiste più sparisce dalla scaletta, il segnaposto resta
        assertEquals(2, db.bands[0].sets[0].items.size)
    }

    @Test fun normalizeRepairsBrokenData() {
        val broken = DB(
            curBand = "boh",
            bands = listOf(
                Band(
                    id = "b1", name = "", me = "Ruolo-inventato", cur = "nonesiste",
                    songs = listOf(Song(id = "a", tr = 99, capo = -3, scroll = 99.0, lead = 500)),
                    sets = emptyList(),
                ),
            ),
            opt = Options(notation = "xx", accid = "yy", fs = 999),
        )
        val ok = normalizeDb(broken)
        assertEquals("b1", ok.curBand)
        assertEquals("Tutti", ok.bands[0].me)
        assertTrue(ok.bands[0].name.isNotBlank())
        assertEquals(1, ok.bands[0].sets.size)
        assertEquals(ok.bands[0].sets[0].id, ok.bands[0].cur)
        val s = ok.bands[0].songs[0]
        assertEquals(11, s.tr); assertEquals(0, s.capo); assertEquals(4.0, s.scroll, 0.0); assertEquals(60, s.lead)
        assertEquals("en", ok.opt.notation); assertEquals("auto", ok.opt.accid); assertEquals(48, ok.opt.fs)
    }

    @Test fun emptyDatabaseStillWorks() {
        val ok = normalizeDb(DB())
        assertEquals(1, ok.bands.size)
        assertEquals(1, ok.bands[0].sets.size)
    }

    @Test fun mergeGivesNewIds() {
        val a = seed()
        var n = 0
        val merged = mergeAsNewBands(a, seed()) { "b${n++}" }
        assertEquals(2, merged.bands.size)
        assertNotEquals(merged.bands[0].id, merged.bands[1].id)
        assertEquals(a.bands[0].id, merged.bands[0].id)
        // stesso nome: il secondo gruppo si distingue
        assertEquals(listOf("COPYCUT", "COPYCUT (2)"), merged.bands.map { it.name })
        val third = mergeAsNewBands(merged, seed()) { "b${n++}" }
        assertEquals("COPYCUT (3)", third.bands[2].name)
    }

    @Test fun setAsTextMatchesTheWebApp() {
        val b = seed().bands[0]
        val text = setAsText(b.entries(), b.name, b.curSet!!.name) { it.key }
        val lines = text.split("\n")
        assertEquals("COPYCUT — Serata tipo", lines[0])
        assertEquals("3 brani · 1 pausa · 26:10", lines[1])
        assertEquals("01. Amore Disperato — Em — 3:40", lines[3])
        assertEquals("--- Pausa (15:00)", lines[5])
        assertEquals("03. Still Burning — D — 4:10", lines[6])
    }

    @Test fun tagsAreParsedLikeTheWebApp() {
        assertEquals(listOf("a", "b"), parseTags(" A, b ,a,, "))
    }
}
