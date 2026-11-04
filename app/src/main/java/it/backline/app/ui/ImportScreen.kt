package it.backline.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.backline.app.core.SheetLine
import it.backline.app.core.chordProMeta
import it.backline.app.core.renderSheet
import it.backline.app.core.tabToChordPro
import it.backline.app.data.Song

/* Importazione: si incolla un tab "accordi sopra il testo" (Ultimate Guitar e
   simili) oppure un ChordPro, e si vede subito come verrà letto. Con
   "Continua" il risultato passa all'editor, dove si rifinisce e si salva. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(newId: String, initial: String = "", onContinue: (Song) -> Unit, onClose: () -> Unit) {
    var raw by rememberSaveable { mutableStateOf(initial) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // un file di testo mandato sul tablet in qualunque modo (cavo, cloud, posta…)
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()?.let { raw = it }
    }
    val converted = remember(raw) { tabToChordPro(raw) }
    val preview = remember(converted) { renderSheet(converted) }
    val chords = remember(converted) { Regex("""\[[^\]]+]""").findAll(converted).count() }
    val sections = preview.count { it is SheetLine.Section }
    val lines = preview.count { it is SheetLine.Lyric }
    val ready = raw.isNotBlank() && chords > 0

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Palco.Pitch).systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            Row(
                Modifier.fillMaxWidth().background(Palco.Panel).padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Ic.Close, "Annulla l'importazione", tint = Palco.Dim, modifier = Modifier.size(22.dp))
                }
                Text(
                    "Importa un brano", color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                Text(
                    "CONTINUA", color = if (ready) Palco.OnAmber else Palco.Dim, fontFamily = Condensed,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.4.sp,
                    modifier = Modifier
                        .background(if (ready) Palco.Amber else Palco.Outline, RoundedCornerShape(10.dp))
                        .clickable(enabled = ready) {
                            val meta = chordProMeta(raw)
                            onContinue(Song(id = newId, title = meta.title, artist = meta.artist, key = meta.key, capo = meta.capo, body = converted))
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Incolla un tab con gli accordi sopra il testo, oppure un ChordPro. Gli accordi vanno " +
                        "dentro le parole, nel punto esatto in cui cadevano.",
                    color = Palco.Dim, fontFamily = Condensed, fontSize = 15.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Incolla dagli appunti") { clipboard.getText()?.text?.let { raw = it } }
                    GhostButton("Apri file") { openFile.launch(arrayOf("*/*")) }
                    if (raw.isNotEmpty()) GhostButton("Svuota") { raw = "" }
                }
                PalcoField(raw, { raw = it }, "Testo da importare", singleLine = false, minLines = 6, mono = true)

                if (raw.isNotBlank()) {
                    Text(
                        "COSÌ SI LEGGERÀ", color = Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        if (chords == 0) "Non ho trovato accordi: controlla che siano su righe a parte, sopra il testo."
                        else "$chords accordi · $lines righe · $sections sezioni",
                        color = if (chords == 0) Palco.Warn else Palco.Amber, fontFamily = Mono, fontSize = 13.sp,
                    )
                    if (chords > 0) Column(
                        Modifier.fillMaxWidth().background(Palco.Panel, RoundedCornerShape(10.dp)).padding(14.dp),
                    ) {
                        preview.take(40).forEach { l ->
                            when (l) {
                                SheetLine.Blank -> Spacer(Modifier.height(8.dp))
                                is SheetLine.Section -> Text(
                                    l.text.uppercase(), color = Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp),
                                )
                                is SheetLine.Tab -> Text(
                                    l.text, color = Palco.Ink, fontFamily = Mono, fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp, softWrap = false, maxLines = 1,
                                )
                                is SheetLine.Lyric -> FlowRow {
                                    l.segs.forEach { s ->
                                        Column {
                                            Text(s.chord.ifEmpty { " " }, color = Palco.Amber, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, softWrap = false, modifier = Modifier.padding(end = 6.dp))
                                            Text(s.text.ifEmpty { " " }, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.Medium, fontSize = 16.sp, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                        if (preview.size > 40) Text("…", color = Palco.Dim, fontFamily = Mono)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
