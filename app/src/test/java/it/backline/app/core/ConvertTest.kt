package it.backline.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/* Gli stessi casi del convertitore dell'app web. */
class ConvertTest {
    @Test fun chordLines() {
        assertTrue(isChordLine("Am     C       G"))
        assertFalse(isChordLine("Am     C       Grease"))
        assertFalse(isChordLine("e|--0--2--3--"))
        assertFalse(isChordLine(""))
    }

    @Test fun annotationsDoNotBreakChordLines() {
        assertTrue(isChordLine("C           G        Am   x2"))
        assertTrue(isChordLine("Am  F  2x"))
        assertTrue(isChordLine("N.C.        F"))
        assertTrue(isChordLine("C  (let ring)   G"))
        assertTrue(isChordLine("Am | F | C"))
        assertFalse(isChordLine("x2"))
        assertTrue(isChordLine("Cmaj7(add9)  F"))
        assertFalse(isChordLine("C  (let ring)   Grease"))
    }

    @Test fun wrapInPlace() {
        assertEquals("[Am]     [C]   x2", wrapChordsInPlace("Am     C   x2"))
        assertEquals("[Am]     [C]       [G]", wrapChordsInPlace("Am     C       G"))
    }

    @Test fun mergeAtTheRightColumn() {
        assertEquals("Ciao [F]bella", mergeChordLyric("N.C. F", "Ciao bella"))
        assertEquals("[Am]Ciao [F]bella", mergeChordLyric("Am   F", "Ciao bella"))
        assertEquals("Hey  [G]", mergeChordLyric("     G", "Hey"))
        assertEquals("Hey       [G]", mergeChordLyric("          G", "Hey"))
        assertEquals("[Am]Ciao [F]", mergeChordLyric("Am   F", "Ciao"))
        assertEquals("[Am]", mergeChordLyric("Am", ""))
        assertEquals("  [Am]", mergeChordLyric("  Am", ""))
        assertEquals("[G][C]Hey", mergeChordLyric(listOf("G", "C"), "Hey"))
    }

    @Test fun columnsSurviveTheMerge() {
        fun cols(s: String): List<Int> {
            val out = ArrayList<Int>(); var len = 0
            var rest = s
            while (rest.isNotEmpty()) {
                val m = Regex("""\[[^\]]+]""").find(rest)
                if (m == null) { len += rest.length; break }
                len += m.range.first; out.add(len); rest = rest.substring(m.range.last + 1)
            }
            return out
        }
        assertEquals(listOf(5, 8), cols(mergeChordLyric(" ".repeat(5) + "G  C", "Hey")))
        assertEquals(listOf(0, 10), cols(mergeChordLyric("Am" + " ".repeat(8) + "F", "Ciao")))
        assertEquals(listOf(2, 8), cols(mergeChordLyric("  Am    F", "Ciao bella")))
    }

    @Test fun fullConversion() {
        assertEquals("[C]Sempre di [G]corsa", tabToChordPro("C" + " ".repeat(9) + "G   x2\nSempre di corsa"))
        assertEquals("# Chorus\n[Am]Ciao [F]bella", tabToChordPro("[Chorus]\nAm   F\nCiao bella"))
        assertEquals(
            "[Am]Tanto il [F]giorno [C]non [G]arriva mai",
            tabToChordPro("Am" + " ".repeat(7) + "F" + " ".repeat(6) + "C" + " ".repeat(3) + "G\nTanto il giorno non arriva mai"),
        )
        assertEquals("Intro\n[Am]  [F]  [C]  [G]", tabToChordPro("Intro\nAm  F  C  G"))
        val guitarTab = "e|-----0-----2-----|\nB|-----1-----3-----|"
        assertEquals(guitarTab, tabToChordPro(guitarTab))
    }

    @Test fun stackedFragmentsMergeTogether() {
        assertEquals("[Ab][B]Whoa[Db] la la", tabToChordPro("Ab\nB   Db\nWhoa la la"))
        assertEquals("[Am] [F] [C]\n[G][C]La la la la", tabToChordPro("Am F C\nG\nC\nLa la la la"))
    }

