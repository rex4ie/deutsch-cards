package com.flashcards.german.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*

@Composable
fun HomeScaffold(
    tab: Screen,
    onTab: (Screen) -> Unit,
    repo: Repo,
    progress: Progress,
    onOpen: (PathItem) -> Unit,
    onStudy: (List<Card>, String, Boolean) -> Unit,
    onStudyDeck: (Deck, List<Card>, String, Boolean) -> Unit,
    onGender: (GenderMode) -> Unit,
    onGenderScope: (Boolean) -> Unit,
    onPracticeTab: (String) -> Unit,
    onVerbs: (VerbMode) -> Unit,
    onVerbForms: (VerbFormChoice) -> Unit,
    onSchedule: (String?) -> Unit,
    updateAvailable: Boolean,
    onAbout: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(progress, updateAvailable, onAbout)
        Box(Modifier.weight(1f)) {
            when (tab) {
                Screen.Learn -> LearnPath(repo, progress, onOpen)
                Screen.Review -> ReviewDashboard(repo, progress, onStudy, onSchedule)
                Screen.Artikel -> PracticeTab(repo, progress, onPracticeTab, onGender, onGenderScope, onVerbs, onVerbForms)
                Screen.Finds -> MyLessonsScreen(repo, progress, Deck.Finds) { c, t, cram -> onStudyDeck(Deck.Finds, c, t, cram) }
                else -> MyLessonsScreen(repo, progress, Deck.Class) { c, t, cram -> onStudyDeck(Deck.Class, c, t, cram) }
            }
        }
        val left = progress.leftToday()
        BottomBar(tab, onTab, minOf(left, dueCount(progress)), minOf(left, myDueCount(repo, progress, Deck.Class)), minOf(left, myDueCount(repo, progress, Deck.Finds)))
    }
}

fun myDueCount(repo: Repo, p: Progress, d: Deck): Int {
    val now = System.currentTimeMillis()
    val st = p.deck(d)
    return repo.cardsOf(d).count { st[it.id]?.let { s -> s.due <= now } ?: true }
}

fun dueCount(p: Progress): Int {
    val now = System.currentTimeMillis()
    return p.cards.values.count { it.due <= now }
}

