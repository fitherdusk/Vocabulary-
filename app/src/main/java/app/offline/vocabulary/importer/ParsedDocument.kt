package app.offline.vocabulary.importer

import java.text.Normalizer
import java.util.Locale

data class Entry(
    val word: String,
    val translation: String = "",
    val transcription: String = "",
    val example: String = "",
    val extra: String = ""
)

data class Mapping(
    val word: Int,
    val translation: Int = -1,
    val transcription: Int = -1,
    val example: Int = -1
)

data class ImportResult(val entries: List<Entry>, val duplicates: Int, val empty: Int)

class ParsedDocument private constructor(
    private val sourceRows: List<List<String>>,
    private val objectKeys: List<String>?,
    val firstRowIsHeader: Boolean
) {
    val canChangeHeader: Boolean = objectKeys == null
    private val firstContent = sourceRows.indexOfFirst { row -> row.any(String::isNotBlank) }
    private val namedHeader = objectKeys ?: if (firstRowIsHeader) sourceRows.getOrNull(firstContent) else null
    val rows: List<List<String>> = if (firstRowIsHeader && firstContent >= 0) {
        sourceRows.filterIndexed { index, _ -> index != firstContent }
    } else sourceRows

    private val width = maxOf(namedHeader?.size ?: 0, sourceRows.maxOfOrNull { it.size } ?: 0)
    val headers: List<String> = List(width) {
        namedHeader?.getOrNull(it)?.trim()?.takeIf(String::isNotEmpty) ?: "Столбец ${it + 1}"
    }
    val suggested: Mapping = suggestMapping()

    fun withHeader(firstRowIsHeader: Boolean): ParsedDocument =
        if (!canChangeHeader || this.firstRowIsHeader == firstRowIsHeader) this
        else ParsedDocument(sourceRows, null, firstRowIsHeader)

    fun prepare(mapping: Mapping): ImportResult {
        require(mapping.word in headers.indices) { "Выберите столбец со словом." }
        val selected = listOf(mapping.word, mapping.translation, mapping.transcription, mapping.example)
        require(selected.all { it == -1 || it in headers.indices }) { "Выбран несуществующий столбец." }
        val used = selected.filter { it >= 0 }.toSet()
        require(used.size == selected.count { it >= 0 }) { "Для каждого поля выберите отдельный столбец." }

        val seen = HashSet<Pair<String, String>>()
        val entries = ArrayList<Entry>()
        var duplicates = 0
        var empty = 0
        for (row in rows) {
            fun value(column: Int) = row.getOrNull(column).orEmpty().trim()
            val word = value(mapping.word)
            val translation = value(mapping.translation)
            when {
                word.isBlank() -> empty++
                !seen.add(normalized(word) to normalized(translation)) -> duplicates++
                else -> {
                    val extra = row.indices.filter { it !in used && row[it].isNotBlank() }
                        .joinToString("\n") { "${headers[it]}: ${value(it)}" }
                    entries.add(Entry(word, translation, value(mapping.transcription), value(mapping.example), extra))
                }
            }
        }
        return ImportResult(entries, duplicates, empty)
    }

    private fun suggestMapping(): Mapping {
        val recognized = namedHeader.orEmpty().map(::role)
        if (recognized.all { it == null }) {
            fun positional(n: Int) = if (n < width) n else -1
            return Mapping(positional(0), positional(1), positional(2), positional(3))
        }
        return Mapping(
            recognized.indexOf("word").takeIf { it >= 0 } ?: recognized.indexOf(null),
            recognized.indexOf("translation"),
            recognized.indexOf("transcription"),
            recognized.indexOf("example")
        )
    }

    companion object {
        internal fun table(rows: List<List<String>>, headerHint: Boolean? = null): ParsedDocument {
            val first = rows.firstOrNull { row -> row.any(String::isNotBlank) }
            require(first != null) { "В файле нет записей для импорта." }
            val knownFields = first.map(::role)
            val hasHeader = headerHint ?: (knownFields.count { it != null } >= 2 || knownFields.firstOrNull() == "word")
            return ParsedDocument(rows, null, hasHeader)
        }

        internal fun objects(keys: List<String>, rows: List<List<String>>): ParsedDocument {
            require(keys.isNotEmpty() && rows.isNotEmpty()) { "В файле нет записей для импорта." }
            return ParsedDocument(rows, keys, false)
        }
    }
}

private val spaced = Regex("[\\p{Z}\\s]+")
private fun normalized(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT).replace(spaced, " ")

private val fields = mapOf(
    "word" to setOf("word", "term", "lemma", "source", "front", "слово", "термин", "оригинал"),
    "translation" to setOf("translation", "meaning", "definition", "target", "back", "перевод", "значение"),
    "transcription" to setOf("transcription", "phonetic", "pronunciation", "ipa", "транскрипция", "произношение", "произношение по-русски", "как читать по-русски", "reading_ru", "pronunciation_ru"),
    "example" to setOf("example", "examples", "sentence", "context", "пример", "примеры", "контекст")
)

internal fun role(header: String): String? {
    val label = normalized(header)
    return fields.entries.firstOrNull { label in it.value }?.key
}
