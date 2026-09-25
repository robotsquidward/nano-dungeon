package com.ajkueterman.nanodungeon.ai

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/**
 * One room of the dungeon. The on-device model returns this data class directly.
 *
 * This file is the core of the demo. `@Generable` tells the ML Kit schema compiler
 * (a KSP processor) to turn this class into a JSON schema. The Prompt API then
 * constrains Gemini Nano's output to that schema and decodes the result back
 * into a `Scene`. The app never parses JSON itself.
 *
 * Each `@Guide` is extra prompting that sits on the field. `description` says
 * what belongs there, and `enumValues` / `minItems` / `maxItems` are hard constraints.
 *
 * The same object goes straight into Compose as UI state. See `SceneCard` in
 * `ui/GameScreen.kt`.
 */
@Generable(description = "A single room in a dark fantasy dungeon crawl")
data class Scene(
    @Guide(description = "Evocative name of the current location, 2 to 5 words")
    val title: String,

    @Guide(description = "2 to 4 sentences of second-person flavor text describing what the player sees, hears and smells")
    val narration: String,

    // A closed set of values the UI can switch on, so the model picks the card's color.
    @Guide(description = "The overall feeling of this room", enumValues = [MOOD_CALM, MOOD_EERIE, MOOD_DANGEROUS])
    val mood: String,

    // Always exactly two choices, so the UI can count on two buttons.
    @Guide(description = "Exactly two distinct, meaningfully different paths forward", minItems = 2, maxItems = 2)
    val choices: List<Choice>,
)

/** One of the two things the player can do next. Rendered as a button. */
@Generable(description = "An action the player can take to leave the current room")
data class Choice(
    @Guide(description = "Short imperative button label, at most 6 words, e.g. 'Follow the rushing water'")
    val label: String,

    @Guide(description = "One short sensory hint about where this leads, at most 12 words")
    val hint: String,
)

const val MOOD_CALM = "calm"
const val MOOD_EERIE = "eerie"
const val MOOD_DANGEROUS = "dangerous"
