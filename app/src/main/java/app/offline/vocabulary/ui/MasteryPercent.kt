package app.offline.vocabulary.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal fun formatMasteryPercent(percent: Double): String {
    val number = DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("ru"))).format(percent)
    return when {
        number == "0" && percent > 0 -> "<0,01%"
        number == "100" && percent < 100 -> ">99,99%"
        else -> "$number%"
    }
}
