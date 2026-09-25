package com.ajkueterman.nanodungeon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ajkueterman.nanodungeon.ai.ModelStatus
import com.ajkueterman.nanodungeon.ui.theme.NanoDungeonTheme

/** Shown until Gemini Nano is downloaded and warmed up. */
@Composable
fun OnboardingScreen(status: ModelStatus, onRetry: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nano Dungeon", style = MaterialTheme.typography.headlineMedium)
            when (status) {
                is ModelStatus.Unavailable -> {
                    Text(
                        status.message,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRetry) { Text("Try again") }
                }
                else -> {
                    // Download byte counts are unreliable, so the progress indicator is indeterminate.
                    CircularProgressIndicator()
                    Text(
                        if (status is ModelStatus.Downloading) "Downloading Gemini Nano…" else "Waking the dungeon master…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingPreview() {
    NanoDungeonTheme { OnboardingScreen(ModelStatus.Downloading, onRetry = {}) }
}