    @Test fun chordProDirectivesBecomeSections() {
        assertEquals(Directive("title", "Ciao"), chordProDirective("{title: Ciao}"))
        assertNull(chordProDirective("[Am]non direttiva"))
        assertEquals("# Chorus\n[Am]Ciao", tabToChordPro("{soc}\n[Am]Ciao\n{eoc}"))
        assertEquals("# Nota\n[Am]Ciao", tabToChordPro("{c: Nota}\n[Am]Ciao"))
    }

    @Test fun metaFromChordPro() {
        val m = chordProMeta("{title: Ultimo Giro}\n{artist: COPYCUT}\n{key: Am}\n[Am]Ciao")
        assertEquals(Meta("Ultimo Giro", "COPYCUT", "Am"), m)
    }

    @Test fun guitarTabLinesAreRecognised() {
        assertTrue(isTabLine("e|-----------------|-----------------|"))
        assertTrue(isTabLine("G|-6---6-6---6-6-6-|-9---9-9---9-9-9-| x2"))
        assertTrue(isTabLine("  D|-7---7-7---7-7-7-|"))
        assertFalse(isTabLine("Am F C G"))
        assertFalse(isTabLine("[Am]Ciao | bella"))
        assertFalse(isTabLine("A | F | C"))
        assertFalse(isTabLine(""))
    }

    @Test fun tabLinesKeepTheirShapeAndSkipPlainMode() {
        val body = "# Intro\ne|---0---|\nG|-6---6-|\n[Am]Ciao"
        val lines = renderSheet(body)
        assertEquals(SheetLine.Tab("e|---0---|"), lines[1])
        assertEquals(SheetLine.Tab("G|-6---6-|"), lines[2])
        // chi canta non vuole la tablatura
        assertTrue(renderSheet(body, SheetOpts(plain = true)).none { it is SheetLine.Tab })
    }

    @Test fun siteHeaderGivesKeyAndCapoAndIsDropped() {
        val txt = "Tuning: E A D G B EKey: ACapo: No capo\n\n[Verse 1]\nA\n  You took my hand"
        assertEquals(Meta(key = "A", capo = 0), chordProMeta(txt))
        assertEquals(Meta(key = "Am", capo = 3), chordProMeta("Tuning: E A D G B E\nKey: Am\nCapo: 3\n[Am]x"))
        assertTrue(!tabToChordPro(txt).contains("Tuning"))
        assertTrue(tabToChordPro(txt).startsWith("# Verse 1"))
    }

    @Test fun realTabWithTabLinesAndSections() {
        val tab = """
Tuning: E A D G B EKey: ACapo: No capo

[Intro]

e|-----------------|-----------------|
G|-6---6-6---6-6-6-|-9---9-9---9-9-9-| x2

[Verse 1]
A
  You took my hand,  You showed me how
                              Bm      F#m          E
  You promised me you'd be around, Uh huh,  that's right
""".trimIndent()
        val out = tabToChordPro(tab)
        assertTrue(out.contains("# Intro"))
        assertTrue(out.contains("e|-----------------|-----------------|"))
        assertTrue(out.contains("# Verse 1"))
        // accordo di una riga e testo sotto: fusi
        assertTrue(out.contains("[A]  You took my hand,  You showed me how"))
        // gli accordi sopra la seconda riga cadono dentro le parole
        assertTrue(out.contains("[Bm]") && out.contains("[F#m]") && out.contains("[E]"))
        assertEquals("A", chordProMeta(tab).key)
    }

    @Test fun rawTabDetection() {
        assertTrue(looksRawTab("Am  F  C\nCiao bella"))
        assertFalse(looksRawTab("[Am]Ciao [F]bella"))
        assertFalse(looksRawTab("Ciao bella\ncome stai"))
    }

    @Test fun convertingTwiceChangesNothing() {
        val once = tabToChordPro("[Verse]\nAm" + " ".repeat(7) + "F\nTanto il giorno\n\nC  G\nLa la")
        assertEquals(once, tabToChordPro(once))
    }
}
