package com.ajkueterman.nanodungeon.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajkueterman.nanodungeon.ai.Breadcrumb
import com.ajkueterman.nanodungeon.ai.Choice
import com.ajkueterman.nanodungeon.ai.DungeonMaster
import com.ajkueterman.nanodungeon.ai.DungeonMasterPrompt
import com.ajkueterman.nanodungeon.ai.Scene
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GameUiState {
    /** Waiting for the opening scene. */
    data object Starting : GameUiState

    /**
     * The player is in a room. [scene] comes straight from the model. While
     * [isThinking] is true, the next room is being generated and the choices are disabled.
     */
    data class Exploring(
        val scene: Scene,
        val depth: Int,
        val isThinking: Boolean = false
    ) : GameUiState

    data class Error(val message: String) : GameUiState
}

@HiltViewModel
class GameViewModel @Inject constructor(
    private val dungeonMaster: DungeonMaster,
) : ViewModel() {

    private val _state = MutableStateFlow<GameUiState>(GameUiState.Starting)
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var seed = DungeonMasterPrompt.SEEDS.random()

    /** The rooms visited and choices made, oldest first. */
    private val trail = mutableListOf<Breadcrumb>()

    /** The step to re-run when the player taps Retry. */
    private var lastStep: (suspend () -> Unit)? = null

    init {
        newRun()
    }

    fun newRun() {
        seed = DungeonMasterPrompt.SEEDS.random()
        trail.clear()
        _state.value = GameUiState.Starting
        launchStep { enter(dungeonMaster.openingScene(seed)) }
    }

    fun choose(choice: Choice) {
        val current = _state.value as? GameUiState.Exploring ?: return
        if (current.isThinking) return
        _state.update { current.copy(isThinking = true) }
        launchStep {
            val next = dungeonMaster.nextScene(seed, trail.toList(), current.scene, choice)
            trail += Breadcrumb(current.scene.title, choice.label)
            enter(next)
        }
    }

    fun retry() {
        val step = lastStep ?: return
        _state.value = GameUiState.Starting
        launchStep(step)
    }

    private fun enter(scene: Scene) {
        _state.value = GameUiState.Exploring(scene = scene, depth = trail.size + 1)
    }

    /** Runs one model call. On failure it shows the error and remembers the step for [retry]. */
    private fun launchStep(step: suspend () -> Unit) {
        lastStep = step
        viewModelScope.launch {
            try {
                step()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = GameUiState.Error(e.message ?: "Something went wrong in the dark.")
            }
        }
    }
}
