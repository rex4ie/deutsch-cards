package com.flashcards.german.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*

/** One of the learner's personal Anki-style decks (Class or Finds), each with its own stats. */
@Composable
fun MyLessonsScreen(repo: Repo, p: Progress, deck: Deck, onStudy: (List<Card>, String, Boolean) -> Unit) {
    val states = p.deck(deck)
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    fun sync() {
        if (syncing) return
        syncing = true
        scope.launch {
            status = runCatching { repo.refresh(deck) }.fold(
                { "Up to date · $it lessons" }, { "Offline · showing saved lessons" },
            )
            syncing = false
        }
    }
    LaunchedEffect(deck) { status = null; sync() }
    val now = System.currentTimeMillis()
    val lessons = repo.lessonsOf(deck)
    val all = repo.cardsOf(deck)
    val fresh = all.filter { it.id !in states }
    val due = all.filter { states[it.id]?.let { s -> s.due <= now } == true }
    val queue = due + fresh

    if (lessons.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Label(deck.title, C.Accent, Modifier.weight(1f))
                if (syncing) CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp), color = C.Accent, strokeWidth = 2.dp)
                else IconBtn(Icons.Rounded.Refresh, "Check for new lessons", tint = C.Muted) { sync() }
            }
            status?.let { Text(it, color = C.Muted, fontSize = 12.sp) }
            Heading("Nothing here yet", 30, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            Text(
                "${deck.blurb} live here as their own deck, with separate stats. " +
                    "New ones appear as soon as they are pushed, no app update needed.",
                color = C.Muted, fontFamily = Serif, fontSize = 17.sp, lineHeight = 25.sp,
            )

        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Label(deck.title, C.Accent, Modifier.weight(1f))
                if (syncing) CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp), color = C.Accent, strokeWidth = 2.dp)
                else IconBtn(Icons.Rounded.Refresh, "Check for new lessons", tint = C.Muted) { sync() }
            }
            status?.let { Text(it, color = C.Muted, fontSize = 12.sp) }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                Heading("${all.size}", 56)
                Spacer(Modifier.width(8.dp))
                Text("cards in ${lessons.size} lessons", color = C.Muted, fontFamily = Serif, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            Spacer(Modifier.height(16.dp))
            PaperCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp)) {
                    Count("New", fresh.size, C.Der, Modifier.weight(1f))
                    Count("Due", due.size, C.Red, Modifier.weight(1f))
                    Count("Mature", states.values.count { it.intervalDays >= 21 }, C.Green, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
            InkButton(
                if (queue.isEmpty()) "All caught up" else "Study ${queue.size} cards",
                Modifier.fillMaxWidth(), color = C.Accent, enabled = queue.isNotEmpty(),
            ) { onStudy(due.shuffled() + fresh, deck.title, false) }
            Spacer(Modifier.height(12.dp))
            InkButton("Review all ${all.size} cards", Modifier.fillMaxWidth(), color = C.Card, textColor = C.Accent) {
                onStudy(all.shuffled(), "${deck.title} · all", true)
            }
            Spacer(Modifier.height(28.dp))
            Label("Lessons")
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        }
        items(lessons.reversed(), key = { it.id }) { l ->
            val cards = l.asCards(deck)
            val lNew = cards.count { it.id !in states }
            val lDue = cards.count { states[it.id]?.let { s -> s.due <= now } == true }
            Row(
                Modifier.fillMaxWidth().clickable { onStudy(cards.filter { it.id !in states || states[it.id]!!.due <= now }.ifEmpty { cards.shuffled() }, l.title, lNew + lDue == 0) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(l.title, fontFamily = Serif, fontSize = 18.sp, color = C.Ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Label(listOf(l.date, l.topic, "${cards.size} cards").filter { it.isNotBlank() }.joinToString("  ·  "))
                }
                if (lNew > 0) { Tag("$lNew new", C.Der); Spacer(Modifier.width(6.dp)) }
                if (lDue > 0) Tag("$lDue due", C.Red)
                if (lNew + lDue == 0) Tag("Done", C.Green)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        }
    }
}

@Composable
private fun Count(label: String, n: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text("$n", color = color, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Label(label)
    }
}
