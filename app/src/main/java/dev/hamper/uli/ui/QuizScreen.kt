package dev.hamper.uli.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hamper.uli.AppViewModel
import dev.hamper.uli.QuizState
import dev.hamper.uli.engine.Code
import dev.hamper.uli.engine.Kind
import dev.hamper.uli.engine.Question
import dev.hamper.uli.ui.theme.QuestionStyle
import dev.hamper.uli.ui.theme.Rounded
import dev.hamper.uli.ui.theme.Uli

@Composable
fun QuizScreen(vm: AppViewModel) {
    // Keep drawing the last state while the screen animates out.
    var last by remember { mutableStateOf(vm.quiz) }
    vm.quiz?.let { last = it }
    val s = last ?: return
    val view = LocalView.current

    fun haptic() {
        val now = vm.quiz ?: return
        if (now.answered) {
            view.performHapticFeedback(if (now.correct) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        RoundTopBar(s, onClose = vm::home)
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = s,
            contentKey = { it.round.index },
            transitionSpec = {
                val spatial = motion.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                (slideInHorizontally(spatial) { it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(spatial) { -it / 3 } + fadeOut(motion.fastEffectsSpec()))
            },
            modifier = Modifier.weight(1f),
            label = "question",
        ) { state ->
            // An outgoing question keeps rendering its own frozen state.
            QuestionBody(
                q = vm.byId.getValue(state.round.queue[state.round.index]),
                state = state,
                isCurrent = state.round.index == s.round.index,
                showSk = vm.progress.showSk,
                onToggleSk = vm::toggleSk,
                onPick = { i -> vm.pick(i); haptic() },
                onSubmit = { vm.submit(); haptic() },
                onNext = vm::next,
            )
        }
    }
}

@Composable
private fun RoundTopBar(s: QuizState, onClose: () -> Unit) {
    val r = s.round
    val segments = r.ids.map { id ->
        when {
            r.firstAttempt[id] == true -> Seg.Right
            r.firstAttempt[id] == false -> Seg.Wrong
            id == r.current -> Seg.Current
            else -> Seg.Upcoming
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Zavrieť kolo") }
        SegmentedProgress(segments, Modifier.weight(1f).padding(horizontal = 6.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "${r.displayNumber}/${r.total}",
            fontFamily = Rounded,
            fontWeight = FontWeight.W700,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuestionBody(
    q: Question,
    state: QuizState,
    isCurrent: Boolean,
    showSk: Boolean,
    onToggleSk: () -> Unit,
    onPick: (Int) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
) {
    val answered = state.answered && isCurrent
    val selected = if (isCurrent) state.selected else emptySet()
    val codeOptions = remember(q.id) { Code.codeOptions(q) }
    val multi = q.kind == Kind.Multi
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(q.topicEnum.title, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                if (state.round.isRetry && isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    Tag("Oprava", Uli.colors.wrongContainer, Uli.colors.onWrongContainer)
                }
                Spacer(Modifier.weight(1f))
                SkToggle(showSk, onToggleSk)
            }
            Spacer(Modifier.height(16.dp))
            // Once answered, the question steps back so the answer and the why fit on screen.
            val size by androidx.compose.animation.core.animateFloatAsState(if (answered) 19f else 23f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "qsize")
            CodeText(
                q.question,
                QuestionStyle.copy(fontSize = size.sp, lineHeight = (size * 1.35f).sp),
                english = true,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AnimatedVisibility(showSk, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                CodeText(
                    q.questionSk,
                    MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (multi) {
                Spacer(Modifier.height(12.dp))
                ChooseCounter(q.choose, selected.size, answered)
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.order.forEachIndexed { pos, i ->
                    val opt = q.options[i]
                    val st = when {
                        !answered -> if (i in selected) OptState.Selected else OptState.Idle
                        opt.correct && i in selected -> OptState.Right
                        opt.correct && !multi -> OptState.Right
                        opt.correct -> OptState.Missed
                        i in selected -> OptState.Wrong
                        else -> OptState.Dim
                    }
                    // After a miss only what matters stays: the right one(s) and your pick.
                    // After a hit the other options stay, dimmed, as context.
                    AnimatedVisibility(
                        visible = st != OptState.Dim || state.correct,
                        enter = fadeIn(),
                        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        OptionCard(
                            text = opt.text,
                            code = i in codeOptions,
                            letter = 'A' + pos,
                            state = st,
                            multi = multi,
                            onClick = if (answered || !isCurrent) null else ({ onPick(i) }),
                            note = opt.note,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Bottom: either the check button (multi) or the feedback panel.
        AnimatedVisibility(
            visible = answered,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FeedbackPanel(
                q = q,
                correct = state.correct,
                wrongNotes = emptyList(),
                last = state.round.index + 1 >= state.round.queue.size,
                maxHeight = screenHeight * 0.6f,
                onNext = onNext,
            )
        }
        if (multi && !answered) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
                BigButton(
                    text = "Skontrolovať",
                    enabled = selected.size == q.choose,
                    onClick = onSubmit,
                )
            }
        } else if (!answered) {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SkToggle(on: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (on) scheme.primary else Color.Transparent,
        contentColor = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(UliIcons.Translate, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("SK", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ChooseCounter(choose: Int, picked: Int, answered: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Vyber $choose",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        repeat(choose) { i ->
            val filled = i < picked
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(if (filled) 14.dp else 12.dp)
                    .clip(if (filled) Shapes.cookie6 else CircleShape)
                    .background(if (filled) scheme.primary else scheme.surfaceContainerHighest),
            )
        }
        if (!answered) {
            Spacer(Modifier.width(4.dp))
            Text("$picked z $choose", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonShapes(RoundedCornerShape(32.dp), RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.fillMaxWidth().height(64.dp),
    ) {
        Text(text, fontFamily = Rounded, fontWeight = FontWeight.W700, fontSize = 20.sp)
        if (trailing) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FeedbackPanel(
    q: Question,
    correct: Boolean,
    wrongNotes: List<String>,
    last: Boolean,
    maxHeight: androidx.compose.ui.unit.Dp,
    onNext: () -> Unit,
) {
    val c = Uli.colors
    val accent = if (correct) c.right else c.wrong
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.heightIn(max = maxHeight).navigationBarsPadding()) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    if (correct) Icons.Filled.Check else Icons.Filled.Close,
                    if (correct) Shapes.verySunny else Shapes.puffy,
                    accent,
                    if (correct) c.onRight else c.onWrong,
                    size = 40.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (correct) "Správne" else "Nesprávne",
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                )
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Explanation(q, wrongNotes)
            }
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)) {
                BigButton(if (last) "Dokončiť kolo" else "Ďalej", onClick = onNext, trailing = true)
            }
        }
    }
}
