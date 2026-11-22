package app.offline.vocabulary

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Android 13+ shares this choice with the system's per-app language settings. */
internal object AppLanguage {
    private fun preferences(context: Context) =
        context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE)

    fun selected(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else preferences(context).getString("language", "").orEmpty()

    fun set(context: Context, tag: String) {
        require(tag in setOf("", "ru", "en"))
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else preferences(context).edit().putString("language", tag).apply()
    }

    fun localized(base: Context): Context {
        val language = selected(base)
        if (language.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.substringBefore(',')))
        return base.createConfigurationContext(config)
    }
}
