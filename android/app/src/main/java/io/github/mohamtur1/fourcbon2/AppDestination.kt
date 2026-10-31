package io.github.mohamtur1.fourcbon2

enum class AppDestination(
    val label: String,
    val marker: String,
) {
    HOME(label = "Home", marker = "H"),
    ASK(label = "Ask", marker = "?"),
    AGENTS(label = "Agents", marker = "A"),
    BROWSER(label = "Browser", marker = "B"),
    ACTIVITY(label = "Activity", marker = "•"),
}
