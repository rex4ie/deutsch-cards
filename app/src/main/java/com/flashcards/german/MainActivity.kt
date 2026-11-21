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
    data object Mine : Screen
    data object Artikel : Screen
    data object Finds : Screen
    data class Schedule(val open: String?) : Screen
    data class Gender(val mode: GenderMode, val nouns: List<GNoun>) : Screen
    data object About : Screen
    data class Play(val item: PathItem) : Screen
    data class Study(val cards: List<Card>, val title: String, val cram: Boolean, val deck: Deck? = null) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo(applicationContext)
        Sfx.init(applicationContext)
        setContent {
            AppTheme {
                var progress by remember { mutableStateOf(repo.load().migrate(repo)) }
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
                            Screen.Learn, Screen.Review, Screen.Mine, Screen.Artikel, Screen.Finds -> HomeScaffold(
                                tab = s,
                                onTab = { tab = it; screen = it },
                                repo = repo,
                                progress = progress,
                                onOpen = { screen = Screen.Play(it) },
                                onStudy = { cards, title, cram -> screen = Screen.Study(cards, title, cram) },
                                onStudyDeck = { deck, cards, title, cram -> screen = Screen.Study(cards, title, cram, deck) },
                                onGenderScope = { learnedOnly -> update(progress.copy(genderLearnedOnly = learnedOnly)) },
                                onSchedule = { open -> screen = Screen.Schedule(open) },
                                onGender = { mode ->
                                    val (all, mine) = genderPool(repo, progress)
                                    screen = Screen.Gender(mode, pickNouns(mode, all, mine, progress.genderStats))
                                },
                                updateAvailable = updateState is UpdateState.Available,
                                onAbout = { screen = Screen.About },
                            )
                            is Screen.Gender -> GenderGame(
                                s.mode, s.nouns, progress.bestSprint, repo,
                                onExit = { screen = tab },
                                onFinish = { res ->
                                    val now = System.currentTimeMillis()
                                    val stats = progress.genderStats.toMutableMap()
                                    (res.right.keys + res.wrong.keys).forEach { id ->
                                        val w = stats[id] ?: WordStat()
                                        stats[id] = w.copy(right = w.right + (res.right[id] ?: 0), wrong = w.wrong + (res.wrong[id] ?: 0), last = now)
                                    }
                                    val answered = res.right.values.sum() + res.wrong.values.sum()
                                    update(
                                        progress.copy(genderStats = stats, bestSprint = maxOf(progress.bestSprint, res.sprintScore ?: 0))
                                            .let { if (answered > 0) it.withActivity(res.right.values.sum()) else it }
                                    )
                                },
                            )
                            is Screen.Schedule -> ScheduleScreen(repo, progress, s.open) { screen = tab }
                            Screen.About -> AboutScreen(repo, progress, updateState) { screen = tab }
                            is Screen.Play -> {
                                val item = s.item
                                val exercises = remember(item) {
                                    when (item) {
                                        is PathItem.LessonStop -> lessonExercises(item.lesson, repo.allCards)
                                        is PathItem.ReviewStop -> reviewExercises(progress.weakestCards(repo, 8, item.afterLesson), repo.allCards)
                                    }
                                }
                                val (title, kicker, words) = when (item) {
                                    is PathItem.LessonStop -> Triple(
                                        item.lesson.title.substringBefore(" & ") + if (item.lesson.parts > 1) " ${item.lesson.part}/${item.lesson.parts}" else "",
                                        "Lesson ${item.lesson.id}", item.lesson.cards.size,
                                    )
                                    is PathItem.ReviewStop -> Triple("Review", "Checkpoint ${item.n}", exercises.mapNotNull { it.card }.distinct().size)
                                }
                                LessonScreen(
                                    title = title, kicker = kicker, build = { exercises }, repo = repo, wordCount = words,
                                    onExit = { screen = tab },
                                    onFinished = { res ->
                                        val now = System.currentTimeMillis()
                                        val cards = progress.cards.toMutableMap()
                                        val stats = progress.words.toMutableMap()
                                        (res.right.keys + res.wrong.keys).forEach { id ->
                                            val w = stats[id] ?: WordStat()
                                            stats[id] = w.copy(right = w.right + (res.right[id] ?: 0), wrong = w.wrong + (res.wrong[id] ?: 0), last = now)
                                        }
                                        var p = progress.copy(words = stats)
                                        p = when (item) {
                                            is PathItem.LessonStop -> {
                                                item.lesson.cards.forEach { c ->
                                                    if (c.id !in cards) cards[c.id] = CardState(reps = 1, intervalDays = 1.0, due = now + Srs.DAY)
                                                }
                                                p.copy(cards = cards, completedLessons = p.completedLessons + item.lesson.id)
                                            }
                                            is PathItem.ReviewStop -> p.copy(completedReviews = p.completedReviews + item.n)
                                        }
                                        update(p.withActivity(res.xp))
                                    },
                                )
                            }
                            is Screen.Study -> StudyScreen(
                                cards = s.cards, title = s.title, cram = s.cram, repo = repo,
                                stateOf = { c -> s.deck?.let { progress.deck(it)[c.id] } ?: progress.cards[c.id] },
                                onRate = { card, rating ->
                                    if (!s.cram) {
                                        val d = s.deck
                                        if (d != null) {
                                            val st = progress.deck(d)[card.id] ?: CardState()
                                            update(progress.withDeck(d, progress.deck(d) + (card.id to Srs.schedule(st, rating))))
                                        } else {
                                            val st = progress.cards[card.id] ?: CardState()
                                            update(progress.copy(cards = progress.cards + (card.id to Srs.schedule(st, rating))))
                                        }
                                    }
                                },
                                onDone = { reviewed ->
                                    if (reviewed > 0) update(if (s.deck != null) progress.copy(myXp = progress.myXp + reviewed) else progress.withActivity(reviewed))
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
