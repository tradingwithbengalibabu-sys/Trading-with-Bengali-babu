package com.example.ui

import android.Manifest
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.SessionPreferences
import com.example.data.model.AccountStatus
import com.example.data.model.SignalAccess
import com.example.data.model.TradeEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.domain.AdvancedTradeAnalytics
import com.example.domain.AdvancedTradeFilter
import com.example.notifications.TradingNotificationHelper
import com.example.ui.components.BrandLogoBadge
import com.example.ui.components.EmergencyRiskWarningDialog
import com.example.ui.components.StudentAvatar
import com.example.ui.screens.AdminStudentsScreen
import com.example.ui.screens.AdvancedAnalyticsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CommunicationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DisciplineScreen
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.LiveClassroomScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.StudentDetailScreen
import com.example.ui.screens.StudentProfileScreen
import com.example.ui.screens.TradeEntryScreen
import com.example.ui.screens.TradeSignalsScreen
import com.example.ui.theme.BangaliBabuTheme
import com.example.ui.theme.LocalTradingColors
import com.example.ui.viewmodel.CommunicationAlertType
import com.example.ui.viewmodel.TradingViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_JOURNAL = "journal"
const val ROUTE_DISCIPLINE = "discipline"
const val ROUTE_REPORTS = "reports"
const val ROUTE_STUDENTS = "students"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun TradingAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionPreferences = remember(context) { SessionPreferences(context.applicationContext) }
    val isDarkMode by sessionPreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentFirebaseUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentFirebaseUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    BangaliBabuTheme(darkTheme = isDarkMode) {
        val fbUser = currentFirebaseUser
        if (fbUser == null) {
            AuthScreen(
                isDarkMode = isDarkMode,
                onToggleTheme = { dark -> scope.launch { sessionPreferences.setDarkMode(dark) } },
                onAuthSuccess = { currentFirebaseUser = Firebase.auth.currentUser }
            )
        } else {
            val uid = fbUser.uid
            val db = remember(context) {
                FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
            }
            key(uid) {
                val viewModel: TradingViewModel = viewModel(
                    key = uid,
                    factory = TradingViewModel.provideFactory(
                        application = context.applicationContext as Application,
                        db = db,
                        uid = uid
                    )
                )
                AuthenticatedGate(
                    uid = uid,
                    isDarkMode = isDarkMode,
                    viewModel = viewModel,
                    onSignOut = {
                        signOutWithCleanup(credentialManager, scope) {
                            currentFirebaseUser = null
                        }
                    }
                )
            }
        }
    }
}

private fun signOutWithCleanup(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onComplete: () -> Unit
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onComplete()
            }
        }
    }
}

