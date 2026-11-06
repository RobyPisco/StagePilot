package it.stagepilot.app

import androidx.compose.ui.Modifier
import it.stagepilot.app.ui.TabBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import it.stagepilot.app.core.keyLabel
import it.stagepilot.app.core.moveItem
import it.stagepilot.app.core.setAsText
import it.stagepilot.app.data.AutoBackup
import it.stagepilot.app.data.DB
import it.stagepilot.app.data.alreadyInBand
import it.stagepilot.app.data.PcClient
import it.stagepilot.app.data.summarizeEdits
import it.stagepilot.app.data.parseEditsPackage
import it.stagepilot.app.data.PcEdit
import it.stagepilot.app.data.PcException
import it.stagepilot.app.data.PcSong
import it.stagepilot.app.data.SetItem
import it.stagepilot.app.data.SetlistPackage
import it.stagepilot.app.data.buildSetlistPackage
import it.stagepilot.app.data.encodeSetlistPackage
import it.stagepilot.app.data.parseSetlistPackage
import it.stagepilot.app.data.setlistImportInfo
import it.stagepilot.app.data.Song
import it.stagepilot.app.data.Store
import it.stagepilot.app.data.decodeBackup
import it.stagepilot.app.data.parsePcPackage
import kotlinx.coroutines.launch
import it.stagepilot.app.data.entries
import it.stagepilot.app.ui.StagePilotTheme
import it.stagepilot.app.ui.Condensed
import it.stagepilot.app.ui.EditorScreen
import it.stagepilot.app.ui.GhostButton
import it.stagepilot.app.ui.ImportScreen
import it.stagepilot.app.ui.LiveScreen
import it.stagepilot.app.ui.Palco
import it.stagepilot.app.ui.PalcoDialog
import it.stagepilot.app.ui.RepertoireScreen
import it.stagepilot.app.ui.SetlistScreen
import it.stagepilot.app.ui.SettingsScreen
import it.stagepilot.app.ui.HomeScreen
import it.stagepilot.app.ui.GuidedPracticeScreen
import it.stagepilot.app.ui.ConcertsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** Testo arrivato da fuori ("Condividi con StagePilot", o dal PC): si consuma una volta sola. */
    private val incoming = mutableStateOf<String?>(null)

    /** Brani preparati con la pagina del PC e mandati con adb. */
    private val incomingSongs = mutableStateOf<List<PcSong>?>(null)

    /** Una scaletta arrivata come file, in attesa di conferma. */
    private val incomingSetlist = mutableStateOf<SetlistPackage?>(null)
    private val incomingEdits = mutableStateOf<List<PcEdit>?>(null)

    /** Il testo di un file arrivato da fuori (condiviso o aperto), se si legge e non è enorme. */
    private fun readStream(intent: Intent): String? {
        val uri = (if (intent.action == Intent.ACTION_VIEW) intent.data
        else androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java))
            ?: return null
        return runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes().takeIf { b -> b.size <= 5_000_000 }?.toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    private fun readShared(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_VIEW) return
        val text = (readStream(intent) ?: intent.getStringExtra(Intent.EXTRA_TEXT))?.takeIf { it.isNotBlank() }
        if (text == null) {
            if (intent.action == Intent.ACTION_VIEW || intent.hasExtra(Intent.EXTRA_STREAM))
                Toast.makeText(this, "Non riesco a leggere il file", Toast.LENGTH_SHORT).show()
            return
        }
        val setlist = parseSetlistPackage(text)
        if (setlist != null) { incomingSetlist.value = setlist; return }
        val songs = parsePcPackage(text)
        if (songs != null) { incomingSongs.value = songs; return }
        val edits = parseEditsPackage(text)
        if (edits != null) { incomingEdits.value = edits; return }
        // un file (non testo condiviso) che non è roba di StagePilot: niente da importare
        if (intent.action == Intent.ACTION_VIEW || intent.type != "text/plain") {
            Toast.makeText(this, "Questo file non è una scaletta di StagePilot", Toast.LENGTH_LONG).show()
            return
        }
        incoming.value = text
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // sul leggio lo schermo non si spegne finché l'app è davanti
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val store = Store(applicationContext)
        // se l'attività si ricrea (rotazione, memoria) l'intent è lo stesso: non riaprire l'importazione
        if (savedInstanceState == null) readShared(intent)
        setContent { StagePilotTheme { App(store, incoming, incomingSongs, incomingSetlist, incomingEdits) } }
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

/** Modifiche al repertorio in attesa di conferma. `ackIds` sono quelle da togliere dalla coda del PC. */
private class EditBatch(val edits: List<PcEdit>, val ackIds: List<String>)

