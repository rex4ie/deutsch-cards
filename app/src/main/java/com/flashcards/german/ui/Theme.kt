package com.flashcards.german.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF0F1222)
    val Surface = Color(0xFF1A1E33)
    val Surface2 = Color(0xFF242945)
    val Border = Color(0xFF2F355A)
    val Text = Color(0xFFF2F3FA)
    val Muted = Color(0xFF9097B8)
    val Primary = Color(0xFF7C5CFF)
    val PrimaryDark = Color(0xFF5A3FD6)
    val Green = Color(0xFF22C55E)
    val GreenDark = Color(0xFF16934A)
    val Red = Color(0xFFEF4444)
    val RedDark = Color(0xFFB92F2F)
    val Amber = Color(0xFFFFB020)
    val Blue = Color(0xFF38BDF8)
    val Pink = Color(0xFFF472B6)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Primary, background = C.Bg, surface = C.Surface,
            onPrimary = Color.White, onBackground = C.Text, onSurface = C.Text,
        ),
        content = content,
    )
}

/** Chunky "3D" button: a coloured face sitting on a darker base, pressing sinks it. */
@Composable
fun ChunkyButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = C.Primary,
    shade: Color = C.PrimaryDark,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val drop by animateDpAsState(if (pressed || !enabled) 0.dp else 4.dp, label = "drop")
    val face = if (enabled) color else C.Surface2
    val base = if (enabled) shade else C.Surface2
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(base)
            .clickable(src, null, enabled = enabled, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().height(52.dp).offset(y = 4.dp - drop)
                .clip(RoundedCornerShape(16.dp)).background(face),
            contentAlignment = Alignment.Center,
        ) {
            Text(text.uppercase(), color = if (enabled) textColor else C.Muted, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
    }
}

/** Selectable option tile used in multiple-choice games. */
@Composable
fun OptionTile(text: String, state: TileState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (bg, border) = when (state) {
        TileState.Idle -> C.Surface to C.Border
        TileState.Selected -> C.Primary.copy(alpha = .18f) to C.Primary
        TileState.Correct -> C.Green.copy(alpha = .18f) to C.Green
        TileState.Wrong -> C.Red.copy(alpha = .18f) to C.Red
        TileState.Faded -> C.Bg to C.Surface2
    }
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(16.dp))
            .clickable(enabled = state != TileState.Faded, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (state == TileState.Faded) C.Surface2 else C.Text,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
    }
}

enum class TileState { Idle, Selected, Correct, Wrong, Faded }

@Composable
fun SpeakerButton(size: Int = 56, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(C.Blue).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("🔊", fontSize = (size / 2.4).sp) }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .15f)).padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Green) {
    val f by androidx.compose.animation.core.animateFloatAsState(fraction.coerceIn(0f, 1f), label = "p")
    Box(modifier.height(14.dp).clip(RoundedCornerShape(50)).background(C.Surface2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(50)).background(color)) {
            Box(Modifier.padding(horizontal = 6.dp, vertical = 3.dp).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .3f)))
        }
    }
}
