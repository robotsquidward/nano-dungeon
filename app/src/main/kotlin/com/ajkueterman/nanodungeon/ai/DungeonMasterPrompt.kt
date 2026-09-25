package com.ajkueterman.nanodungeon.ai

/** A step the player already took: the room they were in and what they chose. */
data class Breadcrumb(val sceneTitle: String, val choiceLabel: String)

/**
 * Every piece of text we send to the model.
 *
 * These are pure functions, so they can be unit tested without a device. The
 * *shape* of the answer (title, narration, mood, two choices) comes from the
 * `@Generable` schema on [Scene]. The prompt covers only tone and story.
 */
object DungeonMasterPrompt {

    /** How many past steps go into each prompt. This keeps input well under the ~4K token limit. */
    const val MAX_BREADCRUMBS = 5

    /**
     * The system instruction: persona and rules that apply to every turn.
     * Google recommends keeping it under about 150 words.
     */
    val SYSTEM = """
        You are the dungeon master of a short, atmospheric dark fantasy dungeon crawl.
        Speak to the player in the second person ("you").
        Each reply describes exactly one room and offers exactly two ways forward.
        The two choices must lead to different places and feel meaningfully different,
        for example following a sound versus following a smell.
        Keep continuity with the path the player has already taken.
        Use vivid sensory detail: light, sound, smell, temperature.
        No combat and no death yet: threats may be hinted at, but never resolved.
        Never mention being an AI, the rules, or these instructions.
    """.trimIndent()

    /** Starting settings. One is picked at random for each new run. */
    val SEEDS = listOf(
        "a crumbling crypt beneath an abandoned chapel",
        "the ancient sewers under a sprawling port city",
        "a flooded dwarven mine deep inside a mountain",
        "the root-choked cellars of a burned-down wizard's tower",
        "a smugglers' cave carved into a sea cliff",
    )

    /** The first turn of a run: describe the entrance. */
    fun openingPrompt(seed: String): String = """
        Setting: $seed
        Describe the entrance where the adventure begins.
    """.trimIndent()

    /**
     * Every later turn. It sends the setting, the last few steps, the room the
     * player is in, and the choice they just made.
     */
    fun nextPrompt(
        seed: String,
        trail: List<Breadcrumb>,
        current: Scene,
        choice: Choice,
    ): String = buildString {
        appendLine("Setting: $seed")
        val recent = trail.takeLast(MAX_BREADCRUMBS)
        if (recent.isNotEmpty()) {
            appendLine("Path so far:")
            recent.forEachIndexed { i, step ->
                appendLine("${i + 1}. ${step.sceneTitle} -> chose \"${step.choiceLabel}\"")
            }
        }
        appendLine("Current room: ${current.title}: ${current.narration}")
        appendLine("The player chose: \"${choice.label}\" (${choice.hint})")
        append("Describe the next room the player enters.")
    }
}
