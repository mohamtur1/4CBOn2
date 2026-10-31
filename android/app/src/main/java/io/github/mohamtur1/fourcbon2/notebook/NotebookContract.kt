package io.github.mohamtur1.fourcbon2.notebook

import io.github.mohamtur1.fourcbon2.analysis.LocalAnalysis

/**
 * Notebook contract (Division 29).
 *
 * The fixed notebook target is `4CBOn2_Kaggle6.ipynb`. The Android app and
 * the notebook share one versioned job specification, so both sides can
 * validate the same contract before any run.
 */
object NotebookContract {
    const val NOTEBOOK_FILENAME = "4CBOn2_Kaggle6.ipynb"
    const val CONTRACT_VERSION = "1.0"

    /** The notebook must be well-formed JSON (structural validation). */
    fun isWellFormedNotebook(jsonText: String): Boolean = LocalAnalysis.isValidJson(jsonText)

    /** The notebook must declare the current nbformat. */
    fun declaresCurrentNbformat(jsonText: String): Boolean {
        if (!isWellFormedNotebook(jsonText)) return false
        val validator = JsonFacts(jsonText)
        return validator.nbformat() == 4
    }

    /** The capability panel and runtime-detection cells must exist. */
    fun containsRequiredSections(jsonText: String): Boolean {
        if (!isWellFormedNotebook(jsonText)) return false
        return jsonText.contains("Runtime detection") &&
            jsonText.contains("Controller") &&
            jsonText.contains("Capability panel") &&
            jsonText.contains("google.colab.ai")
    }

    private class JsonFacts(private val text: String) {
        var i = 0

        fun nbformat(): Int {
            i = indexOf("\"nbformat\"", i)
            if (i < 0) return -1
            i = text.indexOf(':', i + 1)
            if (i < 0) return -1
            i = skipWhitespace()
            var value = 0
            var negative = false
            if (text[i] == '-') {
                negative = true
                i++
            }
            while (i < text.length && text[i].isDigit()) {
                value = value * 10 + (text[i] - '0')
                i++
            }
            return if (negative) -value else value
        }

        private fun indexOf(needle: String, from: Int): Int {
            val idx = text.indexOf(needle, from)
            return idx
        }

        private fun skipWhitespace(): Int {
            while (i < text.length && text[i].isWhitespace()) i++
            return i
        }
    }
}
