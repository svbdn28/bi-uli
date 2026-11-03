package dev.hamper.uli

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.hamper.uli.ui.BrowseScreen
import dev.hamper.uli.ui.ExamEndScreen
import dev.hamper.uli.ui.ExamScreen
import dev.hamper.uli.ui.HomeScreen
import dev.hamper.uli.ui.QuizScreen
import dev.hamper.uli.ui.RoundEndScreen
import dev.hamper.uli.ui.theme.UliTheme
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) applyShot(intent)
        setContent {
            UliTheme {
                UliApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyShot(intent)
    }

    private fun applyShot(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        intent?.getStringExtra("shot")?.let(vm::applyShot)
    }
}

private fun Screen.depth(): Int = when (this) {
    Screen.Home -> 0
    is Screen.Browse, Screen.Quiz, Screen.ExamRun -> 1
    Screen.RoundEnd, Screen.ExamEnd -> 2
}

@Composable
fun UliApp(vm: AppViewModel) {
    val screen = vm.screen
    var backProgress by remember { mutableFloatStateOf(0f) }
    // The exam handles back itself (it asks before throwing an attempt away).
    PredictiveBackHandler(enabled = screen != Screen.Home && screen != Screen.ExamRun) { events ->
        try {
            events.collect { backProgress = it.progress }
            vm.home()
        } catch (e: CancellationException) {
            // Gesture cancelled: stay.
        } finally {
            backProgress = 0f
        }
    }
    val density = LocalDensity.current
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = screen,
            contentKey = { it::class },
            transitionSpec = {
                val spatial = motion.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                val effects = motion.defaultEffectsSpec<Float>()
                if (targetState.depth() >= initialState.depth()) {
                    (slideInHorizontally(spatial) { it / 5 } + fadeIn(effects) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.98f))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                } else {
                    fadeIn(effects).togetherWith(slideOutHorizontally(spatial) { it / 5 } + fadeOut(motion.fastEffectsSpec()))
                }
            },
            label = "screen",
        ) { s ->
            val p = if (s == screen) backProgress else 0f
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - 0.08f * p
                        scaleX = scale
                        scaleY = scale
                        translationX = with(density) { 24.dp.toPx() } * p
                    }
                    .clip(RoundedCornerShape((32 * p).dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                when (s) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Quiz -> QuizScreen(vm)
                    Screen.RoundEnd -> RoundEndScreen(vm)
                    Screen.ExamRun -> ExamScreen(vm)
                    Screen.ExamEnd -> ExamEndScreen(vm)
                    is Screen.Browse -> BrowseScreen(vm, s)
                }
            }
        }
    }
}
