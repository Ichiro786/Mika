package com.wangzan101.mika.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaColors
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/** 分组小标题:13 / 500,辅助文字色。 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp, bottom = 8.dp), style = MikaType.Label, color = MikaTheme.colors.ink3)
}

/** 分组下面的说明,和卡片里的文字对齐。 */
@Composable
fun FooterNote(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text, modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        style = MikaType.Hint, color = color.takeOrElse { MikaTheme.colors.ink3 },
    )
}

/** 图标块的底色和图形色。默认中性;只有状态才上色。 */
enum class TileTone {
    Neutral, Accent, Ok, Warn, Bad;

    fun colors(c: MikaColors): Pair<Color, Color> = when (this) {
        Neutral -> c.neutralSoft to c.ink
        Accent -> c.accentSoft to c.accent
        Ok -> c.okSoft to c.ok
        Warn -> c.warnSoft to c.warn
        Bad -> c.badSoft to c.bad
    }
}

/**
 * The icon block at the start of a row: a small glass tile. A row's colour ([tint]) no longer fills the tile solid
 * (the old phone-settings look); it shows as a soft wash from the top with a hairline edge, and the glyph takes a
 * lighter shade of it. Without a tint the tile is neutral glass and the glyph is ink.
 */
@Composable
fun IconTile(icon: ImageVector, tone: TileTone = TileTone.Neutral, tint: Color? = null) {
    val c = MikaTheme.colors
    val hue = tint ?: tone.colors(c).second.takeIf { tone != TileTone.Neutral }
    val glyph = when {
        hue == null -> c.ink
        c.isDark -> lerp(hue, Color.White, 0.38f)
        else -> lerp(hue, Color.Black, 0.12f)
    }
    val wash = if (hue == null) {
        Brush.verticalGradient(listOf(c.ink.copy(alpha = if (c.isDark) 0.13f else 0.08f), c.ink.copy(alpha = if (c.isDark) 0.05f else 0.03f)))
    } else {
        Brush.verticalGradient(listOf(hue.copy(alpha = if (c.isDark) 0.34f else 0.22f), hue.copy(alpha = if (c.isDark) 0.12f else 0.08f)))
    }
    val edge = Brush.verticalGradient(listOf((hue ?: c.ink).copy(alpha = if (c.isDark) 0.38f else 0.22f), Color.Transparent))
    Box(
        Modifier.size(MikaSize.Tile).background(wash, MikaShapes.Tile).border(0.75.dp, edge, MikaShapes.Tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(MikaSize.TileIcon), tint = glyph)
    }
}

/** 行与行之间的分隔线,从 [start] 画到右边;分组首行([first])不画。 */
private fun Modifier.topHairline(first: Boolean, start: Dp, color: Color): Modifier =
    if (first) this
    else drawBehind {
        val y = 0.25.dp.toPx()
        drawLine(color, Offset(start.toPx(), y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
    }

/** 有图标块时文字从这里开始:左边距 16 + 图标块 30 + 间距 14。 */
private val TextStartWithIcon = 60.dp
private val TextStartPlain = 16.dp

/**
 * 列表行:左边图标块可选,中间标题和说明,右边是 [trailing](值、胶囊、开关、箭头)。
 * [detail] 是说明下面再一行的小字(如「上次结果」),颜色由 [detailColor] 定。
 * [first] 为分组首行时不画上分隔线;[onClick] 为空时整行不可点。
 */
@Composable
fun ListRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    first: Boolean = false,
    titleColor: Color = Color.Unspecified,
    tone: TileTone = TileTone.Neutral,
    detail: String? = null,
    detailColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    /** A solid colour for the icon tile with a white glyph, as on the Settings home; null keeps [tone]. */
    tint: Color? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .topHairline(first, if (icon != null) TextStartWithIcon else TextStartPlain, c.hair)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = MikaSize.RowMin)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) IconTile(icon, tone, tint)
        TextAndTrailing(
            text = {
                Column(Modifier.padding(vertical = 11.dp)) {
                    Text(title, style = MikaType.Body, color = titleColor.takeOrElse { c.ink }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrEmpty()) Text(subtitle, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = c.ink3)
                    if (!detail.isNullOrEmpty()) Text(detail, Modifier.padding(top = 2.dp), style = MikaType.Hint, color = detailColor.takeOrElse { c.ink3 })
                }
            },
            trailing = trailing,
        )
    }
}

/**
 * The text column and the row's trailing items (value, pill, switch, chevron). The trailing part is measured
 * first but never gets more than [TrailingShare] of the room, so a long value ellipsizes instead of squeezing
 * the title and subtitle into one word per line; the text takes everything else.
 */
