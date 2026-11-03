package dev.hamper.uli.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.Topic
import dev.hamper.uli.ui.theme.Mono
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: AppViewModel) {
    val progress = vm.progress
    val ids = vm.ids
    val readiness = Srs.readiness(ids, progress)
    val mastered = Srs.masteredCount(ids, progress)
    val learning = ids.count { progress.card(it).let { c -> !c.isNew && !c.isMastered } }
    val mistakes = Srs.mistakes(ids, progress, size = ids.size).size
    val preview = vm.nextRoundPreview()
    val now = vm.clock()
    val dueInRound = preview.count { Srs.isDue(progress.card(it), now) }
    val newInRound = preview.count { progress.card(it).isNew }
    var confirmReset by remember { mutableStateOf(false) }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottom + 132.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(onReset = { confirmReset = true }) }
            item { ReadinessCard(readiness, mastered, learning, ids.size) }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(
                        title = "Skúška nanečisto",
                        subtitle = "50 otázok",
                        icon = UliIcons.Timer,
                        shape = Shapes.clover4,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = vm::startExam,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        title = "Chyby",
                        subtitle = if (mistakes == 0) "Zatiaľ žiadne" else "$mistakes ${questionsWord(mistakes)}",
                        icon = UliIcons.Replay,
                        shape = Shapes.softBurst,
                        container = Uli.colors.wrongContainer,
                        content = Uli.colors.onWrongContainer,
                        onClick = vm::startMistakes,
                        enabled = mistakes > 0,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        title = "Všetky otázky",
                        subtitle = "${ids.size} na čítanie",
                        icon = UliIcons.Stack,
                        shape = Shapes.cookie6,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { vm.openBrowse() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Text(
                    "Témy",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 2.dp),
                )
            }
            item {
                val topics = Topic.entries
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    topics.forEachIndexed { i, t ->
                        val tIds = vm.questions.filter { it.topicEnum == t }.map { it.id }
                        val m = tIds.count { progress.card(it).isMastered }
                        val l = tIds.count { progress.card(it).let { c -> !c.isNew && !c.isMastered } }
                        TopicRow(t.title, m, l, tIds.size, groupShape(i, topics.size), onClick = { vm.openBrowse(t) })
                    }
                }
            }
        }

        // The one big action lives in the thumb zone.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                )
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        ) {
            ContinueButton(
                subtitle = roundSubtitle(dueInRound, newInRound, preview.size),
                onClick = vm::startRound,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Začať odznova?") },
            text = { Text("Vymaže sa celý tvoj postup: zvládnuté otázky, chyby aj odhad. Nedá sa to vrátiť.") },
            confirmButton = {
                TextButton(onClick = { vm.resetProgress(); confirmReset = false }) {
                    Text("Vymazať postup", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Zrušiť") } },
        )
    }
}

fun questionsWord(n: Int) = when {
    n == 1 -> "otázka"
    n in 2..4 -> "otázky"
    else -> "otázok"
}

private fun roundSubtitle(due: Int, new: Int, size: Int): String = when {
    due > 0 && new > 0 -> "$size otázok: $due na zopakovanie, $new ${if (new in 2..4) "nové" else "nových"}"
    due > 0 -> "$size otázok na zopakovanie"
    new > 0 -> "$size otázok, z toho $new ${if (new in 2..4) "nové" else "nových"}"
    else -> "$size otázok na precvičenie"
}.replace("1 nových", "1 nová")

@Composable
private fun Header(onReset: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Uli.colors.amber),
            contentAlignment = Alignment.Center,
        ) {
            Text(">_", fontFamily = Mono, fontWeight = FontWeight.W700, fontSize = 17.sp, color = Uli.colors.onAmber)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ULI", style = MaterialTheme.typography.titleLarge, lineHeight = 22.sp)
            Text(
                "Úvod do Linuxu, zápočtový test",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Viac") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Vymazať postup") }, onClick = { menu = false; onReset() })
            }
        }
    }
}

