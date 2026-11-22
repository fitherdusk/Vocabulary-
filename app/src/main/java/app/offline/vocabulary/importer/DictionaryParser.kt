package app.offline.vocabulary.importer

import app.offline.vocabulary.R
import app.offline.vocabulary.VocabularyProblem
import app.offline.vocabulary.requireInput
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

object DictionaryParser {
    fun parse(filename: String, input: InputStream): ParsedDocument {
        val ext = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val memory = ImportMemory()
        try {
            return when (ext) {
                "docx" -> readDocx(input, memory)
                "pdf" -> readPdf(input, memory)
                "txt", "tsv", "csv", "json", "jsonl", "ndjson", "md", "markdown" -> {
                    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    val text = InputStreamReader(memory.bounded(input, memory.textBytes), decoder).use { it.readText() }.removePrefix("\uFEFF")
                    requireInput(text.isNotBlank(), R.string.error_empty_file)
                    when (ext) {
                        "csv", "tsv" -> delimitedTable(text, ext)
                        "md", "markdown" -> markdownTable(text, memory)
                        "json", "jsonl", "ndjson" -> jsonTable(text, ext != "json", memory)
                        else -> plainTable(text)
                    }
                }
                else -> throw VocabularyProblem(R.string.error_file_type)
            }
        } catch (e: OutOfMemoryError) {
            memory.tooLarge()
        } catch (e: CharacterCodingException) {
            throw VocabularyProblem(R.string.error_utf8, cause = e)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("scratch file memory exceeded")) memory.tooLarge()
            val message = when (ext) {
                "json", "jsonl", "ndjson" -> R.string.error_invalid_json
                "pdf" -> R.string.error_invalid_pdf
                "docx" -> R.string.error_invalid_docx
                else -> R.string.error_invalid_file
            }
            throw VocabularyProblem(message, cause = e)
        }
    }
}

private fun jsonValue(text: String): Any? {
    val tokenizer = JSONTokener(text)
    val value = tokenizer.nextValue()
    if (tokenizer.nextClean() != '\u0000') throw JSONException("Unexpected trailing content")
    return value
}

private fun jsonTable(text: String, lines: Boolean, memory: ImportMemory): ParsedDocument {
    val values = if (lines) {
        text.lineSequence().mapIndexedNotNull { i, line ->
            if (line.isBlank()) null else try {
                jsonValue(line)
            } catch (e: JSONException) {
                throw VocabularyProblem(R.string.error_json_line, i + 1, cause = e)
            }
        }.toList()
    } else {
        val root = jsonValue(text)
        val records = if (root is JSONObject) {
            listOf("words", "entries", "records", "data", "слова", "записи").firstNotNullOfOrNull(root::optJSONArray)
                ?: root
        } else root
        if (records is JSONArray) List(records.length(), records::opt) else listOf(records)
    }
    requireInput(values.isNotEmpty(), R.string.error_json_empty)
    if (values.any { it is JSONObject }) {
        requireInput(values.all { it is JSONObject }, R.string.error_json_uniform)
        val objects = values.filterIsInstance<JSONObject>()
        val keys = linkedSetOf<String>()
        for (obj in objects) keys.addAll(obj.keys().asSequence().toList())
        memory.checkTable(objects.size, keys.size)
        val columns = keys.toList()
        return ParsedDocument.objects(columns, objects.map { obj -> columns.map { asText(obj.opt(it)) } })
    }
    if (values.any { it is JSONArray }) {
        requireInput(values.all { it is JSONArray }, R.string.error_json_arrays)
        return ParsedDocument.table(values.filterIsInstance<JSONArray>().map { row ->
            List(row.length()) { asText(row.opt(it)) }
        })
    }
    requireInput(values.all { it is String || it == JSONObject.NULL }, R.string.error_json_shape)
    return ParsedDocument.objects(listOf("word"), values.map { listOf(asText(it)) })
}

private fun asText(value: Any?): String =
    if (value == null || value == JSONObject.NULL) "" else value.toString()
