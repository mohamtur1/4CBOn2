package io.github.mohamtur1.fourcbon2.github

/**
 * Local diff between two cached file versions (Division 22).
 *
 * Produces a line-level diff and an exportable change description. The
 * change is always marked as requiring owner approval — no unapproved write,
 * no force-push, and no repository mutation happens here.
 */
object VersionDiff {

    data class DiffLine(
        val kind: Kind,
        val text: String,
    )

    enum class Kind { EQUAL, REMOVED, ADDED }

    data class Diff(
        val removedLines: Int,
        val addedLines: Int,
        val lines: List<DiffLine>,
    )

    data class ExportableChange(
        val path: String,
        val baseRevision: String,
        val headRevision: String,
        val summary: String,
        val patch: String,
        val requiresApproval: Boolean,
    )

    fun diff(oldContent: String, newContent: String): Diff {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        val lines = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        var removed = 0
        var added = 0
        while (i < oldLines.size && j < newLines.size) {
            if (oldLines[i] == newLines[j]) {
                lines.add(DiffLine(Kind.EQUAL, oldLines[i]))
                i++
                j++
            } else if (newLines[j] in oldLines) {
                lines.add(DiffLine(Kind.ADDED, newLines[j]))
                added++
                j++
            } else {
                lines.add(DiffLine(Kind.REMOVED, oldLines[i]))
                removed++
                i++
            }
        }
        while (i < oldLines.size) {
            lines.add(DiffLine(Kind.REMOVED, oldLines[i]))
            removed++
            i++
        }
        while (j < newLines.size) {
            lines.add(DiffLine(Kind.ADDED, newLines[j]))
            added++
            j++
        }
        return Diff(removed, added, lines)
    }

    fun export(
        path: String,
        baseRevision: String,
        headRevision: String,
        diff: Diff,
    ): ExportableChange {
        val patch = diff.lines.joinToString(separator = "\n") { line ->
            when (line.kind) {
                Kind.ADDED -> "+${line.text}"
                Kind.REMOVED -> "-${line.text}"
                Kind.EQUAL -> " ${line.text}"
            }
        }
        return ExportableChange(
            path = path,
            baseRevision = baseRevision,
            headRevision = headRevision,
            summary = "${diff.addedLines} added, ${diff.removedLines} removed in $path",
            patch = patch,
            requiresApproval = true,
        )
    }
}
