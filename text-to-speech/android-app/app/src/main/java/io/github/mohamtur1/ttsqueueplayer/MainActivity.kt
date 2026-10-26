package io.github.mohamtur1.ttsqueueplayer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import io.github.mohamtur1.ttsqueueplayer.core.Chunk
import io.github.mohamtur1.ttsqueueplayer.core.QueueRules
import io.github.mohamtur1.ttsqueueplayer.core.SampleText
import io.github.mohamtur1.ttsqueueplayer.core.SpeechEstimate
import io.github.mohamtur1.ttsqueueplayer.core.TextChunker
import io.github.mohamtur1.ttsqueueplayer.model.ItemSource
import io.github.mohamtur1.ttsqueueplayer.model.QueueItem
import io.github.mohamtur1.ttsqueueplayer.player.PlaybackService
import io.github.mohamtur1.ttsqueueplayer.player.PlaybackSnapshot
import io.github.mohamtur1.ttsqueueplayer.player.PlayerBus
import io.github.mohamtur1.ttsqueueplayer.store.QueueStore
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The single screen of the app — everything from the original web page
 * (paste box, sample, format, voice/language, speed/pitch, speak/pause/stop,
 * progress bar, follow-along highlighting) plus the new 10-item queue with
 * files and pastes, and Next/Previous-document and ±5s transport controls.
 *
 * Playback itself lives in PlaybackService; this screen only sends it
 * commands and renders the snapshots it publishes. Closing this screen
 * therefore never stops the reading.
 */
class MainActivity : Activity() {

    private lateinit var store: QueueStore

    private lateinit var etPaste: EditText
    private lateinit var tvQueueCount: TextView
    private lateinit var tvQueueEmpty: TextView
    private lateinit var queueContainer: LinearLayout
    private lateinit var tvNowPlaying: TextView
    private lateinit var pbProgress: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var btnPlayPause: Button
    private lateinit var spLanguage: Spinner
    private lateinit var spVoice: Spinner
    private lateinit var tvRate: TextView
    private lateinit var tvPitch: TextView
    private lateinit var sbRate: SeekBar
    private lateinit var sbPitch: SeekBar
    private lateinit var svReading: ScrollView
    private lateinit var tvReading: TextView

    // Voice catalogue (a short-lived TTS instance used only to list voices).
    private var voiceTts: TextToSpeech? = null
    private var allVoices: List<Voice> = emptyList()
    private var voiceOptions: List<Pair<String, String>> = listOf("" to "Default voice")

    private val languages = listOf(
        "en-US" to "English (US)",
        "en-GB" to "English (UK)"
    )

    // Reading-pane state
    private var displayedItemId: String? = null
    private val uiChunksCache = mutableMapOf<String, List<Chunk>>()
    private var lastQueueRenderKey: String = ""

