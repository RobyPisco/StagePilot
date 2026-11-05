package it.backline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.backline.app.core.keyLabel
import it.backline.app.core.mmss
import it.backline.app.core.ordinals
import it.backline.app.core.secs
import it.backline.app.core.setDuration
import it.backline.app.data.DB
import it.backline.app.data.Entry
import it.backline.app.data.entries

/* La scaletta: la prima schermata. Grafica Palco. In modalità Modifica le
   righe mostrano i comandi per spostare e togliere, e si possono aggiungere
   brani e segnaposto. In verticale tutto si impila; in orizzontale (tablet sul
   leggio) titolo e comandi stanno a sinistra e la lista occupa tutta l'altezza
   a destra, così in vista ci sono molti brani e non solo due. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetlistScreen(
    db: DB,
    editing: Boolean,
    onOpen: (Int) -> Unit,
    onToggleNotation: () -> Unit,
    onPickSet: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddSong: (String) -> Unit,
    onAddMark: (String, String) -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
) {
    val band = db.band
    val entries = band.entries()
    val ord = ordinals(entries)

    var pickSong by rememberSaveable { mutableStateOf(false) }
    var addMark by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding()) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp

        val listState = rememberLazyListState()
        val drag = remember { DragState() }
        val handlePx = with(LocalDensity.current) { 64.dp.toPx() }
        // vicino al bordo la lista scorre da sola, e la riga resta sotto il dito
        LaunchedEffect(drag.idx >= 0) {
            while (drag.idx >= 0) {
                if (drag.scroll != 0f) drag.offset += listState.scrollBy(drag.scroll)
                delay(16)
            }
        }
        val list: @Composable (Modifier) -> Unit = { m ->
            LazyColumn(m.dragReorder(listState, drag, editing, handlePx, onMove), state = listState, contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)) {
                itemsIndexed(entries) { i, e -> EntryRow(i, e, ord[i], editing, db, entries.size, drag, onOpen, onRemove) }
            }
        }
        val start: @Composable () -> Unit = {
            Box(Modifier.fillMaxWidth().background(Palco.Pitch).padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (editing) AmberButton("Fatto", onClick = onToggleEdit)
                else Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (entries.isNotEmpty()) Palco.Amber else Palco.Outline)
                        .clickable(enabled = entries.isNotEmpty()) { onOpen(0) }.padding(vertical = 17.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.Play, null, tint = Palco.OnAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Avvia il set", color = Palco.OnAmber, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
            }
        }

        if (wide) Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(400.dp).fillMaxHeight()) {
                BrandBar(band.name, onSettings)
                SetTitle(db, entries)
                FlowRow(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { SetChips(db, editing, onToggleNotation, onPickSet, onToggleEdit, onShare) }
                if (editing) EditActions({ pickSong = true }, { addMark = true })
                Spacer(Modifier.weight(1f))
                start()
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palco.Line))
            list(Modifier.weight(1f).fillMaxHeight())
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
                BrandBar(band.name, onSettings)
                SetTitle(db, entries)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { SetChips(db, editing, onToggleNotation, onPickSet, onToggleEdit, onShare) }
                if (editing) EditActions({ pickSong = true }, { addMark = true })
                list(Modifier.weight(1f))
                start()
            }
        }
    }

    if (pickSong) {
        val used = band.curSet?.items.orEmpty().filter { it.type == "song" }.mapNotNull { it.id }.toSet()
        val free = band.songs.filter { it.id !in used }
        PalcoDialog(title = "Aggiungi un brano", confirmText = "", onConfirm = null, onDismiss = { pickSong = false }) {
            if (free.isEmpty()) Text(
                "Tutti i brani del repertorio sono già in scaletta.", color = Palco.Dim, fontFamily = Sans, fontSize = 15.sp,
            ) else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(free, key = { it.id }) { s ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onAddSong(s.id); pickSong = false }.padding(vertical = 10.dp),
                    ) {
                        Text(s.title, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        if (s.artist.isNotBlank()) Text(s.artist, color = Palco.Dim, fontFamily = Sans, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (addMark) {
        var label by rememberSaveable { mutableStateOf("Pausa") }
        var dur by rememberSaveable { mutableStateOf("") }
        val ok = label.isNotBlank() && (dur.isBlank() || secs(dur) > 0)
        PalcoDialog(
            title = "Aggiungi un segnaposto", confirmText = "Aggiungi",
            onConfirm = if (ok) ({ onAddMark(label.trim(), dur.trim()); addMark = false }) else null,
            onDismiss = { addMark = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PalcoField(label, { label = it }, "Nome", supporting = "Pausa, Cambio chitarra, Presentazione…")
                PalcoField(
                    dur, { dur = it.filter { c -> c.isDigit() || c == ':' }.take(5) }, "Durata", keyboard = KeyboardType.Text,
                    supporting = "es. 5:00", error = dur.isNotBlank() && secs(dur) == 0,
                )
            }
        }
    }
}

/** Stato del trascinamento di una riga: quale e di quanto è spostata rispetto al suo posto. */
private class DragState {
    var idx by mutableIntStateOf(-1)
    var offset by mutableFloatStateOf(0f)
    var scroll by mutableFloatStateOf(0f)
}