@Composable
private fun TopBar(p: Progress, updateAvailable: Boolean, onAbout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Heading("Wortschatz", 26, modifier = Modifier.weight(1f))
        StatChip(Icons.Rounded.LocalFireDepartment, "${p.streak}", if (p.streak > 0) C.Gold else C.Muted)
        Spacer(Modifier.width(6.dp))
        StatChip(Icons.Rounded.Bolt, "${p.xp}", C.Gold)
        Box(contentAlignment = Alignment.TopEnd) {
            IconBtn(if (updateAvailable) Icons.Rounded.SystemUpdate else Icons.Rounded.Info, if (updateAvailable) "Update available" else "About", tint = C.Muted, onClick = onAbout)
            if (updateAvailable) Box(Modifier.padding(9.dp).size(9.dp).clip(CircleShape).background(C.Red))
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = C.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Material 3 style navigation bar with a pill indicator behind the active icon. */
@Composable
private fun BottomBar(tab: Screen, onTab: (Screen) -> Unit, due: Int, myDue: Int, findsDue: Int) {
    Row(
        Modifier.fillMaxWidth().background(C.Card).border(1.dp, C.Line).padding(vertical = 10.dp),
    ) {
        NavItem(Icons.Rounded.Forest, "Learn", null, tab == Screen.Learn, Modifier.weight(1f)) { onTab(Screen.Learn) }
        NavItem(Icons.Rounded.Style, "Review", due.takeIf { it > 0 }, tab == Screen.Review, Modifier.weight(1f)) { onTab(Screen.Review) }
        NavItem(Icons.Rounded.Category, "Practice", null, tab == Screen.Artikel, Modifier.weight(1f)) { onTab(Screen.Artikel) }
        NavItem(Icons.AutoMirrored.Rounded.MenuBook, "Class", myDue.takeIf { it > 0 }, tab == Screen.Mine, Modifier.weight(1f)) { onTab(Screen.Mine) }
        NavItem(Icons.Rounded.Headphones, "Finds", findsDue.takeIf { it > 0 }, tab == Screen.Finds, Modifier.weight(1f)) { onTab(Screen.Finds) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, badge: Int?, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val w by animateDpAsState(if (active) 64.dp else 40.dp, label = "pill")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier.width(w).height(32.dp).clip(RoundedCornerShape(50)).background(if (active) C.AccentSoft else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, label, tint = if (active) C.Accent else C.Muted, modifier = Modifier.size(24.dp)) }
            if (badge != null) Box(
                Modifier.offset(x = 2.dp, y = (-2).dp).clip(CircleShape).background(C.Red).padding(horizontal = 5.dp, vertical = 1.dp)
            ) { Text(if (badge > 99) "99+" else "$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (active) C.Ink else C.Muted, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- Learn trail

private enum class Status { Done, Current, Locked }

/** One row in the trail list: a chapter banner or a stepping stone. */
private sealed interface TrailEntry {
    data class Chapter(val n: Int, val lesson: Lesson, val done: Int, val total: Int) : TrailEntry
    data class Stone(val item: PathItem, val index: Int, val status: Status, val x: Float, val nextX: Float?) : TrailEntry
}

private val STONE_ROW = 104.dp
/** Horizontal positions of the stones, as a fraction of half the width, making a gentle S-curve. */
private val wave = floatArrayOf(0f, 0.42f, 0.62f, 0.42f, 0f, -0.42f, -0.62f, -0.42f)

private fun unitOf(item: PathItem) = when (item) {
    is PathItem.LessonStop -> item.unit
    is PathItem.ReviewStop -> item.unit
}

@Composable
private fun LearnPath(repo: Repo, p: Progress, onOpen: (PathItem) -> Unit) {
    val path = repo.path
    val currentIdx = path.indexOfFirst { !p.isDone(it) }.let { if (it < 0) path.size else it }
    val entries = remember(path, p.completedLessons, p.completedReviews) {
        val out = mutableListOf<TrailEntry>()
        val unitTotals = path.groupingBy { unitOf(it) }.eachCount()
        path.forEachIndexed { i, item ->
            val unit = unitOf(item)
            if (i == 0 || unitOf(path[i - 1]) != unit) {
                val first = (item as PathItem.LessonStop).lesson
                out += TrailEntry.Chapter(unit + 1, first, path.count { unitOf(it) == unit && p.isDone(it) }, unitTotals[unit] ?: 0)
            }
            val sameUnitNext = i < path.lastIndex && unitOf(path[i + 1]) == unit
            out += TrailEntry.Stone(
                item, i,
                when { p.isDone(item) -> Status.Done; i == currentIdx -> Status.Current; else -> Status.Locked },
                wave[i % wave.size], if (sameUnitNext) wave[(i + 1) % wave.size] else null,
            )
        }
        out
    }
    val currentEntry = entries.indexOfFirst { it is TrailEntry.Stone && it.status == Status.Current }
    // +1 for the hero card at the top; land with the current stone a little below the top
    val state = rememberLazyListState(initialFirstVisibleItemIndex = if (currentEntry <= 1) 0 else currentEntry - 1)

    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 56.dp)) {
        item(key = "hero") { ContinueCard(path.getOrNull(currentIdx), currentIdx, path.size, p, onOpen) }
        items(entries.size, key = { i -> entries[i].let { if (it is TrailEntry.Stone) it.item.key else "c${(it as TrailEntry.Chapter).n}" } }) { i ->
            when (val e = entries[i]) {
                is TrailEntry.Chapter -> ChapterBanner(e)
                is TrailEntry.Stone -> StoneRow(e) { if (e.status != Status.Locked) onOpen(e.item) }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: PathItem?, idx: Int, total: Int, p: Progress, onOpen: (PathItem) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(C.Accent).padding(20.dp)
    ) {
        if (item == null) {
            Label("Alles geschafft", C.OnAccent.copy(alpha = .8f))
            Heading("Every lesson complete", 26, C.OnAccent)
            Text("Keep your words fresh in Review.", color = C.OnAccent.copy(alpha = .85f), modifier = Modifier.padding(top = 4.dp))
            return@Column
        }
        val (kicker, title, words) = when (item) {
            is PathItem.LessonStop -> Triple(
                "Lesson ${item.lesson.id} · ${idx * 100 / total.coerceAtLeast(1)}% of the path",
                item.lesson.unitTitle + if (item.lesson.parts > 1) " ${item.lesson.part}/${item.lesson.parts}" else "",
                item.lesson.cards.take(4).map { it.de },
            )
            is PathItem.ReviewStop -> Triple("Checkpoint ${item.n}", "Review your trickiest words", emptyList())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item is PathItem.ReviewStop) Icons.Rounded.Replay else Icons.Rounded.Hiking, null, tint = C.OnAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Label(kicker, C.OnAccent.copy(alpha = .85f))
        }
        Spacer(Modifier.height(6.dp))
        Heading(title, 26, C.OnAccent)
        if (words.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                words.forEach {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(C.OnAccent.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(it, color = C.OnAccent, fontFamily = Serif, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        InkButton(
            if (p.completedLessons.isEmpty() && item is PathItem.LessonStop && item.lesson.id == 1) "Start your first lesson" else "Continue",
            Modifier.fillMaxWidth(), color = C.OnAccent, textColor = C.Accent,
        ) { onOpen(item) }
    }
}

@Composable
private fun ChapterBanner(c: TrailEntry.Chapter) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(if (c.done == c.total) Icons.Rounded.EmojiEvents else Icons.Rounded.Park, null, tint = if (c.done == c.total) C.Gold else C.Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Label("Chapter ${c.n}", C.Accent)
            Text(c.lesson.unitTitle, color = C.Ink, fontFamily = Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (c.lesson.titleDe.isNotBlank()) Text(
                c.lesson.titleDe.substringBefore(" & ").replaceFirstChar { it.uppercase() },
                color = C.Muted, fontFamily = Serif, fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
            Spacer(Modifier.height(8.dp))
            ProgressBar(c.done.toFloat() / c.total.coerceAtLeast(1), Modifier.fillMaxWidth().height(6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("${c.done}/${c.total}", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StoneRow(s: TrailEntry.Stone, onClick: () -> Unit) {
    val review = s.item is PathItem.ReviewStop
    val trailDone = C.Moss
    val trailTodo = C.Line
    Box(Modifier.fillMaxWidth().height(STONE_ROW)) {
        // the trail segment to the next stone, drawn as a soft S-curve
        Canvas(Modifier.matchParentSize()) {
            val half = size.width / 2
            val stoneY = 40.dp.toPx()
            val x0 = half + s.x * half * 0.62f
            val next = s.nextX ?: return@Canvas
            val x1 = half + next * half * 0.62f
            val y1 = size.height + stoneY
            val path = Path().apply {
                moveTo(x0, stoneY)
                cubicTo(x0, stoneY + (y1 - stoneY) * .5f, x1, stoneY + (y1 - stoneY) * .5f, x1, y1)
            }
            drawPath(
                path, if (s.status == Status.Done) trailDone else trailTodo,
                style = Stroke(
                    width = 7.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (s.status == Status.Done) null else PathEffect.dashPathEffect(floatArrayOf(2f, 22f)),
                ),
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val half = maxWidth / 2
            val cx = half + half * s.x * 0.62f
            Column(
                Modifier.offset(x = cx - 60.dp, y = 0.dp).width(120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Stone(s.status, review, onClick)
                Spacer(Modifier.height(4.dp))
                val label = when (val it = s.item) {
                    is PathItem.LessonStop -> if (it.lesson.parts > 1) "${it.lesson.unitTitle} ${it.lesson.part}" else it.lesson.unitTitle
                    is PathItem.ReviewStop -> "Checkpoint"
                }
                Text(
                    label, color = if (s.status == Status.Locked) C.Muted else C.Ink, fontSize = 12.sp,
                    fontWeight = if (s.status == Status.Current) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Stone(status: Status, review: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "r")
    val size = if (status == Status.Current) 76.dp else 64.dp
    val shape = if (review) RoundedCornerShape(22.dp) else CircleShape
    val face = when (status) {
        Status.Done -> if (review) C.Gold else C.Accent
        Status.Current -> if (review) C.Gold else C.Accent
        Status.Locked -> C.Sunk
    }
    val accentForRing = face
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        if (status == Status.Current) Canvas(Modifier.fillMaxSize()) {
            drawCircle(accentForRing.copy(alpha = (1f - ring) * .6f), radius = this.size.minDimension / 2 * (0.8f + ring * 0.25f), style = Stroke(3.dp.toPx()))
        }
        Box(
            Modifier.size(size).clip(shape).background(face)
                .then(if (status == Status.Locked) Modifier.border(2.dp, C.Line, shape) else Modifier)
                .clickable(enabled = status != Status.Locked, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val (icon, tint) = when (status) {
                Status.Done -> Icons.Rounded.Check to C.OnAccent
                Status.Current -> (if (review) Icons.Rounded.Replay else Icons.Rounded.PlayArrow) to C.OnAccent
                Status.Locked -> (if (review) Icons.Rounded.Replay else Icons.Rounded.Lock) to C.Muted
            }
            Icon(icon, null, tint = tint, modifier = Modifier.size(if (status == Status.Current) 38.dp else 28.dp))
        }
    }
}

/** "12 of 30 cards left today" under a study button, when the daily limit is on. */
@Composable
fun LimitNote(p: Progress) {
    if (!p.dailyLimitOn) return
    val left = p.leftToday()
    Text(
        if (left == 0) "You've studied your ${p.dailyLimit} cards for today. Review all still works, or change the limit in Settings."
        else "$left of ${p.dailyLimit} cards left today",
        color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
    )
}

// ---------------------------------------------------------------- Review dashboard

@Composable
private fun ReviewDashboard(repo: Repo, p: Progress, onStudy: (List<Card>, String, Boolean) -> Unit, onSchedule: (String?) -> Unit) {
    val now = System.currentTimeMillis()
    val total = repo.allCards.size
    val seenCards = repo.allCards.filter { it.id in p.cards }
    val seen = seenCards.size
    val due = seenCards.filter { p.cards[it.id]!!.due <= now }
    val mature = p.cards.values.count { it.intervalDays >= 21 }
    val young = seen - mature

    val buckets = remember(p.cards) { dueBuckets(repo, p, now) }
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp)) {
        Label("Word bank")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Heading("$seen", 64)
            Spacer(Modifier.width(8.dp))
            Text("of $total words seen", color = C.Muted, fontFamily = Serif, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        Spacer(Modifier.height(12.dp))
        BankBar(mature, young, total)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(C.Green, "Mature $mature")
            Legend(C.Accent, "Learning $young")
            Legend(C.Line, "New ${total - seen}")
        }
        Spacer(Modifier.height(28.dp))
        PaperCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("Due today", if (due.isEmpty()) C.Muted else C.Red)
                    Heading("${due.size} cards", 30)
                    Text(
                        if (due.isEmpty()) "Nothing waiting. Come back tomorrow." else "Spaced repetition keeps them from fading.",
                        color = C.Muted, fontSize = 14.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        ComingUp(buckets, onSchedule)
        Spacer(Modifier.height(24.dp))
        val left = p.leftToday()
        val today = minOf(left, due.size)
        InkButton(
            when { due.isEmpty() -> "No reviews due"; today == 0 -> "Daily limit reached"; today < due.size -> "Review $today of ${due.size} due"; else -> "Review due cards" },
            Modifier.fillMaxWidth(), color = C.Accent, enabled = today > 0,
        ) { onStudy(due.shuffled(), "Due reviews", false) }
        LimitNote(p)
        Spacer(Modifier.height(12.dp))
        InkButton(
            "Review all $seen words", Modifier.fillMaxWidth(), color = C.Card, textColor = C.Accent, enabled = seen > 0,
        ) { onStudy(seenCards.shuffled(), "All words", true) }
        if (seen == 0) Text(
            "Finish a lesson in Learn and its words land here.",
            color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun BankBar(mature: Int, young: Int, total: Int) {
    val a by animateFloatAsState(mature.toFloat() / total, tween(800), label = "a")
    val b by animateFloatAsState(young.toFloat() / total, tween(800), label = "b")
    val bg = C.Sunk; val g = C.Green; val acc = C.Accent
    Canvas(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(50))) {
        drawRect(bg)
        drawRect(g, size = Size(size.width * a, size.height))
        drawRect(acc, Offset(size.width * a, 0f), Size(size.width * b, size.height))
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Label(text, C.Muted)
    }
}
