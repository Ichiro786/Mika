package com.wangzan101.mika.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.R
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.settings.AccessibilityState
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.settings.Prerequisites
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.SystemStatus
import com.wangzan101.mika.task.TriggerRuntime
import com.wangzan101.mika.ui.Fix
import com.wangzan101.mika.ui.PhoneSetupChrome
import com.wangzan101.mika.ui.PhoneSetupScreen
import com.wangzan101.mika.ui.components.OrbitTextField
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.identity.MikaMark
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.launch

/** Shapes and type the flow uses on top of the app's tokens: a slightly rounder card, and its page title. */
internal val FlowCard = RoundedCornerShape(24.dp)
private val FlowTitle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
private val FlowLead = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)

/**
 * The first-run flow: welcome, what Orbit can do, privacy, a model, phone control, permissions, optional extras,
 * done. Calm, one thing per page.
 * - A progress bar and a back button on every step; Skip on every optional one.
 * - The step is saved as the user moves, so a killed app reopens where it was ([replay] never touches that).
 * - [replay] is the same flow opened from Settings: nothing is marked, leaving goes back to Settings.
 * Every action goes to the handlers that already exist: models through the model setup view model, phone control
 * through the pairing guide, permissions through [onFix].
 */
@Composable
fun OnboardingScreen(
    store: ProviderStore,
    status: SystemStatus,
    onFix: (Fix) -> Unit,
    progress: OnboardingProgress,
    replay: Boolean,
    /** Where a replay picks up after a detour (linking WhatsApp); the first run reads its saved step instead. */
    startAt: FlowStep? = null,
    onStep: (FlowStep) -> Unit = {},
    whatsappLinked: Boolean,
    onLinkWhatsapp: () -> Unit,
    onOpenSource: () -> Unit,
    /** Back on the first page: out of the flow. */
    onLeave: () -> Unit,
    onFinish: () -> Unit,
) {
    val reduceMotion = MikaTheme.reduceMotion
    val steps = remember(status.sdkInt) { OnboardingFlow.steps(phoneAvailable = status.sdkInt >= Prerequisites.MIN_SDK) }
    var step by rememberSaveable { mutableStateOf(OnboardingFlow.resumeAt(if (replay) startAt else progress.step(), steps)) }
    var forward by remember { mutableStateOf(true) }
    fun go(to: FlowStep?, ahead: Boolean) {
        if (to == null) return
        forward = ahead
        step = to
        if (!replay) progress.saveStep(to)
        onStep(to)
    }
    val next: () -> Unit = { go(OnboardingFlow.next(step, steps), ahead = true) }
    val back: () -> Unit = { OnboardingFlow.previous(step, steps)?.let { go(it, ahead = false) } ?: onLeave() }
    val phoneReady = status.builtinShell == BuiltinShellStatus.Running

    PageBackground {
        AnimatedContent(
            step, Modifier.fillMaxSize(),
            transitionSpec = {
                if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                else {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = 360f)) { dir * it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -dir * it / 4 } + fadeOut(tween(140)))
                }
            },
            label = "onboarding-step",
        ) { shown ->
            val header: @Composable (onBack: () -> Unit, skipLabel: String?, onSkip: () -> Unit) -> Unit = { onBack, skipLabel, onSkip ->
                FlowHeader(OnboardingFlow.position(shown, steps), steps.size, onBack.takeIf { shown != FlowStep.Welcome }, skipLabel, onSkip)
            }
            val skip = stringResource(R.string.ob_skip).takeIf { OnboardingFlow.optional(shown) }
            when (shown) {
                FlowStep.Welcome -> {
                    BackHandler(onBack = back)
                    WelcomeStep(reduceMotion, next)
                }
                FlowStep.CanDo -> {
                    BackHandler(onBack = back)
                    CanDoStep(reduceMotion, header = { header(back, skip, next) }, onDone = next)
                }
                FlowStep.Privacy -> {
                    BackHandler(onBack = back)
                    PrivacyStep(header = { header(back, skip, next) }, onOpenSource, next)
                }
                FlowStep.Model -> ModelStep(store, header = { onBack -> header(onBack, null, next) }, onBack = back, onContinue = next)
                FlowStep.Phone -> PhoneSetupScreen(
                    status, repair = false, onFix = onFix, onboarding = true,
                    chrome = PhoneSetupChrome(
                        header = { onBack, onLater -> header(onBack, stringResource(R.string.ob_later).takeIf { !phoneReady }, onLater) },
                        onBack = back,
                    ),
                    onClose = next,
                )
                FlowStep.Permissions -> {
                    BackHandler(onBack = back)
                    PermissionsStep(status, onFix, header = { header(back, skip, next) }, next)
                }
                FlowStep.Personal -> {
                    BackHandler(onBack = back)
                    PersonalStep(progress, whatsappLinked, onLinkWhatsapp, header = { header(back, skip, next) }, next)
                }
                FlowStep.Done -> {
                    BackHandler(onBack = back)
                    DoneStep(
                        store, progress.name(), phoneReady, whatsappLinked, reduceMotion,
                        header = { header(back, null, next) }, onFinish = onFinish,
                    )
                }
            }
        }
    }
}

