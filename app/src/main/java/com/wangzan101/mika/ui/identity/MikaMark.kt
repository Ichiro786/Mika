package com.wangzan101.mika.ui.identity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaTheme

/** Decorative Orbit Spark. The caller supplies semantics when the mark conveys meaning. */
@Composable
fun MikaMark(modifier: Modifier = Modifier, dark: Boolean = MikaTheme.colors.isDark) {
    val ribbon = if (dark) Color(0xFFF8F5F1) else Color(0xFF22201F)
    Canvas(modifier.size(40.dp)) {
        val unit = size.minDimension / 100f
        val x = (size.width - unit * 100f) / 2f
        val y = (size.height - unit * 100f) / 2f
        drawContext.transform.translate(x, y)
        scale(unit, unit, pivot = Offset.Zero) {
            drawCircle(Color(0xFFE97D60), radius = 24f, center = Offset(55f, 46f))
            val wave = Path().apply {
                moveTo(5f, 58f)
                cubicTo(19f, 44f, 30f, 41f, 44f, 45f)
                cubicTo(58f, 49f, 72f, 67f, 95f, 52f)
                cubicTo(84f, 68f, 69f, 69f, 55f, 63f)
                cubicTo(37f, 54f, 25f, 48f, 5f, 58f)
                close()
            }
            drawPath(wave, ribbon)
        }
        drawContext.transform.translate(-x, -y)
    }
}

/** Static presentation point for a future state-driven presence component. */
@Composable
fun MikaPresenceAvatar(modifier: Modifier = Modifier, dark: Boolean = MikaTheme.colors.isDark) {
    MikaMark(modifier, dark)
}
