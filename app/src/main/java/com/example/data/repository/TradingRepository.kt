package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.SessionPreferences
import com.example.data.model.AccountStatus
import com.example.data.model.DisciplineReviewEntity
import com.example.data.model.EmotionState
import com.example.data.model.ReviewPeriod
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

class TradingRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val sessionPreferences: SessionPreferences
) {
    private val userDao = database.userDao()
    private val tradeDao = database.tradeDao()
    private val reviewDao = database.disciplineReviewDao()

    val isDarkMode: Flow<Boolean> = sessionPreferences.isDarkMode

    val hasAdmin: Flow<Boolean> = userDao.observeCountByRole(UserRole.ADMIN)
        .let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { count -> emit(count > 0) }
            }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentUser: Flow<UserEntity?> = sessionPreferences.loggedInUserId
        .flatMapLatest { userId ->
            if (userId == null) flowOf(null)
            else userDao.observeUserById(userId)
        }

    suspend fun setDarkMode(isDark: Boolean) {
        sessionPreferences.setDarkMode(isDark)
    }

    suspend fun registerInitialAdmin(
        name: String,
        email: String,
        password: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = name.trim()
            val cleanEmail = email.trim().lowercase(Locale.US)
            require(cleanName.length >= 2) { "Admin name must be at least 2 characters." }
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Please enter a valid email address." }
            require(password.length >= 6) { "Password must be at least 6 characters." }
            check(userDao.getCountByRole(UserRole.ADMIN) == 0) {
                "An Admin account already exists. Please sign in instead."
            }
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(password, salt)
            val now = System.currentTimeMillis()
            val user = UserEntity(
                name = cleanName,
                email = cleanEmail,
                passwordHash = hash,
                passwordSalt = salt,
                role = UserRole.ADMIN,
                status = AccountStatus.ACTIVE,
                dailyProfitGoal = 500.0,
                dailyMaxLossLimit = 200.0,
                maxTradesPerDay = 6,
                maxRiskPerTrade = 100.0,
                createdAt = now,
                updatedAt = now
            )
            val id = userDao.insertUser(user)
            val created = user.copy(id = id)
            sessionPreferences.setLoggedInUserId(id)
            created
        }
    }

    suspend fun registerStudent(
        name: String,
        email: String,
        password: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = name.trim()
            val cleanEmail = email.trim().lowercase(Locale.US)
            require(cleanName.length >= 2) { "Full name must be at least 2 characters." }
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Please enter a valid email address." }
            require(password.length >= 6) { "Password must be at least 6 characters." }
            require(userDao.getUserByEmail(cleanEmail) == null) {
                "An account with this email already exists."
            }
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(password, salt)
            val now = System.currentTimeMillis()
            val user = UserEntity(
                name = cleanName,
                email = cleanEmail,
                passwordHash = hash,
                passwordSalt = salt,
                role = UserRole.STUDENT,
                status = AccountStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
            val id = userDao.insertUser(user)
            val created = user.copy(id = id)
            sessionPreferences.setLoggedInUserId(id)
            created
        }
    }

    suspend fun login(email: String, password: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanEmail = email.trim().lowercase(Locale.US)
            require(cleanEmail.isNotBlank() && password.isNotBlank()) {
                "Email and password are required."
            }
            val user = userDao.getUserByEmail(cleanEmail)
                ?: throw IllegalArgumentException("Invalid email or password.")
            val valid = PasswordHasher.verifyPassword(password, user.passwordSalt, user.passwordHash)
            if (!valid) {
                throw IllegalArgumentException("Invalid email or password.")
            }
            if (user.status == AccountStatus.SUSPENDED) {
                throw SecurityException("This account has been suspended by the Admin.")
            }
            sessionPreferences.setLoggedInUserId(user.id)
            user
        }
    }

    suspend fun logout() {
        sessionPreferences.setLoggedInUserId(null)
    }

    // --- ROLE-BASED ACCESS CONTROLLED QUERIES & MUTATIONS ---

    fun observeStudents(actor: UserEntity): Flow<List<UserEntity>> {
        if (actor.role != UserRole.ADMIN) return emptyFlow()
        return userDao.observeUsersByRole(UserRole.STUDENT)
    }

    fun observeTradesForUser(actor: UserEntity, targetUserId: Long): Flow<List<TradeEntity>> {
        if (actor.role != UserRole.ADMIN && actor.id != targetUserId) {
            return emptyFlow()
        }
        return tradeDao.observeTradesByUser(targetUserId)
    }

    fun observeAllTrades(actor: UserEntity): Flow<List<TradeEntity>> {
        if (actor.role != UserRole.ADMIN) return emptyFlow()
        return tradeDao.observeAllTrades()
    }

    fun observeReviewsForUser(actor: UserEntity, targetUserId: Long): Flow<List<DisciplineReviewEntity>> {
        if (actor.role != UserRole.ADMIN && actor.id != targetUserId) {
            return emptyFlow()
        }
        return reviewDao.observeReviewsByUser(targetUserId)
    }

    suspend fun createStudentByAdmin(
        actor: UserEntity,
        name: String,
        email: String,
        password: String,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        runCatching {
            if (actor.role != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            val cleanName = name.trim()
            val cleanEmail = email.trim().lowercase(Locale.US)
            require(cleanName.length >= 2) { "Student name must be at least 2 characters." }
            require(cleanEmail.contains("@")) { "Valid email required." }
            require(password.length >= 6) { "Temporary password must be at least 6 characters." }
            require(userDao.getUserByEmail(cleanEmail) == null) { "Email is already registered." }

            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(password, salt)
            val now = System.currentTimeMillis()
            val student = UserEntity(
                name = cleanName,
                email = cleanEmail,
                passwordHash = hash,
                passwordSalt = salt,
                role = UserRole.STUDENT,
                status = AccountStatus.ACTIVE,
                dailyProfitGoal = dailyGoal.coerceAtLeast(0.0),
                dailyMaxLossLimit = dailyMaxLoss.coerceAtLeast(0.0),
                maxTradesPerDay = maxTradesPerDay.coerceIn(1, 100),
                maxRiskPerTrade = maxRiskPerTrade.coerceAtLeast(0.0),
                createdAt = now,
                updatedAt = now
            )
            val id = userDao.insertUser(student)
            student.copy(id = id)
        }
    }

    suspend fun updateStudentByAdmin(
        actor: UserEntity,
        student: UserEntity,
        newName: String,
        newEmail: String,
        newPassword: String?,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (actor.role != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            if (student.role != UserRole.STUDENT) throw SecurityException("Cannot modify non-student here.")
            val cleanName = newName.trim()
            val cleanEmail = newEmail.trim().lowercase(Locale.US)
            require(cleanName.length >= 2) { "Name must be at least 2 characters." }
            require(cleanEmail.contains("@")) { "Valid email required." }

            val existingWithEmail = userDao.getUserByEmail(cleanEmail)
            if (existingWithEmail != null && existingWithEmail.id != student.id) {
                throw IllegalArgumentException("Another user already uses this email.")
            }

            var updatedSalt = student.passwordSalt
            var updatedHash = student.passwordHash
            if (!newPassword.isNullOrBlank()) {
                require(newPassword.length >= 6) { "New password must be at least 6 characters." }
                updatedSalt = PasswordHasher.generateSalt()
                updatedHash = PasswordHasher.hashPassword(newPassword, updatedSalt)
            }

            userDao.updateUser(
                student.copy(
                    name = cleanName,
                    email = cleanEmail,
                    passwordSalt = updatedSalt,
                    passwordHash = updatedHash,
                    dailyProfitGoal = dailyGoal.coerceAtLeast(0.0),
                    dailyMaxLossLimit = dailyMaxLoss.coerceAtLeast(0.0),
                    maxTradesPerDay = maxTradesPerDay.coerceIn(1, 100),
                    maxRiskPerTrade = maxRiskPerTrade.coerceAtLeast(0.0),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun setStudentSuspended(
        actor: UserEntity,
        student: UserEntity,
        suspended: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (actor.role != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            if (student.role != UserRole.STUDENT) throw SecurityException("Cannot suspend Admin account.")
            userDao.updateUser(
                student.copy(
                    status = if (suspended) AccountStatus.SUSPENDED else AccountStatus.ACTIVE,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteStudentByAdmin(actor: UserEntity, studentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (actor.role != UserRole.ADMIN) throw SecurityException("Admin privileges required.")
            val target = userDao.getUserById(studentId) ?: throw IllegalArgumentException("Student not found.")
            if (target.role == UserRole.ADMIN) throw SecurityException("Cannot delete Admin account.")
            userDao.deleteUserById(studentId)
        }
    }

    suspend fun updateOwnDisciplineGoals(
        actor: UserEntity,
        dailyGoal: Double,
        dailyMaxLoss: Double,
        maxTradesPerDay: Int,
        maxRiskPerTrade: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fresh = userDao.getUserById(actor.id) ?: throw IllegalStateException("User not found.")
            userDao.updateUser(
                fresh.copy(
                    dailyProfitGoal = dailyGoal.coerceAtLeast(0.0),
                    dailyMaxLossLimit = dailyMaxLoss.coerceAtLeast(0.0),
                    maxTradesPerDay = maxTradesPerDay.coerceIn(1, 100),
                    maxRiskPerTrade = maxRiskPerTrade.coerceAtLeast(0.0),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun saveTrade(
        actor: UserEntity,
        existingTradeId: Long?,
        targetUserId: Long,
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
        screenshotPath: String?,
        notes: String
    ): Result<TradeEntity> = withContext(Dispatchers.IO) {
        runCatching {
            if (actor.status == AccountStatus.SUSPENDED) {
                throw SecurityException("Suspended accounts cannot add or modify trades.")
            }
            if (actor.role != UserRole.ADMIN && actor.id != targetUserId) {
                throw SecurityException("Access denied: You can only manage your own trades.")
            }
            require(instrument.isNotBlank()) { "Trading instrument is required." }
            require(entryPrice > 0.0) { "Entry price must be greater than 0." }
            require(exitPrice > 0.0) { "Exit price must be greater than 0." }
            require(lotSize > 0.0) { "Lot size must be greater than 0." }

            val now = System.currentTimeMillis()
            if (existingTradeId != null && existingTradeId > 0L) {
                val existing = tradeDao.getTradeById(existingTradeId)
                    ?: throw IllegalArgumentException("Trade not found.")
                if (actor.role != UserRole.ADMIN && existing.userId != actor.id) {
                    throw SecurityException("Access denied: Cannot edit another student's trade.")
                }
                val updated = existing.copy(
                    userName = targetUserName.trim().ifEmpty { existing.userName },
                    tradeDateMillis = tradeDateMillis,
                    tradeDateIso = tradeDateIso,
                    instrument = instrument.trim().uppercase(Locale.US),
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
                    strategy = strategy.trim(),
                    entryReason = entryReason.trim(),
                    exitReason = exitReason.trim(),
                    mistakes = mistakes.trim(),
                    lessonsLearned = lessonsLearned.trim(),
                    screenshotPath = screenshotPath,
                    notes = notes.trim(),
                    updatedAt = now
                )
                tradeDao.updateTrade(updated)
                updated
            } else {
                val nextSeq = tradeDao.getMaxTradeId() + 1L
                val code = "TWBB-${String.format(Locale.US, "%04d", nextSeq)}"
                val newTrade = TradeEntity(
                    tradeCode = code,
                    userId = targetUserId,
                    userName = targetUserName.trim(),
                    tradeDateMillis = tradeDateMillis,
                    tradeDateIso = tradeDateIso,
                    instrument = instrument.trim().uppercase(Locale.US),
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
                    strategy = strategy.trim(),
                    entryReason = entryReason.trim(),
                    exitReason = exitReason.trim(),
                    mistakes = mistakes.trim(),
                    lessonsLearned = lessonsLearned.trim(),
                    screenshotPath = screenshotPath,
                    notes = notes.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                val id = tradeDao.insertTrade(newTrade)
                newTrade.copy(id = id)
            }
        }
    }

    suspend fun deleteTrade(actor: UserEntity, tradeId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val trade = tradeDao.getTradeById(tradeId) ?: throw IllegalArgumentException("Trade not found.")
            if (actor.role != UserRole.ADMIN && trade.userId != actor.id) {
                throw SecurityException("Access denied: Cannot delete another student's trade.")
            }
            tradeDao.deleteTradeById(tradeId)
        }
    }

    suspend fun saveDisciplineReview(
        actor: UserEntity,
        targetUserId: Long,
        periodType: ReviewPeriod,
        periodKey: String,
        emotionState: EmotionState,
        psychologyNotes: String,
        slAdherenceRating: Int,
        lessonsSummary: String,
        nextActionPlan: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (actor.role != UserRole.ADMIN && actor.id != targetUserId) {
                throw SecurityException("Access denied.")
            }
            require(periodKey.isNotBlank()) { "Period key is required." }
            reviewDao.upsertReview(
                DisciplineReviewEntity(
                    userId = targetUserId,
                    periodType = periodType,
                    periodKey = periodKey.trim(),
                    emotionState = emotionState,
                    psychologyNotes = psychologyNotes.trim(),
                    slAdherenceRating = slAdherenceRating.coerceIn(1, 5),
                    lessonsSummary = lessonsSummary.trim(),
                    nextActionPlan = nextActionPlan.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            Unit
        }
    }

    suspend fun copyScreenshotToAppStorage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
            val file = File(dir, "chart_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to read selected image.")
            file.absolutePath
        }
    }

    suspend fun exportBackupJson(actor: UserEntity): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
            root.put("brand", "Trading with Bangali Babu")
            root.put("exportedAt", System.currentTimeMillis())

            val users = if (actor.role == UserRole.ADMIN) {
                userDao.getAllUsersOnce()
            } else {
                listOfNotNull(userDao.getUserById(actor.id))
            }
            val trades = if (actor.role == UserRole.ADMIN) {
                tradeDao.getAllTradesOnce()
            } else {
                tradeDao.getTradesByUserOnce(actor.id)
            }
            val reviews = if (actor.role == UserRole.ADMIN) {
                reviewDao.getAllReviewsOnce()
            } else {
                reviewDao.getAllReviewsOnce().filter { it.userId == actor.id }
            }

            val usersArr = JSONArray()
            users.forEach { u ->
                usersArr.put(JSONObject().apply {
                    put("id", u.id)
                    put("name", u.name)
                    put("email", u.email)
                    put("passwordHash", u.passwordHash)
                    put("passwordSalt", u.passwordSalt)
                    put("role", u.role.name)
                    put("status", u.status.name)
                    put("dailyProfitGoal", u.dailyProfitGoal)
                    put("dailyMaxLossLimit", u.dailyMaxLossLimit)
                    put("maxTradesPerDay", u.maxTradesPerDay)
                    put("maxRiskPerTrade", u.maxRiskPerTrade)
                    put("createdAt", u.createdAt)
                    put("updatedAt", u.updatedAt)
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
                    put("direction", t.direction.name)
                    put("timeframe", t.timeframe.name)
                    put("entryPrice", t.entryPrice)
                    put("exitPrice", t.exitPrice)
                    put("stopLoss", t.stopLoss ?: JSONObject.NULL)
                    put("takeProfit", t.takeProfit ?: JSONObject.NULL)
                    put("lotSize", t.lotSize)
                    put("riskAmount", t.riskAmount)
                    put("actualPnl", t.actualPnl)
                    put("result", t.result.name)
                    put("strategy", t.strategy)
                    put("entryReason", t.entryReason)
                    put("exitReason", t.exitReason)
                    put("mistakes", t.mistakes)
                    put("lessonsLearned", t.lessonsLearned)
                    put("screenshotPath", t.screenshotPath ?: JSONObject.NULL)
                    put("notes", t.notes)
                    put("createdAt", t.createdAt)
                    put("updatedAt", t.updatedAt)
                })
            }
            root.put("trades", tradesArr)

            val reviewsArr = JSONArray()
            reviews.forEach { r ->
                reviewsArr.put(JSONObject().apply {
                    put("id", r.id)
                    put("userId", r.userId)
                    put("periodType", r.periodType.name)
                    put("periodKey", r.periodKey)
                    put("emotionState", r.emotionState.name)
                    put("psychologyNotes", r.psychologyNotes)
                    put("slAdherenceRating", r.slAdherenceRating)
                    put("lessonsSummary", r.lessonsSummary)
                    put("nextActionPlan", r.nextActionPlan)
                    put("updatedAt", r.updatedAt)
                })
            }
            root.put("reviews", reviewsArr)
            root.toString(2)
        }
    }

    suspend fun restoreBackupJson(actor: UserEntity, jsonContent: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (actor.role != UserRole.ADMIN) throw SecurityException("Only Admin can restore a full database backup.")
            val root = JSONObject(jsonContent)
            val usersArr = root.getJSONArray("users")
            val tradesArr = root.getJSONArray("trades")
            val reviewsArr = root.optJSONArray("reviews") ?: JSONArray()

            val users = (0 until usersArr.length()).map { i ->
                val o = usersArr.getJSONObject(i)
                UserEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    email = o.getString("email"),
                    passwordHash = o.getString("passwordHash"),
                    passwordSalt = o.getString("passwordSalt"),
                    role = UserRole.entries.firstOrNull { it.name == o.getString("role") } ?: UserRole.STUDENT,
                    status = AccountStatus.entries.firstOrNull { it.name == o.getString("status") } ?: AccountStatus.ACTIVE,
                    dailyProfitGoal = o.optDouble("dailyProfitGoal", 250.0),
                    dailyMaxLossLimit = o.optDouble("dailyMaxLossLimit", 100.0),
                    maxTradesPerDay = o.optInt("maxTradesPerDay", 5),
                    maxRiskPerTrade = o.optDouble("maxRiskPerTrade", 50.0),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }
            require(users.any { it.role == UserRole.ADMIN }) { "Backup file does not contain an Admin account." }

            val trades = (0 until tradesArr.length()).map { i ->
                val o = tradesArr.getJSONObject(i)
                TradeEntity(
                    id = o.getLong("id"),
                    tradeCode = o.getString("tradeCode"),
                    userId = o.getLong("userId"),
                    userName = o.getString("userName"),
                    tradeDateMillis = o.getLong("tradeDateMillis"),
                    tradeDateIso = o.getString("tradeDateIso"),
                    instrument = o.getString("instrument"),
                    direction = TradeDirection.entries.firstOrNull { it.name == o.getString("direction") } ?: TradeDirection.BUY,
                    timeframe = Timeframe.entries.firstOrNull { it.name == o.getString("timeframe") } ?: Timeframe.M15,
                    entryPrice = o.getDouble("entryPrice"),
                    exitPrice = o.getDouble("exitPrice"),
                    stopLoss = if (o.isNull("stopLoss")) null else o.getDouble("stopLoss"),
                    takeProfit = if (o.isNull("takeProfit")) null else o.getDouble("takeProfit"),
                    lotSize = o.getDouble("lotSize"),
                    riskAmount = o.getDouble("riskAmount"),
                    actualPnl = o.getDouble("actualPnl"),
                    result = TradeResult.entries.firstOrNull { it.name == o.getString("result") } ?: TradeResult.BREAK_EVEN,
                    strategy = o.optString("strategy"),
                    entryReason = o.optString("entryReason"),
                    exitReason = o.optString("exitReason"),
                    mistakes = o.optString("mistakes"),
                    lessonsLearned = o.optString("lessonsLearned"),
                    screenshotPath = if (o.isNull("screenshotPath")) null else o.optString("screenshotPath"),
                    notes = o.optString("notes"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }

            val reviews = (0 until reviewsArr.length()).map { i ->
                val o = reviewsArr.getJSONObject(i)
                DisciplineReviewEntity(
                    id = o.getLong("id"),
                    userId = o.getLong("userId"),
                    periodType = ReviewPeriod.entries.firstOrNull { it.name == o.getString("periodType") } ?: ReviewPeriod.WEEKLY,
                    periodKey = o.getString("periodKey"),
                    emotionState = EmotionState.entries.firstOrNull { it.name == o.optString("emotionState") } ?: EmotionState.CALM,
                    psychologyNotes = o.optString("psychologyNotes"),
                    slAdherenceRating = o.optInt("slAdherenceRating", 3),
                    lessonsSummary = o.optString("lessonsSummary"),
                    nextActionPlan = o.optString("nextActionPlan"),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }

            userDao.upsertUsers(users)
            tradeDao.insertTrades(trades)
            reviewDao.upsertReviews(reviews)
            trades.size
        }
    }
}
