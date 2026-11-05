package it.backline.app.ui

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Campo di testo nello stile Palco: bordo scuro, ambra quando è attivo. */
@Composable
fun PalcoField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    error: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontFamily = Sans) },
        singleLine = singleLine,
        minLines = minLines,
        isError = error,
        supportingText = supporting?.let { { Text(it, fontFamily = Sans, fontSize = 12.sp) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = TextStyle(
            color = Palco.Ink, fontSize = if (mono) 15.sp else 17.sp,
            fontFamily = if (mono) Mono else Condensed, fontWeight = FontWeight.Medium,
        ),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palco.Amber, unfocusedBorderColor = Palco.Outline,
            focusedLabelColor = Palco.Amber, unfocusedLabelColor = Palco.Dim,
            cursorColor = Palco.Amber, focusedTextColor = Palco.Ink, unfocusedTextColor = Palco.Ink,
            errorBorderColor = Palco.Warn, errorLabelColor = Palco.Warn,
            focusedSupportingTextColor = Palco.Dim, unfocusedSupportingTextColor = Palco.Dim,
            errorSupportingTextColor = Palco.Warn,
        ),
    )
}

/** Riga in alto: il marchio e, a destra, il nome del gruppo (porta alle impostazioni). */
@Composable
fun BrandBar(bandName: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Backline", color = Palco.Amber, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).background(Palco.Raise).clickable(onClick = onClick)
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(bandName, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Ic.Chevron, null, tint = Palco.Dim, modifier = Modifier.size(12.dp))
        }
    }
}

/** Titolo grande, come nelle app di sistema. */
@Composable
fun LargeTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text, color = Palco.Ink, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp)
        if (sub != null) Text(sub, color = Palco.Dim, fontFamily = Sans, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** La barra in basso con le tre sezioni: Scaletta, Repertorio, Impostazioni. */
@Composable
fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palco.Panel).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palco.Line))
        Row(Modifier.fillMaxWidth()) {
            listOf(Ic.Struct to "Scaletta", Ic.Note to "Repertorio", Ic.Sliders to "Impostazioni").forEachIndexed { i, (icon, label) ->
                val c = if (i == selected) Palco.Amber else Palco.Dim
                Column(
                    Modifier.weight(1f).clickable { onSelect(i) }.padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, label, tint = c, modifier = Modifier.size(24.dp))
                    Text(label, color = c, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** Selettore a segmenti, come quelli di iOS. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palco.Raise).padding(3.dp)) {
        options.forEachIndexed { i, t ->
            Text(
                t, color = Palco.Ink, fontFamily = Sans, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .then(if (i == selected) Modifier.background(Palco.Outline) else Modifier)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
            )
        }
    }
}

/** Il bottone pieno ambra, come "Avvia il set". */
@Composable
fun AmberButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Palco.Amber else Palco.Outline)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Palco.OnAmber, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

/** Bottone secondario: fondo scuro, testo ambra (rosso se distruttivo). */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (danger) Palco.Warn else Palco.Amber, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Palco.Raise)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

/** Dialogo con lo stile del resto dell'app. */
@Composable
fun PalcoDialog(
    title: String,
    confirmText: String,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palco.Panel,
        titleContentColor = Palco.Ink,
        textContentColor = Palco.Ink,
        title = { Text(title, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = content,
        confirmButton = {
            if (onConfirm != null) Text(
                confirmText.uppercase(), color = Palco.Amber, fontFamily = Sans, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, letterSpacing = 1.2.sp,
                modifier = Modifier.clickable(onClick = onConfirm).padding(12.dp),
            )
        },
        dismissButton = {
            Text(
                "ANNULLA", color = Palco.Dim, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, letterSpacing = 1.2.sp,
                modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
            )
        },
    )
}
