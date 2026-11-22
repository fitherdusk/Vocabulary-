package app.offline.vocabulary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.offline.vocabulary.R

@Composable
fun LibraryScreen(
    items: List<LibraryItem>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var renameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    Box(modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 660.dp).fillMaxSize()) {
            ScreenHeading(stringResource(R.string.my_dictionaries), onBack)
            if (items.isEmpty()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                            .padding(horizontal = 26.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(78.dp).background(Panel, CircleShape)
                            .border(1.dp, Hairline, CircleShape), contentAlignment = Alignment.Center) {
                            VocabularyIcon(Glyph.Book, Lavender, Modifier.size(35.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.no_dictionaries), color = Paper, fontSize = 23.sp,
                            fontWeight = FontWeight.Medium, letterSpacing = (-.6).sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.choose_file_device),
                            Modifier.widthIn(max = 310.dp), color = Quiet, fontSize = 15.sp,
                            lineHeight = 21.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        AddDictionaryButton(onImport)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        LibraryRow(item, item.id == selectedId,
                            onSelect = { onSelect(item.id) },
                            onRename = { draft = item.title; renameId = item.id },
                            onDelete = { deleteId = item.id })
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    AddDictionaryButton(onImport, Modifier.fillMaxWidth())
                }
            }
        }
    }

    val renameItem = items.firstOrNull { it.id == renameId }
    if (renameItem != null) {
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(stringResource(R.string.rename_dictionary)) },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true,
                    label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth())
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = { onRename(renameItem.id, draft.trim()); renameId = null },
                    enabled = draft.isNotBlank()) { Text(stringResource(R.string.save)) }
            }
        )
    }
    val deleteItem = items.firstOrNull { it.id == deleteId }
    if (deleteItem != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.delete_dictionary)) },
            text = { Text(stringResource(R.string.confirm_delete, deleteItem.title)) },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = { onDelete(deleteItem.id); deleteId = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun AddDictionaryButton(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onImport, modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(16.dp)) {
        VocabularyIcon(Glyph.Plus, MaterialTheme.colorScheme.onPrimary, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.add_dictionary), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LibraryRow(
    item: LibraryItem, selected: Boolean, onSelect: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(19.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp,
        if (selected) Lavender.copy(alpha = .48f) else Hairline, shape),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = stringResource(R.string.select_dictionary), onClick = onSelect)
            .padding(start = 14.dp, top = 15.dp, bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(if (selected) Lavender.copy(alpha = .15f) else PanelRaised,
                RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                VocabularyIcon(if (selected) Glyph.Check else Glyph.Book, if (selected) Lavender else Quiet,
                    Modifier.size(21.dp), if (selected) stringResource(R.string.selected_dictionary) else null)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Paper,
                    fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(stringResource(R.string.learned_count, item.learned, item.total), color = Quiet, fontSize = 13.sp)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                VocabularyIcon(Glyph.More, Quiet, Modifier.size(21.dp), stringResource(R.string.dictionary_actions, item.title))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() })
            }
        }
    }
}
