package app.offline.vocabulary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.offline.vocabulary.R
import app.offline.vocabulary.data.Availability
import app.offline.vocabulary.data.StudyMode
import app.offline.vocabulary.data.StudySettings

private enum class SettingDialog { COUNT, GOAL, MODE, DIRECTION, ORDER, FONT, RESET, SOURCES, LANGUAGE }

@Composable
fun SettingsScreen(
    settings: StudySettings, availability: Availability, dictionaryName: String?,
    language: String, onLanguageChange: (String) -> Unit,
    onChange: (StudySettings) -> Unit, onReset: () -> Unit, onExport: () -> Unit,
    onRestore: () -> Unit, onBack: () -> Unit
) {
    var dialog by rememberSaveable { mutableStateOf<SettingDialog?>(null) }
    val direction = if (settings.reverse) stringResource(R.string.reverse_direction) else stringResource(R.string.forward_direction)
    val order = if (settings.randomOrder) stringResource(R.string.random_order) else stringResource(R.string.original_order)
    Column(Modifier.fillMaxSize().background(Ink)) {
        ScreenHeading(stringResource(R.string.settings), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            SettingsGroup(stringResource(R.string.language)) {
                SettingRow(stringResource(R.string.language_choices), languageName(language)) { dialog = SettingDialog.LANGUAGE }
            }
            SettingsGroup(stringResource(R.string.learning)) {
                SettingRow(stringResource(R.string.new_per_session), settings.newPerSession.toString()) { dialog = SettingDialog.COUNT }
                SettingsRule()
                SettingRow(stringResource(R.string.daily_word_goal), settings.dailyGoal.toString()) { dialog = SettingDialog.GOAL }
                SettingsRule()
                SettingRow(stringResource(R.string.mode), settings.mode.caption) { dialog = SettingDialog.MODE }
                SettingsRule()
                SettingRow(stringResource(R.string.direction), direction) { dialog = SettingDialog.DIRECTION }
                SettingsRule()
                SettingRow(stringResource(R.string.new_word_order), order) { dialog = SettingDialog.ORDER }
            }
            SettingsGroup(stringResource(R.string.flashcards)) {
                SettingRow(stringResource(R.string.font_size), settings.fontSize.toString()) { dialog = SettingDialog.FONT }
                SettingsRule()
                Row(
                    Modifier.fillMaxWidth().toggleable(value = settings.animations, role = Role.Switch,
                        onValueChange = { onChange(settings.copy(animations = it)) })
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.animations), Modifier.weight(1f), color = Paper)
                    Switch(checked = settings.animations, onCheckedChange = null)
                }
            }
            SettingsGroup(stringResource(R.string.data)) {
                SettingRow(stringResource(R.string.export_backup), "") { onExport() }
                SettingsRule()
                SettingRow(stringResource(R.string.restore_backup), "") { onRestore() }
                if (dictionaryName != null) {
                    SettingsRule()
                    SettingRow(stringResource(R.string.reset_progress), dictionaryName,
                        color = MaterialTheme.colorScheme.error) { dialog = SettingDialog.RESET }
                }
            }
            SettingsGroup(stringResource(R.string.built_in_dictionary)) {
                SettingRow(stringResource(R.string.about_entries), stringResource(R.string.frequent_serbian)) { dialog = SettingDialog.SOURCES }
            }
            Text("made by ai", Modifier.align(Alignment.CenterHorizontally),
                color = Quiet, style = MaterialTheme.typography.bodySmall)
        }
    }
    val close = { dialog = null }
    when (dialog) {
        SettingDialog.LANGUAGE -> ChoiceDialog(stringResource(R.string.language), close) {
            for (tag in listOf("", "ru", "en")) {
                Choice(languageName(tag), language == tag) { close(); onLanguageChange(tag) }
            }
        }
        SettingDialog.COUNT -> NumberDialog(stringResource(R.string.new_per_session), settings.newPerSession,
            listOf(5, 10, 15, 20, 30), close) { onChange(settings.copy(newPerSession = it)); close() }
        SettingDialog.GOAL -> NumberDialog(stringResource(R.string.daily_word_goal), settings.dailyGoal,
            emptyList(), close) { onChange(settings.copy(dailyGoal = it)); close() }
        SettingDialog.MODE -> ChoiceDialog(stringResource(R.string.study_mode), close) {
            StudyMode.values().forEach { mode ->
                val enabled = availability.allows(mode) || mode == StudyMode.CARDS
                val hint = if (enabled) null else when (mode) {
                    StudyMode.QUIZ -> stringResource(R.string.quiz_requirement)
                    StudyMode.TYPING -> stringResource(R.string.translation_requirement)
                    StudyMode.REVIEW -> stringResource(R.string.review_requirement)
                    StudyMode.CARDS -> null
                }
                Choice(mode.caption, settings.mode == mode, enabled, hint) {
                    onChange(settings.copy(mode = mode)); close()
                }
            }
        }
        SettingDialog.DIRECTION -> ChoiceDialog(stringResource(R.string.translation_direction), close) {
            Choice(stringResource(R.string.forward_direction), !settings.reverse) { onChange(settings.copy(reverse = false)); close() }
            Choice(stringResource(R.string.reverse_direction), settings.reverse) { onChange(settings.copy(reverse = true)); close() }
        }
        SettingDialog.ORDER -> ChoiceDialog(stringResource(R.string.new_word_order), close) {
            Choice(stringResource(R.string.original_order), !settings.randomOrder) { onChange(settings.copy(randomOrder = false)); close() }
            Choice(stringResource(R.string.random_order), settings.randomOrder) { onChange(settings.copy(randomOrder = true)); close() }
        }
        SettingDialog.FONT -> FontDialog(settings.fontSize, close) { onChange(settings.copy(fontSize = it)); close() }
        SettingDialog.SOURCES -> ChoiceDialog(stringResource(R.string.frequent_serbian), close) {
            Text(stringResource(R.string.dictionary_origin))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.reading_origin))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.dictionary_license),
                style = MaterialTheme.typography.bodySmall, color = Quiet)
        }
        SettingDialog.RESET -> AlertDialog(
            onDismissRequest = close, title = { Text(stringResource(R.string.confirm_reset_title)) },
            text = { Text(stringResource(R.string.confirm_reset, dictionaryName.orEmpty())) },
            confirmButton = { TextButton(onClick = { close(); onReset() }) { Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } }
        )
        null -> Unit
    }
}

