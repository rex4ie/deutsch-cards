package com.flashcards.german.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*

@Composable
fun StudyScreen(
    cards: List<Card>, title: String, cram: Boolean, repo: Repo, progress: Progress,
    onRate: (Card, Rating) -> Unit, onDone: (reviewed: Int) -> Unit,
) {
    val queue = remember { mutableStateListOf<Card>().apply { addAll(cards) } }
    var reviewed by remember { mutableIntStateOf(0) }
    var germanFront by remember { mutableStateOf(true) }
    var flipped by remember { mutableStateOf(false) }
    val counts = remember { mutableStateMapOf<Rating, Int>() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✕", color = C.Muted, fontSize = 24.sp, modifier = Modifier.clickable { onDone(reviewed) }.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(if (cram) "Practice mode · doesn't affect schedule" else "${queue.size} left", color = C.Muted, fontSize = 13.sp)
            }
            Pill(if (germanFront) "DE → EN" else "EN → DE", C.Primary)
            Spacer(Modifier.width(4.dp))
            Text("⇄", color = C.Text, fontSize = 22.sp, modifier = Modifier.clickable { germanFront = !germanFront; flipped = false }.padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(reviewed.toFloat() / (reviewed + queue.size).coerceAtLeast(1), Modifier.fillMaxWidth(), C.Primary)

        val card = queue.firstOrNull()
        if (card == null) {
            SessionSummary(reviewed, counts, Modifier.weight(1f)) { onDone(reviewed) }
            return@Column
        }
        LaunchedEffect(card, flipped, germanFront) {
            if (germanFront && !flipped || !germanFront && flipped) repo.play(card)
        }
        val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(350), label = "flip")
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp)
                .graphicsLayer { rotationY = rot; cameraDistance = 14f * density }
                .clip(RoundedCornerShape(28.dp)).background(C.Surface)
                .border(2.dp, C.Border, RoundedCornerShape(28.dp))
                .clickable(enabled = !flipped) { flipped = true },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp).graphicsLayer { rotationY = if (rot > 90f) 180f else 0f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val showBack = rot > 90f
                val state = progress.cards[card.id]
                if (state != null && state.reps == 0 && state.lapses > 0) Pill("relearning", C.Red)
                Spacer(Modifier.height(8.dp))
                if (germanFront) GenderedText(card.de, 32) else Text(card.en, color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                if (showBack) {
                    Box(Modifier.padding(vertical = 22.dp).fillMaxWidth(.6f).height(2.dp).background(C.Border))
                    if (germanFront) Text(card.en, color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    else GenderedText(card.de, 30)
                    if (card.section.isNotBlank() && card.section != "vocabulary") {
                        Spacer(Modifier.height(12.dp)); Pill(card.section, C.Muted)
                    }
                } else {
                    Spacer(Modifier.height(28.dp))
                    Text("Tap to reveal", color = C.Muted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(24.dp))
                if (germanFront || showBack) SpeakerButton(52) { repo.play(card) }
            }
        }

        if (!flipped) {
            ChunkyButton("Show answer", Modifier.fillMaxWidth()) { flipped = true }
        } else {
            val st = progress.cards[card.id] ?: CardState()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(Rating.Again, C.Red, C.RedDark), Triple(Rating.Hard, C.Amber, Color(0xFFC98400)),
                    Triple(Rating.Good, C.Green, C.GreenDark), Triple(Rating.Easy, C.Blue, Color(0xFF1F8FC0)),
                ).forEach { (r, c, d) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (cram) "" else Srs.label(st, r), color = C.Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        ChunkyButton(r.name, Modifier.fillMaxWidth(), color = c, shade = d) {
                            onRate(card, r)
                            counts[r] = (counts[r] ?: 0) + 1
                            queue.removeAt(0)
                            if (r == Rating.Again) queue.add(minOf(queue.size, 3), card) else reviewed++
                            flipped = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSummary(reviewed: Int, counts: Map<Rating, Int>, modifier: Modifier, onDone: () -> Unit) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("✨", fontSize = 80.sp)
        Text("Session complete", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("$reviewed cards reviewed", color = C.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Again ${counts[Rating.Again] ?: 0}", C.Red)
            Pill("Hard ${counts[Rating.Hard] ?: 0}", C.Amber)
            Pill("Good ${counts[Rating.Good] ?: 0}", C.Green)
            Pill("Easy ${counts[Rating.Easy] ?: 0}", C.Blue)
        }
        Spacer(Modifier.height(40.dp))
        ChunkyButton("Done", Modifier.fillMaxWidth(), color = C.Green, shade = C.GreenDark, onClick = onDone)
    }
}
