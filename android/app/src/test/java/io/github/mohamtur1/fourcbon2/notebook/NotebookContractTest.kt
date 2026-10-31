package io.github.mohamtur1.fourcbon2.notebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NotebookContractTest {

    private val sampleNotebook = """
        {"cells":[{"cell_type":"markdown","metadata":{},"source":["# 4CBOn2 Kaggle6"]},
        {"cell_type":"code","execution_count":null,"metadata":{},"outputs":[],"source":["import google.colab"]}],
        "metadata":{},
        "nbformat":4,
        "nbformat_minor":5}
    """.trimIndent()

    @Test
    fun validNotebookJsonIsAccepted() {
        assertTrue(NotebookContract.isWellFormedNotebook(sampleNotebook))
        assertTrue(NotebookContract.declaresCurrentNbformat(sampleNotebook))
    }

    @Test
    fun malformedNotebookJsonIsRejected() {
        assertFalse(NotebookContract.isWellFormedNotebook("{not json"))
        assertFalse(NotebookContract.declaresCurrentNbformat("{not json"))
    }

    @Test
    fun requiredSectionsArePresentInSample() {
        // A minimal sample lacks the full sections; the real notebook must have them.
        assertFalse(NotebookContract.containsRequiredSections(sampleNotebook))
    }

    @Test
    fun realNotebookFileMatchesContract() {
        val repoRoot = File("../..")
        val notebook = File(repoRoot, NotebookContract.NOTEBOOK_FILENAME)
        assumeTrue("Notebook file not reachable from test working directory", notebook.exists())
        val text = notebook.readText()
        assertTrue(NotebookContract.isWellFormedNotebook(text))
        assertTrue(NotebookContract.declaresCurrentNbformat(text))
        assertTrue(NotebookContract.containsRequiredSections(text))
    }
}
