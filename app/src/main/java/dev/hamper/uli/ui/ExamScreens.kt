package dev.hamper.uli.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.ExamState
import dev.hamper.uli.engine.Code
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.Kind
import dev.hamper.uli.engine.Question
import dev.hamper.uli.ui.theme.Mono
import dev.hamper.uli.ui.theme.QuestionStyle
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli
import kotlinx.coroutines.delay
import java.util.Locale

fun formatPoints(p: Double): String = String.format(Locale.ROOT, "%.1f", p).replace('.', ',').removeSuffix(",0")

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

@Composable
fun ExamScreen(vm: AppViewModel) {
    var last by remember { mutableStateOf(vm.exam) }
    vm.exam?.let { last = it }
    val s = last ?: return
    var confirmQuit by remember { mutableStateOf(false) }
    BackHandler { confirmQuit = true }

    var now by remember { mutableLongStateOf(vm.clock()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = vm.clock()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmQuit = true }) { Icon(Icons.Filled.Close, contentDescription = "Ukončiť skúšku") }
            ExamBar((s.exam.index).toFloat() / s.exam.ids.size, Modifier.weight(1f).padding(horizontal = 6.dp))
            Spacer(Modifier.width(12.dp))
            Icon(UliIcons.Timer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                formatDuration(now - s.exam.startedAt),
                fontFamily = Mono,
                fontWeight = FontWeight.W500,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = s,
            contentKey = { it.exam.index },
            transitionSpec = {
                val spatial = motion.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                (slideInHorizontally(spatial) { it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(spatial) { -it / 3 } + fadeOut(motion.fastEffectsSpec()))
            },
            modifier = Modifier.weight(1f),
            label = "exam",
        ) { st ->
            val id = st.exam.ids.getOrNull(st.exam.index) ?: return@AnimatedContent
            ExamQuestion(
                q = vm.byId.getValue(id),
                st = st,
                number = st.exam.index + 1,
                total = st.exam.ids.size,
                showSk = vm.progress.showSk,
                onToggleSk = vm::toggleSk,
                onPick = vm::examPick,
                onNext = vm::examNext,
            )
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Ukončiť skúšku?") },
            text = { Text("Tento pokus sa nezapočíta. Na skúške sa tiež nedá vrátiť.") },
            confirmButton = { TextButton(onClick = { confirmQuit = false; vm.home() }) { Text("Ukončiť") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Pokračovať") } },
        )
    }
}

@Composable
private fun ExamBar(value: Float, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(fill, size = Size((size.width * value).coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

@Composable
private fun ExamQuestion(
    q: Question,
    st: ExamState,
    number: Int,
    total: Int,
    showSk: Boolean,
    onToggleSk: () -> Unit,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val codeOptions = remember(q.id) { Code.codeOptions(q) }
    val multi = q.kind == Kind.Multi
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Otázka $number z $total",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                SkToggleSmall(showSk, onToggleSk)
            }
            Spacer(Modifier.height(14.dp))
            CodeText(q.question, QuestionStyle, english = true, color = MaterialTheme.colorScheme.onSurface)
            AnimatedVisibility(showSk, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                CodeText(q.questionSk, MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            if (multi) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Vyber ${q.choose}: ${st.selected.size} z ${q.choose}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                st.order.forEachIndexed { pos, i ->
                    OptionCard(
                        text = q.options[i].text,
                        code = i in codeOptions,
                        letter = 'A' + pos,
                        state = if (i in st.selected) OptState.Selected else OptState.Idle,
                        multi = multi,
                        onClick = { onPick(i) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
            BigButton(
                text = if (number == total) "Odovzdať" else "Ďalej",
                enabled = st.selected.size == q.choose,
                onClick = onNext,
                trailing = number != total,
            )
        }
    }
}

@Composable
private fun SkToggleSmall(on: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (on) scheme.primary else Color.Transparent,
        contentColor = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(UliIcons.Translate, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("SK", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ExamEndScreen(vm: AppViewModel) {
    val summary = vm.examSummary ?: return
    val res = summary.result
    val c = Uli.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val accent = if (res.passed) c.right else c.wrong
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 16.dp, bottom = bottom + 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(
                    color = if (res.passed) c.rightContainer else c.wrongContainer,
                    contentColor = if (res.passed) c.onRightContainer else c.onWrongContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(
                            "Skúška nanečisto",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (res.passed) c.onRightContainer else c.onWrongContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (res.passed) "Prešiel si" else "Ešte nie",
                                style = MaterialTheme.typography.displaySmall,
                                color = if (res.passed) c.onRightContainer else c.onWrongContainer,
                                modifier = Modifier.weight(1f),
                            )
                            ShapeBadge(
                                if (res.passed) Icons.Filled.Check else Icons.Filled.Close,
                                if (res.passed) Shapes.verySunny else Shapes.puffy,
                                accent,
                                if (res.passed) c.onRight else c.onWrong,
                                size = 56.dp,
                                iconSize = 28.dp,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                formatPoints(res.points),
                                fontFamily = Rounded,
                                fontWeight = FontWeight.W800,
                                fontSize = 64.sp,
                                lineHeight = 64.sp,
                                color = if (res.passed) c.onRightContainer else c.onWrongContainer,
                            )
                            Text(
                                " / 80 b",
                                fontFamily = Rounded,
                                fontWeight = FontWeight.W700,
                                fontSize = 22.sp,
                                color = (if (res.passed) c.onRightContainer else c.onWrongContainer).copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        PointsBar(res.points, accent, if (res.passed) c.onRightContainer else c.onWrongContainer)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Stat("${res.correct} z ${res.total}", "správne")
                            Stat(formatDuration(summary.durationMs), "čas")
                            Stat("64 b", "hranica")
                        }
                    }
                }
            }
            if (res.wrong.isNotEmpty()) {
                item {
                    Text(
                        "Kde si chyboval (${res.wrong.size})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp),
                    )
                }
                itemsIndexed(res.wrong) { i, id ->
                    ReviewItem(vm.byId.getValue(id), summary.exam.answers[id].orEmpty(), groupShape(i, res.wrong.size))
                }
            }
        }
        BottomActions(primary = "Hotovo", onPrimary = vm::home, secondary = "Nová skúška", onSecondary = vm::startExam, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, fontFamily = Rounded, fontWeight = FontWeight.W700, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColorAlpha())
    }
}

@Composable
private fun LocalContentColorAlpha(): Color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.7f)

@Composable
private fun PointsBar(points: Double, fill: Color, ink: Color) {
    val v = (points / Exam.MAX_POINTS).toFloat()
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val h = 12.dp.toPx()
        val y = (size.height - h) / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(ink.copy(alpha = 0.12f), Offset(0f, y), Size(size.width, h), r)
        drawRoundRect(fill, Offset(0f, y), Size((size.width * v).coerceAtLeast(h), h), r)
        val x = size.width * 0.8f
        drawRoundRect(ink, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
    }
}

/** A missed question: what you picked, what is right, and why (tap to open). */
@Composable
fun ReviewItem(q: Question, picked: Set<Int>?, shape: androidx.compose.ui.graphics.Shape, startExpanded: Boolean = false) {
    var open by remember { mutableStateOf(startExpanded) }
    val codeOptions = remember(q.id) { Code.codeOptions(q) }
    Surface(
        onClick = { open = !open },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                CodeText(
                    q.question,
                    MaterialTheme.typography.titleMedium,
                    english = true,
                    modifier = Modifier.weight(1f),
                    maxLines = if (open) Int.MAX_VALUE else 2,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Zbaliť" else "Rozbaliť",
                    modifier = Modifier.rotate(if (open) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) {
                Spacer(Modifier.height(12.dp))
                if (picked != null) {
                    WrongAnswers(q, picked, codeOptions)
                    Spacer(Modifier.height(6.dp))
                }
                CorrectAnswers(q, codeOptions)
                Spacer(Modifier.height(14.dp))
                val notes = picked.orEmpty().filter { !q.options[it].correct }.mapNotNull { q.options[it].note }
                Explanation(q, notes)
            }
        }
    }
}

@Composable
fun BottomActions(primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.3f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    1f to MaterialTheme.colorScheme.background,
                ),
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigButton(primary, onClick = onPrimary, trailing = true)
        TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 2.dp)) {
            Text(secondary, style = MaterialTheme.typography.titleSmall)
        }
    }
}
