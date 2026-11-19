package com.flashcards.german.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
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
    val card: Card?
    data class Teach(override val card: Card) : Ex
    data class ChooseDe(override val card: Card, val options: List<Card>) : Ex
    data class ChooseEn(override val card: Card, val options: List<Card>) : Ex
    data class Listen(override val card: Card, val options: List<Card>) : Ex
    data class Match(val cards: List<Card>) : Ex { override val card: Card? get() = null }
    data class Type(override val card: Card) : Ex
}

private fun options(c: Card, near: List<Card>, pool: List<Card>): List<Card> {
    val same = near.filter { it.de != c.de && it.en != c.en }.shuffled()
    val others = pool.filter { it.de != c.de && it.en != c.en }.shuffled().take(4)
    return (listOf(c) + (same + others).distinctBy { it.de }.distinctBy { it.en }.take(3)).shuffled()
}

private fun quiz(c: Card, i: Int, near: List<Card>, pool: List<Card>): Ex = when (i % 4) {
    0 -> Ex.ChooseDe(c, options(c, near, pool))
    1 -> Ex.Listen(c, options(c, near, pool))
    2 -> Ex.ChooseEn(c, options(c, near, pool))
    else -> if (c.de.length <= 28) Ex.Type(c) else Ex.ChooseDe(c, options(c, near, pool))
}

/** A normal lesson: teach in small groups, then quiz each group. */
fun lessonExercises(lesson: Lesson, pool: List<Card>): List<Ex> {
    val out = mutableListOf<Ex>()
    lesson.cards.chunked(if (lesson.cards.size > 5) (lesson.cards.size + 1) / 2 else 5).forEach { chunk ->
        chunk.forEach { out += Ex.Teach(it) }
        val games = chunk.mapIndexed { i, c -> quiz(c, i, lesson.cards, pool) }.toMutableList<Ex>()
        val matchable = chunk.distinctBy { it.en }.distinctBy { it.de }
        if (matchable.size >= 3) games += Ex.Match(matchable)
        out += games.shuffled()
    }
    // final round: one more quiz per word in a different format
    out += lesson.cards.shuffled().mapIndexed { i, c -> quiz(c, i + 1, lesson.cards, pool) }
    return out
}

/** Adaptive review: no teaching, the learner's weakest words in mixed formats. */
fun reviewExercises(cards: List<Card>, pool: List<Card>): List<Ex> {
    val out = cards.mapIndexed { i, c -> quiz(c, i, cards, pool) }.toMutableList<Ex>()
    cards.distinctBy { it.en }.distinctBy { it.de }.take(5).takeIf { it.size >= 3 }?.let { out.add(out.size / 2, Ex.Match(it)) }
    out += cards.shuffled().take(4).mapIndexed { i, c -> quiz(c, i + 2, cards, pool) }
    return out
}

data class LessonResult(val xp: Int, val right: Map<String, Int>, val wrong: Map<String, Int>)

