# Nano Dungeon: MVP plan

**Status (2026-09-25):** The MVP is built. It passes `assembleDebug` and 3 unit tests, and it was verified on a Pixel 10 (`nano-v4-fast`): the opening scene and 5 chained choices each took about 5s.

**Differences from the plan:** `MainActivity` gates on `DungeonMaster.status` directly, with no OnboardingViewModel. `genai-prompt` 1.0.0-beta4 exposes only the `STOP`/`MAX_TOKENS`/`OTHER` typed finish reasons.

## Context
This is a small demo app to share with other developers. It shows the ML Kit GenAI **Prompt API**, focusing on **structured output** (`@Generable` / `@Guide` → Kotlin data class). An on-device "dungeon master" returns a typed `Scene` data class. That class goes unchanged through a ViewModel into Compose, so the model's output *is* the UI state. The first pass is one vertical slice: an opening scene, two choices, and a loop. Combat is deferred.

Reference app: `~/Developer/dungeon-intern`. It uses the Prompt API only for plain text, never structured output. We copy its build setup, Hilt wiring, model prepare/download flow and inference mutex.

## Decisions (from Q&A)
- **Location:** `~/Developer/nano-dungeon`, package `com.ajkueterman.nanodungeon`, with `git init`.
- **Architecture:** mirror dungeon-intern, with Hilt, a `@HiltViewModel`, `StateFlow`-driven sealed UI state and an onboarding state gate in `MainActivity`. It stays a **single `:app` module**, and there is no Nav Compose because there is only one screen. Both keep the demo readable.
- **Opening:** the app picks a random seed setting, such as a crypt beneath the chapel, the sewers under the city, or a flooded dwarven mine. The first structured call generates the opening `Scene`.
- **Memory:** the ViewModel keeps a trail of `Breadcrumb(sceneTitle, choiceLabel)`. The last 5 entries go into each prompt, which stays well under the 4K input-token limit.

