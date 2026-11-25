package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
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
import com.example.data.remote.CurrencyRateService
import com.example.data.remote.ExchangeRateQuote
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.domain.SmartAlertEvaluationResult
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class TradingRepository(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val sessionPreferences: SessionPreferences,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val storage by lazy { Firebase.storage }

    companion object {
        const val BOOTSTRAP_ADMIN_EMAIL = "tradingwithbengalibabu@gmail.com"
        private const val MAX_SCREENSHOT_BASE64_LENGTH = 320_000
        val ALLOWED_GENDERS = setOf("Male", "Female", "Other", "Prefer not to say")
    }

    val isDarkMode: Flow<Boolean> = sessionPreferences.isDarkMode
    val displayCurrency: Flow<TradingCurrency> = sessionPreferences.displayCurrency
    val cachedUsdToInrRate: Flow<Double> = sessionPreferences.cachedUsdToInrRate
    val cachedRateUpdatedAtMillis: Flow<Long> = sessionPreferences.cachedRateUpdatedAtMillis
    val selectedTimezoneId: Flow<String> = sessionPreferences.selectedTimezoneId
    val customTradingSessions: Flow<List<CustomTradingSession>> = sessionPreferences.customTradingSessions

    suspend fun setDarkMode(isDark: Boolean) {
        sessionPreferences.setDarkMode(isDark)
    }

    suspend fun setDisplayCurrency(currency: TradingCurrency) {
        sessionPreferences.setDisplayCurrency(currency)
    }

    suspend fun setTimezoneId(timezoneId: String) {
        sessionPreferences.setTimezoneId(timezoneId)
    }

    suspend fun saveCustomTradingSessions(sessions: List<CustomTradingSession>) {
        sessionPreferences.saveCustomTradingSessions(sessions)
    }

    suspend fun resetDefaultTradingSessions() {
        sessionPreferences.resetDefaultTradingSessions()
    }

    suspend fun refreshExchangeRate(): Result<ExchangeRateQuote> {
        return CurrencyRateService.fetchLiveUsdToInr().onSuccess { quote ->
            sessionPreferences.saveCachedUsdToInrRate(quote.usdToInr, quote.fetchedAtMillis)
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun isValidDocId(id: String): Boolean {
        return id.length in 1..128 && id.matches(Regex("^[a-zA-Z0-9_\\-]+$"))
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    suspend fun ensureUserProfile(firebaseUser: FirebaseUser): Result<UserEntity> {
        val uid = firebaseUser.uid
        val docPath = "users/$uid"
        return try {
            val cleanEmail = (firebaseUser.email ?: "").trim().lowercase(Locale.US)
            require(cleanEmail.length in 3..120) { "Verified Google email address is required." }

            val isAdminAccount = cleanEmail == BOOTSTRAP_ADMIN_EMAIL && firebaseUser.isEmailVerified
            val userDocRef = db.collection("users").document(uid)
            val existingSnap = userDocRef.get().await()

            if (isAdminAccount) {
                val adminRef = db.collection("admins").document(uid)
                val adminSnap = adminRef.get().await()
                if (!adminSnap.exists()) {
                    adminRef.set(
                        mapOf(
                            "uid" to uid,
                            "email" to cleanEmail,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(
                    UserEntity::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Unable to read user profile.")
                Result.success(existing)
            } else {
                var initialName = (firebaseUser.displayName ?: "").trim()
                    .ifEmpty { cleanEmail.substringBefore("@").ifEmpty { "Trader" } }
                    .take(80)
                var preferredCurrency = TradingCurrency.USD.name
                var dailyGoal = if (isAdminAccount) 500.0 else 250.0
                var dailyLoss = if (isAdminAccount) 200.0 else 100.0
                var maxTrades = if (isAdminAccount) 6 else 5
                var maxRisk = if (isAdminAccount) 100.0 else 50.0

                if (!isAdminAccount && firebaseUser.isEmailVerified) {
                    val invite = runCatching {
                        db.collection("student_invites")
                            .whereEqualTo("email", cleanEmail)
                            .get()
                            .await()
                            .toObjects(
                                StudentInvite::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                            .firstOrNull()
                    }.getOrNull()

                    if (invite != null) {
                        if (invite.statusEnum == AccountStatus.SUSPENDED) {
                            throw SecurityException("Your invitation has been suspended by the Admin.")
                        }
                        if (invite.name.isNotBlank()) initialName = invite.name.take(80)
                        preferredCurrency = invite.preferredCurrencyEnum.name
                        dailyGoal = invite.dailyProfitGoal.coerceAtLeast(0.0)
                        dailyLoss = invite.dailyMaxLossLimit.coerceAtLeast(0.0)
                        maxTrades = invite.maxTradesPerDay.coerceIn(1, 100)
                        maxRisk = invite.maxRiskPerTrade.coerceAtLeast(0.0)
                    }
                }

                val role = if (isAdminAccount) UserRole.ADMIN.name else UserRole.STUDENT.name
                val payload = mapOf(
                    "userId" to uid,
                    "name" to initialName,
                    "email" to cleanEmail,
                    "role" to role,
                    "status" to AccountStatus.ACTIVE.name,
                    "preferredCurrency" to preferredCurrency,
                    "dailyProfitGoal" to dailyGoal,
                    "dailyMaxLossLimit" to dailyLoss,
                    "maxTradesPerDay" to maxTrades,
                    "maxRiskPerTrade" to maxRisk,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(payload).await()
                val createdSnap = userDocRef.get().await()
                val created = createdSnap.toObject(
                    UserEntity::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserEntity(
                    id = uid,
                    userId = uid,
                    name = initialName,
                    email = cleanEmail,
                    role = role,
                    status = AccountStatus.ACTIVE.name,
                    preferredCurrency = preferredCurrency,
                    dailyProfitGoal = dailyGoal,
                    dailyMaxLossLimit = dailyLoss,
                    maxTradesPerDay = maxTrades,
                    maxRiskPerTrade = maxRisk
                )
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeCurrentUserProfile(uid: String): Flow<UserEntity?> {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return flowOf(null)
        val path = "users/$uid"
        return db.collection("users").document(uid)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(UserEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeStudents(actor: UserEntity): Flow<List<UserEntity>> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || actor.roleEnum != UserRole.ADMIN) return flowOf(emptyList())
        val path = "users"
        return db.collection(path)
            .whereEqualTo("role", UserRole.STUDENT.name)
            .snapshots()
            .map { snap ->
                snap.toObjects(UserEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name.lowercase(Locale.US) }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeStudentInvites(actor: UserEntity): Flow<List<StudentInvite>> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || actor.roleEnum != UserRole.ADMIN) return flowOf(emptyList())
        val path = "student_invites"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(StudentInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name.lowercase(Locale.US) }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeTradesForUser(actor: UserEntity, targetUserId: String): Flow<List<TradeEntity>> = flow {
        val uid = requireUserId()
        if (actor.roleEnum != UserRole.ADMIN && uid != targetUserId) {
            emit(emptyList())
            return@flow
        }
        val path = "trades"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", targetUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TradeEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<TradeEntity> { it.tradeDateMillis }
                                .thenByDescending { it.createdAt?.seconds ?: 0L }
                        )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllTrades(actor: UserEntity): Flow<List<TradeEntity>> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || actor.roleEnum != UserRole.ADMIN) return flowOf(emptyList())
        val path = "trades"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(TradeEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<TradeEntity> { it.tradeDateMillis }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeReviewsForUser(actor: UserEntity, targetUserId: String): Flow<List<DisciplineReviewEntity>> = flow {
        val uid = requireUserId()
        if (actor.roleEnum != UserRole.ADMIN && uid != targetUserId) {
            emit(emptyList())
            return@flow
        }
        val path = "discipline_reviews"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", targetUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DisciplineReviewEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- ADMIN STUDENT INVITE & ACCOUNT MANAGEMENT ---

    suspend fun createStudentInviteByAdmin(
        actor: UserEntity,
        name: String,
        email: String,
        preferredCurrency: TradingCurrency,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double
    ): Result<StudentInvite> {
        var docPath = "student_invites"
        return try {
            val uid = requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            val cleanName = name.trim().take(80)
            val cleanEmail = email.trim().lowercase(Locale.US).take(120)
            require(cleanName.length >= 2) { "Student name must be at least 2 characters." }
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Valid Google email required." }

            val dupUser = db.collection("users").whereEqualTo("email", cleanEmail).get().await()
            require(dupUser.isEmpty) { "A student with this email is already registered." }
            val dupInvite = db.collection("student_invites").whereEqualTo("email", cleanEmail).get().await()
            require(dupInvite.isEmpty) { "An invite for this email already exists." }

            val docRef = db.collection("student_invites").document()
            docPath = docRef.path
            val payload = mapOf(
                "name" to cleanName,
                "email" to cleanEmail,
                "status" to AccountStatus.ACTIVE.name,
                "preferredCurrency" to preferredCurrency.name,
                "dailyProfitGoal" to dailyGoal.coerceAtLeast(0.0),
                "dailyMaxLossLimit" to dailyMaxLoss.coerceAtLeast(0.0),
                "maxTradesPerDay" to maxTradesPerDay.coerceIn(1, 100),
                "maxRiskPerTrade" to maxRiskPerTrade.coerceAtLeast(0.0),
                "createdByUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                StudentInvite(
                    id = docRef.id,
                    name = cleanName,
                    email = cleanEmail,
                    status = AccountStatus.ACTIVE.name,
                    preferredCurrency = preferredCurrency.name,
                    dailyProfitGoal = dailyGoal.coerceAtLeast(0.0),
                    dailyMaxLossLimit = dailyMaxLoss.coerceAtLeast(0.0),
                    maxTradesPerDay = maxTradesPerDay.coerceIn(1, 100),
                    maxRiskPerTrade = maxRiskPerTrade.coerceAtLeast(0.0),
                    createdByUid = uid
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateStudentInviteByAdmin(
        actor: UserEntity,
        invite: StudentInvite,
        newName: String,
        newEmail: String,
        preferredCurrency: TradingCurrency,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double
    ): Result<Unit> {
        val docPath = "student_invites/${invite.id}"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            require(invite.id.isNotBlank()) { "Invalid invite ID." }
            val cleanName = newName.trim().take(80)
            val cleanEmail = newEmail.trim().lowercase(Locale.US).take(120)
            require(cleanName.length >= 2) { "Name must be at least 2 characters." }
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Valid email required." }

            db.collection("student_invites").document(invite.id).update(
                mapOf(
                    "name" to cleanName,
                    "email" to cleanEmail,
                    "preferredCurrency" to preferredCurrency.name,
                    "dailyProfitGoal" to dailyGoal.coerceAtLeast(0.0),
                    "dailyMaxLossLimit" to dailyMaxLoss.coerceAtLeast(0.0),
                    "maxTradesPerDay" to maxTradesPerDay.coerceIn(1, 100),
                    "maxRiskPerTrade" to maxRiskPerTrade.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStudentInviteByAdmin(actor: UserEntity, inviteId: String): Result<Unit> {
        val docPath = "student_invites/$inviteId"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            require(inviteId.isNotBlank()) { "Invalid invite ID." }
            db.collection("student_invites").document(inviteId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateStudentByAdmin(
        actor: UserEntity,
        student: UserEntity,
        newName: String,
        newEmail: String,
        preferredCurrency: TradingCurrency,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double
    ): Result<Unit> {
        val docPath = "users/${student.id}"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            if (student.roleEnum != UserRole.STUDENT) throw SecurityException("Cannot modify non-student here.")
            require(student.id.isNotBlank()) { "Invalid student ID." }

            val cleanName = newName.trim().take(80)
            val cleanEmail = newEmail.trim().lowercase(Locale.US).take(120)
            require(cleanName.length >= 2) { "Name must be at least 2 characters." }
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Valid email required." }

            db.collection("users").document(student.id).update(
                mapOf(
                    "name" to cleanName,
                    "email" to cleanEmail,
                    "preferredCurrency" to preferredCurrency.name,
                    "dailyProfitGoal" to dailyGoal.coerceAtLeast(0.0),
                    "dailyMaxLossLimit" to dailyMaxLoss.coerceAtLeast(0.0),
                    "maxTradesPerDay" to maxTradesPerDay.coerceIn(1, 100),
                    "maxRiskPerTrade" to maxRiskPerTrade.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setStudentSuspended(
        actor: UserEntity,
        student: UserEntity,
        suspended: Boolean
    ): Result<Unit> {
        val docPath = "users/${student.id}"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            if (student.roleEnum != UserRole.STUDENT) throw SecurityException("Cannot suspend Admin account.")
            require(student.id.isNotBlank()) { "Invalid student ID." }

            db.collection("users").document(student.id).update(
                mapOf(
                    "status" to if (suspended) AccountStatus.SUSPENDED.name else AccountStatus.ACTIVE.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStudentByAdmin(actor: UserEntity, studentId: String): Result<Unit> {
        val docPath = "users/$studentId"
        return try {
            val uid = requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            require(studentId.isNotBlank() && studentId != uid) { "Cannot delete Admin account." }

            val targetSnap = db.collection("users").document(studentId).get().await()
            val target = targetSnap.toObject(UserEntity::class.java)
            if (target?.roleEnum == UserRole.ADMIN) throw SecurityException("Cannot delete Admin account.")

            val studentTrades = db.collection("trades").whereEqualTo("userId", studentId).get().await()
            val studentReviews = db.collection("discipline_reviews").whereEqualTo("userId", studentId).get().await()
            val studentWarnings = db.collection("warnings").whereEqualTo("studentId", studentId).get().await()
            val studentSmartAlerts = db.collection("smart_discipline_alerts").whereEqualTo("userId", studentId).get().await()
            val studentWeeklyReports = db.collection("weekly_reports").whereEqualTo("userId", studentId).get().await()
            val studentMessages = db.collection("conversations").document(studentId).collection("messages").get().await()
            val convRef = db.collection("conversations").document(studentId)
            val convSnap = convRef.get().await()

            val refsToDelete = buildList {
                studentTrades.documents.forEach { add(it.reference) }
                studentReviews.documents.forEach { add(it.reference) }
                studentWarnings.documents.forEach { add(it.reference) }
                studentSmartAlerts.documents.forEach { add(it.reference) }
                studentWeeklyReports.documents.forEach { add(it.reference) }
                studentMessages.documents.forEach { add(it.reference) }
                if (convSnap.exists()) add(convRef)
                add(db.collection("users").document(studentId))
            }

            refsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            val photoPath = target?.profilePhotoPath?.takeIf { it.isNotBlank() }
                ?: "student_profiles/$studentId/avatar.jpg"
            runCatching { storage.reference.child(photoPath).delete().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOwnDisciplineGoals(
        actor: UserEntity,
        preferredCurrency: TradingCurrency,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double,
        slLookbackCount: Int = actor.effectiveSlLookbackCount,
        slAlertThreshold: Int = actor.effectiveSlAlertThreshold,
        strategySlThreshold: Int = actor.effectiveStrategySlThreshold
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot modify settings.")
            }
            val safeLookback = slLookbackCount.coerceIn(2, 50)
            val safeSlThreshold = slAlertThreshold.coerceIn(2, safeLookback)
            val safeStrategyThreshold = strategySlThreshold.coerceIn(1, 50)
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            ref.update(
                mapOf(
                    "preferredCurrency" to preferredCurrency.name,
                    "dailyProfitGoal" to dailyGoal.coerceAtLeast(0.0),
                    "dailyMaxLossLimit" to dailyMaxLoss.coerceAtLeast(0.0),
                    "maxTradesPerDay" to maxTradesPerDay.coerceIn(1, 100),
                    "maxRiskPerTrade" to maxRiskPerTrade.coerceAtLeast(0.0),
                    "slAlertLookbackCount" to safeLookback,
                    "slAlertThreshold" to safeSlThreshold,
                    "strategySlThreshold" to safeStrategyThreshold,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveTrade(
        actor: UserEntity,
        existingTradeId: String?,
        targetUserId: String,
        targetUserName: String,
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
        timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID
    ): Result<TradeEntity> {
        var docPath = "trades"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot add or modify trades.")
            }
            if (actor.roleEnum != UserRole.ADMIN && uid != targetUserId) {
                throw SecurityException("Access denied: You can only manage your own trades.")
            }

            val cleanInstrument = instrument.trim().uppercase(Locale.US).take(40)
            require(cleanInstrument.isNotEmpty()) { "Trading instrument is required." }
            require(tradeDateMillis > 0L) { "Valid trade date is required." }
            require(tradeDateIso.length == 10) { "Invalid trade date format." }
            val cleanExitDateIso = exitDateIso.trim()
            require(cleanExitDateIso.isEmpty() || cleanExitDateIso.length == 10) { "Invalid exit date format." }
            val cleanEntryTime = entryTimeMillis?.takeIf { it > 0L }
            val cleanExitTime = exitTimeMillis?.takeIf { it > 0L }
            if (cleanEntryTime != null && cleanExitTime != null) {
                require(cleanExitTime >= cleanEntryTime) { "Exit time must be on or after entry time." }
            }
            require(usdToInrRate in 1.0..1000.0) { "Exchange rate must be between 1 and 1000 INR/USD." }
            require(entryPrice > 0.0) { "Entry price must be greater than 0." }
            require(exitPrice > 0.0) { "Exit price must be greater than 0." }
            require(lotSize > 0.0) { "Lot size must be greater than 0." }
            if (stopLoss != null) require(stopLoss >= 0.0) { "Stop loss cannot be negative." }
            if (takeProfit != null) require(takeProfit >= 0.0) { "Take profit cannot be negative." }
            if (screenshotData != null) {
                require(screenshotData.length <= MAX_SCREENSHOT_BASE64_LENGTH) {
                    "Screenshot image is too large. Please select a smaller chart image."
                }
            }

            require(fees >= 0.0) { "Fees and commissions cannot be negative." }
            val cleanRisk = riskAmount.coerceAtLeast(0.0)
            val cleanFees = fees.coerceAtLeast(0.0)
            val statusName = tradeStatus.name
            val cleanUserName = targetUserName.trim().ifEmpty { actor.name }.take(80)
            val cleanStrategy = strategy.trim().take(120)
            val cleanEntryReason = entryReason.trim().take(1000)
            val cleanExitReason = exitReason.trim().take(1000)
            val cleanMistakes = mistakes.trim().take(500)
            val cleanLessons = lessonsLearned.trim().take(1000)
            val cleanNotes = notes.trim().take(1500)
            val cleanSession = tradingSession.trim().take(60)
            val cleanTimezone = timezoneId.trim().ifEmpty { TradeDateTimeHelper.DEFAULT_TIMEZONE_ID }.take(60)
            val closureName = exitClosureType.name

            if (!existingTradeId.isNullOrBlank()) {
                val docRef = db.collection("trades").document(existingTradeId)
                docPath = docRef.path
                val snap = docRef.get().await()
                val existing = snap.toObject(TradeEntity::class.java)
                    ?: throw IllegalArgumentException("Trade not found.")
                if (actor.roleEnum != UserRole.ADMIN && existing.userId != uid) {
                    throw SecurityException("Access denied: Cannot edit another student's trade.")
                }

                // Partial update: preserves tradeCode, userId, and createdAt immutability
                val updates = hashMapOf<String, Any?>(
                    "userName" to cleanUserName,
                    "tradeDateMillis" to tradeDateMillis,
                    "tradeDateIso" to tradeDateIso,
                    "entryTimeMillis" to cleanEntryTime,
                    "exitDateIso" to cleanExitDateIso,
                    "exitTimeMillis" to cleanExitTime,
                    "tradingSession" to cleanSession,
                    "exitClosureType" to closureName,
                    "timezoneId" to cleanTimezone,
                    "instrument" to cleanInstrument,
                    "direction" to direction.name,
                    "timeframe" to timeframe.name,
                    "currency" to currency.name,
                    "usdToInrRate" to usdToInrRate,
                    "entryPrice" to entryPrice,
                    "exitPrice" to exitPrice,
                    "stopLoss" to stopLoss,
                    "takeProfit" to takeProfit,
                    "lotSize" to lotSize,
                    "riskAmount" to cleanRisk,
                    "actualPnl" to actualPnl,
                    "fees" to cleanFees,
                    "tradeStatus" to statusName,
                    "result" to result.name,
                    "strategy" to cleanStrategy,
                    "entryReason" to cleanEntryReason,
                    "exitReason" to cleanExitReason,
                    "mistakes" to cleanMistakes,
                    "lessonsLearned" to cleanLessons,
                    "screenshotData" to screenshotData,
                    "notes" to cleanNotes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
                Result.success(
                    existing.copy(
                        userName = cleanUserName,
                        tradeDateMillis = tradeDateMillis,
                        tradeDateIso = tradeDateIso,
                        entryTimeMillis = cleanEntryTime,
                        exitDateIso = cleanExitDateIso,
                        exitTimeMillis = cleanExitTime,
                        tradingSession = cleanSession,
                        exitClosureType = closureName,
                        timezoneId = cleanTimezone,
                        instrument = cleanInstrument,
                        direction = direction.name,
                        timeframe = timeframe.name,
                        currency = currency.name,
                        usdToInrRate = usdToInrRate,
                        entryPrice = entryPrice,
                        exitPrice = exitPrice,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        lotSize = lotSize,
                        riskAmount = cleanRisk,
                        actualPnl = actualPnl,
                        fees = cleanFees,
                        tradeStatus = statusName,
                        result = result.name,
                        strategy = cleanStrategy,
                        entryReason = cleanEntryReason,
                        exitReason = cleanExitReason,
                        mistakes = cleanMistakes,
                        lessonsLearned = cleanLessons,
                        screenshotData = screenshotData,
                        notes = cleanNotes
                    )
                )
            } else {
                val docRef = db.collection("trades").document()
                docPath = docRef.path
                val suffix = docRef.id.take(4).uppercase(Locale.US)
                val code = "TWBB-$suffix"
                val createMap = mapOf(
                    "tradeCode" to code,
                    "userId" to targetUserId,
                    "userName" to cleanUserName,
                    "tradeDateMillis" to tradeDateMillis,
                    "tradeDateIso" to tradeDateIso,
                    "entryTimeMillis" to cleanEntryTime,
                    "exitDateIso" to cleanExitDateIso,
                    "exitTimeMillis" to cleanExitTime,
                    "tradingSession" to cleanSession,
                    "exitClosureType" to closureName,
                    "timezoneId" to cleanTimezone,
                    "instrument" to cleanInstrument,
                    "direction" to direction.name,
                    "timeframe" to timeframe.name,
                    "currency" to currency.name,
                    "usdToInrRate" to usdToInrRate,
                    "entryPrice" to entryPrice,
                    "exitPrice" to exitPrice,
                    "stopLoss" to stopLoss,
                    "takeProfit" to takeProfit,
                    "lotSize" to lotSize,
                    "riskAmount" to cleanRisk,
                    "actualPnl" to actualPnl,
                    "fees" to cleanFees,
                    "tradeStatus" to statusName,
                    "result" to result.name,
                    "strategy" to cleanStrategy,
                    "entryReason" to cleanEntryReason,
                    "exitReason" to cleanExitReason,
                    "mistakes" to cleanMistakes,
                    "lessonsLearned" to cleanLessons,
                    "screenshotData" to screenshotData,
                    "notes" to cleanNotes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }

                docRef.set(createMap).await()
                Result.success(
                    TradeEntity(
                        id = docRef.id,
                        tradeCode = code,
                        userId = targetUserId,
                        userName = cleanUserName,
                        tradeDateMillis = tradeDateMillis,
                        tradeDateIso = tradeDateIso,
                        entryTimeMillis = cleanEntryTime,
                        exitDateIso = cleanExitDateIso,
                        exitTimeMillis = cleanExitTime,
                        tradingSession = cleanSession,
                        exitClosureType = closureName,
                        timezoneId = cleanTimezone,
                        instrument = cleanInstrument,
                        direction = direction.name,
                        timeframe = timeframe.name,
                        currency = currency.name,
                        usdToInrRate = usdToInrRate,
                        entryPrice = entryPrice,
                        exitPrice = exitPrice,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        lotSize = lotSize,
                        riskAmount = cleanRisk,
                        actualPnl = actualPnl,
                        fees = cleanFees,
                        tradeStatus = statusName,
                        result = result.name,
                        strategy = cleanStrategy,
                        entryReason = cleanEntryReason,
                        exitReason = cleanExitReason,
                        mistakes = cleanMistakes,
                        lessonsLearned = cleanLessons,
                        screenshotData = screenshotData,
                        notes = cleanNotes
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingTradeId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun deleteTrade(actor: UserEntity, tradeId: String): Result<Unit> {
        val docPath = "trades/$tradeId"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot delete trades.")
            }
            require(tradeId.isNotBlank()) { "Trade ID is required." }
            val ref = db.collection("trades").document(tradeId)
            val snap = ref.get().await()
            val trade = snap.toObject(TradeEntity::class.java)
                ?: throw IllegalArgumentException("Trade not found.")
            if (actor.roleEnum != UserRole.ADMIN && trade.userId != uid) {
                throw SecurityException("Access denied: Cannot delete another student's trade.")
            }
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveDisciplineReview(
        actor: UserEntity,
        targetUserId: String,
        periodType: ReviewPeriod,
        periodKey: String,
        emotionState: EmotionState,
        psychologyNotes: String,
        slAdherenceRating: Int,
        lessonsSummary: String,
        nextActionPlan: String
    ): Result<Unit> {
        var docPath = "discipline_reviews"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot save reviews.")
            }
            if (actor.roleEnum != UserRole.ADMIN && uid != targetUserId) {
                throw SecurityException("Access denied.")
            }

            val cleanKey = periodKey.trim().take(32)
            require(cleanKey.length >= 4) { "Period key must be at least 4 characters." }
            val cleanPsych = psychologyNotes.trim().take(2000)
            val cleanLessons = lessonsSummary.trim().take(2000)
            val cleanPlan = nextActionPlan.trim().take(2000)
            val rating = slAdherenceRating.coerceIn(1, 5)

            // Check if a review for the same periodType & periodKey already exists for this user
            val existingQuery = db.collection("discipline_reviews")
                .whereEqualTo("userId", targetUserId)
                .get()
                .await()
            val existingMatch = existingQuery.toObjects(DisciplineReviewEntity::class.java)
                .firstOrNull { it.periodType == periodType.name && it.periodKey == cleanKey }

            if (existingMatch != null && existingMatch.id.isNotBlank()) {
                val docRef = db.collection("discipline_reviews").document(existingMatch.id)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "periodType" to periodType.name,
                        "periodKey" to cleanKey,
                        "emotionState" to emotionState.name,
                        "psychologyNotes" to cleanPsych,
                        "slAdherenceRating" to rating,
                        "lessonsSummary" to cleanLessons,
                        "nextActionPlan" to cleanPlan,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val docRef = db.collection("discipline_reviews").document()
                docPath = docRef.path
                docRef.set(
                    mapOf(
                        "userId" to targetUserId,
                        "periodType" to periodType.name,
                        "periodKey" to cleanKey,
                        "emotionState" to emotionState.name,
                        "psychologyNotes" to cleanPsych,
                        "slAdherenceRating" to rating,
                        "lessonsSummary" to cleanLessons,
                        "nextActionPlan" to cleanPlan,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun encodeScreenshotToBase64(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected image.")
            val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: throw IllegalArgumentException("Unsupported image format.")

            val maxDim = 960
            val scale = minOf(1f, maxDim.toFloat() / max(original.width, original.height).toFloat())
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).roundToInt().coerceAtLeast(1),
                    (original.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }

            var quality = 80
            var jpegBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                jpegBytes = out.toByteArray()
                quality -= 12
            } while (jpegBytes.size > 210_000 && quality >= 30)

            if (scaled !== original) scaled.recycle()
            original.recycle()

            val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            require(encoded.length <= MAX_SCREENSHOT_BASE64_LENGTH) {
                "Compressed chart screenshot exceeds 320 KB limit."
            }
            encoded
        }
    }

    suspend fun exportBackupJson(actor: UserEntity): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        return try {
            val users = if (actor.roleEnum == UserRole.ADMIN) {
                db.collection("users").get().await().toObjects(UserEntity::class.java)
            } else {
                listOfNotNull(
                    db.collection("users").document(uid).get().await().toObject(UserEntity::class.java)
                )
            }

            val trades = if (actor.roleEnum == UserRole.ADMIN) {
                db.collection("trades").get().await().toObjects(TradeEntity::class.java)
            } else {
                db.collection("trades").whereEqualTo("userId", uid).get().await()
                    .toObjects(TradeEntity::class.java)
            }

            val reviews = if (actor.roleEnum == UserRole.ADMIN) {
                db.collection("discipline_reviews").get().await()
                    .toObjects(DisciplineReviewEntity::class.java)
            } else {
                db.collection("discipline_reviews").whereEqualTo("userId", uid).get().await()
                    .toObjects(DisciplineReviewEntity::class.java)
            }

            val root = JSONObject()
            root.put("brand", "Trading with Bangali Babu")
            root.put("exportedAt", System.currentTimeMillis())

            val usersArr = JSONArray()
            users.forEach { u ->
                usersArr.put(JSONObject().apply {
                    put("id", u.id)
                    put("userId", u.userId)
                    put("name", u.name)
                    put("email", u.email)
                    put("role", u.role)
                    put("status", u.status)
                    put("preferredCurrency", u.preferredCurrency)
                    put("dailyProfitGoal", u.dailyProfitGoal)
                    put("dailyMaxLossLimit", u.dailyMaxLossLimit)
                    put("maxTradesPerDay", u.maxTradesPerDay)
                    put("maxRiskPerTrade", u.maxRiskPerTrade)
                    put("mobileNumber", u.mobileNumber)
                    put("whatsappNumber", u.whatsappNumber)
                    put("dateOfBirth", u.dateOfBirth)
                    put("gender", u.gender)
                    put("fullAddress", u.fullAddress)
                    put("profilePhotoUrl", u.profilePhotoUrl)
                    put("profileCompleted", u.profileCompleted)
                })
            }
            root.put("users", usersArr)

            val tradesArr = JSONArray()
            trades.forEach { t ->
                tradesArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("tradeCode", t.tradeCode)
                    put("userId", t.userId)
                    put("userName", t.userName)
                    put("tradeDateMillis", t.tradeDateMillis)
                    put("tradeDateIso", t.tradeDateIso)
                    put("entryTimeMillis", t.entryTimeMillis ?: JSONObject.NULL)
                    put("exitDateIso", t.exitDateIso)
                    put("exitTimeMillis", t.exitTimeMillis ?: JSONObject.NULL)
                    put("tradingSession", t.tradingSession)
                    put("exitClosureType", t.exitClosureType)
                    put("timezoneId", t.effectiveTimezoneId)
                    put("instrument", t.instrument)
                    put("direction", t.direction)
                    put("timeframe", t.timeframe)
                    put("currency", t.currencyEnum.name)
                    put("usdToInrRate", t.effectiveUsdToInrRate)
                    put("entryPrice", t.entryPrice)
                    put("exitPrice", t.exitPrice)
                    put("stopLoss", t.stopLoss ?: JSONObject.NULL)
                    put("takeProfit", t.takeProfit ?: JSONObject.NULL)
                    put("lotSize", t.lotSize)
                    put("riskAmount", t.riskAmount)
                    put("actualPnl", t.actualPnl)
                    put("fees", t.fees)
                    put("tradeStatus", t.tradeStatusEnum.name)
                    put("result", t.result)
                    put("strategy", t.strategy)
                    put("entryReason", t.entryReason)
                    put("exitReason", t.exitReason)
                    put("mistakes", t.mistakes)
                    put("lessonsLearned", t.lessonsLearned)
                    put("screenshotData", t.screenshotData ?: JSONObject.NULL)
                    put("notes", t.notes)
                })
            }
            root.put("trades", tradesArr)

            val reviewsArr = JSONArray()
            reviews.forEach { r ->
                reviewsArr.put(JSONObject().apply {
                    put("id", r.id)
                    put("userId", r.userId)
                    put("periodType", r.periodType)
                    put("periodKey", r.periodKey)
                    put("emotionState", r.emotionState)
                    put("psychologyNotes", r.psychologyNotes)
                    put("slAdherenceRating", r.slAdherenceRating)
                    put("lessonsSummary", r.lessonsSummary)
                    put("nextActionPlan", r.nextActionPlan)
                })
            }
            root.put("reviews", reviewsArr)
            Result.success(root.toString(2))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "backup_export")
            Result.failure(e)
        }
    }

    suspend fun restoreBackupJson(actor: UserEntity, jsonContent: String): Result<Int> {
        return try {
            val uid = requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Only Admin can restore a cloud database backup.")
            }

            val root = JSONObject(jsonContent)
            val tradesArr = root.getJSONArray("trades")
            val reviewsArr = root.optJSONArray("reviews") ?: JSONArray()

            var restoredTrades = 0
            val batch = db.batch()
            var opsInBatch = 0

            suspend fun flushIfNeeded() {
                if (opsInBatch >= 400) {
                    batch.commit().await()
                    opsInBatch = 0
                }
            }

            for (i in 0 until tradesArr.length()) {
                val o = tradesArr.getJSONObject(i)
                val rawId = o.optString("id")
                val ref = if (isValidDocId(rawId)) db.collection("trades").document(rawId)
                else db.collection("trades").document()

                val existingSnap = ref.get().await()
                val tradeUserId = o.optString("userId").takeIf { isValidDocId(it) } ?: uid
                val tradeCode = o.optString("tradeCode").ifBlank { "TWBB-${ref.id.take(4).uppercase(Locale.US)}" }.take(32)
                val instrument = o.optString("instrument").trim().uppercase(Locale.US).ifBlank { "UNKNOWN" }.take(40)
                val direction = TradeDirection.entries.firstOrNull { it.name == o.optString("direction") }?.name ?: TradeDirection.BUY.name
                val timeframe = Timeframe.entries.firstOrNull { it.name == o.optString("timeframe") }?.name ?: Timeframe.M15.name
                val currency = TradingCurrency.entries.firstOrNull { it.name.equals(o.optString("currency"), ignoreCase = true) }?.name ?: TradingCurrency.USD.name
                val usdToInrRate = o.optDouble("usdToInrRate", TradingAnalytics.DEFAULT_USD_TO_INR_RATE)
                    .let { if (it.isNaN() || it <= 0.0 || it > 1000.0) TradingAnalytics.DEFAULT_USD_TO_INR_RATE else it }
                val result = TradeResult.entries.firstOrNull { it.name == o.optString("result") }?.name ?: TradeResult.BREAK_EVEN.name
                val sl = if (o.isNull("stopLoss")) null else o.optDouble("stopLoss").takeIf { !it.isNaN() && it >= 0.0 }
                val tp = if (o.isNull("takeProfit")) null else o.optDouble("takeProfit").takeIf { !it.isNaN() && it >= 0.0 }
                val shot = if (o.isNull("screenshotData")) null else o.optString("screenshotData").takeIf { it.isNotBlank() && it.length <= MAX_SCREENSHOT_BASE64_LENGTH }
                val dateIso = o.optString("tradeDateIso").let { if (it.length == 10) it else "2026-01-01" }
                val dateMillis = o.optLong("tradeDateMillis").let { if (it > 0L) it else System.currentTimeMillis() }
                val entryTime = if (o.isNull("entryTimeMillis")) null else o.optLong("entryTimeMillis").takeIf { it > 0L }
                val rawExitTime = if (o.isNull("exitTimeMillis")) null else o.optLong("exitTimeMillis").takeIf { it > 0L }
                val exitTime = if (rawExitTime != null && (entryTime == null || rawExitTime >= entryTime)) rawExitTime else null
                val exitIso = o.optString("exitDateIso").trim().let { if (it.length == 10) it else "" }
                val restoredSession = o.optString("tradingSession").trim().take(60)
                val restoredClosure = ExitClosureType.entries.firstOrNull { it.name == o.optString("exitClosureType") }?.name ?: ""
                val restoredTz = o.optString("timezoneId").trim().ifBlank { TradeDateTimeHelper.DEFAULT_TIMEZONE_ID }.take(60)

                val restoredStatus = TradeStatus.entries.firstOrNull { it.name.equals(o.optString("tradeStatus"), ignoreCase = true) }?.name
                    ?: TradeStatus.CLOSED.name
                val restoredFees = o.optDouble("fees", 0.0).let { if (it.isNaN() || it < 0.0) 0.0 else it }

                if (existingSnap.exists()) {
                    batch.update(
                        ref,
                        hashMapOf<String, Any?>(
                            "userName" to o.optString("userName").ifBlank { actor.name }.take(80),
                            "tradeDateMillis" to dateMillis,
                            "tradeDateIso" to dateIso,
                            "entryTimeMillis" to entryTime,
                            "exitDateIso" to exitIso,
                            "exitTimeMillis" to exitTime,
                            "tradingSession" to restoredSession,
                            "exitClosureType" to restoredClosure,
                            "timezoneId" to restoredTz,
                            "instrument" to instrument,
                            "direction" to direction,
                            "timeframe" to timeframe,
                            "currency" to currency,
                            "usdToInrRate" to usdToInrRate,
                            "entryPrice" to o.optDouble("entryPrice", 1.0).coerceAtLeast(0.0001),
                            "exitPrice" to o.optDouble("exitPrice", 1.0).coerceAtLeast(0.0001),
                            "stopLoss" to sl,
                            "takeProfit" to tp,
                            "lotSize" to o.optDouble("lotSize", 1.0).coerceAtLeast(0.0001),
                            "riskAmount" to o.optDouble("riskAmount", 0.0).coerceAtLeast(0.0),
                            "actualPnl" to o.optDouble("actualPnl", 0.0),
                            "fees" to restoredFees,
                            "tradeStatus" to restoredStatus,
                            "result" to result,
                            "strategy" to o.optString("strategy").take(120),
                            "entryReason" to o.optString("entryReason").take(1000),
                            "exitReason" to o.optString("exitReason").take(1000),
                            "mistakes" to o.optString("mistakes").take(500),
                            "lessonsLearned" to o.optString("lessonsLearned").take(1000),
                            "screenshotData" to shot,
                            "notes" to o.optString("notes").take(1500),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val createPayload = mapOf(
                        "tradeCode" to tradeCode,
                        "userId" to tradeUserId,
                        "userName" to o.optString("userName").ifBlank { actor.name }.take(80),
                        "tradeDateMillis" to dateMillis,
                        "tradeDateIso" to dateIso,
                        "entryTimeMillis" to entryTime,
                        "exitDateIso" to exitIso,
                        "exitTimeMillis" to exitTime,
                        "tradingSession" to restoredSession,
                        "exitClosureType" to restoredClosure,
                        "timezoneId" to restoredTz,
                        "instrument" to instrument,
                        "direction" to direction,
                        "timeframe" to timeframe,
                        "currency" to currency,
                        "usdToInrRate" to usdToInrRate,
                        "entryPrice" to o.optDouble("entryPrice", 1.0).coerceAtLeast(0.0001),
                        "exitPrice" to o.optDouble("exitPrice", 1.0).coerceAtLeast(0.0001),
                        "stopLoss" to sl,
                        "takeProfit" to tp,
                        "lotSize" to o.optDouble("lotSize", 1.0).coerceAtLeast(0.0001),
                        "riskAmount" to o.optDouble("riskAmount", 0.0).coerceAtLeast(0.0),
                        "actualPnl" to o.optDouble("actualPnl", 0.0),
                        "fees" to restoredFees,
                        "tradeStatus" to restoredStatus,
                        "result" to result,
                        "strategy" to o.optString("strategy").take(120),
                        "entryReason" to o.optString("entryReason").take(1000),
                        "exitReason" to o.optString("exitReason").take(1000),
                        "mistakes" to o.optString("mistakes").take(500),
                        "lessonsLearned" to o.optString("lessonsLearned").take(1000),
                        "screenshotData" to shot,
                        "notes" to o.optString("notes").take(1500),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    batch.set(ref, createPayload)
                }
                restoredTrades++
                opsInBatch++
                flushIfNeeded()
            }

            for (i in 0 until reviewsArr.length()) {
                val o = reviewsArr.getJSONObject(i)
                val rawId = o.optString("id")
                val ref = if (isValidDocId(rawId)) db.collection("discipline_reviews").document(rawId)
                else db.collection("discipline_reviews").document()
                val existingSnap = ref.get().await()
                val reviewUserId = o.optString("userId").takeIf { isValidDocId(it) } ?: uid
                val periodType = ReviewPeriod.entries.firstOrNull { it.name == o.optString("periodType") }?.name ?: ReviewPeriod.WEEKLY.name
                val periodKey = o.optString("periodKey").trim().ifBlank { "2026-W01" }.padEnd(4, '0').take(32)
                val emotionState = EmotionState.entries.firstOrNull { it.name == o.optString("emotionState") }?.name ?: EmotionState.CALM.name

                if (existingSnap.exists()) {
                    batch.update(
                        ref,
                        mapOf(
                            "periodType" to periodType,
                            "periodKey" to periodKey,
                            "emotionState" to emotionState,
                            "psychologyNotes" to o.optString("psychologyNotes").take(2000),
                            "slAdherenceRating" to o.optInt("slAdherenceRating", 3).coerceIn(1, 5),
                            "lessonsSummary" to o.optString("lessonsSummary").take(2000),
                            "nextActionPlan" to o.optString("nextActionPlan").take(2000),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        ref,
                        mapOf(
                            "userId" to reviewUserId,
                            "periodType" to periodType,
                            "periodKey" to periodKey,
                            "emotionState" to emotionState,
                            "psychologyNotes" to o.optString("psychologyNotes").take(2000),
                            "slAdherenceRating" to o.optInt("slAdherenceRating", 3).coerceIn(1, 5),
                            "lessonsSummary" to o.optString("lessonsSummary").take(2000),
                            "nextActionPlan" to o.optString("nextActionPlan").take(2000),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                opsInBatch++
                flushIfNeeded()
            }

            if (opsInBatch > 0) {
                batch.commit().await()
            }
            Result.success(restoredTrades)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "backup_restore")
            Result.failure(e)
        }
    }

    // --- STUDENT–ADMIN COMMUNICATION SYSTEM ---

    fun observeOwnConversation(actor: UserEntity): Flow<ConversationThread?> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || actor.roleEnum == UserRole.ADMIN) return flowOf(null)
        val path = "conversations/$uid"
        return db.collection("conversations").document(uid)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(ConversationThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeAllConversations(actor: UserEntity): Flow<List<ConversationThread>> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || actor.roleEnum != UserRole.ADMIN) return flowOf(emptyList())
        val path = "conversations"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(ConversationThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAtMillis }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeDirectMessages(actor: UserEntity, studentUid: String): Flow<List<DirectMessageEntity>> = flow {
        val uid = requireUserId()
        if (!isValidDocId(studentUid)) {
            emit(emptyList())
            return@flow
        }
        if (actor.roleEnum != UserRole.ADMIN && uid != studentUid) {
            emit(emptyList())
            return@flow
        }
        val path = "conversations/$studentUid/messages"
        emitAll(
            db.collection("conversations").document(studentUid).collection("messages")
                .snapshots()
                .map { snap ->
                    snap.toObjects(DirectMessageEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.effectiveTimestampMillis }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWarningsForStudent(actor: UserEntity, targetStudentId: String): Flow<List<StudentWarningEntity>> = flow {
        val uid = requireUserId()
        if (actor.roleEnum != UserRole.ADMIN && uid != targetStudentId) {
            emit(emptyList())
            return@flow
        }
        val path = "warnings"
        emitAll(
            db.collection(path)
                .whereEqualTo("studentId", targetStudentId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(StudentWarningEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.effectiveTimestampMillis }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllWarnings(actor: UserEntity): Flow<List<StudentWarningEntity>> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || actor.roleEnum != UserRole.ADMIN) return flowOf(emptyList())
        val path = "warnings"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(StudentWarningEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.effectiveTimestampMillis }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeAnnouncements(): Flow<List<AnnouncementEntity>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        val path = "announcements"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(AnnouncementEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.effectiveTimestampMillis }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun sendDirectMessage(
        actor: UserEntity,
        targetStudent: UserEntity,
        messageBody: String
    ): Result<Unit> {
        val targetStudentId = targetStudent.id.ifBlank { targetStudent.userId }
        val docPath = "conversations/$targetStudentId"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot send messages.")
            }
            if (actor.roleEnum != UserRole.ADMIN && uid != targetStudentId) {
                throw SecurityException("Access denied: Students can only message Admin in their own conversation.")
            }
            require(isValidDocId(targetStudentId)) { "Invalid student conversation ID." }

            val cleanBody = messageBody.trim().take(2000)
            require(cleanBody.isNotEmpty()) { "Message cannot be empty." }

            val cleanStudentName = targetStudent.name.trim().ifEmpty { "Student" }.take(80)
            val cleanStudentEmail = targetStudent.email.trim().ifEmpty { "student@trading.local" }.take(120)
            val cleanSenderName = actor.name.trim().ifEmpty { actor.roleEnum.label }.take(80)
            val nowMillis = System.currentTimeMillis()
            val isFromAdmin = actor.roleEnum == UserRole.ADMIN

            val convRef = db.collection("conversations").document(targetStudentId)
            val msgRef = convRef.collection("messages").document()

            db.runTransaction { tx ->
                val convSnap = tx.get(convRef)
                val existingUnreadAdmin = convSnap.getLong("unreadByAdminCount")?.toInt() ?: 0
                val existingUnreadStudent = convSnap.getLong("unreadByStudentCount")?.toInt() ?: 0

                val nextUnreadAdmin = if (isFromAdmin) 0 else (existingUnreadAdmin + 1)
                val nextUnreadStudent = if (isFromAdmin) (existingUnreadStudent + 1) else 0

                if (convSnap.exists()) {
                    tx.update(
                        convRef,
                        mapOf(
                            "studentName" to cleanStudentName,
                            "studentEmail" to cleanStudentEmail,
                            "lastMessageText" to cleanBody,
                            "lastMessageSenderRole" to actor.roleEnum.name,
                            "lastMessageAtMillis" to nowMillis,
                            "unreadByAdminCount" to nextUnreadAdmin,
                            "unreadByStudentCount" to nextUnreadStudent,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        convRef,
                        mapOf(
                            "studentId" to targetStudentId,
                            "studentName" to cleanStudentName,
                            "studentEmail" to cleanStudentEmail,
                            "lastMessageText" to cleanBody,
                            "lastMessageSenderRole" to actor.roleEnum.name,
                            "lastMessageAtMillis" to nowMillis,
                            "unreadByAdminCount" to nextUnreadAdmin,
                            "unreadByStudentCount" to nextUnreadStudent,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.set(
                    msgRef,
                    mapOf(
                        "studentId" to targetStudentId,
                        "senderId" to uid,
                        "senderName" to cleanSenderName,
                        "senderRole" to actor.roleEnum.name,
                        "body" to cleanBody,
                        "sentAtMillis" to nowMillis,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markConversationRead(actor: UserEntity, studentUid: String): Result<Unit> {
        val docPath = "conversations/$studentUid"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                return Result.success(Unit)
            }
            if (!isValidDocId(studentUid)) {
                return Result.success(Unit)
            }
            if (actor.roleEnum != UserRole.ADMIN && uid != studentUid) {
                throw SecurityException("Access denied.")
            }

            val convRef = db.collection("conversations").document(studentUid)
            val snap = convRef.get().await()
            if (!snap.exists()) return Result.success(Unit)

            if (actor.roleEnum == UserRole.ADMIN) {
                val unread = snap.getLong("unreadByAdminCount")?.toInt() ?: 0
                if (unread > 0) {
                    convRef.update(
                        mapOf(
                            "unreadByAdminCount" to 0,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                val unread = snap.getLong("unreadByStudentCount")?.toInt() ?: 0
                if (unread > 0) {
                    convRef.update(
                        mapOf(
                            "unreadByStudentCount" to 0,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendWarningByAdmin(
        actor: UserEntity,
        student: UserEntity,
        severity: WarningSeverity,
        title: String,
        message: String
    ): Result<Unit> {
        var docPath = "warnings"
        return try {
            val uid = requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Admin privileges required to send warnings.")
            }
            val targetStudentId = student.id.ifBlank { student.userId }
            require(isValidDocId(targetStudentId)) { "Invalid student ID." }

            val cleanTitle = title.trim().take(120)
            val cleanMessage = message.trim().take(2000)
            require(cleanTitle.isNotEmpty()) { "Warning title is required." }
            require(cleanMessage.isNotEmpty()) { "Warning message is required." }

            val cleanStudentName = student.name.trim().ifEmpty { "Student" }.take(80)
            val cleanAdminName = actor.name.trim().ifEmpty { "Admin" }.take(80)
            val docRef = db.collection("warnings").document()
            docPath = docRef.path

            docRef.set(
                mapOf(
                    "studentId" to targetStudentId,
                    "studentName" to cleanStudentName,
                    "senderAdminId" to uid,
                    "senderAdminName" to cleanAdminName,
                    "severity" to severity.name,
                    "title" to cleanTitle,
                    "message" to cleanMessage,
                    "readByStudent" to false,
                    "sentAtMillis" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markWarningReadByStudent(actor: UserEntity, warning: StudentWarningEntity): Result<Unit> {
        val docPath = "warnings/${warning.id}"
        return try {
            val uid = requireUserId()
            if (warning.id.isBlank() || warning.readByStudent) return Result.success(Unit)
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot modify records.")
            }
            if (actor.roleEnum != UserRole.ADMIN && warning.studentId != uid) {
                throw SecurityException("Access denied.")
            }
            db.collection("warnings").document(warning.id).update(
                mapOf(
                    "readByStudent" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWarningByAdmin(actor: UserEntity, warningId: String): Result<Unit> {
        val docPath = "warnings/$warningId"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Admin privileges required.")
            }
            require(isValidDocId(warningId)) { "Invalid warning ID." }
            db.collection("warnings").document(warningId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun publishAnnouncementByAdmin(
        actor: UserEntity,
        title: String,
        message: String,
        priority: AnnouncementPriority
    ): Result<Unit> {
        var docPath = "announcements"
        return try {
            val uid = requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Admin privileges required to publish announcements.")
            }
            val cleanTitle = title.trim().take(120)
            val cleanMessage = message.trim().take(2500)
            require(cleanTitle.isNotEmpty()) { "Announcement title is required." }
            require(cleanMessage.isNotEmpty()) { "Announcement message cannot be empty." }

            val cleanAuthorName = actor.name.trim().ifEmpty { "Admin" }.take(80)
            val docRef = db.collection("announcements").document()
            docPath = docRef.path

            docRef.set(
                mapOf(
                    "title" to cleanTitle,
                    "message" to cleanMessage,
                    "priority" to priority.name,
                    "authorId" to uid,
                    "authorName" to cleanAuthorName,
                    "publishedAtMillis" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncementByAdmin(actor: UserEntity, announcementId: String): Result<Unit> {
        val docPath = "announcements/$announcementId"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Admin privileges required.")
            }
            require(isValidDocId(announcementId)) { "Invalid announcement ID." }
            db.collection("announcements").document(announcementId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markAnnouncementsRead(actor: UserEntity, latestPublishedMillis: Long): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docPath = "users/$uid"
        return try {
            if (actor.statusEnum == AccountStatus.SUSPENDED) return Result.success(Unit)
            if (latestPublishedMillis <= 0L || latestPublishedMillis <= actor.lastAnnouncementReadMillis) {
                return Result.success(Unit)
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "lastAnnouncementReadMillis" to latestPublishedMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- SMART TRADING DISCIPLINE ALERTS & WEEKLY REPORTS ---

    fun observeSmartAlertsForUser(actor: UserEntity, targetUserId: String): Flow<List<SmartDisciplineAlertEntity>> = flow {
        val uid = requireUserId()
        if (actor.roleEnum != UserRole.ADMIN && uid != targetUserId) {
            emit(emptyList())
            return@flow
        }
        val path = "smart_discipline_alerts"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", targetUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SmartDisciplineAlertEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<SmartDisciplineAlertEntity> { !it.isActiveUnacknowledged }
                                .thenBy { it.acknowledged }
                                .thenByDescending { it.effectiveTimestampMillis }
                        )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllSmartAlerts(actor: UserEntity): Flow<List<SmartDisciplineAlertEntity>> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || actor.roleEnum != UserRole.ADMIN) return flowOf(emptyList())
        val path = "smart_discipline_alerts"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(SmartDisciplineAlertEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<SmartDisciplineAlertEntity> { !it.isActiveUnacknowledged }
                            .thenBy { it.acknowledged }
                            .thenByDescending { it.effectiveTimestampMillis }
                    )
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun syncSmartAlertsForUser(
        actor: UserEntity,
        targetUser: UserEntity,
        evaluation: SmartAlertEvaluationResult
    ): Result<List<SmartDisciplineAlertEntity>> {
        val targetUid = targetUser.id.ifBlank { targetUser.userId }
        var docPath = "smart_discipline_alerts"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                return Result.success(emptyList())
            }
            if (actor.roleEnum != UserRole.ADMIN && uid != targetUid) {
                throw SecurityException("Access denied.")
            }

            val newlyCreated = mutableListOf<SmartDisciplineAlertEntity>()
            for (alert in evaluation.newAlertsToCreate) {
                if (!isValidDocId(alert.id)) continue
                val ref = db.collection("smart_discipline_alerts").document(alert.id)
                docPath = ref.path
                val existingSnap = ref.get().await()
                if (!existingSnap.exists()) {
                    val safeFx = if (alert.exchangeRateUsed in 1.0..1000.0) alert.exchangeRateUsed else TradingAnalytics.DEFAULT_USD_TO_INR_RATE
                    ref.set(
                        mapOf(
                            "userId" to alert.userId,
                            "userName" to alert.userName.trim().ifEmpty { "Trader" }.take(80),
                            "alertType" to alert.alertType,
                            "triggerKey" to alert.triggerKey.take(128),
                            "strategyName" to alert.strategyName.take(120),
                            "stopLossCount" to alert.stopLossCount.coerceIn(0, 10000),
                            "lookbackCount" to alert.lookbackCount.coerceIn(0, 10000),
                            "recommendedBreakDays" to alert.recommendedBreakDays.coerceIn(0, 30),
                            "lossAmountUsd" to alert.lossAmountUsd.coerceAtLeast(0.0),
                            "lossAmountInr" to alert.lossAmountInr.coerceAtLeast(0.0),
                            "exchangeRateUsed" to safeFx,
                            "triggerDateIso" to alert.triggerDateIso.take(20),
                            "resolved" to alert.resolved,
                            "title" to alert.title.take(140),
                            "message" to alert.message.take(2000),
                            "acknowledged" to false,
                            "acknowledgedAtMillis" to 0L,
                            "triggeredAtMillis" to alert.triggeredAtMillis.coerceAtLeast(1L),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    newlyCreated.add(alert)
                }
            }

            for (upd in evaluation.existingAlertsToUpdate) {
                if (!isValidDocId(upd.alertId)) continue
                val ref = db.collection("smart_discipline_alerts").document(upd.alertId)
                docPath = ref.path
                val safeFx = if (upd.newExchangeRateUsed in 1.0..1000.0) upd.newExchangeRateUsed else TradingAnalytics.DEFAULT_USD_TO_INR_RATE
                ref.update(
                    mapOf(
                        "stopLossCount" to upd.newStopLossCount.coerceIn(0, 10000),
                        "lookbackCount" to upd.newLookbackCount.coerceIn(0, 10000),
                        "lossAmountUsd" to upd.newLossAmountUsd.coerceAtLeast(0.0),
                        "lossAmountInr" to upd.newLossAmountInr.coerceAtLeast(0.0),
                        "exchangeRateUsed" to safeFx,
                        "triggerDateIso" to upd.newTriggerDateIso.take(20),
                        "resolved" to upd.newResolved,
                        "title" to upd.newTitle.take(140),
                        "message" to upd.newMessage.take(2000),
                        "triggeredAtMillis" to upd.newTriggeredAtMillis.coerceAtLeast(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(newlyCreated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeSmartAlert(
        actor: UserEntity,
        alert: SmartDisciplineAlertEntity,
        breakDays: Int = 0
    ): Result<Unit> {
        val docPath = "smart_discipline_alerts/${alert.id}"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot modify alerts.")
            }
            if (actor.roleEnum != UserRole.ADMIN && alert.userId != uid) {
                throw SecurityException("Access denied.")
            }
            if (!isValidDocId(alert.id)) {
                throw IllegalArgumentException("Invalid alert ID.")
            }

            val nowMillis = System.currentTimeMillis()
            val safeBreakDays = breakDays.coerceIn(0, 30)

            db.collection("smart_discipline_alerts").document(alert.id).update(
                mapOf(
                    "acknowledged" to true,
                    "acknowledgedAtMillis" to nowMillis,
                    "recommendedBreakDays" to safeBreakDays,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeWeeklyReportsForUser(actor: UserEntity, targetUserId: String): Flow<List<WeeklyReportEntity>> = flow {
        val uid = requireUserId()
        if (actor.roleEnum != UserRole.ADMIN && uid != targetUserId) {
            emit(emptyList())
            return@flow
        }
        val path = "weekly_reports"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", targetUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(WeeklyReportEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.weekKey }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllWeeklyReports(actor: UserEntity): Flow<List<WeeklyReportEntity>> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || actor.roleEnum != UserRole.ADMIN) return flowOf(emptyList())
        val path = "weekly_reports"
        return db.collection(path)
            .snapshots()
            .map { snap ->
                snap.toObjects(WeeklyReportEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.weekKey }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun saveWeeklyReports(
        actor: UserEntity,
        reports: List<WeeklyReportEntity>,
        overwriteExisting: Boolean = false
    ): Result<List<WeeklyReportEntity>> {
        var docPath = "weekly_reports"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                return Result.success(emptyList())
            }
            val newlyCreated = mutableListOf<WeeklyReportEntity>()
            for (report in reports) {
                if (actor.roleEnum != UserRole.ADMIN && report.userId != uid) continue
                if (!isValidDocId(report.id)) continue
                val ref = db.collection("weekly_reports").document(report.id)
                docPath = ref.path
                val existingSnap = ref.get().await()
                if (!existingSnap.exists()) {
                    ref.set(
                        mapOf(
                            "userId" to report.userId,
                            "userName" to report.userName.trim().ifEmpty { "Trader" }.take(80),
                            "weekKey" to report.weekKey.take(24),
                            "weekStartIso" to report.weekStartIso.take(10),
                            "weekEndIso" to report.weekEndIso.take(10),
                            "currencyCode" to report.currencyEnum.name,
                            "totalTrades" to report.totalTrades.coerceAtLeast(0),
                            "winningTrades" to report.winningTrades.coerceAtLeast(0),
                            "losingTrades" to report.losingTrades.coerceAtLeast(0),
                            "breakEvenTrades" to report.breakEvenTrades.coerceAtLeast(0),
                            "stopLossHits" to report.stopLossHits.coerceAtLeast(0),
                            "netPnl" to report.netPnl,
                            "winRatePercent" to report.winRatePercent.coerceIn(0.0, 100.0),
                            "disciplineScore" to report.disciplineScore.coerceIn(0, 100),
                            "overtradingDays" to report.overtradingDays.coerceAtLeast(0),
                            "revengeTradingCount" to report.revengeTradingCount.coerceAtLeast(0),
                            "noStopLossCount" to report.noStopLossCount.coerceAtLeast(0),
                            "ruleViolationsCount" to report.ruleViolationsCount.coerceAtLeast(0),
                            "repeatedMistakes" to report.repeatedMistakes.take(50),
                            "poorStrategies" to report.poorStrategies.take(50),
                            "ruleViolationDetails" to report.ruleViolationDetails.take(50),
                            "improvementAreas" to report.improvementAreas.take(50),
                            "personalizedSuggestions" to report.personalizedSuggestions.take(50),
                            "summaryText" to report.summaryText.take(1500),
                            "readByUser" to false,
                            "generatedAtMillis" to report.generatedAtMillis.coerceAtLeast(1L),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    newlyCreated.add(report)
                } else if (overwriteExisting) {
                    ref.update(
                        mapOf(
                            "userName" to report.userName.trim().ifEmpty { "Trader" }.take(80),
                            "currencyCode" to report.currencyEnum.name,
                            "totalTrades" to report.totalTrades.coerceAtLeast(0),
                            "winningTrades" to report.winningTrades.coerceAtLeast(0),
                            "losingTrades" to report.losingTrades.coerceAtLeast(0),
                            "breakEvenTrades" to report.breakEvenTrades.coerceAtLeast(0),
                            "stopLossHits" to report.stopLossHits.coerceAtLeast(0),
                            "netPnl" to report.netPnl,
                            "winRatePercent" to report.winRatePercent.coerceIn(0.0, 100.0),
                            "disciplineScore" to report.disciplineScore.coerceIn(0, 100),
                            "overtradingDays" to report.overtradingDays.coerceAtLeast(0),
                            "revengeTradingCount" to report.revengeTradingCount.coerceAtLeast(0),
                            "noStopLossCount" to report.noStopLossCount.coerceAtLeast(0),
                            "ruleViolationsCount" to report.ruleViolationsCount.coerceAtLeast(0),
                            "repeatedMistakes" to report.repeatedMistakes.take(50),
                            "poorStrategies" to report.poorStrategies.take(50),
                            "ruleViolationDetails" to report.ruleViolationDetails.take(50),
                            "improvementAreas" to report.improvementAreas.take(50),
                            "personalizedSuggestions" to report.personalizedSuggestions.take(50),
                            "summaryText" to report.summaryText.take(1500),
                            "generatedAtMillis" to report.generatedAtMillis.coerceAtLeast(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(newlyCreated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markWeeklyReportRead(actor: UserEntity, report: WeeklyReportEntity): Result<Unit> {
        val docPath = "weekly_reports/${report.id}"
        return try {
            val uid = requireUserId()
            if (report.id.isBlank() || report.readByUser) return Result.success(Unit)
            if (actor.statusEnum == AccountStatus.SUSPENDED) return Result.success(Unit)
            if (actor.roleEnum != UserRole.ADMIN && report.userId != uid) {
                throw SecurityException("Access denied.")
            }
            db.collection("weekly_reports").document(report.id).update(
                mapOf(
                    "readByUser" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- STUDENT PROFILE & FIREBASE STORAGE PHOTO MANAGEMENT ---

    suspend fun updateStudentProfileDetails(
        actor: UserEntity,
        targetUser: UserEntity,
        fullName: String,
        mobileNumber: String,
        whatsappNumber: String,
        dateOfBirth: String,
        gender: String,
        fullAddress: String,
        editedEmail: String? = null
    ): Result<UserEntity> {
        val targetUid = targetUser.id.ifBlank { targetUser.userId }
        val docPath = "users/$targetUid"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot update profile details.")
            }
            if (actor.roleEnum != UserRole.ADMIN && uid != targetUid) {
                throw SecurityException("Access denied: You can only update your own profile.")
            }
            require(isValidDocId(targetUid)) { "Invalid user ID." }

            val cleanName = fullName.trim().take(80)
            val cleanMobile = mobileNumber.trim().take(30)
            val cleanWhatsapp = whatsappNumber.trim().take(30)
            val cleanDob = dateOfBirth.trim().take(20)
            val cleanGender = gender.trim()
            val cleanAddress = fullAddress.trim().take(300)

            require(cleanName.length >= 2) { "Full name must be at least 2 characters." }
            require(cleanMobile.count { it.isDigit() } in 7..15) {
                "Enter a valid Mobile Number (7–15 digits)."
            }
            require(cleanWhatsapp.count { it.isDigit() } in 7..15) {
                "Enter a valid WhatsApp Number (7–15 digits)."
            }
            require(cleanDob.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                "Select a valid Date of Birth (YYYY-MM-DD)."
            }
            require(cleanGender in ALLOWED_GENDERS) {
                "Please select your Gender."
            }
            require(cleanAddress.length >= 5) {
                "Full Address must be at least 5 characters."
            }

            val updates = hashMapOf<String, Any>(
                "name" to cleanName,
                "mobileNumber" to cleanMobile,
                "whatsappNumber" to cleanWhatsapp,
                "dateOfBirth" to cleanDob,
                "gender" to cleanGender,
                "fullAddress" to cleanAddress,
                "profileCompleted" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val finalEmail = if (actor.roleEnum == UserRole.ADMIN && !editedEmail.isNullOrBlank()) {
                val cleanEmail = editedEmail.trim().lowercase(Locale.US).take(120)
                require(cleanEmail.length >= 3 && cleanEmail.contains("@") && cleanEmail.contains(".")) {
                    "Enter a valid Email Address."
                }
                updates["email"] = cleanEmail
                cleanEmail
            } else {
                targetUser.email
            }

            db.collection("users").document(targetUid).update(updates).await()

            Result.success(
                targetUser.copy(
                    name = cleanName,
                    email = finalEmail,
                    mobileNumber = cleanMobile,
                    whatsappNumber = cleanWhatsapp,
                    dateOfBirth = cleanDob,
                    gender = cleanGender,
                    fullAddress = cleanAddress,
                    profileCompleted = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun uploadStudentProfilePhoto(
        actor: UserEntity,
        targetUser: UserEntity,
        imageUri: Uri
    ): Result<UserEntity> {
        val targetUid = targetUser.id.ifBlank { targetUser.userId }
        val docPath = "users/$targetUid"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot upload profile photos.")
            }
            if (actor.roleEnum != UserRole.ADMIN && uid != targetUid) {
                throw SecurityException("Access denied: You can only update your own profile photo.")
            }
            require(isValidDocId(targetUid)) { "Invalid user ID." }

            val compressedBytes = encodeProfilePhotoBytes(imageUri).getOrThrow()
            val storagePath = "student_profiles/$targetUid/avatar_${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()

            try {
                storageRef.putBytes(compressedBytes, metadata).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw formatStorageException(e, storagePath)
            }
            val downloadUrl = try {
                storageRef.downloadUrl.await().toString().take(1500)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw formatStorageException(e, storagePath)
            }
            require(downloadUrl.isNotBlank()) { "Unable to retrieve uploaded photo URL." }

            db.collection("users").document(targetUid).update(
                mapOf(
                    "profilePhotoUrl" to downloadUrl,
                    "profilePhotoPath" to storagePath,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val oldPath = targetUser.profilePhotoPath
            if (oldPath.isNotBlank() && oldPath != storagePath) {
                runCatching { storage.reference.child(oldPath).delete().await() }
            }

            Result.success(
                targetUser.copy(
                    profilePhotoUrl = downloadUrl,
                    profilePhotoPath = storagePath
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStudentProfilePhoto(
        actor: UserEntity,
        targetUser: UserEntity
    ): Result<UserEntity> {
        val targetUid = targetUser.id.ifBlank { targetUser.userId }
        val docPath = "users/$targetUid"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot delete profile photos.")
            }
            if (actor.roleEnum != UserRole.ADMIN && uid != targetUid) {
                throw SecurityException("Access denied: You can only delete your own profile photo.")
            }
            require(isValidDocId(targetUid)) { "Invalid user ID." }

            val storagePath = targetUser.profilePhotoPath.takeIf { it.isNotBlank() }
                ?: "student_profiles/$targetUid/avatar.jpg"

            try {
                storage.reference.child(storagePath).delete().await()
            } catch (se: StorageException) {
                if (se.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                    throw se
                }
            }

            db.collection("users").document(targetUid).update(
                mapOf(
                    "profilePhotoUrl" to "",
                    "profilePhotoPath" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                targetUser.copy(
                    profilePhotoUrl = "",
                    profilePhotoPath = ""
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private fun decodeNormalizedBitmap(rawBytes: ByteArray): Bitmap {
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: throw IllegalArgumentException("Unsupported image format.")
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(rawBytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return decoded
        }
        return try {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        } catch (_: Throwable) {
            decoded
        }
    }

    private fun formatStorageException(err: Throwable, storagePath: String): Exception {
        val se = generateSequence(err) { it.cause }
            .filterIsInstance<StorageException>()
            .firstOrNull() ?: return if (err is Exception) err else IllegalStateException(err.message ?: "Storage error", err)

        val bucket = runCatching { storage.app.options.storageBucket.orEmpty() }.getOrDefault("")
        val detail = when (se.errorCode) {
            StorageException.ERROR_BUCKET_NOT_FOUND,
            StorageException.ERROR_PROJECT_NOT_FOUND ->
                "Firebase Storage is not initialized for bucket '$bucket'. Enable Storage and publish storage.rules in Firebase Console."
            StorageException.ERROR_NOT_AUTHORIZED ->
                "Firebase Storage upload denied by storage.rules ($storagePath). Verify your verified account permissions and published storage.rules."
            StorageException.ERROR_NOT_AUTHENTICATED ->
                "Sign in with Google before uploading files to Firebase Storage."
            else ->
                "Firebase Storage upload failed ($storagePath): ${se.localizedMessage ?: "code ${se.errorCode}"}"
        }
        return IllegalStateException(detail, se)
    }

    private suspend fun encodeProfilePhotoBytes(uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected profile image.")
            val original = decodeNormalizedBitmap(rawBytes)

            val maxDim = 512
            val scale = minOf(1f, maxDim.toFloat() / max(original.width, original.height).toFloat())
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).roundToInt().coerceAtLeast(1),
                    (original.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }

            var quality = 85
            var jpegBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                jpegBytes = out.toByteArray()
                quality -= 10
            } while (jpegBytes.size > 200_000 && quality >= 35)

            if (scaled !== original) scaled.recycle()
            original.recycle()

            require(jpegBytes.isNotEmpty()) { "Selected image produced empty data." }
            jpegBytes
        }
    }

    // --- ADMIN TRADE DESK, SCREENSHOTS, VOICE MESSAGES, REACTIONS & SIGNAL-ONLY BLOCKING ---

    fun observeSignalAccessRecord(uid: String): Flow<SignalAccessRecord?> {
        if (uid.isBlank() || !isValidDocId(uid)) return flowOf(null)
        return db.collection("signal_access").document(uid)
            .snapshots()
            .map { snap -> snap.toObject(SignalAccessRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { emit(null) }
    }

    fun observeAllSignalAccessRecords(actor: UserEntity): Flow<List<SignalAccessRecord>> {
        if (actor.roleEnum != UserRole.ADMIN) return flowOf(emptyList())
        return db.collection("signal_access")
            .snapshots()
            .map { snap -> snap.toObjects(SignalAccessRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "signal_access")
                emit(emptyList())
            }
    }

    suspend fun setStudentSignalAccess(
        actor: UserEntity,
        student: UserEntity,
        access: SignalAccess
    ): Result<Unit> {
        val targetUid = student.id.ifBlank { student.userId }
        val docPath = "signal_access/$targetUid"
        return try {
            val adminUid = requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Only Admin can update Trade Signal access.")
            }
            require(isValidDocId(targetUid)) { "Invalid student ID." }

            val nowMillis = System.currentTimeMillis()
            val batch = db.batch()
            val userRef = db.collection("users").document(targetUid)
            batch.update(
                userRef,
                mapOf(
                    "signalAccess" to access.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val signalAccessRef = db.collection("signal_access").document(targetUid)
            val existingSnap = signalAccessRef.get().await()
            if (existingSnap.exists()) {
                batch.update(
                    signalAccessRef,
                    mapOf(
                        "studentName" to student.name.take(80).ifBlank { "Student" },
                        "studentEmail" to student.email.take(120).ifBlank { "student@unknown.com" },
                        "signalAccess" to access.name,
                        "updatedByUid" to adminUid,
                        "updatedAtMillis" to nowMillis,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    signalAccessRef,
                    hashMapOf(
                        "userId" to targetUid,
                        "studentName" to student.name.take(80).ifBlank { "Student" },
                        "studentEmail" to student.email.take(120).ifBlank { "student@unknown.com" },
                        "signalAccess" to access.name,
                        "updatedByUid" to adminUid,
                        "updatedAtMillis" to nowMillis,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeTradeSignalsForUser(
        actor: UserEntity,
        onStreamError: ((String?) -> Unit)? = null
    ): Flow<List<TradeSignalEntity>> {
        val uid = actor.id.ifBlank { actor.userId }.ifBlank { auth.currentUser?.uid.orEmpty() }
        if (uid.isBlank() || actor.statusEnum == AccountStatus.SUSPENDED) {
            onStreamError?.invoke(null)
            return flowOf(emptyList())
        }
        if (actor.roleEnum == UserRole.ADMIN) {
            return db.collection("trade_signals")
                .snapshots()
                .map { snap ->
                    onStreamError?.invoke(null)
                    snap.toObjects(TradeSignalEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAtMillis }
                }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, "trade_signals")
                    val root = unwrapFirestoreError(error)
                    onStreamError?.invoke(root?.message ?: error.message ?: "Failed to load Admin Trade Signals.")
                    emit(emptyList())
                }
        }

        if (actor.signalAccessEnum == SignalAccess.BLOCKED) {
            onStreamError?.invoke(null)
            return flowOf(emptyList())
        }

        val allAudienceFlow = db.collection("trade_signals")
            .whereEqualTo("audienceType", SignalAudienceType.ALL_STUDENTS.name)
            .snapshots()
            .map {
                onStreamError?.invoke(null)
                it.toObjects(TradeSignalEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "trade_signals")
                val root = unwrapFirestoreError(error)
                onStreamError?.invoke(root?.message ?: error.message ?: "Unable to sync Trade Signals.")
                emit(emptyList())
            }

        val targetedAudienceFlow = db.collection("trade_signals")
            .whereArrayContains("selectedStudentIds", uid)
            .snapshots()
            .map {
                it.toObjects(TradeSignalEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "trade_signals")
                val root = unwrapFirestoreError(error)
                onStreamError?.invoke(root?.message ?: error.message ?: "Unable to sync targeted Trade Signals.")
                emit(emptyList())
            }

        val accessFlow = observeSignalAccessRecord(uid)

        return combine(allAudienceFlow, targetedAudienceFlow, accessFlow) { allList, targetedList, accessRec ->
            if (accessRec?.signalAccessEnum == SignalAccess.BLOCKED) {
                emptyList()
            } else {
                (allList + targetedList)
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAtMillis }
            }
        }
    }

    fun observeReactionsForSignal(signalId: String): Flow<List<TradeSignalReactionEntity>> {
        if (signalId.isBlank() || !isValidDocId(signalId)) return flowOf(emptyList())
        return db.collection("trade_signals").document(signalId)
            .collection("reactions")
            .snapshots()
            .map { snap ->
                snap.toObjects(TradeSignalReactionEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.reactedAtMillis }
            }
            .catch { emit(emptyList()) }
    }

    suspend fun publishOrUpdateTradeSignal(
        actor: UserEntity,
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
        removeExistingVoice: Boolean
    ): Result<TradeSignalEntity> {
        val signalRef = if (existingSignal != null && existingSignal.id.isNotBlank()) {
            db.collection("trade_signals").document(existingSignal.id)
        } else {
            db.collection("trade_signals").document()
        }
        val signalId = signalRef.id
        val docPath = "trade_signals/$signalId"

        return try {
            val uid = requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Only Admin can publish or edit trade signals.")
            }

            val cleanInstrument = instrument.trim().uppercase(Locale.US).take(40)
            require(cleanInstrument.isNotBlank()) { "Instrument / Index name is required." }
            require(entryPrice > 0.0) { "Entry price must be greater than 0." }
            require(stopLoss >= 0.0) { "Stop loss cannot be negative." }
            require(target1 > 0.0) { "Target 1 must be greater than 0." }
            if (target2 != null) require(target2 > 0.0) { "Target 2 must be greater than 0." }

            val cleanStudentIds = if (audienceType == SignalAudienceType.SELECTED_STUDENTS) {
                selectedStudentIds.distinct().filter { isValidDocId(it) }.take(500)
            } else {
                emptyList()
            }
            if (audienceType == SignalAudienceType.SELECTED_STUDENTS) {
                require(cleanStudentIds.isNotEmpty()) { "Select at least one student for Selected Students mode." }
            }

            require(keptScreenshotUrls.size == keptScreenshotPaths.size) { "Screenshot metadata mismatch." }
            require(keptScreenshotUrls.size + newScreenshotUris.size <= 4) {
                "Maximum 4 screenshots allowed per trade signal."
            }

            val finalUrls = keptScreenshotUrls.toMutableList()
            val finalPaths = keptScreenshotPaths.toMutableList()
            val uploadTs = System.currentTimeMillis()

            newScreenshotUris.forEachIndexed { idx, uri ->
                val bytes = encodeSignalScreenshotBytes(uri).getOrThrow()
                val storagePath = "trade_signals/$signalId/screenshots/${uploadTs}_$idx.jpg"
                val ref = storage.reference.child(storagePath)
                val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
                try {
                    ref.putBytes(bytes, meta).await()
                    val dlUrl = ref.downloadUrl.await().toString().take(1500)
                    require(dlUrl.isNotBlank()) { "Uploaded screenshot URL was empty." }
                    finalUrls.add(dlUrl)
                    finalPaths.add(storagePath)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw formatStorageException(e, storagePath)
                }
            }

            removedScreenshotPaths.forEach { path ->
                if (path.isNotBlank()) {
                    runCatching { storage.reference.child(path).delete().await() }
                }
            }

            var finalVoiceUrl = if (removeExistingVoice) "" else existingVoiceUrl
            var finalVoicePath = if (removeExistingVoice) "" else existingVoicePath
            var finalVoiceDuration = if (removeExistingVoice) 0 else existingVoiceDurationSec

            if ((removeExistingVoice || newVoiceFile != null) && existingVoicePath.isNotBlank()) {
                runCatching { storage.reference.child(existingVoicePath).delete().await() }
            }

            if (newVoiceFile != null && newVoiceFile.exists()) {
                val voiceBytes = withContext(Dispatchers.IO) { newVoiceFile.readBytes() }
                require(voiceBytes.size <= 14_500_000) { "Voice note exceeds 15 MB limit." }
                val voiceStoragePath = "trade_signals/$signalId/voice/voice_$uploadTs.m4a"
                val vRef = storage.reference.child(voiceStoragePath)
                val vMeta = StorageMetadata.Builder().setContentType("audio/mp4").build()
                try {
                    vRef.putBytes(voiceBytes, vMeta).await()
                    finalVoiceUrl = vRef.downloadUrl.await().toString().take(1500)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw formatStorageException(e, voiceStoragePath)
                }
                finalVoicePath = voiceStoragePath
                finalVoiceDuration = newVoiceDurationSec.coerceIn(1, 1800)
            }

            val rrLabel = TradeSignalEntity.calculateRiskRewardLabel(entryPrice, stopLoss, target1)
            val cleanStrategy = strategyName.trim().take(120)
            val cleanInstructions = instructions.trim().take(2500)
            val nowMillis = System.currentTimeMillis()

            val payload = hashMapOf<String, Any?>(
                "instrument" to cleanInstrument,
                "direction" to direction.name,
                "entryPrice" to entryPrice,
                "stopLoss" to stopLoss,
                "target1" to target1,
                "target2" to target2,
                "riskRewardRatio" to rrLabel,
                "strategyName" to cleanStrategy,
                "instructions" to cleanInstructions,
                "status" to status.name,
                "audienceType" to audienceType.name,
                "selectedStudentIds" to cleanStudentIds,
                "screenshotUrls" to finalUrls,
                "screenshotPaths" to finalPaths,
                "voiceNoteUrl" to finalVoiceUrl,
                "voiceNotePath" to finalVoicePath,
                "voiceDurationSec" to finalVoiceDuration,
                "updatedAtMillis" to nowMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (existingSignal == null || existingSignal.id.isBlank()) {
                payload["authorId"] = uid
                payload["authorName"] = actor.name.take(80).ifBlank { "Bangali Babu" }
                payload["createdAtMillis"] = nowMillis
                payload["createdAt"] = FieldValue.serverTimestamp()
                signalRef.set(payload).await()
            } else {
                signalRef.update(payload).await()
            }

            Result.success(
                TradeSignalEntity(
                    id = signalId,
                    instrument = cleanInstrument,
                    direction = direction.name,
                    entryPrice = entryPrice,
                    stopLoss = stopLoss,
                    target1 = target1,
                    target2 = target2,
                    riskRewardRatio = rrLabel,
                    strategyName = cleanStrategy,
                    instructions = cleanInstructions,
                    status = status.name,
                    audienceType = audienceType.name,
                    selectedStudentIds = cleanStudentIds,
                    screenshotUrls = finalUrls,
                    screenshotPaths = finalPaths,
                    voiceNoteUrl = finalVoiceUrl,
                    voiceNotePath = finalVoicePath,
                    voiceDurationSec = finalVoiceDuration,
                    authorId = existingSignal?.authorId?.ifBlank { uid } ?: uid,
                    authorName = existingSignal?.authorName?.ifBlank { actor.name } ?: actor.name,
                    createdAtMillis = existingSignal?.createdAtMillis ?: nowMillis,
                    updatedAtMillis = nowMillis
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTradeSignalStatus(
        actor: UserEntity,
        signal: TradeSignalEntity,
        newStatus: TradeSignalStatus
    ): Result<Unit> {
        val docPath = "trade_signals/${signal.id}"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Only Admin can update signal status.")
            }
            require(signal.id.isNotBlank()) { "Invalid signal ID." }
            db.collection("trade_signals").document(signal.id).update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAtMillis" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTradeSignal(actor: UserEntity, signal: TradeSignalEntity): Result<Unit> {
        val docPath = "trade_signals/${signal.id}"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Only Admin can delete signals.")
            }
            require(signal.id.isNotBlank()) { "Invalid signal ID." }

            signal.screenshotPaths.forEach { path ->
                if (path.isNotBlank()) {
                    runCatching { storage.reference.child(path).delete().await() }
                }
            }
            if (signal.voiceNotePath.isNotBlank()) {
                runCatching { storage.reference.child(signal.voiceNotePath).delete().await() }
            }

            val reactionsSnap = db.collection("trade_signals").document(signal.id)
                .collection("reactions").get().await()
            val batch = db.batch()
            reactionsSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.collection("trade_signals").document(signal.id))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTradeSignalReaction(
        actor: UserEntity,
        signal: TradeSignalEntity,
        emoji: String,
        existingUserReaction: TradeSignalReactionEntity?
    ): Result<Unit> {
        val uid = actor.id.ifBlank { actor.userId }.ifBlank { auth.currentUser?.uid.orEmpty() }
        val docPath = "trade_signals/${signal.id}/reactions/$uid"
        return try {
            requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Account is suspended.")
            }
            if (actor.roleEnum != UserRole.ADMIN && actor.signalAccessEnum == SignalAccess.BLOCKED) {
                throw SecurityException("Your Trade Signal access is currently blocked by Admin.")
            }
            require(emoji in TradeSignalEntity.ALLOWED_REACTIONS) { "Unsupported emoji reaction." }
            require(signal.id.isNotBlank()) { "Invalid signal." }

            val reactionRef = db.collection("trade_signals").document(signal.id)
                .collection("reactions").document(uid)

            if (existingUserReaction != null && existingUserReaction.emoji == emoji) {
                reactionRef.delete().await()
            } else if (existingUserReaction != null) {
                reactionRef.update(
                    mapOf(
                        "userName" to actor.name.take(80).ifBlank { "Trader" },
                        "emoji" to emoji,
                        "reactedAtMillis" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                reactionRef.set(
                    hashMapOf(
                        "signalId" to signal.id,
                        "userId" to uid,
                        "userName" to actor.name.take(80).ifBlank { "Trader" },
                        "emoji" to emoji,
                        "reactedAtMillis" to System.currentTimeMillis(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun encodeSignalScreenshotBytes(uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected screenshot.")
            val original = decodeNormalizedBitmap(rawBytes)

            val maxDim = 1440
            val scale = minOf(1f, maxDim.toFloat() / max(original.width, original.height).toFloat())
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).roundToInt().coerceAtLeast(1),
                    (original.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }

            var quality = 88
            var jpegBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                jpegBytes = out.toByteArray()
                quality -= 10
            } while (jpegBytes.size > 650_000 && quality >= 40)

            if (scaled !== original) scaled.recycle()
            original.recycle()

            require(jpegBytes.isNotEmpty()) { "Screenshot produced empty data." }
            jpegBytes
        }
    }

    // --- LIVE ONLINE CLASSROOM (FIRESTORE COORDINATION + MODERATION + LIVE CHAT) ---

    fun observeLiveClassroom(roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID): Flow<LiveClassroomEntity?> {
        if (!isValidDocId(roomId)) return flowOf(null)
        return db.collection("live_classrooms").document(roomId)
            .snapshots()
            .map { snap -> snap.toObject(LiveClassroomEntity::class.java) }
            .catch { emit(null) }
    }

    fun observeClassroomParticipants(
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Flow<List<LiveClassroomParticipantEntity>> {
        if (!isValidDocId(roomId)) return flowOf(emptyList())
        return db.collection("live_classrooms").document(roomId)
            .collection("participants")
            .snapshots()
            .map { snap ->
                snap.toObjects(LiveClassroomParticipantEntity::class.java)
                    .sortedWith(
                        compareBy<LiveClassroomParticipantEntity> { it.isRemoved }
                            .thenBy { it.roleEnum != UserRole.ADMIN }
                            .thenByDescending { it.handRaised }
                            .thenBy { it.joinedAtMillis }
                    )
            }
            .catch { emit(emptyList()) }
    }

    fun observeClassroomMessages(
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Flow<List<LiveClassroomMessageEntity>> {
        if (!isValidDocId(roomId)) return flowOf(emptyList())
        return db.collection("live_classrooms").document(roomId)
            .collection("messages")
            .snapshots()
            .map { snap ->
                snap.toObjects(LiveClassroomMessageEntity::class.java)
                    .sortedBy { it.sentAtMillis }
            }
            .catch { emit(emptyList()) }
    }

    suspend fun startLiveClassByAdmin(
        actor: UserEntity,
        title: String,
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Result<LiveClassroomEntity> {
        val docPath = "live_classrooms/$roomId"
        return try {
            val uid = requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Only Admin/Teacher can start Live Class.")
            }
            val cleanTitle = title.trim().ifBlank { "Live Market Classroom" }.take(120)
            val nowMillis = System.currentTimeMillis()
            val roomRef = db.collection("live_classrooms").document(roomId)
            val existing = roomRef.get().await()

            if (existing.exists()) {
                roomRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "roomName" to roomId,
                        "isLive" to true,
                        "isScreenSharing" to false,
                        "hostUid" to uid,
                        "hostName" to actor.name.take(80).ifBlank { "Bangali Babu" },
                        "startedAtMillis" to nowMillis,
                        "endedAtMillis" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                roomRef.set(
                    hashMapOf(
                        "title" to cleanTitle,
                        "roomName" to roomId,
                        "isLive" to true,
                        "isScreenSharing" to false,
                        "hostUid" to uid,
                        "hostName" to actor.name.take(80).ifBlank { "Bangali Babu" },
                        "startedAtMillis" to nowMillis,
                        "endedAtMillis" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Clear stale student participant records from previous ended sessions and register teacher
            val oldParticipants = roomRef.collection("participants").get().await()
            val batch = db.batch()
            oldParticipants.documents.forEach { pDoc ->
                if (pDoc.id != uid) batch.delete(pDoc.reference)
            }
            val teacherParticipantRef = roomRef.collection("participants").document(uid)
            val teacherExists = oldParticipants.documents.any { it.id == uid }
            if (teacherExists) {
                batch.update(
                    teacherParticipantRef,
                    mapOf(
                        "name" to actor.name.take(80).ifBlank { "Bangali Babu" },
                        "role" to UserRole.ADMIN.name,
                        "profilePhotoUrl" to actor.profilePhotoUrl.take(1500),
                        "joinedAtMillis" to nowMillis,
                        "handRaised" to false,
                        "voiceApproved" to true,
                        "isMutedByTeacher" to false,
                        "isSelfMuted" to true,
                        "isRemoved" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    teacherParticipantRef,
                    hashMapOf(
                        "uid" to uid,
                        "name" to actor.name.take(80).ifBlank { "Bangali Babu" },
                        "role" to UserRole.ADMIN.name,
                        "profilePhotoUrl" to actor.profilePhotoUrl.take(1500),
                        "joinedAtMillis" to nowMillis,
                        "handRaised" to false,
                        "voiceApproved" to true,
                        "isMutedByTeacher" to false,
                        "isSelfMuted" to true,
                        "isRemoved" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            Result.success(
                LiveClassroomEntity(
                    id = roomId,
                    title = cleanTitle,
                    roomName = roomId,
                    isLive = true,
                    isScreenSharing = false,
                    hostUid = uid,
                    hostName = actor.name,
                    startedAtMillis = nowMillis,
                    endedAtMillis = 0L
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setScreenSharingStateByAdmin(
        actor: UserEntity,
        isScreenSharing: Boolean,
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Result<Unit> {
        val docPath = "live_classrooms/$roomId"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) throw SecurityException("Admin only.")
            db.collection("live_classrooms").document(roomId).update(
                mapOf(
                    "isScreenSharing" to isScreenSharing,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun endLiveClassByAdmin(
        actor: UserEntity,
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Result<Unit> {
        val docPath = "live_classrooms/$roomId"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) throw SecurityException("Only Admin/Teacher can end Live Class.")
            db.collection("live_classrooms").document(roomId).update(
                mapOf(
                    "isLive" to false,
                    "isScreenSharing" to false,
                    "endedAtMillis" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinLiveClass(
        actor: UserEntity,
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Result<LiveClassroomParticipantEntity> {
        val uid = actor.id.ifBlank { actor.userId }.ifBlank { auth.currentUser?.uid.orEmpty() }
        val docPath = "live_classrooms/$roomId/participants/$uid"
        return try {
            requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot join Live Class.")
            }
            val partRef = db.collection("live_classrooms").document(roomId)
                .collection("participants").document(uid)
            val existing = partRef.get().await()
            val nowMillis = System.currentTimeMillis()
            val existingObj = existing.toObject(LiveClassroomParticipantEntity::class.java)

            if (existing.exists() && existingObj?.isRemoved == true && actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("You were removed from this Live Class session by the Teacher.")
            }

            if (existing.exists()) {
                partRef.update(
                    mapOf(
                        "name" to actor.name.take(80).ifBlank { "Participant" },
                        "profilePhotoUrl" to actor.profilePhotoUrl.take(1500),
                        "joinedAtMillis" to nowMillis,
                        "handRaised" to false,
                        "isSelfMuted" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(
                    (existingObj ?: LiveClassroomParticipantEntity(id = uid, uid = uid)).copy(
                        name = actor.name.take(80),
                        profilePhotoUrl = actor.profilePhotoUrl.take(1500),
                        joinedAtMillis = nowMillis,
                        handRaised = false,
                        isSelfMuted = true
                    )
                )
            } else {
                val isTeacher = actor.roleEnum == UserRole.ADMIN
                partRef.set(
                    hashMapOf(
                        "uid" to uid,
                        "name" to actor.name.take(80).ifBlank { "Participant" },
                        "role" to actor.roleEnum.name,
                        "profilePhotoUrl" to actor.profilePhotoUrl.take(1500),
                        "joinedAtMillis" to nowMillis,
                        "handRaised" to false,
                        "voiceApproved" to isTeacher,
                        "isMutedByTeacher" to !isTeacher,
                        "isSelfMuted" to true,
                        "isRemoved" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(
                    LiveClassroomParticipantEntity(
                        id = uid,
                        uid = uid,
                        name = actor.name.take(80),
                        role = actor.roleEnum.name,
                        profilePhotoUrl = actor.profilePhotoUrl.take(1500),
                        joinedAtMillis = nowMillis,
                        handRaised = false,
                        voiceApproved = isTeacher,
                        isMutedByTeacher = !isTeacher,
                        isSelfMuted = true,
                        isRemoved = false
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun leaveLiveClass(
        actor: UserEntity,
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Result<Unit> {
        val uid = actor.id.ifBlank { actor.userId }.ifBlank { auth.currentUser?.uid.orEmpty() }
        val docPath = "live_classrooms/$roomId/participants/$uid"
        return try {
            requireUserId()
            db.collection("live_classrooms").document(roomId)
                .collection("participants").document(uid)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOwnClassroomAudioControls(
        actor: UserEntity,
        handRaised: Boolean? = null,
        isSelfMuted: Boolean? = null,
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Result<Unit> {
        val uid = actor.id.ifBlank { actor.userId }.ifBlank { auth.currentUser?.uid.orEmpty() }
        val docPath = "live_classrooms/$roomId/participants/$uid"
        return try {
            requireUserId()
            val updates = hashMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (handRaised != null) updates["handRaised"] = handRaised
            if (isSelfMuted != null) updates["isSelfMuted"] = isSelfMuted
            db.collection("live_classrooms").document(roomId)
                .collection("participants").document(uid)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun moderateClassroomParticipantByAdmin(
        actor: UserEntity,
        participantUid: String,
        voiceApproved: Boolean? = null,
        isMutedByTeacher: Boolean? = null,
        handRaised: Boolean? = null,
        isRemoved: Boolean? = null,
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Result<Unit> {
        val docPath = "live_classrooms/$roomId/participants/$participantUid"
        return try {
            requireUserId()
            if (actor.roleEnum != UserRole.ADMIN) {
                throw SecurityException("Only Teacher/Admin can moderate classroom participants.")
            }
            require(isValidDocId(participantUid)) { "Invalid participant ID." }
            val updates = hashMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (voiceApproved != null) updates["voiceApproved"] = voiceApproved
            if (isMutedByTeacher != null) updates["isMutedByTeacher"] = isMutedByTeacher
            if (handRaised != null) updates["handRaised"] = handRaised
            if (isRemoved != null) updates["isRemoved"] = isRemoved
            db.collection("live_classrooms").document(roomId)
                .collection("participants").document(participantUid)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendClassroomChatMessage(
        actor: UserEntity,
        body: String,
        roomId: String = LiveClassroomEntity.DEFAULT_ROOM_ID
    ): Result<Unit> {
        val uid = actor.id.ifBlank { actor.userId }.ifBlank { auth.currentUser?.uid.orEmpty() }
        val msgRef = db.collection("live_classrooms").document(roomId).collection("messages").document()
        val docPath = "live_classrooms/$roomId/messages/${msgRef.id}"
        return try {
            requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot chat in Live Class.")
            }
            val cleanBody = body.trim().take(1500)
            require(cleanBody.isNotBlank()) { "Message cannot be empty." }
            msgRef.set(
                hashMapOf(
                    "roomId" to roomId,
                    "senderId" to uid,
                    "senderName" to actor.name.take(80).ifBlank { "Participant" },
                    "senderRole" to actor.roleEnum.name,
                    "body" to cleanBody,
                    "sentAtMillis" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }
}
