package com.wangzan101.mika.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaRadius
import com.wangzan101.mika.ui.theme.MikaSize

/** Rounded tonal shape shared by Orbit's form fields and note cards. */
val OrbitFieldShape = RoundedCornerShape(MikaRadius.Lg)

/**
 * Orbit's text field: a small label above a rounded tonal box (no Material outline).
 * [modifier] lands on the editable text itself, so test tags and text input reach it directly.
 */
@Composable
fun OrbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(label, Modifier.padding(start = MikaSpace.S4, bottom = MikaSpace.S8), style = MikaType.Caption, color = c.ink2)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .background(c.fill, OrbitFieldShape).border(1.dp, c.hair, OrbitFieldShape)
                .keepsKeyboard()
                .padding(start = MikaSpace.S16, end = if (trailing == null) MikaSpace.S16 else MikaSpace.S8, top = MikaSpace.S4, bottom = MikaSpace.S4),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(Modifier.weight(1f).padding(vertical = MikaSpace.S12)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = MikaType.Body, color = c.ink3, maxLines = if (singleLine) 1 else 3)
                }
                BasicTextField(
                    value, onValueChange,
                    modifier.fillMaxWidth().semantics { contentDescription = label },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 2,
                    maxLines = if (singleLine) 1 else 6,
                    textStyle = MikaType.Body.copy(color = c.ink, textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                )
            }
            if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** A quiet explanatory card: a leading icon and muted text, for privacy or how-it-works notes. */
@Composable
fun OrbitNote(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    Row(
        modifier.fillMaxWidth().background(c.fill, OrbitFieldShape).padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(MikaSize.TileIcon), tint = c.ink3)
        Text(text, Modifier.weight(1f), style = MikaType.Caption, color = c.ink2)
    }
}