// ---------- Frame ----------

/** Back (when there is somewhere to go back to), a segmented progress bar, and Skip or Later on optional steps. */
@Composable
internal fun FlowHeader(position: Int, total: Int, onBack: (() -> Unit)?, trailing: String?, onTrailing: () -> Unit) {
    val c = MikaTheme.colors
    val label = stringResource(R.string.ob_step_of, position, total)
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) Box(
            Modifier.size(MikaSize.TouchTarget).clip(CircleShape).background(c.fill).border(0.75.dp, c.glassEdge, CircleShape)
                .clickable(onClickLabel = stringResource(R.string.back), role = Role.Button, onClick = onBack).testTag("onboarding-back"),
            contentAlignment = Alignment.Center,
        ) { Icon(MikaIcons.Back, stringResource(R.string.back), Modifier.size(22.dp), tint = c.ink) }
        else Spacer(Modifier.size(MikaSize.TouchTarget))
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 1f..total.toFloat(), total - 2)
            }.testTag("onboarding-progress"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(total) { i ->
                Box(Modifier.weight(1f).height(3.dp).background(if (i < position) c.ink else c.ink.copy(alpha = 0.18f), CircleShape))
            }
        }
        if (trailing != null) Box(
            Modifier.heightIn(min = MikaSize.TouchTarget).widthIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill)
                .clickable(role = Role.Button, onClick = onTrailing).padding(horizontal = 8.dp).testTag("onboarding-skip"),
            contentAlignment = Alignment.Center,
        ) { Text(trailing, style = MikaType.Body, fontWeight = FontWeight.Medium, color = c.ink2, maxLines = 1) }
        else Spacer(Modifier.size(MikaSize.TouchTarget))
    }
}

/**
 * The flow's main button: the page's ink on its background (off-white on black in the dark theme), so it reads
 * at full contrast whatever accent the wallpaper gives and never competes with the coral mark.
 */
@Composable
internal fun FlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = MikaTheme.colors
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(MikaShapes.Button)
            .background(if (enabled) c.ink else c.ink.copy(alpha = 0.12f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            color = if (enabled) c.bg else c.ink3)
    }
}

/** A quiet "do this later" under a step: a text button and one line on what still works. */
@Composable
internal fun FlowLater(label: String, note: String, onClick: () -> Unit, tag: String) {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill).clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp).testTag(tag),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = MikaType.BodyStrong, color = c.ink) }
        Text(note, style = MikaType.Caption, color = c.ink3, textAlign = TextAlign.Center)
    }
}

/** A step's page: header, scrolling content with the flow's side margins, and the bottom bar above the gesture area. */
@Composable
internal fun FlowPage(
    header: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        header()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            content = content,
        )
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 18.dp), content = bottom)
    }
}

@Composable
internal fun FlowTitleBlock(title: String, lead: String?) {
    val c = MikaTheme.colors
    Text(title, Modifier.semantics { heading() }, style = FlowTitle, color = c.ink)
    if (lead != null) Text(lead, Modifier.padding(top = 10.dp), style = FlowLead, color = c.ink2)
}

/** A frosted card: a soft fill and a hairline, in the flow's rounder shape. */
@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MikaTheme.colors
    Column(modifier.fillMaxWidth().clip(FlowCard).background(c.fill).border(0.75.dp, c.glassEdge, FlowCard), content = content)
}

