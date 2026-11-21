package app.offline.vocabulary.data

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import kotlin.random.Random

/** Index equivalent glosses so that a synonym cannot appear as a wrong answer. */
private class QuizRelations(pairs: Iterable<Pair<String, String>>) {
    private val translationsByWord = mutableMapOf<String, MutableSet<String>>()
    private val wordsByTranslation = mutableMapOf<String, MutableSet<String>>()
    private val translationsByVariant = mutableMapOf<String, MutableSet<String>>()
    private val variantsByTranslation = mutableMapOf<String, Set<String>>()

    init {
        for ((word, translation) in pairs) {
            if (translation.isEmpty()) continue
            translationsByWord.getOrPut(word) { mutableSetOf() }.add(translation)
            wordsByTranslation.getOrPut(translation) { mutableSetOf() }.add(word)
        }
        for (translation in wordsByTranslation.keys) {
            val variants = translationVariants(translation)
            variantsByTranslation[translation] = variants
            for (variant in variants) {
                translationsByVariant.getOrPut(variant) { mutableSetOf() }.add(translation)
            }
        }
    }

    private fun related(translation: String): Set<String> = variantsByTranslation[translation].orEmpty()
        .flatMapTo(mutableSetOf()) { translationsByVariant[it].orEmpty() }

    fun excluded(prompt: String, reverse: Boolean): Set<String> = if (reverse) {
        related(prompt).flatMapTo(mutableSetOf()) { wordsByTranslation[it].orEmpty() }
    } else translationsByWord[prompt].orEmpty().flatMapTo(mutableSetOf(), ::related)

    fun hasOptions(prompt: String, reverse: Boolean): Boolean {
        val answers = if (reverse) translationsByWord.size else wordsByTranslation.size
        return answers >= 4 && answers - excluded(prompt, reverse).size >= 3
    }

    fun anyQuiz(reverse: Boolean): Boolean {
        val prompts = if (reverse) wordsByTranslation.keys else translationsByWord.keys
        return prompts.any { hasOptions(it, reverse) }
    }
}

internal fun storeCapabilities(db: SQLiteDatabase, id: Long, uniquePairs: Set<Pair<String, String>>) {
    val relations = QuizRelations(uniquePairs)
    db.execSQL("INSERT INTO capabilities(dictionary_id,translated,quiz_forward,quiz_reverse) VALUES (?,?,?,?)",
        arrayOf(id, uniquePairs.any { it.second.isNotBlank() }.flag(),
            relations.anyQuiz(reverse = false).flag(), relations.anyQuiz(reverse = true).flag()))
}

internal fun rebuildCapabilities(db: SQLiteDatabase) {
    db.rows("SELECT id FROM dictionaries") { it.getLong(0) }.forEach { id ->
        val pairs = mutableSetOf<Pair<String, String>>()
        db.rawQuery("SELECT word, translation FROM words WHERE dictionary_id=?", arrayOf(id.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                pairs += normalizeAnswer(cursor.getString(0)) to normalizeAnswer(cursor.getString(1))
            }
        }
        storeCapabilities(db, id, pairs)
    }
}

internal data class Candidate(val id: Long, val word: String, val translation: String, val seen: Boolean, val due: Long)

internal fun decodeOptions(encoded: String): List<String> {
    val array = JSONArray(encoded)
    return List(array.length()) { array.getString(it) }
}

/** The pool keeps only the columns needed to choose a session, not examples or cumulative answer history. */
internal class StudyPool(db: SQLiteDatabase, id: Long, requestedReverse: Boolean) {
    private val words = db.rows("SELECT id,word,translation,attempts,due FROM words WHERE dictionary_id=? ORDER BY id", id) {
        Candidate(it.getLong(0), it.getString(1), it.getString(2), it.getInt(3) > 0, it.getLong(4))
    }
    val reverse = requestedReverse && words.any { it.translation.isNotBlank() }
    private fun prompt(w: Candidate) = normalizeAnswer(if (reverse) w.translation else w.word)
    private fun answer(w: Candidate) = if (reverse) w.word else w.translation
    private val answersByPrompt = mutableMapOf<String, MutableMap<String,String>>()
    private val uniqueAnswers = mutableMapOf<String, String>()
    init {
        words.filter { it.translation.isNotBlank() }.forEach { w ->
            val value = answer(w)
            val key = normalizeAnswer(value)
            uniqueAnswers.putIfAbsent(key, value)
            answersByPrompt.getOrPut(prompt(w)) { mutableMapOf() }.putIfAbsent(key, value)
        }
    }

    private val answers by lazy { uniqueAnswers.entries.shuffled() }
    private val relations = QuizRelations(words.map { normalizeAnswer(it.word) to normalizeAnswer(it.translation) })
    private fun quizEligible(w: Candidate) =
        w.translation.isNotBlank() && relations.hasOptions(prompt(w), reverse)

    val availability = Availability(words.isNotEmpty(), words.any(::quizEligible), uniqueAnswers.isNotEmpty(),
        words.any { it.seen && (!reverse || it.translation.isNotBlank()) })

    fun queue(settings: StudySettings, now: Long): List<Candidate> {
        val eligible = words.filter {
            when (settings.mode) {
                StudyMode.QUIZ -> quizEligible(it)
                StudyMode.TYPING -> it.translation.isNotBlank()
                else -> !reverse || it.translation.isNotBlank()
            }
        }
        val reviews = eligible.asSequence().filter { it.seen && (settings.mode == StudyMode.REVIEW || it.due <= now) }
            .sortedBy { it.due }.take(30).toList()
        if (settings.mode == StudyMode.REVIEW) return reviews
        val new = eligible.filterNot { it.seen }
        return reviews + (if (settings.randomOrder) new.shuffled() else new).take(settings.newPerSession)
    }

    /** For quizzes, exclude every valid translation of the prompt when selecting distractors. */
    fun optionsJson(w: Candidate, mode: StudyMode): String {
        val acceptable = answersByPrompt[prompt(w)].orEmpty()
        val options = when (mode) {
            StudyMode.QUIZ -> {
                val excluded = relations.excluded(prompt(w), reverse)
                val choices = mutableListOf(answer(w))
                val start = Random.nextInt(answers.size)
                for (offset in answers.indices) {
                    val entry = answers[(start + offset) % answers.size]
                    if (entry.key !in excluded) choices.add(entry.value)
                    if (choices.size == 4) break
                }
                choices.shuffled()
            }
            StudyMode.TYPING -> acceptable.values.toList()
            else -> emptyList()
        }
        return JSONArray(options).toString()
    }
}
