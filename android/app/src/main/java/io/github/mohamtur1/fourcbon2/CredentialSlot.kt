package io.github.mohamtur1.fourcbon2

/**
 * Names supported by the local secure credential-entry UI.
 * No credential values are bundled with the application.
 */
enum class CredentialSlot(
    internal val storageKey: String,
    val displayTitle: String,
    val scopeDescription: String,
) {
    GITHUB_FINE_GRAINED_PAT(
        storageKey = "github_fine_grained_pat",
        displayTitle = "GitHub Fine-Grained PAT",
        scopeDescription = "Repository read access (mohamtur1/4CBOn2); writes stay separately gated.",
    ),
    KAGGLE_BEARER_ACCESS_TOKEN(
        storageKey = "kaggle_bearer_access_token",
        displayTitle = "Kaggle Bearer / KGAT Token",
        scopeDescription = "Bearer access token (KGAT_... or OAuth Bearer) for read-only Kaggle API probes.",
    ),
    KAGGLE_API_TOKEN(
        storageKey = "kaggle_api_token_basic",
        displayTitle = "Kaggle API Key (kaggle.json)",
        scopeDescription = "Legacy Kaggle username + key pair (kaggle.json or username:key) using HTTP Basic auth.",
    ),
}
