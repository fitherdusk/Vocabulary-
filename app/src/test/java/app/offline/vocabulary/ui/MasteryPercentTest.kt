package app.offline.vocabulary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MasteryPercentTest {
    private fun formatMasteryPercent(percent: Double) =
        app.offline.vocabulary.ui.formatMasteryPercent(percent, java.util.Locale.forLanguageTag("ru"))

    @Test fun firstAnswerShowsEvenInLargeDictionariesAndCompletionIsExact() {
        assertEquals("0%", formatMasteryPercent(0.0))
        assertEquals("<0,01%", formatMasteryPercent(10.0 / 100_000))
        assertEquals("0,01%", formatMasteryPercent(10.0 / 1917))
        assertEquals("0,02%", formatMasteryPercent(40.0 / 1917))
        assertEquals("32,5%", formatMasteryPercent(32.5))
        assertEquals(">99,99%", formatMasteryPercent(99.9999))
        assertEquals("100%", formatMasteryPercent(100.0))
    }
}
