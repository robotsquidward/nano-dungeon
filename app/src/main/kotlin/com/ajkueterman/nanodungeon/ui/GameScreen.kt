package com.ajkueterman.nanodungeon.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ajkueterman.nanodungeon.game.Moment
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
                title = {
                    val number = (state as? GameUiState.Exploring)?.sceneNumber
                    Text(if (number != null) "Scene $number" else "Nano Dungeon")
                },
                actions = { TextButton(onClick = onNewRun) { Text("New run") } },
            )
        },
    ) { padding ->
        Box(Modifier
            .fillMaxSize()
            .padding(padding)) {
            when (state) {
                GameUiState.Starting -> CenteredMessage(
                    "You descend into the dark…",
                    showSpinner = true
                )

                is GameUiState.Error -> ErrorMessage(state.message, onRetry)
                is GameUiState.Exploring -> ExploringContent(state, onChoose)
            }
        }
    }
}

@Composable
private fun ExploringContent(state: GameUiState.Exploring, onChoose: (Choice) -> Unit) {
    val listState = rememberLazyListState()
    // When something new happens, scroll to the top of the newest moment.
    LaunchedEffect(state.moments.size, state.current.title) {
        listState.animateScrollToItem(state.moments.lastIndex + 1)
    }
    Column(Modifier.fillMaxSize()) {
        // The scene log scrolls. The choices stay pinned below it.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") { SceneHeader(state.current) }
            itemsIndexed(state.moments) { index, moment ->
                MomentEntry(
                    moment,
                    isLatest = index == state.moments.lastIndex,
                    Modifier.animateItem()
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isThinking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "The dungeon shifts around you…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // One button per generated Choice from the newest Scene.
                state.current.choices.forEach { choice ->
                    ChoiceButton(
                        choice,
                        enabled = !state.isThinking,
                        onClick = { onChoose(choice) })
                }
            }
        }
    }
}

/** The location's title, plus the mood the model picked for the newest moment. */
@Composable
fun SceneHeader(scene: Scene, modifier: Modifier = Modifier) {
    val moodColor by animateColorAsState(scene.moodColor(), label = "mood")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            scene.mood.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = moodColor
        )
        Text(scene.title, style = MaterialTheme.typography.headlineMedium)
        HorizontalDivider(thickness = 2.dp, color = moodColor)
    }
}

/** One step in the scene log: what the player did, then what happened. */
@Composable
private fun MomentEntry(moment: Moment, isLatest: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        moment.action?.let { action ->
            Text(
                "› ${action.label}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            moment.scene.narration,
            style = MaterialTheme.typography.bodyLarge,
            // Earlier moments fade back so the newest one stands out.
            color = if (isLatest) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A generated [Choice]. Its `kind` sets the style: "investigate" is a filled
 * button that keeps you here, and "move" is an outlined button that leads elsewhere.
 */
@Composable
fun ChoiceButton(
    choice: Choice,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content: @Composable RowScope.() -> Unit = {
        Column(Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)) {
            Text(
                if (choice.isMove) "MOVE ON" else "INVESTIGATE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(choice.label, style = MaterialTheme.typography.titleMedium)
            Text(
                choice.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val buttonModifier = modifier.fillMaxWidth()
    if (choice.isMove) {
        OutlinedButton(
            onClick,
            buttonModifier,
            enabled,
            shape = MaterialTheme.shapes.medium,
            content = content
        )
    } else {
        FilledTonalButton(
            onClick,
            buttonModifier,
            enabled,
            shape = MaterialTheme.shapes.medium,
            content = content
        )
    }
}

private fun Scene.moodColor(): Color = when (mood) {
    MOOD_CALM -> MoodColors.calm
    MOOD_EERIE -> MoodColors.eerie
    MOOD_DANGEROUS -> MoodColors.dangerous
    else -> Color.Gray
}

@Composable
private fun CenteredMessage(text: String, showSpinner: Boolean = false) {
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

// Handwritten Scenes. The model's output has exactly this shape, so any Scene works here.
private val PreviewArrival = Scene(
    title = "The Weeping Culvert",
    narration = "Cold water laps at your ankles as the tunnel narrows. A rotted leather pouch hangs " +
            "from a rusted hook, and pale fungus crowds the brickwork beside it.",
    mood = MOOD_EERIE,
    choices = emptyList(),
)

private val PreviewOutcome = Scene(
    title = "The Weeping Culvert",
    narration = "The pouch splits as you lift it. Three tarnished silver coins and a damp, folded note " +
            "spill into your palm. The note reads only: 'Do not follow the bells.'",
    mood = MOOD_EERIE,
    choices = listOf(
        Choice(
            "Scrape a sample of the fungus",
            "It glistens faintly, smelling of sour milk.",
            KIND_INVESTIGATE
        ),
        Choice(
            "Follow the rushing water",
            "The roar grows louder, and colder air drifts back.",
            KIND_MOVE
        ),
    ),
)

private val PreviewMoments = listOf(
    Moment(action = null, scene = PreviewArrival),
    Moment(action = Choice("Open the leather pouch", "", KIND_INVESTIGATE), scene = PreviewOutcome),
)

@Preview
@Composable
private fun GameScreenPreview() {
    NanoDungeonTheme {
        GameScreen(
            GameUiState.Exploring(PreviewMoments, sceneNumber = 3),
            onChoose = {},
            onRetry = {},
            onNewRun = {})
    }
}

@Preview
@Composable
private fun GameScreenThinkingPreview() {
    NanoDungeonTheme {
        GameScreen(
            GameUiState.Exploring(PreviewMoments, sceneNumber = 3, isThinking = true),
            onChoose = {}, onRetry = {}, onNewRun = {},
        )
    }
}
