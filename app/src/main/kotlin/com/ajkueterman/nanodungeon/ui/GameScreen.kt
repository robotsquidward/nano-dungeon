package com.ajkueterman.nanodungeon.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajkueterman.nanodungeon.ai.Choice
import com.ajkueterman.nanodungeon.ai.KIND_INVESTIGATE
import com.ajkueterman.nanodungeon.ai.KIND_MOVE
import com.ajkueterman.nanodungeon.ai.MOOD_CALM
import com.ajkueterman.nanodungeon.ai.MOOD_DANGEROUS
import com.ajkueterman.nanodungeon.ai.MOOD_EERIE
import com.ajkueterman.nanodungeon.ai.Scene
import com.ajkueterman.nanodungeon.ai.isMove
import com.ajkueterman.nanodungeon.game.GameUiState
import com.ajkueterman.nanodungeon.game.GameViewModel
import com.ajkueterman.nanodungeon.ui.theme.MoodColors
import com.ajkueterman.nanodungeon.ui.theme.NanoDungeonTheme

@Composable
fun GameScreen(viewModel: GameViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameScreen(
        state = state,
        onChoose = viewModel::choose,
        onRetry = viewModel::retry,
        onNewRun = viewModel::newRun,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameUiState,
    onChoose: (Choice) -> Unit,
    onRetry: () -> Unit,
    onNewRun: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nano Dungeon") },
                actions = { TextButton(onClick = onNewRun) { Text("New run") } },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state) {
                is GameUiState.Starting -> LoadingMessage()
                is GameUiState.Error -> ErrorMessage(state.message, onRetry)
                is GameUiState.Exploring -> ExploringContent(state, onChoose)
            }
        }
    }
}

@Composable
private fun ExploringContent(
    state: GameUiState.Exploring,
    onChoose: (Choice) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cross-fade when the model returns a new Scene.
        AnimatedContent(
            targetState = state.scene,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "scene",
        ) { scene ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SceneCard(scene)
                // One button per generated Choice. The schema allows 2 or 3.
                scene.choices.forEach { choice ->
                    ChoiceButton(
                        choice,
                        enabled = !state.isThinking,
                        onClick = { onChoose(choice) })
                }
            }
        }
        if (state.isThinking) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "The dungeon shifts around you…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renders a model-generated [Scene]. Every field on screen comes from the
 * structured output, including the border color, which comes from `mood`.
 */
@Composable
fun SceneCard(
    scene: Scene,
    modifier: Modifier = Modifier,
) {
    val moodColor by animateColorAsState(scene.moodColor(), label = "mood")
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, moodColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = scene.mood.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = moodColor,
            )
            Text(
                text = scene.title,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = scene.narration,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * A generated [Choice]: the label is the action and the hint is the flavor text under it.
 * The small caption comes from its `kind`.
 */
@Composable
fun ChoiceButton(
    choice: Choice,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = if (choice.isMove) "MOVE ON" else "INVESTIGATE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = choice.label,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = choice.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Scene.moodColor(): Color = when (mood) {
    MOOD_CALM -> MoodColors.calm
    MOOD_EERIE -> MoodColors.eerie
    MOOD_DANGEROUS -> MoodColors.dangerous
    else -> Color.Gray
}

@Composable
private fun LoadingMessage(
    text: String = "You descend into the dark…",
    showSpinner: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) CircularProgressIndicator()
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your torch gutters out.", style = MaterialTheme.typography.titleLarge)
        Text(
            message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}

// A handwritten Scene. The model's output has exactly this shape, so any Scene works here.
private val PreviewScene = Scene(
    title = "The Weeping Culvert",
    narration = "Cold water laps at your ankles as the tunnel narrows. Somewhere ahead, " +
            "a steady roar echoes off slick brick, and the air smells of rust and rot.",
    mood = MOOD_EERIE,
    choices = listOf(
        Choice(
            "rotted pouch",
            "Open the rotted pouch",
            "Something inside clinks faintly.",
            KIND_INVESTIGATE
        ),
        Choice(
            "rushing water",
            "Follow the rushing water",
            "The roar grows louder, and colder air drifts back.",
            KIND_MOVE
        ),
        Choice(
            "side passage",
            "Squeeze into the side passage",
            "A smell of wet moss and turned earth.",
            KIND_MOVE
        ),
    ),
)

@Preview
@Composable
private fun GameScreenPreview() {
    NanoDungeonTheme {
        GameScreen(GameUiState.Exploring(PreviewScene), onChoose = {}, onRetry = {}, onNewRun = {})
    }
}

@Preview
@Composable
private fun GameScreenThinkingPreview() {
    NanoDungeonTheme {
        GameScreen(
            GameUiState.Exploring(PreviewScene, isThinking = true),
            onChoose = {}, onRetry = {}, onNewRun = {},
        )
    }
}
