package app.offline.vocabulary.data

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepetitionTest {
    private val zone = ZoneId.of("Europe/Belgrade")
    private val now = LocalDateTime.parse("2025-03-29T22:00:00").atZone(zone).toInstant().toEpochMilli()

    @Test fun knownAnswersFollowOneSixThenMultiplicationAndSetMastery() {
        val first = schedule(Progress(), Rating.KNOWN, now, zone)
        val second = schedule(first, Rating.KNOWN, first.due, zone)
        val third = schedule(second, Rating.KNOWN, second.due, zone)
        assertEquals(listOf(1, 6, 16), listOf(first.interval, second.interval, third.interval))
        assertEquals(3, third.successes)
        assertEquals(3, third.attempts)
        assertEquals(0, third.errors)
        assertEquals(second.due, third.last)
        assertTrue(first.due < second.due && second.due < third.due)
    }

    @Test fun againComesBackInTenMinutesAndKeepsLifetimeHistory() {
        val old = Progress(8, 3, 2, 2.6, 15, now - 30000, now)
        val again = schedule(old, Rating.AGAIN, now, zone)
        assertEquals(9, again.attempts)
        assertEquals(0, again.successes)
        assertEquals(3, again.errors)
        assertEquals(0, again.interval)
        assertEquals(now + Duration.ofMinutes(10).toMillis(), again.due)
        assertTrue(again.ease < old.ease)
    }

    @Test fun hardReturnsNextLocalDateAndDoesNotCountAsError() {
        val after = schedule(Progress(5, 4, 1, 2.4, 35, now - 1000, now), Rating.HARD, now, zone)
        val nextMidnight = LocalDateTime.parse("2025-03-30T00:00:00").atZone(zone).toInstant().toEpochMilli()
        assertEquals(nextMidnight, after.due)
        assertEquals(1, after.interval)
        assertEquals(0, after.successes)
        assertEquals(1, after.errors)
        // Six calendar days cross the spring-forward boundary.
        val dst = schedule(Progress(1, 1, 0), Rating.KNOWN, nextMidnight, zone)
        assertEquals(Duration.ofHours(143).toMillis(), dst.due - nextMidnight)
    }

    @Test fun repeatedLapsesClampEaseAndPermitRecovery() {
        var progress = Progress()
        repeat(30) { progress = schedule(progress, Rating.AGAIN, now, zone) }
        assertEquals(1.3, progress.ease, 0.00001)
        val recovery = schedule(progress, Rating.KNOWN, now, zone)
        assertEquals(1, recovery.interval)
        assertEquals(30, recovery.errors)
    }
}
