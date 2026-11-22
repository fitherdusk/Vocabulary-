package app.offline.vocabulary

import android.content.Context
import androidx.annotation.StringRes

internal class VocabularyProblem(
    @StringRes val resource: Int,
    vararg val arguments: Any,
    cause: Throwable? = null
) : IllegalArgumentException("Vocabulary error $resource", cause) {
    fun display(context: Context): String = context.getString(resource, *arguments)
}

internal fun requireInput(condition: Boolean, @StringRes resource: Int, vararg arguments: Any) {
    if (!condition) throw VocabularyProblem(resource, *arguments)
}