@Composable
fun LessonScreen(
    title: String, kicker: String, build: () -> List<Ex>, repo: Repo, wordCount: Int,
    onExit: () -> Unit, onFinished: (LessonResult) -> Unit,
) {
    val queue = remember { mutableStateListOf<Ex>().apply { addAll(build()) } }
    var done by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var streak by remember { mutableIntStateOf(0) }
    val right = remember { mutableStateMapOf<String, Int>() }
    val wrong = remember { mutableStateMapOf<String, Int>() }
    var confirmExit by remember { mutableStateOf(false) }

    if (finished) {
        val result = remember { LessonResult(10 + wordCount * 2 + (if (mistakes == 0) 10 else 0), right.toMap(), wrong.toMap()) }
        LaunchedEffect(Unit) { onFinished(result) }
        LessonComplete(title, kicker, result.xp, mistakes, done, wordCount, onExit)
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.Close, "Leave lesson") { confirmExit = true }
            Spacer(Modifier.width(8.dp))
            ProgressBar(done.toFloat() / (done + queue.size).coerceAtLeast(1), Modifier.weight(1f), C.Green)
            Spacer(Modifier.width(12.dp))
            Label(if (streak >= 3) "$streak in a row" else "${done}/${done + queue.size}", if (streak >= 3) C.Gold else C.Muted)
        }
        val current = queue.firstOrNull()
        if (current == null) { LaunchedEffect(Unit) { finished = true }; return@Column }
        AnimatedContent(
            current, Modifier.weight(1f),
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            label = "ex",
        ) { ex ->
            ExerciseHost(ex, repo, onWordResult = { card, ok ->
                if (ok) right[card.id] = (right[card.id] ?: 0) + 1 else wrong[card.id] = (wrong[card.id] ?: 0) + 1
            }) { correct ->
                queue.removeAt(0)
                if (correct) { done++; if (ex !is Ex.Teach) streak++ }
                else { mistakes++; streak = 0; queue.add(ex) }
                if (queue.isEmpty()) finished = true
            }
        }
    }

    if (confirmExit) ConfirmDialog(
        "Leave this lesson?", "Your progress in this lesson won't be saved.", "Leave", "Keep going",
        onConfirm = onExit, onDismiss = { confirmExit = false },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, yes: String, no: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        PaperCard {
            Column(Modifier.padding(24.dp)) {
                Heading(title, 22)
                Text(body, color = C.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                InkButton(no, Modifier.fillMaxWidth(), onClick = onDismiss)
                Spacer(Modifier.height(10.dp))
                InkButton(yes, Modifier.fillMaxWidth(), color = C.Card, textColor = C.Red, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun ExerciseHost(ex: Ex, repo: Repo, onWordResult: (Card, Boolean) -> Unit, onResult: (Boolean) -> Unit) {
    var checked by remember(ex) { mutableStateOf<Boolean?>(null) }
    var canCheck by remember(ex) { mutableStateOf(false) }
    var checkAction by remember(ex) { mutableStateOf<() -> Boolean>({ false }) }
    val haptic = LocalHapticFeedback.current

    fun check() {
        val ok = checkAction()
        checked = ok
        if (!ok) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Sfx.play(if (ok) Sound.Correct else Sound.Wrong)
        ex.card?.let { repo.play(it); onWordResult(it, ok) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            val register: (Boolean, () -> Boolean) -> Unit = { ready, action -> canCheck = ready; checkAction = action }
            when (ex) {
                is Ex.Teach -> TeachView(ex.card, repo)
                is Ex.ChooseDe -> ChooseView("Translate into German", ex.card.en, null, ex.options, { it.de }, true, ex.card, checked, register, repo)
                is Ex.ChooseEn -> ChooseView("What does this mean?", ex.card.de, ex.card, ex.options, { it.en }, false, ex.card, checked, register, repo)
                is Ex.Listen -> ChooseView("What do you hear?", null, ex.card, ex.options, { it.de }, true, ex.card, checked, register, repo)
                is Ex.Type -> TypeView(ex.card, checked, register, onSubmit = { if (checked == null && canCheck) check() })
                is Ex.Match -> MatchView(ex.cards, repo, onWordResult) { onResult(true) }
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

private val praise = listOf("Richtig!", "Genau!", "Sehr gut!", "Prima!", "Super!")

@Composable
private fun FeedbackBar(ex: Ex, checked: Boolean?, enabled: Boolean, onClick: () -> Unit) {
    val bg = when (checked) { true -> C.GreenSoft; false -> C.RedSoft; null -> C.Bg }
    Column(Modifier.fillMaxWidth().background(bg)) {
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(when (checked) { true -> C.Green; false -> C.Red; null -> C.Line }))
        Column(Modifier.padding(20.dp)) {
            if (checked != null) {
                Heading(if (checked) remember { praise.random() } else "Not quite.", 22, if (checked) C.Green else C.Red)
                ex.card?.let {
                    Text("${it.de}  =  ${it.en}", color = if (checked) C.Green else C.Red, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                }
            }
            InkButton(
                when { ex is Ex.Teach -> "Got it"; checked == null -> "Check"; else -> "Continue" },
                Modifier.fillMaxWidth(),
                color = when (checked) { true -> C.Green; false -> C.Red; null -> C.Accent },
                enabled = checked != null || enabled, onClick = onClick,
            )
        }
    }
}

@Composable
private fun Prompt(text: String) {
    Label(text, modifier = Modifier.padding(top = 12.dp, bottom = 14.dp))
}

/** New-word card styled as a ruled index card. */
@Composable
private fun TeachView(card: Card, repo: Repo) {
    LaunchedEffect(card) { delay(250); repo.play(card) }
    Prompt("New word")
    PaperCard(Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Label(card.section.takeIf { it.isNotBlank() && it != "vocabulary" } ?: "Vokabel", C.Accent)
            }
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(C.Accent.copy(alpha = .6f)))
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                GenderedText(card.de, 32)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(40.dp).height(1.dp).background(C.Rule))
                Spacer(Modifier.height(10.dp))
                Text(card.en, color = C.Muted, fontFamily = Serif, fontSize = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                SpeakerButton(56) { repo.play(card) }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    GenderKey()
}

@Composable
private fun GenderKey() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Label("der", C.Der); Label("die", C.Die); Label("das", C.Das)
    }
}

@Composable
private fun ChooseView(
    title: String, promptText: String?, audioCard: Card?, options: List<Card>, label: (Card) -> String, serifOptions: Boolean,
    answer: Card, checked: Boolean?, register: (Boolean, () -> Boolean) -> Unit, repo: Repo,
) {
    var selected by remember(answer) { mutableStateOf<Card?>(null) }
    LaunchedEffect(answer) { if (audioCard != null) { delay(300); repo.play(audioCard) } }
    LaunchedEffect(selected) { register(selected != null) { selected == answer } }
    Prompt(title)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
        if (audioCard != null) {
            SpeakerButton(if (promptText == null) 84 else 48) { repo.play(audioCard) }
            Spacer(Modifier.width(16.dp))
        }
        if (promptText != null) {
            if (audioCard != null) GenderedText(promptText, 28, TextAlign.Start) else Heading(promptText, 28)
        }
    }
    options.forEachIndexed { i, o ->
        val st = when {
            checked != null && o == answer -> TileState.Correct
            checked == false && o == selected -> TileState.Wrong
            o == selected -> TileState.Selected
            else -> TileState.Idle
        }
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("${i + 1}", modifier = Modifier.width(22.dp))
            OptionTile(label(o), st, Modifier.weight(1f), serif = serifOptions) { if (checked == null) selected = o }
        }
    }
}

@Composable
private fun TypeView(card: Card, checked: Boolean?, register: (Boolean, () -> Boolean) -> Unit, onSubmit: () -> Unit) {
    var value by remember(card) { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(value.text) { register(value.text.isNotBlank()) { answerMatches(value.text, card.de) } }
    Prompt("Write it in German")
    Heading(card.en, 28, modifier = Modifier.padding(bottom = 20.dp))
    OutlinedTextField(
        value, { value = it }, Modifier.fillMaxWidth(),
        enabled = checked == null,
        placeholder = { Text("auf Deutsch", color = C.Muted, fontFamily = Serif) },
        textStyle = TextStyle(color = C.Ink, fontSize = 22.sp, fontFamily = Serif),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line, cursorColor = C.Accent,
            focusedContainerColor = C.Card, unfocusedContainerColor = C.Card, disabledContainerColor = C.Card, disabledTextColor = C.Ink, disabledBorderColor = C.Line,
        ),
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ä", "ö", "ü", "ß").forEach { ch ->
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(6.dp))
                    .clickable(enabled = checked == null) {
                        val t = value.text.substring(0, value.selection.start) + ch + value.text.substring(value.selection.end)
                        value = TextFieldValue(t, TextRange(value.selection.start + 1))
                    },
                contentAlignment = Alignment.Center,
            ) { Text(ch, color = C.Ink, fontSize = 20.sp, fontFamily = Serif) }
        }
    }
    Text("Include the article: der, die or das.", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun MatchView(cards: List<Card>, repo: Repo, onWordResult: (Card, Boolean) -> Unit, onDone: () -> Unit) {
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
            if (l == r) { matched += l; Sfx.play(Sound.Correct); onWordResult(l, true); selLeft = null; selRight = null }
            else {
                wrong = l to r; Sfx.play(Sound.Wrong); onWordResult(l, false)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(550); wrong = null; selLeft = null; selRight = null
            }
        }
    }
    LaunchedEffect(matched.size) { if (matched.size == cards.size) { delay(450); onDone() } }

    Prompt("Match the pairs")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            left.forEach { c ->
                val st = when { c in matched -> TileState.Faded; wrong?.first == c -> TileState.Wrong; selLeft == c -> TileState.Selected; else -> TileState.Idle }
                OptionTile(c.en, st, Modifier.fillMaxWidth().heightIn(min = 64.dp)) { if (wrong == null) selLeft = c }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            right.forEach { c ->
                val st = when { c in matched -> TileState.Faded; wrong?.second == c -> TileState.Wrong; selRight == c -> TileState.Selected; else -> TileState.Idle }
                OptionTile(c.de, st, Modifier.fillMaxWidth().heightIn(min = 64.dp), serif = true) { if (wrong == null) { selRight = c; repo.play(c) } }
            }
        }
    }
}

