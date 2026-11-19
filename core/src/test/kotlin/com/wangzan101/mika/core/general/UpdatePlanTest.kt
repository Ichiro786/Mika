package com.wangzan101.mika.core.general

import com.wangzan101.mika.core.assembly.knownTools
import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.tool.ActionPass
import com.wangzan101.mika.core.tool.FakeScreen
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.Verb
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePlanTest {
    private val tools = toolDispatcher(FakeScreen())
    private fun call(args: String) = runBlocking { tools.execute(Part.ToolCall("p", UpdatePlan.NAME, args), ActionPass()) }

    @Test fun offeredAsAReadOnlyPlanTool() {
        assertTrue(UpdatePlan.NAME in tools.definitions().map { it.name })
        assertEquals(Verb.UpdatePlan, knownTools().verb(UpdatePlan.NAME))
        assertEquals(Risk.Read, UpdatePlan.risk)
    }

    @Test fun acceptsAPlanAndItsCurrentStep() {
        val r = call("""{"steps":["Open Maps","Search","Book"],"current":2}""")
        assertEquals(JsonPrimitive(3), r.content["planSteps"])
        assertEquals(JsonPrimitive(2), r.content["current"])
    }

    @Test fun rejectsACurrentStepOutsideThePlan() {
        val r = call("""{"steps":["a","b"],"current":5}""")
        assertEquals(JsonPrimitive(false), r.content["ok"])
    }

    @Test fun encodesOneStepPerLine() {
        assertEquals("a\nb c", UpdatePlan.encode(listOf("a", "b\nc")))
    }
}
