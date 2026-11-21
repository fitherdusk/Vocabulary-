package app.offline.vocabulary.importer

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    private fun parse(name: String, text: String) = DictionaryParser.parse(name, text.byteInputStream())
    private fun ParsedDocument.import() = prepare(suggested)

    @Test fun originalSerbianAssetsAreWordsOnlyAndStayInSourceOrder() {
        val source = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
        val first = source.resolve("mojsrpski/serbian-most-frequent-1-1000.txt")
        val second = source.resolve("mojsrpski/serbian-most-frequent-1000-2000.txt")
        val a = first.inputStream().use { DictionaryParser.parse(first.name, it).import() }
        val b = second.inputStream().use { DictionaryParser.parse(second.name, it).import() }
        assertEquals(999, a.entries.size)
        assertEquals(918, b.entries.size)
        val all = a.entries + b.entries
        assertEquals(1917, all.distinctBy { it.word }.size)
        assertEquals(listOf("да", "је", "не"), all.take(3).map { it.word })
        assertEquals("обе", all.last().word)
        assertTrue(all.all { it.translation.isEmpty() && it.transcription.isEmpty() && it.example.isEmpty() })
    }

    @Test fun plainWordsDashesRichRowsAndUtf8Bom() {
        val input = "\uFEFFalpha - один - первый\r\nbeta | два | /b/ | Пример.\r\nživot\r\n"
        val entries = parse("test.TXT", input).import().entries
        assertEquals(listOf(Entry("alpha", "один - первый"), Entry("beta", "два", "/b/", "Пример."), Entry("život")), entries)
    }

    @Test fun csvHandlesSemicolonsEscapedQuotesAndMultilineExample() {
        val input = "example;word;translation;source\r\n\"А, \"\"Б\"\".\nВ.\";prvi;первый;стр. 3\r\n"
        val document = parse("words.CSV", input)
        assertEquals(Mapping(1, 2, -1, 0), document.suggested)
        assertEquals(Entry("prvi", "первый", "", "А, \"Б\".\nВ.", "source: стр. 3"), document.import().entries.single())
    }

    @Test fun standardCommaCsvPreservesQuotedDelimitersAndEmptyOptionalFields() {
        val input = "word,translation,example\n\"hi, all\",\"всем привет\",\n"
        assertEquals(Entry("hi, all", "всем привет"), parse("data.csv", input).import().entries.single())
    }

    @Test fun detectsTabDelimiterRegardlessOfExtension() {
        val content = "перевод\tпример\tслово\nодин\t\tprvi"
        for (ext in listOf("csv", "tsv")) {
            assertEquals(Entry("prvi", "один"), parse("data.$ext", content).import().entries.single())
        }
    }

    @Test fun arbitraryColumnMappingAndHeaderToggle() {
        val parsed = parse("test.tsv", "B\tA\nцвет\tboja")
        assertFalse(parsed.firstRowIsHeader)
        val header = parsed.withHeader(true)
        assertTrue(header.canChangeHeader)
        assertEquals(listOf("B", "A"), header.headers)
        assertEquals(Entry("boja", "цвет"), header.prepare(Mapping(1, 0)).entries.single())
        assertEquals(2, header.withHeader(false).rows.size)
        assertThrows(IllegalArgumentException::class.java) { header.prepare(Mapping(1, 1)) }
    }

    @Test fun deduplicateAfterMappingAndKeepDifferentMeaningsAndOriginalSpelling() {
        val content = " слово | перевод\n Café | нормальный  текст \n CAFE\u0301 | НОРМАЛЬНЫЙ текст\n|перевод\nCafé|другое"
        val result = parse("data.txt", content).import()
        assertEquals(1, result.duplicates)
        assertEquals(1, result.empty)
        assertEquals(listOf(Entry("Café", "нормальный  текст"), Entry("Café", "другое")), result.entries)
    }

    @Test fun jsonCombinesKeysAcrossAllRecordsIncludingMissingTranslation() {
        val content = """{"words":[{"word":"dva","translation":"два"},{"word":"tri","note":"из заметок","example":"позже"}]}"""
        val document = parse("data.json", content)
        assertFalse(document.canChangeHeader)
        assertEquals(listOf(Entry("dva", "два"), Entry("tri", example = "позже", extra = "note: из заметок")), document.import().entries)
        assertEquals(listOf(Entry("word"), Entry("број")), parse("x.json", """["word","број"]""").import().entries)
    }

    @Test fun jsonLinesAndArrayTable() {
        val content = """{"term":"dva","meaning":"два"}

            {"term":"три","example":"пример"}""".trimIndent()
        assertEquals(listOf(Entry("dva", "два"), Entry("три", example = "пример")), parse("data.jsonl", content).import().entries)
        assertEquals(Entry("reč", "слово"), parse("test.json", """[["translation","word"],["слово","reč"]]""").import().entries.single())
    }

    @Test fun markdownSupportsRowAlignmentEscapedPipesAndInlineStyles() {
        val content = """
            # Словарь

            | Перевод | Слово | Пример |
            | :--- | ---: | --- |
            | номер | **broj** | a \| b |
        """.trimIndent()
        assertEquals(Entry("broj", "номер", example = "a | b"), parse("test.md", content).import().entries.single())
        assertEquals(listOf(Entry("prvi", "первый"), Entry("два")), parse("list.md", "# Глава\n- prvi – первый\n2. два").import().entries)
    }

    @Test fun multipleMarkdownTablesCombineNamedColumns() {
        val input = """
            | word | translation |
            | --- | --- |
            | jedan | один |

            # Ещё

            | translation | word | note |
            | --- | --- | --- |
            | два | dva | 2 |
        """.trimIndent()
        assertEquals(listOf(Entry("jedan", "один"), Entry("dva", "два", extra = "note: 2")), parse("lists.md", input).import().entries)
    }

    @Test fun onlyWordsAndMetadataNeverSuggestsATranslation() {
        val parsed = parse("data.csv", "word,notes\nboja,глава 1")
        assertEquals(Entry("boja", extra = "notes: глава 1"), parsed.import().entries.single())
        assertEquals(listOf(Entry("word"), Entry("other")), parse("plain.txt", "word\nother").import().entries)
    }

    @Test fun oversizedStreamGetsReadableError() {
        val stream = ImportMemory().bounded(ByteArray(4).inputStream(), limit = 3)
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            stream.read(ByteArray(4))
        }.message.orEmpty().contains("памяти"))
    }

    @Test fun docxRejectsDoctypeBeforeExpandingAnyEntities() {
        val xml = """<!DOCTYPE w:document [<!ENTITY a "text">]><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:t>&a;</w:t></w:r></w:p></w:document>"""
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            DictionaryParser.parse("d.docx", zipXml(xml).inputStream())
        }.message.orEmpty().contains("DTD"))
    }

    @Test fun docxReadsRunFragmentsWithoutInsertingSpacesAndPreservesCellBoundaries() {
        val content = """<w:tbl><w:tr>${cell("translation")}${cell("word")}</w:tr>
            <w:tr>${cell("два")}<w:tc><w:p><w:r><w:t>d</w:t></w:r><w:r><w:t>va</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"""
        assertEquals(Entry("dva", "два"), DictionaryParser.parse("data.docx", docx(content).inputStream()).import().entries.single())
    }

    @Test fun docxParagraphsKeepUserText() {
        val content = "<w:p><w:r><w:t>reč - слово</w:t></w:r></w:p><w:p><w:r><w:t>rečnik</w:t></w:r></w:p>"
        assertEquals(listOf(Entry("reč", "слово"), Entry("rečnik")), DictionaryParser.parse("data.docx", docx(content).inputStream()).import().entries)
    }

    @Test fun errorsExplainMalformedOrUnsupportedFiles() {
        for ((file, content) in listOf("data.txt" to " \n", "data.csv" to "word,translation\na,\"не закрыто", "bad.docx" to "invalid", "data.exe" to "word")) {
            assertThrows(file, IllegalArgumentException::class.java) { parse(file, content) }
        }
        val err = assertThrows(IllegalArgumentException::class.java) { parse("test.jsonl", "{\"word\":\"x\"}\n{") }
        assertTrue(err.message.orEmpty().contains("2"))
        val invalidText = byteArrayOf(0xC0.toByte(), 0xC0.toByte())
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            DictionaryParser.parse("test.txt", invalidText.inputStream())
        }.message.orEmpty().contains("UTF-8"))
    }

    private fun cell(text: String) = "<w:tc><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>"

    private fun docx(body: String): ByteArray =
        zipXml("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>""")

    private fun zipXml(xml: String): ByteArray = ByteArrayOutputStream().use { raw ->
        ZipOutputStream(raw).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        raw.toByteArray()
    }
}
