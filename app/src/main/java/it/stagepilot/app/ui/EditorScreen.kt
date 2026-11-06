package it.stagepilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.stagepilot.app.core.looksRawTab
import it.stagepilot.app.core.normalizeBody
import it.stagepilot.app.core.secs
import it.stagepilot.app.core.tabToChordPro
import it.stagepilot.app.core.toEnglish
import it.stagepilot.app.data.Band
import it.stagepilot.app.data.Song

/* L'editor del brano. Il testo si scrive con gli accordi tra [parentesi
   quadre] e le sezioni su righe che iniziano con #. Gli accordi si possono
   scrivere anche in Do Re Mi: si salvano sempre in notazione inglese. */
@Composable
fun EditorScreen(
    band: Band,
    song: Song?,
    draft: Song? = null,
    inSetInitial: Boolean,
    newId: String,
    onSave: (Song, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    // un brano nuovo può partire da una bozza (l'importazione): si usano i suoi valori
    val src = song ?: draft
    val key = song?.id ?: "new"
    var title by rememberSaveable(key) { mutableStateOf(src?.title ?: "") }
    var artist by rememberSaveable(key) { mutableStateOf(src?.artist ?: "") }
    var tone by rememberSaveable(key) { mutableStateOf(src?.key ?: "") }
    var bpm by rememberSaveable(key) { mutableStateOf(src?.bpm?.toString() ?: "") }
    var dur by rememberSaveable(key) { mutableStateOf(src?.dur ?: "") }
    var tags by rememberSaveable(key) { mutableStateOf(src?.tags?.joinToString(", ") ?: "") }
    var capo by rememberSaveable(key) { mutableStateOf(src?.capo?.toString() ?: "0") }
    var lead by rememberSaveable(key) { mutableStateOf(src?.lead?.toString() ?: "0") }
    var body by rememberSaveable(key) { mutableStateOf(src?.body ?: "") }
    var noteAll by rememberSaveable(key) { mutableStateOf(src?.notes?.get("Tutti") ?: "") }
    var noteMe by rememberSaveable(key) { mutableStateOf(src?.notes?.get(band.me) ?: "") }
    var inSet by rememberSaveable(key) { mutableStateOf(inSetInitial) }
    var confirmDelete by remember { mutableStateOf(false) }

    val durOk = dur.isBlank() || secs(dur) > 0
    val canSave = title.isNotBlank() && durOk

    fun save() {
        val notes = (src?.notes ?: emptyMap()).toMutableMap().apply {
            if (noteAll.isBlank()) remove("Tutti") else put("Tutti", noteAll.trim())
            if (band.me != "Tutti") { if (noteMe.isBlank()) remove(band.me) else put(band.me, noteMe.trim()) }
        }
        val saved = (song ?: draft?.copy(id = newId) ?: Song(id = newId)).copy(
            title = title.trim(),
            artist = artist.trim(),
            key = toEnglish(tone.trim()),
            bpm = bpm.trim().toIntOrNull(),
            dur = dur.trim(),
            tags = tags.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
            capo = (capo.toIntOrNull() ?: 0).coerceIn(0, 11),
            lead = (lead.toIntOrNull() ?: 0).coerceIn(0, 60),
            body = normalizeBody(body),
            notes = notes,
        )
        onSave(saved, inSet)
    }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Palco.Pitch).systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            Row(
                Modifier.fillMaxWidth().background(Palco.Panel).padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Ic.Close, "Chiudi senza salvare", tint = Palco.Dim, modifier = Modifier.size(22.dp))
                }
                Text(
                    if (song == null) "Nuovo brano" else "Modifica brano", color = Palco.Ink, fontFamily = Condensed,
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                Text(
                    "SALVA", color = if (canSave) Palco.OnAmber else Palco.Dim, fontFamily = Condensed,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.4.sp,
                    modifier = Modifier
                        .background(if (canSave) Palco.Amber else Palco.Outline, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .clickable(enabled = canSave, onClick = ::save)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PalcoField(title, { title = it }, "Titolo")
                PalcoField(artist, { artist = it }, "Artista")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PalcoField(tone, { tone = it }, "Tonalità", modifier = Modifier.weight(1f), supporting = "Am, Do, F#m…")
                    PalcoField(bpm, { bpm = it.filter(Char::isDigit).take(3) }, "BPM", modifier = Modifier.weight(1f), keyboard = KeyboardType.Number)
                    PalcoField(
                        dur, { dur = it.filter { c -> c.isDigit() || c == ':' }.take(5) }, "Durata", modifier = Modifier.weight(1f),
                        keyboard = KeyboardType.Text, supporting = "3:40", error = !durOk,
                    )
                }
                PalcoField(tags, { tags = it }, "Tag", supporting = "separati da virgola: originale, lenta")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PalcoField(
                        capo, { capo = it.filter(Char::isDigit).take(2) }, "Capotasto", modifier = Modifier.weight(1f),
                        keyboard = KeyboardType.Number, supporting = "0 = nessuno",
                    )
                    PalcoField(
                        lead, { lead = it.filter(Char::isDigit).take(2) }, "Attesa (s)", modifier = Modifier.weight(1f),
                        keyboard = KeyboardType.Number, supporting = "prima dello scorrimento",
                    )
                }

                Text(
                    "TESTO E ACCORDI", color = Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp),
                )
                PalcoField(
                    body, { body = it }, "Chart", singleLine = false, minLines = 10, mono = true,
                    supporting = "Accordi tra [quadre]. Le righe con # sono sezioni.",
                )
                // un tab incollato grezzo (accordi sopra il testo) si legge male: si offre la conversione
                if (looksRawTab(body)) {
                    Text(
                        "Ci sono righe di accordi senza le quadre: così a schermo finirebbero come testo cantato.",
                        color = Palco.Warn, fontFamily = Condensed, fontSize = 14.sp,
                    )
                    GhostButton("Converti accordi sopra il testo") { body = tabToChordPro(body) }
                }

                Text(
                    "NOTE", color = Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp),
                )
                PalcoField(noteAll, { noteAll = it }, "Per tutta la band", singleLine = false, minLines = 2)
                if (band.me != "Tutti") PalcoField(noteMe, { noteMe = it }, "Solo per me (${band.me})", singleLine = false, minLines = 2)

                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "In scaletta: ${band.curSet?.name ?: "—"}", color = Palco.Ink, fontFamily = Condensed,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = inSet, onCheckedChange = { inSet = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Palco.OnAmber, checkedTrackColor = Palco.Amber,
                            uncheckedThumbColor = Palco.Dim, uncheckedTrackColor = Palco.Raise, uncheckedBorderColor = Palco.Outline,
                        ),
                    )
                }

                if (onDelete != null) {
                    Spacer(Modifier.height(6.dp))
                    GhostButton("Elimina brano", danger = true) { confirmDelete = true }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete && onDelete != null) PalcoDialog(
        title = "Eliminare il brano?",
        confirmText = "Elimina",
        onConfirm = { confirmDelete = false; onDelete() },
        onDismiss = { confirmDelete = false },
    ) {
        Text(
            "«${song?.title}» sparisce dal repertorio e da tutte le scalette. Non si può annullare.",
            color = Palco.Dim, fontFamily = Condensed, fontSize = 15.sp,
        )
    }
}
