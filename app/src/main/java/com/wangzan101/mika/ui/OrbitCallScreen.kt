package com.wangzan101.mika.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wangzan101.mika.R
import com.wangzan101.mika.settings.Prerequisites
import com.wangzan101.mika.ui.identity.MikaPresenceAvatar
import com.wangzan101.mika.ui.identity.PresenceState
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.voice.CallPhase
import com.wangzan101.mika.voice.OrbitCall
import com.wangzan101.mika.voice.SpeechFailure
import com.wangzan101.mika.voice.SystemCallSpeaker
import com.wangzan101.mika.voice.VoiceSource
import kotlinx.coroutines.flow.map

/**
 * A hands-free call with Orbit over the open chat: a big orb that follows your voice, what was heard and
 * what Orbit is saying, and one red button to hang up. Everything said stays in the conversation.
 */
@Composable
internal fun OrbitCallScreen(
    vm: ChatViewModel,
    check: Prerequisites.Check,
    voice: VoiceSource?,
    openSpeechModel: () -> Unit,
    onEnd: () -> Unit,
) {
    Dialog(onEnd, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val context = LocalContext.current
        var micOk by remember {
            mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        }
        var micAsked by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micOk = granted
            micAsked = true
        }
        LaunchedEffect(Unit) { if (!micOk) launcher.launch(Manifest.permission.RECORD_AUDIO) }
        BackHandler(onBack = onEnd)
        Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            when {
                voice == null -> CallNotice(stringResource(R.string.orbit_call_no_voice), stringResource(R.string.orbit_call_choose_voice)) {
                    onEnd(); openSpeechModel()
                }
                !micOk && micAsked -> CallNotice(stringResource(R.string.orbit_call_needs_mic), stringResource(R.string.orbit_call_open_settings)) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                micOk -> LiveCall(vm, check, voice)
            }
            HangUp(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), onEnd)
        }
    }
}

@Composable
private fun LiveCall(vm: ChatViewModel, check: Prerequisites.Check, voice: VoiceSource) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenLine = stringResource(R.string.orbit_call_screen_line)
    val call = remember(vm, voice) {
        OrbitCall(
            scope, voice, SystemCallSpeaker(context),
            exchanges = vm.ui.map { it.exchanges },
            send = { vm.send(it, check) },
            needsScreenLine = screenLine,
        )
    }
    DisposableEffect(call) {
        call.start()
        onDispose { call.hangUp() }
    }
    val phase by call.phase.collectAsState()
    val level = (phase as? CallPhase.Listening)?.level ?: 0f
    val pulse by animateFloatAsState(if (MikaTheme.reduceMotion) 1f else 1f + level.coerceIn(0f, 1f) * 0.18f, label = "voice")
    val presence = when (phase) {
        is CallPhase.Listening -> PresenceState.Listening
        is CallPhase.Thinking -> PresenceState.Thinking
        is CallPhase.Speaking -> PresenceState.Working
        is CallPhase.NeedsScreen -> PresenceState.NeedsUser
        is CallPhase.Failed -> PresenceState.Error
        else -> PresenceState.Idle
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = 72.dp, bottom = 160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.orbit_call_orbit_title), style = MikaType.Title, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(phaseLabel(phase)), style = MikaType.Caption, color = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.weight(1f))
        MikaPresenceAvatar(Modifier.size(196.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, state = presence, dark = true)
        Spacer(Modifier.weight(1f))
        val words = when (val p = phase) {
            is CallPhase.Listening -> p.heard
            is CallPhase.Thinking -> p.said
            is CallPhase.Speaking -> p.text
            is CallPhase.NeedsScreen -> p.what
            else -> ""
        }
        AnimatedContent(words, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "words") { text ->
            Text(
                text, Modifier.fillMaxWidth(), style = MikaType.Body, color = Color.White.copy(alpha = 0.86f),
                textAlign = TextAlign.Center, maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun phaseLabel(phase: CallPhase): Int = when (phase) {
    CallPhase.Connecting -> R.string.orbit_call_connecting
    is CallPhase.Listening -> R.string.orbit_call_listening
    is CallPhase.Thinking -> R.string.orbit_call_thinking
    is CallPhase.Speaking -> R.string.orbit_call_speaking
    is CallPhase.NeedsScreen -> R.string.orbit_call_needs_screen
    CallPhase.Ended -> R.string.orbit_call_ended
    is CallPhase.Failed -> if (phase.why == SpeechFailure.NoPermission) R.string.orbit_call_needs_mic else R.string.orbit_call_failed
}

@Composable
private fun CallNotice(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MikaType.Body, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                .clickable(role = Role.Button, onClick = onAction).padding(horizontal = 22.dp, vertical = 13.dp),
        ) { Text(action, style = MikaType.Label, color = Color.White) }
    }
}

@Composable
private fun HangUp(modifier: Modifier, onEnd: () -> Unit) {
    val label = stringResource(R.string.orbit_call_hang_up)
    Box(
        modifier.size(72.dp).clip(CircleShape).background(Color(0xFFFF3B30))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onEnd),
        contentAlignment = Alignment.Center,
    ) {
        // The handset turned down, the usual sign for ending a call.
        Icon(MikaIcons.Call, label, Modifier.size(30.dp).rotate(135f), tint = Color.White)
    }
}
