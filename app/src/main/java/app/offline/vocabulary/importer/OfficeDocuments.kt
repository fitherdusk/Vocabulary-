package app.offline.vocabulary.importer

import app.offline.vocabulary.R
import app.offline.vocabulary.VocabularyProblem
import app.offline.vocabulary.requireInput
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.ext.DefaultHandler2

internal fun readPdf(input: InputStream, memory: ImportMemory): ParsedDocument {
    val scratch = MemoryUsageSetting.setupMainMemoryOnly(memory.officeBytes)
    PDDocument.load(memory.bounded(input, memory.officeBytes), scratch).use { document ->
        requireInput(document.currentAccessPermission.canExtractContent(), R.string.error_pdf_permissions)
        val output = PdfTextOutput(memory)
        ColumnTextStripper().writeText(document, output)
        val text = output.toString()
        requireInput(text.isNotBlank(), R.string.error_pdf_text)
        return plainTable(text)
    }
}

/** Preserve wide gaps between separately positioned text runs as table cell boundaries. */
private class ColumnTextStripper : PDFTextStripper() {
    private var previous: TextPosition? = null

    init { sortByPosition = true }

    override fun writeString(text: String, positions: MutableList<TextPosition>) {
        val last = previous
        val first = positions.firstOrNull()
        if (last != null && first != null) {
            val gap = first.xDirAdj - last.xDirAdj - last.widthDirAdj
            if (gap > maxOf(first.widthOfSpace * 4, first.heightDir)) output.write("\t")
        }
        super.writeString(text, positions)
        previous = positions.lastOrNull()
    }

    override fun writeLineSeparator() {
        previous = null
        super.writeLineSeparator()
    }

    override fun writePageStart() {
        previous = null
        super.writePageStart()
    }
}

internal fun readDocx(input: InputStream, memory: ImportMemory): ParsedDocument {
    ZipInputStream(memory.bounded(input, memory.officeBytes).buffered()).use { zip ->
        val unpacked = memory.bounded(zip, memory.officeBytes)
        val skipBuffer = ByteArray(8192)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name != "word/document.xml") {
                while (unpacked.read(skipBuffer) >= 0) { /* Skip other parts without retaining them. */ }
                continue
            }
            val handler = WordContent()
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.entityResolver = handler
            // Android's Expat and standard JVM SAX readers both implement a lexical handler.
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.parse(InputSource(unpacked))
            if (handler.tables.isNotEmpty()) return ParsedDocument.table(handler.tables)
            return plainTable(handler.paragraphs.joinToString("\n"))
        }
    }
    throw VocabularyProblem(R.string.error_docx_body)
}

private class WordContent : DefaultHandler2() {
    val tables = ArrayList<List<String>>()
    val paragraphs = ArrayList<String>()
    private var tableDepth = 0
    private var row: MutableList<String>? = null
    private var cell: MutableList<String>? = null
    private var paragraph: StringBuilder? = null
    private var inText = false

    override fun startDTD(name: String?, publicId: String?, systemId: String?) {
        throw VocabularyProblem(R.string.error_docx_dtd)
    }

    override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
        throw VocabularyProblem(R.string.error_docx_external)

    override fun startElement(uri: String, local: String, qName: String, attrs: Attributes) {
        if (!wordNamespace(uri)) return
        when (local) {
            "tbl" -> tableDepth++
            "tr" -> if (tableDepth == 1) row = ArrayList()
            "tc" -> if (tableDepth == 1) cell = ArrayList()
            "p" -> paragraph = StringBuilder()
            "t" -> inText = true
            "tab" -> paragraph?.append('\t')
            "br", "cr" -> paragraph?.append('\n')
        }
    }

    override fun characters(chars: CharArray, start: Int, length: Int) {
        if (inText) paragraph?.append(chars, start, length)
    }

    override fun endElement(uri: String, local: String, qName: String) {
        if (!wordNamespace(uri)) return
        when (local) {
            "t" -> inText = false
            "p" -> {
                val value = paragraph?.toString().orEmpty()
                if (tableDepth == 0) paragraphs.add(value) else cell?.add(value)
                paragraph = null
            }
            "tc" -> if (tableDepth == 1) {
                row?.add(cell.orEmpty().joinToString("\n"))
                cell = null
            }
            "tr" -> if (tableDepth == 1) {
                row?.let(tables::add)
                row = null
            }
            "tbl" -> tableDepth--
        }
    }

    private fun wordNamespace(uri: String): Boolean =
        uri == "http://schemas.openxmlformats.org/wordprocessingml/2006/main" ||
            uri == "http://purl.oclc.org/ooxml/wordprocessingml/main"
}
