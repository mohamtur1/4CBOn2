// Compile-only stubs (see AndroidCompileStubs.kt header).
package android.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
)
annotation class SuppressLint(vararg val value: String)
