// Compile-only stubs (see AndroidCompileStubs.kt header).
package android.database

open class Cursor : java.io.Closeable {
    open fun moveToFirst(): Boolean = false
    open fun getString(columnIndex: Int): String? = null
    override fun close() = Unit
}
