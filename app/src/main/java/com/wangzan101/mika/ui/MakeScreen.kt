package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.identity.MikaMark
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
) {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).testTag("make-screen")) {
        TopBar("", MikaIcons.Menu, onOpenDrawer, stringResource(R.string.chat_menu),
            titleIcon = { MikaMark(Modifier.size(36.dp)) })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 26.dp)) {
            item {
                Text(stringResource(R.string.nav_make), style = MikaType.LargeTitle, color = c.ink,
                    modifier = Modifier.padding(bottom = 24.dp).semantics { heading() })
            }
            item { MakeAction(MikaIcons.New, stringResource(R.string.teach_title), onTeach) }
            item { MakeAction(MikaIcons.Spark, stringResource(R.string.memory_and_skills), onSkills) }
            item { MakeAction(MikaIcons.Globe, stringResource(R.string.drawer_browser), onBrowser) }
            item { MakeAction(MikaIcons.Doc, stringResource(R.string.file_library_title), onFiles) }
        }
    }
}

@Composable
private fun MakeAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Row(Modifier.fillMaxWidth().clip(MikaShapes.Card).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = c.ink)
        Text(label, Modifier.weight(1f), style = MikaType.BodyStrong, color = c.ink)
        Icon(MikaIcons.Chevron, null, Modifier.size(18.dp), tint = c.ink3)
    }
    HorizontalDivider(color = c.hair)
}
