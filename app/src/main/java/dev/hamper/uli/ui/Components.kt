package dev.hamper.uli.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.hamper.uli.engine.Code
import dev.hamper.uli.ui.theme.Mono
import dev.hamper.uli.ui.theme.Uli

/** A shape that is [progress] of the way between two Material shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        val m = Matrix()
        m.scale(size.width, size.height)
        if (rotation != 0f) {
            m.translate(0.5f, 0.5f)
            m.rotateZ(rotation)
            m.translate(-0.5f, -0.5f)
        }
        path.transform(m)
        return Outline.Generic(path)
    }
}

class PolygonShape(polygon: RoundedPolygon, rotation: Float = 0f) : Shape {
    private val inner = MorphShape(Morph(polygon, polygon), 0f, rotation)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        inner.createOutline(size, layoutDirection, density)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object Shapes {
    val cookie12 by lazy { PolygonShape(MaterialShapes.Cookie12Sided) }
    val cookie9 by lazy { PolygonShape(MaterialShapes.Cookie9Sided) }
    val cookie6 by lazy { PolygonShape(MaterialShapes.Cookie6Sided) }
    val sunny by lazy { PolygonShape(MaterialShapes.Sunny) }
    val verySunny by lazy { PolygonShape(MaterialShapes.VerySunny) }
    val clover4 by lazy { PolygonShape(MaterialShapes.Clover4Leaf) }
    val clover8 by lazy { PolygonShape(MaterialShapes.Clover8Leaf) }
    val pill by lazy { PolygonShape(MaterialShapes.Pill) }
    val gem by lazy { PolygonShape(MaterialShapes.Gem) }
    val puffy by lazy { PolygonShape(MaterialShapes.Puffy) }
    val softBurst by lazy { PolygonShape(MaterialShapes.SoftBurst) }
    val flower by lazy { PolygonShape(MaterialShapes.Flower) }
    val ghostish by lazy { PolygonShape(MaterialShapes.Ghostish) }
    val arch by lazy { PolygonShape(MaterialShapes.Arch) }
    val pentagon by lazy { PolygonShape(MaterialShapes.Pentagon) }
}

/** Prose with commands, paths and flags set in mono. */
@Composable
fun rememberCodeText(text: String, english: Boolean): AnnotatedString {
    val bg = Uli.colors.codeBackground
    return remember(text, english, bg) {
        val spans = Code.spans(text, english)
        buildAnnotatedString {
            var pos = 0
            for (r in spans) {
                if (r.first > pos) append(text.substring(pos, r.first))
                withStyle(SpanStyle(fontFamily = Mono, fontSize = 0.92.em(), background = bg, fontWeight = FontWeight.W500)) {
                    append(text.substring(r.first, r.last + 1).replace(' ', '\u00A0'))
                }
                pos = r.last + 1
            }
            if (pos < text.length) append(text.substring(pos))
        }
    }
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

@Composable
fun CodeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    english: Boolean = false,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        rememberCodeText(text, english),
        style = style,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The demo as a little terminal: prompts in amber, commands bright, output dimmed. */
@Composable
fun Terminal(demo: String, modifier: Modifier = Modifier) {
    val c = Uli.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.terminal)
            .padding(top = 10.dp, bottom = 14.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.terminalPrompt))
            Spacer(Modifier.width(8.dp))
            Text("bob@uli: ~", fontFamily = Mono, fontSize = 11.sp, color = c.terminalDim)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            for (line in demo.lines()) {
                val text = buildAnnotatedString {
                    if (line.startsWith("$ ")) {
                        withStyle(SpanStyle(color = c.terminalPrompt, fontWeight = FontWeight.W700)) { append("$ ") }
                        withStyle(SpanStyle(color = c.terminalText, fontWeight = FontWeight.W500)) { append(line.removePrefix("$ ")) }
                    } else if (line == "$") {
                        withStyle(SpanStyle(color = c.terminalPrompt, fontWeight = FontWeight.W700)) { append("$") }
                    } else {
                        withStyle(SpanStyle(color = c.terminalDim)) { append(line.replace("\t", "    ")) }
                    }
                }
                Text(text, fontFamily = Mono, fontSize = 13.sp, lineHeight = 20.sp, softWrap = false)
            }
        }
    }
}

/** A badge: an icon inside a Material shape. */
@Composable
fun ShapeBadge(
    icon: ImageVector,
    shape: Shape,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size(iconSize))
    }
}

enum class Seg { Right, Wrong, Current, Upcoming }

/** Round progress: one pill per card, colored by the first attempt. */
@Composable
fun SegmentedProgress(segments: List<Seg>, modifier: Modifier = Modifier) {
    val c = Uli.colors
    Row(modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (s in segments) {
            val color by animateColorAsState(
                when (s) {
                    Seg.Right -> c.right
                    Seg.Wrong -> c.wrong
                    Seg.Current -> MaterialTheme.colorScheme.primary
                    Seg.Upcoming -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "seg",
            )
            val h by animateDpAsState(if (s == Seg.Current) 10.dp else 6.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "segh")
            Box(
                Modifier
                    .weight(if (s == Seg.Current) 1.6f else 1f)
                    .align(Alignment.CenterVertically)
                    .height(h)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Small uppercase-free label pill. */
@Composable
fun Tag(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Grouped list item corners: big outside, tight between neighbours. */
fun groupShape(index: Int, count: Int, outer: androidx.compose.ui.unit.Dp = 24.dp, inner: androidx.compose.ui.unit.Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
fun Modifier.pressScale(pressed: Boolean): Modifier {
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    return this.scale(s)
}

@Composable
fun OutlinedBox(border: Color, shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.border(BorderStroke(2.dp, border), shape).heightIn(min = 0.dp)) { content() }
}
