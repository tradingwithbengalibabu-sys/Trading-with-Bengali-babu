package com.example.domain

import com.example.data.model.Timeframe
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradingCurrency
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
    val displayCurrency: TradingCurrency = TradingCurrency.INR,
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
    val timeframePerformance: List<CategoryPerformance> = emptyList(),
    val inrTradeCount: Int = 0,
    val usdTradeCount: Int = 0
)

data class DisciplineMetrics(
    val displayCurrency: TradingCurrency = TradingCurrency.INR,
    val todayTradeCount: Int = 0,
    val todayNetPnl: Double = 0.0,
    val convertedDailyGoal: Double = 0.0,
    val convertedDailyMaxLoss: Double = 0.0,
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
    const val DEFAULT_USD_TO_INR_RATE = 84.50

    private val monthInputFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    private val monthLabelFmt = SimpleDateFormat("MMM yy", Locale.US)
    private val isoDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val indianInstrumentTokens = listOf(
        "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX", "BANKEX",
        "RELIANCE", "TCS", "HDFC", "INFY", "ICICI", "SBIN", "TATAMOTORS",
        "TATASTEEL", "BAJFINANCE", "ADANI", "ITC", "BHARTIARTL", "LT", "MCX",
        ".NS", ".BO", "NSE:", "BSE:", "INR"
    )

    fun todayIso(): String = isoDateFmt.format(Date())

    fun suggestCurrencyForInstrument(instrument: String): TradingCurrency {
        val upper = instrument.trim().uppercase(Locale.US)
        if (upper.isEmpty()) return TradingCurrency.USD
        if (upper == "USDINR" || upper == "USD/INR") return TradingCurrency.INR
        return if (indianInstrumentTokens.any { upper.contains(it) }) {
            TradingCurrency.INR
        } else {
            TradingCurrency.USD
        }
    }

    fun convertAmount(
        amount: Double,
        fromCurrency: TradingCurrency,
        toCurrency: TradingCurrency,
        usdToInrRate: Double
    ): Double {
        if (fromCurrency == toCurrency) return amount
        val safeRate = if (usdToInrRate > 0.0) usdToInrRate else DEFAULT_USD_TO_INR_RATE
        return when {
            fromCurrency == TradingCurrency.USD && toCurrency == TradingCurrency.INR -> amount * safeRate
            fromCurrency == TradingCurrency.INR && toCurrency == TradingCurrency.USD -> amount / safeRate
            else -> amount
        }
    }

    fun convertedPnl(trade: TradeEntity, targetCurrency: TradingCurrency): Double {
        return convertAmount(
            amount = trade.netRealizedPnl,
            fromCurrency = trade.currencyEnum,
            toCurrency = targetCurrency,
            usdToInrRate = trade.effectiveUsdToInrRate
        )
    }

    fun convertedRisk(trade: TradeEntity, targetCurrency: TradingCurrency): Double {
        return convertAmount(
            amount = trade.riskAmount,
            fromCurrency = trade.currencyEnum,
            toCurrency = targetCurrency,
            usdToInrRate = trade.effectiveUsdToInrRate
        )
    }

    fun calculateDashboardMetrics(
        trades: List<TradeEntity>,
        displayCurrency: TradingCurrency = TradingCurrency.USD
    ): DashboardMetrics {
        if (trades.isEmpty()) return DashboardMetrics(displayCurrency = displayCurrency)

        val closedTrades = trades.filter { it.isClosedTrade }
        if (closedTrades.isEmpty()) {
            return DashboardMetrics(
                displayCurrency = displayCurrency,
                inrTradeCount = trades.count { it.currencyEnum == TradingCurrency.INR },
                usdTradeCount = trades.count { it.currencyEnum == TradingCurrency.USD }
            )
        }

        val convertedTrades = closedTrades.map { trade ->
            trade to convertedPnl(trade, displayCurrency)
        }

        val totalTrades = closedTrades.size
        val wins = convertedTrades.filter { it.first.resultEnum == TradeResult.WIN }
        val losses = convertedTrades.filter { it.first.resultEnum == TradeResult.LOSS }
        val breakEvens = convertedTrades.filter { it.first.resultEnum == TradeResult.BREAK_EVEN }

        val winRate = (wins.size.toDouble() / totalTrades) * 100.0
        val lossRate = (losses.size.toDouble() / totalTrades) * 100.0

        val totalProfit = convertedTrades.filter { it.second > 0.0 }.sumOf { it.second }
        val totalLoss = convertedTrades.filter { it.second < 0.0 }.sumOf { abs(it.second) }
        val netPnl = convertedTrades.sumOf { it.second }

        val avgWin = if (wins.isNotEmpty()) {
            wins.sumOf { it.second.coerceAtLeast(0.0) } / wins.size
        } else 0.0

        val avgLoss = if (losses.isNotEmpty()) {
            losses.sumOf { abs(it.second) } / losses.size
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

        val dailySeries = convertedTrades
            .groupBy { it.first.tradeDateIso }
            .entries
            .sortedBy { it.key }
            .map { (dateIso, dayTrades) ->
                DayPnlSummary(
                    dateIso = dateIso,
                    netPnl = dayTrades.sumOf { it.second },
                    tradeCount = dayTrades.size
                )
            }

        val bestDay = dailySeries.maxByOrNull { it.netPnl }
        val worstDay = dailySeries.minByOrNull { it.netPnl }

        val monthlySeries = convertedTrades
            .groupBy { it.first.tradeDateIso.take(7) }
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
                    grossProfit = monthTrades.filter { it.second > 0 }.sumOf { it.second },
                    grossLoss = monthTrades.filter { it.second < 0 }.sumOf { abs(it.second) },
                    netPnl = monthTrades.sumOf { it.second },
                    tradeCount = monthTrades.size
                )
            }

        val chronological = convertedTrades.sortedWith(
            compareBy<Pair<TradeEntity, Double>> { it.first.tradeDateMillis }
                .thenBy { it.first.createdAt?.seconds ?: 0L }
                .thenBy { it.first.id }
        )
        var running = 0.0
        val equityCurve = chronological.mapIndexed { idx, (trade, pnlConverted) ->
            running += pnlConverted
            EquityPoint(
                index = idx + 1,
                tradeCode = trade.tradeCode,
                dateIso = trade.tradeDateIso,
                cumulativePnl = running,
                tradePnl = pnlConverted
            )
        }

        val streak = calculateStreak(chronological.map { it.first })

        val instrumentPerf = convertedTrades
            .groupBy { it.first.instrument.trim().uppercase(Locale.US).ifEmpty { "OTHER" } }
            .entries
            .map { (symbol, list) ->
                val w = list.count { it.first.resultEnum == TradeResult.WIN }
                CategoryPerformance(
                    category = symbol,
                    tradeCount = list.size,
                    winCount = w,
                    netPnl = list.sumOf { it.second },
                    winRate = (w.toDouble() / list.size) * 100.0
                )
            }
            .sortedByDescending { it.netPnl }

        val timeframePerf = Timeframe.entries.mapNotNull { tf ->
            val list = convertedTrades.filter { it.first.timeframeEnum == tf }
            if (list.isEmpty()) null else {
                val w = list.count { it.first.resultEnum == TradeResult.WIN }
                CategoryPerformance(
                    category = tf.label,
                    tradeCount = list.size,
                    winCount = w,
                    netPnl = list.sumOf { it.second },
                    winRate = (w.toDouble() / list.size) * 100.0
                )
            }
        }

        val inrCount = trades.count { it.currencyEnum == TradingCurrency.INR }
        val usdCount = trades.count { it.currencyEnum == TradingCurrency.USD }

        return DashboardMetrics(
            displayCurrency = displayCurrency,
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
            timeframePerformance = timeframePerf,
            inrTradeCount = inrCount,
            usdTradeCount = usdCount
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

    fun calculateDisciplineMetrics(
        trades: List<TradeEntity>,
        user: UserEntity,
        displayCurrency: TradingCurrency = TradingCurrency.USD,
        currentUsdToInrRate: Double = DEFAULT_USD_TO_INR_RATE
    ): DisciplineMetrics {
        val convertedGoal = convertAmount(
            amount = user.dailyProfitGoal,
            fromCurrency = user.preferredCurrencyEnum,
            toCurrency = displayCurrency,
            usdToInrRate = currentUsdToInrRate
        )
        val convertedMaxLoss = convertAmount(
            amount = user.dailyMaxLossLimit,
            fromCurrency = user.preferredCurrencyEnum,
            toCurrency = displayCurrency,
            usdToInrRate = currentUsdToInrRate
        )

        if (trades.isEmpty()) {
            return DisciplineMetrics(
                displayCurrency = displayCurrency,
                convertedDailyGoal = convertedGoal,
                convertedDailyMaxLoss = convertedMaxLoss
            )
        }

        val today = todayIso()
        val todayTrades = trades.filter { it.tradeDateIso == today }
        val todayPnl = todayTrades.sumOf { convertedPnl(it, displayCurrency) }

        val byDay = trades.groupBy { it.tradeDateIso }
        val overtradingDays = byDay.count { (_, list) -> list.size > user.maxTradesPerDay }
        val lossBreachDays = byDay.count { (_, list) ->
            val dayPnlInPref = list.sumOf { convertedPnl(it, user.preferredCurrencyEnum) }
            user.dailyMaxLossLimit > 0.0 && dayPnlInPref <= -user.dailyMaxLossLimit
        }

        val slSetCount = trades.count { it.stopLoss != null && it.stopLoss > 0.0 }
        val slPercent = (slSetCount.toDouble() / trades.size) * 100.0

        val riskCompliantCount = trades.count { trade ->
            val riskInPref = convertedRisk(trade, user.preferredCurrencyEnum)
            user.maxRiskPerTrade <= 0.0 || riskInPref <= user.maxRiskPerTrade + 1e-6
        }
        val riskPercent = (riskCompliantCount.toDouble() / trades.size) * 100.0

        val avgRisk = trades.map { convertedRisk(it, displayCurrency) }.average()

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

        val todayPnlInPref = todayTrades.sumOf { convertedPnl(it, user.preferredCurrencyEnum) }

        return DisciplineMetrics(
            displayCurrency = displayCurrency,
            todayTradeCount = todayTrades.size,
            todayNetPnl = todayPnl,
            convertedDailyGoal = convertedGoal,
            convertedDailyMaxLoss = convertedMaxLoss,
            isDailyLossLimitBreached = user.dailyMaxLossLimit > 0.0 && todayPnlInPref <= -user.dailyMaxLossLimit,
            isDailyGoalReached = user.dailyProfitGoal > 0.0 && todayPnlInPref >= user.dailyProfitGoal,
            isOvertradingToday = todayTrades.size > user.maxTradesPerDay,
            overtradingDaysCount = overtradingDays,
            dailyLossBreachDaysCount = lossBreachDays,
            stopLossDisciplinePercent = slPercent,
            riskCompliancePercent = riskPercent,
            averageRiskAmount = avgRisk,
            mistakeCounts = sortedMistakes
        )
    }

    fun formatCurrency(value: Double, currency: TradingCurrency = TradingCurrency.USD): String {
        val sign = if (value > 0.0) "+" else if (value < 0.0) "-" else ""
        return "$sign${currency.symbol}${String.format(Locale.US, "%,.2f", abs(value))}"
    }

    fun formatPlainCurrency(value: Double, currency: TradingCurrency = TradingCurrency.USD): String {
        val sign = if (value < 0.0) "-" else ""
        return "$sign${currency.symbol}${String.format(Locale.US, "%,.2f", abs(value))}"
    }

    fun formatCompactCurrency(value: Double, currency: TradingCurrency = TradingCurrency.USD): String {
        val sign = if (value > 0.0) "+" else if (value < 0.0) "-" else ""
        val mag = abs(value)
        val formatted = when {
            mag >= 1_000_000.0 -> String.format(Locale.US, "%.1fM", mag / 1_000_000.0)
            mag >= 1_000.0 -> String.format(Locale.US, "%.1fk", mag / 1_000.0)
            else -> String.format(Locale.US, "%.0f", mag)
        }
        return "$sign${currency.symbol}$formatted"
    }
}
