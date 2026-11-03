package dev.hamper.uli.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
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
import dev.hamper.uli.ui.theme.Mono
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
        RoundTopBar(s, onClose = vm::home, animate = !vm.shotMode)
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
                frozen = vm.shotMode,
            )
        }
    }
}

@Composable
private fun RoundTopBar(s: QuizState, onClose: () -> Unit, animate: Boolean) {
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
        SegmentedProgress(segments, Modifier.weight(1f).padding(horizontal = 6.dp), animate = animate)
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


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
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
    frozen: Boolean,
) {
    val answered = state.answered && isCurrent
    val selected = if (isCurrent) state.selected else emptySet()
    val codeOptions = remember(q.id) { Code.codeOptions(q) }
    val multi = q.kind == Kind.Multi
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    // When the question and its options do not fit (long text, SK line, big font scale), the card drops
    // its decoration so the options get the room. Decided once per question, so nothing jumps later.
    var compact by remember(q.id) { mutableStateOf(false) }

    // Before the answer the question card takes the spare height; after it, the feedback sheet does.
    val cardShare by animateFloatAsState(if (answered) 0f else 1f, motion.defaultSpatialSpec(), label = "share")

    // After the answer, start from the top: the question, then the right option and your pick.
    LaunchedEffect(answered) {
        if (answered && scroll.value > 0) scroll.animateScrollTo(0)
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                // The question card takes whatever height is left, so the options always sit at the
                // bottom, in thumb reach. When everything does not fit, the whole screen scrolls.
                FillColumn(
                    minHeight = viewport,
                    gap = 12.dp,
                    topShare = cardShare,
                    fillColor = if (answered) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                    onOverflow = { if (!answered && isCurrent) compact = true },
                    top = {
                        QuestionCard(
                            q = q,
                            answered = answered,
                            showSk = showSk,
                            onToggleSk = onToggleSk,
                            frozen = frozen,
                            compact = compact,
                            keepHintWhenCompact = multi,
                            label = {
                                Tag(q.topicEnum.title, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                if (state.round.isRetry && isCurrent) {
                                    Tag("Oprava", Uli.colors.wrongContainer, Uli.colors.onWrongContainer)
                                }
                            },
                            hint = {
                                when {
                                    multi -> ChooseCounter(q.choose, selected.size, answered)
                                    q.kind == Kind.TrueFalse -> Hint("Pravda, alebo nepravda? Ťukni.")
                                    else -> Hint("Ťukni na odpoveď")
                                }
                            },
                        )
                    },
                    bottom = {
                        Column {
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    // Once answered only what matters stays: the right one(s) and your pick,
                                    // so the why fits under them without scrolling.
                                    AnimatedVisibility(
                                        visible = st != OptState.Dim,
                                        enter = fadeIn(),
                                        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                                    ) {
                                        Entrance(pos, enabled = isCurrent && !answered && !frozen) {
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
                            }
                            AnimatedVisibility(
                                visible = answered,
                                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                FeedbackPanel(q = q, correct = state.correct, streak = state.round.streak, modifier = Modifier.padding(top = 12.dp))
                            }
                            if (!answered) Spacer(Modifier.height(if (multi) 8.dp else 12.dp))
                        }
                    },
                )
            }
            ScrollEdges(
                scroll = scroll,
                color = if (answered) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
                onMore = if (answered) null else ({ scope.launch { scroll.animateScrollTo(scroll.maxValue) } }),
            )
        }
        // Single choice has no bottom button: the options still end above the gesture bar, never under it.
        if (!answered && !multi) Spacer(Modifier.navigationBarsPadding())

        // Pinned bottom: the check button (multi) or, once answered, "Ďalej" on the panel's color.
        AnimatedVisibility(
            visible = answered,
            enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.fastEffectsSpec()),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            ) {
                val last = state.round.index + 1 >= state.round.queue.size
                BigButton(if (last) "Dokončiť kolo" else "Ďalej", onClick = onNext, trailing = true)
            }
        }
        if (multi && !answered) {
            Box(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                BigButton(
                    text = "Skontrolovať",
                    enabled = selected.size == q.choose,
                    onClick = onSubmit,
                )
            }
        }
    }
}