/** Si trascina la riga dalla maniglia a destra (solo in Modifica): il dito la porta su e giù, e quando
 *  il centro supera un'altra riga le due si scambiano. Fuori dalla maniglia la lista scorre normale. */
private fun Modifier.dragReorder(
    state: LazyListState, drag: DragState, enabled: Boolean, handlePx: Float, onMove: (Int, Int) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x < size.width - handlePx) return@awaitEachGesture
        val first = state.layoutInfo.visibleItemsInfo
            .firstOrNull { down.position.y >= it.offset && down.position.y < it.offset + it.size }
            ?: return@awaitEachGesture
        down.consume()
        drag.idx = first.index
        drag.offset = 0f
        var last = down.position.y
        var stamp: Any? = null
        try {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                if (!ch.pressed) break
                drag.offset += ch.position.y - last
                last = ch.position.y
                drag.scroll = when {
                    ch.position.y < 90f -> -18f
                    ch.position.y > size.height - 90f -> 18f
                    else -> 0f
                }
                // dopo uno scambio si aspetta che la lista si ridisegni prima di valutarne un altro
                val info = state.layoutInfo
                if (info !== stamp) {
                    val cur = info.visibleItemsInfo.firstOrNull { it.index == drag.idx }
                    if (cur != null) {
                        val centre = cur.offset + cur.size / 2f + drag.offset
                        val tgt = info.visibleItemsInfo
                            .firstOrNull { it.index != cur.index && centre >= it.offset && centre < it.offset + it.size }
                        if (tgt != null) {
                            val newOff = if (tgt.index > cur.index) tgt.offset + tgt.size - cur.size else tgt.offset
                            drag.offset += cur.offset - newOff
                            onMove(cur.index, tgt.index)
                            drag.idx = tgt.index
                            stamp = info
                        }
                    }
                }
            }
        } finally {
            drag.idx = -1
            drag.offset = 0f
            drag.scroll = 0f
        }
    }
}

@Composable
private fun SetTitle(db: DB, entries: List<Entry>) {
    val songs = entries.count { it is Entry.Track }
    val marks = entries.size - songs
    val mins = (setDuration(entries) + 30) / 60
    val parts = buildList {
        add("$songs ${if (songs == 1) "brano" else "brani"}")
        add("$mins min")
        if (marks > 0) add("$marks ${if (marks == 1) "pausa" else "pause"}")
    }
    LargeTitle(db.band.curSet?.name ?: "—", parts.joinToString("  ·  "))
}

@Composable
private fun SetChips(
    db: DB, editing: Boolean, onToggleNotation: () -> Unit, onPickSet: (String) -> Unit,
    onToggleEdit: () -> Unit, onShare: () -> Unit,
) {
    val band = db.band
    Chip("Do Re Mi", selected = db.opt.italian, onClick = onToggleNotation)
    band.sets.forEach { s -> Chip(s.name, selected = s.id == band.curSet?.id, onClick = { onPickSet(s.id) }) }
    Chip("Modifica", selected = editing, onClick = onToggleEdit)
    Chip("Condividi", selected = false, onClick = onShare)
}

