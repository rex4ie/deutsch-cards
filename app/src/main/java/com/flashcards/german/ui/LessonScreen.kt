package com.flashcards.german.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*
import kotlinx.coroutines.delay

sealed interface Ex {
    data class Teach(val card: Card) : Ex
    data class ChooseDe(val card: Card, val options: List<Card>) : Ex
    data class ChooseEn(val card: Card, val options: List<Card>) : Ex
    data class Listen(val card: Card, val options: List<Card>) : Ex
    data class Match(val cards: List<Card>) : Ex
    data class Type(val card: Card) : Ex
}

fun buildExercises(lesson: Lesson, pool: List<Card>): List<Ex> {
    fun opts(c: Card): List<Card> {
        val same = lesson.cards.filter { it.de != c.de && it.en != c.en }.shuffled()
        val others = pool.filter { it.de != c.de && it.en != c.en }.shuffled().take(3)
        return (listOf(c) + (same + others).distinctBy { it.de }.take(3)).shuffled()
    }
    val out = mutableListOf<Ex>()
    lesson.cards.chunked(5).forEach { chunk ->
        chunk.forEach { out += Ex.Teach(it) }
        val games = mutableListOf<Ex>()
        chunk.forEachIndexed { i, c ->
            games += when (i % 3) {
                0 -> Ex.ChooseDe(c, opts(c))
                1 -> Ex.Listen(c, opts(c))
                else -> Ex.ChooseEn(c, opts(c))
            }
        }
        if (chunk.size >= 3) games += Ex.Match(chunk.distinctBy { it.en }.distinctBy { it.de })
        chunk.filter { it.de.length <= 28 }.shuffled().take(1).forEach { games += Ex.Type(it) }
        out += games.shuffled()
    }
    return out
}

@Composable
fun LessonScreen(lesson: Lesson, repo: Repo, onExit: () -> Unit, onFinished: (xp: Int) -> Unit) {
    val queue = remember { mutableStateListOf<Ex>().apply { addAll(buildExercises(lesson, repo.allCards)) } }
    val initial = remember { queue.size }
    var done by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var combo by remember { mutableIntStateOf(0) }

    if (finished) {
        val xp = 10 + lesson.cards.size + (if (mistakes == 0) 10 else 0)
        LaunchedEffect(Unit) { onFinished(xp) }
        LessonComplete(lesson, xp, mistakes, initial, onExit)
        return
    }

    val current = queue.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✕", color = C.Muted, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onExit).padding(end = 16.dp))
            ProgressBar(done.toFloat() / (done + queue.size).coerceAtLeast(1), Modifier.weight(1f))
            if (combo >= 3) { Spacer(Modifier.width(12.dp)); Pill("🔥$combo", C.Amber) }
        }
        if (current == null) { LaunchedEffect(Unit) { finished = true }; return@Column }
        AnimatedContent(
            current, Modifier.weight(1f),
            transitionSpec = { (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut()) },
            label = "ex",
        ) { ex ->
            ExerciseHost(ex, repo) { correct ->
                queue.removeAt(0)
                if (correct) { done++; if (ex !is Ex.Teach) combo++ }
                else { mistakes++; combo = 0; queue.add(ex) }
                if (queue.isEmpty()) finished = true
            }
        }
    }
}