/**
 * A column at least [minHeight] tall. The leftover space goes to [top] while [topShare] is 1 (the question
 * card grows, so [bottom] hugs the lower edge, in thumb reach) and to a strip of [fillColor] under
 * [bottom] while it is 0 (the feedback sheet runs down to the pinned button). Taller content just scrolls,
 * and [onOverflow] hears about it.
 */
@Composable
fun FillColumn(
    minHeight: Dp,
    gap: Dp,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    topShare: Float = 1f,
    fillColor: Color = Color.Transparent,
    onOverflow: (() -> Unit)? = null,
    exactTop: Boolean = false,
) {
    Layout(
        content = {
            Box(propagateMinConstraints = true) { top() }
            Box { bottom() }
            Box(Modifier.background(fillColor))
        },
    ) { m, c ->
        val w = c.maxWidth
        val b = m[1].measure(Constraints(minWidth = w, maxWidth = w))
        val g = gap.roundToPx()
        val natural = m[0].minIntrinsicHeight(w)
        if (natural + g + b.height > minHeight.roundToPx()) onOverflow?.invoke()
        val spare = (minHeight.roundToPx() - b.height - g - natural).coerceAtLeast(0)
        val th = natural + (spare * topShare).toInt()
        // exactTop bounds the top too, so a weighted child inside it can take the spare height.
        val t = m[0].measure(Constraints(minWidth = w, maxWidth = w, minHeight = th, maxHeight = if (exactTop) th else Constraints.Infinity))
        val rest = (minHeight.roundToPx() - t.height - g - b.height).coerceAtLeast(0)
        val f = m[2].measure(Constraints.fixed(w, rest))
        layout(w, t.height + g + b.height + rest) {
            t.place(0, 0)
            b.place(0, t.height + g)
            f.place(0, t.height + g + b.height)
        }
    }
}

/**
 * Scroll affordance for a screen that does not fit: a soft fade at the top once scrolled, a fade at the
 * bottom while there is more, and (before the answer) a pill that jumps to the remaining options.
 */
