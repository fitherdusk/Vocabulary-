package app.offline.vocabulary.data

import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.InputStream
import java.io.OutputStream

/**
 * Versioned, streaming UTF-8 JSON. Arrays have a fixed order in v1. User IDs,
 * answer history and the active queue survive a restore. Installation markers
 * belong to the device and are deliberately outside the backup format.
 */
internal object Backup {
    private const val magic = "Offline Vocabulary"
    private data class Table(val name: String, val fields: List<String>) {
        constructor(name: String, fields: String) : this(name, fields.split(" "))
        val columns = fields.joinToString(",")
    }
    private val tables = listOf(
        Table("dictionaries", "id name"),
        Table("words", "id dictionary_id word translation transcription example extra attempts successes errors ease interval_days last due"),
        Table("settings", "id selected new_limit goal mode reverse animations font random"),
        Table("sessions", "id dictionary_id mode reverse position total status"),
        Table("queue", "session_id position word_id options"),
        Table("answers", "session_id position word_id grade time day")
    )
    private val strings = setOf("name","word","translation","transcription","example","extra","mode","status","options","grade")

    fun write(db: SQLiteDatabase, output: OutputStream) = db.atomic {
        val out = JsonWriter(output.writer(Charsets.UTF_8).buffered())
        out.beginArray().value(magic).value(1L)
        for (table in tables) {
            out.beginArray().value(table.name)
            db.rawQuery("SELECT ${table.columns} FROM ${table.name}", null).use { cur ->
                while (cur.moveToNext()) {
                    out.beginArray()
                    table.fields.forEachIndexed { i, field ->
                        when {
                            cur.isNull(i) -> out.nullValue()
                            field in strings -> out.value(cur.getString(i))
                            field == "ease" -> out.value(cur.getDouble(i))
                            else -> out.value(cur.getLong(i))
                        }
                    }
                    out.endArray()
                }
            }
            out.endArray()
        }
        out.endArray()
        out.flush()
    }

    fun read(db: SQLiteDatabase, input: InputStream) {
        val src = JsonReader(input.reader(Charsets.UTF_8).buffered())
        try {
            src.beginArray()
            require(src.nextString() == magic && src.nextInt() == 1) { "Неподдерживаемая версия копии" }
            db.atomic {
                tables.asReversed().forEach { db.execSQL("DELETE FROM ${it.name}") }
                tables.forEach { readTable(db, src, it) }
                src.endArray()
                require(src.peek() == JsonToken.END_DOCUMENT)
                validate(db)
                db.execSQL("INSERT OR IGNORE INTO installation(id) VALUES(1)")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Не удалось восстановить резервную копию. Проверьте формат и целостность файла.", e)
        }
    }

    private fun readTable(db: SQLiteDatabase, src: JsonReader, table: Table) {
        src.beginArray()
        require(src.nextString() == table.name)
        val slots = table.fields.joinToString(",") { "?" }
        db.compileStatement("INSERT INTO ${table.name}(${table.columns}) VALUES($slots)").use { insert ->
            while (src.hasNext()) {
                src.beginArray()
                table.fields.forEachIndexed { i, field ->
                    val slot = i + 1
                    when {
                        src.peek() == JsonToken.NULL && field == "selected" -> {
                            src.nextNull()
                            insert.bindNull(slot)
                        }
                        field in strings -> {
                            require(src.peek() == JsonToken.STRING)
                            insert.bindString(slot, src.nextString())
                        }
                        field == "ease" -> {
                            require(src.peek() == JsonToken.NUMBER)
                            insert.bindDouble(slot, src.nextDouble())
                        }
                        else -> {
                            require(src.peek() == JsonToken.NUMBER)
                            insert.bindLong(slot, src.nextLong())
                        }
                    }
                }
                src.endArray()
                insert.executeInsert()
            }
        }
        src.endArray()
    }

    private fun validate(db: SQLiteDatabase) {
        fun invalid(sql: String) = db.rawQuery(sql, null).use { it.moveToFirst() }
        require(!invalid("SELECT 1 WHERE (SELECT COUNT(*) FROM settings)<>1"))
        require(!invalid("""SELECT 1 FROM settings WHERE selected IS NULL AND EXISTS(SELECT 1 FROM dictionaries)"""))
        require(!invalid("""SELECT 1 FROM dictionaries d WHERE NOT EXISTS(SELECT 1 FROM words w WHERE w.dictionary_id=d.id)"""))
        require(!invalid("""SELECT 1 FROM sessions s WHERE (s.status='active' AND (
            s.dictionary_id<>(SELECT selected FROM settings) OR s.position>=s.total OR
            (SELECT COUNT(*) FROM queue q WHERE q.session_id=s.id)<>s.total)) OR
            (s.status='finished' AND s.position<>s.total) OR
            (SELECT COUNT(*) FROM answers a WHERE a.session_id=s.id)<>s.position LIMIT 1"""))
        require(!invalid("""SELECT 1 FROM answers a JOIN sessions s ON s.id=a.session_id JOIN words w ON w.id=a.word_id
            WHERE s.dictionary_id<>w.dictionary_id OR a.position>=s.position LIMIT 1"""))
        require(!invalid("""SELECT 1 FROM words w WHERE w.attempts<>(SELECT COUNT(*) FROM answers a WHERE a.word_id=w.id)
            OR w.errors<>(SELECT COUNT(*) FROM answers a WHERE a.word_id=w.id AND a.grade='AGAIN') LIMIT 1"""))
        db.rawQuery("""SELECT q.options,q.position,s.total,s.status,s.mode,s.reverse,
            s.dictionary_id=w.dictionary_id AS same_dictionary,w.word,w.translation
            FROM queue q JOIN sessions s ON s.id=q.session_id JOIN words w ON w.id=q.word_id""", null).use { cursor ->
            while (cursor.moveToNext()) {
                require(cursor.number("same_dictionary") == 1 && cursor.text("status") == "active")
                require(cursor.number("position") < cursor.number("total"))
                val options = decodeOptions(cursor.text("options")).map(::normalizeAnswer)
                val answer = normalizeAnswer(cursor.text(if (cursor.number("reverse") == 1) "word" else "translation"))
                when (StudyMode.valueOf(cursor.text("mode"))) {
                    StudyMode.QUIZ -> require(options.size == 4 && options.toSet().size == 4 && answer in options)
                    StudyMode.TYPING -> require(answer in options)
                    else -> require(options.isEmpty())
                }
            }
        }
    }
}