@Composable
private fun IconSquare(icon: ImageVector, tint: Color, fill: Color) {
    Box(Modifier.size(44.dp).background(fill, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

/** A small state pill: a dot or check and a word, in the state's colour and never colour alone. */
@Composable
internal fun StatePill(text: String, color: Color, soft: Color, pulsing: Boolean = false, check: Boolean = false, modifier: Modifier = Modifier) {
    val reduceMotion = MikaTheme.reduceMotion
    val pulse = if (pulsing && !reduceMotion) rememberInfiniteTransition(label = "pill").animateFloat(
        1f, 0.35f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "pill-dot",
    ) else null
    Row(
        modifier.heightIn(min = 32.dp).background(soft, MikaShapes.Pill).padding(start = 10.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (check) Icon(MikaIcons.Check, null, Modifier.size(15.dp), tint = color)
        else Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse?.value ?: 1f }.background(color, CircleShape))
        Text(text, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ---------- 1 Welcome ----------

/**
 * The orb rises and settles on a spring, then the title, the line and the button fade up in turn; afterwards only
 * a slow breathing glow. With animations off everything is simply there.
 */
@Composable
private fun WelcomeStep(reduceMotion: Boolean, onStart: () -> Unit) {
    val c = MikaTheme.colors
    val orb = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val text = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val cta = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        launch { orb.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f)) }
        launch { text.animateTo(1f, tween(520, delayMillis = 380)) }
        launch { cta.animateTo(1f, tween(520, delayMillis = 640)) }
    }
    val breathe = if (reduceMotion) null else rememberInfiniteTransition(label = "welcome").animateFloat(
        0.75f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "welcome-glow",
    )
    val glow = Color(0xFFE97D60)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(260.dp).drawBehind {
                    val a = (breathe?.value ?: 1f) * orb.value.coerceIn(0f, 1f)
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.30f * a), glow.copy(alpha = 0.10f * a), Color.Transparent)))
                },
                contentAlignment = Alignment.Center,
            ) {
                MikaMark(
                    Modifier.size(168.dp).graphicsLayer {
                        val p = orb.value
                        scaleX = 0.55f + 0.45f * p; scaleY = 0.55f + 0.45f * p
                        translationY = (1f - p) * 46.dp.toPx()
                        alpha = p.coerceIn(0f, 1f)
                    }.clearAndSetSemantics {},
                )
            }
            Column(
                Modifier.graphicsLayer { alpha = text.value; translationY = (1f - text.value) * 14.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.welcome_title), Modifier.padding(top = 4.dp).semantics { heading() },
                    fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = c.ink, textAlign = TextAlign.Center)
                Text(stringResource(R.string.ob_welcome_lead), Modifier.padding(top = 14.dp),
                    fontSize = 17.sp, lineHeight = 26.sp, color = c.ink2, textAlign = TextAlign.Center)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 18.dp)
                .graphicsLayer { alpha = cta.value; translationY = (1f - cta.value) * 14.dp.toPx() },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.ob_welcome_note), Modifier.fillMaxWidth(), style = MikaType.Caption, color = c.ink3, textAlign = TextAlign.Center)
            FlowButton(stringResource(R.string.welcome_start), onStart, Modifier.testTag("welcome-start"))
        }
    }
}

// ---------- 2 What Orbit can do ----------

@Composable
private fun CanDoStep(reduceMotion: Boolean, header: @Composable () -> Unit, onDone: () -> Unit) {
    val c = MikaTheme.colors
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val pageLabel = stringResource(R.string.ob_card_of, pager.currentPage + 1, 3)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        header()
        HorizontalPager(
            pager, Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp).testTag("onboarding-cards"),
            contentPadding = PaddingValues(start = 20.dp, end = 34.dp), pageSpacing = 12.dp,
        ) { page ->
            GlassCard(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 20.dp)) {
                    when (page) {
                        0 -> MessageScene()
                        1 -> SummaryScene()
                        else -> BackgroundScene()
                    }
                }
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 24.dp)) {
                    val (title, body) = when (page) {
                        0 -> R.string.ob_can1_title to R.string.ob_can1_body
                        1 -> R.string.ob_can2_title to R.string.ob_can2_body
                        else -> R.string.ob_can3_title to R.string.ob_can3_body
                    }
                    Text(stringResource(title), Modifier.semantics { heading() }, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, color = c.ink)
                    Text(stringResource(body), Modifier.padding(top = 8.dp), fontSize = 15.sp, lineHeight = 22.sp, color = c.ink2)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = pageLabel },
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            ) {
                repeat(3) { i ->
                    Box(Modifier.height(6.dp).width(if (i == pager.currentPage) 20.dp else 6.dp).background(if (i == pager.currentPage) c.ink else c.ink.copy(alpha = 0.28f), CircleShape))
                }
            }
            val last = pager.currentPage == 2
            FlowButton(stringResource(if (last) R.string.ob_continue else R.string.next), {
                if (last) onDone() else scope.launch {
                    if (reduceMotion) pager.scrollToPage(pager.currentPage + 1) else pager.animateScrollToPage(pager.currentPage + 1)
                }
            }, Modifier.testTag("onboarding-next"))
        }
    }
}

