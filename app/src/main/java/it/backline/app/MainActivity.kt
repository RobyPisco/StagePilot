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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import it.backline.app.data.PcClient
import it.backline.app.data.PcException
import it.backline.app.data.PcSong
import it.backline.app.data.SetItem
import it.backline.app.data.Song
import it.backline.app.data.Store
import it.backline.app.data.decodeBackup
import it.backline.app.data.parsePcPackage
import kotlinx.coroutines.launch
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
    /** Testo arrivato da fuori ("Condividi con Backline", o dal PC): si consuma una volta sola. */
    private val incoming = mutableStateOf<String?>(null)

    /** Brani preparati con la pagina del PC e mandati con adb. */
    private val incomingSongs = mutableStateOf<List<PcSong>?>(null)

    private fun readShared(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
            val songs = parsePcPackage(text)
            if (songs != null) incomingSongs.value = songs else incoming.value = text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        // se l'attività si ricrea (rotazione, memoria) l'intent è lo stesso: non riaprire l'importazione
        if (savedInstanceState == null) readShared(intent)
        setContent { BacklineTheme { App(store, incoming, incomingSongs) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShared(intent)
    }
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

/** Brani in attesa di conferma. `ackIds` sono quelli da togliere dalla coda del PC (vuoto se arrivano da adb). */
private class PcBatch(val songs: List<PcSong>, val ackIds: List<String>)

@Composable
private fun App(store: Store, incoming: MutableState<String?>, incomingSongs: MutableState<List<PcSong>?>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("pc", Context.MODE_PRIVATE) }
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
    // testo con cui parte l'importazione (vuoto se si è aperta a mano)
    var importText by rememberSaveable { mutableStateOf("") }

    // ── brani dalla pagina del PC ──
    var pcAddr by remember { mutableStateOf(prefs.getString("addr", "") ?: "") }
    var pcCode by remember { mutableStateOf(prefs.getString("code", "") ?: "") }
    var pcStatus by remember { mutableStateOf("") }
    var pcBusy by remember { mutableStateOf(false) }
    // brani in attesa di conferma (dalla coda del PC o mandati con adb)
    var pendingPc by remember { mutableStateOf<PcBatch?>(null) }

    fun fetchPc(silent: Boolean) {
        if (pcAddr.isBlank() || pcCode.isBlank()) {
            if (!silent) pcStatus = "Scrivi l'indirizzo e il codice."
            return
        }
        scope.launch {
            pcBusy = true
            try {
                val songs = PcClient.fetch(pcAddr, pcCode)
                if (songs.isEmpty()) {
                    if (!silent) pcStatus = "Niente di nuovo sul PC."
                } else {
                    pendingPc = PcBatch(songs, songs.map { it.id })
                    pcStatus = "${songs.size} ${if (songs.size == 1) "brano" else "brani"} sul PC."
                }
            } catch (e: PcException) {
                if (!silent) pcStatus = e.message ?: "Errore"
            } finally {
                pcBusy = false
            }
        }
    }

    // all'apertura dell'app: se è configurato, si guarda se il PC ha qualcosa in coda (senza disturbare se non c'è)
    LaunchedEffect(Unit) { fetchPc(silent = true) }

    // brani mandati con adb: si chiede conferma come per quelli scaricati
    val sharedSongs = incomingSongs.value
    LaunchedEffect(sharedSongs) {
        if (sharedSongs != null) {
            pendingPc = PcBatch(sharedSongs, emptyList())
            incomingSongs.value = null
        }
    }

    // un tab condiviso da un'altra app o mandato dal PC: si apre l'importazione già con il testo
    val shared = incoming.value
    LaunchedEffect(shared) {
        if (shared != null) {
            importText = shared
            newId = store.newId("s")
            draft = null
            route = "import"
            incoming.value = null
        }
    }

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
            pcAddr = pcAddr,
            pcCode = pcCode,
            pcStatus = pcStatus,
            pcBusy = pcBusy,
            onPcSave = { a, c ->
                pcAddr = a; pcCode = c
                prefs.edit().putString("addr", a).putString("code", c).apply()
            },
            onPcFetch = { pcStatus = ""; fetchPc(silent = false) },
        )

        "import" -> ImportScreen(
            newId = newId,
            initial = importText,
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
            onImport = { draft = null; importText = ""; newId = store.newId("s"); route = "import" },
            onSettings = { route = "settings" },
            onSort = { s -> store.updateOptions { it.copy(sort = s) } },
        )
    }

    pendingPc?.let { batch ->
        val n = batch.songs.size
        PalcoDialog(
            title = if (n == 1) "Un brano dal PC" else "$n brani dal PC",
            confirmText = "Aggiungi",
            onConfirm = {
                store.addPcSongs(batch.songs)
                scope.launch { runCatching { PcClient.ack(pcAddr, pcCode, batch.ackIds) } }
                pendingPc = null
                pcStatus = ""
                toast(context, if (n == 1) "Brano aggiunto" else "$n brani aggiunti")
            },
            onDismiss = { pendingPc = null },
        ) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                batch.songs.take(8).forEach { s ->
                    Text(
                        s.title + if (s.artist.isNotBlank()) " — ${s.artist}" else "",
                        color = Palco.Ink, fontFamily = Condensed, fontSize = 16.sp,
                    )
                }
                if (n > 8) Text("e altri ${n - 8}…", color = Palco.Dim, fontFamily = Condensed, fontSize = 14.sp)
                Text(
                    "Finiscono nel repertorio di «${db.band.name}»" +
                        if (batch.songs.any { it.inSet }) ", e in coda alla scaletta in uso." else ".",
                    color = Palco.Dim, fontFamily = Condensed, fontSize = 14.sp,
                )
            }
        }
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
