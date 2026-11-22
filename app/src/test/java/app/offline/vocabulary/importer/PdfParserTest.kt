package app.offline.vocabulary.importer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PdfParserTest {
    @Before fun initPdf() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    @Test fun readsTextLayer() {
        val document = DictionaryParser.parse("text.pdf", pdf("alpha - beta").inputStream())
        assertEquals(Entry("alpha", "beta"), document.prepare(document.suggested).entries.single())
    }

    @Test fun rejectsMissingTextLayer() {
        val error = assertThrows(app.offline.vocabulary.VocabularyProblem::class.java) {
            DictionaryParser.parse("scan.pdf", pdf(null).inputStream())
        }
        assertEquals(app.offline.vocabulary.R.string.error_pdf_text, error.resource)
    }

    private fun pdf(line: String?): ByteArray = PDDocument().use { document ->
        val page = PDPage()
        document.addPage(page)
        if (line != null) PDPageContentStream(document, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 12f)
            stream.newLineAtOffset(50f, 720f)
            stream.showText(line)
            stream.endText()
        }
        ByteArrayOutputStream().use { output ->
            document.save(output)
            output.toByteArray()
        }
    }
}
