package com.flashcards.german.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
    onStudyMine: (List<Card>, String, Boolean) -> Unit,
    updateAvailable: Boolean,
    onAbout: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(progress, updateAvailable, onAbout)
        Box(Modifier.weight(1f)) {
            when (tab) {
                Screen.Learn -> LearnPath(repo, progress, onOpen)
                Screen.Review -> ReviewDashboard(repo, progress, onStudy)
                else -> MyLessonsScreen(repo, progress, onStudyMine)
            }
        }
        BottomBar(tab, onTab, dueCount(progress), myDueCount(repo, progress))
    }
}

fun myDueCount(repo: Repo, p: Progress): Int {
    val now = System.currentTimeMillis()
    return repo.myLessons.flatMap { it.asCards() }.count { p.myCards[it.id]?.let { s -> s.due <= now } ?: true }
}

fun dueCount(p: Progress): Int {
    val now = System.currentTimeMillis()
    return p.cards.values.count { it.due <= now }
}

@Composable
private fun TopBar(p: Progress, updateAvailable: Boolean, onAbout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Heading("Wortschatz", 26)
            Label("${p.streak}-day streak  ·  ${p.xp} xp  ·  ${p.cards.size} words")
        }
        Box(Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAbout).padding(8.dp)) {
            if (updateAvailable) Tag("Update", C.Red, filled = true) else Tag("About")
        }
    }
    Box(Modifier.fillMaxWidth().height(1.5.dp).background(C.Ink))
}

@Composable
private fun BottomBar(tab: Screen, onTab: (Screen) -> Unit, due: Int, myDue: Int) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(C.Ink))
        Row(Modifier.fillMaxWidth().background(C.Card)) {
            NavItem("Learn", null, tab == Screen.Learn, Modifier.weight(1f)) { onTab(Screen.Learn) }
            Box(Modifier.width(1.5.dp).height(56.dp).background(C.Ink))
            NavItem("Review", due.takeIf { it > 0 }, tab == Screen.Review, Modifier.weight(1f)) { onTab(Screen.Review) }
            Box(Modifier.width(1.5.dp).height(56.dp).background(C.Ink))
            NavItem("Mine", myDue.takeIf { it > 0 }, tab == Screen.Mine, Modifier.weight(1f)) { onTab(Screen.Mine) }
        }
    }
}

@Composable
private fun NavItem(label: String, badge: Int?, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(56.dp).background(if (active) C.Ink else C.Card).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), color = if (active) C.Card else C.Ink, fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(CircleShape).background(C.Red).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text("$badge", color = C.Card, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------- Learn path

private enum class Status { Done, Current, Locked }

@Composable
private fun LearnPath(repo: Repo, p: Progress, onOpen: (PathItem) -> Unit) {
    val path = repo.path
    val currentIdx = path.indexOfFirst { !p.isDone(it) }.let { if (it < 0) path.size else it }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (currentIdx - 2).coerceAtLeast(0))
    val unitTotals = remember(path) { path.groupingBy { unitOf(it) }.eachCount() }

    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)) {
        itemsIndexed(path, key = { _, it -> it.key }) { i, item ->
            val unit = unitOf(item)
            val status = when {
                p.isDone(item) -> Status.Done
                i == currentIdx -> Status.Current
                else -> Status.Locked
            }
            if (i == 0 || unitOf(path[i - 1]) != unit) {
                val doneInUnit = path.count { unitOf(it) == unit && p.isDone(it) }
                UnitHeader(item, unit + 1, doneInUnit, unitTotals[unit] ?: 0)
            }
            val isLastInUnit = i == path.lastIndex || unitOf(path[i + 1]) != unit
            PathRow(item, status, isFirst = i == 0 || unitOf(path[i - 1]) != unit, isLast = isLastInUnit) {
                if (status != Status.Locked) onOpen(item)
            }
        }
    }
}

private fun unitOf(item: PathItem) = when (item) {
    is PathItem.LessonStop -> item.unit
    is PathItem.ReviewStop -> item.unit
}

@Composable
private fun UnitHeader(first: PathItem, n: Int, done: Int, total: Int) {
    val lesson = (first as? PathItem.LessonStop)?.lesson ?: return
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Unit %02d".format(n), C.Red)
            Spacer(Modifier.weight(1f))
            Label("$done / $total", if (done == total) C.Green else C.Muted)
        }
        Spacer(Modifier.height(4.dp))
        Heading(lesson.unitTitle, 26)
        if (lesson.titleDe.isNotBlank()) Text(
            lesson.titleDe.substringBefore(" & "), color = C.Muted, fontFamily = Serif,
            fontSize = 17.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
    }
}