@Composable
private fun ExerciseHost(ex: Ex, repo: Repo, onResult: (Boolean) -> Unit) {
    // answer state: null = unanswered, true/false = checked
    var checked by remember(ex) { mutableStateOf<Boolean?>(null) }
    var canCheck by remember(ex) { mutableStateOf(false) }
    var checkAction by remember(ex) { mutableStateOf<() -> Boolean>({ false }) }
    val haptic = LocalHapticFeedback.current

    fun check() {
        val ok = checkAction()
        checked = ok
        if (!ok) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val card = when (ex) { is Ex.ChooseDe -> ex.card; is Ex.ChooseEn -> ex.card; is Ex.Listen -> ex.card; is Ex.Type -> ex.card; else -> null }
        card?.let { repo.play(it) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            val register: (Boolean, () -> Boolean) -> Unit = { ready, action -> canCheck = ready; checkAction = action }
            when (ex) {
                is Ex.Teach -> TeachView(ex.card, repo)
                is Ex.ChooseDe -> ChooseView("Select the correct translation", ex.card.en, null, ex.options, { it.de }, ex.card, checked, register, repo)
                is Ex.ChooseEn -> ChooseView("What does this mean?", ex.card.de, ex.card, ex.options, { it.en }, ex.card, checked, register, repo)
                is Ex.Listen -> ChooseView("What do you hear?", null, ex.card, ex.options, { it.de }, ex.card, checked, register, repo)
                is Ex.Type -> TypeView(ex.card, checked, register, onSubmit = { if (checked == null && canCheck) check() })
                is Ex.Match -> MatchView(ex.cards, repo) { onResult(true) }
            }
        }
        if (ex is Ex.Match) return@Column
        FeedbackBar(ex, checked, enabled = ex is Ex.Teach || canCheck) {
            when {
                ex is Ex.Teach -> onResult(true)
                checked == null -> check()
                else -> onResult(checked == true)
            }
        }
    }
}

