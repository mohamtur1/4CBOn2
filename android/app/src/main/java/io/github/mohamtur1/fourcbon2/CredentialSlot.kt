package io.github.mohamtur1.fourcbon2

/**
 * Names supported by the future secure credential-entry UI.
 * No credential values are bundled with the application.
 */
enum class CredentialSlot(
    internal val storageKey: String,
) {
    GITHUB_FINE_GRAINED_PAT(storageKey = "github_fine_grained_pat"),
    KAGGLE_BEARER_ACCESS_TOKEN(storageKey = "kaggle_bearer_access_token"),
}
