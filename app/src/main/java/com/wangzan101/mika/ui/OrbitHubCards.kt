package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One soft glow in a card's wash: a colour centred at a point given as a fraction of the card. */
internal data class Glow(val color: Color, val x: Float, val y: Float, val radius: Float)

/** The two looks: warm gold and olive for memory, dusty rose for skills. Fixed colours, the same in light and dark. */
internal object HubCardPalette {
    val Memory = Color(0xFF55520F) to listOf(
        Glow(Color(0xFFC09A22), 0.05f, 0.0f, 0.75f),
        Glow(Color(0xFF4A2E08), 0.72f, 0.12f, 0.55f),
        Glow(Color(0xFF8E9A22), 0.18f, 0.55f, 0.6f),
        Glow(Color(0xFFC4C232), 0.92f, 1.0f, 0.65f),
    )
    val Skills = Color(0xFF7D5A55) to listOf(
        Glow(Color(0xFF8A6460), 0.1f, 0.05f, 0.7f),
        Glow(Color(0xFFB08C82), 0.55f, 0.55f, 0.75f),
        Glow(Color(0xFF6E4652), 1.0f, 0.6f, 0.5f),
        Glow(Color(0xFFA48078), 0.15f, 1.0f, 0.55f),
    )
}

private val CardShape = RoundedCornerShape(30.dp)
private val Mono = FontFamily.Monospace
private val Day = DateTimeFormatter.ofPattern("dd/MM/yy")

/**
 * A square card with a blurred colour wash, after the user's reference: a big bold name, a mono line under it,
 * and at the foot a date on the left and an icon on the right. The whole card opens its page.
 */
@Composable
internal fun HubGlowCard(
    title: String,
    subtitle: String,
    atMs: Long?,
    icon: ImageVector,
    palette: Pair<Color, List<Glow>>,
    modifier: Modifier = Modifier,
    tag: String = "hub-card",
    onOpen: () -> Unit,
) {
    val (base, glows) = palette
    val ink = Color(0xFFF7F4EE)
    val date = remember(atMs) { atMs?.takeIf { it > 0 }?.let { Day.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } }
    Box(
        modifier.aspectRatio(1f).clip(CardShape)
            .drawBehind {
                drawRect(base)
                glows.forEach { g ->
                    val r = size.maxDimension * g.radius
                    drawRect(Brush.radialGradient(listOf(g.color, g.color.copy(alpha = 0f)), Offset(size.width * g.x, size.height * g.y), r))
                }
                // A faint sheen from the top so the card reads as glass, not a flat swatch.
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.12f))))
            }
            .border(0.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), CardShape)
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag(tag),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
            Text(
                title.uppercase(), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
            )
            Text(
                subtitle.uppercase(), Modifier.padding(top = 6.dp), color = ink.copy(alpha = 0.92f), maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.6.sp),
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.orEmpty(), Modifier.weight(1f), color = ink, maxLines = 1,
                    style = TextStyle(fontFamily = Mono, fontSize = 14.sp, letterSpacing = 0.4.sp),
                )
                Icon(icon, null, Modifier.size(24.dp), tint = ink)
            }
        }
    }
}

/** Memory and Skills side by side, as two glow cards. */
@Composable
internal fun HubGlowPair(
    memoryTitle: String, memorySub: String, memoryAt: Long?,
    skillsTitle: String, skillsSub: String, skillsAt: Long?,
    onOpen: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HubGlowCard(memoryTitle, memorySub, memoryAt, com.wangzan101.mika.ui.theme.MikaIcons.Thought, HubCardPalette.Memory,
            Modifier.weight(1f), "hub-card-memory", onOpen)
        HubGlowCard(skillsTitle, skillsSub, skillsAt, com.wangzan101.mika.ui.theme.MikaIcons.Spark, HubCardPalette.Skills,
            Modifier.weight(1f), "hub-card-skills", onOpen)
    }
}