@Composable
private fun UserLine(text: String) {
    val tone = MikaTheme.userBubble
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(text, Modifier.widthIn(max = 250.dp).background(tone.bg, MikaShapes.UserBubble).padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 15.sp, lineHeight = 21.sp, color = tone.fg)
    }
}

@Composable
private fun DoneLineSmall(text: String) {
    val c = MikaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(MikaIcons.Check, null, Modifier.size(16.dp), tint = c.ok)
        Text(text, style = MikaType.Caption, color = c.ink2)
    }
}

/** Example: asking Orbit to message someone, the steps it takes, and the question it asks before sending. */
@Composable
private fun MessageScene() {
    val c = MikaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UserLine(stringResource(R.string.ob_can1_ask))
        Column(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DoneLineSmall(stringResource(R.string.ob_can1_step1))
            DoneLineSmall(stringResource(R.string.ob_can1_step2))
            DoneLineSmall(stringResource(R.string.ob_can1_step3))
        }
        Column(Modifier.fillMaxWidth().background(c.answer, MikaShapes.AnswerBubble).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ob_can1_confirm), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(2.dp).height(40.dp).background(c.ink.copy(alpha = 0.2f)))
                Text(stringResource(R.string.ob_can1_message), fontSize = 14.sp, lineHeight = 20.sp, color = c.ink2)
            }
            // A picture of the approval, not working buttons: TalkBack reads the texts, nothing here is a control.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.chat_send), Modifier.background(c.ink, MikaShapes.Pill).padding(horizontal = 18.dp, vertical = 8.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.bg)
                Text(stringResource(R.string.ob_edit), Modifier.background(c.fill, MikaShapes.Pill).padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink)
            }
        }
    }
}

/** Example: a summary of unread chats, one line per chat. */
@Composable
private fun SummaryScene() {
    val c = MikaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UserLine(stringResource(R.string.ob_can2_ask))
        Column(Modifier.fillMaxWidth(0.92f).background(c.answer, MikaShapes.AnswerBubble).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ob_can2_lead), fontSize = 14.sp, lineHeight = 20.sp, color = c.ink)
            listOf(
                R.string.ob_can2_n1 to R.string.ob_can2_t1, R.string.ob_can2_n2 to R.string.ob_can2_t2, R.string.ob_can2_n3 to R.string.ob_can2_t3,
            ).forEach { (name, line) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(name), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(stringResource(line), fontSize = 14.sp, lineHeight = 20.sp, color = c.ink2)
                }
            }
        }
        Box(Modifier.padding(start = 4.dp)) { DoneLineSmall(stringResource(R.string.ob_can2_meta)) }
    }
}

/** Example: one task running with real progress, one scheduled, one done. */
@Composable
private fun BackgroundScene() {
    val c = MikaTheme.colors
    @Composable fun label(text: String) = Text(text.uppercase(), Modifier.padding(start = 2.dp), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = c.ink3)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        label(stringResource(R.string.personal_status_running))
        Column(Modifier.fillMaxWidth().background(c.answer, MikaShapes.Card).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ob_can3_task), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(stringResource(R.string.ob_can3_progress), style = MikaType.Caption, color = c.ink2)
            Box(Modifier.fillMaxWidth().height(4.dp).background(c.ink.copy(alpha = 0.12f), CircleShape)) {
                Box(Modifier.fillMaxWidth(0.56f).height(4.dp).background(c.accent, CircleShape))
            }
        }
        Box(Modifier.padding(top = 6.dp)) { label(stringResource(R.string.tasks_up_next)) }
        Row(Modifier.fillMaxWidth().background(c.answer, MikaShapes.Card).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(MikaIcons.Clock, null, Modifier.padding(top = 1.dp).size(22.dp), tint = c.ink2)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.ob_can3_next), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(stringResource(R.string.ob_can3_when), style = MikaType.Caption, color = c.ink2)
            }
        }
        Box(Modifier.padding(start = 2.dp, top = 4.dp)) { DoneLineSmall(stringResource(R.string.ob_can3_done)) }
    }
}

