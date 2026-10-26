package io.github.mohamtur1.ttsqueueplayer.player

/**
 * Immutable view of the player at a moment in time, published by the
 * PlaybackService and consumed by the UI (controls, highlight, progress).
 */
data class PlaybackSnapshot(
    val serviceAlive: Boolean = false,
    val ttsReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isEnded: Boolean = false,
    val itemId: String? = null,
    val itemIndex: Int = -1,
    val itemCount: Int = 0,
    val chunkIndex: Int = 0,
    val chunkCount: Int = 0
) {
    val progressPercent: Int
        get() = if (chunkCount <= 0) 0 else ((chunkIndex.coerceIn(0, chunkCount) * 100) / chunkCount)
}

/**
 * Tiny in-process event bus. Activity and Service live in the same process,
 * so a static listener list is all that is needed (no broadcasts, no IPC).
 * All publishes happen on the main thread.
 */
object PlayerBus {

    @Volatile
    var snapshot: PlaybackSnapshot = PlaybackSnapshot()
        private set

    private val listeners = mutableListOf<(PlaybackSnapshot) -> Unit>()

    fun publish(newSnapshot: PlaybackSnapshot) {
        snapshot = newSnapshot
        val copy: List<(PlaybackSnapshot) -> Unit>
        synchronized(listeners) { copy = listeners.toList() }
        copy.forEach { runCatching { it(newSnapshot) } }
    }

    fun addListener(listener: (PlaybackSnapshot) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: (PlaybackSnapshot) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }
}
