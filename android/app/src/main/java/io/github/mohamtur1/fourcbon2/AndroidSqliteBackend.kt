package io.github.mohamtur1.fourcbon2

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Division 11 (Android backend): production SQLite implementation of [LocalStoreBackend].
 *
 * Split out of `LocalDatabase.kt` in v0.3 (Division 43) so the rest of the persistence layer —
 * [LocalStoreBackend], [DiskDirectoryBackend], and [FourCBOn2Database] — stays free of `android.*`
 * imports and can be compiled and executed by the pure-JVM verification harness
 * (`scripts/verify_pure_kotlin.sh`) even when no Android SDK is installed.
 *
 * Production Android SQLite backend storing versioned JSON entity rows in an indexed table.
 */
class AndroidSqliteBackend(context: Context) : LocalStoreBackend {
    private val helper = DbHelper(context.applicationContext)

    override fun upsert(namespace: String, id: String, payloadJson: String, updatedAtEpochMs: Long) {
        validateKey(namespace, "namespace")
        validateKey(id, "id")
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put(COL_NAMESPACE, namespace)
            put(COL_ID, id)
            put(COL_PAYLOAD, payloadJson)
            put(COL_UPDATED_AT, updatedAtEpochMs)
        }
        db.insertWithOnConflict(TABLE_ENTITIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun get(namespace: String, id: String): StoredEntityRow? {
        val db = helper.readableDatabase
        db.query(
            TABLE_ENTITIES,
            arrayOf(COL_NAMESPACE, COL_ID, COL_PAYLOAD, COL_UPDATED_AT),
            "$COL_NAMESPACE = ? AND $COL_ID = ?",
            arrayOf(namespace, id),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return StoredEntityRow(
                namespace = cursor.getString(0),
                id = cursor.getString(1),
                payloadJson = cursor.getString(2),
                updatedAtEpochMs = cursor.getLong(3),
            )
        }
    }

    override fun listByNamespace(namespace: String): List<StoredEntityRow> {
        val db = helper.readableDatabase
        val out = ArrayList<StoredEntityRow>()
        db.query(
            TABLE_ENTITIES,
            arrayOf(COL_NAMESPACE, COL_ID, COL_PAYLOAD, COL_UPDATED_AT),
            "$COL_NAMESPACE = ?",
            arrayOf(namespace),
            null,
            null,
            "$COL_UPDATED_AT DESC, $COL_ID ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    StoredEntityRow(
                        namespace = cursor.getString(0),
                        id = cursor.getString(1),
                        payloadJson = cursor.getString(2),
                        updatedAtEpochMs = cursor.getLong(3),
                    ),
                )
            }
        }
        return out
    }

    override fun delete(namespace: String, id: String): Boolean {
        val db = helper.writableDatabase
        return db.delete(
            TABLE_ENTITIES,
            "$COL_NAMESPACE = ? AND $COL_ID = ?",
            arrayOf(namespace, id),
        ) > 0
    }

    override fun clearNamespace(namespace: String) {
        val db = helper.writableDatabase
        db.delete(TABLE_ENTITIES, "$COL_NAMESPACE = ?", arrayOf(namespace))
    }

    private class DbHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.enableWriteAheadLogging()
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_ENTITIES (
                    $COL_NAMESPACE TEXT NOT NULL,
                    $COL_ID TEXT NOT NULL,
                    $COL_PAYLOAD TEXT NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY ($COL_NAMESPACE, $COL_ID)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX idx_${TABLE_ENTITIES}_ns_updated
                ON $TABLE_ENTITIES ($COL_NAMESPACE, $COL_UPDATED_AT DESC)
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Schema v1 baseline; future migrations preserve existing rows.
        }
    }

    private companion object {
        const val DB_NAME = "4cbon2_local_workspace.db"
        const val DB_VERSION = 1
        const val TABLE_ENTITIES = "entity_records"
        const val COL_NAMESPACE = "namespace"
        const val COL_ID = "entity_id"
        const val COL_PAYLOAD = "payload_json"
        const val COL_UPDATED_AT = "updated_at_ms"
    }
}