    private val busListener: (PlaybackSnapshot) -> Unit = { snapshot ->
        runOnUiThread { applySnapshot(snapshot) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = QueueStore.get(this)

        bindViews()
        setupAddButtons()
        setupTransportButtons()
        setupSettingsControls()
        initVoiceCatalogue()
        requestNotificationPermissionIfNeeded()

        PlayerBus.addListener(busListener)
        rebuildQueueRows(force = true)
        applySnapshot(PlayerBus.snapshot)
        showStoredPositionIfPlayerIdle()

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        rebuildQueueRows(force = true)
        applySnapshot(PlayerBus.snapshot)
        showStoredPositionIfPlayerIdle()
    }

    override fun onDestroy() {
        PlayerBus.removeListener(busListener)
        runCatching { voiceTts?.shutdown() }
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // View setup
    // ---------------------------------------------------------------------

    private fun bindViews() {
        etPaste = findViewById(R.id.etPaste)
        tvQueueCount = findViewById(R.id.tvQueueCount)
        tvQueueEmpty = findViewById(R.id.tvQueueEmpty)
        queueContainer = findViewById(R.id.queueContainer)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        pbProgress = findViewById(R.id.pbProgress)
        tvProgress = findViewById(R.id.tvProgress)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        spLanguage = findViewById(R.id.spLanguage)
        spVoice = findViewById(R.id.spVoice)
        tvRate = findViewById(R.id.tvRate)
        tvPitch = findViewById(R.id.tvPitch)
        sbRate = findViewById(R.id.sbRate)
        sbPitch = findViewById(R.id.sbPitch)
        svReading = findViewById(R.id.svReading)
        tvReading = findViewById(R.id.tvReading)
    }

    private fun setupAddButtons() {
        findViewById<Button>(R.id.btnPasteClipboard).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData? = clipboard.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()
            } else null
            if (text.isNullOrBlank()) {
                toast("Clipboard is empty — copy some text first.")
            } else {
                etPaste.setText(TextChunker.formatText(text))
                etPaste.setSelection(etPaste.text.length)
            }
        }

        findViewById<Button>(R.id.btnSample).setOnClickListener {
            etPaste.setText(TextChunker.formatText(SampleText.TEXT))
            etPaste.setSelection(etPaste.text.length)
        }

        findViewById<Button>(R.id.btnFormat).setOnClickListener {
            etPaste.setText(TextChunker.formatText(etPaste.text.toString()))
            etPaste.setSelection(etPaste.text.length)
        }

        findViewById<Button>(R.id.btnAddPaste).setOnClickListener {
            val text = TextChunker.formatText(etPaste.text.toString())
            when {
                text.isBlank() -> toast("Paste or type some text first.")
                !QueueRules.canAdd(store.size) ->
                    toast("Queue is full (${QueueRules.MAX_ITEMS}/${QueueRules.MAX_ITEMS}). Remove an item first.")
                else -> {
                    val item = store.addPaste(text, ItemSource.PASTE)
                    if (item != null) {
                        etPaste.setText("")
                        toast("Added to queue (${store.size}/${QueueRules.MAX_ITEMS}): ${item.title}")
                        afterQueueChanged()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnOpenFiles).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_OPEN_FILES)
        }
    }

    private fun setupTransportButtons() {
        btnPlayPause.setOnClickListener {
            if (store.size == 0) {
                toast("Add a document or paste text first.")
                return@setOnClickListener
            }
            val snap = PlayerBus.snapshot
            if (snap.serviceAlive && snap.isPlaying) {
                PlaybackService.send(this, PlaybackService.ACTION_PAUSE)
            } else {
                PlaybackService.send(this, PlaybackService.ACTION_PLAY)
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            if (PlayerBus.snapshot.serviceAlive) {
                PlaybackService.send(this, PlaybackService.ACTION_STOP)
            }
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (store.size > 0) PlaybackService.send(this, PlaybackService.ACTION_NEXT)
        }
        findViewById<Button>(R.id.btnPrevious).setOnClickListener {
            if (store.size > 0) PlaybackService.send(this, PlaybackService.ACTION_PREVIOUS)
        }
        findViewById<Button>(R.id.btnRewind).setOnClickListener {
            if (store.size > 0) PlaybackService.send(this, PlaybackService.ACTION_REWIND)
        }
        findViewById<Button>(R.id.btnForward).setOnClickListener {
            if (store.size > 0) PlaybackService.send(this, PlaybackService.ACTION_FORWARD)
        }
    }

    private fun setupSettingsControls() {
        // Language spinner
        spLanguage.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, languages.map { it.second }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val langIndex = languages.indexOfFirst { it.first == store.languageTag }.coerceAtLeast(0)
        spLanguage.setSelection(langIndex)
        spLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val tag = languages[position].first
                if (tag != store.languageTag) {
                    store.languageTag = tag
                    store.voiceName = "" // voices differ per language; back to default
                    refreshVoiceSpinner()
                    PlaybackService.instance?.onSettingsChanged()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Voice spinner (populated once the catalogue TTS is ready)
        refreshVoiceSpinner()
        spVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val name = voiceOptions.getOrNull(position)?.first ?: ""
                if (name != store.voiceName) {
                    store.voiceName = name
                    PlaybackService.instance?.onSettingsChanged()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Speed: seekbar 0..25  ->  0.5 .. 3.0 step 0.1 (same range as the HTML)
        sbRate.progress = ((store.rate - 0.5f) / 0.1f).roundToInt().coerceIn(0, 25)
        updateRateLabel()
        sbRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                store.rate = 0.5f + progress * 0.1f
                updateRateLabel()
                if (fromUser) PlaybackService.instance?.onSettingsChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        // Pitch: seekbar 0..14  ->  0.8 .. 1.5 step 0.05 (same range as the HTML)
        sbPitch.progress = ((store.pitch - 0.8f) / 0.05f).roundToInt().coerceIn(0, 14)
        updatePitchLabel()
        sbPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                store.pitch = 0.8f + progress * 0.05f
                updatePitchLabel()
                if (fromUser) PlaybackService.instance?.onSettingsChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun updateRateLabel() {
        tvRate.text = String.format(Locale.US, "Speed: %.1f", store.rate)
    }

    private fun updatePitchLabel() {
        tvPitch.text = String.format(Locale.US, "Pitch: %.2f", store.pitch)
    }

    // ---------------------------------------------------------------------
    // Voices
    // ---------------------------------------------------------------------

    private fun initVoiceCatalogue() {
        voiceTts = TextToSpeech(applicationContext) { status ->
            runOnUiThread {
                if (status == TextToSpeech.SUCCESS) {
                    allVoices = (voiceTts?.voices ?: emptySet()).toList()
                        .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
                    refreshVoiceSpinner()
                }
            }
        }
    }

    private fun refreshVoiceSpinner() {
        val locale = Locale.forLanguageTag(store.languageTag)
        var filtered = allVoices.filter { voice ->
            voice.locale.language == locale.language &&
                (locale.country.isEmpty() || voice.locale.country.isEmpty() ||
                    voice.locale.country.equals(locale.country, ignoreCase = true))
        }
        if (filtered.isEmpty()) {
            filtered = allVoices.filter { it.locale.language == locale.language }
        }
        voiceOptions = listOf("" to "Default voice for ${languages.first { it.first == store.languageTag }.second}") +
            filtered.map { it.name to "${it.name} (${it.locale.toLanguageTag()})" }

        spVoice.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, voiceOptions.map { it.second }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val selected = voiceOptions.indexOfFirst { it.first == store.voiceName }
        spVoice.setSelection(if (selected >= 0) selected else 0)
    }

    // ---------------------------------------------------------------------
    // Files & share intent
    // ---------------------------------------------------------------------

    @Deprecated("Framework Activity has no Activity-Result API; this is the no-androidx way.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_FILES || resultCode != RESULT_OK || data == null) return

        val uris = mutableListOf<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i)?.uri?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return

        Thread {
            var added = 0
            var skippedFull = 0
            var skippedEmpty = 0
            for (uri in uris) {
                if (!QueueRules.canAdd(store.size)) {
                    skippedFull++
                    continue
                }
                val rawText = readTextFromUri(uri)
                val text = rawText?.replace("\r\n", "\n")?.trim().orEmpty()
                if (text.isBlank()) {
                    skippedEmpty++
                    continue
                }
                val name = queryDisplayName(uri)
                if (store.addFile(name, text) != null) added++ else skippedFull++
            }
            runOnUiThread {
                when {
                    added > 0 && skippedFull == 0 && skippedEmpty == 0 ->
                        toast("Added $added file(s) to queue (${store.size}/${QueueRules.MAX_ITEMS}).")
                    added > 0 ->
                        toast("Added $added file(s). Skipped: $skippedFull (queue full), $skippedEmpty (empty).")
                    skippedFull > 0 ->
                        toast("Queue is full (${QueueRules.MAX_ITEMS}/${QueueRules.MAX_ITEMS}). Nothing added.")
                    else ->
                        toast("No readable text found in the selected file(s).")
                }
                if (added > 0) afterQueueChanged()
            }
        }.start()
    }

    private fun readTextFromUri(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val text = reader.readText()
            if (text.length > MAX_FILE_CHARS) text.substring(0, MAX_FILE_CHARS) else text
        }
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
    }

    /** Share-sheet entry point: other apps can send text straight into the queue. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
        if (shared.isNullOrBlank()) return
        intent.action = null // don't re-add on rotation/resume
        when {
            !QueueRules.canAdd(store.size) ->
                toast("Queue is full (${QueueRules.MAX_ITEMS}/${QueueRules.MAX_ITEMS}). Shared text not added.")
            else -> {
                val item = store.addPaste(TextChunker.formatText(shared), ItemSource.SHARE)
                if (item != null) {
                    toast("Shared text added to queue: ${item.title}")
                    afterQueueChanged()
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Queue UI
    // ---------------------------------------------------------------------

    private fun afterQueueChanged() {
        rebuildQueueRows(force = true)
        PlaybackService.instance?.onExternalQueueChanged()
        applySnapshot(PlayerBus.snapshot)
        showStoredPositionIfPlayerIdle()
    }

    private fun rebuildQueueRows(force: Boolean = false) {
        val queue = store.queue
        val snap = PlayerBus.snapshot
        val renderKey = queue.joinToString("|") { it.id + ":" + it.title } +
            "#" + snap.itemId + "#" + snap.isPlaying + "#" + snap.isPaused
        if (!force && renderKey == lastQueueRenderKey) return
        lastQueueRenderKey = renderKey

        tvQueueCount.text = "Queue: ${queue.size}/${QueueRules.MAX_ITEMS}"
        tvQueueEmpty.visibility = if (queue.isEmpty()) View.VISIBLE else View.GONE
        queueContainer.removeAllViews()

        val currentId = if (snap.serviceAlive) snap.itemId else store.positionItemId
        queue.forEachIndexed { index, item ->
            queueContainer.addView(makeQueueRow(index, item, item.id == currentId, snap))
        }
    }

    private fun makeQueueRow(index: Int, item: QueueItem, isCurrent: Boolean, snap: PlaybackSnapshot): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(4), dp(6))
            if (isCurrent) setBackgroundColor(Color.rgb(232, 240, 254))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val marker = when {
            isCurrent && snap.serviceAlive && snap.isPlaying -> "▶ "
            isCurrent && snap.serviceAlive && snap.isPaused -> "⏸ "
            else -> ""
        }
        val sourceLabel = when (item.source) {
            ItemSource.FILE -> "File"
            ItemSource.PASTE -> "Pasted"
            ItemSource.SHARE -> "Shared"
        }
        val minutes = (SpeechEstimate.totalSeconds(uiChunksFor(item), store.rate) / 60.0)
        val minutesText = if (minutes < 1.0) "<1 min" else "~${minutes.roundToInt()} min"

        val title = TextView(this).apply {
            text = "$marker${index + 1}. ${item.title}\n$sourceLabel • ${item.wordCount} words • $minutesText"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(title)

        fun smallButton(label: String, description: String, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                contentDescription = description
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setOnClickListener { onClick() }
            }

        row.addView(smallButton("↑", "Move up") {
            if (store.move(item.id, -1)) afterQueueChanged()
        })
        row.addView(smallButton("↓", "Move down") {
            if (store.move(item.id, +1)) afterQueueChanged()
        })
        row.addView(smallButton("✎", "Rename") { showRenameDialog(item) })
        row.addView(smallButton("✕", "Remove") { showRemoveDialog(item) })

        row.setOnClickListener {
            PlaybackService.send(this, PlaybackService.ACTION_PLAY_ITEM, item.id)
        }
        return row
    }

    private fun showRenameDialog(item: QueueItem) {
        val input = EditText(this).apply {
            setText(item.title)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                if (store.rename(item.id, input.text.toString())) afterQueueChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveDialog(item: QueueItem) {
        AlertDialog.Builder(this)
            .setTitle("Remove from queue?")
            .setMessage("“${item.title}” will be removed from the queue.")
            .setPositiveButton("Remove") { _, _ ->
                if (store.remove(item.id)) {
                    if (displayedItemId == item.id) displayedItemId = null
                    afterQueueChanged()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------------
    // Player UI updates
    // ---------------------------------------------------------------------

    private fun applySnapshot(snap: PlaybackSnapshot) {
        rebuildQueueRows()

        val queue = store.queue
        val item = store.itemById(snap.itemId)
            ?: store.itemById(store.positionItemId)
            ?: queue.firstOrNull()

        when {
            queue.isEmpty() -> {
                tvNowPlaying.text = "Nothing in the queue yet"
                pbProgress.progress = 0
                tvProgress.text = ""
            }
            item != null -> {
                val index = store.indexOfId(item.id)
                tvNowPlaying.text = item.title
                if (snap.serviceAlive && snap.itemId == item.id) {
                    pbProgress.progress = snap.progressPercent
                    tvProgress.text = "Document ${index + 1} of ${queue.size} • ${snap.progressPercent}%"
                } else {
                    pbProgress.progress = 0
                    tvProgress.text = "Document ${index + 1} of ${queue.size}"
                }
            }
        }

        btnPlayPause.text = when {
            snap.serviceAlive && snap.isPlaying -> "⏸ Pause"
            snap.serviceAlive && snap.isPaused -> "▶ Resume"
            else -> "▶ Speak"
        }

        if (snap.serviceAlive && item != null && snap.itemId == item.id) {
            updateReadingPane(item, snap.chunkIndex, autoScroll = snap.isPlaying)
        }
    }

    /** When the service isn't running, still show where you left off (persisted position). */
    private fun showStoredPositionIfPlayerIdle() {
        if (PlayerBus.snapshot.serviceAlive) return
        val item = store.itemById(store.positionItemId) ?: store.queue.firstOrNull()
        if (item == null) {
            tvReading.text = "Add a document or paste text, then press Speak."
            displayedItemId = null
            return
        }
        val chunk = if (item.id == store.positionItemId) store.positionChunk else 0
        updateReadingPane(item, chunk, autoScroll = false)
    }

    private fun updateReadingPane(item: QueueItem, chunkIndex: Int, autoScroll: Boolean) {
        val chunks = uiChunksFor(item)
        val chunk = chunks.getOrNull(chunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0)))

        if (chunk == null) {
            if (displayedItemId != item.id) {
                tvReading.text = item.text
                displayedItemId = item.id
            }
            return
        }

        val spannable = SpannableString(item.text)
        spannable.setSpan(
            BackgroundColorSpan(Color.rgb(255, 249, 196)),
            chunk.startOffset.coerceIn(0, item.text.length),
            chunk.endOffset.coerceIn(0, item.text.length),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvReading.text = spannable
        displayedItemId = item.id

        if (autoScroll) {
            tvReading.post {
                val layout = tvReading.layout ?: return@post
                val line = layout.getLineForOffset(chunk.startOffset.coerceIn(0, item.text.length))
                val y = layout.getLineTop(line) - dp(90)
                svReading.smoothScrollTo(0, y.coerceAtLeast(0))
            }
        }
    }

    private fun uiChunksFor(item: QueueItem): List<Chunk> =
        uiChunksCache.getOrPut(item.id) { TextChunker.chunk(item.text) }

    // ---------------------------------------------------------------------
    // Misc
    // ---------------------------------------------------------------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQUEST_OPEN_FILES = 1001
        private const val REQUEST_NOTIFICATIONS = 1002
        private const val MAX_FILE_CHARS = 2_000_000
    }
}
