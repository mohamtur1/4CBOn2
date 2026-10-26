package io.github.mohamtur1.ttsqueueplayer.store

import android.content.Context
import io.github.mohamtur1.ttsqueueplayer.core.QueueRules
import io.github.mohamtur1.ttsqueueplayer.model.ItemSource
import io.github.mohamtur1.ttsqueueplayer.model.QueueItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-phone storage for everything that must survive the app being closed:
 * the queue (max 10 items, files and pastes mixed), the exact playback
 * position (which item, which chunk), and the voice/rate/pitch/language
 * settings. Stored in SharedPreferences as JSON — no database needed for
 * up to 10 documents, and no permissions required.
 *
 * The Activity and the PlaybackService run in the same process, so they share
 * this single instance via [get].
 */
class QueueStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tts_queue_store", Context.MODE_PRIVATE)

    private val items: MutableList<QueueItem> = loadItems()

    /** Read-only snapshot of the queue, in play order. */
    val queue: List<QueueItem> get() = synchronized(items) { items.toList() }

    val size: Int get() = synchronized(items) { items.size }

    // ---- queue mutations -------------------------------------------------

    /** Adds an item to the END of the queue. Returns null if the queue is full or text is blank. */
    fun add(title: String, source: ItemSource, text: String): QueueItem? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        synchronized(items) {
            if (!QueueRules.canAdd(items.size)) return null
            val item = QueueItem(id = newId(), title = title, source = source, text = clean)
            items.add(item)
            saveItems()
            return item
        }
    }

    fun addPaste(text: String, source: ItemSource = ItemSource.PASTE): QueueItem? {
        val fallback = synchronized(items) { items.count { it.source != ItemSource.FILE } + 1 }
        return add(QueueRules.autoTitle(text, fallback), source, text)
    }

    fun addFile(displayName: String, text: String): QueueItem? =
        add(QueueRules.fileTitle(displayName), ItemSource.FILE, text)

    fun remove(id: String): Boolean = synchronized(items) {
        val removed = items.removeAll { it.id == id }
        if (removed) saveItems()
        removed
    }

    fun rename(id: String, newTitle: String): Boolean = synchronized(items) {
        val item = items.firstOrNull { it.id == id } ?: return false
        val clean = newTitle.trim()
        if (clean.isEmpty()) return false
        item.title = clean
        saveItems()
        true
    }

    /** Moves an item one step (-1 = up / earlier, +1 = down / later). */
    fun move(id: String, direction: Int): Boolean = synchronized(items) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return false
        val target = index + direction
        if (target < 0 || target >= items.size) return false
        val item = items.removeAt(index)
        items.add(target, item)
        saveItems()
        true
    }

    fun itemById(id: String?): QueueItem? =
        if (id == null) null else synchronized(items) { items.firstOrNull { it.id == id } }

    fun indexOfId(id: String?): Int =
        if (id == null) -1 else synchronized(items) { items.indexOfFirst { it.id == id } }

    // ---- playback position ----------------------------------------------

    var positionItemId: String?
        get() = prefs.getString(KEY_POS_ITEM, null)
        set(value) = prefs.edit().putString(KEY_POS_ITEM, value).apply()

    var positionChunk: Int
        get() = prefs.getInt(KEY_POS_CHUNK, 0)
        set(value) = prefs.edit().putInt(KEY_POS_CHUNK, value).apply()

    fun savePosition(itemId: String?, chunk: Int) {
        prefs.edit()
            .putString(KEY_POS_ITEM, itemId)
            .putInt(KEY_POS_CHUNK, chunk.coerceAtLeast(0))
            .apply()
    }

    // ---- settings --------------------------------------------------------

    var languageTag: String // "en-US" or "en-GB", as in the original HTML
        get() = prefs.getString(KEY_LANG, "en-US") ?: "en-US"
        set(value) = prefs.edit().putString(KEY_LANG, value).apply()

    var voiceName: String // engine voice name; "" = engine default for the language
        get() = prefs.getString(KEY_VOICE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    var rate: Float // 0.5 .. 3.0, default 1.2 as in the HTML
        get() = prefs.getFloat(KEY_RATE, 1.2f)
        set(value) = prefs.edit().putFloat(KEY_RATE, value.coerceIn(0.5f, 3.0f)).apply()

    var pitch: Float // 0.8 .. 1.5, default 1.0 as in the HTML
        get() = prefs.getFloat(KEY_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_PITCH, value.coerceIn(0.8f, 1.5f)).apply()

    // ---- persistence internals ------------------------------------------

    private fun loadItems(): MutableList<QueueItem> {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                QueueItem(
                    id = o.getString("id"),
                    title = o.optString("title", "Document"),
                    source = runCatching { ItemSource.valueOf(o.optString("source", "PASTE")) }
                        .getOrDefault(ItemSource.PASTE),
                    text = o.optString("text", "")
                )
            }.filter { it.text.isNotBlank() }.take(QueueRules.MAX_ITEMS).toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveItems() {
        val arr = JSONArray()
        synchronized(items) {
            for (item in items) {
                arr.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("source", item.source.name)
                        .put("text", item.text)
                )
            }
        }
        prefs.edit().putString(KEY_QUEUE, arr.toString()).apply()
    }

    companion object {
        private const val KEY_QUEUE = "queue_json"
        private const val KEY_POS_ITEM = "position_item_id"
        private const val KEY_POS_CHUNK = "position_chunk"
        private const val KEY_LANG = "language_tag"
        private const val KEY_VOICE = "voice_name"
        private const val KEY_RATE = "rate"
        private const val KEY_PITCH = "pitch"

        private val counter = AtomicInteger(0)
        private fun newId(): String =
            "q" + System.nanoTime().toString(36) + counter.incrementAndGet().toString(36)

        @Volatile
        private var instance: QueueStore? = null

        fun get(context: Context): QueueStore =
            instance ?: synchronized(this) {
                instance ?: QueueStore(context).also { instance = it }
            }
    }
}
