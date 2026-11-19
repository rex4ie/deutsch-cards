package com.flashcards.german.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette: printed index cards — warm paper, black ink, one red accent. */
object C {
    val Bg = Color(0xFFF2EDE3)        // paper
    val Card = Color(0xFFFBF8F2)      // index card
    val Ink = Color(0xFF1C1A17)
    val Muted = Color(0xFF746C60)
    val Line = Color(0xFFDCD3C3)
    val Rule = Color(0xFFC9DCE6)      // blue ruling on index cards
    val Red = Color(0xFFC23B2A)
    val RedSoft = Color(0xFFF4DCD6)
    val Green = Color(0xFF2D7A4C)
    val GreenSoft = Color(0xFFD9EBDD)
    val Gold = Color(0xFFCF961A)
    val Der = Color(0xFF2A5F8C)
    val Die = Color(0xFFA9375A)
    val Das = Color(0xFF2D7A4C)
}

val Serif = FontFamily.Serif
val Mono = FontFamily.Monospace

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Ink, background = C.Bg, surface = C.Card,
            onPrimary = C.Card, onBackground = C.Ink, onSurface = C.Ink,
        ),
        typography = Typography(bodyLarge = TextStyle(color = C.Ink, fontSize = 16.sp)),
        content = content,
    )
}

/** Small uppercase monospace label, like a typed catalogue tag. */
@Composable
fun Label(text: String, color: Color = C.Muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = color, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

@Composable
fun Heading(text: String, size: Int = 28, color: Color = C.Ink, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text, color = color, fontFamily = Serif, fontSize = size.sp, fontWeight = FontWeight.Bold, lineHeight = (size * 1.15).sp, modifier = modifier, textAlign = align)
}

/** Paper card with a hard, printed offset shadow. */
@Composable
fun PaperCard(modifier: Modifier = Modifier, shadow: Dp = 4.dp, border: Color = C.Ink, bg: Color = C.Card, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.padding(end = shadow, bottom = shadow)) {
        Box(Modifier.matchParentSize().offset(shadow, shadow).clip(RoundedCornerShape(6.dp)).background(border))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(bg).border(1.5.dp, border, RoundedCornerShape(6.dp)), content = content)
    }
}

/** Button that is pressed "into" its printed shadow. */
@Composable
fun InkButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.Ink,
    textColor: Color = C.Card,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val off by animateDpAsState(if (pressed || !enabled) 0.dp else 4.dp, label = "off")
    Box(modifier.height(58.dp).clickable(src, null, enabled = enabled, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(54.dp).offset(4.dp, 4.dp).clip(RoundedCornerShape(6.dp)).background(if (enabled) C.Ink else C.Line))
        Box(
            Modifier.fillMaxWidth().height(54.dp).offset(4.dp - off, 4.dp - off).clip(RoundedCornerShape(6.dp))
                .background(if (enabled) color else C.Bg).border(1.5.dp, if (enabled) C.Ink else C.Line, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text.uppercase(), color = if (enabled) textColor else C.Muted, fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
        }
    }
}

enum class TileState { Idle, Selected, Correct, Wrong, Faded }

@Composable
fun OptionTile(text: String, state: TileState, modifier: Modifier = Modifier, serif: Boolean = false, onClick: () -> Unit) {
    val (bg, border, fg) = when (state) {
        TileState.Idle -> Triple(C.Card, C.Ink, C.Ink)
        TileState.Selected -> Triple(C.Ink, C.Ink, C.Card)
        TileState.Correct -> Triple(C.GreenSoft, C.Green, C.Green)
        TileState.Wrong -> Triple(C.RedSoft, C.Red, C.Red)
        TileState.Faded -> Triple(C.Bg, C.Line, C.Line)
    }
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(bg)
            .border(1.5.dp, border, RoundedCornerShape(6.dp))
            .clickable(enabled = state != TileState.Faded, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = fg, fontSize = 17.sp, fontFamily = if (serif) Serif else FontFamily.Default, fontWeight = FontWeight.Medium)
    }
}

/** Speaker glyph drawn in ink, no emoji. */
@Composable
fun SpeakerButton(size: Int = 52, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (filled) C.Ink else C.Card)
            .border(1.5.dp, C.Ink, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (filled) C.Card else C.Ink
        Canvas(Modifier.size((size * 0.46).dp)) {
            val w = this.size.width; val h = this.size.height
            val p = Path().apply {
                moveTo(0f, h * .35f); lineTo(w * .25f, h * .35f); lineTo(w * .52f, h * .1f)
                lineTo(w * .52f, h * .9f); lineTo(w * .25f, h * .65f); lineTo(0f, h * .65f); close()
            }
            drawPath(p, fg)
            drawArc(fg, -45f, 90f, false, Offset(w * .45f, h * .3f), androidx.compose.ui.geometry.Size(w * .3f, h * .4f), style = Stroke(w * .08f, cap = StrokeCap.Round))
            drawArc(fg, -50f, 100f, false, Offset(w * .4f, h * .12f), androidx.compose.ui.geometry.Size(w * .58f, h * .76f), style = Stroke(w * .08f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun Tag(text: String, color: Color = C.Ink, filled: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).background(if (filled) color else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text.uppercase(), color = if (filled) C.Card else color, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) }
}

/** Thin segmented progress bar. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Ink) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "p")
    Box(modifier.height(10.dp).clip(RoundedCornerShape(2.dp)).border(1.5.dp, C.Ink, RoundedCornerShape(2.dp)).background(C.Card)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(color))
    }
}

/** German word with its article coloured by gender. */
@Composable
fun GenderedText(de: String, size: Int, align: TextAlign = TextAlign.Center) {
    val color = when {
        de.startsWith("der ") -> C.Der
        de.startsWith("die ") -> C.Die
        de.startsWith("das ") -> C.Das
        else -> C.Ink
    }
    Text(de, color = color, fontFamily = Serif, fontSize = size.sp, fontWeight = FontWeight.Bold, textAlign = align, lineHeight = (size * 1.2).sp)
}

/** Simple ink check mark. */
@Composable
fun CheckMark(color: Color, size: Dp = 18.dp, stroke: Float = 3f) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val p = Path().apply { moveTo(w * .15f, w * .52f); lineTo(w * .4f, w * .76f); lineTo(w * .86f, w * .26f) }
        drawPath(p, color, style = Stroke(stroke.dp.toPx(), cap = StrokeCap.Round))
    }
}
