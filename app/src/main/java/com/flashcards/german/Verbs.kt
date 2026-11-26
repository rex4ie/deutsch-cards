package com.flashcards.german

import kotlinx.serialization.Serializable

/**
 * One verb for the verb-forms game, from assets/verbs.json (built by tools/build_verbs.py).
 * [prt] holds the Präteritum for ich, du, er, wir, ihr, sie. [aux] is "h" (hat), "s" (ist),
 * or both ("s/h": the first is the usual one, either is accepted). [src] lists where the verb
 * comes from: c = Collins, k = Class, f = frequent verbs.
 */
@Serializable
data class Verb(
    val id: String, val inf: String, val en: String, val aux: String, val pp: String,
    val prt: List<String>, val src: String = "", val irr: Boolean = false, val ctx: String = "",
) {
    val mainAux: Char get() = aux.first()
    /** "fahren – fuhr – ist gefahren" */
    val parts: String get() = "$inf – ${prt[2]} – ${if (mainAux == 's') "ist" else "hat"} $pp"
}

enum class VerbForm(val title: String, val key: String) { Praeteritum("Präteritum", "prt"), Perfekt("Perfekt", "pp") }

/** Which forms a round asks for; remembered in Progress.verbForms. */
enum class VerbFormChoice(val label: String) { Both("Both"), Praeteritum("Präteritum"), Perfekt("Perfekt") }

/** One question: a verb, a form and a person (index into [PERSONS]). Stats are kept per verb and form. */
data class VerbQ(val verb: Verb, val form: VerbForm, val person: Int) {
    val statId: String get() = "${verb.id}:${form.key}"

    /** The answer as shown: "fuhrst" or "bist gefahren". */
    val answer: String get() = when (form) {
        VerbForm.Praeteritum -> verb.prt[person]
        VerbForm.Perfekt -> "${auxOf(verb.mainAux, person)} ${verb.pp}"
    }
}

val PERSONS = listOf("ich", "du", "er/sie/es", "wir", "ihr", "sie/Sie")
private val HABEN = listOf("habe", "hast", "hat", "haben", "habt", "haben")
private val SEIN = listOf("bin", "bist", "ist", "sind", "seid", "sind")
fun auxOf(a: Char, person: Int) = if (a == 's') SEIN[person] else HABEN[person]

private val pronouns = setOf("ich", "du", "er", "sie", "es", "wir", "ihr", "man")
private val reflexive = setOf("sich", "mich", "dich", "uns", "euch")

/** Lowercase, umlauts spelled out, pronoun and reflexive pronoun dropped: "Ich habe mich gewaschen" -> "habe gewaschen". */
private fun verbNorm(s: String): List<String> =
    normalize(s).split(' ').filter { it.isNotEmpty() }.let { w ->
        (if (w.size > 1 && w.first() in pronouns) w.drop(1) else w).filter { it !in reflexive }
    }

/** du forms are written both ways for some verbs (du schlosst / schlossest): accept either. */
private fun duLoose(w: List<String>) = w.map { it.replace(Regex("est$"), "st") }

enum class VerbVerdict { Right, WrongAux, Wrong }

fun checkVerb(q: VerbQ, input: String): VerbVerdict {
    val got = verbNorm(input)
    if (got.isEmpty()) return VerbVerdict.Wrong
    return when (q.form) {
        VerbForm.Praeteritum -> {
            val want = verbNorm(q.verb.prt[q.person])
            if (got == want || (q.person == 1 && duLoose(got) == duLoose(want))) VerbVerdict.Right else VerbVerdict.Wrong
        }
        VerbForm.Perfekt -> {
            val pp = verbNorm(q.verb.pp)
            val auxes = q.verb.aux.split('/').map { normalize(auxOf(it.first(), q.person)) }
            if (got.size != 1 + pp.size || got.drop(1) != pp) VerbVerdict.Wrong
            else if (got.first() in auxes) VerbVerdict.Right
            else if (got.first() in listOf(normalize(auxOf('h', q.person)), normalize(auxOf('s', q.person)))) VerbVerdict.WrongAux
            else VerbVerdict.Wrong
        }
    }
}

enum class VerbMode(val title: String) {
    Round("Round of 20"), Irregular("Irregular verbs"), Weak("Weak spots"), Class("Class verbs"), Collins("Collins verbs"),
}

/** A verb form the learner knows reliably: right at least 3 times and mostly right. */
fun WordStat.knownForm() = right >= 3 && right >= wrong * 3

fun formsFor(choice: VerbFormChoice) = when (choice) {
    VerbFormChoice.Both -> VerbForm.entries.toList()
    VerbFormChoice.Praeteritum -> listOf(VerbForm.Praeteritum)
    VerbFormChoice.Perfekt -> listOf(VerbForm.Perfekt)
}

fun verbPool(mode: VerbMode, verbs: List<Verb>): List<Verb> = when (mode) {
    VerbMode.Irregular -> verbs.filter { it.irr }
    VerbMode.Class -> verbs.filter { 'k' in it.src }
    VerbMode.Collins -> verbs.filter { 'c' in it.src }
    else -> verbs
}

/**
 * Builds a round of [size] questions. Unseen and shaky forms come first, irregular verbs a bit
 * more often (regular ones all follow one pattern), known forms only now and then.
 */
fun pickVerbQs(mode: VerbMode, verbs: List<Verb>, choice: VerbFormChoice, stats: Map<String, WordStat>, size: Int = 20): List<VerbQ> {
    val forms = formsFor(choice)
    val all = verbPool(mode, verbs).flatMap { v -> forms.map { f -> v to f } }
    fun id(p: Pair<Verb, VerbForm>) = "${p.first.id}:${p.second.key}"
    val chosen = if (mode == VerbMode.Weak) {
        all.filter { stats[id(it)]?.let { s -> s.wrong > 0 && !s.knownForm() } == true }
            .sortedByDescending { val s = stats[id(it)]!!; s.wrong - s.right * 0.5 + Math.random() }
    } else {
        all.sortedBy { p ->
            val s = stats[id(p)]
            val base = if (s == null) 0.6 else if (s.knownForm()) 2.0 else 0.0
            base + (if (p.first.irr) 0.0 else 0.5) + Math.random()
        }
    }
    return chosen.distinctBy { it.first.id }.take(size).shuffled().map { (v, f) -> VerbQ(v, f, PERSONS.indices.random()) }
}

fun weakVerbForms(verbs: List<Verb>, stats: Map<String, WordStat>): Int {
    val ids = verbs.flatMap { v -> VerbForm.entries.map { "${v.id}:${it.key}" } }.toSet()
    return stats.count { (k, s) -> k in ids && s.wrong > 0 && !s.knownForm() }
}
