package app.offline.vocabulary.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.offline.vocabulary.importer.Entry
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class RepositoryPersistenceTest {
    private lateinit var context: Context
    private val dbName = "repository-${UUID.randomUUID()}.db"
    private lateinit var repo: VocabularyRepository
    private val entries = listOf(
        Entry("hello", "привет"), Entry("world", "мир"), Entry("house", "дом")
    )

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        repo = VocabularyRepository(context, dbName)
    }

    @After fun cleanUp() {
        repo.close()
        context.deleteDatabase(dbName)
    }

    private fun reopen() {
        repo.close()
        repo = VocabularyRepository(context, dbName)
    }

    private fun backup(): ByteArray = ByteArrayOutputStream().use { out ->
        repo.exportBackup(out)
        out.toByteArray()
    }

    @Test fun milestonesUsePastGradedDaysAndOnlyTheFirstCompletionAtEachThreshold() {
        var day = java.time.LocalDate.of(2025, 2, 18)
        fun now() = day.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        repo.close()
        repo = VocabularyRepository(context, dbName, ::now)
        repo.importDictionary("Original title", entries.take(1))
        fun finish(): Long {
            val card = requireNotNull(repo.startSession())
            assertNull(repo.milestoneForCompletion(card.id))
            repo.rate(card.id, card.index, Rating.KNOWN)
            return card.id
        }
        for (length in 1..20) {
            val first = finish()
            if (length == 1) repo.saveSettings(StudySettings(mode = StudyMode.REVIEW))
            assertEquals(length, repo.snapshot().stats.streak)
            assertEquals(length.takeIf { it in listOf(3, 7, 14, 20) }, repo.milestoneForCompletion(first))
            if (length == 20) {
                val bytes = backup()
                repo.close()
                repo = VocabularyRepository(context, dbName, ::now)
                repo.restoreBackup(bytes.inputStream())
                assertEquals(20, repo.milestoneForCompletion(first))
            }
            assertNull(repo.milestoneForCompletion(finish()))
            day = day.plusDays(1)
        }
        day = day.plusDays(1)
        assertNull(repo.milestoneForCompletion(finish()))
        assertEquals(1, repo.snapshot().stats.streak)
        day = day.plusDays(1)
        assertNull(repo.milestoneForCompletion(finish()))
        day = day.plusDays(1)
        assertEquals(3, repo.milestoneForCompletion(finish()))
    }

    @Test fun anOpenOrAbandonedSessionCountsOnlyDaysWithAnAnswer() {
        var date = java.time.LocalDate.of(2025, 12, 31)
        repo.close()
        repo = VocabularyRepository(context, dbName) {
            date.atTime(23, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        repo.importDictionary("Three entries", entries)
        val opened = requireNotNull(repo.startSession())
        assertEquals(0, repo.snapshot().stats.streak)
        repo.rate(opened.id, opened.index, Rating.AGAIN)
        repo.abandonSession(opened.id)
        assertEquals(1, repo.snapshot().stats.streak)
        date = date.plusDays(1)
        val next = requireNotNull(repo.startSession())
        var card = requireNotNull(repo.rate(next.id, next.index, Rating.KNOWN))
        assertEquals(2, repo.snapshot().stats.streak)
        assertNull(repo.milestoneForCompletion(next.id))
        date = date.plusDays(1)
        while (true) {
            card = repo.rate(card.id, card.index, Rating.KNOWN) ?: break
        }
        assertEquals(3, repo.snapshot().stats.streak)
        assertEquals(3, repo.milestoneForCompletion(next.id))
    }

    @Test fun dictionaryMasteryReflectsPartialLearningLapsesAndReset() {
        val dictionary = repo.importDictionary("Одно слово", entries.take(1))
        fun answer(rating: Rating) {
            val session = requireNotNull(repo.startSession())
            repo.rate(session.id, session.index, rating)
        }
        fun checkPercent(expected: Double, learned: Int = 0) {
            val stats = repo.snapshot().stats
            assertEquals(expected, stats.masteryPercent, 0.000001)
            assertEquals(learned, stats.learned)
        }
        checkPercent(0.0)
        answer(Rating.AGAIN)
        checkPercent(10.0)
        repo.saveSettings(StudySettings(mode = StudyMode.REVIEW))
        answer(Rating.HARD)
        checkPercent(10.0)
        answer(Rating.KNOWN)
        checkPercent(40.0)
        answer(Rating.KNOWN)
        checkPercent(70.0)
        reopen()
        checkPercent(70.0)
        answer(Rating.KNOWN)
        checkPercent(100.0, learned = 1)
        val mastered = backup()
        answer(Rating.AGAIN)
        checkPercent(10.0)
        repo.resetProgress(dictionary)
        checkPercent(0.0)
        repo.restoreBackup(mastered.inputStream())
        checkPercent(100.0, learned = 1)
    }

    @Test fun restartResumesExactCardAndRetriedGradeIsCountedOnce() {
        assertTrue(repo.snapshot().dictionaries.isEmpty())
        val dictionary = repo.importDictionary("Слова", entries)
        val settings = StudySettings(newPerSession = 2, dailyGoal = 17, fontSize = 41)
        repo.saveSettings(settings)
        val first = requireNotNull(repo.startSession())
        val second = requireNotNull(repo.rate(first.id, first.index, Rating.AGAIN))
        assertEquals(1, second.index)
        val snapshot = repo.snapshot()

        reopen()
        assertEquals(dictionary, repo.snapshot().selectedId)
        assertEquals(settings, repo.snapshot().settings)
        assertEquals(second, repo.startSession())
        assertEquals(second, repo.rate(first.id, first.index, Rating.KNOWN))
        assertEquals(snapshot, repo.snapshot())
        assertEquals(1, snapshot.stats.learning)
        assertEquals(2, snapshot.stats.newWords)
        assertEquals(1, snapshot.stats.sessions)
        assertEquals(1, snapshot.stats.today)
        assertNull(repo.rate(second.id, second.index, Rating.HARD))

        val finished = repo.snapshot()
        assertNull(repo.rate(second.id, second.index, Rating.HARD))
        assertEquals(finished, repo.snapshot())
        assertEquals(2, finished.stats.learning)
        assertEquals(20.0 / 3, finished.stats.masteryPercent, 0.000001)
        assertEquals(2, finished.stats.today)
    }

    @Test fun backupRestoresSettingsHistoryAndExactPendingQueue() {
        repo.importDictionary("Основной", entries)
        repo.saveSettings(StudySettings(mode = StudyMode.TYPING, reverse = true, animations = false, fontSize = 30))
        val first = requireNotNull(repo.startSession())
        val next = requireNotNull(repo.rate(first.id, first.index, Rating.KNOWN))
        val original = repo.snapshot()
        assertEquals(40.0 / 3, original.stats.masteryPercent, 0.000001)
        val bytes = backup()

        repo.importDictionary("Другой", listOf(Entry("world")))
        repo.saveSettings(StudySettings())
        repo.restoreBackup(bytes.inputStream())
        reopen()

        assertEquals(original, repo.snapshot())
        assertEquals(next, repo.activeSession())
        assertEquals(next, repo.startSession())
        assertEquals(1, repo.snapshot().stats.streak)
        val last = requireNotNull(repo.rate(next.id, next.index, Rating.HARD))
        assertEquals(2, last.index)
        assertNull(repo.rate(last.id, last.index, Rating.KNOWN))
        assertEquals(3, repo.snapshot().stats.today)
    }

    @Test fun corruptAnswerNearEndOfBackupRollsBackReplacement() {
        repo.importDictionary("Копия", entries)
        val first = requireNotNull(repo.startSession())
        repo.rate(first.id, first.index, Rating.KNOWN)
        val corrupted = JSONArray(String(backup(), Charsets.UTF_8))
        val answersTable = corrupted.getJSONArray(corrupted.length() - 1)
        assertEquals("answers", answersTable.getString(0))
        answersTable.getJSONArray(1).put(3, "INVALID_GRADE")

        repo.importDictionary("Текущий", entries.take(2))
        val current = requireNotNull(repo.startSession())
        val before = repo.snapshot()
        assertThrows(IllegalArgumentException::class.java) {
            repo.restoreBackup(corrupted.toString().byteInputStream())
        }
        reopen()
        assertEquals(before, repo.snapshot())
        assertEquals(current, repo.activeSession())
    }

    @Test fun resetOnlyAffectsRequestedDictionary() {
        val firstId = repo.importDictionary("Первый", entries.take(1))
        val first = requireNotNull(repo.startSession())
        repo.rate(first.id, first.index, Rating.HARD)

        val secondId = repo.importDictionary("Второй", entries)
        val second = requireNotNull(repo.startSession())
        val active = requireNotNull(repo.rate(second.id, second.index, Rating.KNOWN))
        val before = repo.snapshot()
        repo.resetProgress(firstId)
        assertEquals(before, repo.snapshot())
        assertEquals(active, repo.activeSession())
        repo.selectDictionary(firstId)
        assertEquals(1, repo.snapshot().stats.newWords)
        assertEquals(0, repo.snapshot().stats.sessions)
        repo.selectDictionary(secondId)
        assertEquals(before.stats, repo.snapshot().stats)
    }

    @Test fun deletionAndRestoringEmptyLibraryDoNotReinstallBundledFile() {
        var loads = 0
        fun install() = repo.installBundledIfNeeded("Файл") { loads++; entries }
        val empty = backup()
        install()
        install()
        assertEquals(1, loads)
        repo.deleteDictionary(requireNotNull(repo.snapshot().selectedId))
        reopen()
        install()
        assertTrue(repo.snapshot().dictionaries.isEmpty())
        assertEquals(1, loads)

        repo.close()
        context.deleteDatabase(dbName)
        repo = VocabularyRepository(context, dbName)
        repo.restoreBackup(empty.inputStream())
        install()
        assertTrue(repo.snapshot().dictionaries.isEmpty())
        assertEquals(1, loads)
    }
}
