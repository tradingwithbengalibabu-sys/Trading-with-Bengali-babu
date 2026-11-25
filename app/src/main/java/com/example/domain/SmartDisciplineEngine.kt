package com.example.domain

import com.example.data.model.ExitClosureType
import com.example.data.model.SmartAlertType
import com.example.data.model.SmartDisciplineAlertEntity
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradingCurrency
import com.example.data.model.UserEntity
import com.example.data.model.WeeklyReportEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeekWindow(
    val weekKey: String,
    val startIso: String,
    val endIso: String,
    val startMillis: Long,
    val endMillis: Long
) {
    fun isCompleted(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis > endMillis
}

data class AlertCountUpdate(
    val alertId: String,
    val newStopLossCount: Int,
    val newLookbackCount: Int,
    val newLossAmountUsd: Double,
    val newLossAmountInr: Double,
    val newExchangeRateUsed: Double,
    val newTriggerDateIso: String,
    val newResolved: Boolean,
    val newTitle: String,
    val newMessage: String,
    val newTriggeredAtMillis: Long
)

data class SmartAlertEvaluationResult(
    val newAlertsToCreate: List<SmartDisciplineAlertEntity> = emptyList(),
    val existingAlertsToUpdate: List<AlertCountUpdate> = emptyList()
)

object SmartDisciplineEngine {

    const val WEEKLY_LOSS_LIMIT_USD = 200.0
    const val CONSECUTIVE_SL_THRESHOLD = 10
    const val CONSECUTIVE_STRATEGY_SL_THRESHOLD = 3
    const val IST_TIMEZONE_ID = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID

    private val slReasonRegex = Regex("\\b(sl|stop\\s*-?\\s*loss|stoploss|stopped)\\b", RegexOption.IGNORE_CASE)

    private fun istTimeZone(): TimeZone = TimeZone.getTimeZone(IST_TIMEZONE_ID)

    fun formatIstIsoDate(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = istTimeZone()
        }
        return fmt.format(Date(millis))
    }

    fun isStopLossHit(trade: TradeEntity): Boolean {
        if (!trade.isClosedTrade) return false
        if (trade.resultEnum != TradeResult.LOSS) return false
        if (trade.mistakes.contains("No Stop Loss", ignoreCase = true)) return false

        val closure = trade.resolvedExitClosureType
        if (closure == ExitClosureType.AUTO_SL || closure == ExitClosureType.TRAILING_SL) {
            return true
        }

        val reason = trade.exitReason.trim()
        if (reason.isNotEmpty() && slReasonRegex.containsMatchIn(reason)) {
            return true
        }

        val sl = trade.stopLoss
        if (sl != null && sl > 0.0) {
            val hitPrice = when (trade.directionEnum) {
                TradeDirection.BUY -> trade.exitPrice <= sl + 1e-6
                TradeDirection.SELL -> trade.exitPrice >= sl - 1e-6
            }
            if (hitPrice) return true
            if (reason.isBlank()) return true
        }

        return false
    }

    fun realizedTimestampMillis(trade: TradeEntity): Long {
        return trade.exitTimeMillis?.takeIf { it > 0L }
            ?: trade.entryTimeMillis?.takeIf { it > 0L }
            ?: trade.tradeDateMillis
    }

    fun sortTradesChronologically(trades: List<TradeEntity>): List<TradeEntity> {
        return trades.sortedWith(
            compareBy<TradeEntity> { realizedTimestampMillis(it) }
                .thenBy { it.tradeDateMillis }
                .thenBy { it.createdAt?.seconds ?: 0L }
                .thenBy { it.id }
        )
    }

    fun sortClosedTradesChronologically(trades: List<TradeEntity>): List<TradeEntity> {
        return sortTradesChronologically(trades.filter { it.isClosedTrade })
    }

    /**
     * Evaluates the THREE independent automatic risk warning systems for an individual trader:
     * 1. Weekly Loss Warning: Net realized loss (including fees) in current Mon 00:00 – Sun 23:59 IST > $200 USD eq. (closed trades only)
     * 2. Consecutive 10 Stop Loss Warning: 10 strictly consecutive closed SL trades (resets on non-SL closed trade)
     * 3. Same Strategy – 3 Consecutive Stop Loss Warning: 3 strictly consecutive closed SL trades per strategy (resets on non-SL closed trade for that strategy)
     */
    fun evaluateSmartAlerts(
        user: UserEntity,
        trades: List<TradeEntity>,
        existingAlerts: List<SmartDisciplineAlertEntity>,
        liveUsdToInrRate: Double = TradingAnalytics.DEFAULT_USD_TO_INR_RATE,
        nowMillis: Long = System.currentTimeMillis()
    ): SmartAlertEvaluationResult {
        val targetUid = user.id.ifBlank { user.userId }
        if (targetUid.isBlank()) return SmartAlertEvaluationResult()

        val traderName = user.name.ifBlank { "Trader" }
        val safeFxRate = if (liveUsdToInrRate > 0.0) liveUsdToInrRate else TradingAnalytics.DEFAULT_USD_TO_INR_RATE
        val closedChronological = sortClosedTradesChronologically(trades)
        val userAlerts = existingAlerts.filter { it.userId == targetUid }
        val newAlerts = mutableListOf<SmartDisciplineAlertEntity>()
        val updates = mutableListOf<AlertCountUpdate>()
        val triggerDateTodayIso = formatIstIsoDate(nowMillis)

        // =========================================================================
        // SYSTEM 1: Weekly Loss Warning – $200 Equivalent (Asia/Kolkata Mon–Sun)
        // =========================================================================
        val currentWeek = weekWindowForMillis(nowMillis)
        val weekClosedTrades = closedChronological.filter {
            realizedTimestampMillis(it) in currentWeek.startMillis..currentWeek.endMillis
        }
        val weeklyLossUsd = computeNetLossUsd(weekClosedTrades, safeFxRate)
        val weeklyLossInr = roundTwoDecimals(weeklyLossUsd * safeFxRate)
        val currentWeekKey = "WKLOSS_${currentWeek.weekKey}"
        val existingCurrentWeekAlert = userAlerts.firstOrNull {
            it.alertTypeEnum == SmartAlertType.WEEKLY_LOSS_LIMIT && it.triggerKey == currentWeekKey
        }

        if (weeklyLossUsd > WEEKLY_LOSS_LIMIT_USD) {
            val title = weeklyLossTitle()
            val message = weeklyLossMessage()
            val lossUsdRounded = roundTwoDecimals(weeklyLossUsd)
            if (existingCurrentWeekAlert == null) {
                newAlerts.add(
                    SmartDisciplineAlertEntity(
                        id = sanitizeDocId("wk_${targetUid}_${currentWeek.weekKey}"),
                        userId = targetUid,
                        userName = traderName,
                        alertType = SmartAlertType.WEEKLY_LOSS_LIMIT.name,
                        triggerKey = currentWeekKey,
                        strategyName = "",
                        stopLossCount = 0,
                        lookbackCount = weekClosedTrades.size,
                        recommendedBreakDays = 0,
                        lossAmountUsd = lossUsdRounded,
                        lossAmountInr = weeklyLossInr,
                        exchangeRateUsed = safeFxRate,
                        triggerDateIso = triggerDateTodayIso,
                        resolved = false,
                        title = title,
                        message = message,
                        acknowledged = false,
                        acknowledgedAtMillis = 0L,
                        triggeredAtMillis = nowMillis
                    )
                )
            } else if (!existingCurrentWeekAlert.acknowledged) {
                val needsReactivate = existingCurrentWeekAlert.resolved
                val amountChanged = abs(existingCurrentWeekAlert.lossAmountUsd - lossUsdRounded) >= 0.01 ||
                    abs(existingCurrentWeekAlert.lossAmountInr - weeklyLossInr) >= 0.5 ||
                    existingCurrentWeekAlert.lookbackCount != weekClosedTrades.size ||
                    existingCurrentWeekAlert.title != title ||
                    existingCurrentWeekAlert.message != message
                if ((needsReactivate || amountChanged) && existingCurrentWeekAlert.id.isNotBlank()) {
                    updates.add(
                        AlertCountUpdate(
                            alertId = existingCurrentWeekAlert.id,
                            newStopLossCount = 0,
                            newLookbackCount = weekClosedTrades.size,
                            newLossAmountUsd = lossUsdRounded,
                            newLossAmountInr = weeklyLossInr,
                            newExchangeRateUsed = safeFxRate,
                            newTriggerDateIso = if (needsReactivate) triggerDateTodayIso else existingCurrentWeekAlert.triggerDateIso.ifBlank { triggerDateTodayIso },
                            newResolved = false,
                            newTitle = title,
                            newMessage = message,
                            newTriggeredAtMillis = if (needsReactivate) nowMillis else existingCurrentWeekAlert.effectiveTimestampMillis.coerceAtLeast(1L)
                        )
                    )
                }
            }
        }

        // Recalculate all unacknowledged weekly loss alerts on edit/delete
        userAlerts.filter { it.alertTypeEnum == SmartAlertType.WEEKLY_LOSS_LIMIT && !it.acknowledged }
            .forEach { alert ->
                val rawWeekKey = alert.triggerKey.removePrefix("WKLOSS_")
                val weekTradesForAlert = if (rawWeekKey == currentWeek.weekKey) {
                    weekClosedTrades
                } else {
                    closedChronological.filter { weekWindowForMillis(realizedTimestampMillis(it)).weekKey == rawWeekKey }
                }
                val recalculatedLossUsd = computeNetLossUsd(weekTradesForAlert, safeFxRate)
                val recalculatedLossInr = roundTwoDecimals(recalculatedLossUsd * safeFxRate)
                if (recalculatedLossUsd <= WEEKLY_LOSS_LIMIT_USD && !alert.resolved && alert.id.isNotBlank()) {
                    updates.add(
                        AlertCountUpdate(
                            alertId = alert.id,
                            newStopLossCount = 0,
                            newLookbackCount = weekTradesForAlert.size,
                            newLossAmountUsd = roundTwoDecimals(recalculatedLossUsd),
                            newLossAmountInr = recalculatedLossInr,
                            newExchangeRateUsed = safeFxRate,
                            newTriggerDateIso = alert.triggerDateIso.ifBlank { triggerDateTodayIso },
                            newResolved = true,
                            newTitle = alert.title.ifBlank { weeklyLossTitle() },
                            newMessage = alert.message.ifBlank { weeklyLossMessage() },
                            newTriggeredAtMillis = alert.effectiveTimestampMillis.coerceAtLeast(1L)
                        )
                    )
                }
            }

        // =========================================================================
        // SYSTEM 2: Consecutive 10 Stop Loss Warning (All Closed Trades)
        // =========================================================================
        val overallSlRuns = findConsecutiveSlRuns(closedChronological, CONSECUTIVE_SL_THRESHOLD)
        val validStreakKeys = mutableSetOf<String>()

        overallSlRuns.forEach { run ->
            val firstTrade = run.first()
            val lastTrade = run.last()
            val triggerKey = "SL10_${firstTrade.id}"
            validStreakKeys.add(triggerKey)
            val runLossUsd = computeNetLossUsd(run, safeFxRate)
            val runLossInr = roundTwoDecimals(runLossUsd * safeFxRate)
            val triggerDate = formatIstIsoDate(realizedTimestampMillis(lastTrade))
            val title = consecutiveSlTitle()
            val message = consecutiveSlMessage()
            val existing = userAlerts.firstOrNull {
                it.alertTypeEnum == SmartAlertType.STOP_LOSS_STREAK && it.triggerKey == triggerKey
            }

            if (existing == null) {
                newAlerts.add(
                    SmartDisciplineAlertEntity(
                        id = sanitizeDocId("sl10_${targetUid}_${firstTrade.id}"),
                        userId = targetUid,
                        userName = traderName,
                        alertType = SmartAlertType.STOP_LOSS_STREAK.name,
                        triggerKey = triggerKey,
                        strategyName = "",
                        stopLossCount = run.size,
                        lookbackCount = CONSECUTIVE_SL_THRESHOLD,
                        recommendedBreakDays = 0,
                        lossAmountUsd = roundTwoDecimals(runLossUsd),
                        lossAmountInr = runLossInr,
                        exchangeRateUsed = safeFxRate,
                        triggerDateIso = triggerDate,
                        resolved = false,
                        title = title,
                        message = message,
                        acknowledged = false,
                        acknowledgedAtMillis = 0L,
                        triggeredAtMillis = nowMillis
                    )
                )
            } else if (!existing.acknowledged) {
                val needsReactivate = existing.resolved
                val countOrCopyChanged = existing.stopLossCount != run.size ||
                    existing.title != title ||
                    existing.message != message
                if ((needsReactivate || countOrCopyChanged) && existing.id.isNotBlank()) {
                    updates.add(
                        AlertCountUpdate(
                            alertId = existing.id,
                            newStopLossCount = run.size,
                            newLookbackCount = CONSECUTIVE_SL_THRESHOLD,
                            newLossAmountUsd = roundTwoDecimals(runLossUsd),
                            newLossAmountInr = runLossInr,
                            newExchangeRateUsed = safeFxRate,
                            newTriggerDateIso = triggerDate,
                            newResolved = false,
                            newTitle = title,
                            newMessage = message,
                            newTriggeredAtMillis = if (needsReactivate) nowMillis else existing.effectiveTimestampMillis.coerceAtLeast(1L)
                        )
                    )
                }
            }
        }

        // Auto-resolve unacknowledged consecutive-SL alerts invalidated by trade edit or deletion
        userAlerts.filter {
            it.alertTypeEnum == SmartAlertType.STOP_LOSS_STREAK &&
                !it.acknowledged &&
                !it.resolved &&
                it.triggerKey !in validStreakKeys &&
                it.id.isNotBlank()
        }.forEach { stale ->
            updates.add(
                AlertCountUpdate(
                    alertId = stale.id,
                    newStopLossCount = stale.stopLossCount,
                    newLookbackCount = stale.lookbackCount,
                    newLossAmountUsd = stale.lossAmountUsd,
                    newLossAmountInr = stale.lossAmountInr,
                    newExchangeRateUsed = stale.exchangeRateUsed.takeIf { it > 0.0 } ?: safeFxRate,
                    newTriggerDateIso = stale.triggerDateIso,
                    newResolved = true,
                    newTitle = stale.title.ifBlank { consecutiveSlTitle() },
                    newMessage = stale.message.ifBlank { consecutiveSlMessage() },
                    newTriggeredAtMillis = stale.effectiveTimestampMillis.coerceAtLeast(1L)
                )
            )
        }

        // =========================================================================
        // SYSTEM 3: Same Strategy – 3 Consecutive Stop Loss Warning
        // =========================================================================
        val validStrategyKeys = mutableSetOf<String>()
        val groupedByStrategy = closedChronological.groupBy {
            it.strategy.trim().ifEmpty { "General" }.lowercase(Locale.US)
        }

        groupedByStrategy.forEach { (normalizedKey, strategyTrades) ->
            val displayStrategy = strategyTrades.lastOrNull()?.strategy?.trim()?.ifEmpty { "General" } ?: "General"
            val slug = normalizedKey.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "general" }.take(32)
            val stratRuns = findConsecutiveSlRuns(strategyTrades, CONSECUTIVE_STRATEGY_SL_THRESHOLD)

            stratRuns.forEach { run ->
                val firstTrade = run.first()
                val lastTrade = run.last()
                val triggerKey = "STRAT3_${slug}_${firstTrade.id}"
                validStrategyKeys.add(triggerKey)
                val runLossUsd = computeNetLossUsd(run, safeFxRate)
                val runLossInr = roundTwoDecimals(runLossUsd * safeFxRate)
                val triggerDate = formatIstIsoDate(realizedTimestampMillis(lastTrade))
                val title = strategySlTitle()
                val message = strategySlMessage()
                val existing = userAlerts.firstOrNull {
                    it.alertTypeEnum == SmartAlertType.STRATEGY_LOSS_LIMIT && it.triggerKey == triggerKey
                }

                if (existing == null) {
                    newAlerts.add(
                        SmartDisciplineAlertEntity(
                            id = sanitizeDocId("strat3_${targetUid}_${slug}_${firstTrade.id}"),
                            userId = targetUid,
                            userName = traderName,
                            alertType = SmartAlertType.STRATEGY_LOSS_LIMIT.name,
                            triggerKey = triggerKey,
                            strategyName = displayStrategy,
                            stopLossCount = run.size,
                            lookbackCount = CONSECUTIVE_STRATEGY_SL_THRESHOLD,
                            recommendedBreakDays = 0,
                            lossAmountUsd = roundTwoDecimals(runLossUsd),
                            lossAmountInr = runLossInr,
                            exchangeRateUsed = safeFxRate,
                            triggerDateIso = triggerDate,
                            resolved = false,
                            title = title,
                            message = message,
                            acknowledged = false,
                            acknowledgedAtMillis = 0L,
                            triggeredAtMillis = nowMillis
                        )
                    )
                } else if (!existing.acknowledged) {
                    val needsReactivate = existing.resolved
                    val countOrCopyChanged = existing.stopLossCount != run.size ||
                        existing.strategyName != displayStrategy ||
                        existing.title != title ||
                        existing.message != message
                    if ((needsReactivate || countOrCopyChanged) && existing.id.isNotBlank()) {
                        updates.add(
                            AlertCountUpdate(
                                alertId = existing.id,
                                newStopLossCount = run.size,
                                newLookbackCount = CONSECUTIVE_STRATEGY_SL_THRESHOLD,
                                newLossAmountUsd = roundTwoDecimals(runLossUsd),
                                newLossAmountInr = runLossInr,
                                newExchangeRateUsed = safeFxRate,
                                newTriggerDateIso = triggerDate,
                                newResolved = false,
                                newTitle = title,
                                newMessage = message,
                                newTriggeredAtMillis = if (needsReactivate) nowMillis else existing.effectiveTimestampMillis.coerceAtLeast(1L)
                            )
                        )
                    }
                }
            }
        }

        // Auto-resolve unacknowledged strategy SL alerts invalidated by trade edit or deletion
        userAlerts.filter {
            it.alertTypeEnum == SmartAlertType.STRATEGY_LOSS_LIMIT &&
                !it.acknowledged &&
                !it.resolved &&
                it.triggerKey !in validStrategyKeys &&
                it.id.isNotBlank()
        }.forEach { stale ->
            updates.add(
                AlertCountUpdate(
                    alertId = stale.id,
                    newStopLossCount = stale.stopLossCount,
                    newLookbackCount = stale.lookbackCount,
                    newLossAmountUsd = stale.lossAmountUsd,
                    newLossAmountInr = stale.lossAmountInr,
                    newExchangeRateUsed = stale.exchangeRateUsed.takeIf { it > 0.0 } ?: safeFxRate,
                    newTriggerDateIso = stale.triggerDateIso,
                    newResolved = true,
                    newTitle = stale.title.ifBlank { strategySlTitle() },
                    newMessage = stale.message.ifBlank { strategySlMessage() },
                    newTriggeredAtMillis = stale.effectiveTimestampMillis.coerceAtLeast(1L)
                )
            )
        }

        return SmartAlertEvaluationResult(
            newAlertsToCreate = newAlerts,
            existingAlertsToUpdate = updates.distinctBy { it.alertId }
        )
    }

    /**
     * Current live consecutive Stop Loss streak count (from the most recent closed trade backwards).
     */
    fun currentConsecutiveSlCount(trades: List<TradeEntity>): Int {
        val closed = sortClosedTradesChronologically(trades)
        var count = 0
        for (i in closed.indices.reversed()) {
            if (isStopLossHit(closed[i])) count++ else break
        }
        return count
    }

    /**
     * Current live consecutive Stop Loss streak count per strategy.
     */
    fun currentConsecutiveSlByStrategy(trades: List<TradeEntity>): Map<String, Int> {
        val closed = sortClosedTradesChronologically(trades)
        return closed.groupBy { it.strategy.trim().ifEmpty { "General" } }
            .mapValues { (_, stratTrades) ->
                var count = 0
                for (i in stratTrades.indices.reversed()) {
                    if (isStopLossHit(stratTrades[i])) count++ else break
                }
                count
            }
    }

    /**
     * Computes weekly net realized loss in USD (>0 means net loss, 0 means net profit/breakeven)
     * across CLOSED trades in the current IST Monday 00:00 – Sunday 23:59 week, including fees/commissions.
     */
    fun currentWeekNetLossUsd(
        trades: List<TradeEntity>,
        liveUsdToInrRate: Double = TradingAnalytics.DEFAULT_USD_TO_INR_RATE,
        nowMillis: Long = System.currentTimeMillis()
    ): Double {
        val week = weekWindowForMillis(nowMillis)
        val closedWeekTrades = sortClosedTradesChronologically(trades).filter {
            realizedTimestampMillis(it) in week.startMillis..week.endMillis
        }
        val safeFx = if (liveUsdToInrRate > 0.0) liveUsdToInrRate else TradingAnalytics.DEFAULT_USD_TO_INR_RATE
        return computeNetLossUsd(closedWeekTrades, safeFx)
    }

    private fun computeNetLossUsd(closedTrades: List<TradeEntity>, fallbackUsdToInrRate: Double): Double {
        if (closedTrades.isEmpty()) return 0.0
        val netUsd = closedTrades.sumOf { trade ->
            val netOriginal = trade.netRealizedPnl
            val rate = if (trade.usdToInrRate > 0.0) trade.usdToInrRate else fallbackUsdToInrRate
            when (trade.currencyEnum) {
                TradingCurrency.USD -> netOriginal
                TradingCurrency.INR -> netOriginal / rate
            }
        }
        return if (netUsd < 0.0) -netUsd else 0.0
    }

    private fun findConsecutiveSlRuns(
        chronologicalClosedTrades: List<TradeEntity>,
        minLength: Int
    ): List<List<TradeEntity>> {
        val runs = mutableListOf<List<TradeEntity>>()
        var currentRun = mutableListOf<TradeEntity>()
        for (trade in chronologicalClosedTrades) {
            if (isStopLossHit(trade)) {
                currentRun.add(trade)
            } else {
                if (currentRun.size >= minLength) {
                    runs.add(currentRun.toList())
                }
                currentRun.clear()
            }
        }
        if (currentRun.size >= minLength) {
            runs.add(currentRun.toList())
        }
        return runs
    }

    fun weeklyLossTitle(): String = "🚨 WEEKLY LOSS LIMIT EXCEEDED! 🚨"

    fun weeklyLossMessage(): String = """
🚨 WEEKLY LOSS LIMIT EXCEEDED! 🚨

Your trading losses this week have exceeded your $200 equivalent risk limit.

Please stop trading for now. Do not try to recover your losses through emotional or revenge trading.

Take a break, review your trading performance and protect your remaining capital.

Need Mental Support or Trading Guidance?

For mental support or trading-related assistance, please contact Admin Nirab Sarkar.

Your Well-being Comes First. Take a Break. Come Back Stronger.
    """.trimIndent()

    fun consecutiveSlTitle(): String = "⚠️ TRADING DISCIPLINE ALERT!"

    fun consecutiveSlMessage(): String = """
⚠️ TRADING DISCIPLINE ALERT!

You have experienced 10 consecutive Stop Loss trades.

Please take a break from trading. Avoid emotional decisions and review your Trading Psychology, Risk Management and previous trades.

Your mental well-being and capital protection are more important than recovering losses.

For mental support or trading guidance, please contact Admin Nirab Sarkar.

Take a Break. Review. Come Back Stronger.
    """.trimIndent()

    fun strategySlTitle(): String = "🚨 STRATEGY WARNING – REVIEW YOUR SETUP!"

    fun strategySlMessage(): String = """
🚨 STRATEGY WARNING – REVIEW YOUR SETUP!

Your selected strategy has resulted in 3 consecutive Stop Loss trades.

Please stop using this strategy temporarily and review your entry conditions, confirmation signals and current market conditions.

Do not repeat the same setup without understanding what went wrong.

For Strategy Analysis or Trading Psychology guidance, please contact Admin Nirab Sarkar.

Review Your Strategy. Improve Your Discipline. Trade Smarter.
    """.trimIndent()

    fun weekWindowForMillis(millis: Long): WeekWindow {
        val ist = istTimeZone()
        val cal = Calendar.getInstance(ist, Locale.US).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = millis
            val dow = get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
            add(Calendar.DAY_OF_YEAR, -daysFromMonday)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis
        val startIso = formatIstIsoDate(startMillis)
        val weekNum = cal.get(Calendar.WEEK_OF_YEAR)
        val weekYear = runCatching { cal.weekYear }.getOrElse { cal.get(Calendar.YEAR) }
        val weekKey = "${weekYear}-W${String.format(Locale.US, "%02d", weekNum)}"

        val endCal = (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 6)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endMillis = endCal.timeInMillis
        val endIso = formatIstIsoDate(endMillis)
        return WeekWindow(
            weekKey = weekKey,
            startIso = startIso,
            endIso = endIso,
            startMillis = startMillis,
            endMillis = endMillis
        )
    }

    fun buildMissingCompletedWeeklyReports(
        user: UserEntity,
        allUserTrades: List<TradeEntity>,
        existingReports: List<WeeklyReportEntity>,
        displayCurrency: TradingCurrency,
        nowMillis: Long = System.currentTimeMillis()
    ): List<WeeklyReportEntity> {
        val targetUid = user.id.ifBlank { user.userId }
        if (targetUid.isBlank() || allUserTrades.isEmpty()) return emptyList()

        val existingKeys = existingReports.filter { it.userId == targetUid }.map { it.weekKey }.toSet()
        val byWeek = allUserTrades.groupBy { weekWindowForMillis(realizedTimestampMillis(it)) }

        return byWeek.entries
            .filter { (window, weekTrades) ->
                weekTrades.isNotEmpty() &&
                    window.isCompleted(nowMillis) &&
                    window.weekKey !in existingKeys
            }
            .sortedBy { it.key.startMillis }
            .map { (window, weekTrades) ->
                generateWeeklyReport(
                    user = user,
                    week = window,
                    weekTrades = weekTrades,
                    allUserTrades = allUserTrades,
                    displayCurrency = displayCurrency,
                    generatedAtMillis = nowMillis
                )
            }
    }

    fun generateWeeklyReport(
        user: UserEntity,
        week: WeekWindow,
        weekTrades: List<TradeEntity>,
        allUserTrades: List<TradeEntity>,
        displayCurrency: TradingCurrency,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): WeeklyReportEntity {
        val targetUid = user.id.ifBlank { user.userId }
        val chronologicalWeek = sortTradesChronologically(weekTrades)
        val total = chronologicalWeek.size
        val wins = chronologicalWeek.count { it.isClosedTrade && it.resultEnum == TradeResult.WIN }
        val losses = chronologicalWeek.count { it.isClosedTrade && it.resultEnum == TradeResult.LOSS }
        val breakEvens = chronologicalWeek.count { it.isClosedTrade && it.resultEnum == TradeResult.BREAK_EVEN }
        val slHits = chronologicalWeek.count { isStopLossHit(it) }
        val netPnl = chronologicalWeek.sumOf { TradingAnalytics.convertedPnl(it, displayCurrency) }
        val winRate = if (total > 0) (wins.toDouble() / total) * 100.0 else 0.0

        val mistakeCounts = mutableMapOf<String, Int>()
        chronologicalWeek.forEach { t ->
            t.mistakes.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
                .forEach { m ->
                    mistakeCounts[m] = (mistakeCounts[m] ?: 0) + 1
                }
        }
        val repeatedMistakes = mistakeCounts.entries
            .sortedByDescending { it.value }
            .map { (name, count) ->
                if (count >= 2) "$name ($count× — Repeated)" else "$name (1×)"
            }

        val byDay = chronologicalWeek.groupBy { it.tradeDateIso }
        val overtradingDays = byDay.count { (_, dayTrades) -> dayTrades.size > user.maxTradesPerDay }
        val dailyLossBreachDays = byDay.count { (_, dayTrades) ->
            val dayPnlPref = dayTrades.sumOf { TradingAnalytics.convertedPnl(it, user.preferredCurrencyEnum) }
            user.dailyMaxLossLimit > 0.0 && dayPnlPref <= -user.dailyMaxLossLimit
        }

        var revengeCount = 0
        byDay.values.forEach { dayList ->
            val sortedDay = sortTradesChronologically(dayList)
            sortedDay.forEachIndexed { idx, trade ->
                val explicitRevenge = trade.mistakes.contains("Revenge", ignoreCase = true)
                val rapidRiskIncreaseAfterLoss = idx > 0 &&
                    sortedDay[idx - 1].resultEnum == TradeResult.LOSS &&
                    sortedDay[idx - 1].riskAmount > 0.0 &&
                    trade.riskAmount > sortedDay[idx - 1].riskAmount * 1.15
                if (explicitRevenge || rapidRiskIncreaseAfterLoss) {
                    revengeCount++
                }
            }
        }

        var noStopLossCount = 0
        var riskExceededCount = 0
        val ruleViolationDetails = mutableListOf<String>()

        chronologicalWeek.forEach { trade ->
            val issues = mutableListOf<String>()
            val missingSl = trade.stopLoss == null || trade.stopLoss <= 0.0 ||
                trade.mistakes.contains("No Stop Loss", ignoreCase = true)
            if (missingSl) {
                noStopLossCount++
                issues.add("No Stop Loss")
            }
            if (trade.mistakes.contains("Moved SL", ignoreCase = true)) {
                issues.add("Moved SL")
            }
            val riskInPref = TradingAnalytics.convertedRisk(trade, user.preferredCurrencyEnum)
            if (user.maxRiskPerTrade > 0.0 && riskInPref > user.maxRiskPerTrade + 1e-6) {
                riskExceededCount++
                issues.add("Risk > Max Limit")
            }
            if (trade.strategy.isBlank()) {
                issues.add("No Strategy Tagged")
            }
            if (trade.entryReason.isBlank()) {
                issues.add("Missing Entry Reason")
            }
            if (issues.isNotEmpty()) {
                ruleViolationDetails.add("${trade.tradeCode} (${trade.instrument}): ${issues.joinToString(", ")}")
            }
        }

        val overallSlByStrategy = allUserTrades
            .groupBy { it.strategy.trim().ifEmpty { "General" }.lowercase(Locale.US) }
            .mapValues { (_, list) -> list.count { isStopLossHit(it) } }

        val poorStrategies = chronologicalWeek
            .groupBy { it.strategy.trim().ifEmpty { "General" }.lowercase(Locale.US) }
            .mapNotNull { (key, list) ->
                val label = list.lastOrNull()?.strategy?.trim()?.ifEmpty { "General" } ?: "General"
                val stratPnl = list.sumOf { TradingAnalytics.convertedPnl(it, displayCurrency) }
                val stratWins = list.count { it.isClosedTrade && it.resultEnum == TradeResult.WIN }
                val stratWr = (stratWins.toDouble() / list.size) * 100.0
                val weekSl = list.count { isStopLossHit(it) }
                val totalSl = overallSlByStrategy[key] ?: weekSl
                val stratConsecSl = findConsecutiveSlRuns(sortClosedTradesChronologically(list), CONSECUTIVE_STRATEGY_SL_THRESHOLD).isNotEmpty()
                val isPoor = stratPnl < 0.0 ||
                    stratConsecSl ||
                    weekSl >= CONSECUTIVE_STRATEGY_SL_THRESHOLD ||
                    (list.size >= 2 && stratWr < 40.0)
                if (!isPoor) null
                else "$label • ${TradingAnalytics.formatCurrency(stratPnl, displayCurrency)} (${weekSl}W SL / $totalSl total SL, ${String.format(Locale.US, "%.0f%%", stratWr)} WR)"
            }

        val improvementAreas = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        val highSlRatio = total >= 3 && (slHits.toDouble() / total) >= 0.5
        if (slHits >= 5 || highSlRatio) {
            improvementAreas.add("Excessive Stop Losses ($slHits SL hits out of $total trades)")
            suggestions.add("Wait for stronger confirmation on higher timeframes before entry and review your Trading Psychology with Admin Nirab Sarkar.")
        }
        val weekLossUsd = computeNetLossUsd(chronologicalWeek.filter { it.isClosedTrade }, TradingAnalytics.DEFAULT_USD_TO_INR_RATE)
        if (weekLossUsd > WEEKLY_LOSS_LIMIT_USD) {
            improvementAreas.add("Weekly Risk Limit Exceeded (-$${String.format(Locale.US, "%.2f", weekLossUsd)} vs $200 eq. limit)")
            suggestions.add("Respect the $200 equivalent weekly stop-loss boundary and step away immediately when reached.")
        }
        if (currentConsecutiveSlCount(allUserTrades) >= CONSECUTIVE_SL_THRESHOLD) {
            suggestions.add("10 Consecutive Stop Losses detected: Take a break, review Risk Management, and consult Admin Nirab Sarkar before resuming.")
        }
        if (noStopLossCount > 0 || riskExceededCount > 0 || dailyLossBreachDays > 0) {
            val parts = buildList {
                if (noStopLossCount > 0) add("$noStopLossCount trades without Stop Loss")
                if (riskExceededCount > 0) add("$riskExceededCount trades exceeded max risk")
                if (dailyLossBreachDays > 0) add("$dailyLossBreachDays daily loss limit breach(es)")
            }
            improvementAreas.add("Poor Risk Management: ${parts.joinToString(", ")}")
            suggestions.add("Define an exact Stop Loss price and verify risk is <= ${TradingAnalytics.formatPlainCurrency(user.maxRiskPerTrade, user.preferredCurrencyEnum)} BEFORE placing every order.")
        }
        if (overtradingDays > 0 || revengeCount > 0) {
            improvementAreas.add("Overtrading & Emotional Patterns ($overtradingDays overtrading day(s), $revengeCount revenge-risk trade(s))")
            suggestions.add("Cap daily sessions strictly at ${user.maxTradesPerDay} trades and enforce a 30-minute cooldown away from screens immediately after any Stop Loss.")
        }
        val repeatedOnly = mistakeCounts.filterValues { it >= 2 }
        if (repeatedOnly.isNotEmpty()) {
            val topRepeated = repeatedOnly.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} (${it.value}×)" }
            improvementAreas.add("Repeated Mistakes: $topRepeated")
            suggestions.add("Create a pre-trade checklist targeting your #1 repeated mistake (${repeatedOnly.maxByOrNull { it.value }?.key}) and refuse entries unless all checklist items pass.")
        }
        if (poorStrategies.isNotEmpty()) {
            improvementAreas.add("Underperforming Strategies (${poorStrategies.size} flagged)")
            suggestions.add("Pause live trading on flagged strategies and review entry conditions, confirmation signals, and current market conditions.")
        }
        if (ruleViolationDetails.isNotEmpty() && improvementAreas.none { it.contains("Risk Management") }) {
            improvementAreas.add("Rule Discipline Gaps (${ruleViolationDetails.size} trade(s) missed setup/rule documentation)")
            suggestions.add("Log strategy, entry reason, and Stop Loss on 100% of trades before market execution.")
        }

        if (improvementAreas.isEmpty()) {
            improvementAreas.add("Strong overall rule adherence and balanced risk execution this week.")
            suggestions.add("Keep following your winning routine, review your best setups, and maintain consistent risk per trade.")
        }

        val rawScore = 100 -
            (noStopLossCount * 8) -
            (riskExceededCount * 8) -
            (overtradingDays * 10) -
            (revengeCount * 12) -
            (dailyLossBreachDays * 10) -
            (repeatedOnly.values.sum() * 4) -
            (if (highSlRatio) 10 else 0)
        val disciplineScore = rawScore.coerceIn(10, 100)

        val summaryText = buildString {
            append("${week.weekKey} (${week.startIso} to ${week.endIso}): ")
            append("$total trades ($wins W / $losses L), $slHits Stop Loss hits, ")
            append("Net ${TradingAnalytics.formatCurrency(netPnl, displayCurrency)}, Discipline Score $disciplineScore/100.")
        }

        return WeeklyReportEntity(
            id = sanitizeDocId("${targetUid}_${week.weekKey}"),
            userId = targetUid,
            userName = user.name.ifBlank { targetUid },
            weekKey = week.weekKey,
            weekStartIso = week.startIso,
            weekEndIso = week.endIso,
            currencyCode = displayCurrency.name,
            totalTrades = total,
            winningTrades = wins,
            losingTrades = losses,
            breakEvenTrades = breakEvens,
            stopLossHits = slHits,
            netPnl = abs(netPnl).let { if (netPnl < 0) -it else it },
            winRatePercent = winRate,
            disciplineScore = disciplineScore,
            overtradingDays = overtradingDays,
            revengeTradingCount = revengeCount,
            noStopLossCount = noStopLossCount,
            ruleViolationsCount = ruleViolationDetails.size,
            repeatedMistakes = repeatedMistakes,
            poorStrategies = poorStrategies,
            ruleViolationDetails = ruleViolationDetails.take(15),
            improvementAreas = improvementAreas,
            personalizedSuggestions = suggestions,
            summaryText = summaryText,
            readByUser = false,
            generatedAtMillis = generatedAtMillis
        )
    }

    private fun roundTwoDecimals(value: Double): Double = (value * 100.0).roundToInt() / 100.0

    private fun sanitizeDocId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
    }
}
