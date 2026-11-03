package io.github.mohamtur1.fourcbon2.tooling

import java.io.File
import kotlin.system.exitProcess

/**
 * Reflective runner for the pure-JVM verification harness.
 *
 * Discovers every class in [args] package directories, invokes all `@org.junit.Test` methods, and
 * prints a JUnit-style summary. Used by `scripts/verify_pure_kotlin.sh` so the real repo test files
 * are executed (not a rewritten copy) in sandboxes where the Android SDK / Gradle are unavailable.
 */
private annotation class TestMarker

fun main(args: Array<String>) {
    val classDirs = args.toList().ifEmpty { listOf(".") }
    val loader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    val testAnnotation = Class.forName("org.junit.Test")

    val testClasses = mutableListOf<Class<*>>()
    for (dir in classDirs) {
        val root = File(dir)
        if (!root.exists()) continue
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") && '$' !in it.name }.forEach { file ->
            val rel = file.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
            val cls = runCatching { Class.forName(rel, false, loader) }.getOrNull() ?: return@forEach
            if (cls.declaredMethods.any { m -> m.annotations.any { it.annotationClass.java == testAnnotation } }) {
                testClasses += cls
            }
        }
    }

    var passed = 0
    val failures = mutableListOf<String>()
    for (cls in testClasses.sortedBy { it.name }) {
        val instance = runCatching { cls.getDeclaredConstructor().newInstance() }.getOrNull()
        if (instance == null) {
            failures += "${cls.name}: could not instantiate"
            continue
        }
        val methods = cls.declaredMethods
            .filter { m -> m.annotations.any { it.annotationClass.java == testAnnotation } }
            .sortedBy { it.name }
        for (method in methods) {
            val label = "${cls.simpleName}.${method.name}"
            val expectedAttr = method.annotations
                .firstOrNull { it.annotationClass.java == testAnnotation }
            val expected: Class<*>? = runCatching {
                when (val value = expectedAttr?.annotationClass?.java
                    ?.getMethod("expected")
                    ?.invoke(expectedAttr)) {
                    is Class<*> -> value
                    is kotlin.reflect.KClass<*> -> value.java
                    else -> null
                }
            }.getOrNull()
            try {
                method.isAccessible = true
                method.invoke(instance)
                if (expected != null && expected.name != "org.junit.None") {
                    failures += "$label: expected ${expected.simpleName} but nothing was thrown"
                    println("FAIL  $label (expected ${expected.simpleName})")
                } else {
                    passed++
                    println("PASS  $label")
                }
            } catch (t: Throwable) {
                val cause = t.cause ?: t
                if (expected != null && expected.isInstance(cause)) {
                    passed++
                    println("PASS  $label (threw expected ${expected.simpleName})")
                } else {
                    failures += "$label: ${cause::class.java.simpleName}: ${cause.message}"
                    println("FAIL  $label")
                    println("      ${cause::class.java.simpleName}: ${cause.message}")
                }
            }
        }
    }

    println()
    println("Tests run: ${passed + failures.size}, Passed: $passed, Failed: ${failures.size}")
    if (failures.isNotEmpty()) {
        println()
        failures.forEach { println(" - $it") }
        exitProcess(1)
    }
    if (passed == 0) {
        println("No tests discovered.")
        exitProcess(2)
    }
}
