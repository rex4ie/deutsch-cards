package com.flashcards.german

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class Card(
    val en: String, val de: String, val section: String = "", val audio: String,
    val exDe: String = "", val exEn: String = "", val note: String = "",
) {
    val id: String get() = audio
    val hasAudio: Boolean get() = !audio.startsWith("my:")
}

/** The learner's own lessons (grammar, vocab, …) generated from local notes into assets/my_lessons.json. */
@Serializable
data class MyCard(val front: String, val back: String, val note: String = "")

@Serializable
data class MyLesson(val id: String, val title: String, val date: String = "", val topic: String = "", val cards: List<MyCard>) {
    fun asCards(): List<Card> = cards.mapIndexed { i, c ->
        Card(en = c.back, de = c.front, section = topic, audio = "my:$id:$i", note = c.note)
    }
}

@Serializable
data class Lesson(
    val id: Int, val page: Int, val title: String, val titleDe: String = "", val cards: List<Card>,
    val part: Int = 1, val parts: Int = 1,
) {
    val unitTitle: String get() = title.substringBefore(" & ")
}

/** How the learner has done on a word inside lessons (drives adaptive review lessons). */
@Serializable
data class WordStat(val right: Int = 0, val wrong: Int = 0, val last: Long = 0L)

/** One stop on the learning path: a regular lesson or an adaptive review. */
sealed interface PathItem {
    val key: String
    data class LessonStop(val lesson: Lesson, val unit: Int) : PathItem { override val key get() = "L${lesson.id}" }
    data class ReviewStop(val n: Int, val unit: Int, val afterLesson: Int) : PathItem { override val key get() = "R$n" }
}

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
    val completedReviews: Set<Int> = emptySet(),
    val words: Map<String, WordStat> = emptyMap(),
    /** Scheduling for "My lessons" cards, kept apart from the Collins deck. */
    val myCards: Map<String, CardState> = emptyMap(),
    /** der/die/das game: per-noun results and best sprint score, kept apart from everything else. */
    val genderStats: Map<String, WordStat> = emptyMap(),
    val bestSprint: Int = 0,
    val myXp: Int = 0,
    val xp: Int = 0,
    val streak: Int = 0,
    val lastStudyDay: Long = -1,
    val schema: Int = 1,
) {
    /** v1 used one lesson per PDF page; v2 splits pages, so recompute which lessons are done. */
    fun migrate(repo: Repo): Progress = if (schema >= 2) this else copy(
        schema = 2,
        completedLessons = repo.lessons.filter { l -> l.cards.all { it.id in cards } }.map { it.id }.toSet(),
    )
}

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
    private fun asset(name: String): String? =
        runCatching { ctx.assets.open(name).bufferedReader(Charsets.UTF_8).readText() }.getOrNull()

    private val examples: Map<String, List<String>> =
        asset("examples.json")?.let { json.decodeFromString<Map<String, List<String>>>(it) } ?: emptyMap()

    private val pages: List<Lesson> =
        json.decodeFromString<List<Lesson>>(asset("lessons.json")!!).map { l ->
            l.copy(cards = l.cards.map { c ->
                examples[c.id]?.let { e -> c.copy(exDe = e[0], exEn = e.getOrElse(1) { "" }) } ?: c
            })
        }

    /**
     * The learner's own lessons. Fetched on demand from the repo (no app update needed),
     * cached on the phone, with the copy bundled in the APK as a fallback.
     */
    private val myCache = File(ctx.filesDir, "my_lessons.json")
    var myLessons: List<MyLesson> by androidx.compose.runtime.mutableStateOf(
        (runCatching { myCache.readText() }.getOrNull() ?: asset("my_lessons.json"))
            ?.let { runCatching { json.decodeFromString<List<MyLesson>>(it) }.getOrNull() } ?: emptyList()
    )
        private set

    /** Downloads the latest my_lessons.json; returns the number of lessons, or throws. */
    suspend fun refreshMyLessons(): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = java.net.URL("https://raw.githubusercontent.com/${Updater.REPO}/main/app/src/main/assets/my_lessons.json?t=${System.currentTimeMillis()}")
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000; useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (conn.responseCode != 200) error("GitHub answered ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val parsed = json.decodeFromString<List<MyLesson>>(text)
            myCache.writeText(text)
            myLessons = parsed
            parsed.size
        } finally { conn.disconnect() }
    }

    /** Each PDF page split into bite-sized lessons of at most [MAX_WORDS] words. */
    val lessons: List<Lesson> = run {
        var id = 0
        pages.flatMap { page ->
            val parts = (page.cards.size + MAX_WORDS - 1) / MAX_WORDS
            val size = (page.cards.size + parts - 1) / parts
            page.cards.chunked(size).mapIndexed { i, chunk ->
                page.copy(id = ++id, cards = chunk, part = i + 1, parts = parts)
            }
        }
    }
    val allCards: List<Card> = lessons.flatMap { it.cards }

    /** Lessons with an adaptive review stop after every [REVIEW_EVERY] lessons. */
    val path: List<PathItem> = run {
        val out = mutableListOf<PathItem>()
        var unit = 0; var review = 0
        lessons.forEachIndexed { i, l ->
            if (i > 0 && l.unitTitle != lessons[i - 1].unitTitle) unit++
            out += PathItem.LessonStop(l, unit)
            if ((i + 1) % REVIEW_EVERY == 0) out += PathItem.ReviewStop(++review, unit, l.id)
        }
        out
    }

    companion object { const val MAX_WORDS = 9; const val REVIEW_EVERY = 4 }
    private val file = File(ctx.filesDir, "progress.json")

    fun load(): Progress = runCatching { json.decodeFromString<Progress>(file.readText()) }.getOrDefault(Progress())
    fun save(p: Progress) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(p))
        tmp.renameTo(file)
    }

    private var player: MediaPlayer? = null
    fun play(card: Card) {
        if (!card.hasAudio) return
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

/** A noun for the der/die/das game. [card] is kept so audio can be played when it exists. */
data class GNoun(val id: String, val article: String, val word: String, val en: String, val card: Card?, val mine: Boolean)

private val articleRx = Regex("""^(der|die|das) (\S+(?: \S+)?)$""")
private val pluralEn = Regex("""(?i)([^su]s|es)$""")

/** Picks single singular nouns ("die Schule", "der platte Reifen") out of a list of cards. */
fun nounsFrom(cards: List<Card>, mine: Boolean): List<GNoun> = cards.mapNotNull { c ->
    val de = c.de.trim()
    if ('/' in de) return@mapNotNull null
    val m = articleRx.matchEntire(de) ?: return@mapNotNull null
    val word = m.groupValues[2]
    if (!word.split(' ').last().first().isUpperCase()) return@mapNotNull null   // verb phrases
    if (!mine && pluralEn.containsMatchIn(c.en.trim())) return@mapNotNull null   // likely plural
    GNoun(c.id, m.groupValues[1], word, c.en, c.takeIf { it.hasAudio }, mine)
}.distinctBy { it.article + it.word }

/** Common gender rules, used as hints after a wrong answer. */
fun genderHint(word: String): String? {
    val w = word.split(' ').last().lowercase()
    return when {
        w.endsWith("ung") || w.endsWith("heit") || w.endsWith("keit") || w.endsWith("schaft") ||
            w.endsWith("tion") || w.endsWith("ion") || w.endsWith("tät") || w.endsWith("ik") || w.endsWith("ie") -> "Nouns ending in -ung, -heit, -keit, -schaft, -ion, -tät, -ik, -ie are always die."
        w.endsWith("chen") || w.endsWith("lein") -> "-chen and -lein make every noun das."
        w.endsWith("ment") || w.endsWith("um") -> "Most nouns ending in -ment or -um are das."
        w.endsWith("ling") || w.endsWith("ismus") || w.endsWith("ist") || w.endsWith("or") -> "Nouns ending in -ling, -ismus, -ist, -or are usually der."
        w.endsWith("e") -> "Most nouns ending in -e are die (with exceptions like der Junge, der Käse)."
        else -> null
    }
}

fun Progress.isDone(item: PathItem) = when (item) {
    is PathItem.LessonStop -> item.lesson.id in completedLessons
    is PathItem.ReviewStop -> item.n in completedReviews
}

/** Words the learner struggles with most: lesson mistakes, SRS lapses, overdue cards. */
fun Progress.weakestCards(repo: Repo, n: Int, upToLesson: Int): List<Card> {
    val now = System.currentTimeMillis()
    val seen = repo.lessons.filter { it.id <= upToLesson && it.id in completedLessons }.flatMap { it.cards }
    return seen.map { c ->
        val w = words[c.id] ?: WordStat()
        val s = cards[c.id]
        val score = w.wrong * 3.0 - w.right * 0.4 + (s?.lapses ?: 0) * 2.0 +
            (if (s != null && s.due <= now) 1.5 else 0.0) + Math.random()
        c to score
    }.sortedByDescending { it.second }.take(n).map { it.first }
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
