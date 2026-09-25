package com.ajkueterman.nanodungeon.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DungeonMasterPromptTest {

    private val openPouch = Choice("pouch", "Open the pouch", "Something clinks inside", KIND_INVESTIGATE)
    private val goLeft = Choice("left passage", "Go left", "Warm air", KIND_MOVE)

    private val arrival = Scene(
        title = "Flooded Gallery",
        narration = "Black water rises to your chest. A pouch hangs from a hook.",
        mood = MOOD_EERIE,
        choices = listOf(openPouch, goLeft),
    )
    private val afterPouch = arrival.copy(narration = "The pouch holds three silver coins.")

    @Test
    fun `opening prompt names the setting and asks for an entrance`() {
        val prompt = DungeonMasterPrompt.openingPrompt("a sunken crypt")
        assertTrue("Setting: a sunken crypt" in prompt)
        assertTrue("entrance" in prompt)
    }

    @Test
    fun `move prompt asks for a new location`() {
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", emptyList(), arrival, arrival, goLeft)
        assertTrue("Location: Flooded Gallery: Black water rises to your chest." in prompt)
        assertTrue("The player chose: \"Go left\" (Warm air)" in prompt)
        assertTrue("new location" in prompt)
        assertFalse("Just now" in prompt)
        assertFalse("What the player has done" in prompt)
    }

    @Test
    fun `investigate prompt restates the location and what just happened`() {
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", emptyList(), arrival, afterPouch, openPouch)
        assertTrue("Location: Flooded Gallery: Black water rises to your chest." in prompt)
        assertTrue("Just now: The pouch holds three silver coins." in prompt)
        assertTrue("They stay in Flooded Gallery" in prompt)
        assertTrue("Keep the title \"Flooded Gallery\"" in prompt)
        assertFalse("last discovery" in prompt)
    }

    @Test
    fun `last investigation asks the model to wrap up`() {
        val prompt = DungeonMasterPrompt.nextPrompt(
            "a sunken crypt", emptyList(), arrival, afterPouch, openPouch,
            investigationsHere = DungeonMasterPrompt.MAX_INVESTIGATIONS - 1,
        )
        assertTrue("last discovery" in prompt)
    }

    @Test
    fun `prompt keeps only the most recent breadcrumbs`() {
        val trail = (1..10).map { Breadcrumb("Room $it", "Choice $it") }
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", trail, arrival, arrival, goLeft)
        val steps = prompt.lines().filter { it.matches(Regex("""\d+\. In .*""")) }
        assertEquals(DungeonMasterPrompt.MAX_BREADCRUMBS, steps.size)
        assertTrue(steps.first().contains("Room 5"))
        assertTrue(steps.last().contains("Room 10"))
    }
}
