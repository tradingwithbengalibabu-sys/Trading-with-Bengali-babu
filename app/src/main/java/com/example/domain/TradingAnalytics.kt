package com.example.domain

import com.example.data.model.Timeframe
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class DayPnlSummary(
    val dateIso: String,
    val netPnl: Double,
    val tradeCount: Int
)

data class MonthlyPnlSummary(
    val monthKey: String,
    val label: String,
    val grossProfit: Double,
    val grossLoss: Double,
    val netPnl: Double,
    val tradeCount: Int
)

data class EquityPoint(
    val index: Int,
    val tradeCode: String,
    val dateIso: String,
    val cumulativePnl: Double,
    val tradePnl: Double
)

data class CategoryPerformance(
    val category: String,
    val tradeCount: Int,
    val winCount: Int,
    val netPnl: Double,
    val winRate: Double
)

data class StreakInfo(
    val label: String,
    val count: Int,
    val type: TradeResult?
)

data class DashboardMetrics(
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val breakEvenTrades: Int = 0,
    val winRatePercent: Double = 0.0,
    val lossRatePercent: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalLoss: Double = 0.0,
    val netPnl: Double = 0.0,
    val averageWin: Double = 0.0,
    val averageLoss: Double = 0.0,
    val profitFactor: Double = 0.0,
    val averageRiskReward: Double = 0.0,
    val bestTradingDay: DayPnlSummary? = null,
    val worstTradingDay: DayPnlSummary? = null,
    val currentStreak: StreakInfo = StreakInfo("0", 0, null),
    val dailyPnlSeries: List<DayPnlSummary> = emptyList(),
    val monthlySeries: List<MonthlyPnlSummary> = emptyList(),
    val equityCurve: List<EquityPoint> = emptyList(),
    val instrumentPerformance: List<CategoryPerformance> = emptyList(),
    val timeframePerformance: List<CategoryPerformance> = emptyList()
)

data class DisciplineMetrics(
    val todayTradeCount: Int = 0,
    val todayNetPnl: Double = 0.0,
    val isDailyLossLimitBreached: Boolean = false,
    val isDailyGoalReached: Boolean = false,
    val isOvertradingToday: Boolean = false,
    val overtradingDaysCount: Int = 0,
    val dailyLossBreachDaysCount: Int = 0,
    val stopLossDisciplinePercent: Double = 0.0,
    val riskCompliancePercent: Double = 0.0,
    val averageRiskAmount: Double = 0.0,
    val mistakeCounts: List<Pair<String, Int>> = emptyList()
)

data class StudentPerformanceSummary(
    val student: UserEntity,
    val metrics: DashboardMetrics,
    val discipline: DisciplineMetrics
)

object TradingAnalytics {
    private val monthInputFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    private val monthLabelFmt = SimpleDateFormat("MMM yy", Locale.US)
    private val isoDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayIso(): String = isoDateFmt.format(Date())

