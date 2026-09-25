package com.ajkueterman.nanodungeon.ai

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/**
 * One moment in the dungeon. The on-device model returns this data class directly.
 *
 * This file is the core of the demo. `@Generable` tells the ML Kit schema compiler
 * (a KSP processor) to turn this class into a JSON schema. The Prompt API then
 * constrains Gemini Nano's output to that schema and decodes the result back
 * into a `Scene`. The app never parses JSON itself.
 *
 * Each `@Guide` is extra prompting that sits on the field. `description` says
 * what belongs there, and `enumValues` / `minItems` / `maxItems` are hard constraints.
 *
 * The same object goes straight into Compose as UI state. See `ui/GameScreen.kt`.
 */
@Generable(description = "One moment in a dark fantasy dungeon crawl")
data class Scene(
    @Guide(description = "Evocative name of the current location, 2 to 5 words. Unchanged while the player stays in the same location")
    val title: String,

    @Guide(description = "2 to 4 sentences of second-person narration of what the player experiences right now, with concrete sensory detail")
    val narration: String,

    // A closed set of values the UI can switch on, so the model picks the card's color.
    @Guide(
        description = "The overall feeling of this moment",
        enumValues = [MOOD_CALM, MOOD_EERIE, MOOD_DANGEROUS]
    )
    val mood: String,

    // 2 or 3 buttons, in any mix of "move" and "investigate".
    @Guide(
        description = "2 or 3 distinct things the player can do next, in any mix of kinds, each about a different target. At least one must be a 'move' choice",
        minItems = 2,
        maxItems = 3
    )
    val choices: List<Choice>,
)

/** Something the player can do next. Rendered as a button. */
@Generable(description = "An action the player can take")
data class Choice(
    // Declared first so the model commits to *what* the choice is about before writing
    // the label. The app also uses it to drop near-duplicate choices. See distinctTargets().
    @Guide(description = "The one object or direction this choice is about, 1 to 3 words, e.g. 'leather pouch' or 'north tunnel'")
    val target: String,

    @Guide(description = "Short imperative button label naming a specific thing, at most 6 words, e.g. 'Open the leather pouch'")
    val label: String,

    @Guide(description = "One short sensory hint about this action, at most 12 words")
    val hint: String,

    // The model tells the app what the action *does*: stay here, or go somewhere new.
    @Guide(
        description = "'investigate' interacts with something in the current location and stays here. 'move' leaves for a different location",
        enumValues = [KIND_INVESTIGATE, KIND_MOVE],
    )
    val kind: String,
)

val Choice.isMove: Boolean get() = kind == KIND_MOVE

/**
 * Drops investigate choices about the same thing, e.g. "wooden table" and
 * "rough-hewn table". Two targets match when their last word (the noun)
 * matches. Moves are never dropped, because "north tunnel" and "south tunnel"
 * really are different.
 */
fun List<Choice>.distinctTargets(): List<Choice> {
    val seen = mutableSetOf<String>()
    return filter { choice ->
        val noun = choice.target.lowercase().split(Regex("[^a-z]+")).lastOrNull { it.isNotEmpty() }
        choice.isMove || noun == null || seen.add(noun)
    }
}

const val MOOD_CALM = "calm"
const val MOOD_EERIE = "eerie"
const val MOOD_DANGEROUS = "dangerous"

const val KIND_INVESTIGATE = "investigate"
const val KIND_MOVE = "move"
