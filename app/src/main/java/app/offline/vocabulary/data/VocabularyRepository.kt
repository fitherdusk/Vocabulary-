package app.offline.vocabulary.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.offline.vocabulary.importer.Entry
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

internal fun <T> SQLiteDatabase.atomic(block: () -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally { endTransaction() }
}

internal fun <T> SQLiteDatabase.rows(sql: String, vararg args: Any, read: (Cursor) -> T): List<T> =
    rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { cursor ->
        buildList { while (cursor.moveToNext()) add(read(cursor)) }
    }

internal fun Cursor.number(field: String): Int = getInt(getColumnIndexOrThrow(field))
internal fun Cursor.longNumber(field: String): Long = getLong(getColumnIndexOrThrow(field))
internal fun Cursor.text(field: String): String = getString(getColumnIndexOrThrow(field))
internal fun Boolean.flag() = if (this) 1 else 0

class VocabularyRepository(context: Context, databaseName: String = "vocabulary.sqlite") : Closeable {
    private val appContext = context.applicationContext
    private val helper = VocabularyDatabase(appContext, databaseName)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    fun snapshot(): LibrarySnapshot = db.atomic {
        val settings = settings()
        val dictionaries = db.rows("""SELECT d.id, d.name, COUNT(w.id), COALESCE(SUM(w.successes>=3),0)
            FROM dictionaries d LEFT JOIN words w ON w.dictionary_id=d.id GROUP BY d.id ORDER BY d.id DESC""") {
            DictionaryInfo(it.getLong(0), it.getString(1), it.getInt(2), it.getInt(3))
        }
        val selected = selectedId()
        LibrarySnapshot(dictionaries, selected, settings, selected?.let { stats(it) } ?: Stats(),
            selected?.let { availability(it, settings.reverse) } ?: Availability())
    }

    private fun selectedId() = db.rows("SELECT selected FROM settings WHERE selected IS NOT NULL") { it.getLong(0) }.firstOrNull()

    private fun settings() = db.rows("SELECT * FROM settings WHERE id=1") {
        StudySettings(it.number("new_limit"), it.number("goal"), StudyMode.valueOf(it.text("mode")),
            it.number("reverse") != 0, it.number("animations") != 0, it.number("font"), it.number("random") != 0)
    }.single()

    fun saveSettings(settings: StudySettings) = db.atomic {
        require(settings.newPerSession > 0 && settings.dailyGoal > 0) { "Количество слов должно быть положительным" }
        require(settings.fontSize in 12..100) { "Размер шрифта должен быть от 12 до 100" }
        val old = settings()
        if (old.mode != settings.mode || old.reverse != settings.reverse ||
            old.randomOrder != settings.randomOrder || old.newPerSession != settings.newPerSession) {
            abandonActive()
        }
        db.execSQL("""UPDATE settings SET new_limit=?,goal=?,mode=?,reverse=?,animations=?,font=?,random=? WHERE id=1""",
            arrayOf(settings.newPerSession, settings.dailyGoal, settings.mode.name, settings.reverse.flag(),
                settings.animations.flag(), settings.fontSize, settings.randomOrder.flag()))
        ensureMode()
    }

    private fun availability(id: Long, reverse: Boolean): Availability {
        val flags = db.rows("SELECT translated,quiz_forward,quiz_reverse FROM capabilities WHERE dictionary_id=?", id) {
            Triple(it.getInt(0) != 0, it.getInt(1) != 0, it.getInt(2) != 0)
        }.single()
        val translatedOnly = reverse && flags.first
        val filter = if (translatedOnly) " AND translation<>''" else ""
        val seen = db.rows("SELECT 1 FROM words WHERE dictionary_id=? AND attempts>0$filter LIMIT 1", id) { true }.isNotEmpty()
        return Availability(true, if (reverse) flags.third else flags.second, flags.first, seen)
    }

    private fun ensureMode() {
        val pref = settings()
        val id = selectedId() ?: return
        if (pref.mode != StudyMode.CARDS && !availability(id, pref.reverse).allows(pref.mode)) {
            db.execSQL("UPDATE settings SET mode='CARDS' WHERE id=1")
        }
    }

    fun installBundledIfNeeded(name: String, load: () -> List<Entry>) = db.atomic {
        val completed = db.rows("SELECT 1 FROM installation WHERE id=1") { true }.isNotEmpty()
        if (!completed) {
            val hasDictionaries = db.rows("SELECT 1 FROM dictionaries LIMIT 1") { true }.isNotEmpty()
            if (!hasDictionaries) importDictionary(name, load())
            db.execSQL("INSERT INTO installation(id) VALUES(1)")
        }
    }

