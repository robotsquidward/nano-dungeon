package com.ajkueterman.nanodungeon.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DungeonMasterPromptTest {

    private val room = Scene(
        title = "Dripping Vault",
        narration = "Water drips from the ceiling.",
        mood = MOOD_CALM,
        choices = listOf(Choice("Go left", "Warm air"), Choice("Go right", "A distant bell")),
    )

    @Test
    fun `opening prompt names the setting and asks for an entrance`() {
        val prompt = DungeonMasterPrompt.openingPrompt("a sunken crypt")
        assertTrue("Setting: a sunken crypt" in prompt)
        assertTrue("entrance" in prompt)
    }

    @Test
    fun `next prompt includes current room and chosen option`() {
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", emptyList(), room, room.choices[1])
        assertTrue("Current room: Dripping Vault: Water drips from the ceiling." in prompt)
        assertTrue("The player chose: \"Go right\" (A distant bell)" in prompt)
        assertFalse("Path so far" in prompt)
    }

    @Test
    fun `next prompt keeps only the most recent breadcrumbs`() {
        val trail = (1..8).map { Breadcrumb("Room $it", "Choice $it") }
        val prompt = DungeonMasterPrompt.nextPrompt("a sunken crypt", trail, room, room.choices[0])
        val steps = prompt.lines().filter { "-> chose" in it }
        assertEquals(DungeonMasterPrompt.MAX_BREADCRUMBS, steps.size)
        assertTrue(steps.first().contains("Room 4"))
        assertTrue(steps.last().contains("Room 8"))
    }
}
