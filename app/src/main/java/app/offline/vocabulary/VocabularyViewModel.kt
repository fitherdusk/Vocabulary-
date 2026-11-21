package app.offline.vocabulary

import android.app.Application
import android.database.sqlite.SQLiteFullException
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.offline.vocabulary.data.LibrarySnapshot
import app.offline.vocabulary.data.Rating
import app.offline.vocabulary.data.StudyMode
import app.offline.vocabulary.data.StudySession
import app.offline.vocabulary.data.StudySettings
import app.offline.vocabulary.importer.DictionaryParser
import app.offline.vocabulary.importer.Entry
import app.offline.vocabulary.importer.ParsedDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

enum class Screen { HOME, LIBRARY, SETTINGS, STATISTICS, IMPORT, STUDY, COMPLETE }

data class Notice(val title: String, val message: String)

class VocabularyViewModel(
    application: Application, private val state: SavedStateHandle
) : AndroidViewModel(application) {
    private val repo = (application as VocabularyApplication).repository
    private val documents = LocalDocuments(application)
    var screen by mutableStateOf(Screen.valueOf(state["screen"] ?: Screen.HOME.name))
        private set
    var library by mutableStateOf<LibrarySnapshot?>(null)
        private set
    var session by mutableStateOf<StudySession?>(null)
        private set
    var parsed by mutableStateOf<ParsedDocument?>(null)
        private set
    var filename by mutableStateOf(state["filename"] ?: "")
        private set
    var completedCount by mutableStateOf(state["completedCount"] ?: 0)
        private set
    var restoreUri by mutableStateOf(state.get<String>("restoreUri"))
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set

    init { initialize() }

    fun initialize() = runTask("Открываем библиотеку") {
        withContext(Dispatchers.IO) {
            repo.installBundledIfNeeded(BundledDictionary.name) {
                BundledDictionary.read(getApplication<Application>().assets)
            }
        }
        reload()
        when (screen) {
            Screen.STUDY -> {
                session = withContext(Dispatchers.IO) { repo.activeSession() }
                if (session == null) go(Screen.HOME)
            }
            Screen.IMPORT -> {
                try {
                    parsed = parseDraft(filename)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    go(Screen.HOME)
                    throw e
                }
            }
            else -> Unit
        }
    }

    private suspend fun reload() { library = withContext(Dispatchers.IO) { repo.snapshot() } }

    fun refreshStats() {
        if (library == null || busy != null) return
        viewModelScope.launch {
            val initial = library
            try {
                val fresh = withContext(Dispatchers.IO) { repo.snapshot() }
                if (busy == null && initial === library) library = fresh
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e) }
        }
    }

    private fun go(next: Screen) {
        screen = next
        state["screen"] = next.name
    }

    fun navigate(next: Screen) {
        if (busy == null) go(next)
    }

    fun back() {
        if (busy != null) return
        if (screen == Screen.IMPORT) {
            parsed = null
            clearDraft()
            go(Screen.valueOf(state["returnFromImport"] ?: Screen.HOME.name))
        } else {
            val wasStudying = screen == Screen.STUDY
            go(Screen.HOME)
            if (wasStudying) refreshStats()
        }
    }

    fun dismissNotice() { notice = null }

    fun filePickerUnavailable() {
        notice = Notice("Файловый менеджер недоступен", "Для выбора файла включите системное приложение «Файлы».")
    }

    private fun runTask(label: String, action: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e) }
            finally { busy = null }
        }
    }

    private fun report(e: Exception) {
        val fallback = "Операцию не удалось завершить. Попробуйте ещё раз."
        val message = when (e) {
            is SQLiteFullException -> "На устройстве закончилось свободное место."
            is SecurityException, is FileNotFoundException -> "Файл недоступен. Выберите его снова в памяти устройства."
            is IllegalArgumentException, is IllegalStateException, is IOException ->
                e.localizedMessage?.takeIf { it.any { ch -> ch in 'А'..'я' } }
                    ?: if (e is IOException) "Не удалось прочитать или записать файл. Проверьте формат и свободное место." else fallback
            else -> fallback
        }
        notice = Notice("Не удалось выполнить действие", message)
    }

    private suspend fun parseDraft(name: String) = withContext(Dispatchers.IO) {
        documents.draft.inputStream().buffered().use { DictionaryParser.parse(name, it) }
    }

    private fun clearDraft() {
        filename = ""
        state.remove<String>("filename")
        // Release the previous preview before another picker result can stage a new file.
        documents.draft.delete()
    }

    fun readDictionary(uri: Uri) = runTask("Читаем словарь") {
        val name = withContext(Dispatchers.IO) {
            val displayName = documents.displayName(uri)
            documents.stage(uri)
            displayName
        }
        val document = try { parseDraft(name) } catch (e: Exception) {
            clearDraft()
            throw e
        }
        filename = name
        state["filename"] = name
        parsed = document
        state["returnFromImport"] = screen.name
        go(Screen.IMPORT)
    }

    fun importDictionary(name: String, entries: List<Entry>) = runTask("Сохраняем словарь") {
        withContext(Dispatchers.IO) { repo.importDictionary(name, entries) }
        parsed = null
        clearDraft()
        go(Screen.HOME)
        reload()
    }

    fun selectDictionary(id: Long) = runTask("Выбираем словарь") {
        withContext(Dispatchers.IO) { repo.selectDictionary(id) }
        reload()
        go(Screen.HOME)
    }

    fun renameDictionary(id: Long, name: String) = updateLibrary { repo.renameDictionary(id, name) }
    fun deleteDictionary(id: Long) = updateLibrary { repo.deleteDictionary(id) }
    fun resetProgress() {
        val id = library?.selectedId ?: return
        updateLibrary { repo.resetProgress(id) }
    }
    fun saveSettings(new: StudySettings) = updateLibrary { repo.saveSettings(new) }

    private fun updateLibrary(change: () -> Unit) = runTask("Сохраняем изменения") {
        withContext(Dispatchers.IO) { change() }
        reload()
    }

    fun start() = runTask("Готовим занятие") {
        val current = withContext(Dispatchers.IO) { repo.startSession() }
        if (current == null) {
            reload()
            val snapshot = library ?: return@runTask
            notice = when {
                !snapshot.availability.allows(snapshot.settings.mode) ->
                    Notice("Режим недоступен", when (snapshot.settings.mode) {
                        StudyMode.QUIZ -> "Для теста нужны четыре разных варианта перевода. Выберите карточки в настройках."
                        StudyMode.TYPING -> "Добавьте словарь с переводами или выберите карточки в настройках."
                        StudyMode.REVIEW -> "Сначала познакомьтесь со словами в режиме карточек."
                        StudyMode.CARDS -> "Добавьте словарь, чтобы начать занятие."
                    })
                else -> Notice("Все доступные слова пройдены", "Сейчас в этом режиме нет новых слов или повторений, для которых уже наступил срок. Уже знакомые слова можно открыть в режиме «Повторение».")
            }
        } else {
            session = current
            go(Screen.STUDY)
        }
    }

    fun rate(rating: Rating) {
        val current = session ?: return
        runTask("Сохраняем ответ") {
            val next = withContext(Dispatchers.IO) { repo.rate(current.id, current.index, rating) }
            session = next
            if (next == null) {
                completedCount = current.total
                state["completedCount"] = completedCount
                reload()
                go(Screen.COMPLETE)
            }
        }
    }

    fun export(uri: Uri) = runTask("Создаём резервную копию") {
        withContext(Dispatchers.IO) { documents.writeBackup(uri, repo::exportBackup) }
        notice = Notice("Копия сохранена", "Словари и прогресс записаны в выбранный файл.")
    }

    fun chooseRestore(uri: Uri?) {
        restoreUri = uri?.toString()
        state["restoreUri"] = restoreUri
    }

    fun restore() {
        val source = restoreUri ?: return
        chooseRestore(null)
        runTask("Восстанавливаем библиотеку") {
            withContext(Dispatchers.IO) { documents.open(Uri.parse(source)).use(repo::restoreBackup) }
            session = null
            reload()
            go(Screen.HOME)
            notice = Notice("Библиотека восстановлена", "Словари и прогресс загружены из резервной копии.")
        }
    }
}
