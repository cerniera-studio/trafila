package com.cerniera.trafila

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/**
 * The applications, kept on the phone in one small SQLite table. Nothing leaves the phone except
 * by Export, which Mechack sends where he chooses.
 */
class Store private constructor(c: Context) : SQLiteOpenHelper(c.applicationContext, "trafila.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE app (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                firm TEXT NOT NULL DEFAULT '', role TEXT NOT NULL DEFAULT '',
                route TEXT NOT NULL DEFAULT 'DIRECT', via TEXT NOT NULL DEFAULT '', contact TEXT NOT NULL DEFAULT '',
                stage TEXT NOT NULL DEFAULT 'APPLIED', last_contact TEXT, next TEXT NOT NULL DEFAULT '', next_on TEXT,
                notes TEXT NOT NULL DEFAULT '', created TEXT NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun all(): List<Application> = readableDatabase.query("app", null, null, null, null, null, "id").use { cur ->
        val out = mutableListOf<Application>()
        while (cur.moveToNext()) out += read(cur)
        out
    }

    fun get(id: Long): Application? = readableDatabase.query("app", null, "id = ?", arrayOf(id.toString()), null, null, null).use { cur ->
        if (cur.moveToFirst()) read(cur) else null
    }

    /** Adds [a] (its id ignored) and returns the new id. */
    fun add(a: Application): Long = writableDatabase.insert("app", null, values(a))

    fun save(a: Application) { writableDatabase.update("app", values(a), "id = ?", arrayOf(a.id.toString())) }

    fun delete(id: Long) { writableDatabase.delete("app", "id = ?", arrayOf(id.toString())) }

    /** Adds every application not already here (same firm and role, case aside). Returns how many were added. */
    fun import(list: List<Application>): Int {
        val have = all().map { it.firm.lowercase().trim() to it.role.lowercase().trim() }.toSet()
        var n = 0
        writableDatabase.beginTransaction()
        try {
            for (a in list) if ((a.firm.lowercase().trim() to a.role.lowercase().trim()) !in have) { add(a); n++ }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return n
    }

    private fun values(a: Application) = ContentValues().apply {
        put("firm", a.firm); put("role", a.role); put("route", a.route.name); put("via", a.via); put("contact", a.contact)
        put("stage", a.stage.name); put("last_contact", a.lastContact?.toString()); put("next", a.next)
        put("next_on", a.nextOn?.toString()); put("notes", a.notes); put("created", a.created.toString())
    }

    private fun read(c: Cursor): Application {
        fun s(n: String) = c.getString(c.getColumnIndexOrThrow(n)) ?: ""
        fun d(n: String) = s(n).takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return Application(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            firm = s("firm"), role = s("role"),
            route = runCatching { Route.valueOf(s("route")) }.getOrDefault(Route.DIRECT),
            via = s("via"), contact = s("contact"),
            stage = runCatching { Stage.valueOf(s("stage")) }.getOrDefault(Stage.APPLIED),
            lastContact = d("last_contact"), next = s("next"), nextOn = d("next_on"), notes = s("notes"),
            created = d("created") ?: LocalDate.now(),
        )
    }

    companion object {
        @Volatile private var one: Store? = null
        fun of(c: Context): Store = one ?: synchronized(this) { one ?: Store(c).also { one = it } }
    }
}
