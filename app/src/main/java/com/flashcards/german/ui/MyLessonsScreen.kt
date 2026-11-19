package com.flashcards.german.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*

/** Separate Anki-style deck built from the learner's own daily lessons. */
@Composable
fun MyLessonsScreen(repo: Repo, p: Progress, onStudy: (List<Card>, String, Boolean) -> Unit) {
    val now = System.currentTimeMillis()
    val lessons = repo.myLessons
    val all = lessons.flatMap { it.asCards() }
    val fresh = all.filter { it.id !in p.myCards }
    val due = all.filter { p.myCards[it.id]?.let { s -> s.due <= now } == true }
    val queue = due + fresh

    if (lessons.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Label("My lessons", C.Red)
            Heading("Nothing here yet", 30, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            Text(
                "Lessons from your own classes (grammar, vocabulary, anything) live here as their own deck, " +
                    "with separate stats. New lessons arrive with app updates.",
                color = C.Muted, fontFamily = Serif, fontSize = 17.sp, lineHeight = 25.sp,
            )

        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Label("My lessons")
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
                    Count("Mature", p.myCards.values.count { it.intervalDays >= 21 }, C.Green, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
            InkButton(
                if (queue.isEmpty()) "All caught up" else "Study ${queue.size} cards",
                Modifier.fillMaxWidth(), color = C.Red, enabled = queue.isNotEmpty(),
            ) { onStudy(due.shuffled() + fresh, "My lessons", false) }
            Spacer(Modifier.height(12.dp))
            InkButton("Review all ${all.size} cards", Modifier.fillMaxWidth(), color = C.Card, textColor = C.Ink) {
                onStudy(all.shuffled(), "My lessons · all", true)
            }
            Spacer(Modifier.height(28.dp))
            Label("Lessons")
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(C.Ink))
        }
        items(lessons.reversed(), key = { it.id }) { l ->
            val cards = l.asCards()
            val lNew = cards.count { it.id !in p.myCards }
            val lDue = cards.count { p.myCards[it.id]?.let { s -> s.due <= now } == true }
            Row(
                Modifier.fillMaxWidth().clickable { onStudy(cards.filter { it.id !in p.myCards || p.myCards[it.id]!!.due <= now }.ifEmpty { cards.shuffled() }, l.title, lNew + lDue == 0) }
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
        Text("$n", color = color, fontFamily = Mono, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Label(label)
    }
}
