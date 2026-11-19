package com.flashcards.german

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.flashcards.german.ui.*

sealed interface Screen {
    data object Learn : Screen
    data object Review : Screen
    data object About : Screen
    data class PlayLesson(val lesson: Lesson) : Screen
    data class Study(val cards: List<Card>, val title: String, val cram: Boolean) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo(applicationContext)
        setContent {
            AppTheme {
                var progress by remember { mutableStateOf(repo.load()) }
                var screen by remember { mutableStateOf<Screen>(Screen.Learn) }
                var tab by remember { mutableStateOf<Screen>(Screen.Learn) }
                var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
                LaunchedEffect(Unit) {
                    runCatching { Updater.fetchLatest() }.onSuccess {
                        if (it.versionCode > BuildConfig.VERSION_CODE) updateState = UpdateState.Available(it)
                    }
                }
                fun update(p: Progress) { progress = p; repo.save(p) }

                BackHandler(enabled = screen != tab) { screen = tab }

                Box(Modifier.fillMaxSize().background(C.Bg).systemBarsPadding()) {
                    AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { s ->
                        when (s) {
                            Screen.Learn, Screen.Review -> HomeScaffold(
                                tab = s,
                                onTab = { tab = it; screen = it },
                                repo = repo,
                                progress = progress,
                                onOpenLesson = { screen = Screen.PlayLesson(it) },
                                onStudy = { cards, title, cram -> screen = Screen.Study(cards, title, cram) },
                                updateAvailable = updateState is UpdateState.Available,
                                onAbout = { screen = Screen.About },
                            )
                            Screen.About -> AboutScreen(repo, progress, updateState) { screen = tab }
                            is Screen.PlayLesson -> LessonScreen(
                                lesson = s.lesson, repo = repo,
                                onExit = { screen = tab },
                                onFinished = { xp ->
                                    val now = System.currentTimeMillis()
                                    val cards = progress.cards.toMutableMap()
                                    s.lesson.cards.forEach { c ->
                                        if (c.id !in cards) cards[c.id] = CardState(reps = 1, intervalDays = 1.0, due = now + Srs.DAY)
                                    }
                                    update(progress.copy(cards = cards, completedLessons = progress.completedLessons + s.lesson.id).withActivity(xp))
                                },
                            )
                            is Screen.Study -> StudyScreen(
                                cards = s.cards, title = s.title, cram = s.cram, repo = repo,
                                progress = progress,
                                onRate = { card, rating ->
                                    if (!s.cram) {
                                        val st = progress.cards[card.id] ?: CardState()
                                        update(progress.copy(cards = progress.cards + (card.id to Srs.schedule(st, rating))))
                                    }
                                },
                                onDone = { reviewed ->
                                    if (reviewed > 0) update(progress.withActivity(reviewed))
                                    screen = tab
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
