package it.stagepilot.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.stagepilot.app.core.mmss
import it.stagepilot.app.core.songSections
import it.stagepilot.app.data.*
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun GuidedPracticeScreen(db: DB, store: Store, onPassage: (String, String) -> Unit, onSettings: () -> Unit, scopeSetId: String = "") {
    val band = db.band
    var taskDialog by remember { mutableStateOf<PracticeTask?>(null) }
    var newTask by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("open") }
    var scope by rememberSaveable(scopeSetId) { mutableStateOf(scopeSetId) }
    val focusedSet = band.sets.firstOrNull { it.id == scope }
    val scopedSongIds = focusedSet?.items?.mapNotNull { it.id }?.toSet()
    val scopedTasks = band.practiceTasks.filter { scopedSongIds == null || it.songId in scopedSongIds }
    var started by rememberSaveable { mutableStateOf(0L) }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var sessionIds by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(started) {
        if (started != 0L) while (true) { elapsed = ((SystemClock.elapsedRealtime() - started) / 1000).toInt(); delay(500) }
    }
    val pending = scopedTasks.filterNot { it.done }
    val shown = scopedTasks.filter { when (filter) { "mine" -> !it.done && it.role in listOf("Tutti", band.me); "done" -> it.done; else -> !it.done } }
    Column(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 22.dp)) {
        BrandBar(band.name, onSettings)
        LargeTitle("La prova, con un obiettivo.", "Passaggi precisi. Decisioni che non si perdono.")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palco.Panel).padding(22.dp)) {
                Text("AGENDA DELLA BAND", color = Palco.Purple, fontFamily = Sans, fontSize = 11.sp, letterSpacing = 1.5.sp)
                if (focusedSet != null) GhostButton("${focusedSet.name} · Mostra tutto") { scope = "" }
                Text("${pending.size} ${if (pending.size == 1) "passaggio" else "passaggi"} da risolvere", color = Palco.Ink, fontFamily = Sans, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 13.dp))
                Text("${pending.map { it.songId }.distinct().size} ${if (pending.map { it.songId }.distinct().size == 1) "brano coinvolto" else "brani coinvolti"} · ${band.practiceTasks.count { it.done }} passaggi risolti", color = Palco.Dim, fontFamily = Sans, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mmss(elapsed), color = Palco.Ink, fontFamily = Mono, fontSize = 28.sp)
                    GhostButton(if (started == 0L) "Inizia sessione" else "Termina sessione") {
                        if (started == 0L) {
                            elapsed = 0; started = SystemClock.elapsedRealtime()
                            sessionIds = pending.joinToString(",") { it.id }
                        } else {
                            elapsed = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()
                            val tasks = band.practiceTasks.filter { it.id in sessionIds.split(',') }
                            store.logRehearsal(Rehearsal(store.newId("r"), LocalDate.now().toString(), elapsed, tasks.map { it.songId }.distinct(), "${tasks.count { it.done }} passaggi risolti su ${tasks.size}"))
                            started = 0L
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AmberButton("+ Aggiungi un passaggio", enabled = band.songs.isNotEmpty()) { newTask = true }
            Row(Modifier.padding(vertical = 17.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Chip("Da risolvere", filter == "open") { filter = "open" }
                Chip("Per me", filter == "mine") { filter = "mine" }
                Chip("Risolti", filter == "done") { filter = "done" }
            }
            if (shown.isEmpty()) Column(Modifier.padding(vertical = 22.dp)) {
                Text(if (band.songs.isEmpty()) "Importa un brano per preparare la prima prova." else if (filter == "done") "Le decisioni prese resteranno qui, insieme ai passaggi risolti." else "Da dove partiamo?", color = Palco.Ink, fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Aggiungi un punto concreto: «allineare l’attacco del ritornello», «decidere il finale», «ripassare il bridge».", color = Palco.Dim, fontFamily = Sans, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
            }
            shown.forEach { task ->
                val song = band.song(task.songId) ?: return@forEach
                Column(Modifier.padding(bottom = 13.dp).fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Palco.Panel).padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(task.done, { v -> store.saveTask(task.copy(done = v)) })
                        Column(Modifier.weight(1f).clickable { taskDialog = task }) {
                            Text(task.goal, color = Palco.Ink, fontFamily = Sans, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text("${song.title} · ${task.section}", color = Palco.Amber, fontFamily = Sans, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                    Text("PER ${task.role.uppercase()}", color = Palco.Purple, fontFamily = Sans, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 9.dp))
                    if (task.decision.isNotBlank()) Text("Decisione: ${task.decision}", color = Palco.Ink, fontFamily = Sans, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        GhostButton("Apri il passaggio") { onPassage(song.id, task.section) }
                        GhostButton("Appunti") { taskDialog = task }
                    }
                }
            }
            band.rehearsals.takeLast(3).asReversed().forEach { s ->
                Text("${s.date} · ${mmss(s.seconds)} · ${s.notes.ifBlank { "${s.songIds.size} brani" }}", color = Palco.Dim, fontFamily = Sans, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
    if (newTask || taskDialog != null) TaskDialog(if (scopedSongIds == null) band else band.copy(songs = band.songs.filter { it.id in scopedSongIds }), taskDialog, onDismiss = { newTask = false; taskDialog = null }, onSave = { task ->
        store.saveTask(task); newTask = false; taskDialog = null
    }, onDelete = taskDialog?.let { t -> { store.deleteTask(t.id); taskDialog = null } }, newId = { store.newId("task") })
}

@Composable private fun TaskDialog(band: Band, task: PracticeTask?, onDismiss: () -> Unit, onSave: (PracticeTask) -> Unit, onDelete: (() -> Unit)?, newId: () -> String) {
    var songId by remember { mutableStateOf(task?.songId ?: band.songs.firstOrNull()?.id.orEmpty()) }
    var section by remember { mutableStateOf(task?.section ?: "Brano intero") }
    var goal by remember { mutableStateOf(task?.goal ?: "") }
    var role by remember { mutableStateOf(task?.role ?: "Tutti") }
    var decision by remember { mutableStateOf(task?.decision ?: "") }
    PalcoDialog(if (task == null) "Prepara un passaggio" else "Passaggio e decisioni", "Salva", if (goal.isNotBlank() && songId.isNotBlank()) ({ onSave(PracticeTask(task?.id ?: newId(), songId, section, goal.trim(), role, decision.trim(), task?.done ?: false)) }) else null, onDismiss) {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("Brano", band.songs.map { it.id to it.title }, songId) { songId = it; section = "Brano intero" }
            Choice("Passaggio", (listOf("Brano intero") + songSections(band.song(songId)?.body.orEmpty())).map { it to it }, section) { section = it }
            PalcoField(goal, { goal = it }, "Cosa vogliamo sistemare?", supporting = "Es. allineare l’attacco, decidere il finale")
            Choice("Chi ci lavora", ROLES.map { it to it }, role) { role = it }
            PalcoField(decision, { decision = it }, "Decisioni e appunti", singleLine = false, minLines = 3, supporting = "Restano disponibili anche alla prova successiva")
            if (onDelete != null) GhostButton("Elimina questo passaggio", danger = true, onClick = onDelete)
        }
    }
}

@Composable fun Choice(label: String, choices: List<Pair<String,String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Palco.Dim, fontFamily = Sans, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
        Box {
            GhostButton((choices.firstOrNull { it.first == selected }?.second ?: "Scegli") + "  ▾", Modifier.fillMaxWidth()) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false }) }
            }
        }
    }
}
