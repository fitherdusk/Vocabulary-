package app.offline.vocabulary

import android.app.Application
import app.offline.vocabulary.data.VocabularyRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class VocabularyApplication : Application() {
    val repository by lazy { VocabularyRepository(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}