## Build setup (copied from `dungeon-intern/gradle/libs.versions.toml`)
- Gradle 9.6.0, AGP 9.4.1 (built-in Kotlin, so no `kotlin-android` plugin), Kotlin 2.4.10, KSP 2.3.7, Hilt 2.60.1, Compose BOM 2026.08.00, lifecycle 2.11.0, activity-compose 1.13.0, coroutines 1.11.0.
- compileSdk/targetSdk 37, minSdk 31, Java 21 (Android Studio JBR, as in dungeon-intern's `AGENTS.md`).
- `com.google.mlkit:genai-prompt:1.0.0-beta4`
- **New:** `ksp("com.google.mlkit:genai-schema-compiler:1.0.0-alpha1")` is the structured-output schema generator.
- If R8 is ever enabled, add a keep rule for `@Generable` classes. Debug-only builds don't need it.

## Files (`app/src/main/kotlin/com/ajkueterman/nanodungeon/`)
| File | Purpose |
|---|---|
| `NanoDungeonApp.kt` | `@HiltAndroidApp` |
| `MainActivity.kt` | `@AndroidEntryPoint`. Gate: `ModelStatus.Ready` → `GameScreen`, otherwise `OnboardingScreen`, following dungeon-intern's `MainActivity` pattern |
| `ai/Scene.kt` | **The star.** `@Generable` data classes, heavily commented for readers |
| `ai/DungeonMasterPrompt.kt` | System instruction plus a pure `userPrompt(seed, trail, current, choice)` builder |
| `ai/DungeonMaster.kt` | `@Singleton`. Owns the `GenerativeModel`, the `prepare()` (checkStatus/download/warmup) flow, the `status: StateFlow<ModelStatus>`, the inference `Mutex`, and `suspend fun nextScene(...): Scene` |
| `game/GameViewModel.kt` | `@HiltViewModel`. Owns `GameUiState` and the trail, and handles `start()` and `choose(choice)` |
| `ui/GameScreen.kt` | `SceneCard(scene)`, `ChoiceButton(choice)`, a thinking overlay and an error/retry state. `@Preview` uses a hand-built `Scene` to show that a plain data class drives the UI |
| `ui/OnboardingScreen.kt` | Preparing (indeterminate progress, because byte counts are unreliable), Unavailable and Failed states |
| `ui/theme/` | Minimal dark M3 theme |

### Structured output shape (`ai/Scene.kt`)
```kotlin
@Generable
data class Scene(
    @Guide(description = "Evocative name of the current location, 2-5 words")
    val title: String,
    @Guide(description = "2-4 sentences of second-person flavor text describing what the player sees, hears and smells")
    val narration: String,
    @Guide(description = "Overall feeling of the room", enumValues = ["calm", "eerie", "dangerous"])
    val mood: String,          // drives card tint/icon, showing enumValues → UI
    @Guide(description = "Exactly two distinct paths forward", minItems = 2, maxItems = 2)
    val choices: List<Choice>,
)

@Generable
data class Choice(
    @Guide(description = "Short imperative button label, max 6 words")
    val label: String,
    @Guide(description = "One sensory hint about where this leads, max 12 words")
    val hint: String,
)
```
The exact annotation import paths and parameter names are checked against the `genai-schema-compiler` / `genai-prompt` artifacts at build time.

### Inference call (`DungeonMaster.nextScene`)
```kotlin
val base = generateContentRequest(
    SystemInstruction(DungeonMasterPrompt.SYSTEM),
    TextPart(DungeonMasterPrompt.userPrompt(seed, trail, current, choice)),
) { temperature = 0.8f; candidateCount = 1 }
val typed = generateTypedContentRequest(base, Scene::class)
val candidate = mutex.withLock { model.generateContent(typed) }.candidates.firstOrNull()
return candidate?.response ?: throw IOException("No scene (finishReason=${candidate?.finishReason})")
```
- `ModelConfig` matches dungeon-intern: `ModelReleaseStage.PREVIEW` with `ModelPreference.FAST`.
- A failed parse or constraint violation (`PARSE_CLASS_ERROR`, `STRUCTURE_VALUES_INVALID`, `GenAiException` -105) triggers one automatic retry. After that, the UI shows `Error` with a Retry button.

### Prompting
- **System instruction** is under 150 words: you are a terse dungeon master, second person, dark fantasy, never mention being an AI, always give exactly two meaningfully different options, and no combat resolution yet.
- **User turn** is built by the pure function:
  ```
  Setting: <seed>
  Path so far: 1. <title> → chose "<label>" ... (last 5)
  Current room: <title>: <narration>
  The player chose: "<label>" (<hint>)
  Describe the next room the player enters.
  ```
  The opening turn replaces the last three lines with "Describe the entrance where the adventure begins."

### UI state
```kotlin
sealed interface GameUiState {
    data object Starting : GameUiState
    data class Exploring(val scene: Scene, val depth: Int, val isThinking: Boolean) : GameUiState
    data class Error(val message: String) : GameUiState
}
```
While `isThinking` is true, `Exploring` keeps the previous scene on screen with the buttons disabled and a "The dungeon shifts…" indicator. A "New run" action resets the trail and re-seeds.

## Out of scope (future iterations)
- Combat: a second `@Generable` (`Encounter`) plus a sealed "DM response" shape.
- Streaming, prefix caching of the system and setting prefix, persistence, and navigation.

## Verification
1. Build with `./gradlew :app:assembleDebug`, using JDK 21 from the Android Studio JBR. The KSP schema generation must succeed.
2. Run JVM unit tests (JUnit 5) on `DungeonMasterPrompt.userPrompt` for trail truncation to 5 and the opening vs. next-turn formats.
3. Check the `@Preview` render of `GameScreen` with a fake `Scene`.
4. On a **Pixel 10** (the emulator reports UNAVAILABLE), run `adb install` and launch, then check that:
   - onboarding reaches Ready;
   - the opening scene renders with 2 buttons;
   - 5 or more choices chain coherently and the breadcrumbs are reflected;
   - a forced error shows the Retry state.
   Watch `adb logcat` for the finishReason and the parsed `Scene`.
