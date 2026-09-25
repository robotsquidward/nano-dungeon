package com.ajkueterman.nanodungeon.ai

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelConfig
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the on-device model can be used yet. */
sealed interface ModelStatus {
    data object Checking : ModelStatus
    data object Downloading : ModelStatus
    data object Ready : ModelStatus
    data class Unavailable(val message: String) : ModelStatus
}

/**
 * The on-device dungeon master. It wraps the ML Kit GenAI Prompt API.
 *
 * It does two jobs:
 * 1. [prepare] checks that Gemini Nano is on the device, downloads it if
 *    needed, and warms it up. It publishes progress to [status].
 * 2. [openingScene] / [nextScene] send a prompt and get back a typed [Scene].
 *    The model never returns free text here.
 */
@Singleton
class DungeonMaster @Inject constructor() {

    private val model: GenerativeModel = Generation.getClient(
        generationConfig {
            modelConfig = ModelConfig.builder()
                .apply {
                    releaseStage = ModelReleaseStage.PREVIEW
                    preference = ModelPreference.FAST
                }
                .build()
        },
    )

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Checking)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    // The download can take minutes, so it runs in this singleton's scope and a
    // configuration change won't cancel it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // AICore runs one inference at a time. Overlapping calls fail, so we queue them.
    private val inferenceMutex = Mutex()

    /** Starts the check / download / warmup, unless it is already running or done. */
    fun prepare() {
        if (_status.value is ModelStatus.Downloading || _status.value is ModelStatus.Ready) return
        _status.value = ModelStatus.Checking
        scope.launch {
            _status.value = try {
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> Unit
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                        _status.value = ModelStatus.Downloading
                        model.download().collect { Log.d(TAG, "Download: $it") }
                    }
                    else -> throw IOException("Gemini Nano isn't available on this device.")
                }
                model.warmup()
                Log.i(TAG, "Model ready: ${runCatching { model.getBaseModelName() }.getOrNull()}")
                ModelStatus.Ready
            } catch (e: Exception) {
                Log.w(TAG, "Model prepare failed", e)
                ModelStatus.Unavailable(e.message ?: "Couldn't prepare the on-device model.")
            }
        }
    }

    /** Generates the first room of a new run. */
    suspend fun openingScene(seed: String): Scene =
        generateScene(DungeonMasterPrompt.openingPrompt(seed))

    /** Generates the room the player reaches after picking [choice] in [current]. */
    suspend fun nextScene(seed: String, trail: List<Breadcrumb>, current: Scene, choice: Choice): Scene =
        generateScene(DungeonMasterPrompt.nextPrompt(seed, trail, current, choice))

    /**
     * The structured-output call:
     * 1. Build a normal [generateContentRequest] with the system instruction and prompt.
     * 2. Wrap it with [generateTypedContentRequest], passing `Scene::class`.
     *    This attaches the schema that KSP generated from `@Generable`.
     * 3. `generateContent` returns typed candidates. `candidate.response` is
     *    already a [Scene].
     *
     * On-device models sometimes return output that doesn't fit the schema, so
     * we retry once before giving up.
     */
    private suspend fun generateScene(prompt: String): Scene = withContext(Dispatchers.IO) {
        val request = generateContentRequest(
            SystemInstruction(DungeonMasterPrompt.SYSTEM),
            TextPart(prompt),
        ) {
            temperature = 0.8f
            candidateCount = 1
        }
        val typedRequest = generateTypedContentRequest(request, Scene::class)

        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val response = inferenceMutex.withLock { model.generateContent(typedRequest) }
                val candidate = response.candidates.firstOrNull()
                val scene = candidate?.response
                // Also check the list size here, in case a constraint slips through.
                if (scene != null && scene.choices.size == 2) {
                    Log.d(TAG, "Scene: $scene")
                    return@withContext scene
                }
                lastError = IOException("Unusable scene (finishReason=${candidate?.finishReason}): $scene")
            } catch (e: Exception) {
                lastError = e
            }
            Log.w(TAG, "Scene attempt ${attempt + 1} failed", lastError)
        }
        throw lastError ?: IOException("The dungeon master is silent.")
    }

    private companion object {
        const val TAG = "DungeonMaster"
        const val MAX_ATTEMPTS = 2
    }
}
