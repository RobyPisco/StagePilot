package it.stagepilot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.stagepilot.app.core.mmss
import it.stagepilot.app.core.preparation
import it.stagepilot.app.data.*
import java.time.LocalDate

@Composable fun ConcertsScreen(db: DB, store: Store, onStage: (String) -> Unit, onPractice: (String) -> Unit, onSettings: () -> Unit) {
    val band = db.band
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Gig?>(null) }
    var dossierId by remember { mutableStateOf<String?>(null) }
    dossierId?.let { id -> band.events.firstOrNull { it.id == id }?.let { gig ->
        GigDossierScreen(gig, store) { dossierId = null }
        return
    } }
    Column(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 25.dp)) {
        BrandBar(band.name, onSettings)
        LargeTitle("Ogni serata, preparata.", "Una data, una scaletta e quello che resta da fare.")
        Column(Modifier.padding(horizontal = 20.dp)) {
            AmberButton("+ Prepara una serata") { creating = true }
            if (band.events.isEmpty()) Text("Aggiungi un concerto o una prova aperta. StagePilot raccoglie la scaletta, i passaggi ancora da risolvere e gli appunti per arrivare pronti.", color = Palco.Dim, fontFamily = Sans, fontSize = 16.sp, modifier = Modifier.padding(vertical = 24.dp))
            band.events.sortedBy { it.date + it.time }.forEach { gig ->
                val set = band.sets.firstOrNull { it.id == gig.setId }
                val p = preparation(band, set)
                Column(Modifier.padding(top = 17.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palco.Panel).padding(22.dp)) {
                    Text("${gig.date}  ·  ${gig.time}  ·  ${gig.location}", color = Palco.Amber, fontFamily = Sans, fontSize = 13.sp)
                    Text(gig.title, color = Palco.Ink, fontFamily = Sans, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 11.dp))
                    Text("${set?.name ?: "Scaletta da scegliere"} · ${p.tracks} brani · ${mmss(p.duration)}${if (p.missingDurations > 0) " + durate mancanti" else " minuti"}", color = Palco.Dim, fontFamily = Sans, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    if (p.openPassages > 0) Text("• ${p.openPassages} passaggi ancora da risolvere", color = Palco.Purple, fontFamily = Sans, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
                    if (p.pendingSongs > 0) Text("• ${p.pendingSongs} brani non ancora pronti", color = Palco.Dim, fontFamily = Sans, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    if (p.missingDurations > 0) Text("• ${p.missingDurations} durate da completare", color = Palco.Dim, fontFamily = Sans, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    if (gig.notes.isNotBlank()) Text(gig.notes, color = Palco.Ink, fontFamily = Sans, fontSize = 15.sp, modifier = Modifier.padding(top = 15.dp))
                    Row(Modifier.padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        GhostButton("Dossier") { dossierId = gig.id }
                        GhostButton("Modifica") { editing = gig }
                        if (p.openPassages > 0 || p.pendingSongs > 0) GhostButton("Prepara in prova") {
                            if (set != null) store.updateBand { it.copy(cur = set.id) }
                            onPractice(set?.id.orEmpty())
                        }
                        if (set != null) GhostButton("Sul palco") { onStage(set.id) }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        val old = editing
        var title by remember { mutableStateOf(old?.title ?: "") }
        var date by remember { mutableStateOf(old?.date ?: LocalDate.now().toString()) }
        var time by remember { mutableStateOf(old?.time ?: "21:00") }
        var venue by remember { mutableStateOf(old?.location ?: "") }
        var setId by remember { mutableStateOf(old?.setId ?: band.curSet?.id.orEmpty()) }
        var notes by remember { mutableStateOf(old?.notes ?: "") }
        val valid = title.isNotBlank() && runCatching { LocalDate.parse(date) }.isSuccess && Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(time)
        PalcoDialog(if (old == null) "Prepara una serata" else "La tua serata", "Salva", if (valid) ({
            val next=(old ?: Gig(store.newId("gig"), "")).copy(title = title.trim(), date = date, time = time, location = venue.trim(), setId = setId, notes = notes.trim())
            if(old==null) store.saveEvent(next)
            if(old==null || store.saveEventDraft(old,next)) { creating = false; editing = null }
        }) else null, { creating = false; editing = null }) {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PalcoField(title, { title = it }, "Nome della serata")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PalcoField(date, { date = it }, "Data", Modifier.weight(1f), supporting = "AAAA-MM-GG")
                    PalcoField(time, { time = it }, "Inizio", Modifier.weight(1f), supporting = "21:00")
                }
                PalcoField(venue, { venue = it }, "Locale / luogo")
                Choice("Scaletta", band.sets.map { it.id to it.name }, setId) { setId = it }
                PalcoField(notes, { notes = it }, "Appunti della serata", singleLine = false, minLines = 2)
                if (old != null) GhostButton("Elimina serata", danger = true) { store.deleteEvent(old.id); editing = null }
            }
        }
    }
}
