package com.flashcards.german.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GenderMode(val title: String) { Sprint("Sprint"), Round("Round of 20"), Weak("Weak spots"), Mine("My nouns") }

data class GenderResult(val right: Map<String, Int>, val wrong: Map<String, Int>, val sprintScore: Int?)

private fun articleColor(a: String) = when (a) { "der" -> C.Der; "die" -> C.Die; else -> C.Das }

/** Nouns the learner knows reliably: answered right at least 3 times and mostly right. */
private fun WordStat.known() = right >= 3 && right >= wrong * 3

fun pickNouns(mode: GenderMode, all: List<GNoun>, mine: List<GNoun>, stats: Map<String, WordStat>): List<GNoun> = when (mode) {
    // unseen and shaky nouns first, then the rest, so sprints keep teaching something new
    GenderMode.Sprint -> all.sortedBy { n ->
        val s = stats[n.id]; (if (s == null) 0.0 else if (s.known()) 2.0 else 1.0) + Math.random()
    }
    GenderMode.Round -> all.shuffled().sortedBy { stats[it.id]?.known() == true }.take(20).shuffled()
    GenderMode.Weak -> all.plus(mine).filter { (stats[it.id]?.wrong ?: 0) > 0 }
        .sortedByDescending { stats[it.id]!!.wrong - stats[it.id]!!.right * 0.5 + Math.random() }.take(20).shuffled()
    GenderMode.Mine -> mine.shuffled()
}

// ---------------------------------------------------------------- hub (the tab)

@Composable
fun GenderHub(repo: Repo, p: Progress, onPlay: (GenderMode) -> Unit, onScope: (Boolean) -> Unit) {
    val (all, mine) = genderPool(repo, p)
    val totalAll = remember(repo) { nounsFrom(repo.allCards, mine = false).size } + nounsFrom(Deck.entries.flatMap { repo.cardsOf(it) }, mine = true).size
    val learnedCount = genderPool(repo, p.copy(genderLearnedOnly = true)).let { it.first.size + it.second.size }
    val empty = all.isEmpty() && mine.isEmpty()
    val stats = p.genderStats
    val seen = stats.values.sumOf { it.right + it.wrong }
    val right = stats.values.sumOf { it.right }
    val known = stats.values.count { it.known() }
    val weak = all.plus(mine).count { (stats[it.id]?.wrong ?: 0) > 0 }
    val byArticle = remember(stats) {
        (all + mine).groupBy { it.article }.mapValues { (_, ns) ->
            val r = ns.sumOf { stats[it.id]?.right ?: 0 }; val w = ns.sumOf { stats[it.id]?.wrong ?: 0 }
            if (r + w == 0) null else r.toFloat() / (r + w)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            listOf("der", "die", "das").forEach {
                Text(it, color = articleColor(it), fontFamily = Serif, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
            }
        }
        Text("Guess the article, one tap each.", color = C.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        ScopeSwitch(p.genderLearnedOnly, totalAll, learnedCount, onScope)
        if (empty) Text(
            "No learned nouns yet. Finish a lesson in Learn, or study Class or Finds, and they'll show up here.",
            color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(16.dp))

        PaperCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row {
                    BigStat(if (seen == 0) "–" else "${right * 100 / seen}%", "Accuracy", Modifier.weight(1f))
                    BigStat("$known", "Nouns known", Modifier.weight(1f))
                    BigStat("${p.bestSprint}", "Best sprint", Modifier.weight(1f))
                }
                if (byArticle.values.any { it != null }) {
                    Spacer(Modifier.height(16.dp))
                    listOf("der", "die", "das").forEach { a ->
                        val f = byArticle[a]
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a, color = articleColor(a), fontFamily = Serif, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                            ProgressBar(f ?: 0f, Modifier.weight(1f).height(8.dp), articleColor(a))
                            Text(if (f == null) "–" else "${(f * 100).toInt()}%", color = C.Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        ModeCard(Icons.Rounded.Timer, "Sprint", "60 seconds, as many as you can", "Best ${p.bestSprint}", all.isNotEmpty()) { onPlay(GenderMode.Sprint) }
        ModeCard(Icons.Rounded.Casino, "Round of 20", "A relaxed set, favouring nouns you don't know yet", null, all.isNotEmpty()) { onPlay(GenderMode.Round) }
        ModeCard(Icons.Rounded.Healing, "Weak spots", if (weak == 0) "Nouns you get wrong will collect here" else "Nouns you've got wrong before", if (weak > 0) "$weak" else null, weak > 0) { onPlay(GenderMode.Weak) }
        ModeCard(Icons.AutoMirrored.Rounded.MenuBook, "My nouns", if (mine.isEmpty()) "No nouns in Class or Finds yet" else "Nouns from your Class and Finds decks", if (mine.isNotEmpty()) "${mine.size}" else null, mine.isNotEmpty()) { onPlay(GenderMode.Mine) }
    }
}

/** Segmented two-option switch: all nouns vs. only learned ones. */
@Composable
private fun ScopeSwitch(learnedOnly: Boolean, totalAll: Int, learned: Int, onScope: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(C.Sunk).padding(4.dp)) {
        listOf(false to "All nouns · $totalAll", true to "Learned · $learned").forEach { (value, label) ->
            val on = learnedOnly == value
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) C.Card else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, C.Line, RoundedCornerShape(50)) else Modifier)
                    .clickable { if (!on) onScope(value) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) C.Accent else C.Muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = C.Ink, fontFamily = Serif, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = C.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, sub: String, badge: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) C.AccentSoft else C.Sunk), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (enabled) C.Accent else C.Muted)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) C.Ink else C.Muted, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(sub, color = C.Muted, fontSize = 13.sp)
        }
        if (badge != null) Tag(badge, C.Accent)
        Icon(Icons.Rounded.ChevronRight, null, tint = C.Muted)
    }
}

