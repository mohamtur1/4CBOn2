package io.github.mohamtur1.fourcbon2.capability

/** Identity of one owner-selected app. */
data class SelectedApp(
    val packageName: String,
    val displayName: String,
) {
    init {
        require(PACKAGE_PATTERN.matches(packageName)) { "Invalid package name." }
        require(displayName.isNotBlank()) { "Display name is required." }
    }

    companion object {
        private val PACKAGE_PATTERN =
            Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
