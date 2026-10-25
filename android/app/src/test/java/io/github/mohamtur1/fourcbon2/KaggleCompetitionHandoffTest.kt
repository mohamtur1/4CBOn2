package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaggleCompetitionHandoffTest {
    @Test
    fun acceptsCanonicalHttpsCompetitionReference() {
        val reference = KaggleCompetitionReference.parse(
            "https://www.kaggle.com/competitions/titanic",
        )

        assertEquals("https://www.kaggle.com/competitions/titanic", reference?.url)
    }

    @Test
    fun removesOneTrailingSlashFromAcceptedReference() {
        val reference = KaggleCompetitionReference.parse(
            "https://www.kaggle.com/competitions/titanic/",
        )

        assertEquals("https://www.kaggle.com/competitions/titanic", reference?.url)
    }

    @Test
    fun rejectsInsecureAndNonKaggleHosts() {
        assertNull(KaggleCompetitionReference.parse("http://www.kaggle.com/competitions/titanic"))
        assertNull(KaggleCompetitionReference.parse("https://example.com/competitions/titanic"))
        assertNull(KaggleCompetitionReference.parse("https://kaggle.com/competitions/titanic"))
    }

    @Test
    fun rejectsNonCompetitionAndExtraPaths() {
        assertNull(KaggleCompetitionReference.parse("https://www.kaggle.com/"))
        assertNull(KaggleCompetitionReference.parse("https://www.kaggle.com/datasets/example"))
        assertNull(KaggleCompetitionReference.parse("https://www.kaggle.com/competitions/titanic/rules"))
    }

    @Test
    fun rejectsUserInfoQueryFragmentAndExplicitPort() {
        assertNull(KaggleCompetitionReference.parse("https://owner@www.kaggle.com/competitions/titanic"))
        assertNull(KaggleCompetitionReference.parse("https://www.kaggle.com/competitions/titanic?tab=rules"))
        assertNull(KaggleCompetitionReference.parse("https://www.kaggle.com/competitions/titanic#rules"))
        assertNull(KaggleCompetitionReference.parse("https://www.kaggle.com:443/competitions/titanic"))
    }

    @Test
    fun rejectsEmptyAndMalformedInput() {
        assertNull(KaggleCompetitionReference.parse(""))
        assertNull(KaggleCompetitionReference.parse("not a competition URL"))
    }

    @Test
    fun missingReferenceCannotStartHandoff() {
        val state = KaggleManualHandoffState().onOpenRequested()

        assertFalse(state.canOpen)
        assertEquals(KaggleManualHandoffState.Notice.INVALID_REFERENCE, state.notice)
    }

    @Test
    fun validReferenceEnablesOpenRequestWithoutClaimingCompetitionEntry() {
        val state = KaggleManualHandoffState()
            .onReferenceChanged(validReferencePresent = true)
            .onOpenRequested()

        assertTrue(state.canOpen)
        assertEquals(KaggleManualHandoffState.Notice.OPEN_REQUESTED, state.notice)
    }

    @Test
    fun failedOpenDoesNotBecomeSuccessfulHandoff() {
        val state = KaggleManualHandoffState()
            .onReferenceChanged(validReferencePresent = true)
            .onOpenFailed()

        assertEquals(KaggleManualHandoffState.Notice.OPEN_FAILED, state.notice)
    }

    @Test
    fun cancelClearsReferenceAndMarksOnlyCancellation() {
        val state = KaggleManualHandoffState()
            .onReferenceChanged(validReferencePresent = true)
            .onCancel()

        assertFalse(state.referencePresent)
        assertFalse(state.canOpen)
        assertEquals(KaggleManualHandoffState.Notice.CANCELLED, state.notice)
    }
}
