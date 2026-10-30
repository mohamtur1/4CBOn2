package io.github.mohamtur1.fourcbon2

import java.io.File
import java.io.RandomAccessFile
import java.util.Base64

/**
 * A tiny versioned, file-backed local entity store for the first prototype.
 *
 * Open the database with a file in app-private storage (for example
 * `defaultFile(context.filesDir)`). Every mutation is flushed and synced to
 * that file. Closing releases the open file; a fresh instance must read the
 * same file to recover records. This is intentionally not cloud storage,
 * encrypted storage, or a full SQL/Room engine.
 *
 * **Schema history**
 *
 * - Version 1 stored `LocalAgentDraft` and `LocalActionEvent`.
 * - Version 2 adds `LocalKnowledgeItem` (Division 17, offline knowledge library).
 *
 * A version 1 file is still readable: `open()` accepts either header, loads the
 * existing rows unchanged, and then rewrites the file with the version 2 header on the
 * first mutation (or immediately on open, because opening always normalises the file).
 * No version 1 row shape changed, so the migration is a header upgrade rather than a
 * data rewrite. Unknown future versions are still rejected.
 */
class LocalEntityDatabase(
    private val databaseFile: File,
) : AutoCloseable {
    companion object {
        const val SCHEMA_VERSION = 2

        /** Oldest file format this build can still read. */
        const val OLDEST_READABLE_SCHEMA_VERSION = 1

        const val DEFAULT_FILE_NAME = "4cbon2-local-entities.v1.db"

        private const val HEADER_PREFIX = "4CBON2_LOCAL_ENTITY_DB\t"
        private const val HEADER = HEADER_PREFIX + "2"
        private const val LEGACY_V1_HEADER = HEADER_PREFIX + "1"
        private const val DRAFT_ROW = "DRAFT"
        private const val EVENT_ROW = "EVENT"
        private const val KNOWLEDGE_ROW = "KNOWLEDGE"
        private const val EMPTY_FIELD = "~"

        fun defaultFile(storageDirectory: File): File = File(storageDirectory, DEFAULT_FILE_NAME)
    }

    private val drafts = linkedMapOf<String, LocalAgentDraft>()
    private val events = linkedMapOf<String, LocalActionEvent>()
    private val knowledge = linkedMapOf<String, LocalKnowledgeItem>()
    private var openFile: RandomAccessFile? = null

    @Synchronized
    fun open() {
        if (openFile != null) return

        val loaded = readOrEmpty()
        drafts.clear()
        events.clear()
        knowledge.clear()
        drafts.putAll(loaded.drafts)
        events.putAll(loaded.events)
        knowledge.putAll(loaded.knowledge)

        databaseFile.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) { "Cannot create local database directory." }
        }
        val file = RandomAccessFile(databaseFile, "rw")
        openFile = file
        try {
            // Normalises a legacy version 1 header to version 2 without changing any row.
            persist()
        } catch (error: Throwable) {
            openFile = null
            file.close()
            drafts.clear()
            events.clear()
            knowledge.clear()
            throw error
        }
    }

    @Synchronized
    fun saveDraft(draft: LocalAgentDraft) {
        ensureOpen()
        val previous = drafts.put(draft.id, draft)
        try {
            persist()
        } catch (error: Throwable) {
            if (previous == null) drafts.remove(draft.id) else drafts[draft.id] = previous
            throw error
        }
    }

    @Synchronized
    fun recordEvent(event: LocalActionEvent) {
        ensureOpen()
        require(drafts.containsKey(event.draftId)) {
            "Event ${event.id} refers to a missing local draft ${event.draftId}."
        }
        val previous = events.put(event.id, event)
        try {
            persist()
        } catch (error: Throwable) {
            if (previous == null) events.remove(event.id) else events[event.id] = previous
            throw error
        }
    }

    @Synchronized
    fun saveKnowledgeItem(item: LocalKnowledgeItem) {
        ensureOpen()
        val previous = knowledge.put(item.id, item)
        try {
            persist()
        } catch (error: Throwable) {
            if (previous == null) knowledge.remove(item.id) else knowledge[item.id] = previous
            throw error
        }
    }

    @Synchronized
    fun findDraft(id: String): LocalAgentDraft? {
        ensureOpen()
        return drafts[id]
    }

    @Synchronized
    fun findKnowledgeItem(id: String): LocalKnowledgeItem? {
        ensureOpen()
        return knowledge[id]
    }

    @Synchronized
    fun drafts(): List<LocalAgentDraft> {
        ensureOpen()
        return orderedDrafts().toList()
    }

    @Synchronized
    fun events(): List<LocalActionEvent> {
        ensureOpen()
        return orderedEvents().toList()
    }

    @Synchronized
    fun knowledgeItems(): List<LocalKnowledgeItem> {
        ensureOpen()
        return orderedKnowledge().toList()
    }

    @Synchronized
    override fun close() {
        openFile?.close()
        openFile = null
        drafts.clear()
        events.clear()
        knowledge.clear()
    }

    private fun ensureOpen() {
        check(openFile != null) { "The local entity database is closed." }
    }

    private fun readOrEmpty(): LoadedState {
        if (!databaseFile.exists() || databaseFile.length() == 0L) return LoadedState()
        val lines = databaseFile.readText(Charsets.UTF_8)
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return LoadedState()
        val header = lines.first()
        check(header == HEADER || header == LEGACY_V1_HEADER) {
            "Unsupported local entity database header: $header"
        }

        val loaded = LoadedState(
            drafts = linkedMapOf(),
            events = linkedMapOf(),
            knowledge = linkedMapOf(),
        )
        lines.drop(1).forEachIndexed { index, line ->
            parseRow(line, lineNumber = index + 2, loaded)
        }
        loaded.events.values.forEach { event ->
            check(loaded.drafts.containsKey(event.draftId)) {
                "Event ${event.id} refers to missing draft ${event.draftId}."
            }
        }
        return loaded
    }

    private fun parseRow(
        line: String,
        lineNumber: Int,
        loaded: LoadedState,
    ) {
        val columns = line.split('\t')
        when (columns.firstOrNull()) {
            DRAFT_ROW -> {
                check(columns.size == 8) { invalidRow(lineNumber, "draft field count") }
                val draft = try {
                    LocalAgentDraft(
                        id = decode(columns[1], lineNumber),
                        title = decode(columns[2], lineNumber),
                        goal = decode(columns[3], lineNumber),
                        templateName = decode(columns[4], lineNumber),
                        state = parseEnum<LocalDraftState>(columns[5], lineNumber),
                        createdAtEpochMillis = parseTimestamp(columns[6], lineNumber),
                        updatedAtEpochMillis = parseTimestamp(columns[7], lineNumber),
                    )
                } catch (error: IllegalArgumentException) {
                    throw IllegalStateException(invalidRow(lineNumber, error.message), error)
                }
                if (loaded.drafts.put(draft.id, draft) != null) {
                    throw IllegalStateException(invalidRow(lineNumber, "duplicate draft id"))
                }
            }

            EVENT_ROW -> {
                check(columns.size == 7) { invalidRow(lineNumber, "event field count") }
                val event = try {
                    LocalActionEvent(
                        id = decode(columns[1], lineNumber),
                        draftId = decode(columns[2], lineNumber),
                        kind = parseEnum<LocalActionKind>(columns[3], lineNumber),
                        outcome = parseEnum<LocalActionOutcome>(columns[4], lineNumber),
                        note = decode(columns[5], lineNumber),
                        createdAtEpochMillis = parseTimestamp(columns[6], lineNumber),
                    )
                } catch (error: IllegalArgumentException) {
                    throw IllegalStateException(invalidRow(lineNumber, error.message), error)
                }
                if (loaded.events.put(event.id, event) != null) {
                    throw IllegalStateException(invalidRow(lineNumber, "duplicate event id"))
                }
            }

            KNOWLEDGE_ROW -> {
                check(columns.size == 7) { invalidRow(lineNumber, "knowledge field count") }
                val item = try {
                    LocalKnowledgeItem(
                        id = decode(columns[1], lineNumber),
                        title = decode(columns[2], lineNumber),
                        body = decode(columns[3], lineNumber),
                        sourceLabel = decode(columns[4], lineNumber),
                        provenance = parseEnum<KnowledgeProvenance>(columns[5], lineNumber),
                        savedAtEpochMillis = parseTimestamp(columns[6], lineNumber),
                    )
                } catch (error: IllegalArgumentException) {
                    throw IllegalStateException(invalidRow(lineNumber, error.message), error)
                }
                if (loaded.knowledge.put(item.id, item) != null) {
                    throw IllegalStateException(invalidRow(lineNumber, "duplicate knowledge id"))
                }
            }

            else -> throw IllegalStateException(invalidRow(lineNumber, "unknown entity type"))
        }
    }

    private fun decode(value: String, lineNumber: Int): String {
        if (value == EMPTY_FIELD) return ""
        return try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException(invalidRow(lineNumber, "invalid Base64 field"), error)
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, lineNumber: Int): T = try {
        enumValueOf<T>(value)
    } catch (error: IllegalArgumentException) {
        throw IllegalStateException(invalidRow(lineNumber, "unknown enum value"), error)
    }

    private fun parseTimestamp(value: String, lineNumber: Int): Long =
        value.toLongOrNull()?.takeIf { it > 0 }
            ?: throw IllegalStateException(invalidRow(lineNumber, "invalid timestamp"))

    private fun invalidRow(lineNumber: Int, detail: String?): String =
        "Invalid local database row $lineNumber: $detail"

    private fun persist() {
        val file = openFile ?: error("The local entity database is closed.")
        val bytes = serialize().toByteArray(Charsets.UTF_8)
        file.seek(0)
        file.setLength(0)
        file.write(bytes)
        file.fd.sync()
    }

    private fun serialize(): String {
        val lines = mutableListOf(HEADER)
        orderedDrafts().forEach { draft ->
            lines += listOf(
                DRAFT_ROW,
                encode(draft.id),
                encode(draft.title),
                encode(draft.goal),
                encode(draft.templateName),
                draft.state.name,
                draft.createdAtEpochMillis.toString(),
                draft.updatedAtEpochMillis.toString(),
            ).joinToString("\t")
        }
        orderedEvents().forEach { event ->
            lines += listOf(
                EVENT_ROW,
                encode(event.id),
                encode(event.draftId),
                event.kind.name,
                event.outcome.name,
                encode(event.note),
                event.createdAtEpochMillis.toString(),
            ).joinToString("\t")
        }
        orderedKnowledge().forEach { item ->
            lines += listOf(
                KNOWLEDGE_ROW,
                encode(item.id),
                encode(item.title),
                encode(item.body),
                encode(item.sourceLabel),
                item.provenance.name,
                item.savedAtEpochMillis.toString(),
            ).joinToString("\t")
        }
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun encode(value: String): String =
        if (value.isEmpty()) {
            EMPTY_FIELD
        } else {
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        }

    private fun orderedDrafts(): Collection<LocalAgentDraft> = drafts.values.sortedWith(
        Comparator { left, right ->
            val byTime = left.createdAtEpochMillis.compareTo(right.createdAtEpochMillis)
            if (byTime != 0) byTime else left.id.compareTo(right.id)
        },
    )

    private fun orderedEvents(): Collection<LocalActionEvent> = events.values.sortedWith(
        Comparator { left, right ->
            val byTime = left.createdAtEpochMillis.compareTo(right.createdAtEpochMillis)
            if (byTime != 0) byTime else left.id.compareTo(right.id)
        },
    )

    private fun orderedKnowledge(): Collection<LocalKnowledgeItem> = knowledge.values.sortedWith(
        Comparator { left, right ->
            val byTime = left.savedAtEpochMillis.compareTo(right.savedAtEpochMillis)
            if (byTime != 0) byTime else left.id.compareTo(right.id)
        },
    )

    private data class LoadedState(
        val drafts: MutableMap<String, LocalAgentDraft> = linkedMapOf(),
        val events: MutableMap<String, LocalActionEvent> = linkedMapOf(),
        val knowledge: MutableMap<String, LocalKnowledgeItem> = linkedMapOf(),
    )
}
