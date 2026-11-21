package app.offline.vocabulary.data

import java.text.Normalizer
import java.util.Locale

enum class StudyMode { CARDS, QUIZ, TYPING, REVIEW }
enum class Rating { AGAIN, HARD, KNOWN }

data class StudySettings(
    val newPerSession: Int = 10,
    val dailyGoal: Int = 20,
    val mode: StudyMode = StudyMode.CARDS,
    val reverse: Boolean = false,
    val animations: Boolean = true,
    val fontSize: Int = 34,
    val randomOrder: Boolean = false
)

data class DictionaryInfo(val id: Long, val name: String, val total: Int, val learned: Int)

data class Stats(
    val total: Int = 0, val newWords: Int = 0, val learning: Int = 0,
    val learned: Int = 0, val due: Int = 0, val sessions: Int = 0,
    val streak: Int = 0, val today: Int = 0, val masteryPercent: Double = 0.0
)

data class Availability(
    val cards: Boolean = false, val quiz: Boolean = false,
    val typing: Boolean = false, val review: Boolean = false
) {
    fun allows(mode: StudyMode) = when (mode) {
        StudyMode.CARDS -> cards
        StudyMode.QUIZ -> quiz
        StudyMode.TYPING -> typing
        StudyMode.REVIEW -> review
    }
}

data class LibrarySnapshot(
    val dictionaries: List<DictionaryInfo>,
    val selectedId: Long?, val settings: StudySettings, val stats: Stats, val availability: Availability
)

data class StudySession(
    val id: Long, val index: Int, val total: Int, val mode: StudyMode,
    val reverse: Boolean, val card: StudyCard
)

data class StudyCard(
    val wordId: Long, val prompt: String, val answer: String,
    val transcription: String, val example: String, val extra: String, val options: List<String>,
    val acceptedAnswers: List<String> = listOf(answer)
) {
    fun acceptsTypedAnswer(response: String, reverse: Boolean): Boolean {
        val typed = normalizeAnswer(response)
        if (typed.isEmpty()) return false
        return acceptedAnswers.any { candidate ->
            if (reverse) normalizeAnswer(candidate) == typed else typed in translationVariants(candidate)
        }
    }
}

private val usageNote = Regex("""\s*\([^()]*\)\s*$""")

internal fun translationVariants(value: String): Set<String> = buildSet {
    add(normalizeAnswer(value))
    value.split(';').forEach { part ->
        add(normalizeAnswer(part))
        val withoutNote = normalizeAnswer(part.replace(usageNote, ""))
        if (withoutNote.isNotEmpty()) add(withoutNote)
    }
    remove("")
}

private val spaces = Regex("[\\s\\p{Z}]+")
fun normalizeAnswer(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
    .replace(spaces, " ").trim().lowercase(Locale.ROOT)
