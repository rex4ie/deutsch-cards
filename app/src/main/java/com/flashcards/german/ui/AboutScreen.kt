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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = C.Text, fontSize = 26.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp))
            Text("About", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🇩🇪", fontSize = 56.sp)
            Text("Deutsch Cards", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Version ${BuildConfig.VERSION_NAME}", color = C.Muted)
            Spacer(Modifier.height(20.dp))
            when (val s = state) {
                UpdateState.Idle, UpdateState.Checking -> Text("Checking for updates…", color = C.Muted)
                UpdateState.UpToDate -> Pill("✓ You're on the latest version", C.Green)
                is UpdateState.Failed -> Text(s.message, color = C.Red, textAlign = TextAlign.Center)
                is UpdateState.Available -> Pill("New version 1.0.${s.release.versionCode} available", C.Amber)
                is UpdateState.Downloading -> {
                    Text("Downloading… ${(s.fraction * 100).toInt()}%", color = C.Muted)
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(s.fraction, Modifier.fillMaxWidth(), C.Primary)
                }
            }
            Spacer(Modifier.height(20.dp))
            when (val s = state) {
                is UpdateState.Available -> ChunkyButton("Download & install", Modifier.fillMaxWidth(), color = C.Green, shade = C.GreenDark) { install(s.release) }
                is UpdateState.Downloading, UpdateState.Checking -> ChunkyButton("Please wait", Modifier.fillMaxWidth(), enabled = false) {}
                else -> ChunkyButton("Check for updates", Modifier.fillMaxWidth()) { check() }
            }
            if (!Updater.canInstall(ctx)) Text(
                "You'll be asked once to allow this app to install updates.",
                color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Surface).padding(20.dp)) {
            Row2("Lessons", "${progress.completedLessons.size} / ${repo.lessons.size} complete")
            Row2("Words seen", "${progress.cards.size} / ${repo.allCards.size}")
            Row2("Total XP", "${progress.xp}")
            Row2("Audio", "Microsoft Edge neural TTS (Katja)")
            Row2("Words", "Collins German 3000 Words and Phrases")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Source on GitHub", color = C.Blue, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL))) }.padding(8.dp),
        )
    }
}

@Composable
private fun Row2(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = C.Muted)
        Spacer(Modifier.width(16.dp))
        Text(v, color = C.Text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}
