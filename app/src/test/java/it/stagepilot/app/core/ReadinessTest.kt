package it.stagepilot.app.core
import it.stagepilot.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ReadinessTest {
    @Test fun concertPreparationCountsOnlyItsOwnSongsAndPendingPassages() {
        val set = SetList("set", "Live", listOf(SetItem("song", id="a"), SetItem("mark", label="Pausa", dur="5:00"), SetItem("song", id="a"), SetItem("song", id="b")))
        val band = Band("b", "Band", songs=listOf(Song("a",dur="3:00",status="ready"),Song("b",dur="",status="study"),Song("c",status="study")), sets=listOf(set),practiceTasks=listOf(PracticeTask("t1","a",goal="Finale"),PracticeTask("t2","b",goal="Bridge",done=true),PracticeTask("t3","c",goal="Fuori set")))
        val p=preparation(band,set)
        assertEquals(3,p.tracks);assertEquals(660,p.duration)
        assertEquals(1,p.pendingSongs);assertEquals(1,p.openPassages);assertEquals(1,p.missingDurations)
    }
    @Test fun removingAnImportedSongCannotLeaveDanglingPracticeWork() {
        val b=Band("b","Band",songs=listOf(Song("a")),practiceTasks=listOf(PracticeTask("t1","a",goal="Finale"),PracticeTask("t2","missing",goal="Bridge")))
        assertEquals(listOf("t1"),normalizeBand(b).practiceTasks.map { it.id })
    }
    @Test fun focusingAPassageNeverRewritesTheWholeChart() {
        val body="# Strofa\n[Am]hello\n# Ritornello\n[F]world\n# Finale\n[C]end"
        assertEquals("# Ritornello\n[F]world",passageBody(body,"Ritornello"))
        assertEquals(body,passageBody(body,"Missing"))
        assertEquals(body,passageBody(body,"Brano intero"))
        assertEquals(listOf("Strofa","Ritornello","Finale"),songSections(body))
    }
}
