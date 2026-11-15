package com.wangzan101.mika.ui.identity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.settings.OrbitIdentityMode

internal data class OrbitIdentityPalette(val orb: Color, val wave: Color, val glow: Color)

internal fun orbitIdentityPalette(mode: OrbitIdentityMode, dark: Boolean): OrbitIdentityPalette {
    val wave = if (dark) Color(0xFFF8F5F1) else Color(0xFF22201F)
    val orb = if (mode == OrbitIdentityMode.Original) Color(0xFFE97D60) else wave
    return OrbitIdentityPalette(orb, wave, orb)
}

/** Decorative Orbit mark. The caller supplies semantics when the mark conveys meaning. */
@Composable
fun MikaMark(modifier: Modifier = Modifier, dark: Boolean = MikaTheme.colors.isDark) {
    val mode = MikaTheme.identity
    val palette = orbitIdentityPalette(mode, dark)
    Canvas(modifier.size(40.dp)) {
        val unit = size.minDimension / 100f
        val x = (size.width - unit * 100f) / 2f
        val y = (size.height - unit * 100f) / 2f
        drawContext.transform.translate(x, y)
        scale(unit, unit, pivot = Offset.Zero) {
            // A narrow negative-space separation retains the same silhouette in one colour.
            val gap = Path().apply {
                moveTo(5f, 54f)
                cubicTo(19f, 40f, 30f, 37f, 44f, 41f)
                cubicTo(58f, 45f, 72f, 63f, 95f, 48f)
                lineTo(95f, 56f)
                cubicTo(72f, 71f, 58f, 53f, 44f, 49f)
                cubicTo(30f, 45f, 19f, 48f, 5f, 62f)
                close()
            }
            if (mode == OrbitIdentityMode.Monochrome) {
                clipPath(gap, clipOp = ClipOp.Difference) {
                    if (dark) {
                        drawCircle(Brush.linearGradient(listOf(Color(0xFFF7F6F4), Color(0xFF696969)),
                            start = Offset(40f, 22f), end = Offset(68f, 70f)), radius = 24f, center = Offset(55f, 46f))
                    } else {
                        drawCircle(palette.orb, radius = 24f, center = Offset(55f, 46f))
                    }
                }
            } else {
                drawCircle(palette.orb, radius = 24f, center = Offset(55f, 46f))
            }
            val wave = Path().apply {
                moveTo(5f, 58f)
                cubicTo(19f, 44f, 30f, 41f, 44f, 45f)
                cubicTo(58f, 49f, 72f, 67f, 95f, 52f)
                cubicTo(84f, 68f, 69f, 69f, 55f, 63f)
                cubicTo(37f, 54f, 25f, 48f, 5f, 58f)
                close()
            }
            drawPath(wave, palette.wave)
        }
        drawContext.transform.translate(-x, -y)
    }
}
