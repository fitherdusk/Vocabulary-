package app.offline.vocabulary.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.offline.vocabulary.importer.Entry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class StudyPoolTest {
    private val name = "pool-test.sqlite"
    private lateinit var context: Context
    private lateinit var repo: VocabularyRepository

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        repo = VocabularyRepository(context, name)
    }

    @After fun close() {
        repo.close()
        context.deleteDatabase(name)
    }

    @Test fun noTranslationsSupportsForwardCardsAndSeenWordsOnlyReview() {
        repo.importDictionary("Без перевода", listOf(Entry("prvi"), Entry("drugi")))
        assertFalse(repo.snapshot().availability.quiz)
        assertFalse(repo.snapshot().availability.typing)
        assertFalse(repo.snapshot().availability.review)
        repo.saveSettings(StudySettings(newPerSession = 1, reverse = true))
        val first = repo.startSession()!!
        assertFalse(first.reverse)
        assertEquals("prvi", first.card.prompt)
        assertEquals("", first.card.answer)
        assertNull(repo.rate(first.id, 0, Rating.KNOWN))
        repo.saveSettings(StudySettings(mode = StudyMode.REVIEW))
        assertEquals("prvi", repo.startSession()!!.card.prompt)
    }

    @Test fun fourUniqueValidChoicesInBothDirectionsAndNoHomonymDistractors() {
        repo.importDictionary("Таблица", listOf(Entry("A","1"),Entry("A","2"),Entry("B","3"),
            Entry("C","4"),Entry("D","5"),Entry("E","6"),Entry("F","")))
        for (reverse in listOf(false,true)) {
            repo.saveSettings(StudySettings(mode=StudyMode.QUIZ, reverse=reverse))
            assertTrue(repo.snapshot().availability.quiz)
            val session = repo.startSession()!!
            assertEquals(4, session.card.options.distinct().size)
            assertTrue(session.card.answer in session.card.options)
            if (!reverse) {
                assertEquals("A", session.card.prompt)
                assertEquals("1", session.card.answer)
                assertFalse("2" in session.card.options)
            } else {
                assertEquals("1",session.card.prompt)
                assertEquals("A",session.card.answer)
            }
            repo.abandonSession(session.id)
        }
        repo.saveSettings(StudySettings(mode=StudyMode.TYPING))
        assertEquals(setOf("1","2"),repo.startSession()!!.card.acceptedAnswers.toSet())
    }

    @Test fun overlappingRussianMeaningsCannotBecomeWrongChoices() {
        val pairs = listOf(Entry("A","один; первый"), Entry("B","первый"), Entry("C","два"),
            Entry("D","три"), Entry("E","четыре"))
        repo.importDictionary("Значения", pairs)
        for (reverse in listOf(false, true)) {
            repo.saveSettings(StudySettings(mode = StudyMode.QUIZ, reverse = reverse))
            val session = requireNotNull(repo.startSession())
            assertEquals(4, session.card.options.size)
            assertFalse((if (reverse) "B" else "первый") in session.card.options)
            repo.abandonSession(session.id)
        }
    }

    @Test fun fewerThanFourDistinctAnswersDisablesQuizAndKeepsTyping() {
        repo.importDictionary("Таблица", listOf(Entry("A","1"), Entry("B","1"), Entry("C","2"), Entry("D","3")))
        assertFalse(repo.snapshot().availability.quiz)
        assertTrue(repo.snapshot().availability.typing)
    }
}