    fun importDictionary(name: String, entries: List<Entry>): Long = db.atomic {
        require(name.isNotBlank()) { "Введите название словаря" }
        val dictionary = db.insertOrThrow("dictionaries", null, ContentValues().apply { put("name", name.trim()) })
        var inserted = 0
        val unique = mutableSetOf<Pair<String,String>>()
        db.compileStatement("""INSERT INTO words(dictionary_id,word,translation,transcription,example,extra)
            VALUES (?,?,?,?,?,?)""").use { statement ->
            entries.forEach { entry ->
                val word = entry.word.trim()
                val key = normalizeAnswer(word) to normalizeAnswer(entry.translation)
                if (word.isNotBlank() && unique.add(key)) {
                    statement.bindLong(1, dictionary)
                    listOf(word, entry.translation.trim(), entry.transcription.trim(), entry.example.trim(),
                        entry.extra.trim()).forEachIndexed { i, value -> statement.bindString(i + 2, value) }
                    statement.executeInsert()
                    inserted++
                }
            }
        }
        require(inserted > 0) { "В словаре нет слов для импорта" }
        storeCapabilities(db, dictionary, unique)
        select(dictionary)
        dictionary
    }

    fun selectDictionary(id: Long) = db.atomic {
        checkDictionary(id)
        if (id != selectedId()) select(id)
    }

    private fun select(id: Long) {
        abandonActive()
        db.execSQL("UPDATE settings SET selected=? WHERE id=1", arrayOf(id))
        ensureMode()
    }

    private fun checkDictionary(id: Long) {
        require(db.rows("SELECT 1 FROM dictionaries WHERE id=?", id) { true }.isNotEmpty()) { "Словарь не найден" }
    }

    fun renameDictionary(id: Long, name: String) = db.atomic {
        require(name.isNotBlank()) { "Введите название словаря" }
        checkDictionary(id)
        db.execSQL("UPDATE dictionaries SET name=? WHERE id=?", arrayOf(name.trim(), id))
    }

    fun deleteDictionary(id: Long) = db.atomic {
        checkDictionary(id)
        val selected = selectedId() == id
        db.execSQL("DELETE FROM dictionaries WHERE id=?", arrayOf(id))
        if (selected) {
            db.execSQL("UPDATE settings SET selected=(SELECT MAX(id) FROM dictionaries) WHERE id=1")
            ensureMode()
        }
    }

    fun resetProgress(id: Long) = db.atomic {
        checkDictionary(id)
        db.execSQL("DELETE FROM sessions WHERE dictionary_id=?", arrayOf(id))
        db.execSQL("""UPDATE words SET attempts=0,successes=0,errors=0,ease=2.5,interval_days=0,last=0,due=0
            WHERE dictionary_id=?""", arrayOf(id))
        ensureMode()
    }

    private fun stats(id: Long): Stats {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val today = date.toEpochDay()
        val tomorrow = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val wordStats = db.rows("""SELECT COUNT(*) AS total, COUNT(CASE WHEN attempts=0 THEN 1 END) AS new_words,
            COUNT(CASE WHEN successes>=3 THEN 1 END) AS learned,
            COUNT(CASE WHEN attempts>0 AND due<? THEN 1 END) AS due_words,
            COALESCE(AVG(CASE WHEN attempts=0 THEN 0 WHEN successes=0 THEN 10
                WHEN successes=1 THEN 40 WHEN successes=2 THEN 70 ELSE 100 END), 0) AS mastery
            FROM words WHERE dictionary_id=?""", tomorrow, id) {
            val total = it.number("total")
            val newWords = it.number("new_words")
            val learned = it.number("learned")
            Stats(total = total, newWords = newWords, learned = learned, learning = total - newWords - learned,
                due = it.number("due_words"), masteryPercent = it.getDouble(it.getColumnIndexOrThrow("mastery")))
        }.single()
        val sessions = db.rows("SELECT COUNT(*) FROM sessions WHERE dictionary_id=? AND position>0", id) { it.getInt(0) }.single()
        val days = db.rows("""SELECT a.day,COUNT(DISTINCT a.word_id) FROM answers a
            JOIN sessions s ON s.id=a.session_id WHERE s.dictionary_id=? GROUP BY a.day ORDER BY a.day DESC""", id) {
            it.getLong(0) to it.getInt(1)
        }
        val todayCount = days.firstOrNull { it.first == today }?.second ?: 0
        var target = if (todayCount > 0) today else today - 1
        var streak = 0
        for ((day, _) in days) {
            if (day > target) continue
            if (day != target) break
            streak++
            target--
        }
        return wordStats.copy(sessions = sessions, streak = streak, today = todayCount)
    }

