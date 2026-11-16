package com.wangzan101.mika.ui.onboarding

import com.wangzan101.mika.i18n.ChineseTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingFlowTest {
    @get:Rule val texts = ChineseTexts()

    private val all = OnboardingFlow.steps(phoneAvailable = true)

    @Test
    fun theFlowRunsWelcomeToDoneAndDropsThePhoneStepWhereItCannotWork() {
        assertEquals(
            listOf(FlowStep.Welcome, FlowStep.CanDo, FlowStep.Privacy, FlowStep.Model, FlowStep.Phone, FlowStep.Permissions, FlowStep.Personal, FlowStep.Done),
            all,
        )
        val noPhone = OnboardingFlow.steps(phoneAvailable = false)
        assertFalse(FlowStep.Phone in noPhone)
        assertEquals(FlowStep.Permissions, OnboardingFlow.next(FlowStep.Model, noPhone))
        assertEquals(FlowStep.Model, OnboardingFlow.previous(FlowStep.Permissions, noPhone))
    }

    @Test
    fun backWorksOnEveryStepAndTheEndsStop() {
        all.drop(1).forEach { step -> assertEquals(all[all.indexOf(step) - 1], OnboardingFlow.previous(step, all)) }
        assertNull(OnboardingFlow.previous(FlowStep.Welcome, all))
        assertNull(OnboardingFlow.next(FlowStep.Done, all))
        assertEquals(1, OnboardingFlow.position(FlowStep.Welcome, all))
        assertEquals(all.size, OnboardingFlow.position(FlowStep.Done, all))
    }

    @Test
    fun everyOptionalStepCanBeSkippedAndTheModelStepCannot() {
        assertEquals(setOf(FlowStep.CanDo, FlowStep.Phone, FlowStep.Permissions, FlowStep.Personal), all.filter(OnboardingFlow::optional).toSet())
        assertFalse(OnboardingFlow.optional(FlowStep.Model))
    }

    @Test
    fun existingUsersNeverSeeItAndAHalfFinishedRunComesBack() {
        // New install: no model, nothing done.
        assertTrue(OnboardingFlow.shouldStart(done = false, resume = null, hasMainModel = false, modelSetupDone = false))
        // Updated from an earlier version: a model is already there (or the old setup was finished).
        assertFalse(OnboardingFlow.shouldStart(done = false, resume = null, hasMainModel = true, modelSetupDone = false))
        assertFalse(OnboardingFlow.shouldStart(done = false, resume = null, hasMainModel = false, modelSetupDone = true))
        // Killed half way, after the model step: it resumes even though a model now exists.
        assertTrue(OnboardingFlow.shouldStart(done = false, resume = FlowStep.Phone, hasMainModel = true, modelSetupDone = false))
        // Finished: never again, whatever else is true.
        assertFalse(OnboardingFlow.shouldStart(done = true, resume = FlowStep.Phone, hasMainModel = false, modelSetupDone = false))
    }

    @Test
    fun resumingPicksTheSavedStepWhenItStillExists() {
        assertEquals(FlowStep.Permissions, OnboardingFlow.resumeAt(FlowStep.Permissions, all))
        assertEquals(FlowStep.Welcome, OnboardingFlow.resumeAt(null, all))
        assertEquals(FlowStep.Welcome, OnboardingFlow.resumeAt(FlowStep.Phone, OnboardingFlow.steps(phoneAvailable = false)))
    }

    @Test
    fun threeStartersEitherWay() {
        val withPhone = OnboardingFlow.starters(phoneReady = true)
        val without = OnboardingFlow.starters(phoneReady = false)
        assertEquals(3, withPhone.size)
        assertEquals(3, without.size)
        assertEquals(listOf("ob-message", "ob-summary", "ob-brief"), withPhone.map { it.id })
        // Without phone control nothing offered needs another app.
        assertEquals(listOf("ob-research", "ob-file", "ob-brief"), without.map { it.id })
        (withPhone + without).forEach { assertTrue(it.title.isNotBlank() && it.prompt.isNotBlank()) }
    }

    @Test
    fun namesAreTidiedForGreetings() {
        assertEquals("Sam", OnboardingFlow.cleanName("  Sam \n"))
        assertEquals("Ana Maria", OnboardingFlow.cleanName("Ana   Maria"))
        assertEquals("", OnboardingFlow.cleanName("   "))
        assertEquals(OnboardingFlow.NAME_MAX, OnboardingFlow.cleanName("x".repeat(200)).length)
    }

    @Test
    fun keyProviderChipsAreOnlyOnesTheAppKnows() {
        assertEquals(listOf("Anthropic", "OpenAI", "Groq"), keyProviders(listOf("Groq", "OpenAI", "Ollama", "Anthropic")))
        assertEquals(emptyList<String>(), keyProviders(listOf("Ollama")))
    }
}
