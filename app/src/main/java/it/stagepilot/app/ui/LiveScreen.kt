package it.stagepilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.stagepilot.app.core.SheetLine
import it.stagepilot.app.core.SheetOpts
import it.stagepilot.app.core.SpeedBasis
import it.stagepilot.app.core.speedBasis
import it.stagepilot.app.core.flatFor
import it.stagepilot.app.core.keyLabel
import it.stagepilot.app.core.mmss
import it.stagepilot.app.core.notesFor
import it.stagepilot.app.core.ordinals
import it.stagepilot.app.core.renderSheet
import it.stagepilot.app.core.roman
import it.stagepilot.app.core.secs
import it.stagepilot.app.core.speedFor
import it.stagepilot.app.core.speedLabel
import it.stagepilot.app.core.stepSpeed
import it.stagepilot.app.core.transpose
import it.stagepilot.app.data.DB
import it.stagepilot.app.data.Entry
import it.stagepilot.app.data.Store
import it.stagepilot.app.data.entries
import it.stagepilot.app.data.SetItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* La vista live: schermo pieno, testo scalabile, sei comandi grandi sempre a
   portata. Lo schermo non si spegne finché è aperta. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(db: DB, startIndex: Int, store: Store, onClose: () -> Unit) {
    val band = db.band
    val opt = db.opt
    val entries = band.entries()
    if (entries.isEmpty()) { onClose(); return }
    val ord = ordinals(entries)

    var index by rememberSaveable(band.id, band.cur) { mutableIntStateOf(startIndex.coerceIn(0, entries.lastIndex)) }
    var playing by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var struct by remember { mutableStateOf(false) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }
    var requestSong by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val entry = entries[index.coerceIn(0, entries.lastIndex)]
    val next = entries.getOrNull(index + 1)
    var trackElapsed by remember(index) { mutableIntStateOf(0) }
    LaunchedEffect(index) { while (true) { delay(1000); trackElapsed++ } }
    val remaining = (entries.drop(index).sumOf { when (it) { is Entry.Track -> secs(it.song.dur); is Entry.Mark -> secs(it.dur) } } - trackElapsed).coerceAtLeast(0)

    // lo schermo resta acceso per tutta la durata del set
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(onBack = onClose)

    // cronometro del set
    LaunchedEffect(Unit) { while (true) { delay(1000); elapsed++ } }

    fun go(delta: Int) {
        val n = index + delta
        if (n in entries.indices) { playing = false; index = n }
    }
    LaunchedEffect(index) { scroll.scrollTo(0) }

    // battito: lampeggio (striscia e cerchio) e, se lo accendi toccando il cerchio, il tic del metronomo
    val songBpm = (entry as? Entry.Track)?.song?.bpm?.takeIf { it in 20..300 }
    val showBeat = songBpm != null && (opt.pulse || opt.click)
    var clicking by remember(index) { mutableStateOf(false) }       // a ogni brano si riparte in silenzio
    val beat = rememberBeat(songBpm.takeIf { showBeat }, sound = clicking && opt.click)

    // scorrimento automatico: il chart arriva in fondo quando finisce il brano
    // il brano com'è adesso: la velocità si può cambiare mentre scorre e deve valere subito,
    // non alla prossima pressione di Play
    val currentEntry by androidx.compose.runtime.rememberUpdatedState(entry)
    // base della velocità (BPM, durata o fissa): si calcola una volta per brano, non a ogni fotogramma
    val liveSong = (entry as? Entry.Track)?.song
    val basis = remember(liveSong?.body, liveSong?.bpm, liveSong?.dur) {
        speedBasis(liveSong?.body, liveSong?.bpm, secs(liveSong?.dur))
    }
    val basisNow by androidx.compose.runtime.rememberUpdatedState(basis)
    LaunchedEffect(playing, index) {
        val first = currentEntry as? Entry.Track ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        delay(first.song.lead * 1000L)
        var last = androidx.compose.runtime.withFrameNanos { it }
        var carry = 0f
        while (playing) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val song = (currentEntry as? Entry.Track)?.song ?: break
            if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue) { playing = false; break }
            carry += speedFor(scroll.maxValue.toFloat(), basisNow, song.scroll, song.lead) * dt
            val px = carry.toInt()
            if (px > 0) { scroll.scrollBy(px.toFloat()); carry -= px }
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier.fillMaxSize().background(Palco.Pitch).systemBarsPadding()
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { ev ->
                // girapagine Bluetooth e tastiere: stessi tasti dell'app web
                if (ev.type != KeyEventType.KeyDown) false else when (ev.key) {
                    Key.DirectionRight, Key.PageDown -> { go(1); true }
                    Key.DirectionLeft, Key.PageUp -> { go(-1); true }
                    Key.Spacebar -> { playing = !playing; true }
                    Key.Escape -> { onClose(); true }
                    else -> false
                }
            },
    ) {
        // testata
        Row(
            Modifier.fillMaxWidth().background(Palco.Panel).padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Ic.Back, "Torna alla scaletta", tint = Palco.Dim, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                when (entry) {
                    is Entry.Track -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                ord[index.coerceIn(0, ord.lastIndex)].toString().padStart(2, '0'), color = Palco.Amber, fontFamily = Mono,
                                fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.song.title, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                                fontSize = 23.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val sub = listOfNotNull(
                            entry.song.artist.takeIf { it.isNotBlank() },
                            entry.song.bpm?.let { "$it BPM" },
                            if (opt.timer) mmss(elapsed) else null,
                        ).joinToString("  ·  ")
                        Text(sub, color = Palco.Dim, fontFamily = Condensed, fontSize = 13.sp, maxLines = 1)
                    }
                    is Entry.Mark -> {
                        Text(
                            entry.label, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                            fontSize = 23.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (opt.timer) Text(mmss(elapsed), color = Palco.Dim, fontFamily = Mono, fontSize = 13.sp)
                    }
                }
            }
            if (showBeat) BeatDot(
                beat, soundAvailable = opt.click, soundOn = clicking,
                onToggleSound = { clicking = !clicking }, modifier = Modifier.padding(end = 14.dp),
            )
            if (entry is Entry.Track) {
                val k = keyLabel(entry.song, opt.notation, opt.accid)
                if (k.isNotEmpty()) Text(
                    k, color = Palco.OnAmber, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Palco.Amber).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))

        // chart
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .then(if (locked) Modifier.border(3.dp, Palco.Amber) else Modifier),
        ) {
            when (entry) {
                is Entry.Mark -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        entry.label, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                        fontSize = 56.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
                    )
                }
                is Entry.Track -> {
                    val song = entry.song
                    val keyShown = transpose(song.key, song.tr)
                    val lines = remember(song.body, song.tr, struct, opt.notation, opt.accid, opt.roots, opt.plain) {
                        renderSheet(
                            song.body,
                            SheetOpts(
                                tr = song.tr, struct = struct, ital = opt.italian,
                                flat = flatFor(keyShown, opt.accid), roots = opt.roots, plain = opt.plain,
                            ),
                        )
                    }
                    val notes = notesFor(song.notes, band.me)
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 40.dp)) {
                        if (song.capo > 0) Text(
                            "CAPO ${roman(song.capo)}", color = Palco.OnAmber, fontFamily = Mono,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(6.dp)).background(Palco.Amber)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                        notes.forEach { (who, txt) ->
                            Column(
                                Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(Palco.Panel).padding(12.dp),
                            ) {
                                Text(who.uppercase(), color = Palco.Amber, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.6.sp)
                                Text(txt, color = Palco.Ink, fontFamily = Condensed, fontSize = 15.sp)
                            }
                        }
                        lines.forEach { l -> SheetLineView(l, opt.fs) }
                    }
                }
            }
            // blocco touch: una manata sullo schermo non fa più niente
            if (locked) Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                },
            )
        }

        // prossimo, e a destra la voce che apre le opzioni (trasposizione, notazione, testo, scorrimento):
        // i comandi da provare in sala restano a un tocco, ma non rubano spazio al chart sul palco
        Row(
            Modifier.fillMaxWidth().background(Palco.Panel).padding(start = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Prossimo  ", color = Palco.Dim, fontFamily = Condensed, fontSize = 14.sp)
            Text(
                when (next) { is Entry.Track -> next.song.title; is Entry.Mark -> next.label; null -> "Fine scaletta" },
                color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (band.cur != "practice-view") GhostButton("Regia · ~${mmss(remaining)}") { overview = true }
            Row(
                Modifier.clickable { toolsOpen = !toolsOpen }.heightIn(min = 44.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "OPZIONI", color = if (toolsOpen) Palco.Amber else Palco.Dim, fontFamily = Condensed,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.width(6.dp))
                Icon(if (toolsOpen) Ic.Chevron else Ic.Up, null, tint = if (toolsOpen) Palco.Amber else Palco.Dim, modifier = Modifier.size(14.dp))
            }
        }

        // trasposizione, notazione, corpo del testo
        if (toolsOpen) Row(
            Modifier.fillMaxWidth().background(Palco.Panel).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val song = (entry as? Entry.Track)?.song
            ToolButton("−", enabled = song != null) { song?.let { s -> store.updateSong(s.id) { it.copy(tr = (it.tr - 1).coerceIn(-11, 11)) } } }
            Text(
                (song?.tr ?: 0).let { if (it > 0) "+$it" else "$it" }, color = Palco.Ink, fontFamily = Mono, fontSize = 14.sp,
                textAlign = TextAlign.Center, modifier = Modifier.width(30.dp),
            )
            ToolButton("+", enabled = song != null) { song?.let { s -> store.updateSong(s.id) { it.copy(tr = (it.tr + 1).coerceIn(-11, 11)) } } }
            Spacer(Modifier.weight(1f))
            ToolButton("Do Re Mi", active = opt.italian) { store.updateOptions { it.copy(notation = if (it.notation == "it") "en" else "it") } }
            ToolButton("A−") { store.updateOptions { it.copy(fs = (it.fs - 2).coerceIn(14, 48)) } }
            ToolButton("A+") { store.updateOptions { it.copy(fs = (it.fs + 2).coerceIn(14, 48)) } }
        }

        // velocità dello scorrimento, salvata sul brano: si registra una volta in prova
        (entry as? Entry.Track)?.song?.takeIf { toolsOpen }?.let { s ->
            Row(
                Modifier.fillMaxWidth().background(Palco.Panel).padding(start = 18.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Scorrimento", color = Palco.Dim, fontFamily = Condensed, fontSize = 14.sp)
                    Text(
                        when (basis.first) {
                            SpeedBasis.BPM -> "base dai BPM (${s.bpm}, 4/4)"
                            SpeedBasis.DURATION -> "base dalla durata del brano"
                            SpeedBasis.FIXED -> "nessun BPM né durata: fissa"
                        },
                        color = Palco.Dim, fontFamily = Condensed, fontSize = 11.sp,
                    )
                }
                ToolButton("−") { store.updateSong(s.id) { it.copy(scroll = stepSpeed(it.scroll, -1)) } }
                Text(
                    "×" + speedLabel(s.scroll), color = Palco.Ink, fontFamily = Mono,
                    fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(54.dp),
                )
                ToolButton("+") { store.updateSong(s.id) { it.copy(scroll = stepSpeed(it.scroll, +1)) } }
            }
        }

        // i sei comandi
        if (opt.bar) Row(
            Modifier.fillMaxWidth().background(Palco.Raise),
        ) {
            Control(Ic.Prev, "Prec") { go(-1) }
            Control(if (playing) Ic.Pause else Ic.Play, if (playing) "Pausa" else "Play", primary = true, active = playing) {
                if (entry is Entry.Track) playing = !playing
            }
            Control(Ic.Stop, "Stop") { playing = false; scope.launch { scroll.animateScrollTo(0) } }
            Control(Ic.Next, "Succ") { go(1) }
            Control(Ic.Struct, "Strut", active = struct) { struct = !struct }
            Control(Ic.Lock, "Blocca", active = locked, alwaysOn = true) { locked = !locked }
        }
    }

    if (overview) PalcoDialog("Regia della serata", "", null, { overview = false }) {
        Column {
            Text("${mmss(elapsed)} trascorsi · circa ${mmss(remaining)} dal brano corrente alla fine del set", color = Palco.Dim, fontFamily = Sans, fontSize = 13.sp)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 13.dp)) {
                items(entries.size) { n ->
                    val e = entries[n]
                    val name = when (e) { is Entry.Track -> e.song.title; is Entry.Mark -> e.label }
                    val detail = when (e) { is Entry.Track -> listOfNotNull(e.song.key.takeIf { it.isNotBlank() }, e.song.bpm?.let { "$it BPM" }, e.song.dur.takeIf { it.isNotBlank() }).joinToString(" · "); is Entry.Mark -> "Pausa · ${e.dur}" }
                    Column(Modifier.fillMaxWidth().clickable { playing = false; index = n; overview = false }.padding(vertical = 11.dp)) {
                        Text("${if (n == index) "▶ " else ""}${n + 1}. $name", color = if (n == index) Palco.Amber else Palco.Ink, fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(detail, color = Palco.Dim, fontFamily = Sans, fontSize = 13.sp)
                    }
                }
            }
            GhostButton("+ Richiesta fuori scaletta", Modifier.padding(top = 15.dp)) { overview = false; requestSong = true }
        }
    }
    if (requestSong) PalcoDialog("Richiesta dal pubblico", "", null, { requestSong = false }) {
        val used = band.curSet?.items.orEmpty().mapNotNull { it.id }.toSet()
        val available = band.songs.filter { it.id !in used }
        Column {
            Text("Scegli dal repertorio: il brano entra in fondo alla scaletta e si apre subito.", color = Palco.Dim, fontFamily = Sans, fontSize = 14.sp)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(available.size) { n ->
                    val s = available[n]
                    Text(s.title, color = Palco.Ink, fontFamily = Sans, fontSize = 19.sp, modifier = Modifier.fillMaxWidth().clickable {
                        val before = store.db.value.band.entries().size
                        store.updateItems { it + SetItem("song", id = s.id) }
                        val after = store.db.value.band.entries().size
                        if (after > before) { playing = false; index = after - 1; requestSong = false }
                    }.padding(vertical = 14.dp))
                }
            }
            if (available.isEmpty()) Text("Tutti i brani sono già nella scaletta: puoi raggiungerli da Regia.", color = Palco.Dim, fontFamily = Sans, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetLineView(line: SheetLine, fs: Int) {
    when (line) {
        SheetLine.Blank -> Spacer(Modifier.height((fs * 0.6f).dp))
        is SheetLine.Section -> Text(
            line.text.uppercase(), color = Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, letterSpacing = 2.4.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        )
        // tablatura: spaziatura fissa e senza andare a capo, se no le note si disallineano
        is SheetLine.Tab -> Text(
            line.text, color = Palco.Ink, fontFamily = Mono, fontWeight = FontWeight.Medium,
            fontSize = (fs * 0.55f).coerceIn(10f, 18f).sp, softWrap = false, maxLines = 1,
        )
        is SheetLine.Lyric -> {
            val anyChord = line.segs.any { it.chord.isNotEmpty() }
            val anyText = line.segs.any { it.text.isNotEmpty() }
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                line.segs.forEach { s ->
                    Column {
                        if (anyChord) Text(
                            s.chord.ifEmpty { " " }, color = Palco.Amber, fontFamily = Mono, fontWeight = FontWeight.SemiBold,
                            fontSize = (fs * 0.92f).sp, softWrap = false, modifier = Modifier.padding(end = 8.dp),
                        )
                        if (anyText) Text(
                            s.text.ifEmpty { " " }, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.Medium,
                            fontSize = fs.sp, softWrap = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(text: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp).clip(shape)
            .then(if (active) Modifier.background(Palco.Amber) else Modifier.border(1.dp, Palco.Outline, shape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = when { active -> Palco.OnAmber; enabled -> Palco.Ink; else -> Palco.Dim },
        )
    }
}

@Composable
private fun RowScope.Control(
    icon: ImageVector, label: String, primary: Boolean = false, active: Boolean = false,
    alwaysOn: Boolean = false, onClick: () -> Unit,
) {
    val bg = when { primary && !active -> Palco.Amber; primary && active -> Palco.AmberDeep; else -> Palco.Raise }
    val fg = when {
        primary && !active -> Palco.OnAmber
        primary && active -> Palco.Amber
        active || alwaysOn && active -> Palco.Amber
        else -> Palco.Ink
    }
    Column(
        Modifier.weight(1f).height(76.dp).background(bg).clickable(onClick = onClick).padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(), color = if (primary) fg else Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp, letterSpacing = 1.1.sp,
        )
    }
}

/** Il battito del brano: `on` è vero per un attimo a ogni colpo, `accent` sul primo di ogni quattro (4/4). */
class BeatState {
    var on by mutableStateOf(false)
    var accent by mutableStateOf(false)
}

/** Fa battere `bpm` (se non è nullo) e, con `sound`, suona il metronomo. Il suono lo conta il thread di `Clicker`;
 *  qui il lampeggio parte quando il primo colpo arriva alle orecchie e poi segue il suo stesso passo. Le attese sono
 *  calcolate dall'inizio, non una dopo l'altra, così il tempo non scivola. Si riparte da capo a ogni cambio di BPM
 *  o quando il suono si accende o si spegne. */
@Composable
private fun rememberBeat(bpm: Int?, sound: Boolean): BeatState {
    val state = remember { BeatState() }
    val clicker = remember { Clicker() }
    DisposableEffect(Unit) { onDispose { clicker.stop() } }
    LaunchedEffect(bpm, sound) {
        state.on = false
        if (bpm == null) return@LaunchedEffect
        try {
            if (sound) {
                clicker.start(bpm)
                delay(clicker.latencyMs)
            }
            val period = 60_000_000_000L / bpm
            val flash = minOf(140L, period / 2_000_000L)
            val start = System.nanoTime()
            var n = 0L
            while (true) {
                state.accent = n % 4 == 0L
                state.on = true
                delay(flash)
                state.on = false
                n++
                val wait = (start + n * period - System.nanoTime()) / 1_000_000
                if (wait > 0) delay(wait)
            }
        } finally {
            if (sound) clicker.stop()
        }
    }
    return state
}

/** Il cerchio del battito, in testata: grande, si accende a tempo. Se il click è abilitato, toccarlo accende
 *  o spegne il tic (con la scritta CLICK e un anello quando suona). */
@Composable
private fun BeatDot(beat: BeatState, soundAvailable: Boolean, soundOn: Boolean, onToggleSound: () -> Unit, modifier: Modifier = Modifier) {
    val size = if (beat.accent) 38.dp else 30.dp
    Column(
        modifier.then(if (soundAvailable) Modifier.clickable(onClick = onToggleSound) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (beat.on) (if (beat.accent) Palco.Ink else Palco.Amber) else Palco.Raise)
                    .then(if (soundOn) Modifier.border(3.dp, Palco.Amber, androidx.compose.foundation.shape.CircleShape) else Modifier),
            )
        }
        if (soundAvailable) Text(
            if (soundOn) "CLICK" else "click", color = if (soundOn) Palco.Amber else Palco.Dim,
            fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
        )
    }
}