// ---------- 3 Privacy ----------

@Composable
private fun PrivacyStep(header: @Composable () -> Unit, onOpenSource: () -> Unit, onContinue: () -> Unit) {
    val c = MikaTheme.colors
    FlowPage(header, bottom = { FlowButton(stringResource(R.string.ob_continue), onContinue, Modifier.testTag("onboarding-next")) }) {
        FlowTitleBlock(stringResource(R.string.ob_privacy_title), stringResource(R.string.ob_privacy_lead))
        GlassCard(Modifier.padding(top = 26.dp)) {
            PrivacyRow(MikaIcons.Code, c.ink, c.fill, R.string.ob_privacy_open, R.string.ob_privacy_open_body)
            Box(Modifier.padding(start = 80.dp).fillMaxWidth().height(0.75.dp).background(c.hair))
            PrivacyRow(MikaIcons.Phone, c.ok, c.okSoft, R.string.ob_privacy_local, R.string.ob_privacy_local_body)
            Box(Modifier.padding(start = 80.dp).fillMaxWidth().height(0.75.dp).background(c.hair))
            PrivacyRow(MikaIcons.External, c.warn, c.warnSoft, R.string.ob_privacy_sent, R.string.ob_privacy_sent_body)
        }
        Row(
            Modifier.padding(top = 8.dp).heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill)
                .clickable(role = Role.Button, onClick = onOpenSource).padding(horizontal = 4.dp).testTag("onboarding-source"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.ob_privacy_source), style = MikaType.Body, fontWeight = FontWeight.Medium, color = c.accent)
            Icon(MikaIcons.External, null, Modifier.size(16.dp), tint = c.accent)
        }
    }
}

@Composable
private fun PrivacyRow(icon: ImageVector, tint: Color, fill: Color, title: Int, body: Int) {
    val c = MikaTheme.colors
    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconSquare(icon, tint, fill)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), Modifier.semantics { heading() }, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(stringResource(body), fontSize = 14.5.sp, lineHeight = 21.sp, color = c.ink2)
        }
    }
}

// ---------- 5 Phone control (the frame around the existing pairing guide) ----------

/** Title, one plain paragraph and the live state above the pairing guide's step list; a settled check once connected. */
@Composable
internal fun PhoneStepIntro(connected: Boolean) {
    val c = MikaTheme.colors
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 18.dp)) {
        if (connected) {
            Box(Modifier.padding(bottom = 14.dp).size(64.dp).background(c.ok, CircleShape), contentAlignment = Alignment.Center) {
                Icon(MikaIcons.Check, null, Modifier.size(32.dp), tint = if (c.isDark) Color(0xFF04140B) else Color.White)
            }
            FlowTitleBlock(stringResource(R.string.ob_phone_on_title), stringResource(R.string.ob_phone_on_body))
            StatePill(stringResource(R.string.phone_connected), c.ok, c.okSoft, check = true, modifier = Modifier.padding(top = 16.dp).testTag("onboarding-phone-state"))
        } else {
            FlowTitleBlock(stringResource(R.string.ob_phone_title), stringResource(R.string.ob_phone_lead))
            StatePill(stringResource(R.string.ob_phone_not_connected), c.warn, c.warnSoft, pulsing = true, modifier = Modifier.padding(top = 16.dp).testTag("onboarding-phone-state"))
        }
    }
}

// ---------- 6 Permissions ----------

/**
 * Nothing is requested just by arriving here. Each row says in one line why Orbit wants it and shows whether it is
 * already allowed; the buttons hand over to the system's own prompt or settings page.
 */
