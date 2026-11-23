package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SessionPreferences
import com.example.data.model.DisciplineReviewEntity
import com.example.data.model.EmotionState
import com.example.data.model.ReviewPeriod
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TradingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val sessionPreferences = SessionPreferences(application)
    val repository = TradingRepository(application, database, sessionPreferences)

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthBusy = MutableStateFlow(false)
    val isAuthBusy: StateFlow<Boolean> = _isAuthBusy.asStateFlow()

    val isDarkMode: StateFlow<Boolean> = repository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hasAdmin: StateFlow<Boolean?> = repository.hasAdmin
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentUser: StateFlow<UserEntity?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val personalTrades: StateFlow<List<TradeEntity>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else repository.observeTradesForUser(user, user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalMetrics: StateFlow<DashboardMetrics> = personalTrades
        .map { TradingAnalytics.calculateDashboardMetrics(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    val personalDiscipline: StateFlow<DisciplineMetrics> = combine(personalTrades, currentUser) { trades, user ->
        if (user == null) DisciplineMetrics()
        else TradingAnalytics.calculateDisciplineMetrics(trades, user)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DisciplineMetrics())

    val personalReviews: StateFlow<List<DisciplineReviewEntity>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else repository.observeReviewsForUser(user, user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin-only reactive streams
    val students: StateFlow<List<UserEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.role == UserRole.ADMIN) repository.observeStudents(user)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlatformTrades: StateFlow<List<TradeEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null && user.role == UserRole.ADMIN) repository.observeAllTrades(user)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val combinedStudentsMetrics: StateFlow<DashboardMetrics> = combine(students, allPlatformTrades) { studentList, allTrades ->
        val studentIds = studentList.map { it.id }.toSet()
        val onlyStudentTrades = allTrades.filter { it.userId in studentIds }
        TradingAnalytics.calculateDashboardMetrics(onlyStudentTrades)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    fun clearAuthError() {
        _authError.value = null
    }

    fun emitMessage(message: String) {
        _snackbarMessage.tryEmit(message)
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch { repository.setDarkMode(isDark) }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isAuthBusy.value = true
            _authError.value = null
            repository.login(email, password)
                .onFailure { _authError.value = it.message ?: "Sign in failed." }
            _isAuthBusy.value = false
        }
    }

    fun registerInitialAdmin(name: String, email: String, password: String) {
        viewModelScope.launch {
            _isAuthBusy.value = true
            _authError.value = null
            repository.registerInitialAdmin(name, email, password)
                .onSuccess { emitMessage("Admin account created. Welcome, ${it.name}!") }
                .onFailure { _authError.value = it.message ?: "Admin setup failed." }
            _isAuthBusy.value = false
        }
    }

    fun registerStudent(name: String, email: String, password: String) {
        viewModelScope.launch {
            _isAuthBusy.value = true
            _authError.value = null
            repository.registerStudent(name, email, password)
                .onSuccess { emitMessage("Student account created. Welcome, ${it.name}!") }
                .onFailure { _authError.value = it.message ?: "Registration failed." }
            _isAuthBusy.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _authError.value = null
        }
    }

    fun saveTrade(
        existingTradeId: Long?,
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
        screenshotPath: String?,
        notes: String,
        onSaved: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.saveTrade(
                actor = actor,
                existingTradeId = existingTradeId,
                targetUserId = actor.id,
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
                screenshotPath = screenshotPath,
                notes = notes
            ).onSuccess { saved ->
                emitMessage("${saved.tradeCode} saved successfully.")
                onSaved()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not save trade.")
            }
        }
    }

    fun deleteTrade(tradeId: Long) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteTrade(actor, tradeId)
                .onSuccess { emitMessage("Trade deleted.") }
                .onFailure { emitMessage(it.message ?: "Failed to delete trade.") }
        }
    }

    suspend fun copyScreenshot(uri: Uri): String? {
        return repository.copyScreenshotToAppStorage(uri)
            .onFailure { emitMessage(it.message ?: "Failed to attach screenshot.") }
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
                targetUserId = actor.id,
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

    // --- ADMIN STUDENT MANAGEMENT ---
    fun createStudent(
        name: String,
        email: String,
        password: String,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        onSuccess: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.createStudentByAdmin(
                actor, name, email, password, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade
            ).onSuccess {
                emitMessage("Student ${it.name} created.")
                onSuccess()
            }.onFailure {
                emitMessage(it.message ?: "Failed to create student.")
            }
        }
    }

    fun updateStudent(
        student: UserEntity,
        newName: String,
        newEmail: String,
        newPassword: String?,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        onSuccess: () -> Unit
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateStudentByAdmin(
                actor, student, newName, newEmail, newPassword, dailyGoal, dailyMaxLoss, maxTradesPerDay, maxRiskPerTrade
            ).onSuccess {
                emitMessage("Student account updated.")
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

    fun deleteStudent(studentId: Long) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteStudentByAdmin(actor, studentId)
                .onSuccess { emitMessage("Student account removed.") }
                .onFailure { emitMessage(it.message ?: "Delete failed.") }
        }
    }

    fun observeStudentReviews(studentId: Long) = currentUser.flatMapLatest { actor ->
        if (actor == null) flowOf(emptyList())
        else repository.observeReviewsForUser(actor, studentId)
    }

    suspend fun exportBackupJson(): Result<String> {
        val actor = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
        return repository.exportBackupJson(actor)
    }

    fun restoreBackupJson(json: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.restoreBackupJson(actor, json)
                .onSuccess { count -> emitMessage("Backup restored ($count trades synced).") }
                .onFailure { emitMessage(it.message ?: "Backup restore failed.") }
        }
    }
}
