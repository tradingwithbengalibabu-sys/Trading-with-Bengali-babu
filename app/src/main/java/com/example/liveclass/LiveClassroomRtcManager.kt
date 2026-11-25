package com.example.liveclass

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
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

enum class LiveKitConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class LiveClassroomRtcState(
    val isConfigured: Boolean = LiveKitTokenGenerator.isConfigured(),
    val connectionState: LiveKitConnectionState = LiveKitConnectionState.DISCONNECTED,
    val connectedRoomName: String? = null,
    val canPublishAudio: Boolean = false,
    val isMicEnabled: Boolean = false,
    val isCameraEnabled: Boolean = false,
    val isScreenShareActive: Boolean = false,
    val localCameraTrack: VideoTrack? = null,
    val localScreenShareTrack: VideoTrack? = null,
    val remoteTeacherCameraTrack: VideoTrack? = null,
    val remoteScreenShareTrack: VideoTrack? = null,
    val activeSpeakerNames: List<String> = emptyList(),
    val noticeMessage: String? = null
)

class LiveClassroomRtcManager(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var room: Room? = null
    private var eventJob: Job? = null
    private var lastConnectedAsTeacher: Boolean = false
    private var lastConnectedPublishAudio: Boolean = false

    private var savedAudioMode: Int? = null
    private var savedSpeakerphoneOn: Boolean? = null

    private val _state = MutableStateFlow(
        LiveClassroomRtcState(isConfigured = LiveKitTokenGenerator.isConfigured())
    )
    val state: StateFlow<LiveClassroomRtcState> = _state.asStateFlow()

    fun currentRoom(): Room? = room

    fun initRenderer(renderer: TextureViewRenderer): Boolean {
        val activeRoom = room ?: return false
        return runCatching {
            activeRoom.initVideoRenderer(renderer)
            true
        }.onFailure { err ->
            Log.w("LiveClassroomRtc", "initVideoRenderer warning: ${err.message}")
        }.getOrDefault(false)
    }

    suspend fun connect(
        roomName: String,
        participantUid: String,
        participantName: String,
        isTeacher: Boolean,
        canPublishAudio: Boolean
    ) {
        val creds = LiveKitTokenGenerator.loadCredentialsFromBuildConfig()
        if (creds == null) {
            _state.value = _state.value.copy(
                isConfigured = false,
                connectionState = LiveKitConnectionState.DISCONNECTED,
                canPublishAudio = false,
                noticeMessage = "LiveKit audio/video/screen streaming requires valid LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET in AI Studio Secrets. Text chat remains active."
            )
            return
        }

        val current = room
        val needsTokenRefresh =
            current != null &&
                _state.value.connectionState == LiveKitConnectionState.CONNECTED &&
                _state.value.connectedRoomName == roomName &&
                (lastConnectedAsTeacher != isTeacher || lastConnectedPublishAudio != canPublishAudio)

        if (current != null &&
            _state.value.connectionState == LiveKitConnectionState.CONNECTED &&
            _state.value.connectedRoomName == roomName &&
            !needsTokenRefresh
        ) {
            return
        }

        val wasMicEnabled = _state.value.isMicEnabled
        val wasCameraEnabled = _state.value.isCameraEnabled
        disconnect()
        _state.value = _state.value.copy(
            isConfigured = true,
            connectionState = LiveKitConnectionState.CONNECTING,
            connectedRoomName = roomName,
            canPublishAudio = canPublishAudio,
            noticeMessage = null
        )

        val token = LiveKitTokenGenerator.generateRoomToken(
            apiKey = creds.apiKey,
            apiSecret = creds.apiSecret,
            roomName = roomName,
            participantUid = participantUid,
            participantName = participantName,
            isTeacher = isTeacher,
            canPublishAudio = canPublishAudio
        )

        val newRoom = LiveKit.create(appContext)
        room = newRoom
        lastConnectedAsTeacher = isTeacher
        lastConnectedPublishAudio = canPublishAudio
        startEventObserver(newRoom)

        runCatching {
            enableClassroomSpeakerAudio()
            newRoom.connect(creds.url, token)
            if (isTeacher && wasCameraEnabled) {
                runCatching { newRoom.localParticipant.setCameraEnabled(true) }
            } else {
                // Student camera is NEVER enabled.
                runCatching { newRoom.localParticipant.setCameraEnabled(false) }
            }
            if ((isTeacher || canPublishAudio) && wasMicEnabled && needsTokenRefresh) {
                runCatching { newRoom.localParticipant.setMicrophoneEnabled(true) }
            } else {
                runCatching { newRoom.localParticipant.setMicrophoneEnabled(false) }
            }
            refreshTracksSnapshot(newRoom)
            _state.value = _state.value.copy(
                connectionState = LiveKitConnectionState.CONNECTED,
                canPublishAudio = isTeacher || canPublishAudio,
                noticeMessage = null
            )
        }.onFailure { err ->
            Log.e("LiveClassroomRtc", "Failed to connect to LiveKit", err)
            restoreAudioMode()
            _state.value = _state.value.copy(
                connectionState = LiveKitConnectionState.ERROR,
                canPublishAudio = false,
                noticeMessage = err.localizedMessage ?: "Unable to connect to real-time LiveKit server."
            )
        }
    }

    private fun startEventObserver(activeRoom: Room) {
        eventJob?.cancel()
        eventJob = scope.launch {
            activeRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected,
                    is RoomEvent.TrackSubscribed,
                    is RoomEvent.TrackUnsubscribed,
                    is RoomEvent.TrackPublished,
                    is RoomEvent.TrackUnpublished,
                    is RoomEvent.TrackMuted,
                    is RoomEvent.TrackUnmuted -> refreshTracksSnapshot(activeRoom)

                    is RoomEvent.TrackSubscriptionFailed -> {
                        Log.w("LiveClassroomRtc", "Track subscription failed for sid=${event.sid}: ${event.exception.message}")
                        refreshTracksSnapshot(activeRoom)
                    }

                    is RoomEvent.ActiveSpeakersChanged -> {
                        val names = event.speakers.mapNotNull { p ->
                            p.name?.takeIf { it.isNotBlank() } ?: p.identity?.value
                        }
                        _state.value = _state.value.copy(activeSpeakerNames = names)
                    }

                    is RoomEvent.Reconnecting -> {
                        _state.value = _state.value.copy(connectionState = LiveKitConnectionState.RECONNECTING)
                    }

                    is RoomEvent.Reconnected -> {
                        _state.value = _state.value.copy(connectionState = LiveKitConnectionState.CONNECTED)
                        refreshTracksSnapshot(activeRoom)
                    }

                    is RoomEvent.Disconnected -> {
                        restoreAudioMode()
                        _state.value = _state.value.copy(
                            connectionState = LiveKitConnectionState.DISCONNECTED,
                            canPublishAudio = false,
                            isMicEnabled = false,
                            isCameraEnabled = false,
                            isScreenShareActive = false,
                            localCameraTrack = null,
                            localScreenShareTrack = null,
                            remoteTeacherCameraTrack = null,
                            remoteScreenShareTrack = null,
                            activeSpeakerNames = emptyList()
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun refreshTracksSnapshot(activeRoom: Room) {
        var remoteScreenTrack: VideoTrack? = null
        var remoteCamTrack: VideoTrack? = null
        activeRoom.remoteParticipants.values.forEach { participant ->
            participant.videoTrackPublications.forEach { (pub, track) ->
                if (!pub.muted && track is VideoTrack) {
                    when (pub.source) {
                        Track.Source.SCREEN_SHARE -> remoteScreenTrack = track
                        Track.Source.CAMERA -> remoteCamTrack = track
                        else -> Unit
                    }
                }
            }
        }

        var localCamTrack: VideoTrack? = null
        var localScreenTrack: VideoTrack? = null
        activeRoom.localParticipant.videoTrackPublications.forEach { (pub, track) ->
            if (track is VideoTrack) {
                when (pub.source) {
                    Track.Source.CAMERA -> if (activeRoom.localParticipant.isCameraEnabled) localCamTrack = track
                    Track.Source.SCREEN_SHARE -> if (activeRoom.localParticipant.isScreenShareEnabled) localScreenTrack = track
                    else -> Unit
                }
            }
        }

        val localSharing = activeRoom.localParticipant.isScreenShareEnabled
        val localCam = activeRoom.localParticipant.isCameraEnabled
        val localMic = activeRoom.localParticipant.isMicrophoneEnabled

        _state.value = _state.value.copy(
            localCameraTrack = localCamTrack,
            localScreenShareTrack = localScreenTrack,
            remoteTeacherCameraTrack = remoteCamTrack,
            remoteScreenShareTrack = remoteScreenTrack,
            isCameraEnabled = localCam,
            isScreenShareActive = localSharing || remoteScreenTrack != null,
            isMicEnabled = localMic
        )
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Boolean> {
        val activeRoom = room ?: return Result.failure(IllegalStateException("Audio session not connected."))
        return runCatching {
            activeRoom.localParticipant.setMicrophoneEnabled(enabled)
            val actual = activeRoom.localParticipant.isMicrophoneEnabled
            refreshTracksSnapshot(activeRoom)
            if (enabled && !actual) {
                throw IllegalStateException("Microphone track did not activate. Verify mic permission and Teacher approval.")
            }
            actual
        }
    }

    suspend fun setCameraEnabled(enabled: Boolean): Result<Boolean> {
        if (!lastConnectedAsTeacher && enabled) {
            return Result.failure(SecurityException("Only Teacher/Admin can publish camera video."))
        }
        val activeRoom = room ?: return Result.failure(IllegalStateException("Live Classroom video session not connected."))
        return runCatching {
            activeRoom.localParticipant.setCameraEnabled(enabled)
            val actual = activeRoom.localParticipant.isCameraEnabled
            refreshTracksSnapshot(activeRoom)
            if (enabled && !actual) {
                throw IllegalStateException("Camera track failed to publish. Check camera permission.")
            }
            actual
        }
    }

    suspend fun startScreenShare(permissionIntentData: Intent): Result<Unit> {
        if (!lastConnectedAsTeacher) {
            return Result.failure(SecurityException("Only Teacher/Admin can share screen."))
        }
        val activeRoom = room ?: return Result.failure(
            IllegalStateException("Connect to Live Classroom before starting screen share.")
        )
        return runCatching {
            ScreenShareForegroundService.start(appContext)
            // Small yield so Android 14+ registers the active MediaProjection foreground service cleanly.
            delay(220L)
            activeRoom.localParticipant.setScreenShareEnabled(
                true,
                ScreenCaptureParams(mediaProjectionPermissionResultData = permissionIntentData)
            )
            refreshTracksSnapshot(activeRoom)
            if (!activeRoom.localParticipant.isScreenShareEnabled) {
                throw IllegalStateException("Screen share track could not start. Confirm screen capture permission.")
            }
        }.onFailure { err ->
            Log.e("LiveClassroomRtc", "startScreenShare failed: ${err.message}", err)
            ScreenShareForegroundService.stop(appContext)
        }
    }

    suspend fun stopScreenShare() {
        val activeRoom = room
        if (activeRoom != null) {
            runCatching { activeRoom.localParticipant.setScreenShareEnabled(false) }
            refreshTracksSnapshot(activeRoom)
        }
        ScreenShareForegroundService.stop(appContext)
        _state.value = _state.value.copy(
            isScreenShareActive = _state.value.remoteScreenShareTrack != null,
            localScreenShareTrack = null
        )
    }

    fun disconnect() {
        eventJob?.cancel()
        eventJob = null
        val activeRoom = room
        room = null
        if (activeRoom != null) {
            runCatching { activeRoom.disconnect() }
            runCatching { activeRoom.release() }
        }
        ScreenShareForegroundService.stop(appContext)
        restoreAudioMode()
        lastConnectedAsTeacher = false
        lastConnectedPublishAudio = false
        _state.value = LiveClassroomRtcState(isConfigured = LiveKitTokenGenerator.isConfigured())
    }

    fun release() {
        disconnect()
        scope.cancel()
    }

    private fun enableClassroomSpeakerAudio() {
        val mgr = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (savedAudioMode == null) savedAudioMode = mgr.mode
            @Suppress("DEPRECATION")
            if (savedSpeakerphoneOn == null) savedSpeakerphoneOn = mgr.isSpeakerphoneOn
            mgr.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = mgr.availableCommunicationDevices.firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) {
                    mgr.setCommunicationDevice(speaker)
                } else {
                    @Suppress("DEPRECATION")
                    mgr.isSpeakerphoneOn = true
                }
            } else {
                @Suppress("DEPRECATION")
                mgr.isSpeakerphoneOn = true
            }
        }.onFailure { err ->
            Log.w("LiveClassroomRtc", "Audio speaker routing warning: ${err.message}")
        }
    }

    private fun restoreAudioMode() {
        val mgr = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mgr.clearCommunicationDevice()
            }
            savedSpeakerphoneOn?.let {
                @Suppress("DEPRECATION")
                mgr.isSpeakerphoneOn = it
            }
            savedAudioMode?.let { mgr.mode = it }
            savedAudioMode = null
            savedSpeakerphoneOn = null
        }
    }
}
