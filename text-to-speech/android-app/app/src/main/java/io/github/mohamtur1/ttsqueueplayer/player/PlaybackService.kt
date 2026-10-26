package io.github.mohamtur1.ttsqueueplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.mohamtur1.ttsqueueplayer.MainActivity
import io.github.mohamtur1.ttsqueueplayer.R
import io.github.mohamtur1.ttsqueueplayer.core.Chunk
import io.github.mohamtur1.ttsqueueplayer.core.SpeechEstimate
import io.github.mohamtur1.ttsqueueplayer.core.TextChunker
import io.github.mohamtur1.ttsqueueplayer.model.QueueItem
import io.github.mohamtur1.ttsqueueplayer.store.QueueStore
import java.util.Locale

/**
 * The player itself. Lives in a foreground service so reading continues while
 * the user is in other apps or the screen is off. Owns:
 *  - the Android TextToSpeech engine (on-device voices, offline),
 *  - a MediaSession -> notification-bar + lock-screen + headset controls:
 *        ⏮ Previous document | ⏪ −5s | ⏯ Play/Pause | ⏩ +5s | ⏭ Next document
 *  - the queue position (item + chunk), persisted on every chunk so closing
 *    the app never loses your place,
 *  - audio-focus handling (pause for calls/other media, resume afterwards
 *    when the interruption was temporary), and pause-on-headphones-unplug.
 *
 * TTS has no true "pause mid-sentence" or timestamp seek, so Pause stops the
 * current chunk and Resume re-speaks it from its start (chunks are sentence-
 * sized, so this loses at most a few words), and ±5s jumps across chunks by
 * estimated spoken duration (see SpeechEstimate).
 */
class PlaybackService : Service() {

    private lateinit var store: QueueStore
    private lateinit var tts: TextToSpeech
    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    private var ttsReady = false
    private var pendingPlay = false
    private var isForeground = false

    private var isPlaying = false
    private var isPaused = false
    private var isEnded = false
    private var resumeOnFocusGain = false

    private var currentItemId: String? = null
    private var chunkIndex = 0
    private var lastItemIndex = 0

    private val chunksCache = mutableMapOf<String, List<Chunk>>()