@Composable
private fun EditActions(onAddSong: () -> Unit, onAddMark: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GhostButton("+ Brano", onClick = onAddSong)
        GhostButton("+ Segnaposto", onClick = onAddMark)
    }
}

@Composable
private fun EntryRow(
    i: Int, e: Entry, number: Int, editing: Boolean, db: DB, count: Int, drag: DragState,
    onOpen: (Int) -> Unit, onRemove: (Int) -> Unit,
) {
    // le righe formano una scheda con gli angoli arrotondati: tondi in alto sulla prima, in basso sull'ultima
    val r = 14.dp
    val shape = RoundedCornerShape(
        topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp,
        bottomStart = if (i == count - 1) r else 0.dp, bottomEnd = if (i == count - 1) r else 0.dp,
    )
    val dragging = drag.idx == i
    val lift = Modifier.zIndex(if (dragging) 1f else 0f)
        .graphicsLayer { translationY = if (drag.idx == i) drag.offset else 0f }
        .clip(shape).background(if (dragging) Palco.Raise else Palco.Panel)
    Column(Modifier.padding(horizontal = 16.dp)) {
        when (e) {
            is Entry.Track -> Row(
                lift.fillMaxWidth().heightIn(min = 60.dp)
                    .then(if (editing) Modifier else Modifier.clickable { onOpen(i) })
                    .padding(start = 16.dp, end = if (editing) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    number.toString(), color = Palco.Amber, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        e.song.title, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val sub = listOfNotNull(
                        e.song.artist.takeIf { it.isNotBlank() },
                        e.song.bpm?.let { "$it BPM" },
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, color = Palco.Dim, fontFamily = Sans, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (editing) EditButtons(i, onRemove) else {
                    val k = keyLabel(e.song, db.opt.notation, db.opt.accid)
                    if (k.isNotEmpty()) Text(
                        k, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(46.dp).clip(RoundedCornerShape(8.dp)).background(Palco.Raise).padding(vertical = 5.dp),
                    )
                    Text(
                        if (secs(e.song.dur) > 0) mmss(secs(e.song.dur)) else "", color = Palco.Dim,
                        fontFamily = Sans, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(38.dp),
                    )
                }
            }
            is Entry.Mark -> Row(
                lift.fillMaxWidth().heightIn(min = 50.dp)
                    .then(if (editing) Modifier else Modifier.clickable { onOpen(i) })
                    .padding(start = 16.dp, end = if (editing) 4.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("–", color = Palco.Dim, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                Text(
                    e.label, color = Palco.Dim, fontFamily = Sans, fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.weight(1f),
                )
                if (editing) EditButtons(i, onRemove) else Text(
                    if (secs(e.dur) > 0) mmss(secs(e.dur)) else "", color = Palco.Dim, fontFamily = Sans,
                    fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp),
                )
            }
        }
        if (i < count - 1) Box(Modifier.fillMaxWidth().background(Palco.Panel).padding(start = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))
        }
    }
}

@Composable
private fun EditButtons(i: Int, onRemove: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallIcon(Ic.Close, "Togli dalla scaletta", tint = Palco.Warn) { onRemove(i) }
        // la maniglia non fa niente da sola: il trascinamento lo gestisce la lista (dragReorder)
        Box(Modifier.size(width = 56.dp, height = 44.dp), contentAlignment = Alignment.Center) {
            Icon(Ic.Grip, "Trascina per spostare", tint = Palco.Dim, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SmallIcon(
    icon: ImageVector, desc: String, enabled: Boolean = true,
    tint: Color = Palco.Ink, onClick: () -> Unit,
) {
    Box(Modifier.size(44.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = if (enabled) tint else Palco.Outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Palco.OnAmber else Palco.Ink,
        fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp,
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) Palco.Amber else Palco.Raise)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
