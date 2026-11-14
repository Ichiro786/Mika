package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.identity.MikaPresenceAvatar
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

@Composable
internal fun MakeScreen(
    onOpenDrawer: () -> Unit,
    onTeach: () -> Unit,
    onSkills: () -> Unit,
    onBrowser: () -> Unit,
    onFiles: () -> Unit,
    onOpenOrbitHub: () -> Unit = {},
    presence: com.wangzan101.mika.ui.identity.PresenceState = com.wangzan101.mika.ui.identity.PresenceState.Idle,
    bottomInset: Dp = 0.dp,
) {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).testTag("make-screen")) {
        TopBar("", MikaIcons.Menu, onOpenDrawer, stringResource(R.string.chat_menu),
            titleIcon = { OrbitHubButton(presence, onOpenOrbitHub) }, centerTitle = true,
            barHeight = orbitHeaderBarHeight())
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = bottomInset + 26.dp)) {
            item {
                Text(stringResource(R.string.nav_make), style = MikaType.LargeTitle, color = c.ink,
                    modifier = Modifier.padding(bottom = 24.dp).semantics { heading() })
            }
            item {
                MikaCard {
                    MakeAction(MikaIcons.New, stringResource(R.string.teach_title), onTeach)
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = c.hair)
                    MakeAction(MikaIcons.Spark, stringResource(R.string.memory_and_skills), onSkills)
                }
            }
            item {
                MikaCard(Modifier.padding(top = 14.dp)) {
                    MakeAction(MikaIcons.Globe, stringResource(R.string.drawer_browser), onBrowser)
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = c.hair)
                    MakeAction(MikaIcons.Doc, stringResource(R.string.file_library_title), onFiles)
                }
            }
        }
    }
}

@Composable
private fun MakeAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Row(Modifier.fillMaxWidth().clip(MikaShapes.Card).clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(c.answer, MikaShapes.Tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = c.ink)
        }
        Text(label, Modifier.weight(1f), style = MikaType.BodyStrong, color = c.ink)
        Icon(MikaIcons.Chevron, null, Modifier.size(18.dp), tint = c.ink3)
    }
}
