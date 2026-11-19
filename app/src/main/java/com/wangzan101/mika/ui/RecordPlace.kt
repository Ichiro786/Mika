package com.wangzan101.mika.ui

/**
 * How a run record names a web page as a place: the core writes `网站 <domain>` (see `RunEvent.placeLabel`).
 * That marker is a stable record format, not display text: the UI only ever shows what follows it. This is the
 * one place the app reads it, so the Chinese marker can never reach the screen on its own.
 */
internal object RecordPlace {
    private const val SITE_PREFIX = "网站 "

    fun isSite(place: String): Boolean = place.trimStart().startsWith(SITE_PREFIX)

    /** The site's domain when [place] names a web page; null otherwise. */
    fun site(place: String): String? =
        place.trim().takeIf { it.startsWith(SITE_PREFIX) }?.removePrefix(SITE_PREFIX)?.trim()?.ifEmpty { null }
}
