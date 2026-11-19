package com.wangzan101.mika.ui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSpecsTest {
    private val full = MikaMotion.specs(reduceMotion = false)
    private val reduced = MikaMotion.specs(reduceMotion = true)

    private fun spring(spec: Any?): SpringSpec<*> = spec as SpringSpec<*>

    @Test
    fun spatialSpringsMatchTheExpressiveTokens() {
        assertEquals(0.9f, spring(full.spatialFast<Float>()).dampingRatio)
        assertEquals(1400f, spring(full.spatialFast<Float>()).stiffness)
        assertEquals(700f, spring(full.spatialDefault<Float>()).stiffness)
        assertEquals(300f, spring(full.spatialSlow<Float>()).stiffness)
        assertEquals(0.6f, spring(full.bouncy<Float>()).dampingRatio)
        assertEquals(500f, spring(full.bouncy<Float>()).stiffness)
    }

    @Test
    fun effectsSpringsNeverBounce() {
        listOf(full.effectsFast<Float>(), full.effectsDefault<Float>(), full.effectsSlow<Float>()).forEach {
            assertEquals(1f, spring(it).dampingRatio)
        }
    }

    @Test
    fun removeAnimationsSnapsEverySpecFromOnePlace() {
        listOf(
            reduced.spatialFast<Float>(), reduced.spatialDefault<Float>(), reduced.spatialSlow<Float>(), reduced.bouncy<Float>(),
            reduced.effectsFast<Float>(), reduced.effectsDefault<Float>(), reduced.effectsSlow<Float>(),
            reduced.timed<Float>(MikaMotion.Duration.MEDIUM), reduced.offset(), reduced.size(),
            MikaMotion.spatial<Float>(true), MikaMotion.effects<Float>(true), MikaMotion.page<Float>(true), MikaMotion.switch<Float>(true),
        ).forEach { assertTrue("$it should snap", it is SnapSpec<*>) }
    }

    @Test
    fun timedTweensUseTheDurationScale() {
        val t = full.timed<Float>(MikaMotion.Duration.SHORT) as TweenSpec<Float>
        assertEquals(200, t.durationMillis)
        assertEquals(100, MikaMotion.Duration.MICRO)
        assertEquals(300, MikaMotion.Duration.MEDIUM)
        assertEquals(450, MikaMotion.Duration.LONG)
    }

    @Test
    fun exitIsFasterThanEnter() {
        assertTrue(spring(full.spatialFast<Float>()).stiffness > spring(full.spatialDefault<Float>()).stiffness)
        assertTrue(spring(full.effectsFast<Float>()).stiffness > spring(full.effectsDefault<Float>()).stiffness)
    }

    @Test
    fun staggerIsThirtyMillisecondsCappedAtSixItems() {
        assertEquals(0, MikaMotion.staggerDelay(0))
        assertEquals(30, MikaMotion.staggerDelay(1))
        assertEquals(150, MikaMotion.staggerDelay(5))
        assertEquals(150, MikaMotion.staggerDelay(40))
    }
}
