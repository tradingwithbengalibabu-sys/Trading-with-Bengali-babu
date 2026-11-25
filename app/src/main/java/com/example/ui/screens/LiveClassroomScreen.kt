package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PresentToAll
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VoiceOverOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.LiveClassroomEntity
import com.example.data.model.LiveClassroomMessageEntity
import com.example.data.model.LiveClassroomParticipantEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.domain.TradeDateTimeHelper
import com.example.liveclass.LiveClassroomRtcManager
import com.example.liveclass.LiveClassroomRtcState
import com.example.liveclass.LiveKitConnectionState
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.GradientButton
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.LocalTradingColors
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LiveClassroomScreen(
    currentUser: UserEntity,
    classroom: LiveClassroomEntity?,
    participants: List<LiveClassroomParticipantEntity>,
    messages: List<LiveClassroomMessageEntity>,
    hasJoined: Boolean,
    rtcState: LiveClassroomRtcState,
    rtcManager: LiveClassroomRtcManager,
    selectedTimezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
    onStartLiveClass: (String) -> Unit,
    onEndLiveClass: () -> Unit,
    onJoinLiveClass: () -> Unit,
    onLeaveLiveClass: () -> Unit,
    onStartScreenShare: (Intent) -> Unit,
    onStopScreenShare: () -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onToggleRaiseHand: (Boolean) -> Unit,
    onToggleMic: (Boolean) -> Unit,
    onModerateParticipant: (
        participantUid: String,
        voiceApproved: Boolean?,
        isMutedByTeacher: Boolean?,
        handRaised: Boolean?,
        isRemoved: Boolean?
    ) -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val ext = LocalTradingColors.current
    val isTeacher = currentUser.roleEnum == UserRole.ADMIN
    val myUid = currentUser.id.ifBlank { currentUser.userId }
    val activeParticipants = remember(participants) { participants.filter { !it.isRemoved } }
    val myParticipant = remember(participants, myUid) { participants.firstOrNull { it.uid == myUid } }
    val isLive = classroom?.isLive == true

    var classTitleInput by remember(classroom?.title) {
        mutableStateOf(classroom?.title?.takeIf { it.isNotBlank() } ?: "Live Trading & Chart Breakdown")
    }
    var confirmEndClass by remember { mutableStateOf(false) }
    var fullscreenStage by remember { mutableStateOf(false) }
    var selectedPhoneTab by remember { mutableIntStateOf(0) }

    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            onStartScreenShare(data)
        } else {
            onShowMessage("Screen share permission cancelled.")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleMic(true)
        } else {
            onShowMessage("Microphone permission is required for voice participation.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleCamera(true)
        } else {
            onShowMessage("Camera permission is required for Teacher live video.")
        }
    }

    fun requestMicEnable() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onToggleMic(true)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun requestTeacherCameraToggle(enable: Boolean) {
        if (!enable) {
            onToggleCamera(false)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onToggleCamera(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchScreenCapture() {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mgr == null) {
            onShowMessage("Screen capture service unavailable on this device.")
            return
        }
        screenShareLauncher.launch(mgr.createScreenCaptureIntent())
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = classroom?.title?.takeIf { isLive && it.isNotBlank() } ?: "Live Online Classroom",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LiveStatusPill(isLive = isLive)
                                Text(
                                    text = "${activeParticipants.size} participant${if (activeParticipants.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.gold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("live_class_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isLive) {
                        if (isTeacher) {
                            Button(
                                onClick = { confirmEndClass = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ext.loss),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .height(38.dp)
                                    .testTag("teacher_end_class_button")
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("End Class", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        } else if (hasJoined) {
                            OutlinedButton(
                                onClick = onLeaveLiveClass,
                                border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.padding(end = 8.dp).height(38.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, tint = ext.loss, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Leave", color = ext.loss, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (rtcState.noticeMessage != null) {
                Surface(
                    color = ext.goldContainer,
                    border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = rtcState.noticeMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // OFFLINE LOBBY
            if (!isLive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(ext.gold.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LiveTv,
                                    contentDescription = "Live Classroom",
                                    tint = ext.gold,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Text(
                                text = if (isTeacher) "Start Live Teaching Session" else "Live Classroom Is Offline",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (isTeacher) {
                                    "Stream your tablet screen live, explain chart setups, answer voice Q&A questions, and manage student microphones in real time."
                                } else {
                                    "When Bangali Babu starts a Live Class, you will get an instant alert and a Join Live Class button here. No student camera is needed."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isTeacher) {
                                OutlinedTextField(
                                    value = classTitleInput,
                                    onValueChange = { classTitleInput = it },
                                    label = { Text("Class Session Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("live_class_title_input")
                                )
                                GradientButton(
                                    text = "Start Live Class",
                                    icon = Icons.Rounded.PlayArrow,
                                    onClick = { onStartLiveClass(classTitleInput) },
                                    colors = listOf(ext.profit, Color(0xFF059669)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("start_live_class_button")
                                )
                            }
                        }
                    }
                }
                return@Scaffold
            }

            // STUDENT REMOVED OR NOT YET JOINED
            if (!isTeacher && myParticipant?.isRemoved == true) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    EmptyTradingState(
                        icon = Icons.Rounded.PersonRemove,
                        title = "Removed from Session",
                        subtitle = "The Teacher removed you from this live session. You can return to the dashboard."
                    )
                }
                return@Scaffold
            }

            if (!isTeacher && !hasJoined) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(1.5.dp, ext.profit.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LiveStatusPill(isLive = true)
                            Text(
                                text = classroom?.title ?: "Live Trading Session",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Host: ${classroom?.hostName ?: "Bangali Babu"} • ${activeParticipants.size} active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Audio & screen watch only — student camera is disabled for privacy.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.gold,
                                textAlign = TextAlign.Center
                            )
                            GradientButton(
                                text = "Join Live Class",
                                icon = Icons.Rounded.LiveTv,
                                onClick = onJoinLiveClass,
                                colors = listOf(ext.profit, Color(0xFF059669)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("join_live_class_button")
                            )
                        }
                    }
                }
                return@Scaffold
            }

            // ACTIVE CLASSROOM WORKSPACE
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Screen Share & Teacher Video Stage + Controls
                ClassroomStageCard(
                    isTeacher = isTeacher,
                    classroom = classroom,
                    rtcState = rtcState,
                    rtcManager = rtcManager,
                    onStartScreenShare = { launchScreenCapture() },
                    onStopScreenShare = onStopScreenShare,
                    onToggleCamera = { enable -> requestTeacherCameraToggle(enable) },
                    onFullscreen = { fullscreenStage = true }
                )

                // Voice & Participation Bar (Teacher & Student)
                ClassroomAudioBar(
                    isTeacher = isTeacher,
                    myParticipant = myParticipant,
                    rtcState = rtcState,
                    onToggleMic = { enable ->
                        if (enable) requestMicEnable() else onToggleMic(false)
                    },
                    onToggleRaiseHand = onToggleRaiseHand
                )

                // Chat + Participants Moderation Area
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth >= 700.dp
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ClassroomChatPanel(
                                messages = messages,
                                selectedTimezoneId = selectedTimezoneId,
                                onSendMessage = onSendMessage,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            ClassroomParticipantsPanel(
                                isTeacher = isTeacher,
                                participants = activeParticipants,
                                activeSpeakers = rtcState.activeSpeakerNames,
                                onModerateParticipant = onModerateParticipant,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TabRow(
                                selectedTabIndex = selectedPhoneTab,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = ext.gold
                            ) {
                                Tab(
                                    selected = selectedPhoneTab == 0,
                                    onClick = { selectedPhoneTab = 0 },
                                    text = { Text("Live Chat (${messages.size})") },
                                    icon = { Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                                Tab(
                                    selected = selectedPhoneTab == 1,
                                    onClick = { selectedPhoneTab = 1 },
                                    text = { Text("Participants (${activeParticipants.size})") },
                                    icon = { Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                            if (selectedPhoneTab == 0) {
                                ClassroomChatPanel(
                                    messages = messages,
                                    selectedTimezoneId = selectedTimezoneId,
                                    onSendMessage = onSendMessage,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            } else {
                                ClassroomParticipantsPanel(
                                    isTeacher = isTeacher,
                                    participants = activeParticipants,
                                    activeSpeakers = rtcState.activeSpeakerNames,
                                    onModerateParticipant = onModerateParticipant,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEndClass) {
        AlertDialog(
            onDismissRequest = { confirmEndClass = false },
            title = { Text("End Live Class?") },
            text = { Text("This stops screen sharing and disconnects all students from the active session.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEndClass = false
                        onEndLiveClass()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.loss)
                ) {
                    Text("End Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEndClass = false }) { Text("Cancel") }
            }
        )
    }

    val fullscreenPrimaryTrack = if (isTeacher) {
        rtcState.localScreenShareTrack ?: rtcState.localCameraTrack
    } else {
        rtcState.remoteScreenShareTrack ?: rtcState.remoteTeacherCameraTrack
    }
    val fullscreenPipTrack = if (isTeacher && rtcState.localScreenShareTrack != null) {
        rtcState.localCameraTrack
    } else if (!isTeacher && rtcState.remoteScreenShareTrack != null) {
        rtcState.remoteTeacherCameraTrack
    } else null

    if (fullscreenStage && fullscreenPrimaryTrack != null) {
        Dialog(
            onDismissRequest = { fullscreenStage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LiveKitVideoTrackView(
                        track = fullscreenPrimaryTrack,
                        connectionState = rtcState.connectionState,
                        roomName = rtcState.connectedRoomName,
                        rtcManager = rtcManager,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (fullscreenPipTrack != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, ext.gold),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(16.dp)
                                .size(width = 132.dp, height = 88.dp)
                        ) {
                            LiveKitVideoTrackView(
                                track = fullscreenPipTrack,
                                connectionState = rtcState.connectionState,
                                roomName = rtcState.connectedRoomName,
                                rtcManager = rtcManager,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    IconButton(
                        onClick = { fullscreenStage = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStatusPill(isLive: Boolean) {
    val ext = LocalTradingColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot"
    )
    Surface(
        color = if (isLive) ext.loss.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (isLive) ext.loss.copy(alpha = alpha) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isLive) ext.loss.copy(alpha = alpha) else MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Text(
                text = if (isLive) "LIVE" else "OFFLINE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (isLive) ext.loss else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassroomStageCard(
    isTeacher: Boolean,
    classroom: LiveClassroomEntity?,
    rtcState: LiveClassroomRtcState,
    rtcManager: LiveClassroomRtcManager,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onFullscreen: () -> Unit
) {
    val ext = LocalTradingColors.current
    val primaryTrack = if (isTeacher) {
        rtcState.localScreenShareTrack ?: rtcState.localCameraTrack
    } else {
        rtcState.remoteScreenShareTrack ?: rtcState.remoteTeacherCameraTrack
    }
    val pipTrack = if (isTeacher && rtcState.localScreenShareTrack != null) {
        rtcState.localCameraTrack
    } else if (!isTeacher && rtcState.remoteScreenShareTrack != null) {
        rtcState.remoteTeacherCameraTrack
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1220)),
        border = BorderStroke(
            1.dp,
            if (rtcState.isScreenShareActive || rtcState.isCameraEnabled || classroom?.isScreenSharing == true) {
                ext.profit.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 8.5f),
                contentAlignment = Alignment.Center
            ) {
                if (primaryTrack != null) {
                    LiveKitVideoTrackView(
                        track = primaryTrack,
                        connectionState = rtcState.connectionState,
                        roomName = rtcState.connectedRoomName,
                        rtcManager = rtcManager,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (pipTrack != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, ext.gold),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(width = 108.dp, height = 72.dp)
                        ) {
                            LiveKitVideoTrackView(
                                track = pipTrack,
                                connectionState = rtcState.connectionState,
                                roomName = rtcState.connectedRoomName,
                                rtcManager = rtcManager,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    IconButton(
                        onClick = onFullscreen,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = "Fullscreen Video", tint = Color.White)
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (rtcState.isScreenShareActive) Icons.Rounded.CastConnected else Icons.Rounded.PresentToAll,
                            contentDescription = "Classroom Video Stage",
                            tint = if (rtcState.isScreenShareActive) ext.profit else ext.gold,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = when {
                                isTeacher && rtcState.isScreenShareActive ->
                                    "Sharing Your Screen Live"
                                isTeacher ->
                                    "Share Charting Screen or Turn On Teacher Camera"
                                classroom?.isScreenSharing == true && !rtcState.isConfigured ->
                                    "Teacher Screen Share Active (Add LiveKit Secrets to view video)"
                                classroom?.isScreenSharing == true ->
                                    "Connecting to Teacher Screen Stream..."
                                !rtcState.isConfigured ->
                                    "LiveKit Secrets Not Configured — Text Chat Active"
                                else ->
                                    "Waiting for Teacher video or screen share..."
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (isTeacher) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E182B))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { if (rtcState.isScreenShareActive) onStopScreenShare() else onStartScreenShare() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (rtcState.isScreenShareActive) ext.loss else ext.electricBlue
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(40.dp).testTag("teacher_screen_share_button")
                    ) {
                        Icon(
                            imageVector = if (rtcState.isScreenShareActive) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (rtcState.isScreenShareActive) "Stop Screen" else "Share Screen",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Button(
                        onClick = { onToggleCamera(!rtcState.isCameraEnabled) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (rtcState.isCameraEnabled) ext.profit else ext.purple
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(40.dp).testTag("teacher_camera_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (rtcState.isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (rtcState.isCameraEnabled) "Camera On" else "Camera Off",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveKitVideoTrackView(
    track: VideoTrack?,
    connectionState: LiveKitConnectionState,
    roomName: String?,
    rtcManager: LiveClassroomRtcManager,
    modifier: Modifier = Modifier
) {
    if (track == null) return
    var rendererRef by remember { mutableStateOf<TextureViewRenderer?>(null) }
    var initializedForKey by remember(rendererRef) { mutableStateOf<String?>(null) }

    val roomKey = "${roomName.orEmpty()}_$connectionState"

    DisposableEffect(rendererRef) {
        onDispose {
            runCatching { rendererRef?.release() }
        }
    }

    DisposableEffect(track, rendererRef, roomKey) {
        val view = rendererRef
        var attached = false
        if (view != null && connectionState == LiveKitConnectionState.CONNECTED) {
            if (initializedForKey != roomKey) {
                if (rtcManager.initRenderer(view)) {
                    initializedForKey = roomKey
                }
            }
            if (initializedForKey != null) {
                runCatching {
                    track.addRenderer(view)
                    attached = true
                }
            }
        }
        onDispose {
            if (view != null && attached) {
                runCatching { track.removeRenderer(view) }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureViewRenderer(ctx).also { tv ->
                rendererRef = tv
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ClassroomAudioBar(
    isTeacher: Boolean,
    myParticipant: LiveClassroomParticipantEntity?,
    rtcState: LiveClassroomRtcState,
    onToggleMic: (Boolean) -> Unit,
    onToggleRaiseHand: (Boolean) -> Unit
) {
    val ext = LocalTradingColors.current
    val rtcConnected = rtcState.connectionState == LiveKitConnectionState.CONNECTED
    val canSpeak = isTeacher || (myParticipant?.canSpeakLive == true)
    val micOn = rtcConnected && rtcState.isMicEnabled
    val handRaised = myParticipant?.handRaised == true

    Surface(
        color = ext.cardSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isTeacher -> if (micOn) "Teacher Mic Live" else "Teacher Mic Muted"
                        myParticipant?.isMutedByTeacher == true && myParticipant.voiceApproved -> "Muted by Teacher"
                        canSpeak && micOn -> "Voice Live — Speaking"
                        canSpeak && rtcConnected -> "Voice Approved — Tap Mic to Ask"
                        canSpeak -> "Voice Approved (Waiting for Audio Server)"
                        handRaised -> "Hand Raised — Waiting for Teacher Approval"
                        else -> "Listen Only Mode (No Camera Required)"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (micOn) ext.profit else MaterialTheme.colorScheme.onSurface
                )
                val connLabel = when (rtcState.connectionState) {
                    LiveKitConnectionState.CONNECTED -> "Audio & Video Connected"
                    LiveKitConnectionState.CONNECTING -> "Connecting Audio & Video..."
                    LiveKitConnectionState.RECONNECTING -> "Reconnecting Audio & Video..."
                    LiveKitConnectionState.ERROR -> "Audio/Video Server Error — Chat Active"
                    LiveKitConnectionState.DISCONNECTED ->
                        if (rtcState.isConfigured) "Audio Disconnected — Chat Active"
                        else "LiveKit Secrets Missing — Chat Active"
                }
                Text(
                    text = connLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isTeacher) {
                OutlinedButton(
                    onClick = { onToggleRaiseHand(!handRaised) },
                    border = BorderStroke(1.dp, if (handRaised) ext.gold else MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(42.dp).testTag("student_raise_hand_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PanTool,
                        contentDescription = "Raise Hand",
                        tint = if (handRaised) ext.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (handRaised) "Lower" else "Ask Voice", style = MaterialTheme.typography.labelMedium)
                }
            }

            Button(
                onClick = { onToggleMic(!micOn) },
                enabled = canSpeak && rtcConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (micOn) ext.profit else ext.electricBlue
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(42.dp).testTag("classroom_mic_toggle_button")
            ) {
                Icon(
                    imageVector = if (micOn) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = if (micOn) "Mute Mic" else "Unmute Mic",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (micOn) "Mic On" else "Mic Off", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ClassroomChatPanel(
    messages: List<LiveClassroomMessageEntity>,
    selectedTimezoneId: String,
    onSendMessage: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "No chat questions yet. Ask a question anytime!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isAdminSender = msg.senderRoleEnum == UserRole.ADMIN
                        Surface(
                            color = if (isAdminSender) ext.gold.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            border = if (isAdminSender) BorderStroke(1.dp, ext.gold.copy(alpha = 0.35f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isAdminSender) "${msg.senderName} (Teacher)" else msg.senderName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isAdminSender) ext.gold else ext.electricBlue
                                    )
                                    Text(
                                        text = TradeDateTimeHelper.formatTime12h(msg.sentAtMillis, selectedTimezoneId),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(text = msg.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Ask a question in chat...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("classroom_chat_input")
                )
                IconButton(
                    onClick = {
                        val txt = draft.trim()
                        if (txt.isNotEmpty()) {
                            onSendMessage(txt) { draft = "" }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ext.gold)
                        .testTag("classroom_chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send Chat", tint = Color(0xFF0A1628))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassroomParticipantsPanel(
    isTeacher: Boolean,
    participants: List<LiveClassroomParticipantEntity>,
    activeSpeakers: List<String>,
    onModerateParticipant: (
        participantUid: String,
        voiceApproved: Boolean?,
        isMutedByTeacher: Boolean?,
        handRaised: Boolean?,
        isRemoved: Boolean?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (participants.isEmpty()) {
                item {
                    Text(
                        text = "No participants joined yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(participants, key = { it.uid }) { p ->
                    val isPTeacher = p.roleEnum == UserRole.ADMIN
                    val speaking = p.name in activeSpeakers || (p.canSpeakLive && !p.isSelfMuted)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        border = if (p.handRaised) BorderStroke(1.dp, ext.gold.copy(alpha = 0.6f)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StudentAvatar(photoUrl = p.profilePhotoUrl, name = p.name, size = 36.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPTeacher) "${p.name} (Teacher)" else p.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isPTeacher) ext.gold else MaterialTheme.colorScheme.onSurface
                                    )
                                    val statusText = when {
                                        isPTeacher -> if (p.isSelfMuted) "Host • Mic Muted" else "Host • Speaking"
                                        p.handRaised -> "Raised Hand — Requesting Voice"
                                        p.canSpeakLive && !p.isSelfMuted -> "Voice Active"
                                        p.canSpeakLive -> "Voice Approved (Mic Ready)"
                                        else -> "Muted (Listen Only)"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            speaking -> ext.profit
                                            p.handRaised -> ext.gold
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (p.handRaised) {
                                    Icon(Icons.Rounded.PanTool, contentDescription = "Hand Raised", tint = ext.gold, modifier = Modifier.size(18.dp))
                                }
                            }

                            if (isTeacher && !isPTeacher) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!p.voiceApproved || p.handRaised) {
                                        OutlinedButton(
                                            onClick = {
                                                onModerateParticipant(p.uid, true, false, false, null)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = ext.profit, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Approve Voice", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                onModerateParticipant(p.uid, false, true, false, null)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.VoiceOverOff, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Revoke Voice", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onModerateParticipant(p.uid, null, !p.isMutedByTeacher, null, null)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (p.isMutedByTeacher) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (p.isMutedByTeacher) "Unmute" else "Mute Mic", style = MaterialTheme.typography.labelSmall)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onModerateParticipant(p.uid, false, true, false, true)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = ext.loss, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Remove", style = MaterialTheme.typography.labelSmall, color = ext.loss)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
