package it.stagepilot.app.core

import it.stagepilot.app.data.Entry
import it.stagepilot.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/* Gli stessi casi dell'app web: la logica portata deve comportarsi uguale. */
class CoreTest {
    @Test fun transposeKeepsTextAndMovesChords() {
        assertEquals("Bm G D A", transpose("Am F C G", 2))
        assertEquals("Am F", transpose("Am F", 0))
        assertEquals("C | G", transpose("D | A", -2))
    }

    @Test fun isChordRejectsWords() {
        assertTrue(isChord("F#m7"))
        assertTrue(isChord("C/G"))
        assertTrue(isChord("Fadd9"))
        assertFalse(isChord("Break"))
        assertFalse(isChord("N.C."))
        assertFalse(isChord("Ciao"))
    }

    @Test fun spellItalianAndFlats() {
        assertEquals("Sol7", spell("G7", ital = true, flat = false))
        assertEquals("Sib", spell("A#", ital = true, flat = true))
        assertEquals("Bb", spell("A#", ital = false, flat = true))
        assertEquals("Break", spell("Break", ital = true, flat = false))
    }

    @Test fun flatsFollowTheKey() {
        assertTrue(useFlats("F"))
        assertTrue(useFlats("Dm"))
        assertTrue(useFlats("Bb"))
        assertFalse(useFlats("G"))
        assertFalse(useFlats("Em"))
    }

    @Test fun showChordsTransposesThenSpells() {
        assertEquals("Re", showChords("C", 2, ital = true, flat = false))
        assertEquals("Fa#m", showChords("Dm", 4, ital = true, flat = false))
    }

    @Test fun rootsUseTheBassNoteForInversions() {
        assertEquals("G", rootOf("C/G"))
        assertEquals("F#", rootOf("F#m7"))
        assertEquals("C", showChords("C/G", 0, false, false, roots = false).substringBefore('/'))
    }

    @Test fun italianInputBecomesEnglish() {
        assertEquals("F", toEnglish("Fa"))
        assertEquals("Fadd9", toEnglish("Fadd9"))
        assertEquals("C/G", toEnglish("Do/Sol"))
        assertEquals("La sala chiude [C]", "La sala chiude [Do]".let { normalizeBody(it) })
    }

    @Test fun romanCapo() {
        assertEquals("III", roman(3))
        assertEquals("", roman(0))
    }

    @Test fun parserSplitsChordsAndText() {
        val s = segs("[Am]Ultimo giro, il [F]bicchiere")
        assertEquals(listOf(Seg("Am", "Ultimo giro, il "), Seg("F", "bicchiere")), s)
        assertEquals(listOf(Seg(null, "solo testo")), segs("solo testo"))
    }

    @Test fun sheetModes() {
        val body = "# Strofa\n[Am]Ciao [F]mondo\n[C]  [G]"
        val full = renderSheet(body)
        assertEquals(SheetLine.Section("Strofa"), full[0])
        val struct = renderSheet(body, SheetOpts(struct = true))
        assertTrue((struct[1] as SheetLine.Lyric).segs.all { it.text.isEmpty() })
        val plain = renderSheet(body, SheetOpts(plain = true))
        assertEquals(2, plain.size)   // sezione + riga cantata: i soli accordi spariscono
    }

    @Test fun speedReachesTheEndWhenTheSongEnds() {
        // 1000 px in 100 s di brano utile: 10 px/s
        assertEquals(10f, speedFor(1000f, 108, 1.0, 8), 0.001f)
        assertEquals(0f, speedFor(0f, 100), 0f)
        assertEquals(20f, speedFor(1000f, 0), 0.001f)
    }

    @Test fun chartLengthFromBpm() {
        // 4 accordi su una riga di soli accordi = 4 battute; a 120 BPM una battuta dura 2 secondi
        assertEquals(8.0, estimateChartSeconds("[Am] [F] [C] [G]", 120)!!, 0.001)
        // una riga cantata con un solo accordo vale due battute
        assertEquals(4.0, estimateChartSeconds("[Am]Ciao a tutti", 120)!!, 0.001)
        // con tre accordi vale tre battute; sezioni e righe vuote non contano
        assertEquals(6.0, estimateChartSeconds("# Strofa\n\n[Am]Ciao [F]a [C]tutti", 120)!!, 0.001)
        // senza BPM o senza accordi non si può stimare
        assertNull(estimateChartSeconds("[Am]Ciao", null))
        assertNull(estimateChartSeconds("[Am]Ciao", 0))
        assertNull(estimateChartSeconds("# solo sezione", 120))
        // più veloce il brano, più corto il chart nel tempo
        assertTrue(estimateChartSeconds("[Am]Ciao", 200)!! < estimateChartSeconds("[Am]Ciao", 60)!!)
    }

    @Test fun speedBasisPrefersBpmThenDuration() {
        assertEquals(SpeedBasis.BPM, speedBasis("[Am]Ciao", 120, 200).first)
        assertEquals(SpeedBasis.DURATION, speedBasis("[Am]Ciao", null, 200).first)
        assertEquals(SpeedBasis.FIXED, speedBasis("[Am]Ciao", null, 0).first)
        // dai BPM: 1000 px da percorrere in 4 s di chart = 250 px/s; la correzione la moltiplica
        assertEquals(250f, speedFor(1000f, SpeedBasis.BPM to 4.0), 0.01f)
        assertEquals(500f, speedFor(1000f, SpeedBasis.BPM to 4.0, factor = 2.0), 0.01f)
        // dai BPM l'attesa iniziale non si sottrae
        assertEquals(250f, speedFor(1000f, SpeedBasis.BPM to 4.0, lead = 3), 0.01f)
        // dalla durata sì, come prima
        assertEquals(10f, speedFor(1000f, SpeedBasis.DURATION to 108.0, lead = 8), 0.01f)
    }

    @Test fun speedStepsGoUpAndDownAndStayInRange() {
        assertEquals(1.2, stepSpeed(1.0, +1), 0.0)
        assertEquals(0.9, stepSpeed(1.0, -1), 0.0)
        assertEquals(12.0, stepSpeed(12.0, +1), 0.0)
        assertEquals(0.25, stepSpeed(0.25, -1), 0.0)
        // un valore salvato fuori dai gradini (da un backup web) riparte dal più vicino
        assertEquals(1.5, stepSpeed(1.3, +1), 0.0)
        assertEquals("1", speedLabel(1.0))
        assertEquals("1.5", speedLabel(1.5))
        assertEquals("0.25", speedLabel(0.25))
        assertEquals("12", speedLabel(12.0))
    }

    @Test fun durationsAndOrdinals() {
        val e = listOf(
            Entry.Track(Song("a", dur = "3:20")),
            Entry.Mark("Pausa", "15:00"),
            Entry.Track(Song("b", dur = "4:10")),
        )
        assertEquals(200 + 900 + 250, setDuration(e))
        assertEquals(listOf(1, 0, 2), ordinals(e))
        assertEquals("3:20", mmss(200))
    }
}
