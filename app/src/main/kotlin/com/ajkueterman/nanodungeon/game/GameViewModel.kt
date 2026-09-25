package com.ajkueterman.nanodungeon.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajkueterman.nanodungeon.ai.Breadcrumb
import com.ajkueterman.nanodungeon.ai.Choice
import com.ajkueterman.nanodungeon.ai.DungeonMaster
import com.ajkueterman.nanodungeon.ai.DungeonMasterPrompt
import com.ajkueterman.nanodungeon.ai.Scene
import com.ajkueterman.nanodungeon.ai.isMove
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One entry in the current scene's log: the model's [Scene], plus the choice
 * that led to it. [action] is null for the moment the player arrived.
 */
data class Moment(val action: Choice?, val scene: Scene)

sealed interface GameUiState {
    /** Waiting for the opening scene. */
    data object Starting : GameUiState

    /**
     * The player is in a scene. [moments] is everything that has happened in
     * this location, oldest first. The newest [Scene] supplies the choices.
     * While [isThinking] is true, the next moment is being generated and the
     * choices are disabled.
     */
    data class Exploring(
        val moments: List<Moment>,
        val sceneNumber: Int,
        val isThinking: Boolean = false,
    ) : GameUiState {
        val current: Scene get() = moments.last().scene
    }

    data class Error(val message: String) : GameUiState
}

@HiltViewModel
class GameViewModel @Inject constructor(
    private val dungeonMaster: DungeonMaster,
) : ViewModel() {

    private val _state = MutableStateFlow<GameUiState>(GameUiState.Starting)
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var seed = DungeonMasterPrompt.SEEDS.random()

    /** Every choice made this run, oldest first. */
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
        launchStep {
            val opening = dungeonMaster.openingScene(seed)
            _state.value = GameUiState.Exploring(listOf(Moment(null, opening)), sceneNumber = 1)
        }
    }

    fun choose(choice: Choice) {
        val exploring = _state.value as? GameUiState.Exploring ?: return
        if (exploring.isThinking) return
        _state.update { exploring.copy(isThinking = true) }
        launchStep {
            val scenesHere = exploring.moments.map { it.scene }
            val next = dungeonMaster.nextScene(seed, trail.toList(), scenesHere, choice)
            trail += Breadcrumb(exploring.current.title, choice.label)
            _state.value = if (choice.isMove) {
                // A new location: start a fresh log.
                GameUiState.Exploring(listOf(Moment(choice, next)), exploring.sceneNumber + 1)
            } else {
                // Same location: add to the log. The title is pinned so it can't drift.
                // Once the location is explored, keep only "move" choices, even if the model offered more.
                val choices = if (DungeonMasterPrompt.isExplored(scenesHere)) next.choices.filter { it.isMove } else next.choices
                val moment = Moment(choice, next.copy(title = exploring.current.title, choices = choices))
                exploring.copy(moments = exploring.moments + moment, isThinking = false)
            }
        }
    }

    fun retry() {
        val step = lastStep ?: return
        _state.value = GameUiState.Starting
        launchStep(step)
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
