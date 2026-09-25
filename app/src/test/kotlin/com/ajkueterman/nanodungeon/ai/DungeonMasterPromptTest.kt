package com.ajkueterman.nanodungeon.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DungeonMasterPromptTest {

    private val openPouch = Choice("Open the pouch", "Something clinks inside", KIND_INVESTIGATE)
    private val goLeft = Choice("Go left", "Warm air", KIND_MOVE)

    private val arrival = Scene(
        title = "Dripping Vault",
        narration = "Water drips from the ceiling. A pouch hangs from a hook.",
        mood = MOOD_CALM,
        choices = listOf(openPouch, goLeft),
    )
    private val outcome = arrival.copy(narration = "The pouch holds three silver coins.")

    @Test
    fun `opening prompt names the setting and asks for an entrance`() {
        val prompt = DungeonMasterPrompt.openingPrompt("a sunken crypt")
        assertTrue("Setting: a sunken crypt" in prompt)
        assertTrue("entrance" in prompt)
    }

    @Test
    fun `move prompt asks for a new location`() {
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", emptyList(), listOf(arrival), goLeft)
        assertTrue("Current location: Dripping Vault: Water drips from the ceiling." in prompt)
        assertTrue("The player chose: \"Go left\" (Warm air)" in prompt)
        assertTrue("new location" in prompt)
        assertFalse("What the player has done" in prompt)
        assertFalse("Most recently" in prompt)
    }

    @Test
    fun `investigate prompt stays in the location and includes the latest outcome`() {
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", emptyList(), listOf(arrival, outcome), openPouch)
        assertTrue("Most recently: The pouch holds three silver coins." in prompt)
        assertTrue("They stay in Dripping Vault" in prompt)
        assertTrue("Keep the title \"Dripping Vault\"" in prompt)
        assertFalse("explored enough" in prompt)
    }

    @Test
    fun `investigate prompt forces a move once the location is explored`() {
        val scene = List(DungeonMasterPrompt.MAX_INVESTIGATIONS_PER_SCENE) { outcome }
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", emptyList(), scene, openPouch)
        assertTrue("explored enough" in prompt)
    }

    @Test
    fun `prompt keeps only the most recent breadcrumbs`() {
        val trail = (1..10).map { Breadcrumb("Room $it", "Choice $it") }
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", trail, listOf(arrival), goLeft)
        val steps = prompt.lines().filter { it.matches(Regex("""\d+\. In .*""")) }
        assertEquals(DungeonMasterPrompt.MAX_BREADCRUMBS, steps.size)
        assertTrue(steps.first().contains("Room 5"))
        assertTrue(steps.last().contains("Room 10"))
    }

    @Test
    fun `prompt lists what was already done in this location`() {
        val trail = listOf(Breadcrumb("Entry Hall", "Go down"), Breadcrumb("Dripping Vault", "Open the pouch"))
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", trail, listOf(arrival, outcome), openPouch)
        assertTrue("Already done here (don't offer these again): Open the pouch" in prompt)
        assertFalse("Already done here (don't offer these again): Go down" in prompt)
    }
}