@Composable
private fun AuthenticatedGate(
    uid: String,
    isDarkMode: Boolean,
    viewModel: TradingViewModel,
    onSignOut: () -> Unit
) {
    val ext = LocalTradingColors.current
    val userState by viewModel.userState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, uid) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val state = userState) {
        UiState.Loading -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        BrandLogoBadge(size = 76.dp)
                        CircularProgressIndicator(color = ext.gold)
                        Text("Syncing cloud trading profile...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        is UiState.Error -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BrandLogoBadge(size = 64.dp)
                            Text("Unable to Load Profile", style = MaterialTheme.typography.titleLarge)
                            Text(state.message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = ext.loss)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = viewModel::retryProfileBootstrap) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry")
                                }
                                OutlinedButton(onClick = onSignOut) {
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }
            }
        }
        is UiState.Success -> {
            val user = state.data
            if (user.statusEnum == AccountStatus.SUSPENDED) {
                SuspendedAccountScreen(user = user, onSignOut = onSignOut)
            } else {
                AuthenticatedWorkspace(
                    user = user,
                    isDarkMode = isDarkMode,
                    snackbarHostState = snackbarHostState,
                    viewModel = viewModel,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun SuspendedAccountScreen(
    user: UserEntity,
    onSignOut: () -> Unit
) {
    val ext = LocalTradingColors.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = "Suspended",
                        tint = ext.loss,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "Account Suspended",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = ext.loss
                    )
                    Text(
                        text = "${user.name} (${user.email}), your Trading with Bangali Babu student access has been suspended by the Admin. Please contact your mentor to restore access.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWorkspace(
    user: UserEntity,
    isDarkMode: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: TradingViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalTradingColors.current
    val personalTrades by viewModel.personalTrades.collectAsStateWithLifecycle()
    val personalMetrics by viewModel.personalMetrics.collectAsStateWithLifecycle()
    val personalDiscipline by viewModel.personalDiscipline.collectAsStateWithLifecycle()
    val personalReviews by viewModel.personalReviews.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val pendingInvites by viewModel.pendingStudentInvites.collectAsStateWithLifecycle()
    val allPlatformTrades by viewModel.allPlatformTrades.collectAsStateWithLifecycle()
    val studentSummaries by viewModel.studentPerformanceSummaries.collectAsStateWithLifecycle()
    val combinedStudentsMetrics by viewModel.combinedStudentsMetrics.collectAsStateWithLifecycle()
    val displayCurrency by viewModel.displayCurrency.collectAsStateWithLifecycle()
    val liveUsdToInrRate by viewModel.liveUsdToInrRate.collectAsStateWithLifecycle()
    val isRefreshingRate by viewModel.isRefreshingRate.collectAsStateWithLifecycle()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val warnings by viewModel.warnings.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val unreadMessagesCount by viewModel.unreadMessagesCount.collectAsStateWithLifecycle()
    val unreadWarningsCount by viewModel.unreadWarningsCount.collectAsStateWithLifecycle()
    val unreadAnnouncementsCount by viewModel.unreadAnnouncementsCount.collectAsStateWithLifecycle()
    val totalUnreadCount by viewModel.totalUnreadCount.collectAsStateWithLifecycle()

    val personalSmartAlerts by viewModel.personalSmartAlerts.collectAsStateWithLifecycle()
    val activePersonalSmartAlerts by viewModel.activePersonalSmartAlerts.collectAsStateWithLifecycle()
    val personalWeeklyReports by viewModel.personalWeeklyReports.collectAsStateWithLifecycle()
    val liveCurrentWeekReport by viewModel.liveCurrentWeekReport.collectAsStateWithLifecycle()
    val unreadWeeklyReportsCount by viewModel.unreadWeeklyReportsCount.collectAsStateWithLifecycle()
    val allPlatformSmartAlerts by viewModel.allPlatformSmartAlerts.collectAsStateWithLifecycle()
    val allPlatformWeeklyReports by viewModel.allPlatformWeeklyReports.collectAsStateWithLifecycle()
    val isUpdatingProfile by viewModel.isUpdatingProfile.collectAsStateWithLifecycle()
    val isUploadingPhoto by viewModel.isUploadingPhoto.collectAsStateWithLifecycle()

    val selectedTimezoneId by viewModel.selectedTimezoneId.collectAsStateWithLifecycle()
    val customTradingSessions by viewModel.customTradingSessions.collectAsStateWithLifecycle()
    val advancedFilter by viewModel.advancedFilter.collectAsStateWithLifecycle()
    val personalAdvancedAnalysis by viewModel.personalAdvancedAnalysis.collectAsStateWithLifecycle()
    val personalCalendarMonth by viewModel.personalCalendarMonth.collectAsStateWithLifecycle()
    val calendarMonthYear by viewModel.calendarMonthYear.collectAsStateWithLifecycle()

    val tradeSignals by viewModel.tradeSignals.collectAsStateWithLifecycle()
    val activeTradeSignalsCount by viewModel.activeTradeSignalsCount.collectAsStateWithLifecycle()
    val signalReactionsById by viewModel.signalReactionsById.collectAsStateWithLifecycle()
    val allSignalAccessRecords by viewModel.allSignalAccessRecords.collectAsStateWithLifecycle()
    val isOwnSignalAccessBlocked by viewModel.isOwnSignalAccessBlocked.collectAsStateWithLifecycle()
    val isSavingSignal by viewModel.isSavingSignal.collectAsStateWithLifecycle()
    val voicePlaybackState by viewModel.voicePlaybackState.collectAsStateWithLifecycle()
    val tradeSignalsError by viewModel.tradeSignalsError.collectAsStateWithLifecycle()

    val liveClassroom by viewModel.liveClassroom.collectAsStateWithLifecycle()
    val classroomParticipants by viewModel.classroomParticipants.collectAsStateWithLifecycle()
    val classroomMessages by viewModel.classroomMessages.collectAsStateWithLifecycle()
    val hasJoinedLiveClass by viewModel.hasJoinedLiveClass.collectAsStateWithLifecycle()
    val rtcState by viewModel.rtcState.collectAsStateWithLifecycle()

    var showTradeEditor by remember { mutableStateOf(false) }
    var editingTrade by remember { mutableStateOf<TradeEntity?>(null) }
    var inspectedStudentId by remember { mutableStateOf<String?>(null) }
    var editingProfileUserId by remember { mutableStateOf<String?>(null) }
    var showCommunicationScreen by remember { mutableStateOf(false) }
    var commInitialTab by remember { mutableIntStateOf(0) }
    var commTargetStudentId by remember { mutableStateOf<String?>(null) }
    var showAnalyticsScreen by remember { mutableStateOf(false) }
    var analyticsInitialTab by remember { mutableIntStateOf(0) }
    var analyticsStudentId by remember { mutableStateOf<String?>(null) }
    var studentAnalyticsFilter by remember { mutableStateOf(AdvancedTradeFilter()) }
    var showTradeSignalsScreen by remember { mutableStateOf(false) }
    var showLiveClassroomScreen by remember { mutableStateOf(false) }
    var minimizedEmergencyAlertIds by remember { mutableStateOf(setOf<String>()) }
    var confirmLogout by remember { mutableStateOf(false) }

    fun openCommunication(tab: Int = 0, studentId: String? = null) {
        commInitialTab = tab
        commTargetStudentId = studentId
        showCommunicationScreen = true
    }

    fun openAnalytics(tab: Int = 0, studentId: String? = null) {
        analyticsInitialTab = tab
        analyticsStudentId = studentId
        if (studentId != null) studentAnalyticsFilter = AdvancedTradeFilter()
        showAnalyticsScreen = true
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(user.id) {
        TradingNotificationHelper.ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !TradingNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel, user.id) {
        viewModel.incomingAlerts.collect { alert ->
            val notifId = alert.id.hashCode()
            when (alert.type) {
                CommunicationAlertType.MESSAGE ->
                    TradingNotificationHelper.showMessageNotification(context, alert.title, alert.body, notifId)
                CommunicationAlertType.WARNING ->
                    TradingNotificationHelper.showWarningNotification(context, alert.title, alert.body, notifId)
                CommunicationAlertType.ANNOUNCEMENT ->
                    TradingNotificationHelper.showAnnouncementNotification(context, alert.title, alert.body, notifId)
                CommunicationAlertType.SMART_ALERT ->
                    TradingNotificationHelper.showSmartDisciplineAlertNotification(context, alert.title, alert.body, notifId)
                CommunicationAlertType.WEEKLY_REPORT ->
                    TradingNotificationHelper.showWeeklyReportNotification(context, alert.title, alert.body, notifId)
                CommunicationAlertType.TRADE_SIGNAL ->
                    TradingNotificationHelper.showTradeSignalNotification(context, alert.title, alert.body, notifId)
                CommunicationAlertType.LIVE_CLASS ->
                    TradingNotificationHelper.showLiveClassNotification(context, alert.title, alert.body, notifId)
            }
            val suppressSnackbar = when {
                showCommunicationScreen -> true
                alert.type == CommunicationAlertType.TRADE_SIGNAL && showTradeSignalsScreen -> true
                alert.type == CommunicationAlertType.LIVE_CLASS && showLiveClassroomScreen -> true
                else -> false
            }
            if (!suppressSnackbar) {
                viewModel.emitMessage("${alert.title}: ${alert.body.take(70)}")
            }
        }
    }

    if (showTradeSignalsScreen) {
        TradeSignalsScreen(
            currentUser = user,
            signals = tradeSignals,
            reactionsBySignalId = signalReactionsById,
            students = students,
            signalAccessRecords = allSignalAccessRecords,
            isBlockedForCurrentUser = isOwnSignalAccessBlocked,
            isSavingSignal = isSavingSignal,
            voicePlaybackState = voicePlaybackState,
            feedError = tradeSignalsError,
            selectedTimezoneId = selectedTimezoneId,
            onPublishOrUpdateSignal = viewModel::publishOrUpdateTradeSignal,
            onUpdateSignalStatus = viewModel::updateTradeSignalStatus,
            onDeleteSignal = viewModel::deleteTradeSignal,
            onToggleReaction = viewModel::toggleTradeSignalReaction,
            onToggleVoicePlay = viewModel::toggleSignalVoicePlay,
            onSeekVoice = viewModel::seekSignalVoice,
            onStopVoice = { viewModel.voicePlayer.stop() },
            onSetStudentSignalAccess = viewModel::setStudentSignalAccess,
            onShowMessage = viewModel::emitMessage,
            onBack = { showTradeSignalsScreen = false }
        )
        return
    }

    if (showLiveClassroomScreen) {
        LiveClassroomScreen(
            currentUser = user,
            classroom = liveClassroom,
            participants = classroomParticipants,
            messages = classroomMessages,
            hasJoined = hasJoinedLiveClass,
            rtcState = rtcState,
            rtcManager = viewModel.rtcManager,
            selectedTimezoneId = selectedTimezoneId,
            onStartLiveClass = viewModel::startLiveClass,
            onEndLiveClass = viewModel::endLiveClass,
            onJoinLiveClass = viewModel::joinLiveClass,
            onLeaveLiveClass = viewModel::leaveLiveClass,
            onStartScreenShare = viewModel::startTeacherScreenShare,
            onStopScreenShare = viewModel::stopTeacherScreenShare,
            onToggleCamera = viewModel::toggleTeacherCamera,
            onToggleRaiseHand = viewModel::toggleRaiseHand,
            onToggleMic = viewModel::toggleClassroomMic,
            onModerateParticipant = viewModel::moderateClassroomParticipant,
            onSendMessage = viewModel::sendClassroomMessage,
            onShowMessage = viewModel::emitMessage,
            onBack = { showLiveClassroomScreen = false }
        )
        return
    }

    if (showCommunicationScreen) {
        CommunicationScreen(
            currentUser = user,
            students = students,
            conversations = conversations,
            warnings = warnings,
            announcements = announcements,
            unreadMessagesCount = unreadMessagesCount,
            unreadWarningsCount = unreadWarningsCount,
            unreadAnnouncementsCount = unreadAnnouncementsCount,
            initialTab = commInitialTab,
            initialStudentId = commTargetStudentId,
            observeThreadMessages = viewModel::observeDirectMessages,
            onSendMessageToAdmin = viewModel::sendDirectMessageToAdmin,
            onSendMessageToStudent = viewModel::sendDirectMessageToStudent,
            onMarkConversationRead = viewModel::markConversationRead,
            onSendWarning = viewModel::sendWarning,
            onMarkWarningRead = viewModel::markWarningRead,
            onDeleteWarning = viewModel::deleteWarning,
            onPublishAnnouncement = viewModel::publishAnnouncement,
            onDeleteAnnouncement = viewModel::deleteAnnouncement,
            onMarkAnnouncementsRead = viewModel::markAllAnnouncementsRead,
            onBack = { showCommunicationScreen = false }
        )
        return
    }

    if (showAnalyticsScreen) {
        val inspectingStudent = if (user.roleEnum == UserRole.ADMIN && analyticsStudentId != null) {
            students.firstOrNull { it.id == analyticsStudentId }
        } else null
        if (inspectingStudent != null) {
            val stTrades = remember(allPlatformTrades, inspectingStudent.id) {
                allPlatformTrades.filter { it.userId == inspectingStudent.id }
            }
            val stAnalysis = remember(
                stTrades, displayCurrency, selectedTimezoneId, customTradingSessions, studentAnalyticsFilter, inspectingStudent.maxTradesPerDay
            ) {
                AdvancedTradeAnalytics.analyze(
                    trades = stTrades,
                    displayCurrency = displayCurrency,
                    timezoneId = selectedTimezoneId,
                    customSessions = customTradingSessions,
                    filter = studentAnalyticsFilter,
                    maxTradesPerDayThreshold = inspectingStudent.maxTradesPerDay
                )
            }
            val stCalendar = remember(stAnalysis.enrichedTrades, calendarMonthYear, selectedTimezoneId) {
                AdvancedTradeAnalytics.buildCalendarMonth(
                    enrichedTrades = stAnalysis.enrichedTrades,
                    year = calendarMonthYear.first,
                    monthZeroBased = calendarMonthYear.second,
                    timezoneId = selectedTimezoneId
                )
            }
            AdvancedAnalyticsScreen(
                traderTitle = inspectingStudent.name,
                analysis = stAnalysis,
                calendarMonth = stCalendar,
                filter = studentAnalyticsFilter,
                displayCurrency = displayCurrency,
                liveUsdToInrRate = liveUsdToInrRate,
                isRefreshingRate = isRefreshingRate,
                selectedTimezoneId = selectedTimezoneId,
                customSessions = customTradingSessions,
                initialTab = analyticsInitialTab,
                onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                onRefreshRate = { viewModel.refreshExchangeRate() },
                onUpdateFilter = { transform -> studentAnalyticsFilter = transform(studentAnalyticsFilter) },
                onClearFilter = { studentAnalyticsFilter = AdvancedTradeFilter() },
                onSelectTimezone = viewModel::setTimezoneId,
                onSaveCustomSessions = viewModel::saveCustomTradingSessions,
                onResetDefaultSessions = viewModel::resetDefaultTradingSessions,
                onShiftCalendarMonth = viewModel::shiftCalendarMonth,
                onBack = { showAnalyticsScreen = false }
            )
        } else {
            AdvancedAnalyticsScreen(
                traderTitle = user.name,
                analysis = personalAdvancedAnalysis,
                calendarMonth = personalCalendarMonth,
                filter = advancedFilter,
                displayCurrency = displayCurrency,
                liveUsdToInrRate = liveUsdToInrRate,
                isRefreshingRate = isRefreshingRate,
                selectedTimezoneId = selectedTimezoneId,
                customSessions = customTradingSessions,
                initialTab = analyticsInitialTab,
                onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                onRefreshRate = { viewModel.refreshExchangeRate() },
                onUpdateFilter = viewModel::updateAdvancedFilter,
                onClearFilter = viewModel::clearAdvancedFilter,
                onSelectTimezone = viewModel::setTimezoneId,
                onSaveCustomSessions = viewModel::saveCustomTradingSessions,
                onResetDefaultSessions = viewModel::resetDefaultTradingSessions,
                onShiftCalendarMonth = viewModel::shiftCalendarMonth,
                onBack = { showAnalyticsScreen = false }
            )
        }
        return
    }

    if (showTradeEditor) {
        TradeEntryScreen(
            user = user,
            editingTrade = editingTrade,
            liveUsdToInrRate = liveUsdToInrRate,
            isRefreshingRate = isRefreshingRate,
            selectedTimezoneId = selectedTimezoneId,
            customSessions = customTradingSessions,
            onRefreshRate = { viewModel.refreshExchangeRate() },
            onEncodeScreenshot = viewModel::encodeScreenshot,
            onSave = { existingId, dateMillis, dateIso, instrument, dir, tf, currency, fxRate, ep, xp, sl, tp, lot, risk, pnl, fees, tradeStatus, res, strat, entryR, exitR, mistakes, lessons, shotData, notes, entryTimeMs, exitDateIsoStr, exitTimeMs, sessName, closureType, tzId ->
                viewModel.saveTrade(
                    existingTradeId = existingId,
                    tradeDateMillis = dateMillis,
                    tradeDateIso = dateIso,
                    instrument = instrument,
                    direction = dir,
                    timeframe = tf,
                    currency = currency,
                    usdToInrRate = fxRate,
                    entryPrice = ep,
                    exitPrice = xp,
                    stopLoss = sl,
                    takeProfit = tp,
                    lotSize = lot,
                    riskAmount = risk,
                    actualPnl = pnl,
                    fees = fees,
                    tradeStatus = tradeStatus,
                    result = res,
                    strategy = strat,
                    entryReason = entryR,
                    exitReason = exitR,
                    mistakes = mistakes,
                    lessonsLearned = lessons,
                    screenshotData = shotData,
                    notes = notes,
                    entryTimeMillis = entryTimeMs,
                    exitDateIso = exitDateIsoStr,
                    exitTimeMillis = exitTimeMs,
                    tradingSession = sessName,
                    exitClosureType = closureType,
                    timezoneId = tzId
                ) {
                    showTradeEditor = false
                    editingTrade = null
                }
            },
            onCancel = {
                showTradeEditor = false
                editingTrade = null
            }
        )
        return
    }

    if (editingProfileUserId != null) {
        val ownUid = user.id.ifBlank { user.userId }
        val targetProfileUser = if (editingProfileUserId == ownUid) {
            user
        } else {
            students.firstOrNull { it.id == editingProfileUserId }
        }
        if (targetProfileUser != null) {
            StudentProfileScreen(
                viewer = user,
                targetUser = targetProfileUser,
                isSaving = isUpdatingProfile,
                isUploadingPhoto = isUploadingPhoto,
                onUploadPhoto = { uri -> viewModel.uploadProfilePhoto(targetProfileUser, uri) },
                onDeletePhoto = { viewModel.deleteProfilePhoto(targetProfileUser) },
                onSaveProfile = { fullName, mobile, whatsapp, dob, gender, address, editedEmail ->
                    viewModel.saveStudentProfile(
                        targetUser = targetProfileUser,
                        fullName = fullName,
                        mobileNumber = mobile,
                        whatsappNumber = whatsapp,
                        dateOfBirth = dob,
                        gender = gender,
                        fullAddress = address,
                        editedEmail = editedEmail
                    ) {
                        editingProfileUserId = null
                    }
                },
                onBack = { editingProfileUserId = null }
            )
            return
        }
    }

    if (user.roleEnum == UserRole.ADMIN && inspectedStudentId != null) {
        val summary = studentSummaries.firstOrNull { it.student.id == inspectedStudentId }
        if (summary != null) {
            val stTrades = remember(allPlatformTrades, summary.student.id) {
                allPlatformTrades.filter { it.userId == summary.student.id }
            }
            val stReviews by viewModel.observeStudentReviews(summary.student.id)
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val stUnread = conversations.firstOrNull { it.studentId == summary.student.id }?.unreadByAdminCount ?: 0
            val stSmartAlerts = remember(allPlatformSmartAlerts, summary.student.id) {
                allPlatformSmartAlerts.filter { it.userId == summary.student.id }
            }
            val stWeeklyReports = remember(allPlatformWeeklyReports, summary.student.id) {
                allPlatformWeeklyReports.filter { it.userId == summary.student.id }
            }

            StudentDetailScreen(
                summary = summary,
                studentTrades = stTrades,
                studentReviews = stReviews,
                studentSmartAlerts = stSmartAlerts,
                studentWeeklyReports = stWeeklyReports,
                displayCurrency = displayCurrency,
                liveUsdToInrRate = liveUsdToInrRate,
                isRefreshingRate = isRefreshingRate,
                selectedTimezoneId = selectedTimezoneId,
                unreadMessagesCount = stUnread,
                signalAccessRecord = allSignalAccessRecords.firstOrNull { it.userId == summary.student.id },
                onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                onRefreshRate = { viewModel.refreshExchangeRate() },
                onMessageStudent = { openCommunication(tab = 0, studentId = summary.student.id) },
                onWarnStudent = { openCommunication(tab = 1, studentId = summary.student.id) },
                onEditProfile = { editingProfileUserId = summary.student.id },
                onOpenStudentAnalytics = { tab -> openAnalytics(tab = tab, studentId = summary.student.id) },
                onSyncStudentDiscipline = { viewModel.syncStudentDisciplineData(summary.student, stTrades) },
                onToggleSignalAccess = { newAccess -> viewModel.setStudentSignalAccess(summary.student, newAccess) },
                onBack = { inspectedStudentId = null }
            )
            return
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

    val destinations = remember(user.roleEnum) {
        buildList {
            add(NavDestination(ROUTE_DASHBOARD, "Dashboard", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard))
            add(NavDestination(ROUTE_JOURNAL, "Journal", Icons.Rounded.Book, Icons.Outlined.Book))
            add(NavDestination(ROUTE_DISCIPLINE, "Discipline", Icons.Rounded.Psychology, Icons.Outlined.Psychology))
            add(NavDestination(ROUTE_REPORTS, "Reports", Icons.Rounded.Assessment, Icons.Outlined.Assessment))
            if (user.roleEnum == UserRole.ADMIN) {
                add(NavDestination(ROUTE_STUDENTS, "Students", Icons.Rounded.Groups, Icons.Outlined.Groups))
            }
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandLogoBadge(size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Trading with Bangali Babu",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${user.name} (${user.roleEnum.label})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.gold,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showLiveClassroomScreen = true },
                            modifier = Modifier.testTag("top_live_classroom_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (liveClassroom?.isLive == true) {
                                        Badge(containerColor = ext.loss)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LiveTv,
                                    contentDescription = "Live Classroom",
                                    tint = if (liveClassroom?.isLive == true) ext.profit else ext.gold
                                )
                            }
                        }
                        IconButton(
                            onClick = { showTradeSignalsScreen = true },
                            modifier = Modifier.testTag("top_trade_signals_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (activeTradeSignalsCount > 0) {
                                        Badge(containerColor = ext.profit, contentColor = Color.White) {
                                            Text(activeTradeSignalsCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ShowChart,
                                    contentDescription = "Trade Signals",
                                    tint = ext.gold
                                )
                            }
                        }
                        IconButton(
                            onClick = { editingProfileUserId = user.id.ifBlank { user.userId } },
                            modifier = Modifier.testTag("top_profile_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (user.roleEnum == UserRole.STUDENT && user.hasIncompleteProfile) {
                                        Badge(containerColor = ext.gold)
                                    }
                                }
                            ) {
                                StudentAvatar(
                                    photoUrl = user.profilePhotoUrl,
                                    name = user.name,
                                    size = 28.dp,
                                    borderColor = ext.electricBlue
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val defaultTab = when {
                                    user.roleEnum == UserRole.STUDENT && unreadMessagesCount == 0 && unreadWarningsCount > 0 -> 1
                                    user.roleEnum == UserRole.STUDENT && unreadMessagesCount == 0 && unreadAnnouncementsCount > 0 -> 2
                                    else -> 0
                                }
                                openCommunication(tab = defaultTab, studentId = null)
                            },
                            modifier = Modifier.testTag("top_communication_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (totalUnreadCount > 0) {
                                        Badge(containerColor = ext.loss, contentColor = Color.White) {
                                            Text(if (totalUnreadCount > 99) "99+" else totalUnreadCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (totalUnreadCount > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                                    contentDescription = "Messages and Notifications",
                                    tint = ext.gold
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleDarkMode(!isDarkMode) },
                            modifier = Modifier.testTag("top_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Switch Theme",
                                tint = ext.gold
                            )
                        }
                        IconButton(
                            onClick = { confirmLogout = true },
                            modifier = Modifier.testTag("top_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ext.gold,
                                    selectedTextColor = ext.gold,
                                    indicatorColor = ext.gold.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.padding(top = 8.dp))
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ext.gold,
                                    selectedTextColor = ext.gold,
                                    indicatorColor = ext.gold.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            val latestUnreadWarning = remember(warnings) {
                                warnings.firstOrNull { !it.readByStudent }
                            }
                            val latestAnnouncement = remember(announcements) {
                                announcements.firstOrNull()
                            }
                            val latestWeeklyReport = remember(personalWeeklyReports) {
                                personalWeeklyReports.firstOrNull()
                            }
                            val activeClassroomCount = remember(classroomParticipants) {
                                classroomParticipants.count { !it.isRemoved }
                            }
                            DashboardScreen(
                                user = user,
                                metrics = personalMetrics,
                                discipline = personalDiscipline,
                                displayCurrency = displayCurrency,
                                liveUsdToInrRate = liveUsdToInrRate,
                                isRefreshingRate = isRefreshingRate,
                                unreadMessagesCount = unreadMessagesCount,
                                unreadWarningsCount = unreadWarningsCount,
                                unreadAnnouncementsCount = unreadAnnouncementsCount,
                                latestUnreadWarning = latestUnreadWarning,
                                latestAnnouncement = latestAnnouncement,
                                activeSmartAlerts = activePersonalSmartAlerts,
                                latestWeeklyReport = latestWeeklyReport,
                                unreadWeeklyReportsCount = unreadWeeklyReportsCount,
                                smartInsights = personalAdvancedAnalysis.smartInsights,
                                activeSignalsCount = activeTradeSignalsCount,
                                isSignalAccessBlocked = isOwnSignalAccessBlocked,
                                isClassroomLive = liveClassroom?.isLive == true,
                                classroomParticipantCount = activeClassroomCount,
                                onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                                onRefreshRate = { viewModel.refreshExchangeRate() },
                                onAddTradeClick = {
                                    editingTrade = null
                                    showTradeEditor = true
                                },
                                onOpenContactAdmin = { openCommunication(tab = 0, studentId = null) },
                                onOpenWarnings = { openCommunication(tab = 1, studentId = null) },
                                onOpenAnnouncements = { openCommunication(tab = 2, studentId = null) },
                                onAcknowledgeSmartAlert = viewModel::acknowledgeSmartAlert,
                                onOpenWeeklyReports = { navigateToTab(ROUTE_DISCIPLINE) },
                                onOpenProfile = { editingProfileUserId = user.id.ifBlank { user.userId } },
                                onOpenTimeAnalytics = { openAnalytics(tab = 0, studentId = null) },
                                onOpenCalendar = { openAnalytics(tab = 4, studentId = null) },
                                onOpenTradeSignals = { showTradeSignalsScreen = true },
                                onOpenLiveClassroom = { showLiveClassroomScreen = true }
                            )
                        }
                        composable(ROUTE_JOURNAL) {
                            JournalScreen(
                                trades = personalTrades,
                                displayCurrency = displayCurrency,
                                timezoneId = selectedTimezoneId,
                                customSessions = customTradingSessions,
                                onAddTrade = {
                                    editingTrade = null
                                    showTradeEditor = true
                                },
                                onEditTrade = { trade ->
                                    editingTrade = trade
                                    showTradeEditor = true
                                },
                                onDeleteTrade = viewModel::deleteTrade,
                                onOpenCalendarAnalytics = { openAnalytics(tab = 4, studentId = null) }
                            )
                        }
                        composable(ROUTE_DISCIPLINE) {
                            DisciplineScreen(
                                user = user,
                                discipline = personalDiscipline,
                                displayCurrency = displayCurrency,
                                reviews = personalReviews,
                                smartAlerts = personalSmartAlerts,
                                weeklyReports = personalWeeklyReports,
                                liveCurrentWeekReport = liveCurrentWeekReport,
                                onUpdateGoals = viewModel::updateOwnDisciplineGoals,
                                onSaveReview = viewModel::saveDisciplineReview,
                                onAcknowledgeSmartAlert = viewModel::acknowledgeSmartAlert,
                                onMarkWeeklyReportRead = viewModel::markWeeklyReportRead,
                                onSaveCurrentWeekReportNow = viewModel::saveCurrentWeekReportNow
                            )
                        }
                        composable(ROUTE_REPORTS) {
                            ReportsScreen(
                                currentUser = user,
                                personalTrades = personalTrades,
                                students = students,
                                allPlatformTrades = allPlatformTrades,
                                displayCurrency = displayCurrency,
                                liveUsdToInrRate = liveUsdToInrRate,
                                isRefreshingRate = isRefreshingRate,
                                selectedTimezoneId = selectedTimezoneId,
                                customSessions = customTradingSessions,
                                onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                                onRefreshRate = { viewModel.refreshExchangeRate() },
                                onExportBackupJson = viewModel::exportBackupJson,
                                onRestoreBackupJson = viewModel::restoreBackupJson,
                                onOpenDeepAnalytics = { openAnalytics(tab = 0, studentId = null) },
                                onShowMessage = viewModel::emitMessage
                            )
                        }
                        if (user.roleEnum == UserRole.ADMIN) {
                            composable(ROUTE_STUDENTS) {
                                val unreadByStudent = remember(conversations) {
                                    conversations.associate { it.studentId to it.unreadByAdminCount }
                                }
                                val smartAlertsByStudent = remember(allPlatformSmartAlerts) {
                                    allPlatformSmartAlerts.groupBy { it.userId }
                                }
                                val latestWeeklyByStudent = remember(allPlatformWeeklyReports) {
                                    allPlatformWeeklyReports
                                        .groupBy { it.userId }
                                        .mapValues { (_, list) -> list.maxByOrNull { it.weekKey }!! }
                                }
                                val accessByUid = remember(allSignalAccessRecords) {
                                    allSignalAccessRecords.associateBy { it.userId }
                                }
                                AdminStudentsScreen(
                                    summaries = studentSummaries,
                                    pendingInvites = pendingInvites,
                                    combinedMetrics = combinedStudentsMetrics,
                                    displayCurrency = displayCurrency,
                                    liveUsdToInrRate = liveUsdToInrRate,
                                    isRefreshingRate = isRefreshingRate,
                                    unreadByStudent = unreadByStudent,
                                    smartAlertsByStudent = smartAlertsByStudent,
                                    latestWeeklyReportByStudent = latestWeeklyByStudent,
                                    signalAccessByUid = accessByUid,
                                    totalUnreadMessages = unreadMessagesCount,
                                    onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                                    onRefreshRate = { viewModel.refreshExchangeRate() },
                                    onOpenStudentDetail = { studentId -> inspectedStudentId = studentId },
                                    onOpenAdminInbox = { openCommunication(tab = 0, studentId = null) },
                                    onOpenAnnouncements = { openCommunication(tab = 2, studentId = null) },
                                    onOpenTradeDesk = { showTradeSignalsScreen = true },
                                    onOpenLiveClassroom = { showLiveClassroomScreen = true },
                                    onMessageStudent = { student -> openCommunication(tab = 0, studentId = student.id) },
                                    onWarnStudent = { student -> openCommunication(tab = 1, studentId = student.id) },
                                    onCreateInvite = viewModel::createStudentInvite,
                                    onUpdateInvite = viewModel::updateStudentInvite,
                                    onDeleteInvite = viewModel::deleteStudentInvite,
                                    onUpdateStudent = viewModel::updateStudent,
                                    onToggleSuspend = viewModel::toggleStudentSuspension,
                                    onToggleSignalAccess = viewModel::setStudentSignalAccess,
                                    onDeleteStudent = viewModel::deleteStudent,
                                    onEditStudentProfile = { student -> editingProfileUserId = student.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingEmergencyAlerts = remember(activePersonalSmartAlerts, minimizedEmergencyAlertIds) {
        activePersonalSmartAlerts.filter { it.id !in minimizedEmergencyAlertIds }
    }
    if (pendingEmergencyAlerts.isNotEmpty()) {
        val alertToShow = pendingEmergencyAlerts.first()
        EmergencyRiskWarningDialog(
            alert = alertToShow,
            alertIndex = 1,
            totalAlerts = pendingEmergencyAlerts.size,
            onUnderstand = {
                viewModel.acknowledgeSmartAlert(alertToShow, alertToShow.recommendedBreakDays)
            },
            onMinimize = {
                minimizedEmergencyAlertIds = minimizedEmergencyAlertIds + alertToShow.id
            }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign Out?") },
            text = { Text("Your trades, screenshots, reviews, and discipline data remain securely synced in Firebase Cloud Firestore.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onSignOut()
                }) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            }
        )
    }
}
