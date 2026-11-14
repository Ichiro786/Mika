package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.GlassBackdrop
import com.wangzan101.mika.ui.components.GlassBar
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

@Composable
internal fun PrimaryNavigation(
    current: Route,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    onSelect: (Route) -> Unit,
) {
    val c = MikaTheme.colors
    val tabs = listOf(
        Triple(Route.Chat, R.string.nav_chat, MikaIcons.Message),
        Triple(Route.Tasks, R.string.personal_tab_tasks, MikaIcons.ListChecks),
        Triple(Route.Make, R.string.nav_make, MikaIcons.New),
    )
    val selected = if (current == Route.Personal) Route.Chat else current
    GlassBar(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        backdrop = backdrop,
        // Thinner tint than the composer so the live blur of what scrolls behind reads as frosted glass.
        tint = if (c.isDark) 0.58f else 0.6f,
    ) {
        Row(Modifier.weight(1f).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { (route, label, icon) ->
                val active = selected == route
                val selectedColor = c.answer
                val background by animateColorAsState(
                    if (active) selectedColor else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = if (MikaTheme.reduceMotion) snap() else tween(MikaMotion.SWITCH_MS, easing = MikaMotion.Decelerate),
                    label = "primary-navigation-selection",
                )
                Column(
                    Modifier.weight(1f).heightIn(min = 50.dp).testTag("primary-nav-${route.name}").clip(MikaShapes.Pill)
                        .background(background)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(route) })
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = if (active) c.ink else c.ink3)
                    Text(
                        stringResource(label), style = MikaType.Hint,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) c.ink else c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
