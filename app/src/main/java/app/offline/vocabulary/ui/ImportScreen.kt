package app.offline.vocabulary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.offline.vocabulary.R
import app.offline.vocabulary.VocabularyProblem
import app.offline.vocabulary.importer.Entry
import app.offline.vocabulary.importer.ImportResult
import app.offline.vocabulary.importer.Mapping
import app.offline.vocabulary.importer.ParsedDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ImportField(val title: Int) {
    WORD(R.string.word), TRANSLATION(R.string.translation), TRANSCRIPTION(R.string.transcription), EXAMPLE(R.string.example)
}

private val mappingSaver = listSaver<Mapping, Int>(
    save = { listOf(it.word, it.translation, it.transcription, it.example) },
    restore = { Mapping(it[0], it[1], it[2], it[3]) }
)

private fun Mapping.column(field: ImportField): Int = when (field) {
    ImportField.WORD -> word
    ImportField.TRANSLATION -> translation
    ImportField.TRANSCRIPTION -> transcription
    ImportField.EXAMPLE -> example
}

private fun Mapping.assign(field: ImportField, column: Int): Mapping {
    val columns = mutableListOf(word, translation, transcription, example)
    val occupant = if (column >= 0) columns.indexOf(column) else -1
    if (occupant >= 0) columns[occupant] = this.column(field)
    columns[field.ordinal] = column
    return Mapping(columns[0], columns[1], columns[2], columns[3])
}

private data class Preview(val result: ImportResult? = null, val hasTranslations: Boolean = false, val error: Exception? = null)

@Composable
fun ImportScreen(
    filename: String,
    document: ParsedDocument,
    onConfirm: (name: String, entries: List<Entry>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultName = stringResource(R.string.my_dictionary)
    var name by rememberSaveable(filename) {
        mutableStateOf(filename.substringBeforeLast('.', filename).trim().ifBlank { defaultName })
    }
    var hasHeader by rememberSaveable(filename) { mutableStateOf(document.firstRowIsHeader) }
    val adjusted by key(document, hasHeader) {
        produceState<ParsedDocument?>(if (document.firstRowIsHeader == hasHeader) document else null) {
            if (value == null) value = withContext(Dispatchers.Default) { document.withHeader(hasHeader) }
        }
    }

    Column(
        modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeading(stringResource(R.string.import_title), onBack, Modifier.widthIn(max = 700.dp))

        Column(
            Modifier.widthIn(max = 700.dp).fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                filename, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.dictionary_name)) }, singleLine = true,
                isError = name.isBlank(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp)
            )
            if (document.canChangeHeader) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = hasHeader, role = Role.Switch, onValueChange = { hasHeader = it })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.first_row_headers), Modifier.weight(1f))
                    Switch(checked = hasHeader, onCheckedChange = null)
                }
            }
            val source = adjusted
            if (source == null) {
                ImportProgress()
            } else {
                ImportColumns(filename, hasHeader, source, name, onConfirm)
            }
        }
    }
}

@Composable
private fun ImportColumns(
    filename: String,
    hasHeader: Boolean,
    source: ParsedDocument,
    name: String,
    onConfirm: (String, List<Entry>) -> Unit
) {
    var mapping by rememberSaveable(filename, hasHeader, stateSaver = mappingSaver) {
        mutableStateOf(source.suggested)
    }
    var picking by rememberSaveable(filename, hasHeader) { mutableStateOf<ImportField?>(null) }
    val resources = LocalContext.current.resources
    val preview by key(source, mapping, LocalConfiguration.current.locales.toLanguageTags()) {
        produceState(Preview()) {
            value = try {
                withContext(Dispatchers.Default) {
                    val result = source.prepare(mapping) { resources.getString(R.string.column_number, it + 1) }
                    Preview(result, result.entries.any { it.translation.isNotBlank() })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Preview(error = e)
            }
        }
    }
    SectionTitle(stringResource(R.string.map_columns))
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            ImportField.values().forEachIndexed { index, field ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .20f))
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { picking = field }
                        .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(field.title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        columnName(source, mapping.column(field)), Modifier.weight(1.2f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val tint = MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(Modifier.size(16.dp)) {
                        val p = size.width / 16
                        drawLine(tint, Offset(4*p, 6*p), Offset(8*p, 10*p), 1.5f*p, StrokeCap.Round)
                        drawLine(tint, Offset(8*p, 10*p), Offset(12*p, 6*p), 1.5f*p, StrokeCap.Round)
                    }
                }
            }
        }
    }
    picking?.let { field ->
        val current = mapping.column(field)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(field.title)) },
            text = {
                val indices = if (field == ImportField.WORD) source.headers.indices else -1..source.headers.lastIndex
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(indices.toList()) { index ->
                        val selectable = index < 0 || index != mapping.word || field == ImportField.WORD || current >= 0
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = current == index, enabled = selectable, role = Role.RadioButton) {
                                mapping = mapping.assign(field, index)
                                picking = null
                            }.heightIn(min = 48.dp).padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = current == index, onClick = null, enabled = selectable)
                            Column {
                                Text(columnName(source, index), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selectable) 1f else .45f))
                                val sample = source.rows.firstOrNull()?.getOrNull(index).orEmpty()
                                if (sample.isNotBlank()) {
                                    Text(sample, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    SectionTitle(stringResource(R.string.preview))
    val result = preview.result
    val error = preview.error
    when {
        error != null -> Text(if (error is VocabularyProblem) error.display(LocalContext.current) else stringResource(R.string.error_columns), color = MaterialTheme.colorScheme.error)
        result == null -> ImportProgress()
        else -> {
            Text(
                stringResource(R.string.import_counts, result.entries.size, result.duplicates, result.empty),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.entries.isEmpty()) {
                Text(stringResource(R.string.empty_word_column), color = MaterialTheme.colorScheme.error)
            } else {
                if (!preview.hasTranslations) {
                    Text(stringResource(R.string.no_translations),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column {
                        result.entries.take(10).forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
                            PreviewRow(index + 1, entry)
                        }
                    }
                }
            }
            Button(
                onClick = { onConfirm(name.trim(), result.entries) },
                enabled = result.entries.isNotEmpty() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text(stringResource(R.string.import_dictionary)) }
        }
    }
}

@Composable
private fun columnName(document: ParsedDocument, index: Int): String =
    if (index < 0) stringResource(R.string.unused_column) else document.headerName(index) ?: stringResource(R.string.column_number, index + 1)

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ImportProgress() {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun PreviewRow(number: Int, entry: Entry) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(number.toString(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 20.dp).padding(top = 3.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (entry.transcription.isNotBlank()) Text(entry.transcription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.translation.isNotBlank()) Text(entry.translation, style = MaterialTheme.typography.bodyLarge)
            if (entry.example.isNotBlank()) Text(entry.example, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.extra.isNotBlank()) Text(entry.extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
