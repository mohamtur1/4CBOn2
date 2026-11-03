// ---------------------------------------------------------------------------
// Compile-only stubs for the pure-JVM verification harness.
//
// These types exist ONLY so that `FourCBOn2WorkspaceContainer.kt` (which is otherwise pure Kotlin
// and is exercised directly by the real unit tests) can be type-checked without the Android SDK.
// Nothing in this file is part of the Android build, and every method here is either unreachable
// from the tests or deliberately not implemented — the harness never executes an Android code path.
// ---------------------------------------------------------------------------
package android.content

import java.io.File

class SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    fun edit(): Editor = Editor(values)
    fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    class Editor(private val values: MutableMap<String, Any?>) {
        fun putString(key: String, value: String?): Editor = apply { values[key] = value }
        fun remove(key: String): Editor = apply { values.remove(key) }
        fun commit(): Boolean = true
        fun apply() = Unit
    }
}

open class Context {
    val applicationContext: Context get() = this
    val contentResolver: ContentResolver get() = ContentResolver()
    val filesDir: File get() = File(System.getProperty("java.io.tmpdir"), "4cbon2_stub_files").apply { mkdirs() }
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences()
    fun getSystemService(name: String): Any? = null

    companion object {
        const val MODE_PRIVATE: Int = 0
        const val CONNECTIVITY_SERVICE: String = "connectivity"
    }
}

class ContentResolver {
    fun getType(uri: Any?): String? = null
    fun openInputStream(uri: Any?): java.io.InputStream? = null
    fun openOutputStream(uri: Any?, mode: String?): java.io.OutputStream? = null
    fun query(uri: Any?, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): android.database.Cursor? = null
}

class ContentValues {
    private val values = mutableMapOf<String, Any?>()
    fun put(key: String, value: Any?): Unit {
        values[key] = value
    }

    fun getAsString(key: String): String? = values[key]?.toString()
}
