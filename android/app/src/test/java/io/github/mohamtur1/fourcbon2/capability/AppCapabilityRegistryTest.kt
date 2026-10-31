package io.github.mohamtur1.fourcbon2.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCapabilityRegistryTest {

    private val notes = SelectedApp(packageName = "com.example.notes", displayName = "Notes")
    private val unselected = SelectedApp(packageName = "com.example.bank", displayName = "Bank")

    @Test
    fun unselectedAppReceivesNoCapability() {
        val registry = AppCapabilityRegistry()
        registry.register(
            AppCapability(notes, AppActionKind.OPEN_APP, supported = true, reason = ""),
        )
        assertFalse(registry.isSelected(unselected))
        try {
            registry.supports(unselected, AppActionKind.OPEN_APP)
            throw AssertionError("Expected unselected rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun supportsOnlyRegisteredActions() {
        val registry = AppCapabilityRegistry()
        registry.register(
            AppCapability(notes, AppActionKind.OPEN_APP, supported = true, reason = ""),
        )
        registry.register(
            AppCapability(notes, AppActionKind.PREPARE_INTENT, supported = false, reason = "not yet implemented"),
        )
        assertTrue(registry.supports(notes, AppActionKind.OPEN_APP))
        assertFalse(registry.supports(notes, AppActionKind.PREPARE_INTENT))
        assertEquals("not yet implemented", registry.unavailableReason(notes, AppActionKind.PREPARE_INTENT))
    }

    @Test
    fun reregistrationUpdatesSupport() {
        val registry = AppCapabilityRegistry()
        registry.register(
            AppCapability(notes, AppActionKind.OPEN_APP, supported = false, reason = "pending"),
        )
        registry.register(
            AppCapability(notes, AppActionKind.OPEN_APP, supported = true, reason = ""),
        )
        assertTrue(registry.supports(notes, AppActionKind.OPEN_APP))
        assertEquals(1, registry.all().size)
    }
}
