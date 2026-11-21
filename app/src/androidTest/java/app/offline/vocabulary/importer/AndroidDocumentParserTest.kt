package app.offline.vocabulary.importer

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDocumentParserTest {
    @Test fun readsCyrillicDocxOnAndroidSax() {
        val document = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body><w:p><w:r><w:t>hello - привет</w:t></w:r></w:p>
            <w:p><w:r><w:t>world - мир</w:t></w:r></w:p></w:body></w:document>"""
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(document.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val parsed = DictionaryParser.parse("слова.docx", bytes.toByteArray().inputStream())
        assertEquals(listOf(Entry("hello", "привет"), Entry("world", "мир")), parsed.prepare(parsed.suggested).entries)
    }

    @Test fun readsTextLayerOfAndroidGeneratedPdf() {
        val bytes = ByteArrayOutputStream()
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            page.canvas.drawText("hello - привет", 32f, 72f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f })
            document.finishPage(page)
            document.writeTo(bytes)
        } finally {
            document.close()
        }
        val parsed = DictionaryParser.parse("слова.pdf", bytes.toByteArray().inputStream())
        assertEquals(listOf(Entry("hello", "привет")), parsed.prepare(parsed.suggested).entries)
    }
}
