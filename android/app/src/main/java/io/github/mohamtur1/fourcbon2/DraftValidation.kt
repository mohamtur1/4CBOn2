package io.github.mohamtur1.fourcbon2

/** Bounded local notes, not executable jobs or authorization to act. */
object DraftValidation {
    const val MAX_TITLE = 100
    const val MAX_GOAL = 4000

    fun error(title: String, goal: String): String? = when {
        title.isBlank() -> "Enter a title."
        goal.isBlank() -> "Enter a goal."
        title.length > MAX_TITLE -> "Title must be at most $MAX_TITLE characters."
        goal.length > MAX_GOAL -> "Goal must be at most $MAX_GOAL characters."
        else -> null
    }
}
