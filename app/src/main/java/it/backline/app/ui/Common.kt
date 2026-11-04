package it.backline.app.ui

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
        label = { Text(label, fontFamily = Condensed) },
        singleLine = singleLine,
        minLines = minLines,
        isError = error,
        supportingText = supporting?.let { { Text(it, fontFamily = Condensed, fontSize = 12.sp) } },
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

/** Barra del marchio, con il nome del gruppo a destra. */
@Composable
fun BrandBar(bandName: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "BACKLINE", color = Palco.Amber, fontFamily = Condensed, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, letterSpacing = 2.6.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).border(1.dp, Palco.Outline, CircleShape)
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(bandName, color = Palco.Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Ic.Chevron, null, tint = Palco.Dim, modifier = Modifier.size(12.dp))
        }
    }
}

/** Le due linguette: Scaletta e Repertorio. */
@Composable
fun MainTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        listOf("SCALETTA", "REPERTORIO").forEachIndexed { i, t ->
            Column(
                Modifier.weight(1f).clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    t, color = if (i == selected) Palco.Ink else Palco.Dim, fontFamily = Condensed,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.8.sp,
                    modifier = Modifier.padding(vertical = 13.dp),
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (i == selected) Palco.Amber else Palco.Line))
            }
        }
    }
}

/** Il bottone pieno ambra, come "Avvia il set". */
@Composable
fun AmberButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Palco.Amber else Palco.Outline)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(), color = Palco.OnAmber, fontFamily = Condensed, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, letterSpacing = 1.4.sp,
        )
    }
}

/** Bottone sottile, per azioni secondarie. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) Palco.Warn else Palco.Ink
    Text(
        text.uppercase(), color = c, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        letterSpacing = 1.4.sp,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, if (danger) Palco.Warn else Palco.Outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
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
        title = { Text(title, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = content,
        confirmButton = {
            if (onConfirm != null) Text(
                confirmText.uppercase(), color = Palco.Amber, fontFamily = Condensed, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, letterSpacing = 1.2.sp,
                modifier = Modifier.clickable(onClick = onConfirm).padding(12.dp),
            )
        },
        dismissButton = {
            Text(
                "ANNULLA", color = Palco.Dim, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, letterSpacing = 1.2.sp,
                modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
            )
        },
    )
}
