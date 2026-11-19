package com.flashcards.german.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Schwarzwald" palette: deep forest green on warm cream, with a dark variant
 * that follows the system setting. Red is reserved for mistakes and due counts.
 */
object C {
    internal var dark = false
    private fun pick(light: Long, night: Long) = Color(if (dark) night else light)

    val Bg get() = pick(0xFFF4F1E6, 0xFF111813)          // page
    val Card get() = pick(0xFFFFFDF6, 0xFF1A231D)        // raised surface
    val Sunk get() = pick(0xFFEAE6D7, 0xFF0C110E)        // recessed surface, tracks
    val Ink get() = pick(0xFF1F2A22, 0xFFE9E6DA)         // primary text
    val Muted get() = pick(0xFF6B7366, 0xFF98A296)       // secondary text
    val Line get() = pick(0xFFDDD8C8, 0xFF2B372F)        // hairlines, locked
    val Rule get() = pick(0xFFE4E0D2, 0xFF27322B)        // dividers inside cards
    val Accent get() = pick(0xFF2F5D46, 0xFF8CC5A6)      // forest green
    val AccentSoft get() = pick(0xFFDCE8E0, 0xFF1F3A2C)
    val OnAccent get() = pick(0xFFF4F1E6, 0xFF0F1A13)
    val Moss get() = pick(0xFF8AA26B, 0xFF9DB77E)        // secondary green for trail
    val Gold get() = pick(0xFFB7832A, 0xFFE0B25A)        // streak, XP
    val Red get() = pick(0xFFB5483B, 0xFFE58A7C)         // wrong, due
    val RedSoft get() = pick(0xFFF5DDD8, 0xFF3A201C)
    val Green get() = pick(0xFF2F7D4F, 0xFF7FD39C)       // correct
    val GreenSoft get() = pick(0xFFDCEEDF, 0xFF183224)
    val Der get() = pick(0xFF2C5F8A, 0xFF8FB8E0)
    val Die get() = pick(0xFFA23A5A, 0xFFE59AB3)
    val Das get() = pick(0xFF2F7D4F, 0xFF7FD39C)
}

val Serif = FontFamily.Serif
val Mono = FontFamily.Monospace

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    C.dark = dark
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = C.Accent, background = C.Bg, surface = C.Card, onPrimary = C.OnAccent, onBackground = C.Ink, onSurface = C.Ink)
        else lightColorScheme(primary = C.Accent, background = C.Bg, surface = C.Card, onPrimary = C.OnAccent, onBackground = C.Ink, onSurface = C.Ink),
        typography = Typography(bodyLarge = TextStyle(color = C.Ink, fontSize = 16.sp)),
        content = content,
    )
}

/** Small uppercase label with generous tracking. */
@Composable
fun Label(text: String, color: Color = C.Muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = color, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun Heading(text: String, size: Int = 28, color: Color = C.Ink, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text, color = color, fontFamily = Serif, fontSize = size.sp, fontWeight = FontWeight.Bold, lineHeight = (size * 1.15).sp, modifier = modifier, textAlign = align)
}

/** Soft raised card: cream surface, hairline border, generous radius. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier, shadow: Dp = 0.dp, border: Color = C.Line, bg: Color = C.Card,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(bg).border(1.dp, border, RoundedCornerShape(18.dp)),
        content = content,
    )
}

/** Rounded filled button that dips slightly when pressed. */
@Composable
fun InkButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.Accent,
    textColor: Color = C.OnAccent,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed || !enabled) 0.dp else 3.dp, label = "lift")
    val outlined = color == C.Card || color == C.Bg
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.height(57.dp).clickable(src, null, enabled = enabled, onClick = onClick)) {
        // darker base gives a subtle tactile edge
        Box(Modifier.fillMaxWidth().height(54.dp).offset(y = 3.dp).clip(shape).background(if (!enabled) C.Line else if (outlined) C.Line else color.copy(alpha = .55f).compositeOver(C.Ink)))
        Box(
            Modifier.fillMaxWidth().height(54.dp).offset(y = 3.dp - lift).clip(shape)
                .background(if (enabled) color else C.Sunk)
                .then(if (outlined) Modifier.border(1.dp, C.Line, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = if (enabled) textColor else C.Muted, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.3.sp)
        }
    }
}

private fun Color.compositeOver(under: Color): Color {
    val a = alpha
    return Color(red * a + under.red * (1 - a), green * a + under.green * (1 - a), blue * a + under.blue * (1 - a), 1f)
}

enum class TileState { Idle, Selected, Correct, Wrong, Faded }

@Composable
fun OptionTile(text: String, state: TileState, modifier: Modifier = Modifier, serif: Boolean = false, onClick: () -> Unit) {
    val (bg, border, fg) = when (state) {
        TileState.Idle -> Triple(C.Card, C.Line, C.Ink)
        TileState.Selected -> Triple(C.AccentSoft, C.Accent, C.Accent)
        TileState.Correct -> Triple(C.GreenSoft, C.Green, C.Green)
        TileState.Wrong -> Triple(C.RedSoft, C.Red, C.Red)
        TileState.Faded -> Triple(C.Bg, C.Line, C.Line)
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape).background(bg)
            .border(if (state == TileState.Idle || state == TileState.Faded) 1.dp else 2.dp, border, shape)
            .clickable(enabled = state != TileState.Faded, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = fg, fontSize = 17.sp, fontFamily = if (serif) Serif else FontFamily.Default, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SpeakerButton(size: Int = 52, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (filled) C.Accent else C.AccentSoft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Play audio", tint = if (filled) C.OnAccent else C.Accent, modifier = Modifier.size((size * 0.5).dp))
    }
}

/** Plain tappable icon (close, back, swap…). */
@Composable
fun IconBtn(icon: ImageVector, label: String, tint: Color = C.Ink, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp)) }
}

@Composable
fun Tag(text: String, color: Color = C.Ink, filled: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) color else color.copy(alpha = .12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, color = if (filled) C.OnAccent else color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Accent) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "p")
    Box(modifier.height(12.dp).clip(RoundedCornerShape(50)).background(C.Sunk)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(50)).background(color))
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

@Composable
fun CheckMark(color: Color, size: Dp = 18.dp, @Suppress("UNUSED_PARAMETER") stroke: Float = 3f) {
    Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(size))
}
