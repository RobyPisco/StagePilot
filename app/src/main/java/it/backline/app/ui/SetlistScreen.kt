package it.backline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
   brani e segnaposto. */
@Composable
fun SetlistScreen(
    db: DB,
    editing: Boolean,
    onTab: (Int) -> Unit,
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
    val songs = entries.count { it is Entry.Track }
    val marks = entries.size - songs
    val mins = (setDuration(entries) + 30) / 60

    var pickSong by rememberSaveable { mutableStateOf(false) }
    var addMark by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Palco.Pitch).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            BrandBar(band.name, onSettings)
            MainTabs(selected = 0, onSelect = onTab)

            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    "SCALETTA", color = Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, letterSpacing = 2.sp,
                )
                Text(
                    band.curSet?.name ?: "—", color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                    fontSize = 34.sp, lineHeight = 36.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                val parts = buildList {
                    add("$songs ${if (songs == 1) "brano" else "brani"}")
                    add("$mins min")
                    if (marks > 0) add("$marks ${if (marks == 1) "pausa" else "pause"}")
                }
                Text(parts.joinToString("  ·  "), color = Palco.Dim, fontFamily = Mono, fontSize = 13.sp)
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("Do Re Mi", selected = db.opt.italian, onClick = onToggleNotation)
                band.sets.forEach { s -> Chip(s.name, selected = s.id == band.curSet?.id, onClick = { onPickSet(s.id) }) }
                Chip("Modifica", selected = editing, onClick = onToggleEdit)
                Chip("Condividi", selected = false, onClick = onShare)
            }

            if (editing) Row(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton("+ Brano") { pickSong = true }
                GhostButton("+ Segnaposto") { addMark = true }
            }

            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(entries) { i, e ->
                    when (e) {
                        is Entry.Track -> Row(
                            Modifier.fillMaxWidth().heightIn(min = 58.dp)
                                .then(if (editing) Modifier else Modifier.clickable { onOpen(i) })
                                .padding(start = 20.dp, end = if (editing) 8.dp else 20.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                ord[i].toString().padStart(2, '0'), color = Palco.Amber, fontFamily = Mono,
                                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.width(24.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.song.title, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                                    fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                val sub = listOfNotNull(
                                    e.song.artist.takeIf { it.isNotBlank() },
                                    e.song.bpm?.let { "$it BPM" },
                                ).joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, color = Palco.Dim, fontFamily = Condensed, fontSize = 13.sp)
                            }
                            if (editing) EditButtons(i, entries.lastIndex, onMove, onRemove) else {
                                val k = keyLabel(e.song, db.opt.notation, db.opt.accid)
                                if (k.isNotEmpty()) Text(
                                    k, color = Palco.Ink, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(48.dp).border(1.dp, Palco.Outline, RoundedCornerShape(6.dp)).padding(vertical = 4.dp),
                                )
                                Text(
                                    if (secs(e.song.dur) > 0) mmss(secs(e.song.dur)) else "", color = Palco.Dim,
                                    fontFamily = Mono, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp),
                                )
                            }
                        }
                        is Entry.Mark -> Row(
                            Modifier.fillMaxWidth().heightIn(min = 50.dp).background(Palco.Panel)
                                .then(if (editing) Modifier else Modifier.clickable { onOpen(i) })
                                .padding(start = 20.dp, end = if (editing) 8.dp else 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text("—", color = Palco.Dim, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.width(24.dp))
                            Text(
                                e.label, color = Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.Medium,
                                fontStyle = FontStyle.Italic, fontSize = 19.sp, modifier = Modifier.weight(1f),
                            )
                            if (editing) EditButtons(i, entries.lastIndex, onMove, onRemove) else Text(
                                if (secs(e.dur) > 0) mmss(secs(e.dur)) else "", color = Palco.Dim, fontFamily = Mono,
                                fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp),
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))
                }
            }

            Box(Modifier.fillMaxWidth().background(Palco.Pitch).padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (editing) AmberButton("Fatto", onClick = onToggleEdit)
                else Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (entries.isNotEmpty()) Palco.Amber else Palco.Outline)
                        .clickable(enabled = entries.isNotEmpty()) { onOpen(0) }.padding(vertical = 17.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.Play, null, tint = Palco.OnAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "AVVIA IL SET", color = Palco.OnAmber, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                        fontSize = 17.sp, letterSpacing = 1.4.sp,
                    )
                }
            }
        }
    }

    if (pickSong) {
        val used = band.curSet?.items.orEmpty().filter { it.type == "song" }.mapNotNull { it.id }.toSet()
        val free = band.songs.filter { it.id !in used }
        PalcoDialog(title = "Aggiungi un brano", confirmText = "", onConfirm = null, onDismiss = { pickSong = false }) {
            if (free.isEmpty()) Text(
                "Tutti i brani del repertorio sono già in scaletta.", color = Palco.Dim, fontFamily = Condensed, fontSize = 15.sp,
            ) else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(free, key = { it.id }) { s ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onAddSong(s.id); pickSong = false }.padding(vertical = 10.dp),
                    ) {
                        Text(s.title, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        if (s.artist.isNotBlank()) Text(s.artist, color = Palco.Dim, fontFamily = Condensed, fontSize = 13.sp)
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

@Composable
private fun EditButtons(i: Int, last: Int, onMove: (Int, Int) -> Unit, onRemove: (Int) -> Unit) {
    Row {
        SmallIcon(Ic.Up, "Sposta su", enabled = i > 0) { onMove(i, i - 1) }
        SmallIcon(Ic.Chevron, "Sposta giù", enabled = i < last) { onMove(i, i + 1) }
        SmallIcon(Ic.Close, "Togli dalla scaletta", tint = Palco.Warn) { onRemove(i) }
    }
}

@Composable
private fun SmallIcon(
    icon: ImageVector, desc: String, enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = Palco.Ink, onClick: () -> Unit,
) {
    Box(Modifier.size(44.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = if (enabled) tint else Palco.Outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Text(
        text.uppercase(),
        color = if (selected) Palco.OnAmber else Palco.Ink,
        fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.clip(shape)
            .then(if (selected) Modifier.background(Palco.Amber) else Modifier.border(1.dp, Palco.Outline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