    @Volatile
    private var currentUtteranceId: String? = null

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = QueueStore.get(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        initMediaSession()
        loadPersistedPosition()

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TTSQueuePlayer:playing")
            .apply { setReferenceCounted(false) }

        registerNoisyReceiver()
        startForegroundNow() // required promptly when started via startForegroundService
        initTts()
        publish()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pausePlayback(abandonFocus = true)
            ACTION_TOGGLE -> if (isPlaying) pausePlayback(abandonFocus = true) else play()
            ACTION_STOP -> stopAndExit()
            ACTION_NEXT -> next()
            ACTION_PREVIOUS -> previous()
            ACTION_REWIND -> seekBy(-SpeechEstimate.SEEK_SECONDS)
            ACTION_FORWARD -> seekBy(SpeechEstimate.SEEK_SECONDS)
            ACTION_PLAY_ITEM -> intent.getStringExtra(EXTRA_ITEM_ID)?.let { playItem(it) }
            else -> publish()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        currentUtteranceId = null
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        runCatching { unregisterReceiver(noisyReceiver) }
        runCatching { mediaSession.release() }
        abandonFocus()
        releaseWakeLock()
        PlayerBus.publish(
            PlaybackSnapshot(
                serviceAlive = false,
                isEnded = isEnded,
                itemId = currentItemId,
                itemIndex = store.indexOfId(currentItemId),
                itemCount = store.size,
                chunkIndex = chunkIndex,
                chunkCount = currentChunks().size
            )
        )
        instance = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Transport controls (also called from MediaSession + notification)
    // ---------------------------------------------------------------------

    private fun play() {
        if (store.size == 0) {
            publish()
            stopAndExit()
            return
        }
        ensureCurrentItem()
        isEnded = false
        isPaused = false
        resumeOnFocusGain = false
        requestFocus()
        if (!ttsReady) {
            pendingPlay = true
            isPlaying = true
            publish(); refreshNotificationAndSession()
            return
        }
        speakCurrent()
    }

    private fun playItem(itemId: String) {
        if (store.itemById(itemId) == null) return
        currentItemId = itemId
        chunkIndex = 0
        persistPosition()
        play()
    }

    private fun pausePlayback(abandonFocus: Boolean) {
        handler.removeCallbacksAndMessages(null)
        currentUtteranceId = null
        runCatching { if (ttsReady) tts.stop() }
        pendingPlay = false
        isPlaying = false
        isPaused = true
        if (abandonFocus) abandonFocus()
        releaseWakeLock()
        persistPosition()
        publish(); refreshNotificationAndSession()
    }

    /** Stop button: back to the start of the current document, player closed (like the HTML Stop). */
    private fun stopAndExit() {
        handler.removeCallbacksAndMessages(null)
        currentUtteranceId = null
        runCatching { if (ttsReady) tts.stop() }
        isPlaying = false
        isPaused = false
        pendingPlay = false
        chunkIndex = 0
        persistPosition()
        publish()
        abandonFocus()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    /** Whole queue finished: rewind to the first document ready for a replay, then close. */
    private fun finishQueue() {
        handler.removeCallbacksAndMessages(null)
        currentUtteranceId = null
        runCatching { if (ttsReady) tts.stop() }
        isPlaying = false
        isPaused = false
        isEnded = true
        pendingPlay = false
        currentItemId = store.queue.firstOrNull()?.id
        chunkIndex = 0
        lastItemIndex = 0
        persistPosition()
        abandonFocus()
        releaseWakeLock()
        publish(); refreshNotificationAndSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    /** ⏭ Next document. On the last document the queue ends (no looping). */
    private fun next() {
        val q = store.queue
        if (q.isEmpty()) return
        val index = store.indexOfId(currentItemId)
        if (index < 0) {
            currentItemId = q.first().id; chunkIndex = 0
        } else if (index >= q.size - 1) {
            if (isPlaying || isPaused) finishQueue()
            return
        } else {
            currentItemId = q[index + 1].id; chunkIndex = 0
        }
        persistPosition()
        if (isPlaying) {
            currentUtteranceId = null
            handler.removeCallbacksAndMessages(null)
            runCatching { if (ttsReady) tts.stop() }
            speakCurrent()
        } else {
            publish(); refreshNotificationAndSession()
        }
    }

    /**
     * ⏮ Previous document, music-app style: if we are more than ~3 seconds
     * into the current document, restart IT; otherwise go to the one before.
     */
    private fun previous() {
        val q = store.queue
        if (q.isEmpty()) return
        val chunks = currentChunks()
        val elapsed = SpeechEstimate.elapsedSecondsBefore(chunks, chunkIndex, store.rate)
        val index = store.indexOfId(currentItemId)
        when {
            index < 0 -> { currentItemId = q.first().id; chunkIndex = 0 }
            chunkIndex > 0 && elapsed > 3.0 -> chunkIndex = 0
            index > 0 -> { currentItemId = q[index - 1].id; chunkIndex = 0 }
            else -> chunkIndex = 0
        }
        persistPosition()
        if (isPlaying) {
            currentUtteranceId = null
            handler.removeCallbacksAndMessages(null)
            runCatching { if (ttsReady) tts.stop() }
            speakCurrent()
        } else {
            publish(); refreshNotificationAndSession()
        }
    }

    /** ⏪/⏩ jump about five seconds of speech backwards/forwards within the document. */
    private fun seekBy(seconds: Double) {
        val chunks = currentChunks()
        if (chunks.isEmpty()) return
        chunkIndex = if (seconds < 0) {
            SpeechEstimate.rewindTargetIndex(chunks, chunkIndex, store.rate, -seconds)
        } else {
            SpeechEstimate.forwardTargetIndex(chunks, chunkIndex, store.rate, seconds)
        }
        persistPosition()
        if (isPlaying) {
            currentUtteranceId = null
            handler.removeCallbacksAndMessages(null)
            runCatching { if (ttsReady) tts.stop() }
            speakCurrent()
        } else {
            publish(); refreshNotificationAndSession()
        }
    }

    // ---------------------------------------------------------------------
    // Speaking engine
    // ---------------------------------------------------------------------

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            handler.post {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    runCatching {
                        tts.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                    }
                    applySpeechSettings()
                    if (pendingPlay) {
                        pendingPlay = false
                        speakCurrent()
                    }
                }
                publish()
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                handler.post { if (utteranceId == currentUtteranceId) publish() }
            }

            override fun onDone(utteranceId: String?) {
                handler.post { onChunkFinished(utteranceId) }
            }

            @Deprecated("Deprecated in Android, still the reliably-called overload")
            override fun onError(utteranceId: String?) {
                handler.post {
                    // Errors caused by our own stop()/pause have a stale id and are ignored.
                    if (utteranceId == currentUtteranceId) onChunkFinished(utteranceId)
                }
            }
        })
    }

    private fun speakCurrent() {
        val item = currentItem() ?: run { finishQueue(); return }
        val chunks = chunksFor(item)
        if (chunks.isEmpty() || chunkIndex >= chunks.size) {
            onItemFinished()
            return
        }
        applySpeechSettings()
        acquireWakeLock()

        val chunk = chunks[chunkIndex]
        val utteranceId = "${item.id}#$chunkIndex#${System.nanoTime()}"
        currentUtteranceId = utteranceId
        isPlaying = true
        isPaused = false

        val result = runCatching {
            tts.speak(chunk.text.replace('\n', ' '), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        persistPosition()
        publish(); refreshNotificationAndSession()

        if (result == TextToSpeech.ERROR) {
            handler.postDelayed({ onChunkFinished(utteranceId) }, 150)
        }
    }

    private fun onChunkFinished(utteranceId: String?) {
        if (utteranceId == null || utteranceId != currentUtteranceId || !isPlaying) return
        currentUtteranceId = null
        val chunks = currentChunks()
        val finishedChunk = chunks.getOrNull(chunkIndex)
        chunkIndex++
        persistPosition()
        if (chunkIndex >= chunks.size) {
            onItemFinished()
            return
        }
        val delay = finishedChunk?.let { TextChunker.speedAdjustedPauseMs(it, store.rate) } ?: 100L
        publish()
        if (delay <= 0) {
            speakCurrent()
        } else {
            handler.postDelayed({ if (isPlaying && !isPaused) speakCurrent() }, delay)
        }
    }

    private fun onItemFinished() {
        val q = store.queue
        val index = store.indexOfId(currentItemId)
        if (q.isEmpty() || index < 0 || index >= q.size - 1) {
            finishQueue()
        } else {
            currentItemId = q[index + 1].id
            chunkIndex = 0
            persistPosition()
            speakCurrent() // auto-advance to the next document
        }
    }

    private fun applySpeechSettings() {
        if (!ttsReady) return
        runCatching {
            val locale = Locale.forLanguageTag(store.languageTag)
            val chosen = store.voiceName
            val voice = if (chosen.isNotEmpty()) {
                (tts.voices ?: emptySet()).firstOrNull { it.name == chosen }
            } else null
            if (voice != null) tts.voice = voice else tts.language = locale
            tts.setSpeechRate(store.rate)
            tts.setPitch(store.pitch)
        }
    }

    /** Called by the UI when voice/rate/pitch/language change; applies from the next chunk. */
    fun onSettingsChanged() {
        handler.post {
            applySpeechSettings()
            publish(); refreshNotificationAndSession()
        }
    }

    /**
     * Called by the UI after any queue edit (add file/paste, remove, rename,
     * reorder) — including while playing. Adding never interrupts playback;
     * removing the item being read moves to the next item at the same slot.
     */
    fun onExternalQueueChanged() {
        handler.post {
            chunksCache.clear()
            val q = store.queue
            when {
                q.isEmpty() -> {
                    handler.removeCallbacksAndMessages(null)
                    currentUtteranceId = null
                    runCatching { if (ttsReady) tts.stop() }
                    isPlaying = false; isPaused = false; pendingPlay = false
                    currentItemId = null; chunkIndex = 0
                    persistPosition()
                }
                store.itemById(currentItemId) != null -> {
                    val size = currentChunks().size
                    if (chunkIndex > size) chunkIndex = size
                    persistPosition()
                }
                else -> {
                    val newIndex = lastItemIndex.coerceIn(0, q.size - 1)
                    currentItemId = q[newIndex].id
                    chunkIndex = 0
                    persistPosition()
                    if (isPlaying) {
                        currentUtteranceId = null
                        handler.removeCallbacksAndMessages(null)
                        runCatching { if (ttsReady) tts.stop() }
                        speakCurrent()
                        return@post
                    }
                }
            }
            publish(); refreshNotificationAndSession()
        }
    }

    // ---------------------------------------------------------------------
    // Position / chunks helpers
    // ---------------------------------------------------------------------

    private fun loadPersistedPosition() {
        val savedId = store.positionItemId
        currentItemId = when {
            store.itemById(savedId) != null -> savedId
            else -> store.queue.firstOrNull()?.id
        }
        chunkIndex = if (currentItemId == savedId) store.positionChunk else 0
    }

    private fun ensureCurrentItem() {
        if (store.itemById(currentItemId) == null) {
            currentItemId = store.queue.firstOrNull()?.id
            chunkIndex = 0
        }
        val size = currentChunks().size
        if (chunkIndex < 0 || chunkIndex > size) chunkIndex = 0
        if (size > 0 && chunkIndex == size) chunkIndex = 0 // was left at end: replay document
    }

    private fun persistPosition() = store.savePosition(currentItemId, chunkIndex)

    private fun currentItem(): QueueItem? {
        ensureCurrentItem()
        return store.itemById(currentItemId)
    }

    private fun chunksFor(item: QueueItem): List<Chunk> =
        chunksCache.getOrPut(item.id) { TextChunker.chunk(item.text) }

    private fun currentChunks(): List<Chunk> =
        store.itemById(currentItemId)?.let { chunksFor(it) } ?: emptyList()

    private fun publish() {
        val index = store.indexOfId(currentItemId)
        if (index >= 0) lastItemIndex = index
        PlayerBus.publish(
            PlaybackSnapshot(
                serviceAlive = true,
                ttsReady = ttsReady,
                isPlaying = isPlaying,
                isPaused = isPaused,
                isEnded = isEnded,
                itemId = currentItemId,
                itemIndex = index,
                itemCount = store.size,
                chunkIndex = chunkIndex,
                chunkCount = currentChunks().size
            )
        )
    }

    // ---------------------------------------------------------------------
    // MediaSession + notification (the notification-bar player)
    // ---------------------------------------------------------------------

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "TTSQueuePlayer")
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = play()
            override fun onPause() = pausePlayback(abandonFocus = true)
            override fun onStop() = stopAndExit()
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = previous()
            override fun onRewind() = seekBy(-SpeechEstimate.SEEK_SECONDS)
            override fun onFastForward() = seekBy(SpeechEstimate.SEEK_SECONDS)
        }, handler)
        mediaSession.isActive = true
    }

    private fun refreshNotificationAndSession() {
        updateMediaSession()
        if (isForeground) {
            runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification()) }
        } else {
            startForegroundNow()
        }
    }

    private fun updateMediaSession() {
        val item = store.itemById(currentItemId)
        val index = store.indexOfId(currentItemId)
        val chunks = currentChunks()
        val durationMs = (SpeechEstimate.totalSeconds(chunks, store.rate) * 1000).toLong()
        val positionMs = (SpeechEstimate.elapsedSecondsBefore(chunks, chunkIndex, store.rate) * 1000).toLong()

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, item?.title ?: getString(R.string.app_name))
                .putString(
                    MediaMetadata.METADATA_KEY_ARTIST,
                    if (item != null && index >= 0) "Document ${index + 1} of ${store.size}" else "Queue empty"
                )
                .putString(MediaMetadata.METADATA_KEY_ALBUM, getString(R.string.app_name))
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build()
        )

        val state = when {
            isPlaying -> PlaybackState.STATE_PLAYING
            isPaused -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD
                )
                .setState(state, positionMs, if (isPlaying) 1.0f else 0.0f, SystemClock.elapsedRealtime())
                .build()
        )
    }

    private fun buildNotification(): Notification {
        val item = store.itemById(currentItemId)
        val index = store.indexOfId(currentItemId)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionIntent(action: String, requestCode: Int): PendingIntent =
            PendingIntent.getForegroundService(
                this, requestCode,
                Intent(this, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tts)
            .setContentTitle(item?.title ?: getString(R.string.app_name))
            .setContentText(
                if (item != null && index >= 0) "Document ${index + 1} of ${store.size}"
                else "Queue empty"
            )
            .setContentIntent(openApp)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setDeleteIntent(actionIntent(ACTION_STOP, 9))
            .addAction(android.R.drawable.ic_media_previous, "Previous document", actionIntent(ACTION_PREVIOUS, 1))
            .addAction(android.R.drawable.ic_media_rew, "Rewind 5 seconds", actionIntent(ACTION_REWIND, 2))
            .addAction(playPauseIcon, playPauseLabel, actionIntent(ACTION_TOGGLE, 3))
            .addAction(android.R.drawable.ic_media_ff, "Forward 5 seconds", actionIntent(ACTION_FORWARD, 4))
            .addAction(android.R.drawable.ic_media_next, "Next document", actionIntent(ACTION_NEXT, 5))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 2, 4)
            )
            .build()
    }

    private fun startForegroundNow() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Reading controls while text is being read aloud"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // ---------------------------------------------------------------------
    // Audio focus / noisy / wakelock
    // ---------------------------------------------------------------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        handler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnFocusGain = false
                    if (isPlaying) pausePlayback(abandonFocus = false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (isPlaying) {
                        resumeOnFocusGain = true
                        pausePlayback(abandonFocus = false)
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (resumeOnFocusGain && isPaused) {
                        resumeOnFocusGain = false
                        play()
                    }
                }
            }
        }
    }

    private fun requestFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .setWillPauseWhenDucked(true)
            .build()
        runCatching { audioManager.requestAudioFocus(focusRequest!!) }
    }

    private fun abandonFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isPlaying) {
                resumeOnFocusGain = false
                pausePlayback(abandonFocus = true)
            }
        }
    }

    private fun registerNoisyReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(noisyReceiver, filter)
        }
    }

    private fun acquireWakeLock() {
        runCatching { wakeLock?.takeIf { !it.isHeld }?.acquire(4 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    }

    // ---------------------------------------------------------------------

    companion object {
        const val ACTION_PLAY = "io.github.mohamtur1.ttsqueueplayer.action.PLAY"
        const val ACTION_PAUSE = "io.github.mohamtur1.ttsqueueplayer.action.PAUSE"
        const val ACTION_TOGGLE = "io.github.mohamtur1.ttsqueueplayer.action.TOGGLE"
        const val ACTION_STOP = "io.github.mohamtur1.ttsqueueplayer.action.STOP"
        const val ACTION_NEXT = "io.github.mohamtur1.ttsqueueplayer.action.NEXT"
        const val ACTION_PREVIOUS = "io.github.mohamtur1.ttsqueueplayer.action.PREVIOUS"
        const val ACTION_REWIND = "io.github.mohamtur1.ttsqueueplayer.action.REWIND"
        const val ACTION_FORWARD = "io.github.mohamtur1.ttsqueueplayer.action.FORWARD"
        const val ACTION_PLAY_ITEM = "io.github.mohamtur1.ttsqueueplayer.action.PLAY_ITEM"
        const val EXTRA_ITEM_ID = "extra_item_id"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var instance: PlaybackService? = null
            private set

        fun send(context: Context, action: String, itemId: String? = null) {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
            if (itemId != null) intent.putExtra(EXTRA_ITEM_ID, itemId)
            context.startForegroundService(intent)
        }
    }
}
