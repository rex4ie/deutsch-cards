package com.flashcards.german.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*
import kotlin.math.sin

@Composable
fun HomeScaffold(
    tab: Screen,
    onTab: (Screen) -> Unit,
    repo: Repo,
    progress: Progress,
    onOpenLesson: (Lesson) -> Unit,
    onStudy: (List<Card>, String, Boolean) -> Unit,
    updateAvailable: Boolean,
    onAbout: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopStats(progress, updateAvailable, onAbout)
        Box(Modifier.weight(1f)) {
            if (tab == Screen.Learn) LearnPath(repo, progress, onOpenLesson)
            else ReviewDashboard(repo, progress, onStudy)
        }
        BottomBar(tab, onTab, dueCount(repo, progress))
    }
}

fun dueCount(repo: Repo, p: Progress): Int {
    val now = System.currentTimeMillis()
    return p.cards.values.count { it.due <= now }
}

@Composable
private fun TopStats(p: Progress, updateAvailable: Boolean, onAbout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🇩🇪 Deutsch", color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("🔥 ${p.streak}", C.Amber)
            Pill("⚡ ${p.xp}", C.Primary)
            Pill("📚 ${p.cards.size}", C.Blue)
            Box(Modifier.clip(CircleShape).clickable(onClick = onAbout)) {
                if (updateAvailable) Pill("⬆ Update", C.Green) else Pill("ⓘ", C.Muted)
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Screen, onTab: (Screen) -> Unit, due: Int) {
    Row(
        Modifier.fillMaxWidth().background(C.Surface).padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem("🗺️", "Learn", tab == Screen.Learn) { onTab(Screen.Learn) }
        NavItem("🧠", if (due > 0) "Review · $due" else "Review", tab == Screen.Review) { onTab(Screen.Review) }
    }
}

@Composable
private fun NavItem(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) C.Primary.copy(alpha = .2f) else Color.Transparent
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(bg)
            .border(2.dp, if (active) C.Primary else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (active) C.Text else C.Muted, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- Learn path

private val unitColors = listOf(C.Primary, C.Green, C.Blue, C.Pink, C.Amber)

@Composable
private fun LearnPath(repo: Repo, p: Progress, onOpen: (Lesson) -> Unit) {
    val lessons = repo.lessons
    val currentIdx = lessons.indexOfFirst { it.id !in p.completedLessons }.let { if (it < 0) lessons.size else it }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (currentIdx - 2).coerceAtLeast(0))
    // Units: a new unit starts whenever the chapter title changes
    var unit = 0
    val unitOf = IntArray(lessons.size)
    lessons.forEachIndexed { i, l ->
        if (i > 0 && l.title.substringBefore(" & ") != lessons[i - 1].title.substringBefore(" & ") &&
            l.title.substringBefore(" & ") !in lessons[i - 1].title) unit++
        unitOf[i] = unit
    }

    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        items(lessons.size, key = { lessons[it].id }) { i ->
            val l = lessons[i]
            val color = unitColors[unitOf[i] % unitColors.size]
            if (i == 0 || unitOf[i] != unitOf[i - 1]) UnitBanner(l, color, unitOf[i] + 1)
            val status = when {
                l.id in p.completedLessons -> NodeStatus.Done
                i == currentIdx -> NodeStatus.Current
                else -> NodeStatus.Locked
            }
            val x = (sin(i * 0.9) * 90).dp
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.offset(x = x)) {
                    LessonNode(l, status, color) { if (status != NodeStatus.Locked) onOpen(l) }
                }
            }
        }
    }
}

private enum class NodeStatus { Done, Current, Locked }

@Composable
private fun UnitBanner(l: Lesson, color: Color, n: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = .65f))))
            .padding(20.dp)
    ) {
        Text("UNIT $n", color = Color.White.copy(alpha = .8f), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp)
        Text(l.title.substringBefore(" & "), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        if (l.titleDe.isNotBlank()) Text(l.titleDe.substringBefore(" & "), color = Color.White.copy(alpha = .85f), fontSize = 15.sp)
    }
}