@Composable
private fun FeedbackBar(ex: Ex, checked: Boolean?, enabled: Boolean, onClick: () -> Unit) {
    val bg = when (checked) { true -> C.Green.copy(alpha = .15f); false -> C.Red.copy(alpha = .15f); null -> Color.Transparent }
    Column(Modifier.fillMaxWidth().background(bg).padding(20.dp)) {
        if (checked != null) {
            val card = when (ex) { is Ex.ChooseDe -> ex.card; is Ex.ChooseEn -> ex.card; is Ex.Listen -> ex.card; is Ex.Type -> ex.card; else -> null }
            Text(
                if (checked) listOf("Great job!", "Nice!", "Excellent!", "Correct!").random() else "Correct answer:",
                color = if (checked) C.Green else C.Red, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
            )
            if (card != null) Text("${card.de}  —  ${card.en}", color = if (checked) C.Green else C.Red, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        }
        val (color, shade) = when (checked) { false -> C.Red to C.RedDark; else -> C.Green to C.GreenDark }
        ChunkyButton(
            when { ex is Ex.Teach -> "Got it"; checked == null -> "Check"; else -> "Continue" },
            Modifier.fillMaxWidth(), color = color, shade = shade,
            enabled = checked != null || enabled, onClick = onClick,
        )
    }
}

@Composable
private fun Prompt(text: String) {
    Text(text, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun TeachView(card: Card, repo: Repo) {
    LaunchedEffect(card) { delay(250); repo.play(card) }
    Prompt("New word")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.Primary.copy(alpha = .35f), C.Surface)))
            .border(2.dp, C.Border, RoundedCornerShape(28.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (card.section.isNotBlank() && card.section != "vocabulary") Pill(card.section, C.Muted)
        Spacer(Modifier.height(16.dp))
        GenderedText(card.de, 30)
        Spacer(Modifier.height(10.dp))
        Text(card.en, color = C.Muted, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        SpeakerButton(64) { repo.play(card) }
    }
}

/** German text with the article coloured by gender (der = blue, die = pink, das = green). */
@Composable
fun GenderedText(de: String, size: Int) {
    val color = when {
        de.startsWith("der ") -> C.Blue
        de.startsWith("die ") -> C.Pink
        de.startsWith("das ") -> C.Green
        else -> C.Text
    }
    Text(de, color = color, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = (size * 1.2).sp)
}

@Composable
private fun ChooseView(
    title: String, promptText: String?, audioCard: Card?, options: List<Card>, label: (Card) -> String,
    answer: Card, checked: Boolean?, register: (Boolean, () -> Boolean) -> Unit, repo: Repo,
) {
    var selected by remember(answer) { mutableStateOf<Card?>(null) }
    LaunchedEffect(answer) { if (audioCard != null) { delay(300); repo.play(audioCard) } }
    LaunchedEffect(selected) { register(selected != null) { selected == answer } }
    Prompt(title)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
        if (audioCard != null) { SpeakerButton(if (promptText == null) 88 else 52) { repo.play(audioCard) }; Spacer(Modifier.width(16.dp)) }
        if (promptText != null) Text(promptText, color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
    options.forEach { o ->
        val st = when {
            checked != null && o == answer -> TileState.Correct
            checked == false && o == selected -> TileState.Wrong
            o == selected -> TileState.Selected
            else -> TileState.Idle
        }
        OptionTile(label(o), st, Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            if (checked == null) selected = o
        }
    }
}

@Composable
private fun TypeView(card: Card, checked: Boolean?, register: (Boolean, () -> Boolean) -> Unit, onSubmit: () -> Unit) {
    var value by remember(card) { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(value.text) { register(value.text.isNotBlank()) { answerMatches(value.text, card.de) } }
    Prompt("Type this in German")
    Text(card.en, color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 20.dp))
    OutlinedTextField(
        value, { value = it }, Modifier.fillMaxWidth(),
        enabled = checked == null,
        placeholder = { Text("auf Deutsch…", color = C.Muted) },
        textStyle = androidx.compose.ui.text.TextStyle(color = C.Text, fontSize = 20.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Primary, unfocusedBorderColor = C.Border, focusedContainerColor = C.Surface, unfocusedContainerColor = C.Surface, disabledContainerColor = C.Surface, disabledTextColor = C.Text),
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ä", "ö", "ü", "ß").forEach { ch ->
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2)
                    .clickable(enabled = checked == null) {
                        val t = value.text.substring(0, value.selection.start) + ch + value.text.substring(value.selection.end)
                        value = TextFieldValue(t, TextRange(value.selection.start + 1))
                    },
                contentAlignment = Alignment.Center,
            ) { Text(ch, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
    Text("Tip: include the article (der / die / das).", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun MatchView(cards: List<Card>, repo: Repo, onDone: () -> Unit) {
    val left = remember(cards) { cards.shuffled() }
    val right = remember(cards) { cards.shuffled() }
    var selLeft by remember { mutableStateOf<Card?>(null) }
    var selRight by remember { mutableStateOf<Card?>(null) }
    val matched = remember { mutableStateListOf<Card>() }
    var wrong by remember { mutableStateOf<Pair<Card, Card>?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(selLeft, selRight) {
        val l = selLeft; val r = selRight
        if (l != null && r != null) {
            if (l == r) { matched += l; repo.play(l); selLeft = null; selRight = null }
            else { wrong = l to r; haptic.performHapticFeedback(HapticFeedbackType.LongPress); delay(500); wrong = null; selLeft = null; selRight = null }
        }
    }
    LaunchedEffect(matched.size) { if (matched.size == cards.size) { delay(500); onDone() } }

    Prompt("Tap the matching pairs")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            left.forEach { c ->
                val st = when { c in matched -> TileState.Faded; wrong?.first == c -> TileState.Wrong; selLeft == c -> TileState.Selected; else -> TileState.Idle }
                OptionTile(c.en, st, Modifier.fillMaxWidth().heightIn(min = 64.dp)) { selLeft = c }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            right.forEach { c ->
                val st = when { c in matched -> TileState.Faded; wrong?.second == c -> TileState.Wrong; selRight == c -> TileState.Selected; else -> TileState.Idle }
                OptionTile(c.de, st, Modifier.fillMaxWidth().heightIn(min = 64.dp)) { selRight = c; repo.play(c) }
            }
        }
    }
}

@Composable
private fun LessonComplete(lesson: Lesson, xp: Int, mistakes: Int, total: Int, onExit: () -> Unit) {
    val accuracy = (100 * total / (total + mistakes).coerceAtLeast(1))
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("🏆", fontSize = 96.sp)
        Text("Lesson complete!", color = C.Amber, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(lesson.title, color = C.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultBox("TOTAL XP", "⚡ $xp", C.Amber, Modifier.weight(1f))
            ResultBox("ACCURACY", "🎯 $accuracy%", C.Green, Modifier.weight(1f))
            ResultBox("WORDS", "📚 ${lesson.cards.size}", C.Blue, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text("These words are now in your Review deck.", color = C.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        ChunkyButton("Continue", Modifier.fillMaxWidth(), color = C.Green, shade = C.GreenDark, onClick = onExit)
    }
}

@Composable
private fun ResultBox(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(color)) {
        Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(6.dp), textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth().padding(3.dp).clip(RoundedCornerShape(13.dp)).background(C.Bg).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}
