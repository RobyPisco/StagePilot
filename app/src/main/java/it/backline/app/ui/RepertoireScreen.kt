package it.backline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import it.backline.app.core.keyLabel
import it.backline.app.core.mmss
import it.backline.app.core.secs
import it.backline.app.data.DB

/* Il repertorio: tutti i brani del gruppo, con ricerca. Toccare un brano apre
   l'editor; il puntino ambra dice che è nella scaletta corrente. */
@Composable
fun RepertoireScreen(
    db: DB,
    onTab: (Int) -> Unit,
    onOpenSong: (String) -> Unit,
    onNewSong: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
) {
    val band = db.band
    var query by rememberSaveable { mutableStateOf("") }
    val inSet = band.curSet?.items.orEmpty().filter { it.type == "song" }.mapNotNull { it.id }.toSet()
    val q = query.trim().lowercase()
    val shown = band.songs.filter {
        q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
            it.tags.any { t -> t.contains(q) }
    }

    Box(Modifier.fillMaxSize().background(Palco.Pitch).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            BrandBar(band.name, onSettings)
            MainTabs(selected = 1, onSelect = onTab)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))

            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                PalcoField(query, { query = it }, "Cerca titolo, artista o tag")
                Text(
                    "${shown.size} di ${band.songs.size} brani", color = Palco.Dim, fontFamily = Mono, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onOpenSong(s.id) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(8.dp).background(if (s.id in inSet) Palco.Amber else Palco.Line, CircleShape),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                                fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOfNotNull(
                                s.artist.takeIf { it.isNotBlank() },
                                s.bpm?.let { "$it BPM" },
                                s.tags.takeIf { it.isNotEmpty() }?.joinToString(" "),
                            ).joinToString(" · ")
                            if (sub.isNotEmpty()) Text(
                                sub, color = Palco.Dim, fontFamily = Condensed, fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val k = keyLabel(s, db.opt.notation, db.opt.accid)
                        if (k.isNotEmpty()) Text(
                            k, color = Palco.Ink, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(48.dp).border(1.dp, Palco.Outline, RoundedCornerShape(6.dp)).padding(vertical = 4.dp),
                        )
                        Text(
                            if (secs(s.dur) > 0) mmss(secs(s.dur)) else "", color = Palco.Dim, fontFamily = Mono,
                            fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))
                }
            }

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
}
