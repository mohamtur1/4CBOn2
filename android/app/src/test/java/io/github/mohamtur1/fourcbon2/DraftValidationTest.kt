package io.github.mohamtur1.fourcbon2

import org.junit.Assert.*
import org.junit.Test

class DraftValidationTest {
    @Test fun validDraftPasses() { assertNull(DraftValidation.error("A title", "A goal")) }
    @Test fun blankFieldsAreRejected() {
        assertNotNull(DraftValidation.error("  ", "goal"))
        assertNotNull(DraftValidation.error("title", "\n"))
    }
    @Test fun limitsAreEnforced() {
        assertNull(DraftValidation.error("x".repeat(100), "y".repeat(4000)))
        assertNotNull(DraftValidation.error("x".repeat(101), "goal"))
        assertNotNull(DraftValidation.error("title", "y".repeat(4001)))
    }
}
