package it.stagepilot.app.core

import it.stagepilot.app.data.Band
import it.stagepilot.app.data.SetList

data class Preparation(val tracks: Int, val duration: Int, val pendingSongs: Int, val openPassages: Int, val missingDurations: Int)
fun preparation(band: Band, set: SetList?): Preparation {
    val items = set?.items.orEmpty()
    val ids = items.filter { !it.isMark }.mapNotNull { it.id }.toSet()
    val songs = band.songs.filter { it.id in ids }
    return Preparation(items.count { !it.isMark },
        items.sumOf { if (it.isMark) secs(it.dur) else secs(band.song(it.id)?.dur) },
        songs.count { it.status != "ready" }, band.practiceTasks.count { !it.done && it.songId in ids },
        songs.count { secs(it.dur) == 0 })
}
