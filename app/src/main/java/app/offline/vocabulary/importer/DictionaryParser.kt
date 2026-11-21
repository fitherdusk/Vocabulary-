package app.offline.vocabulary.importer

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
                    require(text.isNotBlank()) { "Файл пуст." }
                    when (ext) {
                        "csv", "tsv" -> delimitedTable(text, ext)
                        "md", "markdown" -> markdownTable(text, memory)
                        "json", "jsonl", "ndjson" -> jsonTable(text, ext != "json", memory)
                        else -> plainTable(text)
                    }
                }
                else -> throw IllegalArgumentException(
                    "Выберите файл TXT, CSV, TSV, JSON, JSONL, Markdown, DOCX или PDF."
                )
            }
        } catch (e: OutOfMemoryError) {
            memory.tooLarge()
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Не удалось прочитать текст. Сохраните файл в кодировке UTF-8.", e)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("scratch file memory exceeded")) memory.tooLarge()
            val message = when (ext) {
                "json", "jsonl", "ndjson" -> "Нарушена структура JSON. Проверьте скобки, кавычки и запятые."
                "pdf" -> "Не удалось прочитать PDF. Проверьте, открывается ли файл без пароля."
                "docx" -> "Не удалось прочитать DOCX. Проверьте, открывается ли документ в редакторе."
                else -> "Не удалось прочитать файл. Проверьте его формат и доступ к файлу."
            }
            throw IllegalArgumentException(message, e)
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
                throw IllegalArgumentException("Строка ${i + 1}: нарушена структура JSON.", e)
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
    require(values.isNotEmpty()) { "В файле JSON нет записей." }
    if (values.any { it is JSONObject }) {
        require(values.all { it is JSONObject }) { "Записи JSON должны иметь одну структуру." }
        val objects = values.filterIsInstance<JSONObject>()
        val keys = linkedSetOf<String>()
        for (obj in objects) keys.addAll(obj.keys().asSequence().toList())
        memory.checkTable(objects.size, keys.size)
        val columns = keys.toList()
        return ParsedDocument.objects(columns, objects.map { obj -> columns.map { asText(obj.opt(it)) } })
    }
    if (values.any { it is JSONArray }) {
        require(values.all { it is JSONArray }) { "Используйте массивы одинакового типа или объекты с полями." }
        return ParsedDocument.table(values.filterIsInstance<JSONArray>().map { row ->
            List(row.length()) { asText(row.opt(it)) }
        })
    }
    require(values.all { it is String || it == JSONObject.NULL }) {
        "Для JSON используйте список слов, таблицу или объекты с полями."
    }
    return ParsedDocument.objects(listOf("word"), values.map { listOf(asText(it)) })
}

private fun asText(value: Any?): String =
    if (value == null || value == JSONObject.NULL) "" else value.toString()
