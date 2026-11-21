package app.offline.vocabulary.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

internal data class Progress(
    val attempts: Int = 0, val successes: Int = 0, val errors: Int = 0,
    val ease: Double = 2.5, val interval: Int = 0, val last: Long = 0, val due: Long = 0
)

/**
 * Three-grade SM-2 adaptation. KNOWN follows 1, 6, then interval × previous ease
 * calendar days. HARD restarts the success run and returns tomorrow. AGAIN returns
 * after 10 minutes. Grades 5, 3 and 0 adjust ease by SM-2, with a floor of 1.3.
 * Three consecutive KNOWN answers indicate mastery. History is independent of the run.
 */
internal fun schedule(old: Progress, rating: Rating, now: Long, zone: ZoneId): Progress {
    val grade = when (rating) { Rating.KNOWN -> 5; Rating.HARD -> 3; Rating.AGAIN -> 0 }
    val penalty = 5 - grade
    val ease = (old.ease + 0.1 - penalty * (0.08 + penalty * 0.02)).coerceIn(1.3, 4.0)
    val run = if (rating == Rating.KNOWN) old.successes + 1 else 0
    val days = when {
        rating == Rating.AGAIN -> 0
        rating == Rating.HARD || run == 1 -> 1
        run == 2 -> 6
        else -> (old.interval * old.ease).roundToInt().coerceIn(1, 36500)
    }
    val next = if (days == 0) now + 600_000L else Instant.ofEpochMilli(now)
        .atZone(zone).toLocalDate().plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    return Progress(old.attempts + 1, run, old.errors + (if (rating == Rating.AGAIN) 1 else 0),
        ease, days, now, next)
}
