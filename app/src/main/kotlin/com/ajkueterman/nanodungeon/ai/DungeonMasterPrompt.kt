package com.ajkueterman.nanodungeon.ai

/** Something the player already did: where they were and what they chose. */
data class Breadcrumb(val sceneTitle: String, val choiceLabel: String)

/**
 * Every piece of text we send to the model.
 *
 * These are pure functions, so they can be unit tested without a device. The
 * *shape* of the answer (title, narration, mood, choices and their kinds) comes
 * from the `@Generable` schema on [Scene]. The prompt covers only tone and story.
 */
object DungeonMasterPrompt {

    /** How many past actions go into each prompt. This keeps input well under the ~4K token limit. */
    const val MAX_BREADCRUMBS = 6

    /**
     * How many investigations a location supports. More than this tends to
     * get repetitive, so the last one is asked to wrap up.
     */
    const val MAX_INVESTIGATIONS = 2

    /**
     * The system instruction: persona and rules that apply to every turn.
     * Google recommends keeping it under about 150 words.
     */
    val SYSTEM = """
        You are the dungeon master of a short, atmospheric dark fantasy dungeon crawl.
        Speak to the player as "you", in lean, specific prose: one sharp detail beats
        three adjectives. Never repeat details the player already knows.
        Offer 2 or 3 choices, mixing freely: two directions, a direction and something
        to investigate, or both. Always include at least one "move" choice.
        Investigate choices interact with one specific thing: an object, plant,
        container, carving or remains. Results are concrete and can help or hurt:
        a pouch holds coins or a note, a plant's spores make you cough and feel sick.
        A given room will usually have one investigate choice at most, maybe two.
        Each investigation reveals something new; never offer the same thing twice.
        Stay consistent with the location's size, light, water and air.
        Player is a human with normal limitations--can't breathe underwater or see in the dark, etc.
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

    /** True when the investigation about to happen is the last one this location supports. */
    fun isLastInvestigation(investigationsHere: Int): Boolean = investigationsHere + 1 >= MAX_INVESTIGATIONS

    /** The first turn of a run: describe the entrance. */
    fun openingPrompt(seed: String): String = """
        Setting: $seed
        Describe the entrance where the adventure begins.
    """.trimIndent()

    /**
     * Every later turn.
     *
     * [location] is the scene that first described where the player is now.
     * [current] is what's on screen. After an investigation the two differ,
     * and sending [location] again keeps the model from forgetting where
     * the player is, e.g. a flooded tunnel or a vast cavern.
     *
     * [investigationsHere] counts investigations already done in this location.
     */
    fun nextPrompt(
        seed: String,
        trail: List<Breadcrumb>,
        location: Scene,
        current: Scene,
        choice: Choice,
        investigationsHere: Int = 0,
    ): String = buildString {
        appendLine("Setting: $seed")
        val recent = trail.takeLast(MAX_BREADCRUMBS)
        if (recent.isNotEmpty()) {
            appendLine("What the player has done so far:")
            recent.forEachIndexed { i, step ->
                appendLine("${i + 1}. In ${step.sceneTitle}: \"${step.choiceLabel}\"")
            }
        }
        appendLine("Location: ${location.title}: ${location.narration}")
        if (current != location) appendLine("Just now: ${current.narration}")
        appendLine("The player chose: \"${choice.label}\" (${choice.hint})")

        if (choice.isMove) {
            append("They leave. Describe the new location they enter.")
        } else {
            appendLine("They stay in ${location.title}. Describe only what happens, consistent with the location above, without describing the location again.")
            append("Keep the title \"${location.title}\".")
            if (isLastInvestigation(investigationsHere)) {
                append(" This is the last discovery here: bring it to a clear conclusion, then offer only \"move\" choices.")
            }
        }
    }
}
