package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionPreferences
import com.example.data.model.AccountStatus
import com.example.data.model.DisciplineReviewEntity
import com.example.data.model.EmotionState
import com.example.data.model.ReviewPeriod
import com.example.data.model.StudentInvite
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.repository.TradingRepository
import com.example.domain.DashboardMetrics
import com.example.domain.DisciplineMetrics
import com.example.domain.StudentPerformanceSummary
import com.example.domain.TradingAnalytics
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

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

    init {
        bootstrapProfile()
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

    val personalMetrics: StateFlow<DashboardMetrics> = personalTrades
        .map { TradingAnalytics.calculateDashboardMetrics(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardMetrics())

    val personalDiscipline: StateFlow<DisciplineMetrics> = combine(personalTrades, currentUser) { trades, user ->
        if (user == null) DisciplineMetrics()
        else TradingAnalytics.calculateDisciplineMetrics(trades, user)
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
        allPlatformTrades
    ) { studentList, allTrades ->
        val byUser = allTrades.groupBy { it.userId }
        studentList.map { student ->
            val stTrades = byUser[student.id].orEmpty()
            StudentPerformanceSummary(
                student = student,
                metrics = TradingAnalytics.calculateDashboardMetrics(stTrades),
                discipline = TradingAnalytics.calculateDisciplineMetrics(stTrades, student)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val combinedStudentsMetrics: StateFlow<DashboardMetrics> = combine(students, allPlatformTrades) { studentList, allTrades ->
        val studentIds = studentList.map { it.id }.toSet()
        val onlyStudentTrades = allTrades.filter { it.userId in studentIds }
        TradingAnalytics.calculateDashboardMetrics(onlyStudentTrades)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardMetrics())

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
        entryPrice: Double,
        exitPrice: Double,
        stopLoss: Double?,
        takeProfit: Double?,
        lotSize: Double,
        riskAmount: Double,
        actualPnl: Double,
        result: TradeResult,
        strategy: String,
        entryReason: String,
        exitReason: String,
        mistakes: String,
        lessonsLearned: String,
        screenshotData: String?,
        notes: String,
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
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                lotSize = lotSize,
                riskAmount = riskAmount,
                actualPnl = actualPnl,
                result = result,
                strategy = strategy,
                entryReason = entryReason,
                exitReason = exitReason,
                mistakes = mistakes,
                lessonsLearned = lessonsLearned,
                screenshotData = screenshotData,
                notes = notes
            ).onSuccess { saved ->
                emitMessage("${saved.tradeCode} saved to cloud.")
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
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateOwnDisciplineGoals(actor, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade)
                .onSuccess { emitMessage("Discipline targets updated.") }
                .onFailure { emitMessage(it.message ?: "Update failed.") }
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
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        onSuccess: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.createStudentInviteByAdmin(
                actor, name, email, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade
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
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        onSuccess: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateStudentInviteByAdmin(
                actor, invite, newName, newEmail, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade
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
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        onSuccess: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateStudentByAdmin(
                actor, student, newName, newEmail, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade
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