@Composable
fun BoxScope.ScrollEdges(scroll: ScrollState, color: Color, onMore: (() -> Unit)?) {
    val motion = MaterialTheme.motionScheme
    val top by animateFloatAsState(if (scroll.value > 0) 1f else 0f, motion.fastEffectsSpec(), label = "topEdge")
    val more = scroll.canScrollForward
    val bottom by animateFloatAsState(if (more) 1f else 0f, motion.fastEffectsSpec(), label = "bottomEdge")
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(18.dp)
            .graphicsLayer { alpha = top }
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0f)))),
    )
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
            .graphicsLayer { alpha = bottom }
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0f), color))),
    )
    if (onMore != null) {
        AnimatedVisibility(
            visible = more,
            enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f),
            exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        ) {
            Surface(
                onClick = onMore,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
            ) {
                Row(Modifier.heightIn(min = 44.dp).padding(start = 14.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ďalšie možnosti", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Options rise in one after another when a question arrives. */
@Composable
private fun Entrance(index: Int, enabled: Boolean, content: @Composable () -> Unit) {
    val p = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) {
            delay(70L + 55L * index)
            p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = p.value.coerceIn(0f, 1f)
            translationY = (1f - p.value) * 28.dp.toPx()
        },
    ) { content() }
}

/**
 * The question on a warm card that fills the space above the options, signed with the app's mark:
 * an amber cookie with a blinking terminal cursor that turns slowly. Alive, but nothing extra to read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(
    q: Question,
    answered: Boolean,
    showSk: Boolean,
    onToggleSk: () -> Unit,
    frozen: Boolean,
    label: @Composable () -> Unit,
    hint: @Composable () -> Unit,
    compact: Boolean = false,
    keepHintWhenCompact: Boolean = false,
) {
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.primary == c.amber
    val card = if (dark) scheme.primaryContainer.copy(alpha = 0.30f) else c.amber.copy(alpha = 0.17f)
    // Only the answer animates the size; going compact snaps, so a question never shrinks in front of you.
    val open by animateFloatAsState(if (answered) 0f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "qsize")
    val qSize = 19f + ((if (compact) 20f else 23f) - 19f) * open
    Surface(
        color = card,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 14.dp, top = if (answered) 16.dp else 14.dp, bottom = if (answered) 16.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Once answered the card steps back to just the question.
                AnimatedVisibility(!answered, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(Modifier.padding(bottom = if (compact) 10.dp else 16.dp), verticalAlignment = Alignment.Top) {
                        FlowRow(
                            Modifier.weight(1f).padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) { label() }
                        Spacer(Modifier.width(8.dp))
                        SkToggle(showSk, onToggleSk)
                    }
                }
                CodeText(
                    q.question,
                    QuestionStyle.copy(fontSize = qSize.sp, lineHeight = (qSize * 1.35f).sp),
                    english = true,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(end = 6.dp),
                )
                AnimatedVisibility(showSk && q.hasDistinctSk, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    CodeText(
                        q.questionSk,
                        MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, end = 6.dp),
                    )
                }
            }
            if (!compact || keepHintWhenCompact) {
                AnimatedVisibility(!answered, enter = fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(Modifier.padding(top = if (compact) 10.dp else 18.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f).padding(bottom = 4.dp)) { hint() }
                        if (!compact) PromptMark(frozen)
                    }
                }
            }
        }
    }
}

/** The app's mark: ">_" on an amber cookie. The cookie turns slowly and the cursor blinks. */
@Composable
private fun PromptMark(frozen: Boolean, size: Dp = 68.dp) {
    val c = Uli.colors
    val t = rememberInfiniteTransition(label = "mark")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "spin")
    val blink by t.animateFloat(
        1f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1060; 1f at 0; 1f at 529; 0f at 530; 0f at 1059 }),
        label = "blink",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (frozen) 12f else spin }
                .clip(Shapes.cookie12)
                .background(c.amber),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(">", fontFamily = Mono, fontWeight = FontWeight.W700, fontSize = 22.sp, color = c.onAmber)
            Text(
                "_",
                fontFamily = Mono,
                fontWeight = FontWeight.W700,
                fontSize = 22.sp,
                color = c.onAmber,
                modifier = Modifier.graphicsLayer { alpha = if (frozen) 1f else blink },
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(UliIcons.Touch, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SkToggle(on: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (on) scheme.primary else scheme.surface.copy(alpha = 0.6f),
        contentColor = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(UliIcons.Translate, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            // Never "S / K": the chip keeps its width and the topic tags wrap instead.
            Text("SK", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun ChooseCounter(choose: Int, picked: Int, answered: Boolean) {
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
                    .size(if (filled) 16.dp else 12.dp)
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

/**
 * The why, as a sheet that scrolls with the question. One reading order and one rhythm:
 * verdict, then why, then the tip, then the terminal.
 */
@Composable
private fun FeedbackPanel(q: Question, correct: Boolean, streak: Int, modifier: Modifier = Modifier) {
    val c = Uli.colors
    val accent = if (correct) c.right else c.wrong
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    if (correct) Icons.Filled.Check else Icons.Filled.Close,
                    if (correct) Shapes.verySunny else Shapes.puffy,
                    accent,
                    if (correct) c.onRight else c.onWrong,
                    size = 34.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (correct) "Správne" else "Nesprávne",
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                if (correct && streak >= 3) {
                    Tag("$streak za sebou", c.amber, c.onAmber)
                }
            }
            Spacer(Modifier.height(12.dp))
            Explanation(q, emptyList())
        }
    }
}