    fun calculateDashboardMetrics(trades: List<TradeEntity>): DashboardMetrics {
        if (trades.isEmpty()) return DashboardMetrics()

        val totalTrades = trades.size
        val wins = trades.filter { it.resultEnum == TradeResult.WIN }
        val losses = trades.filter { it.resultEnum == TradeResult.LOSS }
        val breakEvens = trades.filter { it.resultEnum == TradeResult.BREAK_EVEN }

        val winRate = (wins.size.toDouble() / totalTrades) * 100.0
        val lossRate = (losses.size.toDouble() / totalTrades) * 100.0

        val totalProfit = trades.filter { it.actualPnl > 0.0 }.sumOf { it.actualPnl }
        val totalLoss = trades.filter { it.actualPnl < 0.0 }.sumOf { abs(it.actualPnl) }
        val netPnl = trades.sumOf { it.actualPnl }

        val avgWin = if (wins.isNotEmpty()) {
            wins.sumOf { it.actualPnl.coerceAtLeast(0.0) } / wins.size
        } else 0.0

        val avgLoss = if (losses.isNotEmpty()) {
            losses.sumOf { abs(it.actualPnl) } / losses.size
        } else 0.0

        val profitFactor = when {
            totalLoss > 0.0 -> totalProfit / totalLoss
            totalProfit > 0.0 -> 99.99
            else -> 0.0
        }

        val rrValues = trades.mapNotNull { t ->
            val sl = t.stopLoss
            val tp = t.takeProfit
            when {
                sl != null && tp != null && abs(t.entryPrice - sl) > 1e-9 ->
                    abs(tp - t.entryPrice) / abs(t.entryPrice - sl)
                t.riskAmount > 0.0 ->
                    abs(t.actualPnl) / t.riskAmount
                else -> null
            }
        }
        val avgRr = if (rrValues.isNotEmpty()) rrValues.average() else 0.0

        val dailySeries = trades
            .groupBy { it.tradeDateIso }
            .entries
            .sortedBy { it.key }
            .map { (dateIso, dayTrades) ->
                DayPnlSummary(
                    dateIso = dateIso,
                    netPnl = dayTrades.sumOf { it.actualPnl },
                    tradeCount = dayTrades.size
                )
            }

        val bestDay = dailySeries.maxByOrNull { it.netPnl }
        val worstDay = dailySeries.minByOrNull { it.netPnl }

        val monthlySeries = trades
            .groupBy { it.tradeDateIso.take(7) }
            .entries
            .sortedBy { it.key }
            .map { (monthKey, monthTrades) ->
                val label = runCatching {
                    val d = monthInputFmt.parse(monthKey)
                    if (d != null) monthLabelFmt.format(d) else monthKey
                }.getOrDefault(monthKey)
                MonthlyPnlSummary(
                    monthKey = monthKey,
                    label = label,
                    grossProfit = monthTrades.filter { it.actualPnl > 0 }.sumOf { it.actualPnl },
                    grossLoss = monthTrades.filter { it.actualPnl < 0 }.sumOf { abs(it.actualPnl) },
                    netPnl = monthTrades.sumOf { it.actualPnl },
                    tradeCount = monthTrades.size
                )
            }

        val chronological = trades.sortedWith(
            compareBy<TradeEntity> { it.tradeDateMillis }
                .thenBy { it.createdAt?.seconds ?: 0L }
                .thenBy { it.id }
        )
        var running = 0.0
        val equityCurve = chronological.mapIndexed { idx, trade ->
            running += trade.actualPnl
            EquityPoint(
                index = idx + 1,
                tradeCode = trade.tradeCode,
                dateIso = trade.tradeDateIso,
                cumulativePnl = running,
                tradePnl = trade.actualPnl
            )
        }

        val streak = calculateStreak(chronological)

        val instrumentPerf = trades
            .groupBy { it.instrument.trim().uppercase(Locale.US).ifEmpty { "OTHER" } }
            .entries
            .map { (symbol, list) ->
                val w = list.count { it.resultEnum == TradeResult.WIN }
                CategoryPerformance(
                    category = symbol,
                    tradeCount = list.size,
                    winCount = w,
                    netPnl = list.sumOf { it.actualPnl },
                    winRate = (w.toDouble() / list.size) * 100.0
                )
            }
            .sortedByDescending { it.netPnl }

        val timeframePerf = Timeframe.entries.mapNotNull { tf ->
            val list = trades.filter { it.timeframeEnum == tf }
            if (list.isEmpty()) null else {
                val w = list.count { it.resultEnum == TradeResult.WIN }
                CategoryPerformance(
                    category = tf.label,
                    tradeCount = list.size,
                    winCount = w,
                    netPnl = list.sumOf { it.actualPnl },
                    winRate = (w.toDouble() / list.size) * 100.0
                )
            }
        }

        return DashboardMetrics(
            totalTrades = totalTrades,
            winningTrades = wins.size,
            losingTrades = losses.size,
            breakEvenTrades = breakEvens.size,
            winRatePercent = winRate,
            lossRatePercent = lossRate,
            totalProfit = totalProfit,
            totalLoss = totalLoss,
            netPnl = netPnl,
            averageWin = avgWin,
            averageLoss = avgLoss,
            profitFactor = profitFactor,
            averageRiskReward = avgRr,
            bestTradingDay = bestDay,
            worstTradingDay = worstDay,
            currentStreak = streak,
            dailyPnlSeries = dailySeries,
            monthlySeries = monthlySeries,
            equityCurve = equityCurve,
            instrumentPerformance = instrumentPerf,
            timeframePerformance = timeframePerf
        )
    }