/** Full-screen, unmistakable "you finished" page with a rubber-stamp animation. */
@Composable
private fun LessonComplete(title: String, kicker: String, xp: Int, mistakes: Int, answered: Int, words: Int, onExit: () -> Unit) {
    val accuracy = 100 * answered / (answered + mistakes).coerceAtLeast(1)
    LaunchedEffect(Unit) { Sfx.play(Sound.Done) }
    val stamp = remember { Animatable(2.4f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(200)
        alpha.animateTo(1f, tween(120))
        stamp.animateTo(1f, spring(dampingRatio = .45f, stiffness = 500f))
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.scale(stamp.value).rotate(-8f)
                .border(4.dp, C.Green.copy(alpha = alpha.value), RoundedCornerShape(10.dp))
                .padding(4.dp).border(1.5.dp, C.Green.copy(alpha = alpha.value), RoundedCornerShape(7.dp))
                .padding(horizontal = 26.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("GESCHAFFT", color = C.Green.copy(alpha = alpha.value), fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 4.sp)
        }
        Spacer(Modifier.height(32.dp))
        Label(kicker, C.Accent)
        Heading("$title complete", 30, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(32.dp))
        PaperCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                StatLine("Experience", "+$xp xp")
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
                StatLine("Accuracy", "$accuracy %")
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
                StatLine("Words practised", "$words")
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("These words are now in your review deck.", color = C.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        InkButton("Continue", Modifier.fillMaxWidth(), color = C.Green, onClick = onExit)
    }
}

@Composable
private fun StatLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(k, color = C.Muted, fontFamily = Serif, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(v, color = C.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
