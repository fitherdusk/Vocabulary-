package app.offline.vocabulary

import androidx.compose.ui.res.stringResource

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.offline.vocabulary.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

private class OpenLocalFile : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

private class CreateLocalBackup : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

class MainActivity : ComponentActivity() {
    private val model: VocabularyViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = android.graphics.Color.rgb(16, 16, 18)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(dark))
        setContent {
            VocabularyTheme {
                VocabularyApp(model, AppLanguage.selected(this)) { tag ->
                    if (tag != AppLanguage.selected(this)) {
                        AppLanguage.set(this, tag)
                        recreate()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refreshStats()
    }
}

@Composable
private fun VocabularyApp(model: VocabularyViewModel, language: String, onLanguageChange: (String) -> Unit) {
    val open = rememberLauncherForActivityResult(OpenLocalFile()) { uri -> uri?.let(model::readDictionary) }
    val restoreFile = rememberLauncherForActivityResult(OpenLocalFile()) { uri -> uri?.let(model::chooseRestore) }
    val destination = rememberLauncherForActivityResult(CreateLocalBackup()) { uri -> uri?.let(model::export) }

    fun picker(action: () -> Unit) {
        if (model.busy != null) return
        try { action() } catch (e: ActivityNotFoundException) { model.filePickerUnavailable() }
    }
    val importFile = { picker { open.launch(arrayOf("*/*")) } }
    val screen = model.screen
    val library = model.library
    BackHandler(enabled = screen != Screen.HOME || model.busy != null) { model.back() }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (library == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (model.busy != null) {
                        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.open_library_error), color = Paper)
                        TextButton(onClick = model::initialize) { Text(stringResource(R.string.retry)) }
                    }
                }
            } else {
                val items = remember(library.dictionaries) {
                    library.dictionaries.map { LibraryItem(it.id, it.name, it.learned, it.total) }
                }
                val selected = items.firstOrNull { it.id == library.selectedId }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(selected, library.stats.masteryPercent, library.settings.animations, model::start,
                                { model.navigate(Screen.LIBRARY) }, importFile,
                                { model.navigate(Screen.STATISTICS); model.refreshStats() })
                            Screen.LIBRARY -> LibraryScreen(items, library.selectedId, model::selectDictionary,
                                model::renameDictionary, model::deleteDictionary, importFile, model::back)
                            Screen.STATISTICS -> StatisticsScreen(library.stats, selected?.title,
                                library.settings.dailyGoal, model::back)
                            Screen.SETTINGS -> SettingsScreen(library.settings, library.availability, selected?.title, language, onLanguageChange,
                                model::saveSettings, model::resetProgress,
                                { picker { destination.launch("offline-vocabulary-${LocalDate.now()}.json") } },
                                { picker { restoreFile.launch(arrayOf("*/*")) } }, model::back)
                            Screen.IMPORT -> model.parsed?.let { document ->
                                ImportScreen(model.filename, document, model::importDictionary, model::back)
                            }
                            Screen.STUDY -> model.session?.let { lesson ->
                                StudyScreen(lesson, library.settings, selected?.title.orEmpty(), model::back,
                                    model::rate, busy = model.busy != null)
                            }
                            Screen.COMPLETE -> StudyCompleteScreen(model.completedCount, library.stats.masteryPercent, model.completedMilestone, model::back)
                        }
                    }
                    if (screen == Screen.HOME || screen == Screen.SETTINGS) {
                        VocabularyNavigation(screen == Screen.SETTINGS, { model.navigate(Screen.HOME) },
                            { model.navigate(Screen.SETTINGS) }, importFile)
                    }
                }
            }
            val busy = model.busy
            if (busy != null && library != null) {
                var show by remember(busy) { mutableStateOf(false) }
                LaunchedEffect(busy) { delay(300); show = true }
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }.then(if (show) Modifier.background(Ink.copy(alpha = .86f)) else Modifier),
                    contentAlignment = Alignment.Center) {
                    if (show) Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text(busy, color = Paper)
                    }
                }
            }
        }
    }

    if (model.restoreUri != null) {
        AlertDialog(onDismissRequest = { model.chooseRestore(null) },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_description)) },
            confirmButton = { TextButton(onClick = model::restore) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { model.chooseRestore(null) }) { Text(stringResource(R.string.cancel)) } })
    }
    model.notice?.let { notice ->
        AlertDialog(onDismissRequest = model::dismissNotice, title = { Text(notice.title) },
            text = { Text(notice.message) },
            confirmButton = { TextButton(onClick = model::dismissNotice) { Text(stringResource(R.string.ok)) } })
    }
}