    fun startSession(): StudySession? = db.atomic {
        val id = selectedId() ?: return@atomic null
        activeCard()?.let { return@atomic it }
        val pref = settings()
        val pool = StudyPool(db, id, pref.reverse)
        if (!pool.availability.allows(pref.mode)) return@atomic null
        val queue = pool.queue(pref, System.currentTimeMillis())
        if (queue.isEmpty()) return@atomic null
        val sid = db.insertOrThrow("sessions", null, ContentValues().apply {
            put("dictionary_id", id); put("mode", pref.mode.name); put("reverse", pool.reverse.flag()); put("total", queue.size)
        })
        db.compileStatement("INSERT INTO queue(session_id,position,word_id,options) VALUES(?,?,?,?)").use { insert ->
            queue.forEachIndexed { index, word ->
                insert.bindLong(1,sid); insert.bindLong(2,index.toLong()); insert.bindLong(3,word.id)
                insert.bindString(4, pool.optionsJson(word, pref.mode))
                insert.executeInsert()
            }
        }
        activeCard()
    }

    fun activeSession(): StudySession? = db.atomic { activeCard() }

    private fun activeCard(): StudySession? = db.rows("""SELECT s.id,s.position,s.total,s.mode,s.reverse,
        w.id AS word_id,w.word,w.translation,w.transcription,w.example,w.extra,q.options
        FROM sessions s JOIN settings p ON p.selected=s.dictionary_id
        JOIN queue q ON q.session_id=s.id AND q.position=s.position JOIN words w ON w.id=q.word_id
        WHERE s.status='active'""") {
        val reverse = it.number("reverse") != 0
        val prompt = it.text(if (reverse) "translation" else "word")
        val answer = it.text(if (reverse) "word" else "translation")
        val mode = StudyMode.valueOf(it.text("mode"))
        val saved = decodeOptions(it.text("options"))
        StudySession(it.longNumber("id"), it.number("position"), it.number("total"), mode,
            reverse, StudyCard(it.longNumber("word_id"), prompt, answer, it.text("transcription"),
                it.text("example"), it.text("extra"), if (mode == StudyMode.QUIZ) saved else emptyList(),
                if (mode == StudyMode.TYPING) saved else listOf(answer)))
    }.firstOrNull()

    fun rate(sessionId: Long, index: Int, rating: Rating): StudySession? = db.atomic {
        val session = activeCard() ?: return@atomic null
        if (session.id != sessionId || session.index != index) return@atomic session
        val wid = session.card.wordId
        val old = db.rows("SELECT attempts,successes,errors,ease,interval_days,last,due FROM words WHERE id=?", wid) {
            Progress(it.getInt(0),it.getInt(1),it.getInt(2),it.getDouble(3),it.getInt(4),it.getLong(5),it.getLong(6))
        }.single()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val next = schedule(old, rating, now, zone)
        db.execSQL("UPDATE words SET attempts=?,successes=?,errors=?,ease=?,interval_days=?,last=?,due=? WHERE id=?",
            arrayOf(next.attempts,next.successes,next.errors,next.ease,next.interval,next.last,next.due,wid))
        db.execSQL("INSERT INTO answers(session_id,position,word_id,grade,time,day) VALUES(?,?,?,?,?,?)",
            arrayOf(sessionId, index, wid, rating.name, now, java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()))
        val finished = index + 1 == session.total
        db.execSQL("UPDATE sessions SET position=?,status=? WHERE id=?",
            arrayOf(index + 1, if (finished) "finished" else "active", sessionId))
        if (finished) db.execSQL("DELETE FROM queue WHERE session_id=?", arrayOf(sessionId))
        activeCard()
    }

    private fun abandonActive() {
        db.execSQL("DELETE FROM queue WHERE session_id IN (SELECT id FROM sessions WHERE status='active')")
        db.execSQL("DELETE FROM sessions WHERE status='active' AND position=0")
        db.execSQL("UPDATE sessions SET status='abandoned' WHERE status='active'")
    }

    fun abandonSession(sessionId: Long) = db.atomic {
        if (activeCard()?.id == sessionId) abandonActive()
    }

    fun exportBackup(output: OutputStream) = Backup.write(db, output)
    fun restoreBackup(input: InputStream) = db.atomic {
        Backup.read(db, input)
        enrichLegacySerbian(db, appContext.assets)
        rebuildCapabilities(db)
    }
    override fun close() = helper.close()
}
