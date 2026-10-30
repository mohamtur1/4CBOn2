package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTableAnalysisTest {
    private val sampleCsv = "first,second\n1,2\n3,4\n-5,6"
    private val sampleJson = "[{\"first\":1,\"second\":2},{\"first\":3,\"second\":4},{\"first\":-5,\"second\":6}]"

    @Test
    fun csvSummaryComputesCountMinimumMaximumAndExactTotal() {
        val summary = summaryOf(sampleCsv)

        assertEquals(AnalysisFormat.CSV, summary.format)
        assertEquals(3, summary.rowCount)
        assertEquals(2, summary.columnCount)
        assertEquals(
            listOf(
                ColumnSummary(name = "first", count = 3, minimum = -5L, maximum = 3L, total = -1L),
                ColumnSummary(name = "second", count = 3, minimum = 2L, maximum = 6L, total = 12L),
            ),
            summary.columns,
        )
    }

    @Test
    fun csvKeepsTheHeaderColumnOrder() {
        val summary = summaryOf("second,first\n1,2\n3,4")

        assertEquals(listOf("second", "first"), summary.columns.map { it.name })
        assertEquals(4L, summary.columns[0].total)
        assertEquals(6L, summary.columns[1].total)
    }

    @Test
    fun negativeValuesSummariseExactly() {
        val summary = summaryOf("a\n-5\n-1\n-9")

        assertEquals(ColumnSummary(name = "a", count = 3, minimum = -9L, maximum = -1L, total = -15L), summary.columns.single())
    }

    @Test
    fun csvAndJsonAgreeOnTheSameTable() {
        val csvSummary = summaryOf(sampleCsv)
        val jsonSummary = summaryOf(sampleJson)

        assertEquals(csvSummary.columns, jsonSummary.columns)
        assertEquals(csvSummary.rowCount, jsonSummary.rowCount)
        assertEquals(AnalysisFormat.CSV, csvSummary.format)
        assertEquals(AnalysisFormat.JSON, jsonSummary.format)
    }

    @Test
    fun repeatedAnalysisOfTheSameInputIsIdentical() {
        assertEquals(LocalTableAnalysis.summarize(sampleCsv), LocalTableAnalysis.summarize(sampleCsv))
        assertEquals(LocalTableAnalysis.summarize(sampleJson), LocalTableAnalysis.summarize(sampleJson))
    }

    @Test
    fun blankInputIsRefusedAsEmpty() {
        assertEquals(AnalysisRefusal.EMPTY_INPUT, refusalOf(""))
        assertEquals(AnalysisRefusal.EMPTY_INPUT, refusalOf("   "))
        assertEquals(AnalysisRefusal.EMPTY_INPUT, refusalOf("\n\n"))
    }

    @Test
    fun oversizedInputIsRefusedBeforeItIsParsed() {
        val oversized = "a".repeat(AnalysisBounds.MAX_INPUT_CHARS + 1)
        assertEquals(AnalysisRefusal.INPUT_TOO_LARGE, refusalOf(oversized))

        // Exactly at the cap the input is still read, and is then refused for a field cap
        // instead: the size limit is a real boundary, not an estimate.
        val atCap = "a".repeat(AnalysisBounds.MAX_INPUT_CHARS - 2) + "\n1"
        assertEquals(AnalysisBounds.MAX_INPUT_CHARS, atCap.length)
        assertEquals(AnalysisRefusal.FIELD_TOO_LONG, refusalOf(atCap))
    }

    @Test
    fun csvWithoutQuotingSupportRefusesAQuote() {
        assertEquals(AnalysisRefusal.MALFORMED_SYNTAX, refusalOf("a,b\n\"1\",2"))
        assertEquals(AnalysisRefusal.MALFORMED_SYNTAX, refusalOf("a,b\n1,\"2\""))
    }

    @Test
    fun csvBlankLineIsRefusedAsMalformed() {
        assertEquals(AnalysisRefusal.MALFORMED_SYNTAX, refusalOf("a,b\n1,2\n\n3,4"))
        assertEquals(AnalysisRefusal.MALFORMED_SYNTAX, refusalOf("\na,b\n1,2"))
    }

    @Test
    fun jsonConstructsOutsideTheSubsetAreRefused() {
        val outsideTheSubset = listOf(
            "[{\"a\":true}]",
            "[{\"a\":null}]",
            "[{\"a\":1.5}]",
            "[{\"a\":1e3}]",
            "[{\"a\":[1]}]",
            "[{\"a\":{\"b\":1}}]",
            "[{\"a\":1},]",
            "[{\"a\":1}] trailing",
            "[{a:1}]",
            "[\"a\"]",
            "{\"a\":1}",
            "[{\"a\":1,\"A\":2}]",
            "[{\"a\":1}",
            "[{\"a\":}]",
            "[{\"a\"1}]",
            "[{\"a\":\"1\"}]",
        )

        outsideTheSubset.forEach { text ->
            assertEquals("expected a syntax refusal for $text", AnalysisRefusal.MALFORMED_SYNTAX, refusalOf(text))
        }
    }

    @Test
    fun tooManyColumnsIsRefused() {
        val header = (1..(AnalysisBounds.MAX_COLUMNS + 1)).joinToString(",") { "c$it" }
        val row = (1..(AnalysisBounds.MAX_COLUMNS + 1)).joinToString(",") { "$it" }

        assertEquals(AnalysisRefusal.TOO_MANY_COLUMNS, refusalOf("$header\n$row"))
    }

    @Test
    fun tooManyRowsIsRefused() {
        val text = buildString {
            append("a,b\n")
            repeat(AnalysisBounds.MAX_ROWS + 1) { append("1,2\n") }
        }

        assertEquals(AnalysisRefusal.TOO_MANY_ROWS, refusalOf(text))
    }

    @Test
    fun overlongFieldIsRefusedBeforeItsValue() {
        // Sixty-five digits is both an over-long field and an out-of-range number. The
        // documented order reports the field length first.
        assertEquals(AnalysisRefusal.FIELD_TOO_LONG, refusalOf("c1,c2\n1," + "9".repeat(AnalysisBounds.MAX_FIELD_LENGTH + 1)))
    }

    @Test
    fun duplicateColumnIsRefusedIgnoringCase() {
        assertEquals(AnalysisRefusal.DUPLICATE_COLUMN, refusalOf("Score,score\n1,2"))
        assertEquals(AnalysisRefusal.DUPLICATE_COLUMN, refusalOf("a,a\n1,2"))
        // A distinct name is still accepted, so the rule is not simply "any repeat".
        assertEquals(2, summaryOf("a,b\n1,2").columnCount)
    }

    @Test
    fun csvRaggedRowIsRefusedAsColumnMismatch() {
        assertEquals(AnalysisRefusal.COLUMN_MISMATCH, refusalOf("a,b\n1,2\n3"))
        assertEquals(AnalysisRefusal.COLUMN_MISMATCH, refusalOf("a,b\n1,2\n3,4,5"))
    }

    @Test
    fun jsonMissingOrExtraKeyIsRefusedAsColumnMismatch() {
        assertEquals(AnalysisRefusal.COLUMN_MISMATCH, refusalOf("[{\"a\":1,\"b\":2},{\"a\":3}]"))
        assertEquals(AnalysisRefusal.COLUMN_MISMATCH, refusalOf("[{\"a\":1,\"b\":2},{\"a\":3,\"c\":4}]"))
    }

    @Test
    fun nonCanonicalNumbersAreRefused() {
        val nonCanonical = listOf("007", "+1", "1.0", "1e3", " 1", "1 ", "-0", "1_000", "0x10", "")

        nonCanonical.forEach { value ->
            assertEquals("expected a numeric-form refusal for <$value>", AnalysisRefusal.NON_NUMERIC_VALUE, refusalOf("a,b\n$value,2"))
        }

        // The canonical forms are accepted, so the rule rejects ambiguity rather than numbers.
        assertEquals(0L, summaryOf("a,b\n0,2").columns[0].total)
        assertEquals(-10L, summaryOf("a,b\n-10,2").columns[0].minimum)
    }

    @Test
    fun magnitudeBeyondTheCapIsRefused() {
        val beyond = AnalysisBounds.MAX_ABS_MAGNITUDE + 1
        assertEquals(AnalysisRefusal.VALUE_OUT_OF_RANGE, refusalOf("a\n$beyond"))
        assertEquals(AnalysisRefusal.VALUE_OUT_OF_RANGE, refusalOf("a\n-$beyond"))
        assertEquals(AnalysisRefusal.VALUE_OUT_OF_RANGE, refusalOf("a\n" + "9".repeat(20)))

        // The cap itself is inclusive on both sides.
        assertEquals(AnalysisBounds.MAX_ABS_MAGNITUDE, summaryOf("a\n${AnalysisBounds.MAX_ABS_MAGNITUDE}").columns.single().maximum)
        assertEquals(-AnalysisBounds.MAX_ABS_MAGNITUDE, summaryOf("a\n-${AnalysisBounds.MAX_ABS_MAGNITUDE}").columns.single().minimum)
    }

    @Test
    fun columnsWithoutDataRowsAreRefused() {
        assertEquals(AnalysisRefusal.NO_DATA_ROWS, refusalOf("a,b"))
        assertEquals(AnalysisRefusal.NO_DATA_ROWS, refusalOf("[]"))
        assertEquals(AnalysisRefusal.NO_DATA_ROWS, refusalOf("[ ]"))
    }

    @Test
    fun refusalsAreBoundedAndNeverEchoTheInput() {
        val sentinel = "SENTINELSECRETVALUE"
        val refusals = listOf(
            refusalOutcome("a,b\n1,$sentinel"),
            refusalOutcome("$sentinel,$sentinel\n1,2"),
            refusalOutcome("c1\n" + sentinel.repeat(20)),
        )

        refusals.forEach { refusal ->
            assertFalse("a refusal must not repeat the input", refusal.message.contains(sentinel))
            assertFalse("a refusal must not quote a column name", refusal.message.contains("SENTINEL"))
            assertTrue("a refusal message must not be blank", refusal.message.isNotBlank())
            assertTrue(
                "a refusal message must stay bounded",
                refusal.message.length <= AnalysisBounds.MAX_MESSAGE_LENGTH,
            )
        }
    }

    @Test
    fun checksRunInTheDocumentedOrder() {
        // Blank beats size: whitespace beyond the size cap is still an empty table.
        assertEquals(AnalysisRefusal.EMPTY_INPUT, refusalOf(" ".repeat(AnalysisBounds.MAX_INPUT_CHARS + 1)))

        // Size beats syntax: text that would not parse is refused for its length first.
        assertEquals(AnalysisRefusal.INPUT_TOO_LARGE, refusalOf("\"".repeat(AnalysisBounds.MAX_INPUT_CHARS + 1)))

        // Columns beat rows: both caps are broken and the earlier rule wins.
        val wide = (1..(AnalysisBounds.MAX_COLUMNS + 1)).joinToString(",") { "c$it" }
        val row = (1..(AnalysisBounds.MAX_COLUMNS + 1)).joinToString(",") { "1" }
        val text = buildString {
            append(wide).append('\n')
            repeat(AnalysisBounds.MAX_ROWS + 1) { append(row).append('\n') }
        }
        assertEquals(AnalysisRefusal.TOO_MANY_COLUMNS, refusalOf(text))

        // Duplicate names beat "no data": the header is refused even with no rows.
        assertEquals(AnalysisRefusal.DUPLICATE_COLUMN, refusalOf("a,A"))
    }

    @Test
    fun totalCannotOverflowALong() {
        assertTrue(AnalysisBounds.MAX_ABS_TOTAL < Long.MAX_VALUE)
        assertTrue(AnalysisBounds.MAX_ABS_TOTAL == AnalysisBounds.MAX_ROWS.toLong() * AnalysisBounds.MAX_ABS_MAGNITUDE)

        val text = buildString {
            append("a\n")
            repeat(AnalysisBounds.MAX_ROWS) { append(AnalysisBounds.MAX_ABS_MAGNITUDE).append('\n') }
        }

        assertEquals(AnalysisBounds.MAX_ABS_TOTAL, summaryOf(text).columns.single().total)
    }

    @Test
    fun analysisIsInertAndHoldsNoState() {
        val instanceFields = LocalTableAnalysis::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertTrue("the analyser must hold no instance state", instanceFields.isEmpty())

        val summarize = LocalTableAnalysis::class.java.declaredMethods
            .firstOrNull { it.name == "summarize" }
            ?: throw AssertionError("summarize is missing")
        assertEquals(AnalysisOutcome::class.java, summarize.returnType)
        assertEquals(String::class.java, summarize.parameterTypes.single())

        // No method can accept or return a database, registry, lease, or scope handle.
        LocalTableAnalysis::class.java.declaredMethods.forEach { method ->
            (method.parameterTypes.toList() + method.returnType).forEach { type ->
                val name = type.name
                assertFalse("unexpected boundary type $name", name.contains("Database"))
                assertFalse("unexpected boundary type $name", name.contains("Registry"))
                assertFalse("unexpected boundary type $name", name.contains("Lease"))
                assertFalse("unexpected boundary type $name", name.contains("Scope"))
            }
        }

        // Running an analysis changes no owner decision.
        val now = 1_700_000_000_000L
        val registry = SelectedAppCapabilityRegistry(ActionRouteProbe.NONE)
        val lease = ActionLease(createdAtEpochMillis = now, nowEpochMillis = { now })
        lease.apply(
            LeaseCommand(LeaseCommandKind.STOP, LeaseCommandOrigin.OWNER_LOCAL, epoch = 1, issuedAtEpochMillis = now),
        )

        summaryOf("a\n1")

        assertFalse(registry.isSelected("org.kaggle.app"))
        assertEquals(LeaseState.STOPPED, lease.state)
    }

    @Test
    fun jsonToleratesWhitespaceAndReorderedKeys() {
        val text = """
            [
              { "b" : 2 , "a" : 1 } ,
              { "a" : 3 , "b" : 4 }
            ]
        """.trimIndent()

        val summary = summaryOf(text)

        assertEquals(listOf("b", "a"), summary.columns.map { it.name })
        assertEquals(ColumnSummary(name = "b", count = 2, minimum = 2L, maximum = 4L, total = 6L), summary.columns[0])
        assertEquals(ColumnSummary(name = "a", count = 2, minimum = 1L, maximum = 3L, total = 4L), summary.columns[1])
    }

    @Test
    fun inputStartingWithABracketIsReadAsJson() {
        // Documented sniffing rule: a leading bracket selects JSON, so a CSV whose first
        // field starts with one is refused rather than quietly read as CSV.
        assertEquals(AnalysisRefusal.MALFORMED_SYNTAX, refusalOf("[a],b\n1,2"))
    }

    private fun summaryOf(text: String): TableSummary {
        val outcome = LocalTableAnalysis.summarize(text)
        assertTrue("expected a summary but was $outcome", outcome is AnalysisOutcome.Summarized)
        return (outcome as AnalysisOutcome.Summarized).summary
    }

    private fun refusalOf(text: String): AnalysisRefusal = refusalOutcome(text).reason

    private fun refusalOutcome(text: String): AnalysisOutcome.Refused {
        val outcome = LocalTableAnalysis.summarize(text)
        assertTrue("expected a refusal but was $outcome", outcome is AnalysisOutcome.Refused)
        return outcome as AnalysisOutcome.Refused
    }
}
