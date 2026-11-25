package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.SignalVoicePlayer
import com.example.audio.VoicePlaybackState
import com.example.data.local.CustomTradingSession
import com.example.data.local.SessionPreferences
import com.example.data.model.AccountStatus
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AnnouncementPriority
import com.example.data.model.ConversationThread
import com.example.data.model.DirectMessageEntity
import com.example.data.model.DisciplineReviewEntity
import com.example.data.model.EmotionState
import com.example.data.model.ExitClosureType
import com.example.data.model.LiveClassroomEntity
import com.example.data.model.LiveClassroomMessageEntity
import com.example.data.model.LiveClassroomParticipantEntity
import com.example.data.model.ReviewPeriod
import com.example.data.model.SignalAccess
import com.example.data.model.SignalAccessRecord
import com.example.data.model.SignalAudienceType
import com.example.data.model.SmartAlertType
import com.example.data.model.SmartDisciplineAlertEntity
import com.example.data.model.StudentInvite
import com.example.data.model.StudentWarningEntity
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradeSignalEntity
import com.example.data.model.TradeSignalReactionEntity
import com.example.data.model.TradeSignalStatus
import com.example.data.model.TradeStatus
import com.example.data.model.TradingCurrency
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.model.WarningSeverity
import com.example.data.model.WeeklyReportEntity
import com.example.data.repository.TradingRepository
import com.example.domain.AdvancedAnalysisResult
import com.example.domain.AdvancedTradeAnalytics
import com.example.domain.AdvancedTradeFilter
import com.example.domain.CalendarMonthData
import com.example.domain.DashboardMetrics
import com.example.domain.DisciplineMetrics
import com.example.domain.SmartDisciplineEngine
import com.example.domain.StudentPerformanceSummary
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.liveclass.LiveClassroomRtcManager
import com.example.liveclass.LiveClassroomRtcState
import com.example.liveclass.LiveKitConnectionState
import com.example.liveclass.LiveKitTokenGenerator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class CommunicationAlertType {
    MESSAGE,
    WARNING,
    ANNOUNCEMENT,
    SMART_ALERT,
    WEEKLY_REPORT,
    TRADE_SIGNAL,
    LIVE_CLASS
}

