package dev.hamper.uli.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import dev.hamper.uli.engine.Question
import dev.hamper.uli.ui.theme.Mono
import dev.hamper.uli.ui.theme.Uli

enum class OptState { Idle, Selected, Right, Wrong, Missed, Dim }

private val morphRight by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Sunny) }
private val morphWrong by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Puffy) }
private val morphPick by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie4Sided) }

/** A full-width answer card. The badge morphs into a sun when right and a puff when wrong. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OptionCard(
    text: String,
    code: Boolean,
    letter: Char,
    state: OptState,
    multi: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        when (state) {
            OptState.Idle, OptState.Dim -> scheme.surfaceContainerLow
            OptState.Selected -> scheme.secondaryContainer
            OptState.Right -> c.rightContainer
            OptState.Wrong -> c.wrongContainer
            OptState.Missed -> scheme.surfaceContainerLow
        },
        motion.defaultEffectsSpec(), label = "container",
    )
    val content = when (state) {
        OptState.Right -> c.onRightContainer
        OptState.Wrong -> c.onWrongContainer
        OptState.Selected -> scheme.onSecondaryContainer
        else -> scheme.onSurface
    }
    val corner by animateDpAsState(
        when (state) {
            OptState.Right, OptState.Wrong -> 30.dp
            OptState.Selected -> 26.dp
            else -> 20.dp
        },
        motion.defaultSpatialSpec(), label = "corner",
    )
    val border = when (state) {
        OptState.Missed -> BorderStroke(2.5.dp, c.right)
        OptState.Selected -> BorderStroke(2.dp, scheme.primary)
        else -> null
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // A short pop on the answer: right swells, wrong shakes.
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            OptState.Right -> {
                pop.snapTo(0.97f)
                pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 700f))
            }
            OptState.Wrong -> {
                for (x in listOf(-10f, 9f, -6f, 4f, 0f)) shake.animateTo(x, tween(45))
            }
            else -> {}
        }
    }
    val pressScale by animateFloatAsState(if (pressed) 0.975f else 1f, motion.fastSpatialSpec(), label = "press")

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = pop.value * pressScale
                scaleX = s
                scaleY = s
                translationX = shake.value * density
            }
            .alpha(if (state == OptState.Dim) 0.45f else 1f),
    ) {
        Column {
            Row(
                Modifier.heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OptionBadge(letter, state, multi)
                Spacer(Modifier.width(14.dp))
                if (code) {
                    Text(
                        text,
                        fontFamily = Mono,
                        fontWeight = FontWeight.W500,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            // Why this pick is wrong, right where the eye already is.
            androidx.compose.animation.AnimatedVisibility(
                visible = state == OptState.Wrong && note != null,
                enter = androidx.compose.animation.expandVertically(motion.defaultSpatialSpec()) + androidx.compose.animation.fadeIn(motion.defaultEffectsSpec()),
            ) {
                CodeText(
                    note.orEmpty(),
                    MaterialTheme.typography.bodyMedium,
                    color = content,
                    modifier = Modifier.padding(start = 64.dp, end = 18.dp, bottom = 14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionBadge(letter: Char, state: OptState, multi: Boolean) {
    val c = Uli.colors
    val scheme = MaterialTheme.colorScheme
    val target = when (state) {
        OptState.Right, OptState.Wrong, OptState.Selected -> 1f
        else -> 0f
    }
    val morph = when (state) {
        OptState.Right -> morphRight
        OptState.Wrong -> morphWrong
        else -> morphPick
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 400f)) }
    val spin = (progress.value * 90f)
    val fill = when (state) {
        OptState.Right -> c.right
        OptState.Wrong -> c.wrong
        OptState.Selected -> scheme.primary
        else -> Color.Transparent
    }
    val onFill = when (state) {
        OptState.Right -> c.onRight
        OptState.Wrong -> c.onWrong
        OptState.Selected -> scheme.onPrimary
        else -> scheme.onSurfaceVariant
    }
    val shape = if (multi && state in setOf(OptState.Idle, OptState.Dim, OptState.Missed)) {
        RoundedCornerShape(10.dp)
    } else {
        MorphShape(morph, progress.value.coerceIn(0f, 1f), spin)
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (state in setOf(OptState.Idle, OptState.Dim)) Modifier.border(2.dp, scheme.outline, shape)
                else if (state == OptState.Missed) Modifier.border(2.5.dp, c.right, shape)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            OptState.Right -> Icon(Icons.Filled.Check, "Správne", tint = onFill, modifier = Modifier.size(20.dp))
            OptState.Wrong -> Icon(Icons.Filled.Close, "Nesprávne", tint = onFill, modifier = Modifier.size(20.dp))
            OptState.Missed -> Icon(Icons.Filled.Check, "Správna, nevybraná", tint = c.right, modifier = Modifier.size(20.dp))
            OptState.Selected -> if (multi) {
                Icon(Icons.Filled.Check, null, tint = onFill, modifier = Modifier.size(20.dp))
            } else {
                Text(letter.toString(), color = onFill, fontWeight = FontWeight.W700, fontSize = 15.sp)
            }
            else -> Text(letter.toString(), color = onFill, fontWeight = FontWeight.W700, fontSize = 15.sp)
        }
    }
}

/** Why: the note for a wrong pick, the explanation, the memory hook and the terminal demo. */
@Composable
fun Explanation(q: Question, wrongNotes: List<String>, modifier: Modifier = Modifier) {
    val c = Uli.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (note in wrongNotes) {
            Surface(color = c.wrongContainer.copy(alpha = 0.55f), contentColor = c.onWrongContainer, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Prečo nie tvoja odpoveď", style = MaterialTheme.typography.labelMedium, color = c.wrong)
                    Spacer(Modifier.height(2.dp))
                    CodeText(note, MaterialTheme.typography.bodyMedium)
                }
            }
        }
        CodeText(q.explanation, MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        q.hook?.let { Hook(it) }
        q.demo?.let { Terminal(it) }
        if (q.disputed) {
            Tag(
                "Na skúške vs. v praxi",
                container = Color.Transparent,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = UliIcons.Balance,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

@Composable
fun Hook(text: String) {
    val c = Uli.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (MaterialTheme.colorScheme.primary == c.amber) 0.55f else 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeBadge(UliIcons.Lightbulb, Shapes.flower, c.amber, c.onAmber, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Zapamätaj si", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            CodeText(text, MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Correct answers for study mode, with code options in mono. */
@Composable
fun CorrectAnswers(q: Question, codeOptions: Set<Int>) {
    val c = Uli.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in q.correctIndices.sorted()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.rightContainer).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(Shapes.sunny).background(c.right), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = c.onRight, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                val t = q.options[i].text
                if (i in codeOptions) {
                    Text(t, fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 15.sp, color = c.onRightContainer)
                } else {
                    Text(t, style = MaterialTheme.typography.bodyMedium, color = c.onRightContainer)
                }
            }
        }
    }
}

@Composable
fun WrongAnswers(q: Question, picked: Set<Int>, codeOptions: Set<Int>) {
    val c = Uli.colors
    val wrong = picked.filter { !q.options[it].correct }.sorted()
    val missing = q.correctIndices.isNotEmpty() && picked.isEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in wrong) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.wrongContainer).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(Shapes.puffy).background(c.wrong), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, null, tint = c.onWrong, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                val t = q.options[i].text
                if (i in codeOptions) {
                    Text(t, fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 15.sp, color = c.onWrongContainer)
                } else {
                    Text(t, style = MaterialTheme.typography.bodyMedium, color = c.onWrongContainer)
                }
            }
        }
        if (missing) Text("Bez odpovede", style = MaterialTheme.typography.bodyMedium, color = c.wrong)
    }
}
