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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.offline.vocabulary.data.Availability
import app.offline.vocabulary.data.StudyMode
import app.offline.vocabulary.data.StudySettings

private enum class SettingDialog { COUNT, GOAL, MODE, DIRECTION, ORDER, FONT, RESET, SOURCES }

@Composable
fun SettingsScreen(
    settings: StudySettings, availability: Availability, dictionaryName: String?,
    onChange: (StudySettings) -> Unit, onReset: () -> Unit, onExport: () -> Unit,
    onRestore: () -> Unit, onBack: () -> Unit
) {
    var dialog by rememberSaveable { mutableStateOf<SettingDialog?>(null) }
    val direction = if (settings.reverse) "Перевод → слово" else "Слово → перевод"
    val order = if (settings.randomOrder) "Случайный" else "Исходный"
    Column(Modifier.fillMaxSize().background(Ink)) {
        ScreenHeading("Настройки", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            SettingsGroup("Обучение") {
                SettingRow("Новых слов за занятие", settings.newPerSession.toString()) { dialog = SettingDialog.COUNT }
                SettingsRule()
                SettingRow("Дневная цель в словах", settings.dailyGoal.toString()) { dialog = SettingDialog.GOAL }
                SettingsRule()
                SettingRow("Режим", settings.mode.caption) { dialog = SettingDialog.MODE }
                SettingsRule()
                SettingRow("Направление", direction) { dialog = SettingDialog.DIRECTION }
                SettingsRule()
                SettingRow("Порядок новых слов", order) { dialog = SettingDialog.ORDER }
            }
            SettingsGroup("Карточки") {
                SettingRow("Размер шрифта", settings.fontSize.toString()) { dialog = SettingDialog.FONT }
                SettingsRule()
                Row(
                    Modifier.fillMaxWidth().toggleable(value = settings.animations, role = Role.Switch,
                        onValueChange = { onChange(settings.copy(animations = it)) })
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Анимации", Modifier.weight(1f), color = Paper)
                    Switch(checked = settings.animations, onCheckedChange = null)
                }
            }
            SettingsGroup("Данные") {
                SettingRow("Сохранить резервную копию", "") { onExport() }
                SettingsRule()
                SettingRow("Восстановить из копии", "") { onRestore() }
                if (dictionaryName != null) {
                    SettingsRule()
                    SettingRow("Сбросить прогресс", dictionaryName,
                        color = MaterialTheme.colorScheme.error) { dialog = SettingDialog.RESET }
                }
            }
            SettingsGroup("Встроенный словарь") {
                SettingRow("О переводах и произношении", "Частотный сербский") { dialog = SettingDialog.SOURCES }
            }
        }
    }
    val close = { dialog = null }
    when (dialog) {
        SettingDialog.COUNT -> NumberDialog("Новых слов за занятие", settings.newPerSession,
            listOf(5, 10, 15, 20, 30), close) { onChange(settings.copy(newPerSession = it)); close() }
        SettingDialog.GOAL -> NumberDialog("Дневная цель в словах", settings.dailyGoal,
            emptyList(), close) { onChange(settings.copy(dailyGoal = it)); close() }
        SettingDialog.MODE -> ChoiceDialog("Режим обучения", close) {
            StudyMode.values().forEach { mode ->
                val enabled = availability.allows(mode) || mode == StudyMode.CARDS
                val hint = if (enabled) null else when (mode) {
                    StudyMode.QUIZ -> "Нужно четыре различных варианта ответа"
                    StudyMode.TYPING -> "Нужны слова с переводом"
                    StudyMode.REVIEW -> "Нужны ранее изучавшиеся слова"
                    StudyMode.CARDS -> null
                }
                Choice(mode.caption, settings.mode == mode, enabled, hint) {
                    onChange(settings.copy(mode = mode)); close()
                }
            }
        }
        SettingDialog.DIRECTION -> ChoiceDialog("Направление перевода", close) {
            Choice("Слово → перевод", !settings.reverse) { onChange(settings.copy(reverse = false)); close() }
            Choice("Перевод → слово", settings.reverse) { onChange(settings.copy(reverse = true)); close() }
        }
        SettingDialog.ORDER -> ChoiceDialog("Порядок новых слов", close) {
            Choice("Исходный", !settings.randomOrder) { onChange(settings.copy(randomOrder = false)); close() }
            Choice("Случайный", settings.randomOrder) { onChange(settings.copy(randomOrder = true)); close() }
        }
        SettingDialog.FONT -> FontDialog(settings.fontSize, close) { onChange(settings.copy(fontSize = it)); close() }
        SettingDialog.SOURCES -> ChoiceDialog("Частотный сербский", close) {
            Text("Список из 1917 записей предоставлен MojSrpski. Переводы подобраны для словоформ по открытым словарям.")
            Spacer(Modifier.height(12.dp))
            Text("Русские буквы передают звучание приблизительно. Знак ударения показан, когда его место подтверждено для этой формы. Долгота гласных и мелодика сербского ударения в подсказке опущены.")
            Spacer(Modifier.height(12.dp))
            Text("Викисловарь и его участники · Wiktextract / Kaikki.org · Hrvatski jezični portal. Таблица переводов и чтения — CC BY-SA 4.0. Источники отдельных записей и текст лицензии включены в проект и APK.",
                style = MaterialTheme.typography.bodySmall, color = Quiet)
        }
        SettingDialog.RESET -> AlertDialog(
            onDismissRequest = close, title = { Text("Сбросить прогресс?") },
            text = { Text("История занятий и интервалы повторений словаря «${dictionaryName.orEmpty()}» будут удалены. Все слова останутся в библиотеке.") },
            confirmButton = { TextButton(onClick = { close(); onReset() }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = close) { Text("Отмена") } }
        )
        null -> Unit
    }
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
                    label = { Text("Количество слов") }, singleLine = true, isError = number == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (number == null) Text("Введите положительное целое число", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = number != null, onClick = { number?.let(onSave) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }
    )
}

@Composable
private fun FontDialog(initial: Int, onClose: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Размер шрифта") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Карточка", color = Paper, fontSize = value.sp, lineHeight = (value * 1.22f).sp)
                Text("$value sp", style = MaterialTheme.typography.labelLarge, color = Quiet)
                Slider(value = value.toFloat(), onValueChange = { value = it.toInt() }, valueRange = 20f..64f)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }
    )
}

@Composable
private fun ChoiceDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } })
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
