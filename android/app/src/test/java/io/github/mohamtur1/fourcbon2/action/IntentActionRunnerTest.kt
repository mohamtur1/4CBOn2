package io.github.mohamtur1.fourcbon2.action

import io.github.mohamtur1.fourcbon2.capability.AppActionKind
import io.github.mohamtur1.fourcbon2.capability.AppCapability
import io.github.mohamtur1.fourcbon2.capability.AppCapabilityRegistry
import io.github.mohamtur1.fourcbon2.capability.SelectedApp
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentActionRunnerTest {

    private val notes = SelectedApp(packageName = "com.example.notes", displayName = "Notes")

    private fun runner(supported: Boolean): IntentActionRunner {
        val registry = AppCapabilityRegistry()
        registry.register(
            AppCapability(notes, AppActionKind.OPEN_APP, supported = supported, reason = "not available"),
        )
        registry.register(
            AppCapability(notes, AppActionKind.PREPARE_INTENT, supported = true, reason = ""),
        )
        return IntentActionRunner(registry)
    }

    @Test
    fun unsupportedActionIsReported() {
        val outcome = runner(supported = false).run(
            SupportedAppAction(notes, AppActionKind.OPEN_APP, "Open notes", requiresApproval = false),
        )
        assertEquals(IntentActionRunner.ActionResult.UNSUPPORTED, outcome.result)
    }

    @Test
    fun openActionIsDelivered() {
        val outcome = runner(supported = true).run(
            SupportedAppAction(notes, AppActionKind.OPEN_APP, "Open notes", requiresApproval = false),
        )
        assertEquals(IntentActionRunner.ActionResult.DELIVERED, outcome.result)
    }

    @Test
    fun prepareActionRequiresOwnerHandoff() {
        val outcome = runner(supported = true).run(
            SupportedAppAction(notes, AppActionKind.PREPARE_INTENT, "Prepare intent", requiresApproval = true),
        )
        assertEquals(IntentActionRunner.ActionResult.HANDOFF_REQUIRED, outcome.result)
    }
}
