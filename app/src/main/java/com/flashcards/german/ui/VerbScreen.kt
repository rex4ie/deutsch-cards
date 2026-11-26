package com.flashcards.german.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*
import kotlinx.coroutines.launch

data class VerbResult(val right: Map<String, Int>, val wrong: Map<String, Int>)

// ---------------------------------------------------------------- the Practice tab

/** The Practice tab: der/die/das and verb forms, one switch apart. */
@Composable
fun PracticeTab(
    repo: Repo, p: Progress,
    onTab: (String) -> Unit,
    onGender: (GenderMode) -> Unit, onGenderScope: (Boolean) -> Unit,
    onVerbs: (VerbMode) -> Unit, onVerbForms: (VerbFormChoice) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
            Segmented(listOf("artikel" to "der · die · das", "verbs" to "Verb forms"), p.practiceTab, onTab)
        }
        Box(Modifier.weight(1f)) {
            if (p.practiceTab == "verbs") VerbHub(repo, p, onVerbs, onVerbForms)
            else GenderHub(repo, p, onGender, onGenderScope)
        }
    }
}

@Composable
fun VerbHub(repo: Repo, p: Progress, onPlay: (VerbMode) -> Unit, onForms: (VerbFormChoice) -> Unit) {
    LaunchedEffect(Unit) { repo.refreshVerbs() }
    val verbs = repo.verbs
    val choice = runCatching { VerbFormChoice.valueOf(p.verbForms) }.getOrDefault(VerbFormChoice.Both)
    val stats = p.verbStats
    val seen = stats.values.sumOf { it.right + it.wrong }
    val right = stats.values.sumOf { it.right }
    val known = stats.values.count { it.knownForm() }
    val weak = remember(verbs, stats) { weakVerbForms(verbs, stats) }
    val irregular = remember(verbs) { verbs.count { it.irr } }
    val classN = remember(verbs) { verbs.count { 'k' in it.src } }
    val collinsN = remember(verbs) { verbs.count { 'c' in it.src } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("ging · gegangen", color = C.Accent, fontFamily = Serif, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Type the Präteritum or the Perfekt.", color = C.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Segmented(VerbFormChoice.entries.map { it to it.label }, choice, onForms)
        Spacer(Modifier.height(16.dp))

        PaperCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp)) {
                BigStat(if (seen == 0) "–" else "${right * 100 / seen}%", "Accuracy", Modifier.weight(1f))
                BigStat("$known", "Forms known", Modifier.weight(1f))
                BigStat("${verbs.size}", "Verbs", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(18.dp))
        val any = verbs.isNotEmpty()
        ModeCard(Icons.Rounded.Casino, "Round of 20", "All verbs, favouring forms you don't know yet", null, any) { onPlay(VerbMode.Round) }
        ModeCard(Icons.Rounded.Bolt, "Irregular verbs", "The ones you have to learn by heart: gehen, ging, gegangen", "$irregular", irregular > 0) { onPlay(VerbMode.Irregular) }
        ModeCard(Icons.Rounded.Healing, "Weak spots", if (weak == 0) "Forms you get wrong will collect here" else "Forms you've got wrong before", if (weak > 0) "$weak" else null, weak > 0) { onPlay(VerbMode.Weak) }
        ModeCard(Icons.AutoMirrored.Rounded.MenuBook, "Class verbs", if (classN == 0) "No verbs from class yet" else "Verbs from your class notes", if (classN > 0) "$classN" else null, classN > 0) { onPlay(VerbMode.Class) }
        ModeCard(Icons.Rounded.Forest, "Collins verbs", "Verbs from the Learn path", if (collinsN > 0) "$collinsN" else null, collinsN > 0) { onPlay(VerbMode.Collins) }
        Text(
            "Verb forms from LanguageTool's German dictionary (CC BY-SA 4.0).",
            color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

// ---------------------------------------------------------------- game

@Composable
fun VerbGame(questions: List<VerbQ>, onExit: () -> Unit, onFinish: (VerbResult) -> Unit) {
    var idx by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var verdict by remember { mutableStateOf<VerbVerdict?>(null) }
    var finished by remember { mutableStateOf(false) }
    val right = remember { mutableStateMapOf<String, Int>() }
    val wrong = remember { mutableStateMapOf<String, Int>() }
    val missed = remember { mutableStateListOf<VerbQ>() }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }

    fun finish() {
        if (finished) return
        finished = true
        onFinish(VerbResult(right.toMap(), wrong.toMap()))
    }

    if (finished) {
        VerbSummary(right.values.sum(), wrong.values.sum(), missed, onExit)
        return
    }
    val q = questions.getOrNull(idx) ?: run { LaunchedEffect(Unit) { finish() }; return }
    LaunchedEffect(idx) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (verdict != null) {
            verdict = null; input = TextFieldValue("")
            if (idx + 1 >= questions.size) finish() else idx++
            return
        }
        if (input.text.isBlank()) return
        val v = checkVerb(q, input.text)
        verdict = v
        if (v == VerbVerdict.Right) {
            right[q.statId] = (right[q.statId] ?: 0) + 1
            Sfx.play(Sound.Correct)
        } else {
            wrong[q.statId] = (wrong[q.statId] ?: 0) + 1
            if (missed.none { it.statId == q.statId }) missed += q
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Sfx.play(Sound.Wrong)
            scope.launch { repeat(3) { shake.animateTo(12f, tween(45)); shake.animateTo(-12f, tween(45)) }; shake.animateTo(0f, tween(45)) }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.Close, "Stop", onClick = { if (right.isEmpty() && wrong.isEmpty()) onExit() else finish() })
            Spacer(Modifier.width(8.dp))
            ProgressBar(idx.toFloat() / questions.size, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text("${idx + 1}/${questions.size}", color = C.Muted, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val ok = verdict == VerbVerdict.Right
            val frame = when (verdict) { null -> C.Line; VerbVerdict.Right -> C.Accent; else -> C.Red }
            Column(
                Modifier.offset { IntOffset(shake.value.toInt(), 0) }.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)).background(C.Card).border(2.dp, frame, RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(q.form.title, C.Accent)
                    if (q.verb.irr) Tag("irregular", C.Gold)
                }
                Spacer(Modifier.height(14.dp))
                Text(q.verb.inf, color = C.Ink, fontFamily = Serif, fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 42.sp)
                Text(q.verb.en, color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
                if (q.verb.ctx.isNotEmpty()) Text(
                    q.verb.ctx, color = C.Muted, fontSize = 13.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(PERSONS[q.person], color = C.Accent, fontFamily = Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    input, { if (verdict == null) input = it }, Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text(if (q.form == VerbForm.Perfekt) "hat / ist …" else "Präteritum", color = C.Muted, fontFamily = Serif) },
                    textStyle = TextStyle(color = C.Ink, fontSize = 22.sp, fontFamily = Serif),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line, cursorColor = C.Accent,
                        focusedContainerColor = C.Card, unfocusedContainerColor = C.Card,
                        focusedTextColor = C.Ink, unfocusedTextColor = C.Ink,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ä", "ö", "ü", "ß").forEach { ch ->
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(10.dp))
                            .clickable(enabled = verdict == null) {
                                val t = input.text.substring(0, input.selection.start) + ch + input.text.substring(input.selection.end)
                                input = TextFieldValue(t, TextRange(input.selection.start + 1))
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(ch, color = C.Ink, fontSize = 20.sp, fontFamily = Serif) }
                }
            }

            if (verdict != null) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Sunk).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = if (ok) C.Accent else C.Red, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (ok) "Richtig" else "${PERSONS[q.person].substringBefore('/')} ${q.answer}", color = if (ok) C.Accent else C.Ink, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }
                    if (verdict == VerbVerdict.WrongAux) Text(
                        "Right participle, but ${q.verb.inf} takes ${if (q.verb.mainAux == 's') "sein" else "haben"}.",
                        color = C.Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(q.verb.parts, color = C.Muted, fontSize = 14.sp, fontFamily = Serif, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InkButton(if (verdict == null) "Check" else "Next", Modifier.fillMaxWidth(), enabled = verdict != null || input.text.isNotBlank()) { submit() }
    }
}

@Composable
private fun VerbSummary(right: Int, wrong: Int, missed: List<VerbQ>, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            Label("Verb forms", C.Accent)
            Heading("$right of ${right + wrong} right", 34)
            Spacer(Modifier.height(4.dp))
            Text(if (right + wrong == 0) "No answers this time." else "${right * 100 / (right + wrong)}% accuracy", color = C.Muted, fontSize = 16.sp)
            if (missed.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Label("Worth another look")
                Spacer(Modifier.height(8.dp))
                PaperCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        missed.forEachIndexed { i, q ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Text(q.verb.parts, color = C.Ink, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(q.verb.en, color = C.Muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        InkButton("Done", Modifier.fillMaxWidth(), onClick = onExit)
    }
}
