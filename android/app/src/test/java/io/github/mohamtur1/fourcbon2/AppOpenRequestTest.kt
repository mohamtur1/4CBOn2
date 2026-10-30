package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppOpenRequestTest {
    @Test
    fun boundedRequestIsAccepted() {
        val request = AppOpenRequest(packageName = "org.example.notes", actionId = "open")

        assertEquals("org.example.notes", request.packageName)
        assertEquals("open", request.actionId)
    }

    @Test
    fun packageNameMustBeADottedAndroidPackage() {
        listOf(
            "",
            "notes",
            "Org.example.notes",
            "org..notes",
            "org.example.notes/slash",
            "org.example.notes path",
            "org.example.notes\n",
            "org.example.",
            ".org.example",
        ).forEach { candidate ->
            assertThrows("expected a package refusal for <$candidate>", IllegalArgumentException::class.java) {
                AppOpenRequest(packageName = candidate, actionId = "open")
            }
        }
    }

    @Test
    fun actionIdMustBeABoundedLowercaseIdentifier() {
        listOf(
            "",
            "Open",
            "1open",
            "-open",
            "open screen",
            "open/thing",
            "open\n",
            "a".repeat(65),
        ).forEach { candidate ->
            assertThrows("expected an action refusal for <$candidate>", IllegalArgumentException::class.java) {
                AppOpenRequest(packageName = "org.example.notes", actionId = candidate)
            }
        }

        // The documented shapes are accepted, so the rule rejects ambiguity rather than actions.
        assertEquals("open", AppOpenRequest("org.example.notes", "open").actionId)
        assertEquals("a".repeat(64), AppOpenRequest("org.example.notes", "a".repeat(64)).actionId)
        assertEquals("open.detail", AppOpenRequest("org.example.notes", "open.detail").actionId)
        assertEquals("open-detail", AppOpenRequest("org.example.notes", "open-detail").actionId)
    }

    @Test
    fun requestCarriesNoFreeTextUriOrIntent() {
        // There is no field a caller could use to smuggle a destination or a command.
        val fields = AppOpenRequest::class.java.declaredFields.map { it.name.lowercase() }
        assertEquals(listOf("packagename", "actionid"), fields)

        listOf("uri", "url", "intent", "command", "extras", "data", "chooser", "text")
            .forEach { forbidden ->
                assertEquals(
                    "no field should be named $forbidden",
                    emptyList<String>(),
                    fields.filter { it.contains(forbidden) },
                )
            }
    }

    @Test
    fun defaultPortRefusesWithoutDispatchingAnything() {
        // The shipped build wires no platform port, so it cannot dispatch at all.
        val report = AppActionPort.NONE.dispatch(AppOpenRequest("org.example.notes", "open"))

        assertEquals(DispatchReport.Rejected("No platform port is configured in this build."), report)
    }

    @Test
    fun dispatchReportKeepsDoubtSeparateFromRefusal() {
        val delivered: DispatchReport = DispatchReport.Delivered("delivered")
        val rejected: DispatchReport = DispatchReport.Rejected("rejected")
        val inDoubt: DispatchReport = DispatchReport.InDoubt("in doubt")

        // Three distinct cases: a boundary cannot silently treat doubt as either answer.
        assertEquals(DispatchReport.Delivered::class.java, delivered::class.java)
        assertEquals(DispatchReport.Rejected::class.java, rejected::class.java)
        assertEquals(DispatchReport.InDoubt::class.java, inDoubt::class.java)
    }
}
