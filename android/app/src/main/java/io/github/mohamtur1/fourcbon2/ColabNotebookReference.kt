package io.github.mohamtur1.fourcbon2

/**
 * Fixed future notebook reference. This is navigation metadata only: it neither opens a browser
 * nor signs in, runs a notebook, creates a runtime, or sends a prompt.
 */
object ColabNotebookReference {
    const val repositoryOwner = "mohamtur1"
    const val repositoryName = "4CBOn2"
    const val branch = "main"
    const val notebookFileName = "4CBOn2_Kaggle6.ipynb"

    const val githubNotebookUrl =
        "https://github.com/mohamtur1/4CBOn2/blob/main/4CBOn2_Kaggle6.ipynb"
    const val colabOpenUrl =
        "https://colab.research.google.com/github/mohamtur1/4CBOn2/blob/main/4CBOn2_Kaggle6.ipynb"

    fun manualHandoff(): ColabManualHandoff = ColabManualHandoff(
        notebookFileName = notebookFileName,
        destinationUrl = colabOpenUrl,
        capability = ColabControlCapability.MANUAL_BROWSER_HANDOFF_ONLY,
    )
}

enum class ColabControlCapability {
    /** The owner may later open the fixed URL in a browser; this source does not launch it. */
    MANUAL_BROWSER_HANDOFF_ONLY,

    /** There is no verified Android API for controlling a Colab runtime from this prototype. */
    PROGRAMMATIC_RUNTIME_CONTROL_UNAVAILABLE,

    /** The in-notebook google.colab.ai probe belongs to Division 30 and has not run. */
    BUILT_IN_AI_UNPROBED,
}

data class ColabManualHandoff(
    val notebookFileName: String,
    val destinationUrl: String,
    val capability: ColabControlCapability,
)
