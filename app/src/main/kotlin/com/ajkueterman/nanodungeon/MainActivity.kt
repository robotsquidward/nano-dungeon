package com.ajkueterman.nanodungeon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajkueterman.nanodungeon.ai.DungeonMaster
import com.ajkueterman.nanodungeon.ai.ModelStatus
import com.ajkueterman.nanodungeon.ui.GameScreen
import com.ajkueterman.nanodungeon.ui.OnboardingScreen
import com.ajkueterman.nanodungeon.ui.theme.NanoDungeonTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var dungeonMaster: DungeonMaster

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dungeonMaster.prepare()
        setContent {
            NanoDungeonTheme {
                // Gate: the game is only shown once the on-device model is ready.
                val status by dungeonMaster.status.collectAsStateWithLifecycle()
                if (status is ModelStatus.Ready) {
                    GameScreen()
                } else {
                    OnboardingScreen(status = status, onRetry = dungeonMaster::prepare)
                }
            }
        }
    }
}
