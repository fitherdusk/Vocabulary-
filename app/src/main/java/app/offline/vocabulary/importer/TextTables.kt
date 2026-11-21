package app.offline.vocabulary.importer

private val dash = Regex("\\s+[-–—]\\s+")
private val listMarker = Regex("^\\s*(?:[-+*]|\\d+[.)])\\s+")
private val alignment = Regex(":?-{3,}:?")
private val heading = Regex("^#{1,6}(?:\\s|$).*")
private val divider = Regex("^(?:-{3,}|_{3,}|\\*{3,})$")

internal fun plainTable(text: String): ParsedDocument {
    val rows = text.lineSequence().map(::plainLine).toMutableList()
    if (text.endsWith('\n') || text.endsWith('\r')) rows.removeAt(rows.lastIndex)
    return ParsedDocument.table(rows, headerHint = if (rows.all { it.size <= 1 }) false else null)
}

private fun plainLine(line: String): List<String> = when {
    '\t' in line -> line.split('\t')
    '|' in line -> line.split('|')
    else -> dash.split(line, limit = 2)
}

internal fun markdownTable(text: String, memory: ImportMemory): ParsedDocument {
    val lines = text.lines()
    val tables = ArrayList<Pair<List<String>, List<List<String>>>>()
    var i = 1
    while (i < lines.size) {
        if ('|' !in lines[i - 1] || '|' !in lines[i] || !markdownCells(lines[i]).all { alignment.matches(it) }) {
            i++
            continue
        }
        val header = markdownCells(lines[i - 1])
        val records = ArrayList<List<String>>()
        i++
        while (i < lines.size && '|' in lines[i]) records.add(markdownCells(lines[i++]))
        tables.add(header to records)
    }
    if (tables.size == 1) {
        val (header, records) = tables.single()
        return ParsedDocument.table(listOf(header) + records, true)
    }
    if (tables.isNotEmpty()) {
        val headers = tables.flatMap { it.first }.distinct()
        memory.checkTable(tables.sumOf { it.second.size }, headers.size)
        val records = ArrayList<List<String>>()
        for ((header, table) in tables) {
            require(header.distinct().size == header.size && table.all { it.size <= header.size }) {
                "В таблицах Markdown нужны разные заголовки для каждого столбца."
            }
            val indices = headers.map(header::indexOf)
            records.addAll(table.map { row -> indices.map { row.getOrNull(it).orEmpty() } })
        }
        return ParsedDocument.objects(headers, records)
    }
    val rows = lines.map(String::trim).filterNot {
        heading.matches(it) || divider.matches(it) || it.startsWith("```") || it.startsWith("~~~")
    }.map {
        val item = it.replaceFirst(listMarker, "")
        if ('|' in item) markdownCells(item) else plainLine(item).map(::unstyle)
    }
    return ParsedDocument.table(rows)
}

private fun markdownCells(line: String): List<String> {
    var value = line.trim().removePrefix("|")
    if (value.endsWith("|") && !value.endsWith("\\|")) value = value.dropLast(1)
    val cells = ArrayList<String>()
    val cell = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i++]
        when {
            c == '\\' && i < value.length && value[i] in "|\\" -> cell.append(value[i++])
            c == '|' -> {
                cells.add(unstyle(cell.toString()))
                cell.setLength(0)
            }
            else -> cell.append(c)
        }
    }
    cells.add(unstyle(cell.toString()))
    return cells
}

private fun unstyle(value: String): String {
    val trimmed = value.trim()
    for (marker in listOf("**", "__", "`", "*", "_")) {
        if (trimmed.length > marker.length * 2 && trimmed.startsWith(marker) && trimmed.endsWith(marker)) {
            return trimmed.substring(marker.length, trimmed.length - marker.length)
        }
    }
    return trimmed
}

internal fun delimitedTable(text: String, extension: String): ParsedDocument {
    val default = if (extension == "tsv") '\t' else ','
    val candidates = listOf(default, '\t', ';', ',', '|').distinct()
    // Sample complete logical records so a newline inside a quoted field does not affect detection.
    val delimiter = candidates.maxByOrNull { candidate ->
        val sample = try {
            readDelimited(text, candidate, limit = 40).filter { row -> row.any(String::isNotBlank) }
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        val widths = sample.map { it.size }.filter { it > 1 }
        val consistent = widths.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
        val named = sample.firstOrNull().orEmpty().count { role(it) != null }
        consistent * 10 + widths.size + (if (named >= 2) 1000 else 0)
    } ?: default
    return ParsedDocument.table(readDelimited(text, delimiter))
}

internal fun readDelimited(text: String, delimiter: Char, limit: Int = Int.MAX_VALUE): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var quoted = false
    var closedQuote = false
    var hasContent = false
    var i = 0
    fun finishCell() {
        row.add(cell.toString())
        cell.setLength(0)
        closedQuote = false
    }
    while (i < text.length && rows.size < limit) {
        val c = text[i++]
        if (quoted) {
            if (c == '"') {
                if (i < text.length && text[i] == '"') {
                    cell.append('"')
                    i++
                } else {
                    quoted = false
                    closedQuote = true
                }
            } else cell.append(c)
            continue
        }
        when (c) {
            delimiter -> {
                finishCell()
                hasContent = true
            }
            '\r', '\n' -> {
                if (c == '\r' && i < text.length && text[i] == '\n') i++
                finishCell()
                rows.add(row)
                row = ArrayList()
                hasContent = false
            }
            '"' -> {
                hasContent = true
                if (cell.isBlank() && !closedQuote) {
                    cell.setLength(0)
                    quoted = true
                } else {
                    require(!closedQuote) { "Лишняя кавычка в записи ${rows.size + 1}." }
                    cell.append(c)
                }
            }
            else -> {
                hasContent = true
                if (closedQuote) {
                    require(c.isWhitespace()) { "После закрывающей кавычки в записи ${rows.size + 1} нужен разделитель." }
                } else cell.append(c)
            }
        }
    }
    require(!quoted) { "В записи ${rows.size + 1} не закрыта кавычка." }
    if (hasContent && rows.size < limit) {
        finishCell()
        rows.add(row)
    }
    return rows
}
