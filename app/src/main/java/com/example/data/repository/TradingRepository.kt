package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class TradingRepository(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val sessionPreferences: SessionPreferences,
    private val auth: FirebaseAuth = Firebase.auth
) {
    companion object {
        const val BOOTSTRAP_ADMIN_EMAIL = "tradingwithbengalibabu@gmail.com"
        private const val MAX_SCREENSHOT_BASE64_LENGTH = 320_000
    }

    val isDarkMode: Flow<Boolean> = sessionPreferences.isDarkMode

    suspend fun setDarkMode(isDark: Boolean) {
        sessionPreferences.setDarkMode(isDark)
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

            val batch = db.batch()
            studentTrades.documents.forEach { batch.delete(it.reference) }
            studentReviews.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.collection("users").document(studentId))
            batch.commit().await()
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
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            if (actor.statusEnum == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot modify settings.")
            }
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            ref.update(
                mapOf(
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
        notes: String
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

            val cleanRisk = riskAmount.coerceAtLeast(0.0)
            val cleanUserName = targetUserName.trim().ifEmpty { actor.name }.take(80)
            val cleanStrategy = strategy.trim().take(120)
            val cleanEntryReason = entryReason.trim().take(1000)
            val cleanExitReason = exitReason.trim().take(1000)
            val cleanMistakes = mistakes.trim().take(500)
            val cleanLessons = lessonsLearned.trim().take(1000)
            val cleanNotes = notes.trim().take(1500)

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
                    "instrument" to cleanInstrument,
                    "direction" to direction.name,
                    "timeframe" to timeframe.name,
                    "entryPrice" to entryPrice,
                    "exitPrice" to exitPrice,
                    "stopLoss" to stopLoss,
                    "takeProfit" to takeProfit,
                    "lotSize" to lotSize,
                    "riskAmount" to cleanRisk,
                    "actualPnl" to actualPnl,
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
                        instrument = cleanInstrument,
                        direction = direction.name,
                        timeframe = timeframe.name,
                        entryPrice = entryPrice,
                        exitPrice = exitPrice,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        lotSize = lotSize,
                        riskAmount = cleanRisk,
                        actualPnl = actualPnl,
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
                    "instrument" to cleanInstrument,
                    "direction" to direction.name,
                    "timeframe" to timeframe.name,
                    "entryPrice" to entryPrice,
                    "exitPrice" to exitPrice,
                    "stopLoss" to stopLoss,
                    "takeProfit" to takeProfit,
                    "lotSize" to lotSize,
                    "riskAmount" to cleanRisk,
                    "actualPnl" to actualPnl,
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
                        instrument = cleanInstrument,
                        direction = direction.name,
                        timeframe = timeframe.name,
                        entryPrice = entryPrice,
                        exitPrice = exitPrice,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        lotSize = lotSize,
                        riskAmount = cleanRisk,
                        actualPnl = actualPnl,
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
                    put("dailyProfitGoal", u.dailyProfitGoal)
                    put("dailyMaxLossLimit", u.dailyMaxLossLimit)
                    put("maxTradesPerDay", u.maxTradesPerDay)
                    put("maxRiskPerTrade", u.maxRiskPerTrade)
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
                    put("instrument", t.instrument)
                    put("direction", t.direction)
                    put("timeframe", t.timeframe)
                    put("entryPrice", t.entryPrice)
                    put("exitPrice", t.exitPrice)
                    put("stopLoss", t.stopLoss ?: JSONObject.NULL)
                    put("takeProfit", t.takeProfit ?: JSONObject.NULL)
                    put("lotSize", t.lotSize)
                    put("riskAmount", t.riskAmount)
                    put("actualPnl", t.actualPnl)
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
                val result = TradeResult.entries.firstOrNull { it.name == o.optString("result") }?.name ?: TradeResult.BREAK_EVEN.name
                val sl = if (o.isNull("stopLoss")) null else o.optDouble("stopLoss").takeIf { !it.isNaN() && it >= 0.0 }
                val tp = if (o.isNull("takeProfit")) null else o.optDouble("takeProfit").takeIf { !it.isNaN() && it >= 0.0 }
                val shot = if (o.isNull("screenshotData")) null else o.optString("screenshotData").takeIf { it.isNotBlank() && it.length <= MAX_SCREENSHOT_BASE64_LENGTH }
                val dateIso = o.optString("tradeDateIso").let { if (it.length == 10) it else "2026-01-01" }
                val dateMillis = o.optLong("tradeDateMillis").let { if (it > 0L) it else System.currentTimeMillis() }

                if (existingSnap.exists()) {
                    batch.update(
                        ref,
                        hashMapOf<String, Any?>(
                            "userName" to o.optString("userName").ifBlank { actor.name }.take(80),
                            "tradeDateMillis" to dateMillis,
                            "tradeDateIso" to dateIso,
                            "instrument" to instrument,
                            "direction" to direction,
                            "timeframe" to timeframe,
                            "entryPrice" to o.optDouble("entryPrice", 1.0).coerceAtLeast(0.0001),
                            "exitPrice" to o.optDouble("exitPrice", 1.0).coerceAtLeast(0.0001),
                            "stopLoss" to sl,
                            "takeProfit" to tp,
                            "lotSize" to o.optDouble("lotSize", 1.0).coerceAtLeast(0.0001),
                            "riskAmount" to o.optDouble("riskAmount", 0.0).coerceAtLeast(0.0),
                            "actualPnl" to o.optDouble("actualPnl", 0.0),
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
                        "instrument" to instrument,
                        "direction" to direction,
                        "timeframe" to timeframe,
                        "entryPrice" to o.optDouble("entryPrice", 1.0).coerceAtLeast(0.0001),
                        "exitPrice" to o.optDouble("exitPrice", 1.0).coerceAtLeast(0.0001),
                        "stopLoss" to sl,
                        "takeProfit" to tp,
                        "lotSize" to o.optDouble("lotSize", 1.0).coerceAtLeast(0.0001),
                        "riskAmount" to o.optDouble("riskAmount", 0.0).coerceAtLeast(0.0),
                        "actualPnl" to o.optDouble("actualPnl", 0.0),
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
}
