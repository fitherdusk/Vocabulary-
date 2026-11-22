package app.offline.vocabulary.data

import android.content.Context
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.offline.vocabulary.BundledDictionary
import app.offline.vocabulary.R
import app.offline.vocabulary.VocabularyProblem

internal class VocabularyDatabase(private val context: Context, name: String) : SQLiteOpenHelper(context, name, null, 2) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE installation (id INTEGER PRIMARY KEY CHECK(id=1))")
        db.execSQL("""CREATE TABLE dictionaries (
            id INTEGER PRIMARY KEY AUTOINCREMENT CHECK(id>0), name TEXT NOT NULL CHECK(length(trim(name))>0))""")
        db.execSQL("""CREATE TABLE capabilities (
            dictionary_id INTEGER PRIMARY KEY REFERENCES dictionaries(id) ON DELETE CASCADE,
            translated INTEGER NOT NULL, quiz_forward INTEGER NOT NULL, quiz_reverse INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE words (
            id INTEGER PRIMARY KEY AUTOINCREMENT CHECK(id>0),
            dictionary_id INTEGER NOT NULL REFERENCES dictionaries(id) ON DELETE CASCADE,
            word TEXT NOT NULL CHECK(length(trim(word))>0), translation TEXT NOT NULL, transcription TEXT NOT NULL,
            example TEXT NOT NULL, extra TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),
            successes INTEGER NOT NULL DEFAULT 0 CHECK(successes BETWEEN 0 AND attempts),
            errors INTEGER NOT NULL DEFAULT 0 CHECK(errors BETWEEN 0 AND attempts),
            ease REAL NOT NULL DEFAULT 2.5 CHECK(ease BETWEEN 1.3 AND 4),
            interval_days INTEGER NOT NULL DEFAULT 0 CHECK(interval_days BETWEEN 0 AND 36500),
            last INTEGER NOT NULL DEFAULT 0 CHECK(last>=0), due INTEGER NOT NULL DEFAULT 0 CHECK(due>=0)
        )""")
        db.execSQL("CREATE INDEX words_dictionary ON words(dictionary_id, due)")
        db.execSQL("CREATE INDEX words_seen ON words(dictionary_id) WHERE attempts>0")
        db.execSQL("CREATE INDEX words_translated_seen ON words(dictionary_id) WHERE attempts>0 AND translation<>''")
        db.execSQL("""CREATE TABLE settings (
            id INTEGER PRIMARY KEY CHECK(id=1), selected INTEGER REFERENCES dictionaries(id) ON DELETE SET NULL,
            new_limit INTEGER NOT NULL DEFAULT 10 CHECK(new_limit BETWEEN 1 AND 2147483647),
            goal INTEGER NOT NULL DEFAULT 20 CHECK(goal BETWEEN 1 AND 2147483647),
            mode TEXT NOT NULL DEFAULT 'CARDS' CHECK(mode IN ('CARDS','QUIZ','TYPING','REVIEW')),
            reverse INTEGER NOT NULL DEFAULT 0 CHECK(reverse IN (0,1)),
            animations INTEGER NOT NULL DEFAULT 1 CHECK(animations IN (0,1)),
            font INTEGER NOT NULL DEFAULT 34 CHECK(font BETWEEN 12 AND 100),
            random INTEGER NOT NULL DEFAULT 0 CHECK(random IN (0,1)))""")
        db.execSQL("INSERT INTO settings (id) VALUES (1)")
        db.execSQL("""CREATE TABLE sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT CHECK(id>0),
            dictionary_id INTEGER NOT NULL REFERENCES dictionaries(id) ON DELETE CASCADE,
            mode TEXT NOT NULL CHECK(mode IN ('CARDS','QUIZ','TYPING','REVIEW')), reverse INTEGER NOT NULL CHECK(reverse IN (0,1)),
            position INTEGER NOT NULL DEFAULT 0 CHECK(position>=0), total INTEGER NOT NULL CHECK(total>0),
            status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','finished','abandoned')),
            CHECK(position<=total))""")
        db.execSQL("CREATE UNIQUE INDEX one_active_session ON sessions(status) WHERE status='active'")
        db.execSQL("""CREATE TABLE queue (
            session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
            position INTEGER NOT NULL CHECK(position>=0), word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
            options TEXT NOT NULL, PRIMARY KEY(session_id,position))""")
        db.execSQL("""CREATE TABLE answers (
            session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
            position INTEGER NOT NULL CHECK(position>=0), word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
             day INTEGER NOT NULL, grade TEXT NOT NULL CHECK(grade IN ('AGAIN','HARD','KNOWN')), time INTEGER NOT NULL CHECK(time>=0),
            PRIMARY KEY(session_id,position))""")
        db.execSQL("CREATE INDEX answers_word ON answers(word_id, time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            enrichLegacySerbian(db, context.assets)
            db.execSQL("DELETE FROM capabilities")
            rebuildCapabilities(db)
        } else {
            throw VocabularyProblem(R.string.error_db_version, oldVersion, newVersion)
        }
    }
}

// In 1.0 the bundled dictionary was always the first inserted row. Compare the entire
// original ordered word list as well, since dictionary names are user-editable.
internal fun enrichLegacySerbian(db: SQLiteDatabase, assets: AssetManager) {
    val count = db.rawQuery("""SELECT COUNT(*) FROM words WHERE dictionary_id=1
        AND translation='' AND transcription='' AND example='' AND extra=''""", null).use {
        it.moveToFirst()
        it.getInt(0)
    }
    if (count != BundledDictionary.wordCount) return
    val existing = db.rows("SELECT id,word FROM words WHERE dictionary_id=1 ORDER BY id") {
        it.getLong(0) to it.getString(1)
    }
    val entries = BundledDictionary.read(assets)
    if (existing.size != entries.size || entries.indices.any { existing[it].second != entries[it].word }) return

    db.compileStatement("UPDATE words SET translation=?,transcription=?,extra=? WHERE id=?").use { update ->
        entries.forEachIndexed { i, entry ->
            update.bindString(1, entry.translation)
            update.bindString(2, entry.transcription)
            update.bindString(3, entry.extra)
            update.bindLong(4, existing[i].first)
            update.executeUpdateDelete()
        }
    }
}
