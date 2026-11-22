package app.offline.vocabulary

import android.content.res.AssetManager
import app.offline.vocabulary.importer.Entry

internal object BundledDictionary {
    const val wordCount = 1917
    private val sources = listOf("serbian-most-frequent-1-1000.txt", "serbian-most-frequent-1000-2000.txt")

    fun read(assets: AssetManager): List<Entry> {
        val words = sources.flatMap { filename ->
            assets.open("mojsrpski/$filename").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.removePrefix("\uFEFF").trim() }.filter(String::isNotEmpty).toList()
            }
        }
        return assets.open("mojsrpski/cards.tsv").bufferedReader(Charsets.UTF_8).use { reader ->
            check(reader.readLine() == "word\ttranslation\treading_ru\tnote\treferences")
            val entries = reader.lineSequence().mapIndexed { i, line ->
                val fields = line.split('\t', limit = 5)
                requireInput(fields.size == 5 && words.getOrNull(i) == fields[0], R.string.error_bundle_order)
                Entry(word = fields[0], translation = fields[1], transcription = fields[2], extra = fields[3])
            }.toList()
            requireInput(entries.size == words.size && words.size == wordCount, R.string.error_bundle_incomplete)
            entries
        }
    }
}
