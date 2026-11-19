package com.wangzan101.mika.ui

import com.wangzan101.mika.i18n.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.Hairline
import com.wangzan101.mika.ui.components.IconTile
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaLogo
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.components.ProgressDots
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaRadius
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.cardSurface
import com.wangzan101.mika.ui.identity.MikaPresenceAvatar
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.motion

/**
 * The first three onboarding pages, on the Welcome route: who Orbit is (the mark springs in and the words rise
 * after it), what it can do (swipe cards with example requests), and what stays private. Then [onStart] moves
 * on to choosing a model and setting up phone control, the existing screens with their real checks.
 * Every claim here is something Orbit already does; the requests on the cards are marked as examples.
 */
@Composable
fun WelcomeScreen(startPage: Int = 0, onStart: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(startPage) }
    BackHandler(enabled = page > 0) { page-- }
    val motion = MikaTheme.motion
    PageBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            AnimatedContent(
                page, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(motion.effectsDefault()) + slideInHorizontally(motion.offset()) { dir * it / 12 })
                        .togetherWith(fadeOut(motion.effectsFast()) + slideOutHorizontally(motion.offset()) { -dir * it / 12 })
                },
                label = "onboarding-page",
            ) { p ->
                when (p) {
                    0 -> HelloPage()
                    1 -> AbilitiesPage()
                    else -> PrivacyPage()
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = MikaSpace.S16, end = MikaSpace.S16, top = MikaSpace.S8, bottom = MikaSpace.S12),
                verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
                ProgressDots(Onboarding.DOTS, page)
                if (page == 0) Text(WelcomeCopy.NOTE, Modifier.fillMaxWidth().padding(horizontal = MikaSpace.S16),
                    style = MikaType.Hint, color = MikaTheme.colors.ink3, textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
                    if (page > 0) MikaButton(stringResource(R.string.onboarding_back), { page-- }, Modifier.weight(1f).testTag("onboarding-back"),
                        kind = ButtonKind.Tonal)
                    when (page) {
                        0 -> MikaButton(WelcomeCopy.START, { page = 1 }, Modifier.weight(1f).testTag("welcome-start"))
                        1 -> MikaButton(stringResource(R.string.onboarding_next), { page = 2 }, Modifier.weight(1f).testTag("onboarding-abilities-next"))
                        else -> MikaButton(stringResource(R.string.onboarding_agree), onStart, Modifier.weight(1f).testTag("onboarding-privacy-next"))
                    }
                }
            }
        }
    }
}

/** Page 1: the Orbit mark arrives on the default spatial spring, then the title and promise rise 30ms apart. */
@Composable
private fun HelloPage() {
    val c = MikaTheme.colors
    val motion = MikaTheme.motion
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MikaSpace.S24),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(shown, enter = motion.enter()) {
            MikaPresenceAvatar(Modifier.size(MarkSize), framed = true)
        }
        AnimatedVisibility(shown, enter = motion.enter(delayMs = MikaMotion.staggerDelay(2))) {
            Text(WelcomeCopy.TITLE, Modifier.padding(top = MikaSpace.S24).semantics { heading() }, style = MikaType.LargeTitle,
                color = c.ink, textAlign = TextAlign.Center)
        }
        AnimatedVisibility(shown, enter = motion.enter(delayMs = MikaMotion.staggerDelay(3))) {
            Text(WelcomeCopy.LEAD, Modifier.padding(top = MikaSpace.S12), style = MikaType.Reading, color = c.ink2, textAlign = TextAlign.Center)
        }
    }
}

