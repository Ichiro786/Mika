package com.wangzan101.mika.settings

import kotlin.math.roundToInt

/** Stored dimensions remain independent of the current window and font scale. */
data class OrbitPresenceSize(val headerDp: Int = DEFAULT_HEADER, val hubDp: Int = DEFAULT_HUB) {
    fun clamped() = OrbitPresenceSize(headerDp.coerceIn(MIN_HEADER, MAX_HEADER), hubDp.coerceIn(MIN_HUB, MAX_HUB))

    fun headerForWidth(widthDp: Int): Int =
        clamped().headerDp.coerceAtMost(((if (widthDp > 0) widthDp else 320) - 192).coerceAtLeast(1))

    fun hubForWindow(widthDp: Int, heightDp: Int, fontScale: Float): Int {
        val width = if (widthDp > 0) widthDp else 320
        val height = if (heightDp > 0) heightDp else 640
        val reserved = (200f + 40f * fontScale.coerceIn(1f, 2f)).roundToInt()
        return minOf(clamped().hubDp, (width - 40).coerceAtLeast(1), (height - reserved).coerceAtLeast(1))
    }

    companion object {
        const val MIN_HEADER = 44
        const val MAX_HEADER = 76
        const val DEFAULT_HEADER = 52
        const val MIN_HUB = 130
        const val MAX_HUB = 220
        const val DEFAULT_HUB = 130
    }
}
