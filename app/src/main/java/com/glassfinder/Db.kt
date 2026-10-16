package com.glassfinder

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class BoxInfo(val number: Int, val imagePath: String, val modelCount: Int)
data class Hit(val box: Int, val model: String, val rank: Int, val closeness: Int)

/** Lowercase letters+digits only, so "Galaxy A-10" and "galaxy a10" compare equal. */
fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

// Brand words are ignored while searching, so "samsung a10" still finds an entry written just "A10".
private val BRANDS = listOf(
    "samsung", "xiaomi", "redmi", "oppo", "vivo", "realme", "infinix", "tecno",
    "itel", "huawei", "honor", "apple", "motorola", "nokia", "oneplus", "poco",
    "lenovo", "galaxy"
)

private fun stripBrands(n: String): String {
    var r = n
    for (b in BRANDS) r = r.replace(b, "")
    return r
}

class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "glass.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE boxes(" +
                "number INTEGER PRIMARY KEY, " +
                "image TEXT NOT NULL, " +
                "created INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE models(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "box INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "norm TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_models_box ON models(box)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun nextBoxNumber(): Int =
        readableDatabase.rawQuery("SELECT COALESCE(MAX(number),0)+1 FROM boxes", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    fun boxExists(number: Int): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM boxes WHERE number=?", arrayOf(number.toString()))
            .use { it.moveToFirst() }

    /** Creates the box, or replaces it (image and models) if the number already exists. */
    fun saveBox(number: Int, imagePath: String, models: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR REPLACE INTO boxes(number,image,created) VALUES(?,?,?)",
                arrayOf<Any>(number, imagePath, System.currentTimeMillis())
            )
            replaceModels(db, number, models)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setModels(number: Int, models: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            replaceModels(db, number, models)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun replaceModels(db: SQLiteDatabase, number: Int, models: List<String>) {
        db.execSQL("DELETE FROM models WHERE box=?", arrayOf<Any>(number))
        for (m in models) {
            db.execSQL(
                "INSERT INTO models(box,name,norm) VALUES(?,?,?)",
                arrayOf<Any>(number, m, norm(m))
            )
        }
    }

    fun deleteBox(number: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM models WHERE box=?", arrayOf<Any>(number))
            db.execSQL("DELETE FROM boxes WHERE number=?", arrayOf<Any>(number))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun allBoxes(): List<BoxInfo> {
        val out = mutableListOf<BoxInfo>()
        readableDatabase.rawQuery(
            "SELECT b.number, b.image, (SELECT COUNT(*) FROM models m WHERE m.box=b.number) " +
                "FROM boxes b ORDER BY b.number",
            null
        ).use { c ->
            while (c.moveToNext()) out += BoxInfo(c.getInt(0), c.getString(1), c.getInt(2))
        }
        return out
    }

    fun imageOf(number: Int): String? =
        readableDatabase.rawQuery("SELECT image FROM boxes WHERE number=?", arrayOf(number.toString()))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    fun modelsOf(number: Int): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT name FROM models WHERE box=? ORDER BY id",
            arrayOf(number.toString())
        ).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out
    }

    /** Every box that lists a model matching all the words typed, best matches first. */
    fun search(query: String): List<Hit> {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        var words = tokens.filter { norm(it) !in BRANDS }
        if (words.isEmpty()) words = tokens
        val parts = words.map { norm(it) }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        val joined = parts.joinToString("")

        val hits = mutableListOf<Hit>()
        readableDatabase.rawQuery("SELECT box, name, norm FROM models", null).use { c ->
            while (c.moveToNext()) {
                val n = c.getString(2)
                if (!parts.all { n.contains(it) }) continue
                val nb = stripBrands(n)
                val rank = when {
                    nb == joined -> 0
                    nb.startsWith(joined) -> 1
                    else -> 2
                }
                hits += Hit(c.getInt(0), c.getString(1), rank, nb.length)
            }
        }
        return hits.sortedWith(compareBy({ it.rank }, { it.closeness }, { it.box }))
    }
}