data class CommunicationAlertEvent(
    val id: String,
    val type: CommunicationAlertType,
    val title: String,
    val body: String,
    val targetStudentId: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class TradingViewModel(
    application: Application,
    db: FirebaseFirestore,
    private val uid: String
) : ViewModel() {

    private val sessionPreferences = SessionPreferences(application)
    val repository = TradingRepository(application, db, sessionPreferences)

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _bootstrapError = MutableStateFlow<String?>(null)
    private val _retryTrigger = MutableStateFlow(0)

    private val _isRefreshingRate = MutableStateFlow(false)
    val isRefreshingRate: StateFlow<Boolean> = _isRefreshingRate.asStateFlow()

    private val _rateSourceLabel = MutableStateFlow("Cached / Reference")
    val rateSourceLabel: StateFlow<String> = _rateSourceLabel.asStateFlow()

    private val _incomingAlerts = MutableSharedFlow<CommunicationAlertEvent>(extraBufferCapacity = 16)
    val incomingAlerts = _incomingAlerts.asSharedFlow()

    private val _isUpdatingProfile = MutableStateFlow(false)
    val isUpdatingProfile: StateFlow<Boolean> = _isUpdatingProfile.asStateFlow()

    private val _isUploadingPhoto = MutableStateFlow(false)
    val isUploadingPhoto: StateFlow<Boolean> = _isUploadingPhoto.asStateFlow()

    private val _isSavingSignal = MutableStateFlow(false)
    val isSavingSignal: StateFlow<Boolean> = _isSavingSignal.asStateFlow()

    val voicePlayer = SignalVoicePlayer(application.applicationContext)
    val voicePlaybackState: StateFlow<VoicePlaybackState> = voicePlayer.state

    private val _tradeSignalsError = MutableStateFlow<String?>(null)
    val tradeSignalsError: StateFlow<String?> = _tradeSignalsError.asStateFlow()

    val rtcManager = LiveClassroomRtcManager(application.applicationContext)
    val rtcState: StateFlow<LiveClassroomRtcState> = rtcManager.state

    private val _hasJoinedLiveClass = MutableStateFlow(false)
    val hasJoinedLiveClass: StateFlow<Boolean> = _hasJoinedLiveClass.asStateFlow()

    val displayCurrency: StateFlow<TradingCurrency> = repository.displayCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TradingCurrency.INR)

    val liveUsdToInrRate: StateFlow<Double> = repository.cachedUsdToInrRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TradingAnalytics.DEFAULT_USD_TO_INR_RATE)

    val selectedTimezoneId: StateFlow<String> = repository.selectedTimezoneId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TradeDateTimeHelper.DEFAULT_TIMEZONE_ID)

    val customTradingSessions: StateFlow<List<CustomTradingSession>> = repository.customTradingSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CustomTradingSession.DEFAULT_SESSIONS)

    private val _advancedFilter = MutableStateFlow(AdvancedTradeFilter())
    val advancedFilter: StateFlow<AdvancedTradeFilter> = _advancedFilter.asStateFlow()

    private val _calendarMonthYear = MutableStateFlow(
        TradeDateTimeHelper.currentYearAndMonth(TradeDateTimeHelper.DEFAULT_TIMEZONE_ID)
    )
    val calendarMonthYear: StateFlow<Pair<Int, Int>> = _calendarMonthYear.asStateFlow()

    init {
        bootstrapProfile()
        refreshExchangeRate(showFeedback = false)
    }

    fun setDisplayCurrency(currency: TradingCurrency) {
        viewModelScope.launch { repository.setDisplayCurrency(currency) }
    }

    fun setTimezoneId(timezoneId: String) {
        viewModelScope.launch { repository.setTimezoneId(timezoneId) }
    }

    fun saveCustomTradingSessions(sessions: List<CustomTradingSession>) {
        viewModelScope.launch {
            repository.saveCustomTradingSessions(sessions)
            emitMessage("Trading sessions updated.")
        }
    }

    fun resetDefaultTradingSessions() {
        viewModelScope.launch {
            repository.resetDefaultTradingSessions()
            emitMessage("Default trading sessions restored.")
        }
    }

    fun updateAdvancedFilter(transform: (AdvancedTradeFilter) -> AdvancedTradeFilter) {
        _advancedFilter.value = transform(_advancedFilter.value)
    }

    fun clearAdvancedFilter() {
        _advancedFilter.value = AdvancedTradeFilter()
    }

    fun shiftCalendarMonth(deltaMonths: Int) {
        val (y, m) = _calendarMonthYear.value
        val cal = java.util.Calendar.getInstance(TradeDateTimeHelper.resolveTimeZone(selectedTimezoneId.value)).apply {
            clear()
            set(y, m, 1)
            add(java.util.Calendar.MONTH, deltaMonths)
        }
        _calendarMonthYear.value = cal.get(java.util.Calendar.YEAR) to cal.get(java.util.Calendar.MONTH)
    }

    fun setCalendarMonth(year: Int, monthZeroBased: Int) {
        _calendarMonthYear.value = year to monthZeroBased.coerceIn(0, 11)
    }

    fun refreshExchangeRate(showFeedback: Boolean = true) {
        if (_isRefreshingRate.value) return
        viewModelScope.launch {
            _isRefreshingRate.value = true
            repository.refreshExchangeRate()
                .onSuccess { quote ->
                    _rateSourceLabel.value = quote.source
                    if (showFeedback) {
                        emitMessage("Live rate updated: 1 USD = ₹${String.format(Locale.US, "%.2f", quote.usdToInr)}")
                    }
                }
                .onFailure {
                    _rateSourceLabel.value = "Cached Rate"
                    if (showFeedback) {
                        emitMessage("Using cached rate (1 USD = ₹${String.format(Locale.US, "%.2f", liveUsdToInrRate.value)})")
                    }
                }
            _isRefreshingRate.value = false
        }
    }

    fun retryProfileBootstrap() {
        _bootstrapError.value = null
        _retryTrigger.value += 1
        bootstrapProfile()
    }

    private fun bootstrapProfile() {
        val fbUser = Firebase.auth.currentUser
        if (fbUser == null || fbUser.uid != uid) {
            _bootstrapError.value = "Authentication session unavailable."
            return
        }
        viewModelScope.launch {
            repository.ensureUserProfile(fbUser)
                .onFailure { err ->
                    _bootstrapError.value = err.message ?: "Unable to initialize user profile."
                }
        }
    }

    private fun extractRootFirestoreError(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    val userState: StateFlow<UiState<UserEntity>> = _retryTrigger
        .flatMapLatest {
            repository.observeCurrentUserProfile(uid)
                .map<UserEntity?, UiState<UserEntity>> { user ->
                    if (user != null) UiState.Success(user) else UiState.Loading
                }
                .catch { error ->
                    val root = extractRootFirestoreError(error)
                    emit(UiState.Error(root.message ?: "Failed to load profile"))
                }
        }
        .combine(_bootstrapError) { streamState, bootErr ->
            if (streamState is UiState.Loading && !bootErr.isNullOrBlank()) {
                UiState.Error(bootErr)
            } else {
                streamState
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val currentUser: StateFlow<UserEntity?> = userState
        .map { (it as? UiState.Success)?.data }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val personalTradesState: StateFlow<UiState<List<TradeEntity>>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(UiState.Loading)
            } else {
                repository.observeTradesForUser(user, user.userId.ifBlank { uid })
                    .map<List<TradeEntity>, UiState<List<TradeEntity>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = extractRootFirestoreError(error)
                        emit(UiState.Error(root.message ?: "Failed to load trades"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val personalTrades: StateFlow<List<TradeEntity>> = personalTradesState
        .map { (it as? UiState.Success)?.data.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val personalMetrics: StateFlow<DashboardMetrics> = combine(personalTrades, displayCurrency) { trades, dispCurr ->
        TradingAnalytics.calculateDashboardMetrics(trades, dispCurr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardMetrics())

    val personalAdvancedAnalysis: StateFlow<AdvancedAnalysisResult> = combine(
        combine(personalTrades, displayCurrency, selectedTimezoneId) { trades, curr, tz ->
            Triple(trades, curr, tz)
        },
        combine(customTradingSessions, _advancedFilter, currentUser) { sessions, filter, user ->
            Triple(sessions, filter, user?.maxTradesPerDay ?: 5)
        }
    ) { (trades, curr, tz), (sessions, filter, maxTrades) ->
        AdvancedTradeAnalytics.analyze(
            trades = trades,
            displayCurrency = curr,
            timezoneId = tz,
            customSessions = sessions,
            filter = filter,
            maxTradesPerDayThreshold = maxTrades
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        AdvancedTradeAnalytics.analyze(emptyList(), TradingCurrency.INR)
    )

    val personalCalendarMonth: StateFlow<CalendarMonthData> = combine(
        personalAdvancedAnalysis,
        _calendarMonthYear,
        selectedTimezoneId
    ) { analysis, (year, month), tz ->
        AdvancedTradeAnalytics.buildCalendarMonth(
            enrichedTrades = analysis.enrichedTrades,
            year = year,
            monthZeroBased = month,
            timezoneId = tz
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        AdvancedTradeAnalytics.buildCalendarMonth(
            emptyList(),
            _calendarMonthYear.value.first,
            _calendarMonthYear.value.second
        )
    )

    val personalDiscipline: StateFlow<DisciplineMetrics> = combine(
        personalTrades,
        currentUser,
        displayCurrency,
        liveUsdToInrRate
    ) { trades, user, dispCurr, rate ->
        if (user == null) DisciplineMetrics(displayCurrency = dispCurr)
        else TradingAnalytics.calculateDisciplineMetrics(trades, user, dispCurr, rate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DisciplineMetrics())

    val personalReviews: StateFlow<List<DisciplineReviewEntity>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.observeReviewsForUser(user, user.userId.ifBlank { uid })
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Admin-only reactive streams
    val students: StateFlow<List<UserEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.roleEnum == UserRole.ADMIN) {
                repository.observeStudents(user)
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val rawInvites: StateFlow<List<StudentInvite>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.roleEnum == UserRole.ADMIN) {
                repository.observeStudentInvites(user)
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val pendingStudentInvites: StateFlow<List<StudentInvite>> = combine(rawInvites, students) { invites, registered ->
        val registeredEmails = registered.map { it.email.trim().lowercase(Locale.US) }.toSet()
        invites.filter { it.email.trim().lowercase(Locale.US) !in registeredEmails }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allPlatformTrades: StateFlow<List<TradeEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.roleEnum == UserRole.ADMIN) {
                repository.observeAllTrades(user)
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val studentPerformanceSummaries: StateFlow<List<StudentPerformanceSummary>> = combine(
        students,
        allPlatformTrades,
        displayCurrency,
        liveUsdToInrRate
    ) { studentList, allTrades, dispCurr, rate ->
        val byUser = allTrades.groupBy { it.userId }
        studentList.map { student ->
            val stTrades = byUser[student.id].orEmpty()
            StudentPerformanceSummary(
                student = student,
                metrics = TradingAnalytics.calculateDashboardMetrics(stTrades, dispCurr),
                discipline = TradingAnalytics.calculateDisciplineMetrics(stTrades, student, dispCurr, rate)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val combinedStudentsMetrics: StateFlow<DashboardMetrics> = combine(
        students,
        allPlatformTrades,
        displayCurrency
    ) { studentList, allTrades, dispCurr ->
        val studentIds = studentList.map { it.id }.toSet()
        val onlyStudentTrades = allTrades.filter { it.userId in studentIds }
        TradingAnalytics.calculateDashboardMetrics(onlyStudentTrades, dispCurr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardMetrics())

    // --- STUDENT–ADMIN COMMUNICATION STREAMS & UNREAD BADGES ---

    private val conversationsSynced: StateFlow<Pair<Boolean, List<ConversationThread>>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(false to emptyList())
            } else if (user.roleEnum == UserRole.ADMIN) {
                repository.observeAllConversations(user)
                    .map { true to it }
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(true to emptyList())
                    }
            } else {
                repository.observeOwnConversation(user)
                    .map { true to listOfNotNull(it) }
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(true to emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false to emptyList())

    val conversations: StateFlow<List<ConversationThread>> = conversationsSynced
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val warningsSynced: StateFlow<Pair<Boolean, List<StudentWarningEntity>>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(false to emptyList())
            } else if (user.roleEnum == UserRole.ADMIN) {
                repository.observeAllWarnings(user)
                    .map { true to it }
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(true to emptyList())
                    }
            } else {
                repository.observeWarningsForStudent(user, user.userId.ifBlank { uid })
                    .map { true to it }
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(true to emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false to emptyList())

    val warnings: StateFlow<List<StudentWarningEntity>> = warningsSynced
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val announcementsSynced: StateFlow<Pair<Boolean, List<AnnouncementEntity>>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(false to emptyList())
            } else {
                repository.observeAnnouncements()
                    .map { true to it }
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(true to emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false to emptyList())

    val announcements: StateFlow<List<AnnouncementEntity>> = announcementsSynced
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unreadMessagesCount: StateFlow<Int> = combine(conversations, currentUser) { list, user ->
        when (user?.roleEnum) {
            UserRole.ADMIN -> list.sumOf { it.unreadByAdminCount.coerceAtLeast(0) }
            UserRole.STUDENT -> list.sumOf { it.unreadByStudentCount.coerceAtLeast(0) }
            null -> 0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val unreadWarningsCount: StateFlow<Int> = combine(warnings, currentUser) { list, user ->
        if (user?.roleEnum == UserRole.STUDENT) list.count { !it.readByStudent } else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val unreadAnnouncementsCount: StateFlow<Int> = combine(announcements, currentUser) { list, user ->
        if (user == null || user.roleEnum == UserRole.ADMIN) {
            0
        } else {
            val myId = user.id.ifBlank { uid }
            list.count { it.effectiveTimestampMillis > user.lastAnnouncementReadMillis && it.authorId != myId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    // --- SMART TRADING DISCIPLINE ALERTS & WEEKLY REPORTS ---

    private val smartEvalMutex = Mutex()
    private val inFlightSmartAlertIds = mutableSetOf<String>()
    private val inFlightWeeklyReportIds = mutableSetOf<String>()

    private val personalSmartAlertsSynced: StateFlow<Pair<Boolean, List<SmartDisciplineAlertEntity>>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(false to emptyList())
            } else {
                repository.observeSmartAlertsForUser(user, user.userId.ifBlank { uid })
                    .map { true to it }
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(true to emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false to emptyList())

    val personalSmartAlerts: StateFlow<List<SmartDisciplineAlertEntity>> = personalSmartAlertsSynced
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activePersonalSmartAlerts: StateFlow<List<SmartDisciplineAlertEntity>> = personalSmartAlerts
        .map { list -> list.filter { it.isActiveUnacknowledged } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val personalWeeklyReportsSynced: StateFlow<Pair<Boolean, List<WeeklyReportEntity>>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(false to emptyList())
            } else {
                repository.observeWeeklyReportsForUser(user, user.userId.ifBlank { uid })
                    .map { true to it }
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(true to emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false to emptyList())

    val personalWeeklyReports: StateFlow<List<WeeklyReportEntity>> = personalWeeklyReportsSynced
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val liveCurrentWeekReport: StateFlow<WeeklyReportEntity?> = combine(
        currentUser,
        personalTrades,
        displayCurrency
    ) { user, trades, dispCurr ->
        if (user == null) null
        else {
            val currentWindow = SmartDisciplineEngine.weekWindowForMillis(System.currentTimeMillis())
            val weekTrades = trades.filter {
                it.tradeDateMillis in currentWindow.startMillis..currentWindow.endMillis
            }
            if (weekTrades.isEmpty()) null
            else SmartDisciplineEngine.generateWeeklyReport(
                user = user,
                week = currentWindow,
                weekTrades = weekTrades,
                allUserTrades = trades,
                displayCurrency = dispCurr
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val allPlatformSmartAlerts: StateFlow<List<SmartDisciplineAlertEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.roleEnum == UserRole.ADMIN) {
                repository.observeAllSmartAlerts(user)
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    /**
     * Combined Admin Monitor feed across all students: merges persisted Firestore alerts with
     * real-time evaluated student risk alerts so Admin Nirab Sarkar can immediately see every
     * student triggering Weekly Loss (>$200 eq.), 10 Consecutive SL, or 3 Strategy Consecutive SL.
     */
    val adminMonitoredRiskAlerts: StateFlow<List<SmartDisciplineAlertEntity>> = combine(
        students,
        allPlatformTrades,
        allPlatformSmartAlerts,
        liveUsdToInrRate
    ) { studentList, allTrades, persistedAlerts, fxRate ->
        val studentMap = studentList.associateBy { it.id.ifBlank { it.userId } }
        val tradesByStudent = allTrades.groupBy { it.userId }
        val persistedByStudent = persistedAlerts.groupBy { it.userId }

        val merged = mutableListOf<SmartDisciplineAlertEntity>()
        studentMap.forEach { (sid, student) ->
            val stPersisted = persistedByStudent[sid].orEmpty()
            val stTrades = tradesByStudent[sid].orEmpty()
            val eval = SmartDisciplineEngine.evaluateSmartAlerts(
                user = student,
                trades = stTrades,
                existingAlerts = stPersisted,
                liveUsdToInrRate = fxRate
            )
            val updateById = eval.existingAlertsToUpdate.associateBy { it.alertId }
            stPersisted.forEach { alert ->
                val upd = updateById[alert.id]
                merged.add(
                    if (upd == null) alert
                    else alert.copy(
                        stopLossCount = upd.newStopLossCount,
                        lookbackCount = upd.newLookbackCount,
                        lossAmountUsd = upd.newLossAmountUsd,
                        lossAmountInr = upd.newLossAmountInr,
                        exchangeRateUsed = upd.newExchangeRateUsed,
                        triggerDateIso = upd.newTriggerDateIso,
                        resolved = upd.newResolved,
                        title = upd.newTitle,
                        message = upd.newMessage,
                        triggeredAtMillis = upd.newTriggeredAtMillis
                    )
                )
            }
            merged.addAll(eval.newAlertsToCreate)
        }
        merged.sortedWith(
            compareBy<SmartDisciplineAlertEntity> { !it.isActiveUnacknowledged }
                .thenBy { it.acknowledged }
                .thenByDescending { it.effectiveTimestampMillis }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allPlatformWeeklyReports: StateFlow<List<WeeklyReportEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.roleEnum == UserRole.ADMIN) {
                repository.observeAllWeeklyReports(user)
                    .catch { error ->
                        extractRootFirestoreError(error)
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unreadWeeklyReportsCount: StateFlow<Int> = personalWeeklyReports
        .map { list -> list.count { !it.readByUser } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalUnreadCount: StateFlow<Int> = combine(
        unreadMessagesCount,
        unreadWarningsCount,
        unreadAnnouncementsCount
    ) { msgCount, warnCount, annCount ->
        msgCount + warnCount + annCount
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    // --- ADMIN TRADE DESK, SIGNAL ACCESS & REACTIONS ---

    val ownSignalAccessRecord: StateFlow<SignalAccessRecord?> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(null)
            else repository.observeSignalAccessRecord(user.id.ifBlank { uid })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val allSignalAccessRecords: StateFlow<List<SignalAccessRecord>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.roleEnum == UserRole.ADMIN) {
                repository.observeAllSignalAccessRecords(user)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val isOwnSignalAccessBlocked: StateFlow<Boolean> = combine(
        currentUser,
        ownSignalAccessRecord
    ) { user, record ->
        user != null &&
            user.roleEnum != UserRole.ADMIN &&
            (user.signalAccessEnum == SignalAccess.BLOCKED || record?.signalAccessEnum == SignalAccess.BLOCKED)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val tradeSignalsSynced: StateFlow<Pair<Boolean, List<TradeSignalEntity>>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                _tradeSignalsError.value = null
                flowOf(false to emptyList())
            } else {
                repository.observeTradeSignalsForUser(user) { err ->
                    _tradeSignalsError.value = err
                }
                    .map { true to it }
                    .catch { err ->
                        val root = extractRootFirestoreError(err)
                        _tradeSignalsError.value = root.message ?: "Unable to sync Trade Signals."
                        emit(false to emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false to emptyList())

    val tradeSignals: StateFlow<List<TradeSignalEntity>> = tradeSignalsSynced
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeTradeSignalsCount: StateFlow<Int> = combine(
        tradeSignals,
        isOwnSignalAccessBlocked
    ) { list, blocked ->
        if (blocked) 0 else list.count { it.statusEnum == TradeSignalStatus.ACTIVE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val signalReactionsById: StateFlow<Map<String, List<TradeSignalReactionEntity>>> = combine(
        tradeSignals,
        isOwnSignalAccessBlocked
    ) { signals, blocked ->
        if (blocked) emptyList() else signals
    }.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(emptyMap())
        } else {
            val capped = list.take(40)
            combine(capped.map { sig ->
                repository.observeReactionsForSignal(sig.id).map { reactions -> sig.id to reactions }
            }) { pairs ->
                pairs.toMap()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    // --- LIVE ONLINE CLASSROOM ---

    val liveClassroom: StateFlow<LiveClassroomEntity?> = currentUser
        .flatMapLatest { user ->
            if (user == null || user.statusEnum == AccountStatus.SUSPENDED) flowOf(null)
            else repository.observeLiveClassroom()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val classroomParticipants: StateFlow<List<LiveClassroomParticipantEntity>> = currentUser
        .flatMapLatest { user ->
            if (user == null || user.statusEnum == AccountStatus.SUSPENDED) flowOf(emptyList())
            else repository.observeClassroomParticipants()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val classroomMessages: StateFlow<List<LiveClassroomMessageEntity>> = currentUser
        .flatMapLatest { user ->
            if (user == null || user.statusEnum == AccountStatus.SUSPENDED) flowOf(emptyList())
            else repository.observeClassroomMessages()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        observeIncomingCommunicationAlerts()
        observeAndEvaluateSmartDiscipline()
        observeLiveClassroomModeration()
    }

    private fun observeAndEvaluateSmartDiscipline() {
        viewModelScope.launch {
            combine(
                combine(currentUser, personalTradesState, personalSmartAlertsSynced) { user, tradesState, alertsPair ->
                    Triple(user, tradesState, alertsPair)
                },
                combine(personalWeeklyReportsSynced, displayCurrency, liveUsdToInrRate) { reportsPair, dispCurr, fxRate ->
                    Triple(reportsPair, dispCurr, fxRate)
                }
            ) { (user, tradesState, alertsPair), (reportsPair, dispCurr, fxRate) ->
                Triple(user, tradesState, Pair(alertsPair, reportsPair)) to (dispCurr to fxRate)
            }.collect { (triple, currAndRate) ->
                val (user, tradesState, syncedPairs) = triple
                val (dispCurr, fxRate) = currAndRate
                val (alertsSynced, existingAlerts) = syncedPairs.first
                val (reportsSynced, existingReports) = syncedPairs.second
                if (user == null || tradesState !is UiState.Success || !alertsSynced || !reportsSynced) {
                    return@collect
                }
                val trades = tradesState.data
                smartEvalMutex.withLock {
                    val eval = SmartDisciplineEngine.evaluateSmartAlerts(
                        user = user,
                        trades = trades,
                        existingAlerts = existingAlerts,
                        liveUsdToInrRate = fxRate
                    )
                    val filteredNewAlerts = eval.newAlertsToCreate.filter { it.id !in inFlightSmartAlertIds }
                    if (filteredNewAlerts.isNotEmpty() || eval.existingAlertsToUpdate.isNotEmpty()) {
                        filteredNewAlerts.forEach { inFlightSmartAlertIds.add(it.id) }
                        repository.syncSmartAlertsForUser(
                            actor = user,
                            targetUser = user,
                            evaluation = eval.copy(newAlertsToCreate = filteredNewAlerts)
                        ).onSuccess { createdAlerts ->
                            createdAlerts.forEach { alert ->
                                _incomingAlerts.tryEmit(
                                    CommunicationAlertEvent(
                                        id = "smart_${alert.id}",
                                        type = CommunicationAlertType.SMART_ALERT,
                                        title = alert.title,
                                        body = alert.message
                                    )
                                )
                            }
                            val existingById = existingAlerts.associateBy { it.id }
                            eval.existingAlertsToUpdate.forEach { upd ->
                                val prev = existingById[upd.alertId]
                                if (prev != null && prev.resolved && !upd.newResolved) {
                                    _incomingAlerts.tryEmit(
                                        CommunicationAlertEvent(
                                            id = "smart_reactivated_${upd.alertId}_${upd.newTriggeredAtMillis}",
                                            type = CommunicationAlertType.SMART_ALERT,
                                            title = upd.newTitle,
                                            body = upd.newMessage
                                        )
                                    )
                                }
                            }
                        }.onFailure {
                            filteredNewAlerts.forEach { inFlightSmartAlertIds.remove(it.id) }
                        }
                    }

                    val missingReports = SmartDisciplineEngine.buildMissingCompletedWeeklyReports(
                        user = user,
                        allUserTrades = trades,
                        existingReports = existingReports,
                        displayCurrency = dispCurr
                    ).filter { it.id !in inFlightWeeklyReportIds }

                    if (missingReports.isNotEmpty()) {
                        missingReports.forEach { inFlightWeeklyReportIds.add(it.id) }
                        repository.saveWeeklyReports(
                            actor = user,
                            reports = missingReports,
                            overwriteExisting = false
                        ).onSuccess { createdReports ->
                            createdReports.forEach { report ->
                                _incomingAlerts.tryEmit(
                                    CommunicationAlertEvent(
                                        id = "wkrep_${report.id}",
                                        type = CommunicationAlertType.WEEKLY_REPORT,
                                        title = "Weekly Performance Report (${report.weekKey})",
                                        body = report.summaryText
                                    )
                                )
                            }
                        }.onFailure {
                            missingReports.forEach { inFlightWeeklyReportIds.remove(it.id) }
                        }
                    }
                }
            }
        }
    }

    private fun observeIncomingCommunicationAlerts() {
        viewModelScope.launch {
            var baselineCaptured = false
            val seenLastMillisByStudent = mutableMapOf<String, Long>()
            conversationsSynced
                .filter { it.first }
                .collect { (_, list) ->
                    val user = currentUser.value
                    if (!baselineCaptured) {
                        list.forEach { seenLastMillisByStudent[it.studentId] = it.lastMessageAtMillis }
                        baselineCaptured = true
                    } else if (user != null) {
                        list.forEach { conv ->
                            val prevMillis = seenLastMillisByStudent[conv.studentId] ?: 0L
                            if (conv.lastMessageAtMillis > prevMillis) {
                                seenLastMillisByStudent[conv.studentId] = conv.lastMessageAtMillis
                                val isIncomingForStudent = user.roleEnum == UserRole.STUDENT &&
                                    conv.unreadByStudentCount > 0 &&
                                    conv.lastSenderRoleEnum == UserRole.ADMIN
                                val isIncomingForAdmin = user.roleEnum == UserRole.ADMIN &&
                                    conv.unreadByAdminCount > 0 &&
                                    conv.lastSenderRoleEnum == UserRole.STUDENT
                                if (isIncomingForStudent) {
                                    _incomingAlerts.tryEmit(
                                        CommunicationAlertEvent(
                                            id = "msg_${conv.studentId}_${conv.lastMessageAtMillis}",
                                            type = CommunicationAlertType.MESSAGE,
                                            title = "New Reply from Bangali Babu",
                                            body = conv.lastMessageText,
                                            targetStudentId = conv.studentId
                                        )
                                    )
                                } else if (isIncomingForAdmin) {
                                    _incomingAlerts.tryEmit(
                                        CommunicationAlertEvent(
                                            id = "msg_${conv.studentId}_${conv.lastMessageAtMillis}",
                                            type = CommunicationAlertType.MESSAGE,
                                            title = "Message from ${conv.studentName}",
                                            body = conv.lastMessageText,
                                            targetStudentId = conv.studentId
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
        }

        viewModelScope.launch {
            var baselineCaptured = false
            val seenWarningIds = mutableSetOf<String>()
            warningsSynced
                .filter { it.first }
                .collect { (_, list) ->
                    val user = currentUser.value
                    if (!baselineCaptured) {
                        list.forEach { if (it.id.isNotBlank()) seenWarningIds.add(it.id) }
                        baselineCaptured = true
                    } else if (user != null && user.roleEnum == UserRole.STUDENT) {
                        list.forEach { warning ->
                            if (warning.id.isNotBlank() && seenWarningIds.add(warning.id) && !warning.readByStudent) {
                                _incomingAlerts.tryEmit(
                                    CommunicationAlertEvent(
                                        id = "warn_${warning.id}",
                                        type = CommunicationAlertType.WARNING,
                                        title = "${warning.severityEnum.label}: ${warning.title}",
                                        body = warning.message
                                    )
                                )
                            }
                        }
                    }
                }
        }

        viewModelScope.launch {
            var baselineCaptured = false
            val seenAnnouncementIds = mutableSetOf<String>()
            announcementsSynced
                .filter { it.first }
                .collect { (_, list) ->
                    val user = currentUser.value
                    if (!baselineCaptured) {
                        list.forEach { if (it.id.isNotBlank()) seenAnnouncementIds.add(it.id) }
                        baselineCaptured = true
                    } else if (user != null && user.roleEnum == UserRole.STUDENT) {
                        val myId = user.id.ifBlank { uid }
                        list.forEach { ann ->
                            if (ann.id.isNotBlank() &&
                                seenAnnouncementIds.add(ann.id) &&
                                ann.authorId != myId &&
                                ann.effectiveTimestampMillis > user.lastAnnouncementReadMillis
                            ) {
                                _incomingAlerts.tryEmit(
                                    CommunicationAlertEvent(
                                        id = "ann_${ann.id}",
                                        type = CommunicationAlertType.ANNOUNCEMENT,
                                        title = "Announcement: ${ann.title}",
                                        body = ann.message
                                    )
                                )
                            }
                        }
                    }
                }
        }

        viewModelScope.launch {
            var baselineCaptured = false
            val seenSignalUpdatedAt = mutableMapOf<String, Long>()
            tradeSignalsSynced
                .filter { it.first }
                .collect { (_, list) ->
                    val user = currentUser.value
                    val blocked = isOwnSignalAccessBlocked.value
                    if (!baselineCaptured) {
                        list.forEach { seenSignalUpdatedAt[it.id] = it.updatedAtMillis }
                        baselineCaptured = true
                    } else if (
                        user != null &&
                        user.roleEnum == UserRole.STUDENT &&
                        user.statusEnum == AccountStatus.ACTIVE &&
                        !blocked &&
                        user.signalAccessEnum == SignalAccess.ALLOWED
                    ) {
                        list.forEach { sig ->
                            val prevUpdated = seenSignalUpdatedAt[sig.id]
                            if (prevUpdated == null || sig.updatedAtMillis > prevUpdated) {
                                seenSignalUpdatedAt[sig.id] = sig.updatedAtMillis
                                val title = if (prevUpdated == null) {
                                    "New Trade Signal: ${sig.directionEnum.name} ${sig.instrument}"
                                } else {
                                    "Signal Update (${sig.statusEnum.label}): ${sig.instrument}"
                                }
                                val body = buildString {
                                    append("Entry ${sig.entryPrice} | SL ${sig.stopLoss} | T1 ${sig.target1}")
                                    if (sig.target2 != null) append(" | T2 ${sig.target2}")
                                    if (sig.instructions.isNotBlank()) append(" — ${sig.instructions}")
                                }
                                _incomingAlerts.tryEmit(
                                    CommunicationAlertEvent(
                                        id = "sig_${sig.id}_${sig.updatedAtMillis}",
                                        type = CommunicationAlertType.TRADE_SIGNAL,
                                        title = title,
                                        body = body
                                    )
                                )
                            }
                        }
                    }
                }
        }

        viewModelScope.launch {
            var baselineCaptured = false
            var prevIsLive = false
            liveClassroom.collect { room ->
                val nowLive = room?.isLive == true
                val user = currentUser.value
                if (!baselineCaptured) {
                    prevIsLive = nowLive
                    baselineCaptured = true
                } else {
                    if (!prevIsLive && nowLive && user != null && user.roleEnum == UserRole.STUDENT) {
                        _incomingAlerts.tryEmit(
                            CommunicationAlertEvent(
                                id = "live_${room?.startedAtMillis ?: System.currentTimeMillis()}",
                                type = CommunicationAlertType.LIVE_CLASS,
                                title = "LIVE Class Started: ${room?.title ?: "Trading Classroom"}",
                                body = "${room?.hostName ?: "Bangali Babu"} is live now. Tap Live Classroom to join."
                            )
                        )
                    }
                    prevIsLive = nowLive
                }
            }
        }
    }

    private fun observeLiveClassroomModeration() {
        viewModelScope.launch {
            combine(currentUser, liveClassroom, classroomParticipants, rtcState) { user, room, participants, rtc ->
                listOf(user, room, participants, rtc)
            }.collect { values ->
                val user = values[0] as? UserEntity ?: return@collect
                val room = values[1] as? LiveClassroomEntity
                @Suppress("UNCHECKED_CAST")
                val participants = values[2] as List<LiveClassroomParticipantEntity>
                val rtc = values[3] as LiveClassroomRtcState

                val myUid = user.id.ifBlank { uid }
                val me = participants.firstOrNull { it.uid == myUid }
                val isTeacher = user.roleEnum == UserRole.ADMIN

                if (room != null && !room.isLive && (_hasJoinedLiveClass.value || rtc.connectionState == LiveKitConnectionState.CONNECTED)) {
                    _hasJoinedLiveClass.value = false
                    rtcManager.disconnect()
                }

                if (!isTeacher && me?.isRemoved == true) {
                    if (_hasJoinedLiveClass.value || rtc.connectionState == LiveKitConnectionState.CONNECTED) {
                        _hasJoinedLiveClass.value = false
                        rtcManager.disconnect()
                        emitMessage("You were removed from the Live Classroom by the Teacher.")
                    }
                }

                if (!isTeacher && room?.isLive == true && _hasJoinedLiveClass.value && me != null && !me.isRemoved) {
                    if (rtc.connectionState == LiveKitConnectionState.CONNECTED &&
                        rtc.canPublishAudio != me.canSpeakLive
                    ) {
                        rtcManager.connect(
                            roomName = room.roomName,
                            participantUid = myUid,
                            participantName = user.name,
                            isTeacher = false,
                            canPublishAudio = me.canSpeakLive
                        )
                        if (me.canSpeakLive) {
                            emitMessage("Teacher approved your voice! Tap Mic On to ask your question.")
                        }
                    }
                }

                if (!isTeacher && rtc.isMicEnabled && (me == null || !me.canSpeakLive)) {
                    rtcManager.setMicrophoneEnabled(false)
                    repository.updateOwnClassroomAudioControls(user, isSelfMuted = true)
                    emitMessage("Your microphone was muted by the Teacher.")
                }
            }
        }
    }

    fun emitMessage(message: String) {
        _snackbarMessage.tryEmit(message)
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch { repository.setDarkMode(isDark) }
    }

    fun saveTrade(
        existingTradeId: String?,
        tradeDateMillis: Long,
        tradeDateIso: String,
        instrument: String,
        direction: TradeDirection,
        timeframe: Timeframe,
        currency: TradingCurrency,
        usdToInrRate: Double,
        entryPrice: Double,
        exitPrice: Double,
        stopLoss: Double?,
        takeProfit: Double?,
        lotSize: Double,
        riskAmount: Double,
        actualPnl: Double,
        fees: Double = 0.0,
        tradeStatus: TradeStatus = TradeStatus.CLOSED,
        result: TradeResult,
        strategy: String,
        entryReason: String,
        exitReason: String,
        mistakes: String,
        lessonsLearned: String,
        screenshotData: String?,
        notes: String,
        entryTimeMillis: Long? = null,
        exitDateIso: String = "",
        exitTimeMillis: Long? = null,
        tradingSession: String = "",
        exitClosureType: ExitClosureType = ExitClosureType.UNKNOWN,
        timezoneId: String = selectedTimezoneId.value,
        onSaved: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.saveTrade(
                actor = actor,
                existingTradeId = existingTradeId,
                targetUserId = actor.id.ifBlank { uid },
                targetUserName = actor.name,
                tradeDateMillis = tradeDateMillis,
                tradeDateIso = tradeDateIso,
                instrument = instrument,
                direction = direction,
                timeframe = timeframe,
                currency = currency,
                usdToInrRate = usdToInrRate,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                lotSize = lotSize,
                riskAmount = riskAmount,
                actualPnl = actualPnl,
                fees = fees,
                tradeStatus = tradeStatus,
                result = result,
                strategy = strategy,
                entryReason = entryReason,
                exitReason = exitReason,
                mistakes = mistakes,
                lessonsLearned = lessonsLearned,
                screenshotData = screenshotData,
                notes = notes,
                entryTimeMillis = entryTimeMillis,
                exitDateIso = exitDateIso,
                exitTimeMillis = exitTimeMillis,
                tradingSession = tradingSession,
                exitClosureType = exitClosureType,
                timezoneId = timezoneId
            ).onSuccess { saved ->
                emitMessage("${saved.tradeCode} (${saved.currencyEnum.code}) saved to cloud.")
                onSaved()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not save trade.")
            }
        }
    }

    fun deleteTrade(tradeId: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteTrade(actor, tradeId)
                .onSuccess { emitMessage("Trade deleted.") }
                .onFailure { emitMessage(it.message ?: "Failed to delete trade.") }
        }
    }

    suspend fun encodeScreenshot(uri: Uri): String? {
        return repository.encodeScreenshotToBase64(uri)
            .onFailure { emitMessage(it.message ?: "Failed to process screenshot.") }
            .getOrNull()
    }

    fun updateOwnDisciplineGoals(
        preferredCurrency: TradingCurrency,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        slLookbackCount: Int = currentUser.value?.effectiveSlLookbackCount ?: 10,
        slAlertThreshold: Int = currentUser.value?.effectiveSlAlertThreshold ?: 10,
        strategySlThreshold: Int = currentUser.value?.effectiveStrategySlThreshold ?: 5
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateOwnDisciplineGoals(
                actor = actor,
                preferredCurrency = preferredCurrency,
                dailyGoal = dailyGoal,
                dailyMaxLoss = dailyMaxLoss,
                maxTradesPerDay = maxTradesPerDay,
                maxRiskPerTrade = maxRiskPerTrade,
                slLookbackCount = slLookbackCount,
                slAlertThreshold = slAlertThreshold,
                strategySlThreshold = strategySlThreshold
            )
                .onSuccess { emitMessage("Discipline targets & smart alert thresholds (${preferredCurrency.code}) updated.") }
                .onFailure { emitMessage(it.message ?: "Update failed.") }
        }
    }

    fun acknowledgeSmartAlert(alert: SmartDisciplineAlertEntity, breakDays: Int = 0) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.acknowledgeSmartAlert(actor, alert, breakDays)
                .onSuccess {
                    val confirmMsg = when (alert.alertTypeEnum) {
                        SmartAlertType.WEEKLY_LOSS_LIMIT ->
                            "Weekly loss alert acknowledged. Step away and protect your capital."
                        SmartAlertType.STOP_LOSS_STREAK ->
                            "Discipline alert acknowledged. Take a mental reset before your next trade."
                        SmartAlertType.STRATEGY_LOSS_LIMIT ->
                            "Strategy warning acknowledged. Review ${alert.strategyName.ifBlank { "your setup" }} before resuming."
                    }
                    emitMessage(confirmMsg)
                }
                .onFailure { emitMessage(it.message ?: "Could not acknowledge alert.") }
        }
    }

    fun markWeeklyReportRead(report: WeeklyReportEntity) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.markWeeklyReportRead(actor, report)
        }
    }

    fun saveCurrentWeekReportNow() {
        val actor = currentUser.value ?: return
        val liveReport = liveCurrentWeekReport.value
        if (liveReport == null) {
            emitMessage("Log at least one trade this week to generate a Weekly Performance Report.")
            return
        }
        viewModelScope.launch {
            repository.saveWeeklyReports(actor, listOf(liveReport), overwriteExisting = true)
                .onSuccess { emitMessage("Weekly Performance Report (${liveReport.weekKey}) saved.") }
                .onFailure { emitMessage(it.message ?: "Could not save weekly report.") }
        }
    }

    fun syncStudentDisciplineData(student: UserEntity, studentTrades: List<TradeEntity>) {
        val actor = currentUser.value ?: return
        if (actor.roleEnum != UserRole.ADMIN) return
        val sid = student.id.ifBlank { student.userId }
        val existingAlerts = allPlatformSmartAlerts.value.filter { it.userId == sid }
        val existingReports = allPlatformWeeklyReports.value.filter { it.userId == sid }
        val dispCurr = displayCurrency.value
        val fxRate = liveUsdToInrRate.value
        viewModelScope.launch {
            val eval = SmartDisciplineEngine.evaluateSmartAlerts(
                user = student,
                trades = studentTrades,
                existingAlerts = existingAlerts,
                liveUsdToInrRate = fxRate
            )
            if (eval.newAlertsToCreate.isNotEmpty() || eval.existingAlertsToUpdate.isNotEmpty()) {
                repository.syncSmartAlertsForUser(actor, student, eval)
            }
            if (studentTrades.isNotEmpty()) {
                val missing = SmartDisciplineEngine.buildMissingCompletedWeeklyReports(
                    user = student,
                    allUserTrades = studentTrades,
                    existingReports = existingReports,
                    displayCurrency = dispCurr
                )
                if (missing.isNotEmpty()) {
                    repository.saveWeeklyReports(actor, missing, overwriteExisting = false)
                }
            }
        }
    }

    fun saveDisciplineReview(
        periodType: ReviewPeriod,
        periodKey: String,
        emotionState: EmotionState,
        psychologyNotes: String,
        slAdherenceRating: Int,
        lessonsSummary: String,
        nextActionPlan: String
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.saveDisciplineReview(
                actor = actor,
                targetUserId = actor.id.ifBlank { uid },
                periodType = periodType,
                periodKey = periodKey,
                emotionState = emotionState,
                psychologyNotes = psychologyNotes,
                slAdherenceRating = slAdherenceRating,
                lessonsSummary = lessonsSummary,
                nextActionPlan = nextActionPlan
            ).onSuccess {
                emitMessage("${periodType.label} review saved.")
            }.onFailure {
                emitMessage(it.message ?: "Failed to save review.")
            }
        }
    }

    // --- ADMIN STUDENT & INVITE MANAGEMENT ---
    fun createStudentInvite(
        name: String,
        email: String,
        preferredCurrency: TradingCurrency,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        onSuccess: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.createStudentInviteByAdmin(
                actor, name, email, preferredCurrency, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade
            ).onSuccess {
                emitMessage("Invite created for ${it.email}.")
                onSuccess()
            }.onFailure {
                emitMessage(it.message ?: "Failed to create invite.")
            }
        }
    }

    fun updateStudentInvite(
        invite: StudentInvite,
        newName: String,
        newEmail: String,
        preferredCurrency: TradingCurrency,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        onSuccess: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateStudentInviteByAdmin(
                actor, invite, newName, newEmail, preferredCurrency, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade
            ).onSuccess {
                emitMessage("Student invite updated.")
                onSuccess()
            }.onFailure {
                emitMessage(it.message ?: "Failed to update invite.")
            }
        }
    }

    fun deleteStudentInvite(inviteId: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteStudentInviteByAdmin(actor, inviteId)
                .onSuccess { emitMessage("Invite deleted.") }
                .onFailure { emitMessage(it.message ?: "Delete failed.") }
        }
    }

    fun updateStudent(
        student: UserEntity,
        newName: String,
        newEmail: String,
        preferredCurrency: TradingCurrency,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        onSuccess: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateStudentByAdmin(
                actor, student, newName, newEmail, preferredCurrency, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade
            ).onSuccess {
                emitMessage("Student profile updated.")
                onSuccess()
            }.onFailure {
                emitMessage(it.message ?: "Failed to update student.")
            }
        }
    }

    fun toggleStudentSuspension(student: UserEntity, suspended: Boolean) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.setStudentSuspended(actor, student, suspended)
                .onSuccess {
                    emitMessage(if (suspended) "${student.name} suspended." else "${student.name} reactivated.")
                }
                .onFailure { emitMessage(it.message ?: "Action failed.") }
        }
    }

    fun deleteStudent(studentId: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteStudentByAdmin(actor, studentId)
                .onSuccess { emitMessage("Student account removed.") }
                .onFailure { emitMessage(it.message ?: "Delete failed.") }
        }
    }

    fun observeStudentReviews(studentId: String): Flow<List<DisciplineReviewEntity>> = currentUser.flatMapLatest { actor ->
        if (actor == null) flowOf(emptyList())
        else repository.observeReviewsForUser(actor, studentId).catch { error ->
            extractRootFirestoreError(error)
            emit(emptyList())
        }
    }

    suspend fun exportBackupJson(): Result<String> {
        val actor = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
        return repository.exportBackupJson(actor)
    }

    fun restoreBackupJson(json: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.restoreBackupJson(actor, json)
                .onSuccess { count -> emitMessage("Cloud backup restored ($count trades synced).") }
                .onFailure { emitMessage(it.message ?: "Backup restore failed.") }
        }
    }

    // --- COMMUNICATION ACTIONS ---

    fun observeDirectMessages(studentUid: String): Flow<List<DirectMessageEntity>> =
        currentUser.flatMapLatest { actor ->
            if (actor == null) flowOf(emptyList())
            else repository.observeDirectMessages(actor, studentUid).catch { error ->
                extractRootFirestoreError(error)
                emit(emptyList())
            }
        }

    fun sendDirectMessageToAdmin(messageBody: String, onSuccess: () -> Unit = {}) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.sendDirectMessage(actor = actor, targetStudent = actor, messageBody = messageBody)
                .onSuccess { onSuccess() }
                .onFailure { emitMessage(it.message ?: "Could not send message.") }
        }
    }

    fun sendDirectMessageToStudent(student: UserEntity, messageBody: String, onSuccess: () -> Unit = {}) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.sendDirectMessage(actor = actor, targetStudent = student, messageBody = messageBody)
                .onSuccess { onSuccess() }
                .onFailure { emitMessage(it.message ?: "Could not send reply.") }
        }
    }

    fun markConversationRead(studentUid: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.markConversationRead(actor, studentUid)
        }
    }

    fun sendWarning(
        student: UserEntity,
        severity: WarningSeverity,
        title: String,
        message: String,
        onSuccess: () -> Unit = {}
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.sendWarningByAdmin(actor, student, severity, title, message)
                .onSuccess {
                    emitMessage("Warning sent to ${student.name}.")
                    onSuccess()
                }
                .onFailure { emitMessage(it.message ?: "Failed to send warning.") }
        }
    }

    fun markWarningRead(warning: StudentWarningEntity) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.markWarningReadByStudent(actor, warning)
                .onFailure { emitMessage(it.message ?: "Unable to acknowledge warning.") }
        }
    }

    fun deleteWarning(warningId: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteWarningByAdmin(actor, warningId)
                .onSuccess { emitMessage("Warning removed.") }
                .onFailure { emitMessage(it.message ?: "Failed to remove warning.") }
        }
    }

    fun publishAnnouncement(
        title: String,
        message: String,
        priority: AnnouncementPriority,
        onSuccess: () -> Unit = {}
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.publishAnnouncementByAdmin(actor, title, message, priority)
                .onSuccess {
                    emitMessage("Announcement broadcast to all students.")
                    onSuccess()
                }
                .onFailure { emitMessage(it.message ?: "Failed to publish announcement.") }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteAnnouncementByAdmin(actor, announcementId)
                .onSuccess { emitMessage("Announcement deleted.") }
                .onFailure { emitMessage(it.message ?: "Failed to delete announcement.") }
        }
    }

    fun markAllAnnouncementsRead() {
        val actor = currentUser.value ?: return
        val latest = announcements.value.maxOfOrNull { it.effectiveTimestampMillis } ?: return
        viewModelScope.launch {
            repository.markAnnouncementsRead(actor, latest)
        }
    }

    // --- STUDENT PROFILE & PHOTO ACTIONS ---

    fun saveStudentProfile(
        targetUser: UserEntity,
        fullName: String,
        mobileNumber: String,
        whatsappNumber: String,
        dateOfBirth: String,
        gender: String,
        fullAddress: String,
        editedEmail: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        val actor = currentUser.value ?: return
        if (_isUpdatingProfile.value) return
        viewModelScope.launch {
            _isUpdatingProfile.value = true
            repository.updateStudentProfileDetails(
                actor = actor,
                targetUser = targetUser,
                fullName = fullName,
                mobileNumber = mobileNumber,
                whatsappNumber = whatsappNumber,
                dateOfBirth = dateOfBirth,
                gender = gender,
                fullAddress = fullAddress,
                editedEmail = editedEmail
            ).onSuccess {
                emitMessage("Profile details saved to Cloud Firestore.")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Failed to save profile details.")
            }
            _isUpdatingProfile.value = false
        }
    }

    fun uploadProfilePhoto(targetUser: UserEntity, imageUri: Uri) {
        val actor = currentUser.value ?: return
        if (_isUploadingPhoto.value) return
        viewModelScope.launch {
            _isUploadingPhoto.value = true
            repository.uploadStudentProfilePhoto(actor, targetUser, imageUri)
                .onSuccess { emitMessage("Profile photo uploaded to Firebase Storage.") }
                .onFailure { err ->
                    emitMessage(err.message ?: "Failed to upload profile photo. Check Firebase Storage configuration.")
                }
            _isUploadingPhoto.value = false
        }
    }

    fun deleteProfilePhoto(targetUser: UserEntity) {
        val actor = currentUser.value ?: return
        if (_isUploadingPhoto.value) return
        viewModelScope.launch {
            _isUploadingPhoto.value = true
            repository.deleteStudentProfilePhoto(actor, targetUser)
                .onSuccess { emitMessage("Profile photo removed.") }
                .onFailure { err ->
                    emitMessage(err.message ?: "Failed to remove profile photo.")
                }
            _isUploadingPhoto.value = false
        }
    }

    // --- ADMIN TRADE DESK & SIGNAL ACTIONS ---

    fun setStudentSignalAccess(student: UserEntity, access: SignalAccess) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.setStudentSignalAccess(actor, student, access)
                .onSuccess {
                    val label = if (access == SignalAccess.BLOCKED) {
                        "${student.name} blocked from Admin Trade Signals (Journal & Analytics remain active)."
                    } else {
                        "${student.name} unblocked — Trade Signal access restored."
                    }
                    emitMessage(label)
                }
                .onFailure { emitMessage(it.message ?: "Failed to update signal access.") }
        }
    }

    fun publishOrUpdateTradeSignal(
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
    ) {
        val actor = currentUser.value ?: return
        if (_isSavingSignal.value) return
        viewModelScope.launch {
            _isSavingSignal.value = true
            repository.publishOrUpdateTradeSignal(
                actor = actor,
                existingSignal = existingSignal,
                instrument = instrument,
                direction = direction,
                entryPrice = entryPrice,
                stopLoss = stopLoss,
                target1 = target1,
                target2 = target2,
                strategyName = strategyName,
                instructions = instructions,
                status = status,
                audienceType = audienceType,
                selectedStudentIds = selectedStudentIds,
                keptScreenshotUrls = keptScreenshotUrls,
                keptScreenshotPaths = keptScreenshotPaths,
                newScreenshotUris = newScreenshotUris,
                removedScreenshotPaths = removedScreenshotPaths,
                existingVoiceUrl = existingVoiceUrl,
                existingVoicePath = existingVoicePath,
                existingVoiceDurationSec = existingVoiceDurationSec,
                newVoiceFile = newVoiceFile,
                newVoiceDurationSec = newVoiceDurationSec,
                removeExistingVoice = removeExistingVoice
            ).onSuccess { saved ->
                emitMessage("${saved.directionEnum.name} ${saved.instrument} signal published.")
                onSaved()
            }.onFailure { err ->
                emitMessage(err.message ?: "Failed to save trade signal.")
            }
            _isSavingSignal.value = false
        }
    }

    fun updateTradeSignalStatus(signal: TradeSignalEntity, newStatus: TradeSignalStatus) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateTradeSignalStatus(actor, signal, newStatus)
                .onSuccess { emitMessage("${signal.instrument} marked ${newStatus.label}.") }
                .onFailure { emitMessage(it.message ?: "Failed to update signal status.") }
        }
    }

    fun deleteTradeSignal(signal: TradeSignalEntity) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            if (voicePlaybackState.value.activeUrl == signal.voiceNoteUrl) {
                voicePlayer.stop()
            }
            repository.deleteTradeSignal(actor, signal)
                .onSuccess { emitMessage("${signal.instrument} signal deleted.") }
                .onFailure { emitMessage(it.message ?: "Failed to delete signal.") }
        }
    }

    fun toggleTradeSignalReaction(signal: TradeSignalEntity, emoji: String) {
        val actor = currentUser.value ?: return
        val myUid = actor.id.ifBlank { uid }
        val existing = signalReactionsById.value[signal.id].orEmpty().firstOrNull { it.userId == myUid }
        viewModelScope.launch {
            repository.toggleTradeSignalReaction(actor, signal, emoji, existing)
                .onFailure { emitMessage(it.message ?: "Could not update reaction.") }
        }
    }

    fun toggleSignalVoicePlay(url: String, fallbackDurationSec: Int) {
        voicePlayer.togglePlay(url, fallbackDurationSec)
    }

    fun seekSignalVoice(seconds: Int) {
        voicePlayer.seekTo(seconds)
    }

    // --- LIVE ONLINE CLASSROOM ACTIONS ---

    fun startLiveClass(title: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.startLiveClassByAdmin(actor, title)
                .onSuccess { room ->
                    _hasJoinedLiveClass.value = true
                    rtcManager.connect(
                        roomName = room.roomName,
                        participantUid = actor.id.ifBlank { uid },
                        participantName = actor.name,
                        isTeacher = true,
                        canPublishAudio = true
                    )
                    emitMessage("Live Class is now LIVE!")
                }
                .onFailure { emitMessage(it.message ?: "Could not start Live Class.") }
        }
    }

    fun endLiveClass() {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            rtcManager.disconnect()
            _hasJoinedLiveClass.value = false
            repository.endLiveClassByAdmin(actor)
                .onSuccess { emitMessage("Live Class ended.") }
                .onFailure { emitMessage(it.message ?: "Failed to end Live Class.") }
        }
    }

    fun joinLiveClass() {
        val actor = currentUser.value ?: return
        val room = liveClassroom.value
        if (room == null || !room.isLive) {
            emitMessage("No Live Class is currently active.")
            return
        }
        viewModelScope.launch {
            repository.joinLiveClass(actor, room.roomName)
                .onSuccess { participant ->
                    _hasJoinedLiveClass.value = true
                    rtcManager.connect(
                        roomName = room.roomName,
                        participantUid = actor.id.ifBlank { uid },
                        participantName = actor.name,
                        isTeacher = actor.roleEnum == UserRole.ADMIN,
                        canPublishAudio = participant.canSpeakLive
                    )
                }
                .onFailure { emitMessage(it.message ?: "Unable to join Live Class.") }
        }
    }

    fun leaveLiveClass() {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            rtcManager.disconnect()
            _hasJoinedLiveClass.value = false
            repository.leaveLiveClass(actor)
        }
    }

    fun startTeacherScreenShare(permissionIntentData: Intent) {
        val actor = currentUser.value ?: return
        if (actor.roleEnum != UserRole.ADMIN) return
        if (!LiveKitTokenGenerator.isConfigured()) {
            emitMessage("Screen sharing requires LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET in AI Studio Secrets.")
            return
        }
        viewModelScope.launch {
            val roomName = liveClassroom.value?.roomName ?: LiveClassroomEntity.DEFAULT_ROOM_ID
            if (rtcState.value.connectionState != LiveKitConnectionState.CONNECTED) {
                rtcManager.connect(
                    roomName = roomName,
                    participantUid = actor.id.ifBlank { uid },
                    participantName = actor.name,
                    isTeacher = true,
                    canPublishAudio = true
                )
            }
            rtcManager.startScreenShare(permissionIntentData)
                .onSuccess {
                    repository.setScreenSharingStateByAdmin(actor, true, roomName)
                    emitMessage("Screen sharing started for Live Classroom.")
                }
                .onFailure { err ->
                    emitMessage(err.message ?: "Unable to start screen sharing.")
                }
        }
    }

    fun stopTeacherScreenShare() {
        val actor = currentUser.value ?: return
        if (actor.roleEnum != UserRole.ADMIN) return
        viewModelScope.launch {
            rtcManager.stopScreenShare()
            repository.setScreenSharingStateByAdmin(actor, false)
            emitMessage("Screen sharing stopped.")
        }
    }

    fun toggleTeacherCamera(enable: Boolean) {
        val actor = currentUser.value ?: return
        if (actor.roleEnum != UserRole.ADMIN) return
        if (!LiveKitTokenGenerator.isConfigured()) {
            emitMessage("Camera video requires LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET in AI Studio Secrets.")
            return
        }
        viewModelScope.launch {
            val roomName = liveClassroom.value?.roomName ?: LiveClassroomEntity.DEFAULT_ROOM_ID
            if (rtcState.value.connectionState != LiveKitConnectionState.CONNECTED) {
                rtcManager.connect(
                    roomName = roomName,
                    participantUid = actor.id.ifBlank { uid },
                    participantName = actor.name,
                    isTeacher = true,
                    canPublishAudio = true
                )
            }
            rtcManager.setCameraEnabled(enable)
                .onSuccess { active ->
                    emitMessage(if (active) "Teacher camera enabled." else "Teacher camera turned off.")
                }
                .onFailure { err ->
                    emitMessage(err.message ?: "Unable to toggle Teacher camera.")
                }
        }
    }

    fun toggleRaiseHand(raised: Boolean) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateOwnClassroomAudioControls(actor, handRaised = raised)
                .onFailure { emitMessage(it.message ?: "Unable to update raise hand status.") }
        }
    }

    fun toggleClassroomMic(enable: Boolean) {
        val actor = currentUser.value ?: return
        val myUid = actor.id.ifBlank { uid }
        val me = classroomParticipants.value.firstOrNull { it.uid == myUid }
        val isTeacher = actor.roleEnum == UserRole.ADMIN
        if (!isTeacher && enable && (me == null || !me.canSpeakLive)) {
            emitMessage("Raise hand and wait for Teacher approval before speaking.")
            return
        }
        if (enable && !LiveKitTokenGenerator.isConfigured()) {
            emitMessage("Live voice requires LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET in AI Studio Secrets.")
            return
        }
        viewModelScope.launch {
            val room = liveClassroom.value
            if (enable && rtcState.value.connectionState != LiveKitConnectionState.CONNECTED && room?.isLive == true) {
                rtcManager.connect(
                    roomName = room.roomName,
                    participantUid = myUid,
                    participantName = actor.name,
                    isTeacher = isTeacher,
                    canPublishAudio = isTeacher || (me?.canSpeakLive == true)
                )
            }
            if (rtcState.value.connectionState != LiveKitConnectionState.CONNECTED) {
                if (enable) {
                    emitMessage(rtcState.value.noticeMessage ?: "Real-time audio server is not connected.")
                } else {
                    repository.updateOwnClassroomAudioControls(actor, isSelfMuted = true)
                }
                return@launch
            }
            rtcManager.setMicrophoneEnabled(enable)
                .onSuccess { actual ->
                    repository.updateOwnClassroomAudioControls(actor, isSelfMuted = !actual)
                }
                .onFailure { err ->
                    emitMessage(err.message ?: "Microphone toggle failed.")
                    repository.updateOwnClassroomAudioControls(actor, isSelfMuted = true)
                }
        }
    }

    fun moderateClassroomParticipant(
        participantUid: String,
        voiceApproved: Boolean? = null,
        isMutedByTeacher: Boolean? = null,
        handRaised: Boolean? = null,
        isRemoved: Boolean? = null
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.moderateClassroomParticipantByAdmin(
                actor = actor,
                participantUid = participantUid,
                voiceApproved = voiceApproved,
                isMutedByTeacher = isMutedByTeacher,
                handRaised = handRaised,
                isRemoved = isRemoved
            ).onFailure { emitMessage(it.message ?: "Moderation action failed.") }
        }
    }

    fun sendClassroomMessage(body: String, onSuccess: () -> Unit = {}) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.sendClassroomChatMessage(actor, body)
                .onSuccess { onSuccess() }
                .onFailure { emitMessage(it.message ?: "Could not send message.") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voicePlayer.release()
        rtcManager.release()
    }

    companion object {
        fun provideFactory(
            application: Application,
            db: FirebaseFirestore,
            uid: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TradingViewModel(application, db, uid) as T
            }
        }
    }
}
