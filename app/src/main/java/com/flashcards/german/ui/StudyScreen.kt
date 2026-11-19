package com.flashcards.german.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*

@Composable
fun StudyScreen(
    cards: List<Card>, title: String, cram: Boolean, repo: Repo, stateOf: (Card) -> CardState?,
    onRate: (Card, Rating) -> Unit, onDone: (reviewed: Int) -> Unit,
) {
    val queue = remember { mutableStateListOf<Card>().apply { addAll(cards) } }
    var reviewed by remember { mutableIntStateOf(0) }
    var germanFront by remember { mutableStateOf(true) }
    var flipped by remember { mutableStateOf(false) }
    val counts = remember { mutableStateMapOf<Rating, Int>() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✕", color = C.Ink, fontSize = 22.sp, modifier = Modifier.clickable { onDone(reviewed) }.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Heading(title, 20)
                Label(if (cram) "Practice · schedule unchanged" else "${queue.size} remaining")
            }
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).border(1.5.dp, C.Ink, RoundedCornerShape(4.dp))
                    .clickable { germanFront = !germanFront; flipped = false }.padding(horizontal = 10.dp, vertical = 6.dp)
            ) { Text(if (germanFront) "DE → EN" else "EN → DE", fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.Ink) }
        }
        Spacer(Modifier.height(14.dp))
        ProgressBar(reviewed.toFloat() / (reviewed + queue.size).coerceAtLeast(1), Modifier.fillMaxWidth())

        val card = queue.firstOrNull()
        if (card == null) {
            SessionSummary(reviewed, counts, Modifier.weight(1f)) { onDone(reviewed) }
            return@Column
        }
        LaunchedEffect(card, flipped, germanFront) {
            if (germanFront && !flipped || !germanFront && flipped) repo.play(card)
        }
        val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(380), label = "flip")
        val showBack = rot > 90f
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp)
                .graphicsLayer { rotationY = rot; cameraDistance = 16f * density }
                .clickable(enabled = !flipped) { flipped = true }
        ) {
            PaperCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().graphicsLayer { rotationY = if (showBack) 180f else 0f }) {
                    // index-card header with red margin rule
                    Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(card.section.takeIf { it.isNotBlank() && it != "vocabulary" } ?: "Karteikarte", C.Red)
                        Spacer(Modifier.weight(1f))
                        val st = stateOf(card)
                        if (st != null && st.lapses > 0) Label("${st.lapses}× forgotten", C.Muted)
                    }
                    Box(Modifier.fillMaxWidth().height(1.5.dp).background(C.Red.copy(alpha = .6f)))
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        if (germanFront) GenderedText(card.de, 32) else Heading(card.en, 30, align = TextAlign.Center)
                        if (showBack) {
                            Box(Modifier.padding(vertical = 18.dp).width(48.dp).height(1.dp).background(C.Rule))
                            if (germanFront) Text(card.en, color = C.Ink, fontFamily = Serif, fontSize = 24.sp, textAlign = TextAlign.Center)
                            else GenderedText(card.de, 30)
                            Spacer(Modifier.height(18.dp))
                            if (card.hasAudio) SpeakerButton(48, filled = false) { repo.play(card) }
                            if (card.note.isNotBlank()) Note(card.note)
                            if (card.exDe.isNotBlank()) Example(card)
                        } else {
                            Spacer(Modifier.height(26.dp))
                            if (germanFront && card.hasAudio) SpeakerButton(48, filled = false) { repo.play(card) }
                            Spacer(Modifier.height(18.dp))
                            Label("Tap to reveal")
                        }
                    }
                }
            }
        }

        if (!flipped) {
            InkButton("Show answer", Modifier.fillMaxWidth()) { flipped = true }
        } else {
            val st = stateOf(card) ?: CardState()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(Rating.Again, C.Red, C.Card), Triple(Rating.Hard, C.Card, C.Ink),
                    Triple(Rating.Good, C.Green, C.Card), Triple(Rating.Easy, C.Ink, C.Card),
                ).forEach { (r, bg, fg) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(if (cram) " " else Srs.label(st, r), C.Muted)
                        Spacer(Modifier.height(4.dp))
                        InkButton(r.name, Modifier.fillMaxWidth(), color = bg, textColor = fg) {
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

/** Example sentence, with the vocabulary word highlighted. No audio by design. */
@Composable
private fun Example(card: Card) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
        Spacer(Modifier.height(14.dp))
        Label("Beispiel", C.Red)
        Spacer(Modifier.height(6.dp))
        Text(card.exDe, color = C.Ink, fontFamily = Serif, fontSize = 18.sp, lineHeight = 25.sp)
        Text(card.exEn, color = C.Muted, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Note(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
        Spacer(Modifier.height(14.dp))
        Label("Notiz", C.Red)
        Spacer(Modifier.height(6.dp))
        Text(text, color = C.Ink, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun SessionSummary(reviewed: Int, counts: Map<Rating, Int>, modifier: Modifier, onDone: () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Label("Session finished", C.Green)
        Heading("$reviewed cards reviewed", 32, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
        PaperCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                listOf(Rating.Again to C.Red, Rating.Hard to C.Ink, Rating.Good to C.Green, Rating.Easy to C.Ink).forEachIndexed { i, (r, c) ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(r.name, color = c, fontFamily = Serif, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text("${counts[r] ?: 0}", color = C.Ink, fontFamily = Mono, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        InkButton("Done", Modifier.fillMaxWidth(), color = C.Green, onClick = onDone)
    }
}
