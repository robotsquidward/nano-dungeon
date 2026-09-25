package com.ajkueterman.nanodungeon.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChoiceTest {

    private fun investigate(target: String) = Choice(target, "Examine the $target", "", KIND_INVESTIGATE)
    private fun move(target: String) = Choice(target, "Go to the $target", "", KIND_MOVE)

    @Test
    fun `investigations about the same object are collapsed`() {
        val choices = listOf(investigate("wooden table"), investigate("Rough-hewn table"), move("north tunnel"))
        assertEquals(listOf("wooden table", "north tunnel"), choices.distinctTargets().map { it.target })
    }

    @Test
    fun `moves with the same noun are kept`() {
        val choices = listOf(move("north tunnel"), move("south tunnel"))
        assertEquals(choices, choices.distinctTargets())
    }

    @Test
    fun `different objects are kept`() {
        val choices = listOf(investigate("leather pouch"), investigate("mossy carving"), move("stairs"))
        assertEquals(choices, choices.distinctTargets())
    }
}
