package io.github.mohamtur1.fourcbon2.analysis

import io.github.mohamtur1.fourcbon2.testutil.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalAnalysisTest {

    @Test
    fun csvAnalysisProducesNumericStats() {
        val file = File(createTempDirectory("analysis-csv"), "data.csv")
        file.writeText("name,score\nalpha,10\nbeta,20\ngamma,30\n")
        val result = LocalAnalysis.analyzeCsv(file)
        assertEquals(listOf("name", "score"), result.header)
        assertEquals(3, result.rows)
        assertEquals("score", result.numericColumns.first().name)
        assertEquals(20.0, result.numericColumns.first().mean)
    }

    @Test
    fun csvWithoutRowsIsRejected() {
        val file = File(createTempDirectory("analysis-csv-empty"), "data.csv")
        file.writeText("name,score\n")
        try {
            LocalAnalysis.analyzeCsv(file)
            throw AssertionError("Expected rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun jsonAnalysisExtractsKeysAndObjects() {
        val file = File(createTempDirectory("analysis-json"), "data.json")
        file.writeText("""{"jobs":[{"id":1},{"id":2}],"notes":"ok"}""")
        val result = LocalAnalysis.analyzeJson(file)
        assertEquals(listOf("jobs", "notes"), result.topLevelKeys)
        assertEquals(2, result.objectCount)
    }

    @Test
    fun malformedJsonIsRejected() {
        assertFalse(LocalAnalysis.isValidJson("{\"a\": 1,}"))
        assertFalse(LocalAnalysis.isValidJson("{\"a\": [1, 2"))
        assertFalse(LocalAnalysis.isValidJson("{\"a\": \"unterminated"))
        assertTrue(LocalAnalysis.isValidJson("""{"a":[1,2],"b":"ok"}"""))
    }

    @Test
    fun jsonAnalysisRejectsInvalidDocument() {
        val file = File(createTempDirectory("analysis-json-bad"), "data.json")
        file.writeText("{\"a\": 1,}")
        try {
            LocalAnalysis.analyzeJson(file)
            throw AssertionError("Expected rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
