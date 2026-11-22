package app.offline.vocabulary.importer

import app.offline.vocabulary.R
import app.offline.vocabulary.VocabularyProblem
import java.io.FilterInputStream
import java.io.InputStream
import java.io.Writer

/** Limits scale with the device's currently available heap, independent of the number of dictionary words. */
internal class ImportMemory {
    private val available: Long = Runtime.getRuntime().run { maxMemory() - (totalMemory() - freeMemory()) }
    // Decoding, splitting, preview and deduplication briefly retain multiple views of text.
    val textBytes: Long = available / 24
    val officeBytes: Long = available / 6

    fun checkTable(rows: Int, columns: Int) {
        if (rows.toLong() * columns > available / 32) tooLarge()
    }

    fun bounded(input: InputStream, limit: Long): InputStream = object : FilterInputStream(input) {
        private var count = 0L
        private fun account(n: Int): Int {
            if (n > 0) {
                count += n
                if (count > limit) tooLarge()
            }
            return n
        }

        override fun read(): Int = super.read().also { if (it >= 0) account(1) }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int = account(super.read(bytes, offset, length))
    }

    fun tooLarge(): Nothing = throw VocabularyProblem(R.string.error_file_size)
}

internal class PdfTextOutput(private val memory: ImportMemory) : Writer() {
    private val buffer = StringBuilder()
    override fun write(chars: CharArray, offset: Int, length: Int) {
        if (buffer.length.toLong() + length > memory.textBytes / 2) memory.tooLarge()
        buffer.append(chars, offset, length)
    }
    override fun toString() = buffer.toString()
    override fun flush() = Unit
    override fun close() = Unit
}
