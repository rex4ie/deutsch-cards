package com.flashcards.german

import android.content.Context
import android.media.MediaPlayer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class Card(val en: String, val de: String, val section: String = "", val audio: String) {
    val id: String get() = audio
}

@Serializable
data class Lesson(val id: Int, val page: Int, val title: String, val titleDe: String = "", val cards: List<Card>)

/** Anki-style (SM-2) scheduling state for one card. */
@Serializable
data class CardState(
    val reps: Int = 0,
    val intervalDays: Double = 0.0,
    val ease: Double = 2.5,
    val due: Long = 0L,
    val lapses: Int = 0,
)

@Serializable
data class Progress(
    val cards: Map<String, CardState> = emptyMap(),
    val completedLessons: Set<Int> = emptySet(),
    val xp: Int = 0,
    val streak: Int = 0,
    val lastStudyDay: Long = -1,
)

enum class Rating { Again, Hard, Good, Easy }

object Srs {
    const val DAY = 86_400_000L

    fun schedule(s: CardState, r: Rating, now: Long = System.currentTimeMillis()): CardState = when (r) {
        Rating.Again -> s.copy(reps = 0, intervalDays = 0.0, ease = max(1.3, s.ease - 0.2), lapses = s.lapses + 1, due = now + 60_000)
        Rating.Hard -> {
            val iv = max(1.0, s.intervalDays * 1.2)
            s.copy(reps = s.reps + 1, intervalDays = iv, ease = max(1.3, s.ease - 0.15), due = now + (iv * DAY).toLong())
        }
        Rating.Good -> {
            val iv = goodInterval(s)
            s.copy(reps = s.reps + 1, intervalDays = iv, due = now + (iv * DAY).toLong())
        }
        Rating.Easy -> {
            val iv = max(4.0, goodInterval(s) * 1.3)
            s.copy(reps = s.reps + 1, intervalDays = iv, ease = s.ease + 0.15, due = now + (iv * DAY).toLong())
        }
    }

    private fun goodInterval(s: CardState) = when (s.reps) {
        0 -> 1.0
        1 -> 3.0
        else -> max(s.intervalDays + 1, s.intervalDays * s.ease)
    }

    fun label(s: CardState, r: Rating): String {
        val n = schedule(s, r, 0L)
        if (r == Rating.Again) return "1m"
        val d = n.intervalDays
        return when {
            d < 30 -> "${d.roundToInt()}d"
            d < 365 -> "${(d / 30).roundToInt()}mo"
            else -> "%.1fy".format(d / 365)
        }
    }
}

class Repo(private val ctx: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    val lessons: List<Lesson> =
        json.decodeFromString(ctx.assets.open("lessons.json").bufferedReader(Charsets.UTF_8).readText())
    val allCards: List<Card> = lessons.flatMap { it.cards }
    private val file = File(ctx.filesDir, "progress.json")

    fun load(): Progress = runCatching { json.decodeFromString<Progress>(file.readText()) }.getOrDefault(Progress())
    fun save(p: Progress) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(p))
        tmp.renameTo(file)
    }

    private var player: MediaPlayer? = null
    fun play(card: Card) {
        runCatching {
            player?.release()
            val afd = ctx.assets.openFd("audio/${card.audio}")
            player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare(); start()
            }
        }
    }
}

fun today(): Long = (System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())) / Srs.DAY

fun Progress.withActivity(xpGain: Int): Progress {
    val t = today()
    val newStreak = when (lastStudyDay) {
        t -> streak
        t - 1 -> streak + 1
        else -> 1
    }
    return copy(xp = xp + xpGain, streak = newStreak, lastStudyDay = t)
}

/** Normalise an answer for lenient comparison in typing exercises. */
fun normalize(s: String): String = s.lowercase()
    .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    .replace(Regex("\\b(mpl|fpl|npl)\\b"), "")
    .replace(Regex("[^a-z0-9 ]"), " ")
    .replace(Regex("\\s+"), " ").trim()

fun answerMatches(input: String, de: String): Boolean {
    val i = normalize(input)
    if (i.isEmpty()) return false
    val variants = de.split(" / ").map { normalize(it) } + normalize(de)
    return i in variants
}
