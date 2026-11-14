package com.wangzan101.mika.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/** Chat, Tasks, Make and the personal assistant share one shell (drawer, hub and bottom bar). */
internal fun isPrimaryRoute(route: Route): Boolean =
    route == Route.Chat || route == Route.Personal || route == Route.Tasks || route == Route.Make

/** Left-to-right order of the bottom bar, so a tab change drifts the way the finger moved. */
private fun tabOrder(route: Route): Int = when (route) {
    Route.Chat, Route.Personal -> 0
    Route.Tasks -> 1
    Route.Make -> 2
    else -> 3
}

private val slideSpring = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

private fun AnimatedContentTransitionScope<Route>.still(): ContentTransform = (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false))

/** Tabs: a quick fade with a small sideways drift in the direction of travel. */
internal fun AnimatedContentTransitionScope<Route>.tabTransition(reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return still()
    val dir = if (tabOrder(targetState) >= tabOrder(initialState)) 1 else -1
    return (fadeIn(tween(180, delayMillis = 40)) + slideInHorizontally(slideSpring) { dir * (it * 0.04f).toInt() })
        .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(slideSpring) { -dir * (it * 0.04f).toInt() })
        .using(SizeTransform(clip = false))
}

/** Pages: a shared X-axis slide and fade; [forward] is a push, otherwise a step back. */
internal fun AnimatedContentTransitionScope<Route>.pageTransition(forward: Boolean, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return still()
    val dir = if (forward) 1 else -1
    return (fadeIn(tween(220, delayMillis = 30)) + slideInHorizontally(slideSpring) { dir * (it * 0.08f).toInt() })
        .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(slideSpring) { -dir * (it * 0.08f).toInt() })
        .apply { targetContentZIndex = if (forward) 1f else -1f }
        .using(SizeTransform(clip = false))
}
