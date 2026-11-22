package app.offline.vocabulary

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.offline.vocabulary.data.Rating
import app.offline.vocabulary.data.StudySettings
import app.offline.vocabulary.importer.Entry
import app.offline.vocabulary.data.StudyMode
import app.offline.vocabulary.data.VocabularyRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h830dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivitySmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(sdk = [35], qualifiers = "en-w393dp-h830dp-port-mdpi")
    fun aTwentyDayStreakAppearsOnOneCompletionScreen() {
        waitForText("Frequent Serbian")
        val zone = java.time.ZoneId.systemDefault()
        var date = java.time.LocalDate.now(zone).minusDays(19)
        VocabularyRepository(compose.activity.applicationContext,
            currentTime = { date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }).use { history ->
            history.importDictionary("My word list", listOf(Entry("hvala", "thank you")))
            repeat(19) { index ->
                val session = requireNotNull(history.startSession())
                history.rate(session.id, session.index, Rating.KNOWN)
                if (index == 0) history.saveSettings(StudySettings(mode = StudyMode.REVIEW))
                date = date.plusDays(1)
            }
        }
        compose.activityRule.scenario.recreate()
        waitForText("My word list")
        val milestone = "Keep it up! You’ve practiced 20 days in a row."
        repeat(2) { index ->
            compose.onNodeWithContentDescription("Start a session").performClick()
            waitForText("hvala")
            compose.onNodeWithText("Show translation").performClick()
            waitForText("thank you")
            compose.onNodeWithText("Know it").performClick()
            waitForText("Session complete")
            if (index == 0) {
                compose.onNodeWithText(milestone).assertIsDisplayed()
                compose.activityRule.scenario.recreate()
                waitForText(milestone)
            } else {
                compose.onNodeWithText(milestone).assertDoesNotExist()
            }
            compose.onNodeWithText("Back to home").performClick()
            waitForText("My word list")
        }
    }

    @Test
    @Config(sdk = [28, 35], qualifiers = "ru-w393dp-h830dp-port-mdpi")
    fun switchesInterfaceLanguageAndKeepsTheActiveDictionaryAndHistory() {
        waitForText("Частотный сербский")
        compose.onNodeWithContentDescription("Начать занятие").performClick()
        waitForText("да")
        compose.onNodeWithText("Показать перевод").performClick()
        waitForText("да; что; чтобы")
        compose.onNodeWithText("Знаю").performClick()
        waitForText("је")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Русский / English").performClick()
        compose.onNodeWithText("English").performClick()
        waitForText("New words per session")
        compose.activityRule.scenario.recreate()
        waitForText("New words per session")
        compose.onNodeWithText("Home").performClick()
        waitForText("0.02%")
        compose.onNodeWithContentDescription("Start a session").performClick()
        waitForText("је")
        compose.onNodeWithText("Show translation").performClick()
        waitForText("есть; является (он, она, оно)")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Русский / English").performClick()
        compose.onNodeWithText("Русский").performClick()
        waitForText("Новых слов за занятие")
    }

    @Test
    @Config(sdk = [31, 33, 34, 35])
    fun installedAppOpensBundledDictionaryAndResumesSecondCard() {
        waitForText("Частотный сербский")
        val context = compose.activity.applicationContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
        VocabularyRepository(context).use { repository ->
            val library = repository.snapshot()
            assertEquals(1917, library.dictionaries.single().total)
            assertEquals(StudyMode.CARDS, library.settings.mode)
            assertTrue(library.availability.typing)
            assertTrue(library.availability.quiz)
        }
        if (android.os.Build.VERSION.SDK_INT == 35) captureHomeIfRequested()

        compose.onNodeWithContentDescription("Статистика").performClick()
        waitForText("Изучено 0 из 1917")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Начать занятие").performClick()
        waitForText("да")
        compose.onNodeWithText("Как читать по-русски").assertIsDisplayed()
        compose.onNodeWithText("Показать перевод").performClick()
        waitForText("да; что; чтобы")
        compose.onNodeWithText("Знаю").performClick()
        waitForText("је")

        compose.activityRule.scenario.recreate()
        waitForText("је")
        compose.onNodeWithContentDescription("Назад").performClick()
        waitForText("0,02%")
        compose.onNodeWithContentDescription("Статистика").performClick()
        waitForText("0,02%")
        compose.onNodeWithText("Изучено 0 из 1917").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Начать занятие").performClick()
        waitForText("2 / 10")
        compose.onNodeWithText("је").assertIsDisplayed()

        VocabularyRepository(context).use { reopened ->
            val stats = reopened.snapshot().stats
            assertEquals(1, stats.learning)
            assertEquals(1916, stats.newWords)
            assertEquals("је", reopened.activeSession()?.card?.prompt)
        }
    }

    @Test
    fun filePickerMappingQuizAndTypedAnswers() {
        waitForText("Частотный сербский")
        val context = compose.activity.applicationContext
        val fixture = File(context.cacheDir, "mapping.csv").apply {
            writeText(
                """Поле B;Поле A
                    |dst-1;src-1
                    |dst-2;src-2
                    |dst-3;src-3
                    |dst-4;src-4
                    |dst-1;src-1
                    |unused;
                """.trimMargin()
            )
        }
        compose.onNodeWithText("Добавить словарь").performClick()
        selectLocalFile(Intent.ACTION_OPEN_DOCUMENT, fixture)
        waitForText("Соответствие столбцов")
        compose.onNodeWithText("Первая строка содержит заголовки").performClick()
        waitForText("Поле A")
        compose.onNodeWithText("Слово").performClick()
        compose.onNode(hasText("Поле A") and hasText("src-1")).performClick()
        val counts = "К импорту 4   ·   Дубликатов 1   ·   Пустых 1"
        waitForText(counts)
        compose.onNode(hasSetTextAction()).performTextReplacement("Проверка импорта")

        compose.activityRule.scenario.recreate()
        waitForText(counts)
        compose.onNodeWithText("Импортировать словарь").performScrollTo().performClick()
        waitForText("Изучено: 0 / 4")
        compose.onNodeWithText("Проверка импорта").assertIsDisplayed()

        startInMode("Тест")
        waitForText("Выберите перевод")
        compose.onNodeWithText("src-1").assertIsDisplayed()
        for (n in 1..4) compose.onNodeWithText("dst-$n").assertExists()
        compose.onNodeWithText("dst-1").performScrollTo().performClick()
        compose.onNodeWithText("Верно").assertExists()
        compose.onNodeWithText("Дальше").performScrollTo().performClick()
        waitForText("src-2")
        compose.onNodeWithContentDescription("Назад").performClick()

        startInMode("Ввод ответа")
        waitForText("src-2")
        compose.onNode(hasSetTextAction()).performTextReplacement(" DST-2 ")
        compose.onNodeWithText("Проверить").performScrollTo().performClick()
        compose.onNodeWithText("Верно").assertExists()
        compose.onNodeWithText("Дальше").performScrollTo().performClick()
        waitForText("src-3")
        VocabularyRepository(context).use { reopened ->
            val library = reopened.snapshot()
            assertEquals(2, library.stats.today)
            assertEquals(2, library.stats.learning)
            assertEquals(StudyMode.TYPING, library.settings.mode)
        }
        for (n in 3..4) {
            waitForText("src-$n")
            compose.onNode(hasSetTextAction()).performTextReplacement(if (n == 3) "ошибка" else "dst-4")
            compose.onNodeWithText("Проверить").performScrollTo().performClick()
            compose.onNodeWithText(if (n == 3) "Дальше" else "Завершить").performScrollTo().performClick()
        }
        waitForText("Освоено 32,5% словаря")
        compose.onNodeWithText("На главную").performClick()
        waitForText("32,5%")
    }

    @Test
    fun backupFileRestoresConfirmedResetAndPendingCard() {
        waitForText("Частотный сербский")
        compose.onNodeWithContentDescription("Начать занятие").performClick()
        waitForText("да")
        compose.onNodeWithText("Как читать по-русски").assertIsDisplayed()
        compose.onNodeWithText("Показать перевод").performClick()
        waitForText("да; что; чтобы")
        compose.onNodeWithText("Знаю").performClick()
        waitForText("је")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Настройки").performClick()

        compose.onNodeWithText("made by ai").performScrollTo().assertIsDisplayed()
        val context = compose.activity.applicationContext
        val backup = File(context.cacheDir, "roundtrip.json")
        compose.onNodeWithText("Сохранить резервную копию").performScrollTo().performClick()
        selectLocalFile(Intent.ACTION_CREATE_DOCUMENT, backup)
        waitForText("Копия сохранена")
        compose.onNodeWithText("Понятно").performClick()

        VocabularyRepository(context).use { repo ->
            val snapshot = repo.snapshot()
            val pending = requireNotNull(repo.activeSession())
            compose.onNodeWithText("Сбросить прогресс").performScrollTo().performClick()
            compose.onNodeWithText("Отмена").performClick()
            assertEquals(snapshot, repo.snapshot())
            compose.onNodeWithText("Сбросить прогресс").performScrollTo().performClick()
            compose.onNodeWithText("Сбросить").performClick()
            compose.waitUntil(timeoutMillis = 15_000) { repo.snapshot().stats.newWords == 1917 }

            compose.onNodeWithText("Восстановить из копии").performScrollTo().performClick()
            selectLocalFile(Intent.ACTION_OPEN_DOCUMENT, backup)
            waitForText("Восстановить библиотеку?")
            compose.onNodeWithText("Отмена").performClick()
            assertEquals(1917, repo.snapshot().stats.newWords)

            compose.onNodeWithText("Восстановить из копии").performScrollTo().performClick()
            selectLocalFile(Intent.ACTION_OPEN_DOCUMENT, backup)
            waitForText("Восстановить библиотеку?")
            compose.onNodeWithText("Восстановить").performClick()
            waitForText("Библиотека восстановлена")
            compose.onNodeWithText("Понятно").performClick()
            assertEquals(snapshot, repo.snapshot())
            assertEquals(pending, repo.activeSession())
        }

        compose.onNodeWithContentDescription("Начать занятие").performClick()
        waitForText("2 / 10")
        compose.onNodeWithText("је").assertIsDisplayed()
    }

    private fun selectLocalFile(action: String, file: File) {
        compose.runOnIdle {
            val activity = shadowOf(compose.activity)
            val request = activity.nextStartedActivityForResult
            assertEquals(action, request.intent.action)
            assertEquals(true, request.intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
            activity.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
    }

    private fun startInMode(label: String) {
        waitForText("Настройки")
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Режим").performClick()
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasText("Режим") and hasText(label)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Главная").performClick()
        compose.onNodeWithContentDescription("Начать занятие").performClick()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun captureHomeIfRequested() {
        val path = System.getenv("VOCABULARY_HOME_SCREENSHOT") ?: return
        compose.runOnIdle {
            val decorView = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
            decorView.draw(Canvas(bitmap))
            File(path).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
