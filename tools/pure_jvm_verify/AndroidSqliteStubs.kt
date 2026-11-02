// Compile-only stubs (see AndroidCompileStubs.kt header).
package android.database.sqlite

import android.content.ContentValues

open class SQLiteDatabase {
    open fun insert(table: String, nullColumnHack: String?, values: ContentValues): Long = 0L
    open fun insertWithOnConflict(table: String, nullColumnHack: String?, values: ContentValues, conflictAlgorithm: Int): Long = 0L
    open fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?): Int = 0
    open fun delete(table: String, whereClause: String?, whereArgs: Array<String>?): Int = 0
    open fun execSQL(sql: String) = Unit
    open fun close() = Unit

    companion object {
        const val CONFLICT_REPLACE: Int = 5
    }
}

abstract class SQLiteOpenHelper {
    abstract fun onCreate(db: SQLiteDatabase)
    abstract fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
    open fun getWritableDatabase(): SQLiteDatabase = SQLiteDatabase()
}