@Composable
private fun RowScope.TextAndTrailing(text: @Composable () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    Layout(
        content = {
            Box { text() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { trailing() }
        },
        modifier = Modifier.weight(1f),
    ) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        if (!constraints.hasBoundedWidth) {
            val t = measurables[1].measure(Constraints(maxHeight = constraints.maxHeight))
            val b = measurables[0].measure(Constraints(maxHeight = constraints.maxHeight))
            val h = maxOf(b.height, t.height, constraints.minHeight)
            val w = b.width + (if (t.width > 0) gap else 0) + t.width
            return@Layout layout(w, h) { b.place(0, (h - b.height) / 2); t.place(w - t.width, (h - t.height) / 2) }
        }
        val width = constraints.maxWidth
        val tail = measurables[1].measure(Constraints(maxWidth = (width * TrailingShare).toInt(), maxHeight = constraints.maxHeight))
        val textWidth = (width - tail.width - if (tail.width > 0) gap else 0).coerceAtLeast(0)
        val body = measurables[0].measure(Constraints(minWidth = textWidth, maxWidth = textWidth, maxHeight = constraints.maxHeight))
        val height = maxOf(body.height, tail.height, constraints.minHeight)
        layout(width, height) {
            body.place(0, (height - body.height) / 2)
            tail.place(width - tail.width, (height - tail.height) / 2)
        }
    }
}

private const val TrailingShare = 0.4f

enum class ValueTone { Plain, Ok, Warn, Bad, Link }

/** 行尾的值。 */
@Composable
fun RowValue(text: String, tone: ValueTone = ValueTone.Plain) {
    val c = MikaTheme.colors
    Text(
        text, style = MikaType.Value,
        fontWeight = if (tone == ValueTone.Link) FontWeight.Medium else FontWeight.Normal,
        color = when (tone) {
            ValueTone.Plain -> c.ink3
            ValueTone.Ok -> c.ok
            ValueTone.Warn -> c.warn
            ValueTone.Bad -> c.bad
            ValueTone.Link -> c.accent
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 行尾的小箭头:点进去还有一页。 */
@Composable
fun RowChevron() {
    DirectionalIcon(MikaIcons.Chevron, null, Modifier.size(MikaSize.SmallIcon), tint = MikaTheme.colors.ink3)
}

/** 单选行:左边圆圈,选中时实心强调色。分隔线从文字处开始。 */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    first: Boolean = false,
    enabled: Boolean = true,
    titleStyle: TextStyle = MikaType.Body,
    /** 标题下面的附加内容(例如能力小标签)。 */
    below: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .topHairline(first, 50.dp, c.hair)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioMark(selected, enabled)
        TextAndTrailing(
            text = {
                Column(Modifier.padding(vertical = 9.dp)) {
                    Text(title, style = titleStyle, color = if (enabled) c.ink else c.ink3)
                    if (!subtitle.isNullOrEmpty()) Text(subtitle, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = c.ink3)
                    if (below != null) Box(Modifier.padding(top = 4.dp)) { below() }
                }
            },
            trailing = trailing,
        )
    }
}

@Composable
private fun RadioMark(selected: Boolean, enabled: Boolean = true) {
    val c = MikaTheme.colors
    if (selected && enabled) {
        Box(Modifier.size(22.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(c.onAccent, CircleShape))
        }
    } else {
        Box(Modifier.size(22.dp).border(1.6.dp, c.hair, CircleShape))
    }
}

/**
 * 卡片里的输入行:左边固定宽的标题,右边单行输入框(空时显示 [placeholder])。
 * [secret] 为真时按密码显示;[trailing] 放在输入框右边(例如「显示」)。
 */
@Composable
fun InputRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    first: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    labelWidth: Dp = 84.dp,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth().topHairline(first, TextStartPlain, c.hair).keepsKeyboard()
            .heightIn(min = MikaSize.RowMin).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(labelWidth), style = MikaType.Body, color = c.ink)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MikaType.Body, color = c.ink3, maxLines = 1)
            BasicTextField(
                value, onChange, modifier.fillMaxWidth(), singleLine = true,
                textStyle = MikaType.Body.copy(color = c.ink, textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
        if (trailing != null) Box(Modifier.padding(start = 8.dp)) { trailing() }
    }
}

/** 卡片里自己排版的块之间的分隔线,从 [start] 画到右边。 */
@Composable
fun Hairline(start: Dp = TextStartWithIcon) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(0.5.dp).background(MikaTheme.colors.hair))
}
