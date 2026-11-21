package com.flashcards.german.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

/** Cards that come due within the same window, e.g. "In 3 hours" or "Tomorrow". */
data class DueBucket(val label: String, val order: Long, val cards: List<Pair<Card, Long>>)

private const val HOUR = 3_600_000L

private fun localDay(ms: Long) = (ms + TimeZone.getDefault().getOffset(ms)) / Srs.DAY

/** Groups not-yet-due review cards by when they come due, soonest first. */
fun dueBuckets(repo: Repo, p: Progress, now: Long = System.currentTimeMillis()): List<DueBucket> {
    val today = localDay(now)
    val upcoming = repo.allCards.mapNotNull { c -> p.cards[c.id]?.due?.takeIf { it > now }?.let { c to it } }
    return upcoming.groupBy { (_, due) ->
        val days = localDay(due) - today
        val hours = ceil((due - now).toDouble() / HOUR).toLong()
        when {
            due - now < HOUR -> "In under an hour" to 0L
            days == 0L -> (if (hours == 1L) "In 1 hour" else "In $hours hours") to hours
            days == 1L -> "Tomorrow" to 100L
            days < 7 -> "In $days days" to 100L + days
            days < 14 -> "Next week" to 200L
            days < 30 -> "In ${days / 7} weeks" to 200L + days / 7
            days < 60 -> "Next month" to 300L
            else -> "In ${days / 30} months" to 300L + days / 30
        }
    }.map { (key, cards) -> DueBucket(key.first, key.second, cards.sortedBy { it.second }) }.sortedBy { it.order }
}

/** Compact "Coming up" list for the Review tab: the next few windows and a link to the rest. */
@Composable
fun ComingUp(buckets: List<DueBucket>, onOpen: (String?) -> Unit) {
    Label("Coming up")
    Spacer(Modifier.height(6.dp))
    if (buckets.isEmpty()) {
        Text("No reviews scheduled yet.", color = C.Muted, fontSize = 14.sp)
        return
    }
    PaperCard(Modifier.fillMaxWidth()) {
        Column {
            buckets.take(3).forEachIndexed { i, b ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(b.label) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(b.label, color = C.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${b.cards.size}", color = C.Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(null) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("See full schedule", color = C.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Full schedule: every window with its card count; tap one to see the cards inside. */
@Composable
fun ScheduleScreen(repo: Repo, p: Progress, open: String?, onBack: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val buckets = remember(p.cards) { dueBuckets(repo, p, now) }
    val dueNow = remember(p.cards) { repo.allCards.count { p.cards[it.id]?.let { s -> s.due <= now } == true } }
    val expanded = remember { mutableStateListOf<String>().apply { open?.let { add(it) } } }
    val biggest = (buckets.maxOfOrNull { it.cards.size } ?: 1).coerceAtLeast(1)
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val date = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val today = localDay(now)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.height(8.dp))
            Label("Review schedule", C.Accent)
            Heading("Coming up", 32)
            Text(
                if (dueNow > 0) "$dueNow due now, ${buckets.sumOf { it.cards.size }} scheduled later."
                else "${buckets.sumOf { it.cards.size }} cards scheduled.",
                color = C.Muted, fontSize = 15.sp,
            )
            Spacer(Modifier.height(18.dp))
            if (buckets.isEmpty()) Text("Nothing scheduled yet. Finish a lesson in Learn to start.", color = C.Muted)
        }
        items(buckets, key = { it.label }) { b ->
            val isOpen = b.label in expanded
            Column(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                    .border(1.dp, if (isOpen) C.Accent else C.Line, RoundedCornerShape(16.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { if (isOpen) expanded.remove(b.label) else expanded.add(b.label) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(b.label, color = C.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(b.cards.size.toFloat() / biggest, Modifier.fillMaxWidth(.85f).height(6.dp))
                    }
                    Text("${b.cards.size}", color = C.Accent, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (isOpen) "Hide cards" else "Show cards", tint = C.Muted)
                }
                AnimatedVisibility(isOpen) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                        b.cards.forEach { (card, due) ->
                            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
                            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    GenderedText(card.de, 16, androidx.compose.ui.text.style.TextAlign.Start)
                                    Text(card.en, color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (localDay(due) == today) time.format(Date(due)) else date.format(Date(due)),
                                    color = C.Muted, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