@Composable
private fun LessonNode(l: Lesson, status: NodeStatus, color: Color, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    val face = when (status) { NodeStatus.Done -> C.Amber; NodeStatus.Current -> color; NodeStatus.Locked -> C.Surface2 }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (status == NodeStatus.Current) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Surface).border(2.dp, color, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("START", color = color, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier.size(76.dp).scale(if (status == NodeStatus.Current) scale else 1f)
                .clip(CircleShape).background(face.copy(alpha = if (status == NodeStatus.Locked) 1f else .55f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.size(70.dp).clip(CircleShape).background(face), contentAlignment = Alignment.Center) {
                Text(
                    when (status) { NodeStatus.Done -> "★"; NodeStatus.Current -> "${l.id}"; NodeStatus.Locked -> "🔒" },
                    color = if (status == NodeStatus.Locked) C.Muted else Color.White,
                    fontSize = if (status == NodeStatus.Locked) 22.sp else 26.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${l.cards.size} words", color = if (status == NodeStatus.Locked) C.Surface2 else C.Muted, fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------- Review dashboard

@Composable
private fun ReviewDashboard(repo: Repo, p: Progress, onStudy: (List<Card>, String, Boolean) -> Unit) {
    val now = System.currentTimeMillis()
    val total = repo.allCards.size
    val seen = p.cards.size
    val due = repo.allCards.filter { p.cards[it.id]?.let { s -> s.due <= now } == true }
    val mature = p.cards.values.count { it.intervalDays >= 21 }
    val young = seen - mature
    val encountered = repo.lessons.filter { l -> l.cards.any { it.id in p.cards } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Surface).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Word bank", color = C.Muted, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Ring(seen.toFloat() / total, mature.toFloat() / total) {
                    Text("${(seen * 100f / total).let { if (it in 0.01f..1f) "<1" else "%.0f".format(it) }}%", color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text("$seen / $total", color = C.Muted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("${due.size}", "Due", C.Red)
                    Stat("$young", "Learning", C.Primary)
                    Stat("$mature", "Mature", C.Green)
                    Stat("${total - seen}", "New", C.Muted)
                }
            }
        }
        item {
            ChunkyButton(
                if (due.isEmpty()) "All caught up 🎉" else "Review ${due.size} due cards",
                Modifier.fillMaxWidth(), color = C.Green, shade = C.GreenDark, enabled = due.isNotEmpty(),
            ) { onStudy(due.shuffled(), "Due reviews", false) }
        }
        if (encountered.isNotEmpty()) item {
            Text("Your lessons", color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
        } else item {
            Text(
                "Finish a lesson on the Learn tab and its words will show up here for spaced-repetition review.",
                color = C.Muted, modifier = Modifier.padding(8.dp),
            )
        }
        items(encountered, key = { it.id }) { l ->
            val states = l.cards.mapNotNull { p.cards[it.id] }
            val lDue = l.cards.filter { p.cards[it.id]?.let { s -> s.due <= now } == true }
            val strength = states.map { (it.intervalDays / 21.0).coerceAtMost(1.0) }.average().toFloat()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface)
                    .clickable { onStudy(if (lDue.isNotEmpty()) lDue else l.cards.shuffled(), l.title, lDue.isEmpty()) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2), contentAlignment = Alignment.Center) {
                    Text("${l.id}", color = C.Text, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, color = C.Text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(strength, Modifier.fillMaxWidth(), color = if (strength > .7f) C.Green else C.Primary)
                }
                Spacer(Modifier.width(14.dp))
                if (lDue.isNotEmpty()) Pill("${lDue.size} due", C.Red) else Pill("Practice", C.Muted)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Text(label, color = C.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Ring(seen: Float, mature: Float, center: @Composable ColumnScope.() -> Unit) {
    val a by animateFloatAsState(seen, tween(900), label = "a")
    val b by animateFloatAsState(mature, tween(900), label = "b")
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 18.dp.toPx()
            val inset = w / 2
            val sz = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            val tl = Offset(inset, inset)
            drawArc(C.Surface2, 0f, 360f, false, tl, sz, style = Stroke(w))
            drawArc(C.Primary, -90f, 360f * a, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            if (b > 0f) drawArc(C.Green, -90f, 360f * b, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = center)
    }
}
