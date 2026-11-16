package com.wangzan101.mika.core.cap.phone

import com.wangzan101.mika.core.action.ApprovalSettings
import com.wangzan101.mika.core.action.ConfirmChoice
import com.wangzan101.mika.core.agent.AgentLoop
import com.wangzan101.mika.core.agent.FakeModel
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.gate.ConfirmationGate
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.Role
import com.wangzan101.mika.core.model.StopReason
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.testing.answerReply
import com.wangzan101.mika.core.tool.FakeScreen
import com.wangzan101.mika.core.uistate.ScreenPort
import com.wangzan101.mika.core.uistate.SystemCommandResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class Shell : ScreenPort by FakeScreen() {
    val ran = mutableListOf<String>()
    override suspend fun systemCommand(command: String): SystemCommandResult { ran += command; return SystemCommandResult(0, "Success") }
}

private var seq = 0
private fun shell(command: String) = ModelReply(
    listOf(Part.ToolCall("c${++seq}", "phone_system_command", JsonObject(mapOf("command" to JsonPrimitive(command))).toString())),
    StopReason.ToolUse, null,
)
private val FINISH get() = answerReply("done")

private fun FakeModel.result(i: Int): JsonObject =
    Json.parseToJsonElement(requests[i].last { it.role == Role.Tool }.parts.filterIsInstance<Part.ToolResult>().first().content).jsonObject
private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

/**
 * Uninstalling an app through the shell asks first in Ask mode, like a delete on screen: the card shows the
 * command and the app it removes, it runs only once the user agrees, and Autonomous mode does not ask.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackageRemovalTest {
    @Test fun recognisesCommandsThatRemoveAnAppOrItsData() {
        assertEquals("com.example.app", PackageRemoval.target("pm uninstall com.example.app"))
        assertEquals("com.example.app", PackageRemoval.target("pm uninstall -k --user 0 com.example.app"))
        assertEquals("com.example.app", PackageRemoval.target("cmd package uninstall com.example.app"))
        assertEquals("com.example.app", PackageRemoval.target("cmd  package   uninstall --versionCode 12 'com.example.app'"))
        assertEquals("com.example.app", PackageRemoval.target("sleep 1 && pm uninstall com.example.app; echo ok"))
        assertEquals("com.example.app", PackageRemoval.target("sh -c \"pm uninstall com.example.app\""))
        assertEquals("com.example.app", PackageRemoval.target("pm clear com.example.app"))
        assertEquals("com.android.chrome", PackageRemoval.target("pm uninstall-system-updates com.android.chrome"))
        // Removes something, but which app can't be read out of the text.
        assertEquals("", PackageRemoval.target("pm uninstall \$PKG"))
        assertEquals("", PackageRemoval.target("pm uninstall"))
    }

    @Test fun leavesOtherCommandsAlone() {
        for (command in listOf(
            "pm list packages", "pm path com.example.app", "cmd package list packages -3", "dumpsys package com.example.app",
            "settings get system screen_off_timeout", "echo pm uninstalled nothing", "cat /sdcard/pm-uninstall.log",
            "pm install /data/local/tmp/app.apk", "npm uninstall left-pad", "ls /sdcard/clear",
        )) assertNull(command, PackageRemoval.target(command))
    }

    private fun loop(m: FakeModel, screen: ScreenPort, autonomous: Boolean = false) =
        AgentLoop(m, toolDispatcher(screen), "BASE", confirmations = ConfirmationGate(), approvals = ApprovalSettings { autonomous })

    @Test fun uninstallAsksFirstAndRunsOnceApproved() = runTest {
        val screen = Shell()
        val m = FakeModel(shell("pm uninstall com.example.app"), FINISH)
        val l = loop(m, screen)
        val run = async { l.run("t") }
        advanceUntilIdle()
        val waiting = l.state.value as TaskState.WaitingConfirmation
        assertEquals(IrrevAction.delete, waiting.request.action)
        assertEquals("com.example.app", waiting.request.app)
        assertEquals("pm uninstall com.example.app", waiting.request.content)
        assertTrue(screen.ran.isEmpty())
        l.answerConfirmation(ConfirmChoice.Once)
        run.await()
        assertEquals(listOf("pm uninstall com.example.app"), screen.ran)
        assertEquals("true", m.result(1).s("ok"))
    }

    @Test fun declinedUninstallDoesNotRun() = runTest {
        val screen = Shell()
        val m = FakeModel(shell("cmd package uninstall com.example.app"), FINISH)
        val l = loop(m, screen)
        val run = async { l.run("t") }
        advanceUntilIdle()
        l.answerConfirmation(ConfirmChoice.Deny)
        run.await()
        assertTrue(screen.ran.isEmpty())
        assertEquals("confirmation_declined", m.result(1).s("error"))
    }

    @Test fun autonomousModeDoesNotAsk() = runTest {
        val screen = Shell()
        val m = FakeModel(shell("pm uninstall com.example.app"), FINISH)
        loop(m, screen, autonomous = true).run("t")
        assertEquals(listOf("pm uninstall com.example.app"), screen.ran)
    }

    @Test fun otherCommandsStillRunWithoutAsking() = runTest {
        val screen = Shell()
        val m = FakeModel(shell("pm list packages -3"), FINISH)
        loop(m, screen).run("t")
        assertEquals(listOf("pm list packages -3"), screen.ran)
        assertEquals("true", m.result(1).s("ok"))
    }
}