@Composable
private fun ReadinessCard(readiness: Double, mastered: Int, learning: Int, total: Int) {
    val pct = (readiness * 100).roundToInt()
    val above = pct >= 80
    val c = Uli.colors
    val spin = remember { Animatable(-40f) }
    LaunchedEffect(Unit) { spin.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 120f)) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = spin.value }
                            .clip(Shapes.cookie12)
                            .background(if (above) c.right else c.amber),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$pct",
                            fontFamily = Rounded,
                            fontWeight = FontWeight.W800,
                            fontSize = 50.sp,
                            lineHeight = 50.sp,
                            color = if (above) c.onRight else c.onAmber,
                        )
                        Text(
                            "%",
                            fontFamily = Rounded,
                            fontWeight = FontWeight.W700,
                            fontSize = 22.sp,
                            color = if (above) c.onRight else c.onAmber,
                            modifier = Modifier.padding(bottom = 6.dp, start = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("Odhad na skúške", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (above) "Si nad hranicou 80 %. Drž to." else "Do hranice 80 % ti chýba ${80 - pct} %.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            ExamMeter(readiness.toFloat())
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Zvládnuté", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "$mastered",
                    fontFamily = Rounded,
                    fontWeight = FontWeight.W800,
                    fontSize = 28.sp,
                    lineHeight = 28.sp,
                )
                Text(
                    " / $total",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            MasteryBar(mastered, learning, total)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(MaterialTheme.colorScheme.primary, "$mastered zvládnuté")
                Legend(c.learning, "$learning učíš sa")
                Legend(MaterialTheme.colorScheme.surfaceContainerHighest, "${total - mastered - learning} nové")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Estimate on a 0 to 100 track with the pass line at 80 %. */
@Composable
private fun ExamMeter(value: Float) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 90f)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val passZone = Uli.colors.right.copy(alpha = 0.18f)
    val fill = if (value >= 0.8f) Uli.colors.right else MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurface
    Column {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "hranica 80 %",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(0.8f).padding(end = 6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val h = 14.dp.toPx()
            val y = (size.height - h) / 2
            val r = CornerRadius(h / 2, h / 2)
            drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
            drawRoundRect(passZone, Offset(size.width * 0.8f, y), Size(size.width * 0.2f, h), r)
            val w = (size.width * anim.value).coerceAtLeast(h)
            drawRoundRect(fill, Offset(0f, y), Size(w, h), r)
            val x = size.width * 0.8f
            drawRoundRect(line, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
private fun MasteryBar(mastered: Int, learning: Int, total: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val learn = Uli.colors.learning
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val gap = 3.dp.toPx()
        val wm = size.width * mastered / total
        val wl = size.width * learning / total
        if (learning > 0) drawRoundRect(learn, Offset((wm + if (mastered > 0) gap else 0f), 0f), Size((wl - gap).coerceAtLeast(size.height), size.height), r)
        if (mastered > 0) drawRoundRect(primary, Offset.Zero, Size(wm.coerceAtLeast(size.height), size.height), r)
    }
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(Modifier.padding(14.dp).alpha(if (enabled) 1f else 0.5f)) {
            ShapeBadge(icon, shape, container, content, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, lineHeight = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TopicRow(title: String, mastered: Int, learning: Int, total: Int, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = 0.dp),
    ) {
        Column(Modifier.heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "$mastered/$total",
                    fontFamily = Rounded,
                    fontWeight = FontWeight.W700,
                    fontSize = 15.sp,
                    color = if (mastered == total) Uli.colors.right else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            MasteryBar(mastered, learning, total)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContinueButton(subtitle: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shapes = ButtonShapes(RoundedCornerShape(36.dp), RoundedCornerShape(18.dp)),
        colors = ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(start = 28.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        modifier = Modifier.fillMaxWidth().height(84.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Pokračovať", fontFamily = Rounded, fontWeight = FontWeight.W800, fontSize = 24.sp, lineHeight = 28.sp)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
        }
        Box(
            Modifier.size(60.dp).clip(Shapes.cookie9).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
    }
}