/** Page 2: one card per kind of work, swiped sideways; the next card peeks in so it's clear there's more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AbilitiesPage() {
    val c = MikaTheme.colors
    val cards = WelcomeCopy.abilities()
    val pager = rememberPagerState { cards.size }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = MikaSpace.S32)) {
        Text(stringResource(R.string.onboarding_can_title), Modifier.padding(horizontal = MikaSpace.S24).semantics { heading() },
            style = MikaType.LargeTitle, color = c.ink)
        Text(stringResource(R.string.onboarding_can_hint), Modifier.padding(horizontal = MikaSpace.S24, vertical = MikaSpace.S8),
            style = MikaType.Caption, color = c.ink3)
        HorizontalPager(
            pager, Modifier.fillMaxWidth().padding(top = MikaSpace.S16).testTag("onboarding-abilities"),
            contentPadding = PaddingValues(horizontal = MikaSpace.S24), pageSpacing = MikaSpace.S12,
        ) { i -> AbilityCard(cards[i], i, cards.size) }
        ProgressDots(cards.size, pager.currentPage, Modifier.padding(top = MikaSpace.S16))
    }
}

@Composable
private fun AbilityCard(a: WelcomeCopy.Ability, index: Int, count: Int) {
    val c = MikaTheme.colors
    val position = stringResource(R.string.onboarding_card_position, index + 1, count)
    Column(
        Modifier.fillMaxWidth().cardSurface(c).padding(MikaSpace.S24)
            .semantics(mergeDescendants = true) { stateDescription = position },
        verticalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            IconTile(a.icon)
            Text(a.title, Modifier.weight(1f), style = MikaType.BodyStrong, color = c.ink)
            Text(stringResource(R.string.onboarding_example), Modifier.border(0.5.dp, c.hair, MikaShapes.Pill)
                .padding(horizontal = MikaSpace.S8, vertical = 2.dp), style = MikaType.Micro, color = c.ink3)
        }
        Text("“${a.example}”", style = MikaType.Headline.copy(fontWeight = FontWeight.Normal), color = c.ink)
        Text(a.body, style = MikaType.Callout, color = c.ink2)
    }
}

/** Page 3: four plain facts about where things stay, each one already true of Orbit. */
@Composable
private fun PrivacyPage() {
    val c = MikaTheme.colors
    val motion = MikaTheme.motion
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MikaSpace.S16).padding(top = MikaSpace.S32),
        verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
        Text(stringResource(R.string.onboarding_privacy_title), Modifier.padding(horizontal = MikaSpace.S8).padding(bottom = MikaSpace.S8)
            .semantics { heading() }, style = MikaType.LargeTitle, color = c.ink)
        WelcomeCopy.privacy().forEachIndexed { i, f ->
            AnimatedVisibility(shown, enter = motion.enter(delayMs = MikaMotion.staggerDelay(i + 1))) {
                Row(Modifier.fillMaxWidth().cardSurface(c).padding(MikaSpace.S16)
                    .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
                    IconTile(f.icon)
                    Column(Modifier.weight(1f)) {
                        Text(f.title, style = MikaType.BodyStrong, color = c.ink)
                        Text(f.body, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = c.ink2)
                    }
                }
            }
        }
    }
}

private val MarkSize = 112.dp

/** 欢迎页的文字。 */
object WelcomeCopy {
    val TITLE: String get() = tr(R.string.welcome_title)
    val LEAD: String get() = tr(R.string.welcome_lead)
    val NOTE: String get() = tr(R.string.welcome_note)
    val START: String get() = tr(R.string.welcome_start)

    data class Item(val icon: ImageVector, val title: String, val body: String)

    data class Ability(val icon: ImageVector, val title: String, val body: String, val example: String)

    /** The four kinds of work, each with an example request (two are the home suggestions Orbit ships). */
    fun abilities() = listOf(
        Ability(MikaIcons.Apps, tr(R.string.welcome_apps), tr(R.string.welcome_apps_body), tr(R.string.chat_example_group_summary)),
        Ability(MikaIcons.Globe, tr(R.string.welcome_web), tr(R.string.welcome_web_body), tr(R.string.chat_example_nearby)),
        Ability(MikaIcons.Doc, tr(R.string.welcome_files), tr(R.string.welcome_files_body), tr(R.string.onboarding_example_files)),
        Ability(MikaIcons.Clock, tr(R.string.welcome_schedule), tr(R.string.welcome_schedule_body), tr(R.string.onboarding_example_schedule)),
    )

    /** What stays private, in the order people ask about it. */
    fun privacy() = listOf(
        Item(MikaIcons.Key, tr(R.string.onboarding_privacy_keys), tr(R.string.onboarding_privacy_keys_body)),
        Item(MikaIcons.Globe, tr(R.string.onboarding_privacy_provider), tr(R.string.onboarding_privacy_provider_body)),
        Item(MikaIcons.Shield, tr(R.string.onboarding_privacy_hold), tr(R.string.onboarding_privacy_hold_body)),
        Item(MikaIcons.Edit, tr(R.string.onboarding_privacy_memory), tr(R.string.onboarding_privacy_memory_body)),
    )

    val FEATURES get() = listOf(
        Item(MikaIcons.Apps, tr(R.string.welcome_apps), tr(R.string.welcome_apps_body)),
        Item(MikaIcons.Globe, tr(R.string.welcome_web), tr(R.string.welcome_web_body)),
        Item(MikaIcons.Doc, tr(R.string.welcome_files), tr(R.string.welcome_files_body)),
        Item(MikaIcons.Clock, tr(R.string.welcome_schedule), tr(R.string.welcome_schedule_body)),
    )
}
