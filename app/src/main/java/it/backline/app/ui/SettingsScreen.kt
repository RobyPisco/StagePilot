package it.backline.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.backline.app.data.DB
import it.backline.app.data.ROLES
import it.backline.app.data.Store

/* Gruppo, scalette, modo di leggere e backup: tutto quello che non serve
   sul palco sta qui, fuori dalla strada. */
@Composable
fun SettingsScreen(
    db: DB, store: Store, onClose: () -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    pcAddr: String, pcCode: String, pcStatus: String, pcBusy: Boolean,
    onPcSave: (String, String) -> Unit, onPcFetch: () -> Unit,
) {
    val band = db.band
    val opt = db.opt
    var bandName by remember(band.id) { mutableStateOf(band.name) }
    var addr by remember { mutableStateOf(pcAddr) }
    var code by remember { mutableStateOf(pcCode) }

    // dialoghi: quale è aperto e su cosa
    var newBandDialog by remember { mutableStateOf(false) }
    var newSetDialog by remember { mutableStateOf(false) }
    var renameSet by remember { mutableStateOf<String?>(null) }
    var deleteSet by remember { mutableStateOf<String?>(null) }
    var deleteBand by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            LargeTitle("Impostazioni", "Gruppo, scalette, lettura e backup")
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── gruppo ──
                Section("Gruppo")
                if (db.bands.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    db.bands.forEach { b -> Chip(b.name, selected = b.id == band.id) { store.switchBand(b.id) } }
                }
                PalcoField(bandName, { bandName = it; if (it.isNotBlank()) store.renameBand(it) }, "Nome del gruppo")
                Hint("Il tuo ruolo: decide quali note personali vedi in live.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ROLES.forEach { r -> Chip(r, selected = r == band.me) { store.setRole(r) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("+ Nuovo gruppo") { newBandDialog = true }
                    if (db.bands.size > 1) GhostButton("Elimina gruppo", danger = true) { deleteBand = true }
                }

                // ── scalette ──
                Section("Scalette")
                band.sets.forEach { s ->
                    Column(Modifier.fillMaxWidth().background(Palco.Panel, RoundedCornerShape(12.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.name, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp, modifier = Modifier.weight(1f),
                            )
                            if (s.id == band.curSet?.id) Text("IN USO", color = Palco.Amber, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                        }
                        Text("${s.items.size} ${if (s.items.size == 1) "voce" else "voci"}", color = Palco.Dim, fontFamily = Sans, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("Rinomina") { renameSet = s.id }
                            GhostButton("Duplica") { store.duplicateSet(s.id) }
                            if (band.sets.size > 1) GhostButton("Elimina", danger = true) { deleteSet = s.id }
                        }
                    }
                }
                GhostButton("+ Nuova scaletta") { newSetDialog = true }

                // ── lettura ──
                Section("Come si legge")
                Hint("Notazione degli accordi")
                Segmented(listOf("C D E", "Do Re Mi"), if (opt.italian) 1 else 0) { i ->
                    store.updateOptions { it.copy(notation = if (i == 1) "it" else "en") }
                }
                Hint("Alterazioni: automatiche scelgono dalla tonalità (Si♭ in Fa, La# in Mi)")
                Segmented(listOf("Automatiche", "Diesis", "Bemolli"), listOf("auto", "sharp", "flat").indexOf(opt.accid).coerceAtLeast(0)) { i ->
                    store.updateOptions { it.copy(accid = listOf("auto", "sharp", "flat")[i]) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Corpo del testo in live: ${opt.fs}", color = Palco.Ink, fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("A−", selected = false) { store.updateOptions { it.copy(fs = (it.fs - 2).coerceIn(14, 48)) } }
                        Chip("A+", selected = false) { store.updateOptions { it.copy(fs = (it.fs + 2).coerceIn(14, 48)) } }
                    }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palco.Panel).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Toggle("Barra dei sei comandi", "In fondo alla vista live.", opt.bar) { v -> store.updateOptions { it.copy(bar = v) } }
                    Toggle("Cronometro del set", "Il tempo trascorso, in testata.", opt.timer) { v -> store.updateOptions { it.copy(timer = v) } }
                    Toggle("Pulsazione dei BPM", "Un puntino che batte il tempo del brano, in testata: per attaccare giusti.", opt.pulse) { v -> store.updateOptions { it.copy(pulse = v) } }
                    Toggle("Solo le note fondamentali", "Per chi suona il basso: Am7 diventa A, C/G diventa G.", opt.roots) { v -> store.updateOptions { it.copy(roots = v) } }
                    Toggle("Solo testo", "Senza accordi: il foglio di chi canta.", opt.plain) { v -> store.updateOptions { it.copy(plain = v) } }
                }

                // ── backup ──
                Section("Backup")
                Hint(
                    "Il backup è un file JSON con tutti i gruppi, i brani e le scalette. È lo stesso formato dell'app web: " +
                        "un backup di là si importa qui. Per condividere con la band basta mandare il file.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Esporta") { onExport() }
                    GhostButton("Importa") { onImport() }
                }

                // ── brani dal PC ──
                Section("Brani dal PC")
                Hint(
                    "Sul PC apri la pagina di Backline (tools\\pagina-pc\\avvia.cmd): lì scrivi testo, BPM e dati, e premi Invia. " +
                        "Qui metti una volta sola l'indirizzo e il codice che la pagina mostra. Backline controlla da solo " +
                        "ogni volta che si apre, se telefono e PC sono sulla stessa Wi-Fi.",
                )
                PalcoField(
                    addr, { addr = it; onPcSave(it, code) }, "Indirizzo del PC", supporting = "es. 192.168.178.32:8765",
                )
                PalcoField(
                    code, { code = it.filter(Char::isDigit).take(6); onPcSave(addr, code) }, "Codice",
                    keyboard = KeyboardType.Number, supporting = "4 cifre, le mostra la pagina",
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton(if (pcBusy) "Cerco…" else "Scarica") { if (!pcBusy) onPcFetch() }
                    if (pcStatus.isNotEmpty()) Text(pcStatus, color = Palco.Dim, fontFamily = Sans, fontSize = 14.sp, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(8.dp))
                // la versione vera dell'app, letta dal pacchetto: così non resta mai indietro rispetto alla build
                val context = androidx.compose.ui.platform.LocalContext.current
                val version = remember {
                    runCatching {
                        val i = context.packageManager.getPackageInfo(context.packageName, 0)
                        "Backline ${i.versionName} (build ${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(i)})"
                    }.getOrDefault("Backline")
                }
                Text(version, color = Palco.Dim, fontFamily = Sans, fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (newBandDialog) NameDialog("Nuovo gruppo", "Nome del gruppo", "", "Crea", { newBandDialog = false }) {
        store.newBand(it); newBandDialog = false
    }
    if (newSetDialog) NameDialog("Nuova scaletta", "Nome", "", "Crea", { newSetDialog = false }) {
        store.newSet(it); newSetDialog = false
    }
    renameSet?.let { id ->
        NameDialog("Rinomina la scaletta", "Nome", band.sets.firstOrNull { it.id == id }?.name ?: "", "Salva", { renameSet = null }) {
            store.renameSet(id, it); renameSet = null
        }
    }
    deleteSet?.let { id ->
        PalcoDialog("Eliminare la scaletta?", "Elimina", { store.deleteSet(id); deleteSet = null }, { deleteSet = null }) {
            Text(
                "«${band.sets.firstOrNull { it.id == id }?.name}» sparisce. I brani restano nel repertorio.",
                color = Palco.Dim, fontFamily = Sans, fontSize = 15.sp,
            )
        }
    }
    if (deleteBand) PalcoDialog("Eliminare il gruppo?", "Elimina", { store.deleteBand(band.id); deleteBand = false }, { deleteBand = false }) {
        Text(
            "«${band.name}» sparisce con tutti i suoi brani e le sue scalette. Se vuoi tenerli, esporta prima un backup.",
            color = Palco.Dim, fontFamily = Sans, fontSize = 15.sp,
        )
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text, color = Palco.Dim, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp, start = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Palco.Dim, fontFamily = Sans, fontSize = 14.sp)
}

@Composable
private fun Toggle(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(desc, color = Palco.Dim, fontFamily = Sans, fontSize = 13.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palco.OnAmber, checkedTrackColor = Palco.Amber,
                uncheckedThumbColor = Palco.Dim, uncheckedTrackColor = Palco.Raise, uncheckedBorderColor = Palco.Outline,
            ),
        )
    }
}

@Composable
fun NameDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    PalcoDialog(title, confirm, if (v.isNotBlank()) ({ onOk(v) }) else null, onDismiss) {
        PalcoField(v, { v = it }, label)
    }
}