@Composable
private fun PermissionsStep(status: SystemStatus, onFix: (Fix) -> Unit, header: @Composable () -> Unit, onContinue: () -> Unit) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    var exact by remember { mutableStateOf(TriggerRuntime.canScheduleExactly(context)) }
    LifecycleResumeEffect(Unit) {
        exact = TriggerRuntime.canScheduleExactly(context)
        onPauseOrDispose {}
    }
    FlowPage(header, bottom = { FlowButton(stringResource(R.string.ob_continue), onContinue, Modifier.testTag("onboarding-next")) }) {
        FlowTitleBlock(stringResource(R.string.ob_perm_title), stringResource(R.string.ob_perm_lead))
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionRow(MikaIcons.Bell, R.string.ob_perm_notif, R.string.ob_perm_notif_body, "perm-notifications") {
                if (status.notificationsAllowed) StatePill(stringResource(R.string.ob_perm_allowed), c.ok, c.okSoft, check = true)
                else SmallButton(stringResource(R.string.ob_perm_allow), primary = true) { onFix(Fix.AllowNotifications) }
            }
            PermissionRow(MikaIcons.Layers, R.string.ob_perm_capsule, R.string.ob_perm_capsule_body, "perm-capsule") {
                // Drawn by the accessibility service: there is no separate system permission to ask for.
                if (status.accessibility == AccessibilityState.On) StatePill(stringResource(R.string.ob_perm_capsule_ready), c.ok, c.okSoft, check = true)
                else StatePill(stringResource(R.string.ob_perm_capsule_needs), c.ink2, c.fill)
            }
            PermissionRow(MikaIcons.Alarm, R.string.ob_perm_alarm, R.string.ob_perm_alarm_body, "perm-alarms") {
                if (exact) StatePill(stringResource(R.string.ob_perm_allowed), c.ok, c.okSoft, check = true)
                else SmallButton(stringResource(R.string.ob_perm_allow_now), primary = false) {
                    runCatching { context.startActivity(TriggerRuntime.exactAlarmSettings(context)) }
                }
            }
        }
        Text(stringResource(R.string.ob_perm_note), Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp), style = MikaType.Caption, color = c.ink3)
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: Int, body: Int, tag: String, trailing: @Composable () -> Unit) {
    val c = MikaTheme.colors
    GlassCard(Modifier.testTag(tag)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconSquare(icon, c.ink, c.fill)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(title), Modifier.semantics { heading() }, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(stringResource(body), fontSize = 14.sp, lineHeight = 20.sp, color = c.ink2)
                }
            }
            // On its own line so a long label or a large font never squeezes the explanation.
            Box(Modifier.padding(start = 58.dp)) { trailing() }
        }
    }
}

@Composable
internal fun SmallButton(text: String, primary: Boolean, tag: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Box(
        Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill)
            .then(if (primary) Modifier.background(if (enabled) c.ink else c.ink.copy(alpha = 0.12f)) else Modifier.border(1.dp, c.ink.copy(alpha = 0.22f), MikaShapes.Pill))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (!primary) c.ink else if (enabled) c.bg else c.ink3)
    }
}

// ---------- 7 Optional extras ----------

@Composable
private fun PersonalStep(
    progress: OnboardingProgress, whatsappLinked: Boolean, onLinkWhatsapp: () -> Unit,
    header: @Composable () -> Unit, onContinue: () -> Unit,
) {
    val c = MikaTheme.colors
    var name by rememberSaveable { mutableStateOf(progress.name()) }
    val save = { progress.setName(name); onContinue() }
    FlowPage(header, bottom = { FlowButton(stringResource(R.string.ob_continue), save, Modifier.testTag("onboarding-next")) }) {
        FlowTitleBlock(stringResource(R.string.ob_personal_title), stringResource(R.string.ob_personal_lead))
        GlassCard(Modifier.padding(top = 24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrbitTextField(
                    name, { name = it.take(OnboardingFlow.NAME_MAX) }, stringResource(R.string.ob_name_label),
                    Modifier.fillMaxWidth().testTag("onboarding-name"), placeholder = stringResource(R.string.ob_name_placeholder),
                )
                Text(stringResource(R.string.ob_name_hint), style = MikaType.Caption, color = c.ink3)
            }
        }
        GlassCard(Modifier.padding(top = 12.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconSquare(MikaIcons.Message, c.ok, c.okSoft)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.ob_wa_title), Modifier.semantics { heading() }, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(stringResource(R.string.ob_wa_body), fontSize = 14.5.sp, lineHeight = 21.sp, color = c.ink2)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(c.warnSoft, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(MikaIcons.Exclaim, null, Modifier.padding(top = 2.dp).size(16.dp), tint = c.warn)
                    Text(stringResource(R.string.ob_wa_risk), Modifier.weight(1f), fontSize = 13.5.sp, lineHeight = 19.sp, color = c.ink)
                }
                if (whatsappLinked) StatePill(stringResource(R.string.ob_wa_linked), c.ok, c.okSoft, check = true)
                else SmallButton(stringResource(R.string.ob_wa_title), primary = false, tag = "onboarding-whatsapp") { progress.setName(name); onLinkWhatsapp() }
            }
        }
    }
}

