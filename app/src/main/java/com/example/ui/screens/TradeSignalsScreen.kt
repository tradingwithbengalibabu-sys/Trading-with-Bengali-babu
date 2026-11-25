package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.audio.SignalVoiceRecorder
import com.example.audio.VoicePlaybackState
import com.example.data.model.AccountStatus
import com.example.data.model.SignalAccess
import com.example.data.model.SignalAccessRecord
import com.example.data.model.SignalAudienceType
import com.example.data.model.TradeDirection
import com.example.data.model.TradeSignalEntity
import com.example.data.model.TradeSignalReactionEntity
import com.example.data.model.TradeSignalStatus
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.domain.TradeDateTimeHelper
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.GradientButton
import com.example.ui.components.ResilientFirebaseImage
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.LocalTradingColors
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TradeSignalsScreen(
    currentUser: UserEntity,
    signals: List<TradeSignalEntity>,
    reactionsBySignalId: Map<String, List<TradeSignalReactionEntity>>,
    students: List<UserEntity>,
    signalAccessRecords: List<SignalAccessRecord>,
    isBlockedForCurrentUser: Boolean,
    isSavingSignal: Boolean,
    voicePlaybackState: VoicePlaybackState,
    feedError: String? = null,
    selectedTimezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
    onPublishOrUpdateSignal: (
        existingSignal: TradeSignalEntity?,
        instrument: String,
        direction: TradeDirection,
        entryPrice: Double,
        stopLoss: Double,
        target1: Double,
        target2: Double?,
        strategyName: String,
        instructions: String,
        status: TradeSignalStatus,
        audienceType: SignalAudienceType,
        selectedStudentIds: List<String>,
        keptScreenshotUrls: List<String>,
        keptScreenshotPaths: List<String>,
        newScreenshotUris: List<Uri>,
        removedScreenshotPaths: List<String>,
        existingVoiceUrl: String,
        existingVoicePath: String,
        existingVoiceDurationSec: Int,
        newVoiceFile: File?,
        newVoiceDurationSec: Int,
        removeExistingVoice: Boolean,
        onSaved: () -> Unit
    ) -> Unit,
    onUpdateSignalStatus: (TradeSignalEntity, TradeSignalStatus) -> Unit,
    onDeleteSignal: (TradeSignalEntity) -> Unit,
    onToggleReaction: (TradeSignalEntity, String) -> Unit,
    onToggleVoicePlay: (String, Int) -> Unit,
    onSeekVoice: (Int) -> Unit,
    onStopVoice: () -> Unit,
    onSetStudentSignalAccess: (UserEntity, SignalAccess) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onStopVoice()
        onBack()
    }

    val ext = LocalTradingColors.current
    val isAdmin = currentUser.roleEnum == UserRole.ADMIN
    val myUid = currentUser.id.ifBlank { currentUser.userId }

    var adminTab by remember { mutableIntStateOf(0) }
    var statusFilter by remember { mutableStateOf<TradeSignalStatus?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingSignal by remember { mutableStateOf<TradeSignalEntity?>(null) }
    var confirmDeleteSignal by remember { mutableStateOf<TradeSignalEntity?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var reactionBreakdownSignal by remember { mutableStateOf<TradeSignalEntity?>(null) }

    val filteredSignals = remember(signals, statusFilter) {
        if (statusFilter == null) signals
        else signals.filter { it.statusEnum == statusFilter }
    }

    val accessByUid = remember(signalAccessRecords) {
        signalAccessRecords.associateBy { it.userId }
    }
    val blockedCount = remember(students, accessByUid) {
        students.count { st ->
            val rec = accessByUid[st.id]
            st.signalAccessEnum == SignalAccess.BLOCKED || rec?.signalAccessEnum == SignalAccess.BLOCKED
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isAdmin) "Admin Trade Desk" else "Trade Signal by Admin",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isAdmin) {
                                "${signals.count { it.statusEnum == TradeSignalStatus.ACTIVE }} active signals • $blockedCount signal-blocked"
                            } else {
                                "Real-time trade setups from Bangali Babu"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.gold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onStopVoice()
                            onBack()
                        },
                        modifier = Modifier.testTag("signals_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isAdmin && adminTab == 0) {
                        Button(
                            onClick = {
                                editingSignal = null
                                showEditorDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.gold,
                                contentColor = Color(0xFF0A1628)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(40.dp)
                                .testTag("admin_publish_signal_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Publish", fontWeight = FontWeight.Bold)
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
            if (!isAdmin && isBlockedForCurrentUser) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .fillMaxWidth()
                            .testTag("signal_access_blocked_card"),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(1.5.dp, ext.loss.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ext.loss.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Signals Blocked",
                                    tint = ext.loss,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Text(
                                text = "Trade Signal Access Restricted",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = ext.loss,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Your access to Admin Trade Signals, signal notifications, screenshots, and voice commentary is currently paused by the Admin.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Surface(
                                color = ext.profit.copy(alpha = 0.10f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, ext.profit.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "All your other features — Personal Trading Journal, Dashboard, Analytics, Reviews, and Admin Messages — remain 100% active.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.profit,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Button(
                                onClick = onBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Return to Dashboard")
                            }
                        }
                    }
                }
                return@Scaffold
            }

            if (isAdmin) {
                TabRow(
                    selectedTabIndex = adminTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = ext.gold
                ) {
                    Tab(
                        selected = adminTab == 0,
                        onClick = { adminTab = 0 },
                        text = { Text("Signals Desk (${signals.size})") },
                        modifier = Modifier.testTag("tab_admin_signals_desk")
                    )
                    Tab(
                        selected = adminTab == 1,
                        onClick = { adminTab = 1 },
                        text = { Text("Signal Access (${students.size})") },
                        modifier = Modifier.testTag("tab_admin_signal_access")
                    )
                }
            }

            if (isAdmin && adminTab == 1) {
                SignalAccessManagementList(
                    students = students,
                    accessByUid = accessByUid,
                    onToggleSignalAccess = onSetStudentSignalAccess
                )
            } else {
                // Status Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("All (${signals.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ext.gold.copy(alpha = 0.2f),
                            selectedLabelColor = ext.gold
                        )
                    )
                    TradeSignalStatus.entries.forEach { st ->
                        val count = signals.count { it.statusEnum == st }
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = if (statusFilter == st) null else st },
                            label = { Text("${st.label} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ext.gold.copy(alpha = 0.2f),
                                selectedLabelColor = ext.gold
                            )
                        )
                    }
                }

                if (!feedError.isNullOrBlank()) {
                    Surface(
                        color = ext.lossContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Signal sync issue: $feedError",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.loss,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (filteredSignals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyTradingState(
                            icon = Icons.Rounded.Campaign,
                            title = if (isAdmin) "No Trade Signals Published Yet" else "No Active Trade Signals",
                            subtitle = if (isAdmin) {
                                "Tap Publish above to share BUY/SELL setups with chart screenshots and voice commentary."
                            } else {
                                "New BUY & SELL setups from Bangali Babu will appear here automatically."
                            },
                            actionLabel = if (isAdmin) "Publish Trade Signal" else null,
                            onAction = if (isAdmin) {
                                {
                                    editingSignal = null
                                    showEditorDialog = true
                                }
                            } else null
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(filteredSignals, key = { it.id }) { signal ->
                            val reactions = reactionsBySignalId[signal.id].orEmpty()
                            val myReaction = reactions.firstOrNull { it.userId == myUid }
                            TradeSignalCard(
                                signal = signal,
                                isAdmin = isAdmin,
                                reactions = reactions,
                                myReactionEmoji = myReaction?.emoji,
                                voicePlaybackState = voicePlaybackState,
                                selectedTimezoneId = selectedTimezoneId,
                                onToggleVoicePlay = {
                                    onToggleVoicePlay(signal.voiceNoteUrl, signal.voiceDurationSec)
                                },
                                onSeekVoice = onSeekVoice,
                                onImageClick = { previewImageUrl = it },
                                onToggleReaction = { emoji -> onToggleReaction(signal, emoji) },
                                onViewReactionBreakdown = { reactionBreakdownSignal = signal },
                                onEditSignal = {
                                    editingSignal = signal
                                    showEditorDialog = true
                                },
                                onUpdateStatus = { newStatus -> onUpdateSignalStatus(signal, newStatus) },
                                onDeleteSignal = { confirmDeleteSignal = signal },
                                modifier = Modifier.widthIn(max = 900.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog && isAdmin) {
        TradeSignalEditorDialog(
            editingSignal = editingSignal,
            students = students,
            isSaving = isSavingSignal,
            onShowMessage = onShowMessage,
            onDismiss = {
                if (!isSavingSignal) {
                    showEditorDialog = false
                    editingSignal = null
                }
            },
            onSave = { instrument, dir, entry, sl, t1, t2, strat, instr, status, audience, selectedIds, keptUrls, keptPaths, newUris, removedPaths, exVoiceUrl, exVoicePath, exVoiceDur, newVoiceFile, newVoiceDur, removeVoice ->
                onPublishOrUpdateSignal(
                    editingSignal,
                    instrument,
                    dir,
                    entry,
                    sl,
                    t1,
                    t2,
                    strat,
                    instr,
                    status,
                    audience,
                    selectedIds,
                    keptUrls,
                    keptPaths,
                    newUris,
                    removedPaths,
                    exVoiceUrl,
                    exVoicePath,
                    exVoiceDur,
                    newVoiceFile,
                    newVoiceDur,
                    removeVoice
                ) {
                    showEditorDialog = false
                    editingSignal = null
                }
            }
        )
    }

    confirmDeleteSignal?.let { sig ->
        AlertDialog(
            onDismissRequest = { confirmDeleteSignal = null },
            title = { Text("Delete ${sig.instrument} Signal?") },
            text = { Text("This permanently deletes the signal, attached chart screenshots, voice note, and student reactions.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSignal(sig)
                        confirmDeleteSignal = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ext.loss)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSignal = null }) { Text("Cancel") }
            }
        )
    }

    previewImageUrl?.let { url ->
        ZoomableSignalImageDialog(
            imageUrl = url,
            onDismiss = { previewImageUrl = null }
        )
    }

    reactionBreakdownSignal?.let { sig ->
        val list = reactionsBySignalId[sig.id].orEmpty()
        AlertDialog(
            onDismissRequest = { reactionBreakdownSignal = null },
            title = { Text("${sig.instrument} Reactions (${list.size})") },
            text = {
                if (list.isEmpty()) {
                    Text("No student reactions yet.")
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        list.forEach { r ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(r.userName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(r.emoji, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { reactionBreakdownSignal = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SignalAccessManagementList(
    students: List<UserEntity>,
    accessByUid: Map<String, SignalAccessRecord>,
    onToggleSignalAccess: (UserEntity, SignalAccess) -> Unit
) {
    val ext = LocalTradingColors.current
    if (students.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EmptyTradingState(
                icon = Icons.Rounded.People,
                title = "No Registered Students Yet",
                subtitle = "Once students sign in, manage their independent Trade Signal permissions here."
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Surface(
                color = ext.electricBlue.copy(alpha = 0.10f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.35f)),
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()
            ) {
                Text(
                    text = "Signal-Only Blocking: Blocking Trade Signals prevents the student from receiving signal alerts, screenshots, voice commentary, or reactions while keeping their Trading Journal, Dashboard, Analytics, and Account Login 100% active.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        items(students, key = { it.id }) { st ->
            val rec = accessByUid[st.id]
            val isBlocked = st.signalAccessEnum == SignalAccess.BLOCKED || rec?.signalAccessEnum == SignalAccess.BLOCKED
            Card(
                modifier = Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxWidth()
                    .testTag("signal_access_card_${st.id}"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                border = BorderStroke(
                    1.dp,
                    if (isBlocked) ext.loss.copy(alpha = 0.45f) else ext.profit.copy(alpha = 0.30f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudentAvatar(
                        photoUrl = st.profilePhotoUrl,
                        name = st.name,
                        size = 44.dp
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = st.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = st.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(st.statusEnum)
                            Surface(
                                color = if (isBlocked) ext.loss.copy(alpha = 0.14f) else ext.profit.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = if (isBlocked) "Signals: BLOCKED" else "Signals: ALLOWED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isBlocked) ext.loss else ext.profit,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Switch(
                            checked = !isBlocked,
                            onCheckedChange = { allowed ->
                                onToggleSignalAccess(
                                    st,
                                    if (allowed) SignalAccess.ALLOWED else SignalAccess.BLOCKED
                                )
                            },
                            modifier = Modifier.testTag("signal_access_switch_${st.id}")
                        )
                        Text(
                            text = if (isBlocked) "Blocked" else "Allowed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBlocked) ext.loss else ext.profit
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TradeSignalCard(
    signal: TradeSignalEntity,
    isAdmin: Boolean,
    reactions: List<TradeSignalReactionEntity>,
    myReactionEmoji: String?,
    voicePlaybackState: VoicePlaybackState,
    selectedTimezoneId: String,
    onToggleVoicePlay: () -> Unit,
    onSeekVoice: (Int) -> Unit,
    onImageClick: (String) -> Unit,
    onToggleReaction: (String) -> Unit,
    onViewReactionBreakdown: () -> Unit,
    onEditSignal: () -> Unit,
    onUpdateStatus: (TradeSignalStatus) -> Unit,
    onDeleteSignal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val isBuy = signal.directionEnum == TradeDirection.BUY
    val dirColor = if (isBuy) ext.profit else ext.loss
    val statusColor = when (signal.statusEnum) {
        TradeSignalStatus.ACTIVE -> ext.electricBlue
        TradeSignalStatus.TARGET_HIT -> ext.profit
        TradeSignalStatus.SL_HIT -> ext.loss
        TradeSignalStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val timeLabel = remember(signal.createdAtMillis, selectedTimezoneId) {
        if (signal.createdAtMillis > 0L) {
            TradeDateTimeHelper.formatDateTimeDisplay(signal.createdAtMillis, selectedTimezoneId)
        } else {
            ""
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_signal_card_${signal.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, dirColor.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = dirColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, dirColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isBuy) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                                contentDescription = signal.directionEnum.name,
                                tint = dirColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = signal.directionEnum.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = dirColor
                            )
                        }
                    }
                    Column {
                        Text(
                            text = signal.instrument,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (timeLabel.isNotBlank()) {
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = signal.statusEnum.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (isAdmin) {
                        Text(
                            text = if (signal.audienceEnum == SignalAudienceType.ALL_STUDENTS) {
                                "Audience: All Students"
                            } else {
                                "Audience: ${signal.selectedStudentIds.size} Selected"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.gold
                        )
                    }
                }
            }

            // Price Levels Grid
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PriceMetricColumn("ENTRY", formatSignalPrice(signal.entryPrice), MaterialTheme.colorScheme.onSurface)
                    PriceMetricColumn("STOP LOSS", formatSignalPrice(signal.stopLoss), ext.loss)
                    PriceMetricColumn("TARGET 1", formatSignalPrice(signal.target1), ext.profit)
                    if (signal.target2 != null) {
                        PriceMetricColumn("TARGET 2", formatSignalPrice(signal.target2), ext.profit)
                    }
                    PriceMetricColumn("R:R", signal.computedRiskReward, ext.gold)
                }
            }

            if (signal.strategyName.isNotBlank() || signal.instructions.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (signal.strategyName.isNotBlank()) {
                        Text(
                            text = "Strategy: ${signal.strategyName}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.electricBlue
                        )
                    }
                    if (signal.instructions.isNotBlank()) {
                        Text(
                            text = signal.instructions,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Attached Screenshots (up to 4)
            if (signal.screenshotUrls.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Chart Screenshots (${signal.screenshotUrls.size}) — Tap to zoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.gold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        signal.screenshotUrls.forEachIndexed { idx, url ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .size(width = 140.dp, height = 96.dp)
                                    .clickable { onImageClick(url) }
                            ) {
                                ResilientFirebaseImage(
                                    model = url,
                                    contentDescription = "Signal chart screenshot ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loadingContent = {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Admin Voice Commentary Player
            if (signal.hasVoiceNote) {
                val isThisActive = voicePlaybackState.activeUrl == signal.voiceNoteUrl
                val isPlaying = isThisActive && voicePlaybackState.isPlaying
                val isBuffering = isThisActive && voicePlaybackState.isBuffering
                val totalSec = if (isThisActive && voicePlaybackState.totalSec > 0) {
                    voicePlaybackState.totalSec
                } else {
                    signal.voiceDurationSec.coerceAtLeast(1)
                }
                val currentSec = if (isThisActive) voicePlaybackState.currentSec.coerceAtMost(totalSec) else 0

                Surface(
                    color = ext.purple.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ext.purple.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = onToggleVoicePlay,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ext.purple)
                                    .testTag("signal_voice_play_${signal.id}")
                            ) {
                                if (isBuffering) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause Voice Note" else "Play Voice Note",
                                        tint = Color.White
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = ext.purple, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Admin Voice Commentary",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = ext.purple
                                        )
                                    }
                                    Text(
                                        text = "${formatSeconds(currentSec)} / ${formatSeconds(totalSec)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Slider(
                                    value = currentSec.toFloat(),
                                    onValueChange = { if (isThisActive) onSeekVoice(it.toInt()) },
                                    valueRange = 0f..totalSec.toFloat().coerceAtLeast(1f),
                                    enabled = isThisActive
                                )
                            }
                        }
                        if (isThisActive && voicePlaybackState.error != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = voicePlaybackState.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.loss,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onToggleVoicePlay,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Retry", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Emoji Reaction Bar
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    TradeSignalEntity.ALLOWED_REACTIONS.forEach { emoji ->
                        val count = reactions.count { it.emoji == emoji }
                        val selected = myReactionEmoji == emoji
                        Surface(
                            color = if (selected) ext.gold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) ext.gold else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onToggleReaction(emoji) }
                                .testTag("signal_react_${signal.id}_$emoji")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(emoji, style = MaterialTheme.typography.bodyMedium)
                                if (count > 0) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) ext.gold else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                if (isAdmin && reactions.isNotEmpty()) {
                    TextButton(onClick = onViewReactionBreakdown) {
                        Text("${reactions.size} reacted", style = MaterialTheme.typography.labelSmall, color = ext.gold)
                    }
                }
            }

            // Admin Quick Actions & Status Management
            if (isAdmin) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (signal.statusEnum != TradeSignalStatus.TARGET_HIT) {
                        OutlinedButton(
                            onClick = { onUpdateStatus(TradeSignalStatus.TARGET_HIT) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ext.profit, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Target Hit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (signal.statusEnum != TradeSignalStatus.SL_HIT) {
                        OutlinedButton(
                            onClick = { onUpdateStatus(TradeSignalStatus.SL_HIT) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = null, tint = ext.loss, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("SL Hit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (signal.statusEnum != TradeSignalStatus.CANCELLED) {
                        OutlinedButton(
                            onClick = { onUpdateStatus(TradeSignalStatus.CANCELLED) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel Signal", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onEditSignal,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = ext.electricBlue, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onDeleteSignal,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = ext.loss, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = ext.loss)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceMetricColumn(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TradeSignalEditorDialog(
    editingSignal: TradeSignalEntity?,
    students: List<UserEntity>,
    isSaving: Boolean,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        instrument: String,
        direction: TradeDirection,
        entryPrice: Double,
        stopLoss: Double,
        target1: Double,
        target2: Double?,
        strategyName: String,
        instructions: String,
        status: TradeSignalStatus,
        audienceType: SignalAudienceType,
        selectedStudentIds: List<String>,
        keptScreenshotUrls: List<String>,
        keptScreenshotPaths: List<String>,
        newScreenshotUris: List<Uri>,
        removedScreenshotPaths: List<String>,
        existingVoiceUrl: String,
        existingVoicePath: String,
        existingVoiceDurationSec: Int,
        newVoiceFile: File?,
        newVoiceDurationSec: Int,
        removeExistingVoice: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalTradingColors.current

    var instrument by remember(editingSignal) { mutableStateOf(editingSignal?.instrument ?: "NIFTY") }
    var direction by remember(editingSignal) { mutableStateOf(editingSignal?.directionEnum ?: TradeDirection.BUY) }
    var entryText by remember(editingSignal) { mutableStateOf(editingSignal?.entryPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var slText by remember(editingSignal) { mutableStateOf(editingSignal?.stopLoss?.toString() ?: "") }
    var target1Text by remember(editingSignal) { mutableStateOf(editingSignal?.target1?.takeIf { it > 0 }?.toString() ?: "") }
    var target2Text by remember(editingSignal) { mutableStateOf(editingSignal?.target2?.toString() ?: "") }
    var strategyName by remember(editingSignal) { mutableStateOf(editingSignal?.strategyName ?: "") }
    var instructions by remember(editingSignal) { mutableStateOf(editingSignal?.instructions ?: "") }
    var status by remember(editingSignal) { mutableStateOf(editingSignal?.statusEnum ?: TradeSignalStatus.ACTIVE) }
    var audienceType by remember(editingSignal) { mutableStateOf(editingSignal?.audienceEnum ?: SignalAudienceType.ALL_STUDENTS) }

    val selectedStudents = remember(editingSignal) {
        mutableStateListOf<String>().apply {
            addAll(editingSignal?.selectedStudentIds.orEmpty())
        }
    }

    val keptUrls = remember(editingSignal) {
        mutableStateListOf<String>().apply { addAll(editingSignal?.screenshotUrls.orEmpty()) }
    }
    val keptPaths = remember(editingSignal) {
        mutableStateListOf<String>().apply { addAll(editingSignal?.screenshotPaths.orEmpty()) }
    }
    val removedPaths = remember(editingSignal) { mutableStateListOf<String>() }
    val newScreenshotUris = remember(editingSignal) { mutableStateListOf<Uri>() }

    // Voice recording state (Admin only)
    val voiceRecorder = remember(context) { SignalVoiceRecorder(context) }
    var existingVoiceUrl by remember(editingSignal) { mutableStateOf(editingSignal?.voiceNoteUrl.orEmpty()) }
    var existingVoicePath by remember(editingSignal) { mutableStateOf(editingSignal?.voiceNotePath.orEmpty()) }
    var existingVoiceDur by remember(editingSignal) { mutableIntStateOf(editingSignal?.voiceDurationSec ?: 0) }
    var removeExistingVoice by remember(editingSignal) { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSec by remember { mutableIntStateOf(0) }
    var recordedVoiceFile by remember { mutableStateOf<File?>(null) }
    var recordedVoiceDur by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancelRecording()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSec = 0
            while (isRecording) {
                delay(1000L)
                recordingSec++
            }
        }
    }

    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remaining = (4 - keptUrls.size - newScreenshotUris.size).coerceAtLeast(0)
            if (remaining <= 0) {
                onShowMessage("Maximum 4 screenshots allowed per trade signal.")
            } else {
                newScreenshotUris.addAll(uris.take(remaining))
                if (uris.size > remaining) {
                    onShowMessage("Added $remaining screenshot(s) (max 4 per signal).")
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecording()
                .onSuccess { isRecording = true }
                .onFailure { onShowMessage(it.message ?: "Unable to start microphone.") }
        } else {
            onShowMessage("Microphone permission is needed to record Admin voice commentary.")
        }
    }

    val liveRr = remember(entryText, slText, target1Text) {
        val e = entryText.toDoubleOrNull() ?: 0.0
        val s = slText.toDoubleOrNull() ?: 0.0
        val t = target1Text.toDoubleOrNull() ?: 0.0
        TradeSignalEntity.calculateRiskRewardLabel(e, s, t)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
            border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingSignal == null) "Publish Trade Signal" else "Edit Trade Signal",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                // Preset Instruments
                Text("Instrument / Index", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TradeSignalEntity.PRESET_INSTRUMENTS.forEach { preset ->
                        FilterChip(
                            selected = instrument.equals(preset, ignoreCase = true),
                            onClick = { instrument = preset },
                            label = { Text(preset) }
                        )
                    }
                }
                OutlinedTextField(
                    value = instrument,
                    onValueChange = { instrument = it.uppercase(Locale.US) },
                    label = { Text("Instrument Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("signal_instrument_input")
                )

                // Direction
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeDirection.entries.forEach { dir ->
                        val selected = direction == dir
                        val c = if (dir == TradeDirection.BUY) ext.profit else ext.loss
                        FilterChip(
                            selected = selected,
                            onClick = { direction = dir },
                            label = { Text(dir.name, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.copy(alpha = 0.22f),
                                selectedLabelColor = c
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Price Fields
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entryText,
                        onValueChange = { entryText = it },
                        label = { Text("Entry Price *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("signal_entry_input")
                    )
                    OutlinedTextField(
                        value = slText,
                        onValueChange = { slText = it },
                        label = { Text("Stop Loss *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("signal_sl_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = target1Text,
                        onValueChange = { target1Text = it },
                        label = { Text("Target 1 *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("signal_t1_input")
                    )
                    OutlinedTextField(
                        value = target2Text,
                        onValueChange = { target2Text = it },
                        label = { Text("Target 2 (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("signal_t2_input")
                    )
                }
                Text(
                    text = "Calculated Risk : Reward = $liveRr",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ext.gold
                )

                OutlinedTextField(
                    value = strategyName,
                    onValueChange = { strategyName = it },
                    label = { Text("Strategy / Setup Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Trade Instructions & Confirmation Notes") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                // Signal Status
                Text("Signal Status", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TradeSignalStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                // Screenshots (Max 4)
                val totalShots = keptUrls.size + newScreenshotUris.size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chart Screenshots ($totalShots / 4)", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(
                        onClick = {
                            screenshotPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = totalShots < 4 && !isSaving
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Attach Screenshots")
                    }
                }
                if (totalShots > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keptUrls.forEachIndexed { idx, url ->
                            Box(modifier = Modifier.size(92.dp)) {
                                ResilientFirebaseImage(
                                    model = url,
                                    contentDescription = "Attached screenshot",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                                IconButton(
                                    onClick = {
                                        val path = keptPaths.getOrNull(idx)
                                        if (!path.isNullOrBlank()) removedPaths.add(path)
                                        keptUrls.removeAt(idx)
                                        if (idx < keptPaths.size) keptPaths.removeAt(idx)
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        newScreenshotUris.forEachIndexed { idx, uri ->
                            Box(modifier = Modifier.size(92.dp)) {
                                ResilientFirebaseImage(
                                    model = uri,
                                    contentDescription = "New screenshot",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                                IconButton(
                                    onClick = { newScreenshotUris.removeAt(idx) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Voice Note Recorder (Admin Only)
                Surface(
                    color = ext.purple.copy(alpha = 0.10f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, ext.purple.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Admin Voice Message (Optional)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.purple
                        )
                        if (isRecording) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recording voice note... ${formatSeconds(recordingSec)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = ext.loss
                                )
                                Button(
                                    onClick = {
                                        isRecording = false
                                        voiceRecorder.stopRecording()
                                            .onSuccess { (file, dur) ->
                                                recordedVoiceFile = file
                                                recordedVoiceDur = dur
                                            }
                                            .onFailure { onShowMessage(it.message ?: "Failed to save recording.") }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ext.loss)
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            voiceRecorder.startRecording()
                                                .onSuccess { isRecording = true }
                                                .onFailure { onShowMessage(it.message ?: "Microphone unavailable.") }
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    enabled = !isSaving
                                ) {
                                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = ext.purple, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (recordedVoiceFile != null || (existingVoiceUrl.isNotBlank() && !removeExistingVoice)) "Re-record" else "Record Voice")
                                }
                                if (recordedVoiceFile != null) {
                                    Text("Ready (${formatSeconds(recordedVoiceDur)})", style = MaterialTheme.typography.labelSmall, color = ext.profit, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        recordedVoiceFile = null
                                        recordedVoiceDur = 0
                                    }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove recorded voice", tint = ext.loss)
                                    }
                                } else if (existingVoiceUrl.isNotBlank() && !removeExistingVoice) {
                                    Text("Attached (${formatSeconds(existingVoiceDur)})", style = MaterialTheme.typography.labelSmall, color = ext.electricBlue, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { removeExistingVoice = true }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove existing voice", tint = ext.loss)
                                    }
                                }
                            }
                        }
                    }
                }

                // Audience Selector
                Text("Student Audience", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = audienceType == SignalAudienceType.ALL_STUDENTS,
                        onClick = { audienceType = SignalAudienceType.ALL_STUDENTS },
                        label = { Text("All Eligible Students") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = audienceType == SignalAudienceType.SELECTED_STUDENTS,
                        onClick = { audienceType = SignalAudienceType.SELECTED_STUDENTS },
                        label = { Text("Selected (${selectedStudents.size})") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (audienceType == SignalAudienceType.SELECTED_STUDENTS) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (students.isEmpty()) {
                                Text("No registered students available.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                students.forEach { st ->
                                    val sid = st.id.ifBlank { st.userId }
                                    val checked = sid in selectedStudents
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (checked) selectedStudents.remove(sid) else if (sid.isNotBlank()) selectedStudents.add(sid)
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { on ->
                                                if (on) {
                                                    if (!checked && sid.isNotBlank()) selectedStudents.add(sid)
                                                } else {
                                                    selectedStudents.remove(sid)
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(st.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = if (st.signalAccessEnum == SignalAccess.BLOCKED) "${st.email} (Signal-Blocked)" else st.email,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (st.signalAccessEnum == SignalAccess.BLOCKED) ext.loss else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ext.gold)
                }

                GradientButton(
                    text = if (isSaving) "Uploading & Publishing..." else if (editingSignal == null) "Publish Signal Now" else "Update Signal",
                    onClick = {
                        val ep = entryText.toDoubleOrNull()
                        val sl = slText.toDoubleOrNull()
                        val t1 = target1Text.toDoubleOrNull()
                        val t2 = if (target2Text.isBlank()) null else target2Text.toDoubleOrNull()
                        if (instrument.isBlank() || ep == null || ep <= 0.0 || sl == null || sl < 0.0 || t1 == null || t1 <= 0.0) {
                            onShowMessage("Enter valid Instrument, Entry Price, Stop Loss, and Target 1.")
                            return@GradientButton
                        }
                        if (audienceType == SignalAudienceType.SELECTED_STUDENTS && selectedStudents.isEmpty()) {
                            onShowMessage("Select at least one student for targeted signal.")
                            return@GradientButton
                        }
                        if (isRecording) {
                            onShowMessage("Stop voice recording before publishing.")
                            return@GradientButton
                        }
                        onSave(
                            instrument,
                            direction,
                            ep,
                            sl,
                            t1,
                            t2,
                            strategyName,
                            instructions,
                            status,
                            audienceType,
                            selectedStudents.toList(),
                            keptUrls.toList(),
                            keptPaths.toList(),
                            newScreenshotUris.toList(),
                            removedPaths.toList(),
                            existingVoiceUrl,
                            existingVoicePath,
                            existingVoiceDur,
                            recordedVoiceFile,
                            recordedVoiceDur,
                            removeExistingVoice
                        )
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("submit_trade_signal_button")
                )
            }
        }
    }
}

@Composable
private fun ZoomableSignalImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                            scale = newScale
                            offset = if (newScale > 1f) {
                                Offset(offset.x + pan.x, offset.y + pan.y)
                            } else {
                                Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                ResilientFirebaseImage(
                    model = imageUrl,
                    contentDescription = "Zoomable signal chart screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loadingContent = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    },
                    errorContent = { msg, onRetry ->
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BrokenImage,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Could not load screenshot: $msg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = onRetry) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close screenshot", tint = Color.White)
                }
                Text(
                    text = "Pinch to zoom • Drag to pan",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun formatSignalPrice(price: Double): String {
    return if (price % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", price)
    } else {
        String.format(Locale.US, "%.2f", price)
    }
}

private fun formatSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
