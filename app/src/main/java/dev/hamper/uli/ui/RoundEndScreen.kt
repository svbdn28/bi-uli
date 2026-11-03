package dev.hamper.uli.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.RoundKind
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RoundEndScreen(vm: AppViewModel) {
    val s = vm.roundSummary ?: return
    val c = Uli.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val ratio = s.score.toFloat() / s.total
    val good = ratio >= 0.8f
    val readiness = (Srs.readiness(vm.ids, vm.progress) * 100).roundToInt()
    val mastered = Srs.masteredCount(vm.ids, vm.progress)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 24.dp, bottom = bottom + 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ScoreBadge(s.score, s.total, good) }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                    Text(
                        when {
                            s.score == s.total -> "Bez chyby"
                            good -> "Výborne"
                            ratio >= 0.6f -> "Dobré kolo"
                            else -> "Ešte to dáš"
                        },
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            s.mistakes.isEmpty() && s.kind == RoundKind.Mistakes ->
                                "Všetko na prvý pokus. Z Chýb zmiznú, keď ich trafíš aj neskôr: o 10 minút a potom o 4 hodiny."
                            s.mistakes.isEmpty() -> "Všetko na prvý pokus."
                            s.kind == RoundKind.Mistakes -> "Opravené. Tieto otázky sa ti vrátia o 10 minút."
                            else -> "Chyby si hneď opravil. Vrátia sa ti o 10 minút, kým sa neusadia."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("+${s.newlyMastered}", "novo zvládnuté", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(1f))
                    StatTile("$mastered/${vm.ids.size}", "zvládnuté spolu", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
                    StatTile("$readiness %", "odhad na skúške", if (readiness >= 80) c.rightContainer else MaterialTheme.colorScheme.primaryContainer, if (readiness >= 80) c.onRightContainer else MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
                }
            }
            if (s.mistakes.isNotEmpty()) {
                item {
                    Text(
                        "Na zopakovanie (${s.mistakes.size})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 18.dp, bottom = 2.dp),
                    )
                }
                itemsIndexed(s.mistakes) { i, id ->
                    ReviewItem(vm.byId.getValue(id), null, groupShape(i, s.mistakes.size))
                }
            }
        }
        if (good) Celebration(vm.shotMode, Modifier.align(Alignment.TopCenter).padding(top = top + 24.dp))
        BottomActions(
            primary = "Ďalšie kolo",
            onPrimary = vm::startRound,
            secondary = "Domov",
            onSecondary = vm::home,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ScoreBadge(score: Int, total: Int, good: Boolean) {
    val c = Uli.colors
    val spin = remember { Animatable(-120f) }
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { spin.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 90f)) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    Box(Modifier.size(200.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = spin.value }
                .clip(Shapes.verySunny)
                .background(if (good) c.amber else MaterialTheme.colorScheme.secondaryContainer),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$score",
                fontFamily = Rounded,
                fontWeight = FontWeight.W900,
                fontSize = 76.sp,
                lineHeight = 76.sp,
                color = if (good) c.onAmber else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "/$total",
                fontFamily = Rounded,
                fontWeight = FontWeight.W700,
                fontSize = 26.sp,
                color = (if (good) c.onAmber else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, container: Color, content: Color, modifier: Modifier) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(value, fontFamily = Rounded, fontWeight = FontWeight.W800, fontSize = 24.sp, lineHeight = 26.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.75f))
        }
    }
}

/** A one-shot burst of Material shapes from behind the score, gone in about a second. */
@Composable
private fun Celebration(frozen: Boolean, modifier: Modifier) {
    val t = remember { Animatable(if (frozen) 0.5f else 0f) }
    LaunchedEffect(Unit) { if (!frozen) t.animateTo(1f, tween(1150, easing = FastOutSlowInEasing)) }
    if (t.value >= 1f) return
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(c.amber, c.right, scheme.tertiary, c.learning, scheme.primary, c.wrong)
    val shapes = listOf(Shapes.sunny, Shapes.clover4, Shapes.cookie6, Shapes.gem, Shapes.flower, Shapes.pill)
    Box(modifier.size(200.dp)) {
        for (i in 0 until 18) {
            // Fan out over the upper arc from the badge's rim, then fall a little.
            val angle = 200.0 + i * (140.0 / 17) + (i % 3) * 4.0
            val dist = 92 + (40 + (i % 4) * 22) * t.value
            val rad = Math.toRadians(angle)
            val x = (cos(rad) * dist).toFloat()
            val y = (sin(rad) * dist).toFloat() + 60f * t.value * t.value
            val size = (12 + (i % 3) * 6).dp
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(size)
                    .graphicsLayer {
                        translationX = x * density
                        translationY = y * density
                        rotationZ = t.value * (180f + i * 25f)
                        alpha = 1f - t.value * t.value
                        val s = 0.4f + 0.8f * (1f - (t.value - 0.5f) * (t.value - 0.5f) * 2f)
                        scaleX = s
                        scaleY = s
                    }
                    .clip(shapes[i % shapes.size])
                    .background(colors[i % colors.size]),
            )
        }
    }
}