// ---------------------------------------------------------------- game

@Composable
fun GenderGame(mode: GenderMode, nouns: List<GNoun>, best: Int, repo: Repo, onExit: () -> Unit, onFinish: (GenderResult) -> Unit) {
    val sprint = mode == GenderMode.Sprint
    var idx by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf<String?>(null) }
    var timeLeft by remember { mutableIntStateOf(60) }
    var finished by remember { mutableStateOf(false) }
    val right = remember { mutableStateMapOf<String, Int>() }
    val wrong = remember { mutableStateMapOf<String, Int>() }
    val missed = remember { mutableStateListOf<GNoun>() }
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun finish() {
        if (finished) return
        finished = true
        onFinish(GenderResult(right.toMap(), wrong.toMap(), if (sprint) score else null))
    }
    fun next() {
        answered = null
        if (idx + 1 >= nouns.size) finish() else idx++
    }

    if (sprint) LaunchedEffect(Unit) {
        while (timeLeft > 0 && !finished) { delay(1000); timeLeft-- }
        finish()
    }

    if (finished) {
        GenderSummary(mode, score, right.values.sum(), wrong.values.sum(), best, missed, onExit)
        return
    }
    val n = nouns.getOrNull(idx) ?: run { LaunchedEffect(Unit) { finish() }; return }

    fun answer(a: String) {
        if (answered != null) return
        answered = a
        val ok = a == n.article
        n.card?.let { repo.play(it) }
        if (ok) {
            right[n.id] = (right[n.id] ?: 0) + 1; score++; streak++
            Sfx.play(Sound.Correct)
            scope.launch { pop.snapTo(1.08f); pop.animateTo(1f, spring(dampingRatio = .4f)) }
            scope.launch { delay(if (sprint) 350 else 650); if (answered != null) next() }
        } else {
            wrong[n.id] = (wrong[n.id] ?: 0) + 1; streak = 0
            if (missed.none { it.id == n.id }) missed += n
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Sfx.play(Sound.Wrong)
            scope.launch { repeat(3) { shake.animateTo(14f, tween(45)); shake.animateTo(-14f, tween(45)) }; shake.animateTo(0f, tween(45)) }
            if (sprint) scope.launch { delay(1100); if (answered != null) next() }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.Close, "Stop", onClick = { if (right.isEmpty() && wrong.isEmpty()) onExit() else finish() })
            Spacer(Modifier.width(8.dp))
            if (sprint) {
                ProgressBar(timeLeft / 60f, Modifier.weight(1f), if (timeLeft <= 10) C.Red else C.Accent)
                Spacer(Modifier.width(12.dp))
                Text("${timeLeft}s", color = if (timeLeft <= 10) C.Red else C.Muted, fontWeight = FontWeight.Bold)
            } else {
                ProgressBar(idx.toFloat() / nouns.size, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text("${idx + 1}/${nouns.size}", color = C.Muted, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$score", color = C.Ink, fontFamily = Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(if (sprint) "points" else "correct", color = C.Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (streak >= 3) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = C.Gold, modifier = Modifier.size(18.dp))
                Text("$streak in a row", color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // the noun
        val ok = answered == n.article
        val frame = when { answered == null -> C.Line; ok -> articleColor(n.article); else -> C.Red }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.offset { IntOffset(shake.value.toInt(), 0) }.fillMaxWidth()
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(RoundedCornerShape(28.dp)).background(C.Card).border(2.dp, frame, RoundedCornerShape(28.dp))
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                    if (answered != null) Text(n.article, color = articleColor(n.article), fontFamily = Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    else Text("___", color = C.Line, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text(n.word, color = C.Ink, fontFamily = Serif, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 44.sp)
                Spacer(Modifier.height(6.dp))
                Text(n.en, color = C.Muted, fontSize = 16.sp, textAlign = TextAlign.Center)
                if (answered != null && !ok) {
                    Spacer(Modifier.height(14.dp))
                    genderHint(n.word)?.let {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(C.Sunk).padding(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Rounded.Lightbulb, null, tint = C.Gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = C.Ink, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }

        // answer buttons
        if (answered != null && !ok && !sprint) {
            InkButton("Next", Modifier.fillMaxWidth()) { next() }
            Spacer(Modifier.height(66.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("der", "die", "das").forEach { a ->
                    val c = articleColor(a)
                    val dim = answered != null && a != n.article && a != answered
                    ArticleButton(a, c, dim, Modifier.weight(1f)) { answer(a) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ArticleButton(text: String, color: Color, dim: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (dim) .3f else 1f, label = "a")
    Box(
        modifier.height(116.dp).clip(RoundedCornerShape(22.dp)).background(color.copy(alpha = alpha)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontFamily = Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GenderSummary(mode: GenderMode, score: Int, right: Int, wrong: Int, best: Int, missed: List<GNoun>, onExit: () -> Unit) {
    val newBest = mode == GenderMode.Sprint && score > best
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            Label(if (newBest) "New best sprint" else mode.title, if (newBest) C.Gold else C.Accent)
            Heading(if (mode == GenderMode.Sprint) "$score points" else "$right of ${right + wrong} right", 34)
            Spacer(Modifier.height(4.dp))
            Text(
                if (right + wrong == 0) "No answers this time." else "${right * 100 / (right + wrong)}% accuracy",
                color = C.Muted, fontSize = 16.sp,
            )
            if (missed.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Label("Worth another look")
                Spacer(Modifier.height(8.dp))
                PaperCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        missed.forEachIndexed { i, n ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(articleColor(n.article)))
                                Spacer(Modifier.width(10.dp))
                                Text(n.article, color = articleColor(n.article), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(" ${n.word}", color = C.Ink, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                Text(n.en, color = C.Muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        InkButton("Done", Modifier.fillMaxWidth(), onClick = onExit)
    }
}
