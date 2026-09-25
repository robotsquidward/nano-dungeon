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

sealed interface GameUiState {
    /** Waiting for the opening scene. */
    data object Starting : GameUiState

    /**
     * The player is looking at [scene], which comes straight from the model.
     * While [isThinking] is true, the next scene is being generated and the choices are disabled.
     */
    data class Exploring(val scene: Scene, val isThinking: Boolean = false) : GameUiState

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

    /**
     * The scene that described where the player is. It changes only on a "move".
     * It's sent with every prompt so investigations stay consistent with the location.
     */
    private var location: Scene? = null

    /** Investigations done at [location]. After [DungeonMasterPrompt.MAX_INVESTIGATIONS], only moves are offered. */
    private var investigationsHere = 0

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
            location = opening
            investigationsHere = 0
            _state.value = GameUiState.Exploring(opening)
        }
    }

    fun choose(choice: Choice) {
        val exploring = _state.value as? GameUiState.Exploring ?: return
        if (exploring.isThinking) return
        val current = exploring.scene
        val here = location ?: current
        _state.update { exploring.copy(isThinking = true) }
        launchStep {
            val next = dungeonMaster.nextScene(seed, trail.toList(), here, current, choice, investigationsHere)
            trail += Breadcrumb(here.title, choice.label)
            val scene = if (choice.isMove) {
                location = next
                investigationsHere = 0
                next
            } else {
                // The prompt asks the model to wrap up the last investigation, and this enforces it.
                val choices = if (DungeonMasterPrompt.isLastInvestigation(investigationsHere)) {
                    next.choices.filter { it.isMove }
                } else {
                    next.choices
                }
                investigationsHere++
                // Still in the same place, so keep its title even if the model renamed it.
                next.copy(title = here.title, choices = choices)
            }
            _state.value = GameUiState.Exploring(scene)
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