// ---------- 8 Done ----------

@Composable
private fun DoneStep(
    store: ProviderStore, name: String, phoneReady: Boolean, whatsappLinked: Boolean, reduceMotion: Boolean,
    header: @Composable () -> Unit, onFinish: () -> Unit,
) {
    val c = MikaTheme.colors
    val roles by store.roles.collectAsState()
    val context = LocalContext.current
    val geminiOn by remember { com.wangzan101.mika.gemini.GeminiEngine.get(context) }.enabled.collectAsState()
    // A provider's main model, or the Gemini plan when that is what runs tasks.
    val model = remember(roles) { store.mainModelName() } ?: stringResource(R.string.ob_model_gemini).takeIf { geminiOn }
    val settle = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val ring = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        launch { settle.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 160f)) }
        launch { ring.animateTo(1f, tween(1100, delayMillis = 100)) }
    }
    val glow = Color(0xFFE97D60)
    FlowPage(header, bottom = { FlowButton(stringResource(R.string.ob_done_start), onFinish, Modifier.testTag("onboarding-finish")) }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(220.dp).drawBehind {
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.30f), glow.copy(alpha = 0.10f), Color.Transparent)))
                    // One ring goes out and fades: the short celebration. Still, it is not drawn at all.
                    val p = ring.value
                    if (p > 0f && p < 1f) drawCircle(
                        glow.copy(alpha = 0.7f * (1f - p)), radius = size.minDimension / 2 * (0.5f + 0.5f * p),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                MikaMark(Modifier.size(136.dp).graphicsLayer {
                    val p = settle.value
                    scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p; alpha = p.coerceIn(0f, 1f)
                }.clearAndSetSemantics {})
            }
            Text(
                if (name.isBlank()) stringResource(R.string.ob_done_title) else stringResource(R.string.ob_done_title_named, name),
                Modifier.semantics { heading() }, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
                color = c.ink, textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.ob_done_lead), Modifier.padding(top = 10.dp), style = FlowLead, color = c.ink2, textAlign = TextAlign.Center)
        }
        GlassCard(Modifier.padding(top = 26.dp)) {
            SummaryRow(stringResource(R.string.ob_done_model), model ?: stringResource(R.string.ob_phone_later), ok = model != null)
            Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(0.75.dp).background(c.hair))
            SummaryRow(stringResource(R.string.ob_done_phone), stringResource(if (phoneReady) R.string.phone_connected else R.string.ob_phone_later), ok = phoneReady)
            Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(0.75.dp).background(c.hair))
            SummaryRow(stringResource(R.string.whatsapp_title), stringResource(if (whatsappLinked) R.string.ob_wa_linked else R.string.ob_done_not_linked), ok = whatsappLinked)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, ok: Boolean) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 18.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f), fontSize = 15.5.sp, lineHeight = 22.sp, color = c.ink2)
        Text(value, Modifier.weight(1f, fill = false), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
            color = if (ok) c.ink else c.ink3, textAlign = TextAlign.End)
        if (ok) Icon(MikaIcons.Check, null, Modifier.size(16.dp), tint = c.ok)
    }
}

/** The main model's name as the store has it, or null when none is chosen yet. */
internal fun ProviderStore.mainModelName(): String? = roles.value.resolve(ModelRole.Main)?.let { id ->
    configs.value.firstOrNull { it.id == id }?.model?.takeIf { it.isNotBlank() }
}