@Composable
private fun languageName(tag: String) = when (tag.substringBefore(',').substringBefore('-')) {
    "en" -> stringResource(R.string.language_english)
    "ru" -> stringResource(R.string.language_russian)
    else -> stringResource(R.string.system_language)
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp), color = Lavender, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge)
        Surface(shape = RoundedCornerShape(20.dp), color = Panel) { Column(content = content) }
    }
}

@Composable
private fun SettingRow(label: String, value: String, color: Color = Paper, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = action)
        .heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = color)
            if (value.isNotEmpty()) Text(value, color = Quiet, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = Quiet, fontSize = 22.sp)
    }
}

@Composable
private fun SettingsRule() = HorizontalDivider(Modifier.padding(start = 16.dp), color = Hairline.copy(alpha = .68f))

@Composable
private fun NumberDialog(
    title: String, initial: Int, presets: List<Int>, onClose: () -> Unit, onSave: (Int) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initial.toString()) }
    val number = value.toIntOrNull()?.takeIf { it >= 1 }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { count ->
                            FilterChip(selected = number == count, onClick = { value = count.toString() },
                                label = { Text(count.toString()) })
                        }
                    }
                }
                OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.word_count)) }, singleLine = true, isError = number == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (number == null) Text(stringResource(R.string.positive_number), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = number != null, onClick = { number?.let(onSave) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun FontDialog(initial: Int, onClose: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(stringResource(R.string.font_size)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sample_card), color = Paper, fontSize = value.sp, lineHeight = (value * 1.22f).sp)
                Text("$value sp", style = MaterialTheme.typography.labelLarge, color = Quiet)
                Slider(value = value.toFloat(), onValueChange = { value = it.toInt() }, valueRange = 20f..64f)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ChoiceDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun Choice(
    title: String, selected: Boolean, enabled: Boolean = true, hint: String? = null, action: () -> Unit
) {
    Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = action)
        .heightIn(min = 52.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, enabled = enabled, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = if (enabled) Paper else Quiet)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Quiet)
        }
    }
}
