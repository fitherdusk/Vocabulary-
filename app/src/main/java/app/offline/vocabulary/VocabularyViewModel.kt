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
    private fun text(id: Int) = AppLanguage.localized(getApplication<Application>()).getString(id)
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
    var completedMilestone by mutableStateOf(state.get<Int>("completedMilestone"))
        private set
    var restoreUri by mutableStateOf(state.get<String>("restoreUri"))
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set

    init { initialize() }

    fun initialize() = runTask(text(R.string.opening_library)) {
        withContext(Dispatchers.IO) {
            repo.installBundledIfNeeded(text(R.string.frequent_serbian)) {
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
        notice = Notice(text(R.string.picker_unavailable), text(R.string.enable_picker))
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
        val fallback = text(R.string.operation_failed)
        val message = when (e) {
            is SQLiteFullException -> text(R.string.storage_full)
            is SecurityException, is FileNotFoundException -> text(R.string.file_unavailable)
            is VocabularyProblem -> e.display(AppLanguage.localized(getApplication<Application>()))
            is IOException -> text(R.string.file_io_error)
            else -> fallback
        }
        notice = Notice(text(R.string.error_title), message)
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

    fun readDictionary(uri: Uri) = runTask(text(R.string.reading_dictionary)) {
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

    fun importDictionary(name: String, entries: List<Entry>) = runTask(text(R.string.saving_dictionary)) {
        withContext(Dispatchers.IO) { repo.importDictionary(name, entries) }
        parsed = null
        clearDraft()
        go(Screen.HOME)
        reload()
    }

    fun selectDictionary(id: Long) = runTask(text(R.string.selecting_dictionary)) {
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

    private fun updateLibrary(change: () -> Unit) = runTask(text(R.string.saving_changes)) {
        withContext(Dispatchers.IO) { change() }
        reload()
    }

    fun start() = runTask(text(R.string.preparing_session)) {
        val current = withContext(Dispatchers.IO) { repo.startSession() }
        if (current == null) {
            reload()
            val snapshot = library ?: return@runTask
            notice = when {
                !snapshot.availability.allows(snapshot.settings.mode) ->
                    Notice(text(R.string.mode_unavailable), when (snapshot.settings.mode) {
                        StudyMode.QUIZ -> text(R.string.quiz_unavailable)
                        StudyMode.TYPING -> text(R.string.typing_unavailable)
                        StudyMode.REVIEW -> text(R.string.review_unavailable)
                        StudyMode.CARDS -> text(R.string.add_to_start)
                    })
                else -> Notice(text(R.string.all_done), text(R.string.no_words_due))
            }
        } else {
            session = current
            go(Screen.STUDY)
        }
    }

    fun rate(rating: Rating) {
        val current = session ?: return
        runTask(text(R.string.saving_answer)) {
            val next = withContext(Dispatchers.IO) { repo.rate(current.id, current.index, rating) }
            session = next
            if (next == null) {
                completedMilestone = withContext(Dispatchers.IO) { repo.milestoneForCompletion(current.id) }
                state["completedMilestone"] = completedMilestone
                completedCount = current.total
                state["completedCount"] = completedCount
                reload()
                go(Screen.COMPLETE)
            }
        }
    }

    fun export(uri: Uri) = runTask(text(R.string.creating_backup)) {
        withContext(Dispatchers.IO) { documents.writeBackup(uri, repo::exportBackup) }
        notice = Notice(text(R.string.backup_saved), text(R.string.backup_saved_description))
    }

    fun chooseRestore(uri: Uri?) {
        restoreUri = uri?.toString()
        state["restoreUri"] = restoreUri
    }

    fun restore() {
        val source = restoreUri ?: return
        chooseRestore(null)
        runTask(text(R.string.restoring_library)) {
            withContext(Dispatchers.IO) { documents.open(Uri.parse(source)).use(repo::restoreBackup) }
            session = null
            reload()
            go(Screen.HOME)
            notice = Notice(text(R.string.library_restored), text(R.string.library_restored_description))
        }
    }
}