    private fun calculateStreak(chronological: List<TradeEntity>): StreakInfo {
        if (chronological.isEmpty()) return StreakInfo("0", 0, null)
        val latest = chronological.last().resultEnum
        var count = 0
        for (i in chronological.indices.reversed()) {
            if (chronological[i].resultEnum == latest) count++ else break
        }
        val label = when (latest) {
            TradeResult.WIN -> "${count}W Streak"
            TradeResult.LOSS -> "${count}L Streak"
            TradeResult.BREAK_EVEN -> "${count}BE"
        }
        return StreakInfo(label = label, count = count, type = latest)
    }

    fun calculateDisciplineMetrics(trades: List<TradeEntity>, user: UserEntity): DisciplineMetrics {
        if (trades.isEmpty()) return DisciplineMetrics()
        val today = todayIso()
        val todayTrades = trades.filter { it.tradeDateIso == today }
        val todayPnl = todayTrades.sumOf { it.actualPnl }

        val byDay = trades.groupBy { it.tradeDateIso }
        val overtradingDays = byDay.count { (_, list) -> list.size > user.maxTradesPerDay }
        val lossBreachDays = byDay.count { (_, list) ->
            user.dailyMaxLossLimit > 0.0 && list.sumOf { it.actualPnl } <= -user.dailyMaxLossLimit
        }

        val slSetCount = trades.count { it.stopLoss != null && it.stopLoss > 0.0 }
        val slPercent = (slSetCount.toDouble() / trades.size) * 100.0

        val riskCompliantCount = trades.count {
            user.maxRiskPerTrade <= 0.0 || it.riskAmount <= user.maxRiskPerTrade
        }
        val riskPercent = (riskCompliantCount.toDouble() / trades.size) * 100.0

        val avgRisk = trades.map { it.riskAmount }.average()

        val mistakeMap = mutableMapOf<String, Int>()
        trades.forEach { trade ->
            trade.mistakes.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
                .forEach { mistake ->
                    mistakeMap[mistake] = (mistakeMap[mistake] ?: 0) + 1
                }
        }
        val sortedMistakes = mistakeMap.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        return DisciplineMetrics(
            todayTradeCount = todayTrades.size,
            todayNetPnl = todayPnl,
            isDailyLossLimitBreached = user.dailyMaxLossLimit > 0.0 && todayPnl <= -user.dailyMaxLossLimit,
            isDailyGoalReached = user.dailyProfitGoal > 0.0 && todayPnl >= user.dailyProfitGoal,
            isOvertradingToday = todayTrades.size > user.maxTradesPerDay,
            overtradingDaysCount = overtradingDays,
            dailyLossBreachDaysCount = lossBreachDays,
            stopLossDisciplinePercent = slPercent,
            riskCompliancePercent = riskPercent,
            averageRiskAmount = avgRisk,
            mistakeCounts = sortedMistakes
        )
    }

    fun formatCurrency(value: Double): String {
        val sign = if (value > 0.0) "+" else if (value < 0.0) "-" else ""
        return "$sign$${String.format(Locale.US, "%,.2f", abs(value))}"
    }

    fun formatPlainCurrency(value: Double): String {
        val sign = if (value < 0.0) "-" else ""
        return "$sign$${String.format(Locale.US, "%,.2f", abs(value))}"
    }
}
