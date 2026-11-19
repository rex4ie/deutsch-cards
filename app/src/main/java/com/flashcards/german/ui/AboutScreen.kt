package com.flashcards.german.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashcards.german.*
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val fraction: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
fun AboutScreen(repo: Repo, progress: Progress, initial: UpdateState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(initial) }

    fun check() {
        state = UpdateState.Checking
        scope.launch {
            state = runCatching { Updater.fetchLatest() }.fold(
                { if (it.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(it) else UpdateState.UpToDate },
                { UpdateState.Failed(it.message ?: "Couldn't reach GitHub") },
            )
        }
    }
    fun install(r: Updater.Release) {
        if (!Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx); return }
        state = UpdateState.Downloading(0f)
        scope.launch {
            runCatching { Updater.download(ctx, r.apkUrl) { f -> state = UpdateState.Downloading(f) } }
                .onSuccess { Updater.install(ctx, it); state = UpdateState.Available(r) }
                .onFailure { state = UpdateState.Failed(it.message ?: "Download failed") }
        }
    }
    LaunchedEffect(Unit) { if (state == UpdateState.Idle) check() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("← Back", color = C.Ink, fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Spacer(Modifier.height(20.dp))
        Label("About", C.Red)
        Heading("Wortschatz", 40)
        Label("Version ${BuildConfig.VERSION_NAME}")
        Spacer(Modifier.height(28.dp))
        PaperCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Label("Updates")
                Spacer(Modifier.height(8.dp))
                when (val s = state) {
                    UpdateState.Idle, UpdateState.Checking -> Heading("Checking…", 22, C.Muted)
                    UpdateState.UpToDate -> Heading("You're up to date.", 22, C.Green)
                    is UpdateState.Failed -> Text(s.message, color = C.Red)
                    is UpdateState.Available -> Heading("Version 1.0.${s.release.versionCode} is ready.", 22, C.Red)
                    is UpdateState.Downloading -> {
                        Heading("Downloading ${(s.fraction * 100).toInt()} %", 22)
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(s.fraction, Modifier.fillMaxWidth(), C.Green)
                    }
                }
                Spacer(Modifier.height(18.dp))
                when (val s = state) {
                    is UpdateState.Available -> InkButton("Download & install", Modifier.fillMaxWidth(), color = C.Red) { install(s.release) }
                    is UpdateState.Downloading, UpdateState.Checking -> InkButton("Please wait", Modifier.fillMaxWidth(), enabled = false) {}
                    else -> InkButton("Check for updates", Modifier.fillMaxWidth()) { check() }
                }
                if (!Updater.canInstall(ctx)) Text(
                    "Android will ask once for permission to install updates.",
                    color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Label("Your progress")
        Spacer(Modifier.height(4.dp))
        Row2("Lessons", "${progress.completedLessons.size} / ${repo.lessons.size}")
        Row2("Words seen", "${progress.cards.size} / ${repo.allCards.size}")
        Row2("Experience", "${progress.xp} xp")
        Row2("My lessons", "${progress.myCards.size} cards studied")
        Spacer(Modifier.height(28.dp))
        Label("Credits")
        Spacer(Modifier.height(4.dp))
        Row2("Words", "Collins German 3000")
        Row2("Voice", "Edge neural TTS · Katja")
        Spacer(Modifier.height(16.dp))
        Text(
            "Source on GitHub →", color = C.Red, fontFamily = Mono, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL))) }.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun Row2(k: String, v: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(k, color = C.Muted, fontFamily = Serif, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(v, color = C.Ink, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
    }
}