@Composable
private fun PathRow(item: PathItem, status: Status, isFirst: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val isReview = item is PathItem.ReviewStop
    val title: String
    val sub: String
    when (item) {
        is PathItem.LessonStop -> {
            val l = item.lesson
            title = if (l.parts > 1) "${l.title.substringBefore(" & ")} — part ${l.part}" else l.title
            sub = l.cards.take(3).joinToString(", ") { it.de } + if (l.cards.size > 3) "…" else ""
        }
        is PathItem.ReviewStop -> {
            title = "Review checkpoint"
            sub = "Your trickiest words so far"
        }
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clickable(enabled = status != Status.Locked, onClick = onClick)
            .background(if (status == Status.Current) C.Card else Color.Transparent)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // rail + node
        Box(Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val c = if (status == Status.Done) C.Ink else C.Line
                if (!isFirst) drawLine(c, Offset(x, 0f), Offset(x, size.height / 2), 2.dp.toPx())
                if (!isLast) drawLine(if (status == Status.Done) C.Ink else C.Line, Offset(x, size.height / 2), Offset(x, size.height), 2.dp.toPx())
            }
            Node(status, isReview)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 16.dp)) {
            Text(
                title, fontFamily = Serif, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = if (status == Status.Locked) C.Muted else C.Ink,
                fontStyle = if (isReview) androidx.compose.ui.text.font.FontStyle.Italic else null,
            )
            Text(sub, color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        when (status) {
            Status.Done -> Tag("Done", C.Green)
            Status.Current -> Tag("Start", C.Red, filled = true)
            Status.Locked -> {}
        }
    }
}

@Composable
private fun Node(status: Status, review: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "r")
    val shape = if (review) RoundedCornerShape(4.dp) else CircleShape
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
        if (status == Status.Current) Canvas(Modifier.fillMaxSize()) {
            drawCircle(C.Red.copy(alpha = 1f - ring), radius = size.minDimension / 2 * (0.7f + ring * 0.5f), style = Stroke(2.dp.toPx()))
        }
        Box(
            Modifier.size(26.dp).then(if (review) Modifier.rotate(45f) else Modifier).clip(shape)
                .background(when (status) { Status.Done -> C.Ink; Status.Current -> C.Red; Status.Locked -> C.Bg })
                .border(2.dp, when (status) { Status.Locked -> C.Line; Status.Current -> C.Red; Status.Done -> C.Ink }, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (status == Status.Done) Box(if (review) Modifier.rotate(-45f) else Modifier) { CheckMark(C.Card, 16.dp, 2.5f) }
        }
    }
}

// ---------------------------------------------------------------- Review dashboard

@Composable
private fun ReviewDashboard(repo: Repo, p: Progress, onStudy: (List<Card>, String, Boolean) -> Unit) {
    val now = System.currentTimeMillis()
    val total = repo.allCards.size
    val seenCards = repo.allCards.filter { it.id in p.cards }
    val seen = seenCards.size
    val due = seenCards.filter { p.cards[it.id]!!.due <= now }
    val mature = p.cards.values.count { it.intervalDays >= 21 }
    val young = seen - mature

    Column(Modifier.fillMaxSize().padding(20.dp)) {
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
            Legend(C.Ink, "Learning $young")
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
        Spacer(Modifier.weight(1f))
        InkButton(
            if (due.isEmpty()) "No reviews due" else "Review due cards",
            Modifier.fillMaxWidth(), color = C.Red, enabled = due.isNotEmpty(),
        ) { onStudy(due.shuffled(), "Due reviews", false) }
        Spacer(Modifier.height(12.dp))
        InkButton(
            "Review all $seen words", Modifier.fillMaxWidth(), color = C.Card, textColor = C.Ink, enabled = seen > 0,
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
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        drawRect(C.Card)
        drawRect(C.Green, size = Size(size.width * a, size.height))
        drawRect(C.Ink, Offset(size.width * a, 0f), Size(size.width * b, size.height))
        // tick every 10%
        for (i in 1..9) drawLine(C.Line, Offset(size.width * i / 10, size.height * .7f), Offset(size.width * i / 10, size.height), 1.dp.toPx(), StrokeCap.Butt)
        drawRect(C.Ink, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color).border(1.dp, C.Ink))
        Spacer(Modifier.width(6.dp))
        Label(text, C.Ink)
    }
}
