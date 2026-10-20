package dev.m4s4t0.voidweb

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

data class Site(val url: String, val title: String, val visits: Int = 0, val last: Long = 0)

/**
 * History and bookmarks, in a small SQLite database on the phone. Private tabs never write here.
 * [changes] ticks on every write, so screens that list them can reload.
 */
object Store {
    private lateinit var db: SQLiteDatabase
    var changes by mutableIntStateOf(0)
        private set

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "web.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE history (url TEXT PRIMARY KEY, title TEXT NOT NULL DEFAULT '', visits INTEGER NOT NULL DEFAULT 0, last INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX history_last ON history(last)")
            db.execSQL("CREATE TABLE bookmarks (url TEXT PRIMARY KEY, title TEXT NOT NULL DEFAULT '', added INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }

    fun init(ctx: Context) {
        db = Helper(ctx.applicationContext).writableDatabase
    }

    private fun bump() { changes++ }

    fun addVisit(url: String) {
        if (Urls.isBlank(url) || !(url.startsWith("http://") || url.startsWith("https://"))) return
        // Update, else insert: Android 10's SQLite is older than upserts.
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE history SET visits = visits + 1, last = ? WHERE url = ?", arrayOf<Any>(now, url))
        db.execSQL("INSERT OR IGNORE INTO history(url, visits, last) VALUES(?, 1, ?)", arrayOf<Any>(url, now))
        bump()
    }

    fun setTitle(url: String, title: String) {
        if (title.isBlank()) return
        val v = ContentValues().apply { put("title", title) }
        val n = db.update("history", v, "url = ?", arrayOf(url)) + db.update("bookmarks", v, "url = ? AND title = ''", arrayOf(url))
        if (n > 0) bump()
    }

    fun visited(urls: Array<String>): BooleanArray {
        if (urls.isEmpty()) return BooleanArray(0)
        val found = HashSet<String>()
        urls.toList().chunked(500).forEach { chunk ->
            db.rawQuery("SELECT url FROM history WHERE url IN (${chunk.joinToString(",") { "?" }})", chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) found += c.getString(0)
            }
        }
        return BooleanArray(urls.size) { urls[it] in found }
    }

    private fun sites(sql: String, args: Array<String>): List<Site> = db.rawQuery(sql, args).use { c ->
        buildList { while (c.moveToNext()) add(Site(c.getString(0), c.getString(1), c.getInt(2), c.getLong(3))) }
    }

    fun history(limit: Int = 500): List<Site> = sites("SELECT url, title, visits, last FROM history ORDER BY last DESC LIMIT $limit", emptyArray())

    /** The most visited sites, one per host (the shortcuts on the new-tab page). */
    fun topSites(limit: Int): List<Site> {
        val seen = HashSet<String>()
        return sites("SELECT url, title, visits, last FROM history WHERE visits > 1 ORDER BY visits DESC, last DESC LIMIT 200", emptyArray())
            .filter { seen.add(Urls.displayHost(it.url)) }
            .take(limit)
    }

    /** Bookmarks first, then history, matching the typed text anywhere in the address or title. */
    fun suggest(text: String, limit: Int = 8): List<Site> {
        if (text.isBlank()) return emptyList()
        val like = "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val marks = sites("SELECT url, title, 0, added FROM bookmarks WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' ORDER BY added DESC LIMIT $limit", arrayOf(like, like))
        val hist = sites("SELECT url, title, visits, last FROM history WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' ORDER BY visits DESC, last DESC LIMIT $limit", arrayOf(like, like))
        return (marks + hist).distinctBy { it.url }.take(limit)
    }

    fun removeHistory(url: String) {
        db.delete("history", "url = ?", arrayOf(url))
        bump()
    }

    fun clearHistory() {
        db.delete("history", null, null)
        bump()
    }

    fun bookmarks(): List<Site> = sites("SELECT url, title, 0, added FROM bookmarks ORDER BY added DESC", emptyArray())

    fun isBookmarked(url: String): Boolean = db.rawQuery("SELECT 1 FROM bookmarks WHERE url = ?", arrayOf(url)).use { it.moveToFirst() }

    fun addBookmark(url: String, title: String) {
        db.insertWithOnConflict("bookmarks", null, ContentValues().apply { put("url", url); put("title", title); put("added", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
        bump()
    }

    fun removeBookmark(url: String) {
        db.delete("bookmarks", "url = ?", arrayOf(url))
        bump()
    }

    fun renameBookmark(url: String, title: String) {
        db.update("bookmarks", ContentValues().apply { put("title", title) }, "url = ?", arrayOf(url))
        bump()
    }
}
