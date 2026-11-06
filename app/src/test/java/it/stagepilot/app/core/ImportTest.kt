package it.stagepilot.app.core

import org.junit.Assert.*
import org.junit.Test

class ImportTest {
    @Test fun pastePreservesWordsAndExtractsMetadata() {
        val input = """Test song chords by Example Band
Tuning: E A D G B E Key: Am Capo: 2
Difficulty: beginner
Last update: today
[Verse]
Am      F
hello world
"""
        val p = prepareSong(input)
        assertEquals("Test song", p.meta.title)
        assertEquals("Example Band", p.meta.artist)
        assertEquals("Am", p.meta.key)
        assertEquals(2, p.meta.capo)
        assertEquals(4, p.removed)
        assertTrue(p.body.contains("[Am]hello wo[F]rld"))
        assertFalse(p.body.contains("Difficulty"))
    }
    @Test fun guitarTabIsOptionalAndLyricsAreKept() {
        val raw = "[Intro]\ne|---0---2---|\n\n[Verse]\nAm  F\nhello world"
        assertFalse(prepareSong(raw).body.contains("e|"))
        assertFalse(prepareSong(raw).body.contains("Intro"))
        assertTrue(prepareSong(raw, true).body.contains("e|---0---2---|"))
    }
    @Test fun chordProLabelsAndTimingSurvive() {
        val p = prepareSong("{title: Test}\n{tempo: 123}\n{duration: 3:42}\n{soc: Final chorus}\n[Am]hello\n{eoc}")
        assertEquals(123, p.bpm)
        assertEquals("3:42", p.duration)
        assertTrue(p.body.startsWith("# Final chorus"))
    }
    @Test fun repeatsExpandAndPlainLyricsAreAllowed() {
        assertTrue(prepareSong("| Am | % | G |").body.contains("[Am]"))
        assertEquals("hello world", prepareSong("hello world").body)
        assertEquals("", prepareSong("Difficulty: easy").body)
    }
    @Test fun isolatedChordProChordIsMusicRatherThanASection() {
        assertEquals("# Intro\n[Am]",prepareSong("[Intro]\n[Am]").body)
    }
}
