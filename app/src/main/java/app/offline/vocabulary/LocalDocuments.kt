package app.offline.vocabulary

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class LocalDocuments(private val context: Context) {
    val draft get() = File(context.cacheDir, "import-preview")

    fun displayName(uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return name?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Словарь.txt"
    }

    fun stage(uri: Uri) {
        val temporary = File.createTempFile("import-", ".tmp", context.cacheDir)
        try {
            open(uri).use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            if (!temporary.renameTo(draft)) throw IOException("Не удалось сохранить файл для просмотра.")
        } finally {
            temporary.delete()
        }
    }

    fun open(uri: Uri): InputStream = context.contentResolver.openInputStream(uri)
        ?: throw IOException("Не удалось открыть файл. Выберите его ещё раз.")

    fun writeBackup(uri: Uri, write: (OutputStream) -> Unit) {
        val temporary = File.createTempFile("backup-", ".tmp", context.cacheDir)
        try {
            temporary.outputStream().buffered().use(write)
            val destination = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Не удалось открыть файл для записи.")
            destination.use { output -> temporary.inputStream().use { it.copyTo(output) } }
        } finally {
            temporary.delete()
        }
    }
}
