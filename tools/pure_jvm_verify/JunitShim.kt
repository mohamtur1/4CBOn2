package org.junit

/**
 * Minimal JUnit-4 surface used at compile time by the pure-JVM verification harness
 * (`scripts/verify_pure_kotlin.sh`) when the real `junit:junit` artifact is unavailable.
 *
 * IMPORTANT: this file is *not* part of the Android test source set. It is compiled only by the
 * harness, and the real Gradle/JUnit build must never see it (two `org.junit` definitions would
 * clash on the classpath).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test(val expected: kotlin.reflect.KClass<out Throwable> = None::class)

/** Sentinel meaning "no exception expected" (the real JUnit default is `Test.None`). */
class None : Throwable()

object Assert {
    fun assertEquals(expected: Any?, actual: Any?) {
        assertEquals(null, expected, actual)
    }

    fun assertEquals(message: String?, expected: Any?, actual: Any?) {
        if (expected != actual) {
            fail(prefix(message) + "expected <$expected> but was <$actual>")
        }
    }

    fun assertEquals(expected: Double, actual: Double, delta: Double) {
        require(delta >= 0.0) { "Negative delta is not allowed." }
        if (Math.abs(expected - actual) > delta) {
            fail("expected <$expected> within <$delta> of <$actual>")
        }
    }

    fun assertTrue(condition: Boolean) {
        if (!condition) fail("expected condition to be true")
    }

    fun assertTrue(message: String?, condition: Boolean) {
        if (!condition) fail(prefix(message) + "expected condition to be true")
    }

    fun assertFalse(condition: Boolean) {
        if (condition) fail("expected condition to be false")
    }

    fun assertFalse(message: String?, condition: Boolean) {
        if (condition) fail(prefix(message) + "expected condition to be false")
    }

    fun assertNull(value: Any?) {
        if (value != null) fail("expected null but was <$value>")
    }

    fun assertNotNull(value: Any?) {
        if (value == null) fail("expected a non-null value")
    }

    fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        if (!expected.contentEquals(actual)) {
            fail("expected byte arrays to be equal (expected=${expected.size}B actual=${actual.size}B)")
        }
    }

    fun fail(message: String?): Nothing = throw AssertionError(message)

    private fun prefix(message: String?): String = if (message.isNullOrBlank()) "" else "$message ==> "
}
