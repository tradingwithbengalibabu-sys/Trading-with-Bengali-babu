package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

data class VoicePlaybackState(
    val activeUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentSec: Int = 0,
    val totalSec: Int = 0,
    val error: String? = null
)

class SignalVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis: Long = 0L

    fun startRecording(): Result<File> = runCatching {
        stopSilently()
        val file = File(context.cacheDir, "signal_voice_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        startedAtMillis = System.currentTimeMillis()
        file
    }

    fun stopRecording(): Result<Pair<File, Int>> = runCatching {
        val activeRecorder = recorder ?: throw IllegalStateException("No active voice recording.")
        val file = outputFile ?: throw IllegalStateException("Voice file missing.")
        try {
            activeRecorder.stop()
        } finally {
            runCatching { activeRecorder.release() }
            recorder = null
        }
        val durationSec = ((System.currentTimeMillis() - startedAtMillis) / 1000L)
            .toInt()
            .coerceAtLeast(1)
        require(file.exists() && file.length() > 0L) { "Recorded voice note is empty." }
        // Disown temp handle so cancelRecording() on dialog close does not delete a finished recording.
        outputFile = null
        file to durationSec
    }

    fun cancelRecording() {
        stopSilently()
        runCatching { outputFile?.delete() }
        outputFile = null
    }

    private fun stopSilently() {
        recorder?.let { rec ->
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        recorder = null
    }
}

class SignalVoicePlayer(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null
    private var downloadJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager by lazy {
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    fun togglePlay(url: String, fallbackDurationSec: Int = 0) {
        if (url.isBlank()) return
        val current = _state.value
        if (current.activeUrl == url && mediaPlayer != null && current.error == null) {
            if (current.isBuffering) return
            val player = mediaPlayer ?: return
            if (player.isPlaying) {
                runCatching { player.pause() }
                abandonAudioFocus()
                tickerJob?.cancel()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                requestAudioFocus()
                runCatching { player.start() }
                startTicker()
                _state.value = _state.value.copy(isPlaying = true, error = null)
            }
            return
        }

        stop()
        _state.value = VoicePlaybackState(
            activeUrl = url,
            isPlaying = false,
            isBuffering = true,
            currentSec = 0,
            totalSec = fallbackDurationSec.coerceAtLeast(0),
            error = null
        )

        val isGsUrl = url.startsWith("gs://", ignoreCase = true)
        if (isGsUrl) {
            downloadAndPlayFirebaseAudio(url, fallbackDurationSec)
        } else {
            preparePlayer(
                activeUrl = url,
                dataSource = url,
                fallbackDurationSec = fallbackDurationSec,
                allowFirebaseFallback = isFirebaseStorageUrl(url)
            )
        }
    }

    private fun isFirebaseStorageUrl(url: String): Boolean {
        return url.startsWith("gs://", ignoreCase = true) ||
            url.contains("firebasestorage.googleapis.com", ignoreCase = true) ||
            url.contains("firebasestorage.app", ignoreCase = true)
    }

    private fun preparePlayer(
        activeUrl: String,
        dataSource: String,
        fallbackDurationSec: Int,
        allowFirebaseFallback: Boolean
    ) {
        releasePlayerOnly()
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                if (_state.value.activeUrl != activeUrl) return@setOnPreparedListener
                val durSec = (mp.duration / 1000).coerceAtLeast(fallbackDurationSec)
                requestAudioFocus()
                mp.start()
                _state.value = _state.value.copy(
                    isPlaying = true,
                    isBuffering = false,
                    totalSec = durSec,
                    error = null
                )
                startTicker()
            }
            setOnCompletionListener {
                tickerJob?.cancel()
                abandonAudioFocus()
                _state.value = _state.value.copy(
                    isPlaying = false,
                    currentSec = _state.value.totalSec
                )
            }
            setOnErrorListener { _, what, extra ->
                tickerJob?.cancel()
                abandonAudioFocus()
                Log.w("SignalVoicePlayer", "MediaPlayer error what=$what extra=$extra for $dataSource")
                if (allowFirebaseFallback && _state.value.activeUrl == activeUrl) {
                    downloadAndPlayFirebaseAudio(activeUrl, fallbackDurationSec)
                } else {
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = "Unable to play voice message (error $what/$extra)."
                    )
                }
                true
            }
        }
        mediaPlayer = player
        runCatching {
            player.setDataSource(dataSource)
            player.prepareAsync()
        }.onFailure { err ->
            Log.w("SignalVoicePlayer", "setDataSource failed for $dataSource: ${err.message}")
            if (allowFirebaseFallback && _state.value.activeUrl == activeUrl) {
                downloadAndPlayFirebaseAudio(activeUrl, fallbackDurationSec)
            } else {
                releasePlayerOnly()
                _state.value = VoicePlaybackState(
                    activeUrl = activeUrl,
                    error = err.localizedMessage ?: "Unable to stream voice message."
                )
            }
        }
    }

    private fun downloadAndPlayFirebaseAudio(url: String, fallbackDurationSec: Int) {
        downloadJob?.cancel()
        releasePlayerOnly()
        _state.value = _state.value.copy(
            activeUrl = url,
            isPlaying = false,
            isBuffering = true,
            error = null
        )
        downloadJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val cacheDir = File(appContext.cacheDir, "signal_voice_cache").apply { mkdirs() }
                    val cacheFile = File(cacheDir, "${url.hashCode().toUInt()}.m4a")
                    if (!cacheFile.exists() || cacheFile.length() == 0L) {
                        Firebase.storage.getReferenceFromUrl(url)
                            .getFile(cacheFile)
                            .await()
                    }
                    require(cacheFile.exists() && cacheFile.length() > 0L) {
                        "Downloaded voice file is empty."
                    }
                    cacheFile.absolutePath
                }
            }
            if (_state.value.activeUrl != url) return@launch
            result.onSuccess { localPath ->
                preparePlayer(
                    activeUrl = url,
                    dataSource = localPath,
                    fallbackDurationSec = fallbackDurationSec,
                    allowFirebaseFallback = false
                )
            }.onFailure { err ->
                val readable = when (err) {
                    is StorageException -> when (err.errorCode) {
                        StorageException.ERROR_NOT_AUTHORIZED ->
                            "Voice download denied by storage.rules."
                        StorageException.ERROR_BUCKET_NOT_FOUND,
                        StorageException.ERROR_PROJECT_NOT_FOUND ->
                            "Firebase Storage bucket is not provisioned."
                        StorageException.ERROR_OBJECT_NOT_FOUND ->
                            "Voice recording was not found in storage."
                        else -> err.localizedMessage ?: "Voice download failed."
                    }
                    else -> err.localizedMessage ?: "Unable to download voice message."
                }
                Log.e("SignalVoicePlayer", "Authenticated voice download failed for $url: $readable", err)
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    error = readable
                )
            }
        }
    }

    fun seekTo(seconds: Int) {
        val mp = mediaPlayer ?: return
        val targetMs = (seconds.coerceAtLeast(0) * 1000).coerceAtMost(mp.duration.coerceAtLeast(0))
        runCatching { mp.seekTo(targetMs) }
        _state.value = _state.value.copy(currentSec = targetMs / 1000)
    }

    fun stop() {
        downloadJob?.cancel()
        downloadJob = null
        tickerJob?.cancel()
        tickerJob = null
        releasePlayerOnly()
        abandonAudioFocus()
        _state.value = VoicePlaybackState()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun releasePlayerOnly() {
        mediaPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
    }

    private fun requestAudioFocus() {
        val mgr = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                    .also { audioFocusRequest = it }
                mgr.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                mgr.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }
    }

    private fun abandonAudioFocus() {
        val mgr = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { mgr.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                mgr.abandonAudioFocus(null)
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                val mp = mediaPlayer ?: break
                val playing = runCatching { mp.isPlaying }.getOrDefault(false)
                if (!playing) break
                val posSec = (runCatching { mp.currentPosition }.getOrDefault(0) / 1000).coerceAtLeast(0)
                val durSec = (runCatching { mp.duration }.getOrDefault(0) / 1000).coerceAtLeast(_state.value.totalSec)
                _state.value = _state.value.copy(currentSec = posSec, totalSec = durSec, isPlaying = true)
                delay(250L)
            }
        }
    }
}
