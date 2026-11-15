package com.wangzan101.mika.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.gemini.GeminiEngine
import com.wangzan101.mika.gemini.GeminiRuntime
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orbit: run tasks on the user's Gemini subscription through Google's Antigravity server set up on the phone.
 * Set up, sign in, switch on; space used and removal below.
 */
@Composable
internal fun GeminiPlanSection() {
    val context = LocalContext.current
    val engine = remember { GeminiEngine.get(context) }
    val c = MikaTheme.colors
    val installed by engine.installed.collectAsState()
    val progress by engine.setupProgress.collectAsState()
    val setupError by engine.setupError.collectAsState()
    val signedIn by engine.signedIn.collectAsState()
    val signingIn by engine.signingIn.collectAsState()
    val signInError by engine.signInError.collectAsState()
    val enabled by engine.enabled.collectAsState()
    var used by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(installed, progress == null) {
        used = if (progress == null) withContext(Dispatchers.IO) { engine.runtime.usedBytes() } else null
    }

    GroupLabel(stringResource(R.string.gemini_group))
    GroupCard(Modifier.testTag("gemini-plan")) {
        when {
            !engine.runtime.supported -> ListRow(MikaIcons.Spark, stringResource(R.string.gemini_title),
                stringResource(R.string.gemini_unsupported), first = true)
            progress != null || !installed -> {
                val p = progress
                ListRow(
                    MikaIcons.Spark, stringResource(R.string.gemini_title),
                    if (p != null) stageText(p) else stringResource(R.string.gemini_lead),
                    first = true,
                    detail = setupError?.let { stringResource(R.string.gemini_setup_failed, it) }
                        ?: if (p == null) stringResource(R.string.gemini_setup_note) else null,
                    detailColor = if (setupError != null) c.bad else c.ink3,
                )
                if (p == null) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    MikaButton(stringResource(R.string.gemini_setup), engine::startSetup, Modifier.fillMaxWidth().testTag("gemini-setup"),
                        size = ButtonSize.Medium)
                }
            }
            !signedIn -> {
                ListRow(
                    MikaIcons.Key, stringResource(R.string.gemini_sign_in),
                    if (signingIn) stringResource(R.string.gemini_signing_in) else stringResource(R.string.gemini_lead),
                    first = true,
                    detail = signInError?.let { stringResource(R.string.gemini_sign_in_failed, it) },
                    detailColor = c.bad,
                )
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    MikaButton(
                        stringResource(if (signingIn) R.string.gemini_open_link else R.string.gemini_sign_in),
                        engine::startSignIn, Modifier.fillMaxWidth().testTag("gemini-sign-in"), size = ButtonSize.Medium,
                    )
                }
            }
            else -> {
                ListRow(MikaIcons.Spark, stringResource(R.string.gemini_use), stringResource(R.string.gemini_use_line), first = true,
                    onClick = { engine.setEnabled(!enabled) }) {
                    MikaSwitch(enabled, engine::setEnabled, Modifier.testTag("gemini-use"))
                }
                ListRow(MikaIcons.Check, stringResource(R.string.gemini_signed_in)) {
                    MikaButton(stringResource(R.string.gemini_sign_out), engine::signOut, kind = ButtonKind.Secondary, size = ButtonSize.Small)
                }
            }
        }
        if (installed && progress == null) {
            ListRow(MikaIcons.Download, stringResource(R.string.gemini_space),
                engine.runtime.installedVersion?.let { stringResource(R.string.gemini_version, it) }) {
                used?.let { RowValue(Formatter.formatShortFileSize(context, it)) }
            }
            ListRow(MikaIcons.Trash, stringResource(R.string.gemini_remove), stringResource(R.string.gemini_remove_line),
                titleColor = c.bad, onClick = engine::removeAll)
        }
    }
    FooterNote(stringResource(R.string.gemini_footer))
}

@Composable
private fun stageText(p: GeminiEngine.SetupProgress): String = when (p.stage) {
    GeminiRuntime.Stage.Space -> stringResource(R.string.gemini_stage_space)
    GeminiRuntime.Stage.Base -> stringResource(R.string.gemini_stage_base, p.percent)
    GeminiRuntime.Stage.Python -> stringResource(R.string.gemini_stage_python, p.percent)
    GeminiRuntime.Stage.Server -> stringResource(R.string.gemini_stage_server, p.percent)
    GeminiRuntime.Stage.Prepare -> stringResource(R.string.gemini_stage_prepare, p.percent)
    GeminiRuntime.Stage.Done -> stringResource(R.string.gemini_stage_done)
}