@Composable
private fun App(
    store: Store, incoming: MutableState<String?>, incomingSongs: MutableState<List<PcSong>?>,
    incomingSetlist: MutableState<SetlistPackage?>,
    incomingEdits: MutableState<List<PcEdit>?>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("pc", Context.MODE_PRIVATE) }
    val db by store.db.collectAsStateWithLifecycle()
    val band = db.band
    val saveError by store.error.collectAsStateWithLifecycle()
    saveError?.let { error -> PalcoDialog("Salvataggio non riuscito", "Ho capito", { store.clearError() }, { store.clearError() }) { Text(error, color = Palco.Ink, fontSize = 16.sp) } }

    // dove siamo: "main" (scaletta o repertorio), "live", "edit", "import" o "settings"
    var route by rememberSaveable { mutableStateOf("main") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var liveAt by rememberSaveable { mutableIntStateOf(0) }
    var practiceSongId by rememberSaveable { mutableStateOf("") }
    var practiceSection by rememberSaveable { mutableStateOf("Brano intero") }
    var practiceSetScope by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var newId by rememberSaveable { mutableStateOf("") }
    var editingSet by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = route == "main" && tab != 0) { tab = 0 }
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
    var pendingEdits by remember { mutableStateOf<EditBatch?>(null) }

    var syncConflict by remember { mutableStateOf<it.stagepilot.app.data.SyncConflict?>(null) }
    fun fetchPc(silent: Boolean, policy: String? = null, revision: Int? = null) {
        if (pcBusy) return
        if (pcAddr.isBlank() || pcCode.isBlank()) {
            if (!silent) pcStatus = "Scrivi l'indirizzo e il codice dello studio web."
            return
        }
        scope.launch {
            pcBusy = true
            pcStatus = "Sincronizzazione in corso…"
            try {
                val sent = store.db.value
                val address = pcAddr
                val code = pcCode
                val merged = PcClient.sync(address, code, sent, store.syncBase(), policy, revision)
                check(address == pcAddr && code == pcCode) { "Il collegamento al PC è cambiato. Sincronizza di nuovo." }
                store.acceptSync(merged, sent)
                pcStatus = "PC e tablet sono aggiornati."
                syncConflict = null
            } catch (e: it.stagepilot.app.data.SyncConflict) {
                syncConflict = e
                pcStatus = "Scegli come risolvere le modifiche in conflitto."
            } catch (e: Exception) {
                pcStatus = e.message ?: "Sincronizzazione non riuscita. I dati locali sono disponibili."
            } finally { pcBusy = false }
        }
    }

    // The tablet stays autonomous. Sync only starts from the user's button.
    LaunchedEffect(db) {
        kotlinx.coroutines.delay(1000)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AutoBackup.saveLocal(context, store.exportJson())
        }
    }

    syncConflict?.let { conflict ->
        PalcoDialog("Modifiche da confrontare", "Tieni quelle del tablet", {
            syncConflict = null
            fetchPc(false, "device", conflict.revision)
        }, { syncConflict = null; pcStatus = "Sincronizzazione annullata. Nessun dato è stato sostituito." }) {
            Column {
                Text("Alcune informazioni sono cambiate da entrambe le parti. La scelta riguarda solo queste; tutte le altre modifiche vengono unite.", color = Palco.Ink, fontFamily = Condensed, fontSize = 15.sp)
                conflict.paths.take(6).forEach { path ->
                    val bits = path.split('/')
                    val title = db.bands.flatMap { it.songs }.firstOrNull { it.id in bits }?.title ?: "Libreria"
                    val field = mapOf("bpm" to "BPM", "body" to "testo e accordi", "title" to "titolo", "items" to "ordine della scaletta", "practice" to "appunto di prova", "key" to "tonalità", "status" to "preparazione")[bits.last()] ?: "informazioni"
                    Text("• $title: $field", color = Palco.Dim, fontFamily = Condensed, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                GhostButton("Tieni quelle del PC", Modifier.padding(top = 15.dp)) {
                    syncConflict = null
                    fetchPc(false, "pc", conflict.revision)
                }
            }
        }
    }

    // brani mandati con adb: si chiede conferma come per quelli scaricati
    val sharedSongs = incomingSongs.value
    LaunchedEffect(sharedSongs) {
        if (sharedSongs != null) {
            pendingPc = PcBatch(sharedSongs, emptyList())
            incomingSongs.value = null
        }
    }

    // modifiche mandate con adb: stessa conferma di quelle scaricate
    val sharedEdits = incomingEdits.value
    LaunchedEffect(sharedEdits) {
        if (sharedEdits != null) {
            pendingEdits = EditBatch(sharedEdits, emptyList())
            incomingEdits.value = null
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
                .onFailure { toast(context, "Il file non sembra un backup di StagePilot") }
        }
    }

    var shareChoice by remember { mutableStateOf(false) }

    /** Manda la scaletta come file StagePilot: i brani con i chart, in ordine. Si apre con StagePilot. */
    fun shareSetFile() {
        val set = band.curSet ?: return
        runCatching {
            val dir = java.io.File(context.cacheDir, "condivisi").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val slug = set.name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').ifEmpty { "scaletta" }
            val f = java.io.File(dir, "scaletta-$slug.json").apply { writeText(encodeSetlistPackage(buildSetlistPackage(band, set))) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${band.name} — ${set.name}")
                putExtra(Intent.EXTRA_TEXT, "Scaletta «${set.name}» di ${band.name}: aprila con StagePilot.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Manda la scaletta"))
        }.onFailure { toast(context, "Non riesco a preparare il file") }
    }

    fun shareSetText() {
        val text = setAsText(band.entries(), band.name, band.curSet?.name ?: "") { keyLabel(it, db.opt.notation, db.opt.accid) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${band.name} — ${band.curSet?.name ?: ""}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Condividi la scaletta"))
    }

    when (route) {
        "import" -> ImportScreen(
            newId = newId,
            initial = importText,
            onContinue = { draft = it; editId = null; route = "edit" },
            onClose = { route = "main" },
        )

        "live" -> LiveScreen(db = db, startIndex = liveAt, store = store, onClose = { route = "main" })
        "passage" -> {
            val song = band.song(practiceSongId)
            if (song == null) route = "main" else {
                val focus = song.copy(body = it.stagepilot.app.core.passageBody(song.body, practiceSection))
                val set = it.stagepilot.app.data.SetList("practice-view", song.title, listOf(SetItem("song", id = song.id)))
                val preview = band.copy(songs = band.songs.map { if (it.id == song.id) focus else it }, sets = listOf(set), cur = set.id)
                LiveScreen(db.copy(bands = db.bands.map { if (it.id == band.id) preview else it }), 0, store) { route = "main"; tab = 3 }
            }
        }

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

        else -> Column(Modifier.fillMaxSize().background(Palco.Pitch)) {
            Box(Modifier.weight(1f)) {
            when (tab) {
            0 -> HomeScreen(db, onStage = { setId -> store.updateBand { it.copy(cur = setId) }; liveAt = 0; route = "live" },
                onImport = { draft = null; importText = ""; newId = store.newId("s"); route = "import" },
                onConcerts = { tab = 4 }, onPractice = { practiceSetScope = ""; tab = 3 }, onSettings = { tab = 5 })
            2 -> SetlistScreen(
            db = db,
            editing = editingSet,
            onOpen = { liveAt = it; route = "live" },
            onToggleNotation = { store.updateOptions { it.copy(notation = if (it.notation == "it") "en" else "it") } },
            onPickSet = { id -> store.updateBand { it.copy(cur = id) } },
            onToggleEdit = { editingSet = !editingSet },
            onRemove = { i -> store.updateItems { items -> items.filterIndexed { j, _ -> j != i } } },
            onMove = { from, to -> store.updateItems { items -> moveItem(items, from, to) } },
            onAddSong = { id, at -> store.updateItems { it.toMutableList().apply { add(at.coerceIn(0, size), SetItem("song", id = id)) } } },
            onAddMark = { label, dur, at ->
                store.updateItems { it.toMutableList().apply { add(at.coerceIn(0, size), SetItem("mark", label = label, dur = dur)) } }
            },
            onEditMark = { i, label, dur ->
                store.updateItems { items -> items.mapIndexed { j, x -> if (j == i && x.isMark) x.copy(label = label, dur = dur) else x } }
            },
            onShare = { shareChoice = true },
            onSettings = { tab = 5 },
        )
            1 -> RepertoireScreen(
            db = db,
            onOpenSong = { id -> editId = id; route = "edit" },
            onNewSong = { editId = null; draft = null; newId = store.newId("s"); route = "edit" },
            onImport = { draft = null; importText = ""; newId = store.newId("s"); route = "import" },
            onSettings = { tab = 5 },
            onSort = { s -> store.updateOptions { it.copy(sort = s) } },
            onDeleteSong = { id -> store.deleteSong(id) },
        )
            3 -> GuidedPracticeScreen(db, store, onPassage = { song, section -> practiceSongId = song; practiceSection = section; route = "passage" }, onSettings = { tab = 5 }, scopeSetId = practiceSetScope)
            4 -> ConcertsScreen(db, store, onStage = { setId -> store.updateBand { it.copy(cur = setId) }; liveAt = 0; route = "live" }, onPractice = { setId -> practiceSetScope = setId; tab = 3 }, onSettings = { tab = 5 })
            else -> SettingsScreen(
                    db = db,
                    store = store,
                    onClose = { tab = 0 },
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
                        if (a != pcAddr || c != pcCode) store.clearSyncBase()
                        pcAddr = a; pcCode = c
                        prefs.edit().putString("addr", a).putString("code", c).apply()
                    },
                    onPcFetch = { pcStatus = ""; fetchPc(silent = false) },
                )
            }
            }
            TabBar(tab, onSelect = { if (it == 3) practiceSetScope = ""; tab = it })
        }
    }

    pendingPc?.let { batch ->
        val n = batch.songs.size
        val dups = alreadyInBand(db, batch.songs).size
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
                if (dups > 0) Text(
                    if (dups == 1) "1 è già nel repertorio: non si aggiunge due volte."
                    else "$dups sono già nel repertorio: non si aggiungono due volte.",
                    color = Palco.Amber, fontFamily = Condensed, fontSize = 14.sp,
                )
                Text(
                    "Finiscono nel repertorio di «${db.band.name}»" +
                        if (batch.songs.any { it.inSet }) ", e in coda alla scaletta in uso." else ".",
                    color = Palco.Dim, fontFamily = Condensed, fontSize = 14.sp,
                )
            }
        }
    }

    pendingEdits?.takeIf { pendingPc == null }?.let { batch ->
        val sum = summarizeEdits(db, batch.edits)
        PalcoDialog(
            title = "Modifiche dal PC",
            confirmText = "Applica",
            onConfirm = {
                val r = store.applyPcEdits(batch.edits)
                scope.launch { runCatching { PcClient.ack(pcAddr, pcCode, batch.ackIds) } }
                pendingEdits = null
                pcStatus = ""
                toast(
                    context,
                    if (r.skipped.isEmpty()) "Modifiche applicate"
                    else "Applicate ${r.applied}; ${r.skipped.size} saltate: ${r.skipped.first()}",
                )
            },
            onDismiss = { pendingEdits = null },
        ) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                if (sum.changed.isNotEmpty()) Text(
                    "Dati corretti: " + sum.changed.take(6).joinToString(", ") + if (sum.changed.size > 6) " e altri ${sum.changed.size - 6}" else "",
                    color = Palco.Ink, fontFamily = Condensed, fontSize = 15.sp,
                )
                if (sum.sets.isNotEmpty()) Text(
                    "Scaletta riordinata: " + sum.sets.joinToString(", "),
                    color = Palco.Ink, fontFamily = Condensed, fontSize = 15.sp,
                )
                if (sum.deleted.isNotEmpty()) Text(
                    "Da eliminare: " + sum.deleted.take(6).joinToString(", ") + if (sum.deleted.size > 6) " e altri ${sum.deleted.size - 6}" else "",
                    color = Palco.Warn, fontFamily = Condensed, fontSize = 15.sp,
                )
                Text(
                    "Si applicano al gruppo in uso. Se hai già cambiato qualcosa sul tablet, quella parte resta com'è.",
                    color = Palco.Dim, fontFamily = Condensed, fontSize = 14.sp,
                )
            }
        }
    }

    if (shareChoice) PalcoDialog(
        title = "Condividi la scaletta",
        confirmText = "File StagePilot",
        onConfirm = { shareChoice = false; shareSetFile() },
        onDismiss = { shareChoice = false },
    ) {
        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            Text(
                "«File StagePilot» porta con sé i brani con gli accordi, in ordine: chi lo riceve lo apre con StagePilot " +
                    "e si trova la scaletta pronta. «Solo elenco» manda i titoli come testo, per chiunque.",
                color = Palco.Dim, fontFamily = Condensed, fontSize = 15.sp,
            )
            GhostButton("Solo elenco (testo)") { shareChoice = false; shareSetText() }
        }
    }

    incomingSetlist.value?.let { p ->
        val info = setlistImportInfo(db, p)
        PalcoDialog(
            title = "Scaletta «${p.name.ifBlank { "senza nome" }}»",
            confirmText = "Aggiungi",
            onConfirm = {
                store.importSetlist(p)
                incomingSetlist.value = null
                tab = 0; route = "main"
                toast(context, "Scaletta aggiunta")
            },
            onDismiss = { incomingSetlist.value = null },
        ) {
            Text(
                (if (p.band.isNotBlank()) "Da «${p.band}». " else "") +
                    "${p.items.count { it.type == "song" }} brani in scaletta" +
                    (if (info.newSongs > 0) "; ${info.newSongs} ${if (info.newSongs == 1) "nuovo" else "nuovi"} per te" else "") +
                    (if (info.knownSongs > 0) "; ${info.knownSongs} li hai già, non si duplicano" else "") +
                    ".\n\nFinisce nel gruppo «${db.band.name}», come scaletta nuova: le tue non si toccano.",
                color = Palco.Dim, fontFamily = Condensed, fontSize = 15.sp,
            )
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
