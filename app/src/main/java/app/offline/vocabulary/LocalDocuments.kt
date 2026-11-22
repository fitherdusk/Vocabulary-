package app.offline.vocabulary

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal class LocalDocuments(private val context: Context) {
    val draft get() = File(context.cacheDir, "import-preview")

    fun displayName(uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return name?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/') ?: AppLanguage.localized(context).getString(R.string.default_file_name)
    }

    fun stage(uri: Uri) {
        val temporary = File.createTempFile("import-", ".tmp", context.cacheDir)
        try {
            open(uri).use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            if (!temporary.renameTo(draft)) throw VocabularyProblem(R.string.error_stage_file)
        } finally {
            temporary.delete()
        }
    }

    fun open(uri: Uri): InputStream = context.contentResolver.openInputStream(uri)
        ?: throw VocabularyProblem(R.string.error_open_file)

    fun writeBackup(uri: Uri, write: (OutputStream) -> Unit) {
        val temporary = File.createTempFile("backup-", ".tmp", context.cacheDir)
        try {
            temporary.outputStream().buffered().use(write)
            val destination = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw VocabularyProblem(R.string.error_write_file)
            destination.use { output -> temporary.inputStream().use { it.copyTo(output) } }
        } finally {
            temporary.delete()
        }
    }
}
