# Nano Dungeon

A tiny dungeon crawl where an on-device LLM is the dungeon master.

It demonstrates the ML Kit GenAI **[Prompt API](https://developers.google.com/ml-kit/genai/prompt/android)**,
and in particular **[structured output](https://developers.google.com/ml-kit/genai/prompt/android/structured-output)**.
Gemini Nano returns a Kotlin data class instead of free text, and that object goes
unchanged through a ViewModel into Compose.

Everything runs on device, using Gemini Nano 4 Preview.

## How it works

```
@Generable Scene  ──►  DungeonMaster  ──►  GameViewModel  ──►  GameScreen
(schema via KSP)       (Prompt API)        (StateFlow)         (SceneCard + ChoiceButtons)
```

1. **The schema is a data class.** In [`Scene.kt`](app/src/main/kotlin/com/ajkueterman/nanodungeon/ai/Scene.kt),
   `Scene` and `Choice` are annotated with `@Generable`, and each field carries a `@Guide`:
   - a `description`, which acts as a prompt for that field;
   - optional constraints such as `enumValues`, `minItems` and `maxItems`.

   The `genai-schema-compiler` KSP processor generates the JSON schema at build time.

   ```kotlin
   @Generable(description = "One moment in a dark fantasy dungeon crawl")
   data class Scene(
       @Guide(description = "Evocative name of the current location, 2 to 5 words")
       val title: String,
       @Guide(description = "2 or 3 short sentences ... of what is new right now")
       val narration: String,
       @Guide(description = "The overall feeling of this moment", enumValues = ["calm", "eerie", "dangerous"])
       val mood: String,
       @Guide(description = "2 or 3 distinct things the player can do next ...", minItems = 2, maxItems = 3)
       val choices: List<Choice>,
   )
   ```

2. **Ask for that type.** In [`DungeonMaster.kt`](app/src/main/kotlin/com/ajkueterman/nanodungeon/ai/DungeonMaster.kt),
   a normal request is wrapped with `generateTypedContentRequest(request, Scene::class)`.
   The response candidates are already typed, so there's no JSON parsing:

   ```kotlin
   val request = generateContentRequest(SystemInstruction(SYSTEM), TextPart(prompt)) { temperature = 1f }
   val typedRequest = generateTypedContentRequest(request, Scene::class)
   val scene: Scene? = model.generateContent(typedRequest).candidates.firstOrNull()?.response
   ```

3. **Render it.** [`GameScreen.kt`](app/src/main/kotlin/com/ajkueterman/nanodungeon/ui/GameScreen.kt)
   takes the `Scene` as-is:
   - `SceneCard(scene)` draws the title and narration, with `mood` choosing the border color;
   - one `ChoiceButton` is drawn per generated `Choice`, with `kind` setting its caption.

### The game loop

- **Opening.** Each run picks a random setting, such as a crypt, sewers or a flooded mine, and asks for the opening scene.
- **Choices.** The model offers 2 or 3 choices, and each has a `kind`:
  - **move**: go somewhere new, which gives a new location;
  - **investigate**: interact with something specific here, such as a pouch, a plant or a carving. The player stays in the same location and the model describes a concrete result.
- **Choosing.** Tapping a choice sends a prompt built from:
  - the setting;
  - the player's last few actions (breadcrumbs);
  - the current location's description;
  - what just happened;
  - the chosen option.
- **Consistency.** The location description is sent again on every investigation, so the model doesn't forget where the player is, e.g. a vast cavern or a waist-deep tunnel.

### Prompting vs. app logic

The prompt and the schema do most of the work. A few rules are also enforced in code,
because a small on-device model doesn't always follow instructions:

| Rule | Where |
|---|---|
| 2–3 choices, valid `mood` / `kind` values | Schema constraints (`@Guide`) |
| At least one "move" choice; retry once if missing | `DungeonMaster.generateScene` |
| Drop near-duplicate investigations ("wooden table" vs "rough-hewn table") | `distinctTargets()` in `Scene.kt` |
| At most 2 investigations per location, then only moves | `DungeonMasterPrompt` + `GameViewModel` |
| Location title stays fixed while investigating | `GameViewModel.choose` |

All prompt text lives in [`DungeonMasterPrompt.kt`](app/src/main/kotlin/com/ajkueterman/nanodungeon/ai/DungeonMasterPrompt.kt)
as pure functions, and it's covered by JVM unit tests.

## Project layout

```
app/src/main/kotlin/com/ajkueterman/nanodungeon/
├── ai/
│   ├── Scene.kt                 # @Generable output types: the core of the demo
│   ├── DungeonMasterPrompt.kt   # System instruction + prompt builders
│   └── DungeonMaster.kt         # Prompt API client: status/download, typed generation
├── game/
│   └── GameViewModel.kt         # GameUiState, breadcrumbs, location tracking
├── ui/
│   ├── GameScreen.kt            # SceneCard, ChoiceButton, previews
│   ├── OnboardingScreen.kt      # Model download / unavailable states
│   └── theme/Theme.kt
├── MainActivity.kt              # Gates the game on model readiness
└── NanoDungeonApp.kt            # @HiltAndroidApp
```

## Requirements

- **Device:** one that supports the ML Kit GenAI Prompt API through AICore (developed on a
  Pixel 10). Emulators report the model as unavailable.
- **Model:** the app asks for the preview release stage with `ModelPreference.FULL`. You can
  switch which on-device model is served in the AICore app.
- **Build tools:** JDK 21+ (Android Studio's bundled JBR works), AGP 9.4, Kotlin 2.4, compileSdk 37, minSdk 31.

Key dependencies:

```toml
mlkit-genai-prompt = { module = "com.google.mlkit:genai-prompt", version = "1.0.0-beta4" }
mlkit-genai-schema-compiler = { module = "com.google.mlkit:genai-schema-compiler", version = "1.0.0-alpha1" }  # ksp(...)
```

## Build & run

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug       # build and install on a connected device
./gradlew :app:testDebugUnitTest  # prompt + choice logic tests
```

The first launch may download the model, which can take a few minutes. Then the opening scene
is generated; each scene takes a few seconds on-device. Tag `DungeonMaster` in logcat
shows every parsed `Scene`.

## Not yet

This is a small vertical slice. Ideas for later:

- Combat, as a second `@Generable` type with its own UI.
- An inventory built from what investigations turn up.
- Prefix caching for the shared system instruction.
