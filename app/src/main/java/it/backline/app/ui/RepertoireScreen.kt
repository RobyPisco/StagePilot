package it.backline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.backline.app.core.PC
import it.backline.app.core.keyLabel
import it.backline.app.core.mmss
import it.backline.app.core.secs
import it.backline.app.core.transpose
import it.backline.app.data.DB

/* Il repertorio: tutti i brani del gruppo, con ricerca. Toccare un brano apre
   l'editor; il puntino ambra dice che è nella scaletta corrente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepertoireScreen(
    db: DB,
    onOpenSong: (String) -> Unit,
    onNewSong: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onSort: (String) -> Unit,
    onDeleteSong: (String) -> Unit,
) {
    var toDelete by remember { mutableStateOf<String?>(null) }
    val band = db.band
    var query by rememberSaveable { mutableStateOf("") }
    val inSet = band.curSet?.items.orEmpty().filter { it.type == "song" }.mapNotNull { it.id }.toSet()
    val q = query.trim().lowercase()
    val found = band.songs.filter {
        q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
            it.tags.any { t -> t.contains(q) }
    }
    val shown = when (db.opt.sort) {
        "title" -> found.sortedBy { it.title.lowercase() }
        "artist" -> found.sortedWith(compareBy({ it.artist.lowercase().ifEmpty { "￿" } }, { it.title.lowercase() }))
        "key" -> found.sortedWith(compareBy({ it.key.isEmpty() }, { PC[transpose(it.key, it.tr).takeWhile { c -> c != 'm' }] ?: 99 }, { it.title.lowercase() }))
        "bpm" -> found.sortedWith(compareBy({ it.bpm == null }, { it.bpm ?: 0 }, { it.title.lowercase() }))
        else -> found
    }

    Box(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            BrandBar(band.name, onSettings)
            LargeTitle("Repertorio", "${shown.size} di ${band.songs.size} brani")

            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                PalcoField(query, { query = it }, "Cerca titolo, artista o tag")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("add" to "Inserimento", "title" to "Titolo", "artist" to "Artista", "key" to "Tonalità", "bpm" to "BPM")
                        .forEach { (id, label) -> Chip(label, selected = db.opt.sort == id) { onSort(id) } }
                }
            }

            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)) {
                itemsIndexed(shown, key = { _, it -> it.id }) { idx, s ->
                    val r = 14.dp
                    val shape = RoundedCornerShape(
                        topStart = if (idx == 0) r else 0.dp, topEnd = if (idx == 0) r else 0.dp,
                        bottomStart = if (idx == shown.lastIndex) r else 0.dp, bottomEnd = if (idx == shown.lastIndex) r else 0.dp,
                    )
                    Column(Modifier.padding(horizontal = 16.dp)) {
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
                        // la riga torna al suo posto: si elimina solo dopo la conferma
                        if (it != SwipeToDismissBoxValue.Settled) toDelete = s.id
                        false
                    })
                    SwipeToDismissBox(
                        modifier = Modifier.clip(shape),
                        state = dismiss,
                        backgroundContent = {
                            val toRight = dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                            Box(
                                Modifier.fillMaxSize().background(Palco.Warn).padding(horizontal = 24.dp),
                                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
                            ) {
                                Text(
                                    "ELIMINA", color = Palco.OnAmber, fontFamily = Sans, fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp, letterSpacing = 1.6.sp,
                                )
                            }
                        },
                    ) {
                    Row(
                        Modifier.fillMaxWidth().background(Palco.Panel).heightIn(min = 60.dp).clickable { onOpenSong(s.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(8.dp).background(if (s.id in inSet) Palco.Amber else Palco.Line, CircleShape),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOfNotNull(
                                s.artist.takeIf { it.isNotBlank() },
                                s.bpm?.let { "$it BPM" },
                                s.tags.takeIf { it.isNotEmpty() }?.joinToString(" "),
                            ).joinToString(" · ")
                            if (sub.isNotEmpty()) Text(
                                sub, color = Palco.Dim, fontFamily = Sans, fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val k = keyLabel(s, db.opt.notation, db.opt.accid)
                        if (k.isNotEmpty()) Text(
                            k, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(48.dp).clip(RoundedCornerShape(8.dp)).background(Palco.Raise).padding(vertical = 5.dp),
                        )
                        Text(
                            if (secs(s.dur) > 0) mmss(secs(s.dur)) else "", color = Palco.Dim, fontFamily = Sans,
                            fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp),
                        )
                    }
                    }
                    if (idx < shown.lastIndex) Box(Modifier.fillMaxWidth().background(Palco.Panel).padding(start = 38.dp)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))
                    }
                    }
                }
            }

            Text(
                "Scorri una riga di lato per eliminare il brano", color = Palco.Dim, fontFamily = Sans, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton("Importa", Modifier.padding(vertical = 2.dp), onClick = onImport)
                AmberButton("+ Nuovo brano", Modifier.weight(1f), onClick = onNewSong)
            }
        }
    }
    toDelete?.let { id ->
        val song = band.songs.firstOrNull { it.id == id }
        if (song == null) toDelete = null else PalcoDialog(
            "Eliminare il brano?", "Elimina", { onDeleteSong(id); toDelete = null }, { toDelete = null },
        ) {
            Text(
                "«${song.title}» sparisce dal repertorio e da tutte le scalette.",
                color = Palco.Dim, fontFamily = Sans, fontSize = 15.sp,
            )
        }
    }
}
