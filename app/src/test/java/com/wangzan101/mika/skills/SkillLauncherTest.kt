package com.wangzan101.mika.skills

import com.wangzan101.mika.ui.SkillRow
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillLauncherTest {
    private fun row(name: String, recent: Int, suspended: Boolean = false) =
        SkillRow(name, name, name, "", "", suspended = suspended, recent = List(recent) { "r$it" })

    @Test fun widgetShowsTheMostUsedEnabledSkillsOnly() {
        val top = SkillLauncher.top(listOf(row("a", 1), row("b", 5), row("off", 9, suspended = true), row("c", 3), row("d", 0)))
        assertEquals(listOf("b", "c", "a"), top.map { it.name })
    }

    @Test fun widgetHoldsAtMostThree() {
        assertEquals(SkillLauncher.WIDGET_SKILLS, SkillLauncher.top((1..10).map { row("s$it", it) }).size)
    }
}
