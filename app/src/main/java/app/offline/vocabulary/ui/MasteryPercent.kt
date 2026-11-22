package app.offline.vocabulary.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
internal fun masteryLabel(percent: Double): String =
    formatMasteryPercent(percent, LocalConfiguration.current.locales[0])

internal fun formatMasteryPercent(percent: Double, locale: Locale): String {
    val format = DecimalFormat("0.##", DecimalFormatSymbols(locale))
    val number = format.format(percent)
    return when {
        number == "0" && percent > 0 -> "<${format.format(0.01)}%"
        number == "100" && percent < 100 -> ">${format.format(99.99)}%"
        else -> "$number%"
    }
}
