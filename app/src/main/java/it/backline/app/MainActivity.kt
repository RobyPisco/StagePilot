package it.backline.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.backline.app.core.keyLabel
import it.backline.app.core.moveItem
import it.backline.app.core.setAsText
import it.backline.app.data.DB
import it.backline.app.data.SetItem
import it.backline.app.data.Song
import it.backline.app.data.Store
import it.backline.app.data.decodeBackup
import it.backline.app.data.entries
import it.backline.app.ui.BacklineTheme
import it.backline.app.ui.Condensed
import it.backline.app.ui.EditorScreen
import it.backline.app.ui.GhostButton
import it.backline.app.ui.ImportScreen
import it.backline.app.ui.LiveScreen
import it.backline.app.ui.Palco
import it.backline.app.ui.PalcoDialog
import it.backline.app.ui.RepertoireScreen
import it.backline.app.ui.SetlistScreen
import it.backline.app.ui.SettingsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent { BacklineTheme { App(store) } }
    }
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

@Composable
private fun App(store: Store) {
    val context = LocalContext.current
    val db by store.db.collectAsStateWithLifecycle()
    val band = db.band

    // dove siamo: "main" (scaletta o repertorio), "live", "edit", "import" o "settings"
    var route by rememberSaveable { mutableStateOf("main") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var liveAt by rememberSaveable { mutableIntStateOf(0) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var newId by rememberSaveable { mutableStateOf("") }
    var editingSet by rememberSaveable { mutableStateOf(false) }
    // bozza che arriva dall'importazione: diventa un brano nuovo nell'editor
    var draft by remember { mutableStateOf<Song?>(null) }
    // backup letto da file, in attesa che si scelga cosa farne
    var pendingBackup by remember { mutableStateOf<DB?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray(Charsets.UTF_8)) }
                    ?: error("nessun file")
            }.onSuccess { toast(context, "Backup salvato") }
                .onFailure { toast(context, "Non riesco a salvare il backup") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("nessun file")
                decodeBackup(text)
            }.onSuccess { pendingBackup = it }
                .onFailure { toast(context, "Il file non sembra un backup di Backline") }
        }
    }

    fun shareSet() {
        val text = setAsText(band.entries(), band.name, band.curSet?.name ?: "") { keyLabel(it, db.opt.notation, db.opt.accid) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${band.name} — ${band.curSet?.name ?: ""}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Condividi la scaletta"))
    }

    when (route) {
        "settings" -> SettingsScreen(
            db = db,
            store = store,
            onClose = { route = "main" },
            onExport = {
                val stamp = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
                exportLauncher.launch("backline-backup-$stamp.json")
            },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
        )

        "import" -> ImportScreen(
            newId = newId,
            onContinue = { draft = it; editId = null; route = "edit" },
            onClose = { route = "main" },
        )

        "live" -> LiveScreen(db = db, startIndex = liveAt, store = store, onClose = { route = "main" })

        "edit" -> {
            val song = band.song(editId)
            val inSet = band.curSet?.items.orEmpty().any { it.type == "song" && it.id == song?.id }
            EditorScreen(
                band = band,
                song = song,
                draft = if (song == null) draft else null,
                inSetInitial = if (song == null) true else inSet,
                newId = newId,
                onSave = { saved, wantInSet ->
                    store.saveSong(saved, toSet = wantInSet)
                    if (!wantInSet) store.updateItems { items -> items.filterNot { it.type == "song" && it.id == saved.id } }
                    draft = null
                    route = "main"
                },
                onDelete = song?.let { s -> { store.deleteSong(s.id); route = "main" } },
                onClose = { draft = null; route = "main" },
            )
        }

        else -> if (tab == 0) SetlistScreen(
            db = db,
            editing = editingSet,
            onTab = { tab = it },
            onOpen = { liveAt = it; route = "live" },
            onToggleNotation = { store.updateOptions { it.copy(notation = if (it.notation == "it") "en" else "it") } },
            onPickSet = { id -> store.updateBand { it.copy(cur = id) } },
            onToggleEdit = { editingSet = !editingSet },
            onRemove = { i -> store.updateItems { items -> items.filterIndexed { j, _ -> j != i } } },
            onMove = { from, to -> store.updateItems { items -> moveItem(items, from, to) } },
            onAddSong = { id -> store.updateItems { it + SetItem("song", id = id) } },
            onAddMark = { label, dur -> store.updateItems { it + SetItem("mark", label = label, dur = dur) } },
            onShare = ::shareSet,
            onSettings = { route = "settings" },
        ) else RepertoireScreen(
            db = db,
            onTab = { tab = it },
            onOpenSong = { id -> editId = id; route = "edit" },
            onNewSong = { editId = null; draft = null; newId = store.newId("s"); route = "edit" },
            onImport = { draft = null; newId = store.newId("s"); route = "import" },
            onSettings = { route = "settings" },
            onSort = { s -> store.updateOptions { it.copy(sort = s) } },
        )
    }

    pendingBackup?.let { b ->
        val songs = b.bands.sumOf { it.songs.size }
        PalcoDialog(
            title = "Importare il backup?",
            confirmText = "Aggiungi",
            onConfirm = { store.addBands(b); pendingBackup = null; toast(context, "Gruppi aggiunti") },
            onDismiss = { pendingBackup = null },
        ) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(
                    "Nel file: ${b.bands.size} ${if (b.bands.size == 1) "gruppo" else "gruppi"}, $songs brani.\n\n" +
                        "«Aggiungi» li mette accanto ai tuoi come gruppi nuovi, senza toccare niente. " +
                        "«Sostituisci tutto» cancella quello che hai adesso.",
                    color = Palco.Dim, fontFamily = Condensed, fontSize = 15.sp,
                )
                GhostButton("Sostituisci tutto", danger = true) {
                    store.replaceAll(b); pendingBackup = null; route = "main"; toast(context, "Backup ripristinato")
                }
            }
        }
    }
}
