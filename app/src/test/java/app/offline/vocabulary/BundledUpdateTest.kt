package app.offline.vocabulary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.offline.vocabulary.data.Rating
import app.offline.vocabulary.data.StudyCard
import app.offline.vocabulary.data.VocabularyRepository
import app.offline.vocabulary.importer.Entry
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BundledUpdateTest {
    @Test fun upgradeAndLegacyRestorePreserveRenamedDictionaryAndPendingAnswer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "legacy-content-test.sqlite"
        val oldWords = BundledDictionary.read(context.assets).map { Entry(it.word) }
        val backup = ByteArrayOutputStream()
        VocabularyRepository(context, name).use { repo ->
            repo.installBundledIfNeeded(BundledDictionary.name) { oldWords }
            repo.renameDictionary(1, "Мой сербский")
            val session = repo.startSession()!!
            repo.rate(session.id, 0, Rating.KNOWN)
            repo.exportBackup(backup)
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = 1 }
        VocabularyRepository(context, name).use { repo ->
            fun checkContent() {
                val snapshot = repo.snapshot()
                assertEquals("Мой сербский", snapshot.dictionaries.single().name)
                assertEquals(1, snapshot.stats.today)
                assertTrue(snapshot.availability.quiz)
                assertTrue(snapshot.availability.typing)
                val session = repo.activeSession()!!
                assertEquals(1, session.index)
                assertEquals("је", session.card.prompt)
                assertEquals("йэ", session.card.transcription)
                assertTrue(session.card.answer.startsWith("есть"))
            }
            checkContent()
            repo.resetProgress(1)
            repo.restoreBackup(backup.toByteArray().inputStream())
            checkContent()
        }
    }

    @Test fun russianReadingGlossesAndUncertainSpellingsAreDistinct() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cards = BundledDictionary.read(context.assets).associateBy { it.word }
        assertEquals(1917, cards.size)
        val thanks = cards.getValue("хвала")
        assertEquals("спасибо", thanks.translation)
        assertEquals("хва́ла", thanks.transcription)
        assertEquals("знаю", cards.getValue("знам").translation)
        assertEquals("лю́ди", cards.getValue("људи").transcription)
        val damaged = cards.getValue("марy")
        assertTrue(damaged.translation.isEmpty())
        assertTrue(damaged.transcription.isEmpty())
        assertTrue(damaged.extra.isNotBlank())
    }

    @Test fun typingAcceptsAnyGlossButDoesNotSplitTheSerbianAnswer() {
        val card = StudyCard(1, "је", "есть; является (он, она, оно)", "йэ", "", "", emptyList())
        assertTrue(card.acceptsTypedAnswer(" ЯВЛЯЕТСЯ ", false))
        assertTrue(card.acceptsTypedAnswer("есть", false))
        assertFalse(card.acceptsTypedAnswer("оно", false))
        assertFalse(card.acceptsTypedAnswer("есть", true))
    }
}
