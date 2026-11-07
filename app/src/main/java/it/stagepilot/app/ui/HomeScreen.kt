package it.stagepilot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.stagepilot.app.core.mmss
import it.stagepilot.app.core.setDuration
import it.stagepilot.app.data.DB
import it.stagepilot.app.data.Entry
import it.stagepilot.app.data.entries
import it.stagepilot.app.core.preparation
import java.time.LocalDate

@Composable
fun HomeScreen(db: DB, onStage: (String) -> Unit, onImport: () -> Unit, onConcerts: () -> Unit, onPractice: () -> Unit, onSettings: () -> Unit, pcConfigured: Boolean, pcBusy: Boolean, pcStatus: String, onSync: () -> Unit) {
    val band = db.band
    val entries = band.entries()
    val ready = band.songs.count { it.status == "ready" }
    val gig = band.events.filter { it.date >= LocalDate.now().toString() }.minByOrNull { it.date + it.time }
    val showSet = band.sets.firstOrNull { it.id == gig?.setId } ?: band.curSet
    val prepared = preparation(band, showSet)
    val openTasks = band.practiceTasks.count { !it.done }
    Box(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 1000.dp).verticalScroll(rememberScrollState()).padding(bottom = 22.dp)) {
            BrandBar(band.name, onSettings)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                GhostButton(if (pcBusy) "Sincronizzazione in corso…" else if (pcConfigured) "Sincronizza con il PC" else "Collega lo studio PC", Modifier.fillMaxWidth()) {
                    if (!pcBusy) { if (pcConfigured) onSync() else onSettings() }
                }
                Text(pcStatus.ifBlank { "Dopo le modifiche sul web, tocca qui per aggiornare brani e scalette." }, color = Palco.Dim, fontFamily = Sans, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            }
            LargeTitle("La tua musica.\nIl tuo prossimo palco.", "Tutto pronto, un brano alla volta.")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palco.AmberDeep).padding(24.dp)) {
                    Text(gig?.let { "${it.date} · ${it.time} · ${it.location}" } ?: "LA TUA PROSSIMA SERATA", color = Palco.Amber, fontFamily = Sans, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text(gig?.title ?: showSet?.name ?: "Prepara la prima scaletta", color = Palco.Ink, fontFamily = Sans, fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
                    Text("${prepared.tracks} brani · ${mmss(prepared.duration)}${if (prepared.missingDurations > 0) " + durate da definire" else " minuti"}", color = Palco.Dim, fontFamily = Sans, fontSize = 14.sp, modifier = Modifier.padding(top = 9.dp, bottom = 22.dp))
                    AmberButton("▶  Vai sul palco", enabled = showSet?.items?.isNotEmpty() == true) { showSet?.let { onStage(it.id) } }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(band.songs.size.toString(), "brani in repertorio")
                    Stat(ready.toString(), "pronti da suonare")
                    Stat(band.sets.size.toString(), "scalette")
                }
                Text("La prossima prova", color = Palco.Ink, fontFamily = Sans, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palco.Panel).clickable(onClick = onPractice).padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Note, null, tint = Palco.Purple, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 15.dp)) {
                        Text(if (openTasks > 0) "Passaggi da risolvere: $openTasks" else "Prepara l’agenda della prova", color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Obiettivi, passaggi e decisioni della band.", color = Palco.Dim, fontFamily = Sans, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    Icon(Ic.Chevron, null, tint = Palco.Dim, modifier = Modifier.size(16.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Importa un brano", Modifier.weight(1f), onClick = onImport)
                    GhostButton("Prepara una serata", Modifier.weight(1f), onClick = onConcerts)
                }
            }
        }
    }
}

@Composable private fun Stat(value: String, label: String) {
    Column { Text(value, color = Palco.Ink, fontFamily = Sans, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text(label, color = Palco.Dim, fontFamily = Sans, fontSize = 12.sp) }
}
