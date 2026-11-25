package com.example.domain

import com.example.data.local.CustomTradingSession
import com.example.data.model.AnalysisPeriodType
import com.example.data.model.ExitClosureType
import com.example.data.model.TimeOfDayInterval
import com.example.data.model.Timeframe
import com.example.data.model.TradeDurationBucket
import com.example.data.model.TradeEntity
import com.example.data.model.TradeResult
import com.example.data.model.TradingCurrency
import com.example.data.model.WeekdayFilter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object TradeDateTimeHelper {
    const val DEFAULT_TIMEZONE_ID = "Asia/Kolkata"
    private const val MILLIS_PER_DAY = 86_400_000L

    fun resolveTimeZone(timezoneId: String?): TimeZone {
        val clean = timezoneId?.trim().orEmpty().ifEmpty { DEFAULT_TIMEZONE_ID }
        val tz = TimeZone.getTimeZone(clean)
        // TimeZone.getTimeZone falls back to GMT if unknown unless clean == "GMT"/"UTC"
        return if (tz.id == "GMT" && !clean.equals("GMT", true) && !clean.equals("UTC", true)) {
            TimeZone.getTimeZone(DEFAULT_TIMEZONE_ID)
        } else {
            tz
        }
    }

    fun formatDateIso(millis: Long, timezoneId: String = DEFAULT_TIMEZONE_ID): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = resolveTimeZone(timezoneId)
        }
        return fmt.format(Date(millis))
    }

    fun formatTime12h(millis: Long, timezoneId: String = DEFAULT_TIMEZONE_ID): String {
        val fmt = SimpleDateFormat("hh:mm a", Locale.US).apply {
            timeZone = resolveTimeZone(timezoneId)
        }
        return fmt.format(Date(millis))
    }

    fun formatTime24h(millis: Long, timezoneId: String = DEFAULT_TIMEZONE_ID): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US).apply {
            timeZone = resolveTimeZone(timezoneId)
        }
        return fmt.format(Date(millis))
    }

    fun formatDateTimeDisplay(millis: Long, timezoneId: String = DEFAULT_TIMEZONE_ID): String {
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).apply {
            timeZone = resolveTimeZone(timezoneId)
        }
        return fmt.format(Date(millis))
    }

    fun shortTimezoneTag(timezoneId: String = DEFAULT_TIMEZONE_ID): String {
        return when (timezoneId) {
            "Asia/Kolkata" -> "IST"
            "UTC" -> "UTC"
            "Europe/London" -> "LON"
            "America/New_York" -> "NYC"
            "Asia/Dubai" -> "GST"
            "Asia/Singapore" -> "SGT"
            "Asia/Tokyo" -> "JST"
            else -> resolveTimeZone(timezoneId).getDisplayName(false, TimeZone.SHORT, Locale.US)
        }
    }

    fun parseDateIsoStartMillis(dateIso: String, timezoneId: String = DEFAULT_TIMEZONE_ID): Long? {
        if (dateIso.length != 10) return null
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = resolveTimeZone(timezoneId)
            }
            fmt.parse(dateIso)?.time
        }.getOrNull()
    }

    fun combineDateAndTimeMillis(
        dateMillis: Long,
        hourOfDay: Int,
        minute: Int,
        timezoneId: String = DEFAULT_TIMEZONE_ID
    ): Long {
        val tz = resolveTimeZone(timezoneId)
        val dateIso = formatDateIso(dateMillis, timezoneId)
        val parts = dateIso.split("-")
        val cal = Calendar.getInstance(tz).apply {
            clear()
            if (parts.size == 3) {
                set(
                    parts[0].toIntOrNull() ?: 2026,
                    ((parts[1].toIntOrNull() ?: 1) - 1).coerceIn(0, 11),
                    (parts[2].toIntOrNull() ?: 1).coerceIn(1, 31),
                    hourOfDay.coerceIn(0, 23),
                    minute.coerceIn(0, 59),
                    0
                )
            } else {
                timeInMillis = dateMillis
                set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
            }
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun hourAndMinuteOf(millis: Long, timezoneId: String = DEFAULT_TIMEZONE_ID): Pair<Int, Int> {
        val cal = Calendar.getInstance(resolveTimeZone(timezoneId)).apply {
            timeInMillis = millis
        }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    fun minuteOfDay(millis: Long, timezoneId: String = DEFAULT_TIMEZONE_ID): Int {
        val (h, m) = hourAndMinuteOf(millis, timezoneId)
        return h * 60 + m
    }

    fun weekdayOf(
        entryTimeMillis: Long?,
        tradeDateMillis: Long,
        tradeDateIso: String,
        timezoneId: String = DEFAULT_TIMEZONE_ID
    ): WeekdayFilter? {
        val tz = resolveTimeZone(timezoneId)
        val targetMillis = when {
            entryTimeMillis != null && entryTimeMillis > 0L -> entryTimeMillis
            tradeDateIso.length == 10 -> parseDateIsoStartMillis(tradeDateIso, timezoneId)?.plus(12 * 3600_000L)
                ?: tradeDateMillis
            else -> tradeDateMillis
        }
        if (targetMillis <= 0L) return null
        val cal = Calendar.getInstance(tz).apply { timeInMillis = targetMillis }
        return WeekdayFilter.fromCalendarDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))
    }

    fun timeOfDayIntervalForHour(hour: Int): TimeOfDayInterval {
        return when (hour.coerceIn(0, 23)) {
            in 5..8 -> TimeOfDayInterval.EARLY_MORNING
            in 9..11 -> TimeOfDayInterval.MORNING
            in 12..16 -> TimeOfDayInterval.AFTERNOON
            in 17..20 -> TimeOfDayInterval.EVENING
            else -> TimeOfDayInterval.NIGHT
        }
    }

    fun resolveSessionName(
        trade: TradeEntity,
        sessions: List<CustomTradingSession>,
        timezoneId: String = DEFAULT_TIMEZONE_ID
    ): String {
        if (trade.tradingSession.isNotBlank()) return trade.tradingSession.trim()
        val entry = trade.entryTimeMillis?.takeIf { it > 0L } ?: return "Unassigned"
        val minOfDay = minuteOfDay(entry, timezoneId)
        return sessions.firstOrNull { it.containsMinute(minOfDay) }?.name ?: "Off-Session"
    }

    fun suggestSessionForTimestamp(
        entryTimeMillis: Long,
        sessions: List<CustomTradingSession>,
        timezoneId: String = DEFAULT_TIMEZONE_ID
    ): String {
        val minOfDay = minuteOfDay(entryTimeMillis, timezoneId)
        return sessions.firstOrNull { it.containsMinute(minOfDay) }?.name ?: "Off-Session"
    }

    fun formatDuration(durationMillis: Long): String {
        if (durationMillis < 0L) return "0m"
        val totalSeconds = durationMillis / 1000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            days > 0L -> if (hours > 0L) "${days}d ${hours}h" else "${days}d ${minutes}m"
            hours > 0L -> if (minutes > 0L) "${hours}h ${minutes}m" else "${hours}h"
            minutes > 0L -> if (seconds > 0L && minutes < 10L) "${minutes}m ${seconds}s" else "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun durationBucketFor(durationMillis: Long): TradeDurationBucket {
        val minutes = durationMillis / 60_000.0
        return when {
            minutes < 15.0 -> TradeDurationBucket.SHORT
            minutes <= 120.0 -> TradeDurationBucket.MEDIUM
            else -> TradeDurationBucket.LONG
        }
    }

    fun monthRangeIso(year: Int, monthZeroBased: Int): Pair<String, String> {
        val cal = Calendar.getInstance(Locale.US).apply {
            clear()
            set(year, monthZeroBased.coerceIn(0, 11), 1)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val mStr = String.format(Locale.US, "%02d", monthZeroBased + 1)
        return "$year-$mStr-01" to "$year-$mStr-${String.format(Locale.US, "%02d", daysInMonth)}"
    }

    fun shiftDateIso(dateIso: String, deltaDays: Int, timezoneId: String = DEFAULT_TIMEZONE_ID): String {
        val base = parseDateIsoStartMillis(dateIso, timezoneId) ?: System.currentTimeMillis()
        val cal = Calendar.getInstance(resolveTimeZone(timezoneId)).apply {
            timeInMillis = base + 12 * 3600_000L
            add(Calendar.DAY_OF_YEAR, deltaDays)
        }
        return formatDateIso(cal.timeInMillis, timezoneId)
    }

    fun currentYearAndMonth(timezoneId: String = DEFAULT_TIMEZONE_ID): Pair<Int, Int> {
        val cal = Calendar.getInstance(resolveTimeZone(timezoneId))
        return cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    fun daysBetweenIso(startIso: String, endIso: String, timezoneId: String = DEFAULT_TIMEZONE_ID): Long {
        val s = parseDateIsoStartMillis(startIso, timezoneId) ?: return 0L
        val e = parseDateIsoStartMillis(endIso, timezoneId) ?: return 0L
        return (e - s) / MILLIS_PER_DAY
    }
}

data class AdvancedTradeFilter(
    val specificDateIso: String? = null,
    val dateStartIso: String? = null,
    val dateEndIso: String? = null,
    val timeStartMinutes: Int? = null,
    val timeEndMinutes: Int? = null,
    val weekdays: Set<WeekdayFilter> = emptySet(),
    val sessions: Set<String> = emptySet(),
    val instruments: Set<String> = emptySet(),
    val timeframes: Set<Timeframe> = emptySet(),
    val strategies: Set<String> = emptySet(),
    val results: Set<TradeResult> = emptySet(),
    val durationBuckets: Set<TradeDurationBucket> = emptySet()
) {
    val activeCount: Int
        get() {
            var count = 0
            if (!specificDateIso.isNullOrBlank()) count++
            if (!dateStartIso.isNullOrBlank() || !dateEndIso.isNullOrBlank()) count++
            if (timeStartMinutes != null || timeEndMinutes != null) count++
            if (weekdays.isNotEmpty()) count++
            if (sessions.isNotEmpty()) count++
            if (instruments.isNotEmpty()) count++
            if (timeframes.isNotEmpty()) count++
            if (strategies.isNotEmpty()) count++
            if (results.isNotEmpty()) count++
            if (durationBuckets.isNotEmpty()) count++
            return count
        }

    val isActive: Boolean get() = activeCount > 0
}

data class EnrichedTrade(
    val trade: TradeEntity,
    val effectiveDateIso: String,
    val weekday: WeekdayFilter?,
    val entryHour: Int?,
    val entryMinuteOfDay: Int?,
    val timeOfDayInterval: TimeOfDayInterval?,
    val resolvedSession: String,
    val holdingDurationMillis: Long?,
    val durationBucket: TradeDurationBucket?,
    val timeSincePrevTradeMillis: Long?,
    val sameDaySequenceNumber: Int,
    val isAfterLossSameDay: Boolean,
    val resolvedClosureType: ExitClosureType,
    val convertedPnl: Double,
    val convertedRisk: Double
) {
    val hasExactEntryTime: Boolean get() = trade.hasExactEntryTime
    val hasExactExitTime: Boolean get() = trade.hasExactExitTime
}

data class TimeOfDayPerformance(
    val interval: TimeOfDayInterval,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val breakEvenCount: Int,
    val netPnl: Double,
    val grossProfit: Double,
    val grossLoss: Double,
    val avgProfit: Double,
    val avgLoss: Double,
    val winRatePercent: Double
)

data class HourlyPerformance(
    val hour: Int,
    val hourLabel: String,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val netPnl: Double,
    val avgPnl: Double,
    val winRatePercent: Double
)

data class WeekdayPerformance(
    val weekday: WeekdayFilter,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val netPnl: Double,
    val winRatePercent: Double
)

data class SessionPerformance(
    val sessionName: String,
    val windowLabel: String,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val netPnl: Double,
    val winRatePercent: Double
)

data class DurationBucketPerformance(
    val bucket: TradeDurationBucket,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val netPnl: Double,
    val winRatePercent: Double,
    val avgDurationMillis: Long
)

data class DurationAnalysis(
    val tradesWithDurationCount: Int = 0,
    val tradesMissingDurationCount: Int = 0,
    val avgOverallHoldMillis: Long? = null,
    val avgWinningHoldMillis: Long? = null,
    val avgLosingHoldMillis: Long? = null,
    val bucketPerformance: List<DurationBucketPerformance> = emptyList(),
    val longerHoldsImproveProfitability: Boolean? = null,
    val durationTrendConclusion: String = "Log entry and exit times on trades to analyze holding duration."
)

data class SequenceBucketPerformance(
    val label: String,
    val tradeCount: Int,
    val winCount: Int,
    val netPnl: Double,
    val winRatePercent: Double
)

data class FrequencyOvertradingStats(
    val activeTradingDays: Int = 0,
    val avgTradesPerDay: Double = 0.0,
    val maxTradesInSingleDay: Int = 0,
    val maxTradesDateIso: String? = null,
    val peakTradesInSingleHour: Int = 0,
    val peakHourLabel: String? = null,
    val avgGapBetweenSameDayTradesMillis: Long? = null,
    val minGapBetweenTradesMillis: Long? = null,
    val rapidReEntryCount: Int = 0,
    val rapidReEntryAfterLossCount: Int = 0,
    val rapidReEntryAfterLossNetPnl: Double = 0.0,
    val sequencePerformance: List<SequenceBucketPerformance> = emptyList(),
    val overtradingDetected: Boolean = false,
    val overtradingSummary: String = "No overtrading signals detected from recorded trades."
)

data class EntryExitAnalysis(
    val bestEntryHour: HourlyPerformance? = null,
    val worstEntryHour: HourlyPerformance? = null,
    val avgPnlPerTrade: Double = 0.0,
    val slHitCount: Int = 0,
    val slHitPercent: Double = 0.0,
    val tpHitCount: Int = 0,
    val tpHitPercent: Double = 0.0,
    val trailingSlCount: Int = 0,
    val manualCloseCount: Int = 0,
    val autoCloseCount: Int = 0,
    val breakevenCloseCount: Int = 0,
    val unknownCloseCount: Int = 0,
    val earlyExitCount: Int = 0,
    val earlyExitNetPnl: Double = 0.0,
    val prolongedLossHoldCount: Int = 0
)

data class SmartPerformanceInsight(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val isPositive: Boolean,
    val evidenceCount: Int
)

enum class CalendarDayStatus {
    PROFIT,
    LOSS,
    FLAT,
    NONE
}

data class CalendarDayCell(
    val dayOfMonth: Int,
    val dateIso: String,
    val netPnl: Double,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val breakEvenCount: Int,
    val status: CalendarDayStatus,
    val trades: List<EnrichedTrade>
)

data class CalendarMonthData(
    val year: Int,
    val monthZeroBased: Int,
    val monthTitle: String,
    val firstWeekdayOffsetMondayZero: Int,
    val days: List<CalendarDayCell>,
    val monthNetPnl: Double,
    val monthTotalTrades: Int,
    val profitableDays: Int,
    val losingDays: Int
)

data class PeriodPerformanceReport(
    val periodType: AnalysisPeriodType,
    val periodTitle: String,
    val dateRangeIso: String,
    val displayCurrency: TradingCurrency,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakEvenTrades: Int,
    val winRatePercent: Double,
    val grossProfit: Double,
    val grossLoss: Double,
    val netPnl: Double,
    val averageWin: Double,
    val averageLoss: Double,
    val bestTradingTime: String,
    val worstTradingTime: String,
    val bestInstrument: String,
    val worstInstrument: String,
    val bestStrategy: String,
    val worstStrategy: String,
    val bestTimeframe: String,
    val worstTimeframe: String,
    val dashboardMetrics: DashboardMetrics,
    val trades: List<EnrichedTrade>
)

data class AdvancedAnalysisResult(
    val displayCurrency: TradingCurrency,
    val timezoneId: String,
    val totalSourceTrades: Int,
    val enrichedTrades: List<EnrichedTrade>,
    val dashboardMetrics: DashboardMetrics,
    val exactTimestampCount: Int,
    val legacyTimestampCount: Int,
    val timeOfDayStats: List<TimeOfDayPerformance>,
    val mostProfitableTimeOfDay: TimeOfDayPerformance?,
    val mostLossMakingTimeOfDay: TimeOfDayPerformance?,
    val hourlyStats: List<HourlyPerformance>,
    val weekdayStats: List<WeekdayPerformance>,
    val bestWeekday: WeekdayPerformance?,
    val worstWeekday: WeekdayPerformance?,
    val sessionStats: List<SessionPerformance>,
    val bestSession: SessionPerformance?,
    val worstSession: SessionPerformance?,
    val durationAnalysis: DurationAnalysis,
    val frequencyStats: FrequencyOvertradingStats,
    val entryExitAnalysis: EntryExitAnalysis,
    val smartInsights: List<SmartPerformanceInsight>,
    val availableInstruments: List<String>,
    val availableStrategies: List<String>,
    val availableSessions: List<String>
)

object AdvancedTradeAnalytics {

    fun enrichTrades(
        trades: List<TradeEntity>,
        displayCurrency: TradingCurrency,
        timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
        customSessions: List<CustomTradingSession> = CustomTradingSession.DEFAULT_SESSIONS
    ): List<EnrichedTrade> {
        if (trades.isEmpty()) return emptyList()

        val sortedChronological = trades.sortedWith(
            compareBy<TradeEntity> { it.effectiveEntryMillis }
                .thenBy { it.tradeDateMillis }
                .thenBy { it.createdAt?.seconds ?: 0L }
                .thenBy { it.id }
        )

        val dateCounter = mutableMapOf<String, Int>()
        var prevTrade: TradeEntity? = null

        return sortedChronological.map { trade ->
            val convPnl = TradingAnalytics.convertedPnl(trade, displayCurrency)
            val convRisk = TradingAnalytics.convertedRisk(trade, displayCurrency)
            val effectiveDateIso = when {
                trade.tradeDateIso.length == 10 -> trade.tradeDateIso
                trade.hasExactEntryTime -> TradeDateTimeHelper.formatDateIso(trade.entryTimeMillis!!, timezoneId)
                trade.tradeDateMillis > 0L -> TradeDateTimeHelper.formatDateIso(trade.tradeDateMillis, timezoneId)
                else -> "1970-01-01"
            }

            val seqNum = (dateCounter[effectiveDateIso] ?: 0) + 1
            dateCounter[effectiveDateIso] = seqNum

            val prev = prevTrade
            val isSameDayPrev = prev != null &&
                (prev.tradeDateIso == effectiveDateIso ||
                    (prev.hasExactEntryTime && prev.entryTimeMillis != null &&
                        TradeDateTimeHelper.formatDateIso(prev.entryTimeMillis, timezoneId) == effectiveDateIso))
            val isAfterLossSameDay = isSameDayPrev && prev.resultEnum == TradeResult.LOSS

            val currentEntry = trade.entryTimeMillis
            val prevEntry = prev?.entryTimeMillis
            val gapMillis = if (trade.hasExactEntryTime && currentEntry != null && prev != null && prev.hasExactEntryTime && prevEntry != null) {
                val prevExitOrEntry = prev.exitTimeMillis?.takeIf { it > 0L && it <= currentEntry }
                    ?: prevEntry
                val diff = currentEntry - prevExitOrEntry
                if (diff >= 0L) diff else null
            } else {
                null
            }

            val (hour, minOfDay) = if (trade.hasExactEntryTime) {
                val (h, m) = TradeDateTimeHelper.hourAndMinuteOf(trade.entryTimeMillis!!, timezoneId)
                h to (h * 60 + m)
            } else {
                null to null
            }

            val interval = hour?.let { TradeDateTimeHelper.timeOfDayIntervalForHour(it) }
            val weekday = TradeDateTimeHelper.weekdayOf(
                entryTimeMillis = trade.entryTimeMillis,
                tradeDateMillis = trade.tradeDateMillis,
                tradeDateIso = effectiveDateIso,
                timezoneId = timezoneId
            )
            val resolvedSession = TradeDateTimeHelper.resolveSessionName(trade, customSessions, timezoneId)
            val durationMillis = trade.holdingDurationMillis
            val durationBucket = durationMillis?.let { TradeDateTimeHelper.durationBucketFor(it) }

            prevTrade = trade

            EnrichedTrade(
                trade = trade,
                effectiveDateIso = effectiveDateIso,
                weekday = weekday,
                entryHour = hour,
                entryMinuteOfDay = minOfDay,
                timeOfDayInterval = interval,
                resolvedSession = resolvedSession,
                holdingDurationMillis = durationMillis,
                durationBucket = durationBucket,
                timeSincePrevTradeMillis = gapMillis,
                sameDaySequenceNumber = seqNum,
                isAfterLossSameDay = isAfterLossSameDay,
                resolvedClosureType = trade.resolvedExitClosureType,
                convertedPnl = convPnl,
                convertedRisk = convRisk
            )
        }
    }

    fun applyFilter(
        enrichedTrades: List<EnrichedTrade>,
        filter: AdvancedTradeFilter
    ): List<EnrichedTrade> {
        if (!filter.isActive) return enrichedTrades
        return enrichedTrades.filter { item ->
            val t = item.trade
            if (!filter.specificDateIso.isNullOrBlank() && item.effectiveDateIso != filter.specificDateIso) {
                return@filter false
            }
            if (!filter.dateStartIso.isNullOrBlank() && item.effectiveDateIso < filter.dateStartIso) {
                return@filter false
            }
            if (!filter.dateEndIso.isNullOrBlank() && item.effectiveDateIso > filter.dateEndIso) {
                return@filter false
            }
            if (filter.timeStartMinutes != null || filter.timeEndMinutes != null) {
                val min = item.entryMinuteOfDay ?: return@filter false
                val start = filter.timeStartMinutes ?: 0
                val end = filter.timeEndMinutes ?: 1439
                val inTime = if (start <= end) min in start..end else (min >= start || min <= end)
                if (!inTime) return@filter false
            }
            if (filter.weekdays.isNotEmpty() && (item.weekday == null || item.weekday !in filter.weekdays)) {
                return@filter false
            }
            if (filter.sessions.isNotEmpty() && item.resolvedSession !in filter.sessions) {
                return@filter false
            }
            if (filter.instruments.isNotEmpty()) {
                val norm = t.instrument.trim().uppercase(Locale.US)
                if (norm !in filter.instruments) return@filter false
            }
            if (filter.timeframes.isNotEmpty() && t.timeframeEnum !in filter.timeframes) {
                return@filter false
            }
            if (filter.strategies.isNotEmpty()) {
                val strat = t.strategy.trim().ifEmpty { "General" }
                if (strat !in filter.strategies) return@filter false
            }
            if (filter.results.isNotEmpty() && t.resultEnum !in filter.results) {
                return@filter false
            }
            if (filter.durationBuckets.isNotEmpty() && (item.durationBucket == null || item.durationBucket !in filter.durationBuckets)) {
                return@filter false
            }
            true
        }
    }

    fun analyze(
        trades: List<TradeEntity>,
        displayCurrency: TradingCurrency,
        timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
        customSessions: List<CustomTradingSession> = CustomTradingSession.DEFAULT_SESSIONS,
        filter: AdvancedTradeFilter = AdvancedTradeFilter(),
        maxTradesPerDayThreshold: Int = 5
    ): AdvancedAnalysisResult {
        val allEnriched = enrichTrades(trades, displayCurrency, timezoneId, customSessions)
        val filtered = applyFilter(allEnriched, filter)
        val filteredRaw = filtered.map { it.trade }
        val dashboardMetrics = TradingAnalytics.calculateDashboardMetrics(filteredRaw, displayCurrency)

        val exactTsCount = filtered.count { it.hasExactEntryTime }
        val legacyTsCount = filtered.size - exactTsCount

        // 1. Time of Day
        val timeOfDayStats = TimeOfDayInterval.entries.map { interval ->
            val group = filtered.filter { it.timeOfDayInterval == interval }
            val wins = group.filter { it.trade.resultEnum == TradeResult.WIN }
            val losses = group.filter { it.trade.resultEnum == TradeResult.LOSS }
            val be = group.count { it.trade.resultEnum == TradeResult.BREAK_EVEN }
            val grossProfit = group.filter { it.convertedPnl > 0 }.sumOf { it.convertedPnl }
            val grossLoss = group.filter { it.convertedPnl < 0 }.sumOf { abs(it.convertedPnl) }
            val avgWin = if (wins.isNotEmpty()) wins.sumOf { it.convertedPnl.coerceAtLeast(0.0) } / wins.size else 0.0
            val avgLoss = if (losses.isNotEmpty()) losses.sumOf { abs(it.convertedPnl) } / losses.size else 0.0
            val wr = if (group.isNotEmpty()) (wins.size.toDouble() / group.size) * 100.0 else 0.0
            TimeOfDayPerformance(
                interval = interval,
                tradeCount = group.size,
                winCount = wins.size,
                lossCount = losses.size,
                breakEvenCount = be,
                netPnl = group.sumOf { it.convertedPnl },
                grossProfit = grossProfit,
                grossLoss = grossLoss,
                avgProfit = avgWin,
                avgLoss = avgLoss,
                winRatePercent = wr
            )
        }
        val activeTimeOfDay = timeOfDayStats.filter { it.tradeCount > 0 }
        val mostProfitableTod = activeTimeOfDay.maxByOrNull { it.netPnl }?.takeIf { it.netPnl > 0 || activeTimeOfDay.size > 1 }
        val mostLossTod = activeTimeOfDay.minByOrNull { it.netPnl }?.takeIf { it.netPnl < 0 || activeTimeOfDay.size > 1 }

        // 2. Hourly P&L
        val hourlyStats = (0..23).mapNotNull { h ->
            val group = filtered.filter { it.entryHour == h }
            if (group.isEmpty()) null
            else {
                val w = group.count { it.trade.resultEnum == TradeResult.WIN }
                val l = group.count { it.trade.resultEnum == TradeResult.LOSS }
                val net = group.sumOf { it.convertedPnl }
                HourlyPerformance(
                    hour = h,
                    hourLabel = String.format(Locale.US, "%02d:00–%02d:59", h, h),
                    tradeCount = group.size,
                    winCount = w,
                    lossCount = l,
                    netPnl = net,
                    avgPnl = net / group.size,
                    winRatePercent = (w.toDouble() / group.size) * 100.0
                )
            }
        }

        // 3. Weekday Performance (Mon-Sun)
        val weekdayStats = WeekdayFilter.entries.map { wd ->
            val group = filtered.filter { it.weekday == wd }
            val w = group.count { it.trade.resultEnum == TradeResult.WIN }
            val l = group.count { it.trade.resultEnum == TradeResult.LOSS }
            val wr = if (group.isNotEmpty()) (w.toDouble() / group.size) * 100.0 else 0.0
            WeekdayPerformance(
                weekday = wd,
                tradeCount = group.size,
                winCount = w,
                lossCount = l,
                netPnl = group.sumOf { it.convertedPnl },
                winRatePercent = wr
            )
        }
        val activeWeekdays = weekdayStats.filter { it.tradeCount > 0 }
        val bestWeekday = activeWeekdays.maxByOrNull { it.netPnl }?.takeIf { it.netPnl > 0 || activeWeekdays.size > 1 }
        val worstWeekday = activeWeekdays.minByOrNull { it.netPnl }?.takeIf { it.netPnl < 0 || activeWeekdays.size > 1 }

        // 4. Session Performance
        val sessionWindowMap = customSessions.associate { it.name to it.windowLabel }
        val sessionGroups = filtered.groupBy { it.resolvedSession }
        val sessionStats = sessionGroups.entries.map { (name, group) ->
            val w = group.count { it.trade.resultEnum == TradeResult.WIN }
            val l = group.count { it.trade.resultEnum == TradeResult.LOSS }
            SessionPerformance(
                sessionName = name,
                windowLabel = sessionWindowMap[name] ?: "Custom / Recorded",
                tradeCount = group.size,
                winCount = w,
                lossCount = l,
                netPnl = group.sumOf { it.convertedPnl },
                winRatePercent = (w.toDouble() / group.size) * 100.0
            )
        }.sortedByDescending { it.netPnl }
        val reportableSessions = sessionStats.filter { it.sessionName != "Unassigned" }.ifEmpty { sessionStats }
        val bestSession = reportableSessions.maxByOrNull { it.netPnl }
        val worstSession = reportableSessions.minByOrNull { it.netPnl }?.takeIf { reportableSessions.size > 1 || it.netPnl < 0 }

        // 5. Duration Analysis
        val durationAnalysis = computeDurationAnalysis(filtered)

        // 6. Frequency & Overtrading Analysis
        val frequencyStats = computeFrequencyAnalysis(filtered, maxTradesPerDayThreshold)

        // 7. Entry & Exit Analysis
        val entryExitStats = computeEntryExitAnalysis(filtered, hourlyStats, durationAnalysis.avgWinningHoldMillis)

        // 8. Data-Driven Smart Performance Insights
        val insights = generateSmartInsights(
            filtered = filtered,
            displayCurrency = displayCurrency,
            timeOfDayStats = timeOfDayStats,
            weekdayStats = weekdayStats,
            sessionStats = sessionStats,
            durationAnalysis = durationAnalysis,
            frequencyStats = frequencyStats
        )

        val availableInstruments = allEnriched
            .map { it.trade.instrument.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        val availableStrategies = allEnriched
            .map { it.trade.strategy.trim().ifEmpty { "General" } }
            .distinct()
            .sorted()
        val availableSessions = (customSessions.map { it.name } + allEnriched.map { it.resolvedSession })
            .distinct()

        return AdvancedAnalysisResult(
            displayCurrency = displayCurrency,
            timezoneId = timezoneId,
            totalSourceTrades = trades.size,
            enrichedTrades = filtered.sortedWith(
                compareByDescending<EnrichedTrade> { it.trade.effectiveEntryMillis }
                    .thenByDescending { it.trade.createdAt?.seconds ?: 0L }
            ),
            dashboardMetrics = dashboardMetrics,
            exactTimestampCount = exactTsCount,
            legacyTimestampCount = legacyTsCount,
            timeOfDayStats = timeOfDayStats,
            mostProfitableTimeOfDay = mostProfitableTod,
            mostLossMakingTimeOfDay = mostLossTod,
            hourlyStats = hourlyStats,
            weekdayStats = weekdayStats,
            bestWeekday = bestWeekday,
            worstWeekday = worstWeekday,
            sessionStats = sessionStats,
            bestSession = bestSession,
            worstSession = worstSession,
            durationAnalysis = durationAnalysis,
            frequencyStats = frequencyStats,
            entryExitAnalysis = entryExitStats,
            smartInsights = insights,
            availableInstruments = availableInstruments,
            availableStrategies = availableStrategies,
            availableSessions = availableSessions
        )
    }

    private fun computeDurationAnalysis(filtered: List<EnrichedTrade>): DurationAnalysis {
        val withDuration = filtered.filter { it.holdingDurationMillis != null }
        val missingCount = filtered.size - withDuration.size
        if (withDuration.isEmpty()) {
            return DurationAnalysis(
                tradesWithDurationCount = 0,
                tradesMissingDurationCount = missingCount
            )
        }

        val winDurations = withDuration
            .filter { it.trade.resultEnum == TradeResult.WIN }
            .mapNotNull { it.holdingDurationMillis }
        val lossDurations = withDuration
            .filter { it.trade.resultEnum == TradeResult.LOSS }
            .mapNotNull { it.holdingDurationMillis }

        val avgOverall = withDuration.mapNotNull { it.holdingDurationMillis }.average().toLong()
        val avgWin = if (winDurations.isNotEmpty()) winDurations.average().toLong() else null
        val avgLoss = if (lossDurations.isNotEmpty()) lossDurations.average().toLong() else null

        val buckets = TradeDurationBucket.entries.map { b ->
            val group = withDuration.filter { it.durationBucket == b }
            val w = group.count { it.trade.resultEnum == TradeResult.WIN }
            val l = group.count { it.trade.resultEnum == TradeResult.LOSS }
            val wr = if (group.isNotEmpty()) (w.toDouble() / group.size) * 100.0 else 0.0
            val avgDur = if (group.isNotEmpty()) group.mapNotNull { it.holdingDurationMillis }.average().toLong() else 0L
            DurationBucketPerformance(
                bucket = b,
                tradeCount = group.size,
                winCount = w,
                lossCount = l,
                netPnl = group.sumOf { it.convertedPnl },
                winRatePercent = wr,
                avgDurationMillis = avgDur
            )
        }

        val shortBucket = buckets.first { it.bucket == TradeDurationBucket.SHORT }
        val longOrMed = withDuration.filter {
            it.durationBucket == TradeDurationBucket.MEDIUM || it.durationBucket == TradeDurationBucket.LONG
        }
        val (longerImproves, conclusion) = when {
            withDuration.size < 2 -> null to "Need at least 2 trades with entry & exit times to compare holding duration."
            avgWin != null && avgLoss != null && avgLoss > avgWin * 1.2 -> false to
                "Losing trades are held longer on average (${TradeDateTimeHelper.formatDuration(avgLoss)}) than winning trades (${TradeDateTimeHelper.formatDuration(avgWin)}), reducing profitability."
            shortBucket.tradeCount >= 2 && longOrMed.size >= 2 -> {
                val shortAvg = shortBucket.netPnl / shortBucket.tradeCount
                val longerAvg = longOrMed.sumOf { it.convertedPnl } / longOrMed.size
                if (longerAvg > shortAvg) {
                    true to "Longer holds (15m+) average higher P&L per trade than quick scalps (<15m) in your recorded trades."
                } else {
                    false to "Short-duration trades (<15m) outperform longer holds on average P&L per trade in your data."
                }
            }
            avgWin != null && avgLoss != null && avgWin >= avgLoss -> true to
                "Winning trades are held longer (${TradeDateTimeHelper.formatDuration(avgWin)}) than losing trades (${TradeDateTimeHelper.formatDuration(avgLoss)}), showing healthy letting-winners-run discipline."
            else -> null to "Track more completed entry/exit timestamps across short and long durations for a clearer trend."
        }

        return DurationAnalysis(
            tradesWithDurationCount = withDuration.size,
            tradesMissingDurationCount = missingCount,
            avgOverallHoldMillis = avgOverall,
            avgWinningHoldMillis = avgWin,
            avgLosingHoldMillis = avgLoss,
            bucketPerformance = buckets,
            longerHoldsImproveProfitability = longerImproves,
            durationTrendConclusion = conclusion
        )
    }

    private fun computeFrequencyAnalysis(
        filtered: List<EnrichedTrade>,
        maxTradesPerDayThreshold: Int
    ): FrequencyOvertradingStats {
        if (filtered.isEmpty()) return FrequencyOvertradingStats()

        val byDay = filtered.groupBy { it.effectiveDateIso }
        val activeDays = byDay.size
        val avgPerDay = filtered.size.toDouble() / activeDays
        val maxDayEntry = byDay.maxByOrNull { it.value.size }
        val maxTradesDay = maxDayEntry?.value?.size ?: 0
        val maxTradesDate = maxDayEntry?.key

        val byDateHour = filtered
            .filter { it.entryHour != null }
            .groupBy { "${it.effectiveDateIso} ${String.format(Locale.US, "%02d:00", it.entryHour)}" }
        val peakHourEntry = byDateHour.maxByOrNull { it.value.size }

        val sameDayGaps = filtered
            .filter { it.timeSincePrevTradeMillis != null && it.sameDaySequenceNumber > 1 }
            .mapNotNull { it.timeSincePrevTradeMillis }
        val avgGap = if (sameDayGaps.isNotEmpty()) sameDayGaps.average().toLong() else null
        val minGap = sameDayGaps.minOrNull()

        val rapidThreshold = 15L * 60_000L
        val rapidReEntries = filtered.filter {
            val g = it.timeSincePrevTradeMillis
            g != null && it.sameDaySequenceNumber > 1 && g <= rapidThreshold
        }
        val rapidAfterLoss = rapidReEntries.filter { it.isAfterLossSameDay }

        val seq1to2 = filtered.filter { it.sameDaySequenceNumber in 1..2 }
        val seq3to4 = filtered.filter { it.sameDaySequenceNumber in 3..4 }
        val seq5Plus = filtered.filter { it.sameDaySequenceNumber >= 5 }

        fun makeSeqBucket(label: String, list: List<EnrichedTrade>): SequenceBucketPerformance {
            val w = list.count { it.trade.resultEnum == TradeResult.WIN }
            return SequenceBucketPerformance(
                label = label,
                tradeCount = list.size,
                winCount = w,
                netPnl = list.sumOf { it.convertedPnl },
                winRatePercent = if (list.isNotEmpty()) (w.toDouble() / list.size) * 100.0 else 0.0
            )
        }

        val sequenceBuckets = listOf(
            makeSeqBucket("Trades #1–2", seq1to2),
            makeSeqBucket("Trades #3–4", seq3to4),
            makeSeqBucket("Trades #5+", seq5Plus)
        )

        val exceededDays = byDay.count { it.value.size > maxTradesPerDayThreshold }
        val degradedLateTrades = (seq3to4 + seq5Plus).let { late ->
            late.size >= 2 && late.sumOf { it.convertedPnl } < 0.0 && seq1to2.isNotEmpty()
        }
        val overtradingDetected = exceededDays > 0 || rapidAfterLoss.size >= 2 || degradedLateTrades
        val summary = when {
            exceededDays > 0 && rapidAfterLoss.isNotEmpty() ->
                "$exceededDays day(s) exceeded your $maxTradesPerDayThreshold-trade daily limit and ${rapidAfterLoss.size} rapid re-entries (<15m) occurred after losses."
            exceededDays > 0 ->
                "Peak activity reached $maxTradesDay trades on ${maxTradesDate ?: "a single day"} ($exceededDays day(s) above limit of $maxTradesPerDayThreshold)."
            rapidAfterLoss.size >= 2 ->
                "${rapidAfterLoss.size} rapid re-entries (<15 min after a loss) detected — pause between consecutive trades."
            degradedLateTrades ->
                "Performance declines noticeably after 2 consecutive trades on the same day."
            else ->
                "Frequency is disciplined (avg ${String.format(Locale.US, "%.1f", avgPerDay)} trades/active day, max $maxTradesDay/day)."
        }

        return FrequencyOvertradingStats(
            activeTradingDays = activeDays,
            avgTradesPerDay = avgPerDay,
            maxTradesInSingleDay = maxTradesDay,
            maxTradesDateIso = maxTradesDate,
            peakTradesInSingleHour = peakHourEntry?.value?.size ?: 0,
            peakHourLabel = peakHourEntry?.key,
            avgGapBetweenSameDayTradesMillis = avgGap,
            minGapBetweenTradesMillis = minGap,
            rapidReEntryCount = rapidReEntries.size,
            rapidReEntryAfterLossCount = rapidAfterLoss.size,
            rapidReEntryAfterLossNetPnl = rapidAfterLoss.sumOf { it.convertedPnl },
            sequencePerformance = sequenceBuckets,
            overtradingDetected = overtradingDetected,
            overtradingSummary = summary
        )
    }

    private fun computeEntryExitAnalysis(
        filtered: List<EnrichedTrade>,
        hourlyStats: List<HourlyPerformance>,
        avgWinHoldMillis: Long?
    ): EntryExitAnalysis {
        if (filtered.isEmpty()) return EntryExitAnalysis()

        val bestHour = hourlyStats.maxByOrNull { it.netPnl }
        val worstHour = hourlyStats.minByOrNull { it.netPnl }?.takeIf { hourlyStats.size > 1 || it.netPnl < 0 }
        val avgPnl = filtered.sumOf { it.convertedPnl } / filtered.size

        val slHits = filtered.count {
            it.resolvedClosureType == ExitClosureType.AUTO_SL ||
                it.trade.exitReason.contains("SL", ignoreCase = true)
        }
        val tpHits = filtered.count {
            it.resolvedClosureType == ExitClosureType.AUTO_TP ||
                it.trade.exitReason.contains("TP", ignoreCase = true) ||
                it.trade.exitReason.contains("Target", ignoreCase = true)
        }
        val trailingCount = filtered.count { it.resolvedClosureType == ExitClosureType.TRAILING_SL }
        val manualCount = filtered.count { it.resolvedClosureType == ExitClosureType.MANUAL }
        val autoCount = filtered.count { it.resolvedClosureType.isAutomatic }
        val beCount = filtered.count { it.resolvedClosureType == ExitClosureType.BREAKEVEN }
        val unknownCount = filtered.count { it.resolvedClosureType == ExitClosureType.UNKNOWN }

        val earlyExits = filtered.filter {
            it.trade.mistakes.contains("Early Exit", ignoreCase = true) ||
                it.trade.exitReason.contains("Early Exit", ignoreCase = true)
        }

        val prolongedLosses = filtered.count { item ->
            val isLoss = item.trade.resultEnum == TradeResult.LOSS
            val movedSl = item.trade.mistakes.contains("Moved SL", ignoreCase = true)
            val heldTooLong = isLoss && avgWinHoldMillis != null && item.holdingDurationMillis != null &&
                item.holdingDurationMillis > (avgWinHoldMillis * 1.5).toLong()
            isLoss && (movedSl || heldTooLong)
        }

        return EntryExitAnalysis(
            bestEntryHour = bestHour,
            worstEntryHour = worstHour,
            avgPnlPerTrade = avgPnl,
            slHitCount = slHits,
            slHitPercent = (slHits.toDouble() / filtered.size) * 100.0,
            tpHitCount = tpHits,
            tpHitPercent = (tpHits.toDouble() / filtered.size) * 100.0,
            trailingSlCount = trailingCount,
            manualCloseCount = manualCount,
            autoCloseCount = autoCount,
            breakevenCloseCount = beCount,
            unknownCloseCount = unknownCount,
            earlyExitCount = earlyExits.size,
            earlyExitNetPnl = earlyExits.sumOf { it.convertedPnl },
            prolongedLossHoldCount = prolongedLosses
        )
    }

    fun generateSmartInsights(
        filtered: List<EnrichedTrade>,
        displayCurrency: TradingCurrency,
        timeOfDayStats: List<TimeOfDayPerformance>,
        weekdayStats: List<WeekdayPerformance>,
        sessionStats: List<SessionPerformance>,
        durationAnalysis: DurationAnalysis,
        frequencyStats: FrequencyOvertradingStats
    ): List<SmartPerformanceInsight> {
        if (filtered.isEmpty()) return emptyList()
        val insights = mutableListOf<SmartPerformanceInsight>()

        // 1. Time-of-Day best vs worst
        val activeTod = timeOfDayStats.filter { it.tradeCount >= 2 }
        val bestTod = activeTod.maxByOrNull { it.netPnl }
        val worstTod = activeTod.minByOrNull { it.netPnl }
        if (bestTod != null && bestTod.netPnl > 0) {
            insights.add(
                SmartPerformanceInsight(
                    id = "tod_best_${bestTod.interval.name}",
                    category = "Time of Day",
                    title = "Strongest Edge in ${bestTod.interval.label}",
                    description = "You perform better during ${bestTod.interval.label} (${bestTod.interval.rangeLabel}) with ${String.format(Locale.US, "%.0f%%", bestTod.winRatePercent)} win rate and ${TradingAnalytics.formatCurrency(bestTod.netPnl, displayCurrency)} across ${bestTod.tradeCount} trades.",
                    isPositive = true,
                    evidenceCount = bestTod.tradeCount
                )
            )
        }
        if (worstTod != null && worstTod.netPnl < 0 && worstTod.interval != bestTod?.interval) {
            insights.add(
                SmartPerformanceInsight(
                    id = "tod_worst_${worstTod.interval.name}",
                    category = "Time of Day",
                    title = "${worstTod.interval.label} Losses Need Review",
                    description = "${worstTod.interval.label} (${worstTod.interval.rangeLabel}) shows ${TradingAnalytics.formatCurrency(worstTod.netPnl, displayCurrency)} over ${worstTod.tradeCount} trades (${String.format(Locale.US, "%.0f%%", worstTod.winRatePercent)} WR).",
                    isPositive = false,
                    evidenceCount = worstTod.tradeCount
                )
            )
        }

        // 2. Session comparison
        val activeSessions = sessionStats.filter { it.sessionName != "Unassigned" && it.tradeCount >= 2 }
        if (activeSessions.size >= 2) {
            val bestS = activeSessions.maxByOrNull { it.netPnl }
            val worstS = activeSessions.minByOrNull { it.netPnl }
            if (bestS != null && worstS != null && bestS.sessionName != worstS.sessionName) {
                insights.add(
                    SmartPerformanceInsight(
                        id = "session_cmp",
                        category = "Trading Session",
                        title = "${bestS.sessionName} Leads Session P&L",
                        description = "${bestS.sessionName} generated ${TradingAnalytics.formatCurrency(bestS.netPnl, displayCurrency)} (${bestS.tradeCount} trades, ${String.format(Locale.US, "%.0f%%", bestS.winRatePercent)} WR), compared to ${worstS.sessionName} at ${TradingAnalytics.formatCurrency(worstS.netPnl, displayCurrency)} (${worstS.tradeCount} trades).",
                        isPositive = bestS.netPnl > 0,
                        evidenceCount = bestS.tradeCount + worstS.tradeCount
                    )
                )
            }
        }

        // 3. Consecutive trades / Overtrading degradation
        val earlySeq = frequencyStats.sequencePerformance.firstOrNull { it.label == "Trades #1–2" }
        val laterSeqs = frequencyStats.sequencePerformance.filter { it.label != "Trades #1–2" && it.tradeCount > 0 }
        val laterCount = laterSeqs.sumOf { it.tradeCount }
        val laterNet = laterSeqs.sumOf { it.netPnl }
        if (earlySeq != null && earlySeq.tradeCount >= 2 && laterCount >= 2 && laterNet < 0.0 &&
            (laterNet / laterCount) < (earlySeq.netPnl / earlySeq.tradeCount)
        ) {
            insights.add(
                SmartPerformanceInsight(
                    id = "consecutive_drop",
                    category = "Trade Frequency",
                    title = "Losses Increase After Multiple Consecutive Trades",
                    description = "Your first 1–2 trades/day average ${String.format(Locale.US, "%.0f%%", earlySeq.winRatePercent)} WR (${TradingAnalytics.formatCurrency(earlySeq.netPnl, displayCurrency)}), while trade #3+ drop to ${TradingAnalytics.formatCurrency(laterNet, displayCurrency)} across $laterCount trades.",
                    isPositive = false,
                    evidenceCount = earlySeq.tradeCount + laterCount
                )
            )
        }
        if (frequencyStats.rapidReEntryAfterLossCount >= 2) {
            insights.add(
                SmartPerformanceInsight(
                    id = "rapid_reentry_loss",
                    category = "Overtrading",
                    title = "Rapid Re-Entry After Losses",
                    description = "You took ${frequencyStats.rapidReEntryAfterLossCount} trade(s) within 15 minutes after a losing trade, yielding ${TradingAnalytics.formatCurrency(frequencyStats.rapidReEntryAfterLossNetPnl, displayCurrency)}.",
                    isPositive = frequencyStats.rapidReEntryAfterLossNetPnl >= 0,
                    evidenceCount = frequencyStats.rapidReEntryAfterLossCount
                )
            )
        }

        // 4. Timeframe comparison (e.g., M5 vs M1)
        val tfGroups = filtered.groupBy { it.trade.timeframeEnum }
            .filter { it.value.size >= 2 }
            .mapValues { (_, list) ->
                val w = list.count { it.trade.resultEnum == TradeResult.WIN }
                Triple(list.size, (w.toDouble() / list.size) * 100.0, list.sumOf { it.convertedPnl })
            }
        if (tfGroups.size >= 2) {
            val bestTf = tfGroups.maxByOrNull { it.value.third }!!
            val worstTf = tfGroups.minByOrNull { it.value.third }!!
            if (bestTf.key != worstTf.key && bestTf.value.third > worstTf.value.third) {
                insights.add(
                    SmartPerformanceInsight(
                        id = "tf_cmp_${bestTf.key.name}_${worstTf.key.name}",
                        category = "Timeframe",
                        title = "${bestTf.key.label} Outperforms ${worstTf.key.label}",
                        description = "${bestTf.key.label} earned ${TradingAnalytics.formatCurrency(bestTf.value.third, displayCurrency)} (${String.format(Locale.US, "%.0f%%", bestTf.value.second)} WR, ${bestTf.value.first} trades), while ${worstTf.key.label} produced ${TradingAnalytics.formatCurrency(worstTf.value.third, displayCurrency)} (${worstTf.value.first} trades).",
                        isPositive = bestTf.value.third > 0,
                        evidenceCount = bestTf.value.first + worstTf.value.first
                    )
                )
            }
        }

        // 5. Strategy comparison
        val stratGroups = filtered
            .groupBy { it.trade.strategy.trim().ifEmpty { "General" } }
            .filter { it.value.size >= 2 }
            .mapValues { (_, list) ->
                val w = list.count { it.trade.resultEnum == TradeResult.WIN }
                Triple(list.size, (w.toDouble() / list.size) * 100.0, list.sumOf { it.convertedPnl })
            }
        val profitStrat = stratGroups.filter { it.value.third > 0 }.maxByOrNull { it.value.third }
        val lossStrat = stratGroups.filter { it.value.third < 0 }.minByOrNull { it.value.third }
        if (profitStrat != null && lossStrat != null) {
            insights.add(
                SmartPerformanceInsight(
                    id = "strat_contrast",
                    category = "Strategy Edge",
                    title = "${profitStrat.key} Profitable vs ${lossStrat.key} Losing",
                    description = "${profitStrat.key} is profitable at ${TradingAnalytics.formatCurrency(profitStrat.value.third, displayCurrency)} (${profitStrat.value.first} trades), while ${lossStrat.key} produced ${TradingAnalytics.formatCurrency(lossStrat.value.third, displayCurrency)} (${lossStrat.value.first} trades).",
                    isPositive = false,
                    evidenceCount = profitStrat.value.first + lossStrat.value.first
                )
            )
        } else if (profitStrat != null) {
            insights.add(
                SmartPerformanceInsight(
                    id = "strat_best_${profitStrat.key}",
                    category = "Strategy Edge",
                    title = "${profitStrat.key} Is Your Top Strategy",
                    description = "${profitStrat.key} generated ${TradingAnalytics.formatCurrency(profitStrat.value.third, displayCurrency)} with ${String.format(Locale.US, "%.0f%%", profitStrat.value.second)} win rate across ${profitStrat.value.first} trades.",
                    isPositive = true,
                    evidenceCount = profitStrat.value.first
                )
            )
        } else if (lossStrat != null) {
            insights.add(
                SmartPerformanceInsight(
                    id = "strat_loss_${lossStrat.key}",
                    category = "Strategy Edge",
                    title = "${lossStrat.key} Produces Repeated Losses",
                    description = "${lossStrat.key} stands at ${TradingAnalytics.formatCurrency(lossStrat.value.third, displayCurrency)} across ${lossStrat.value.first} trades (${String.format(Locale.US, "%.0f%%", lossStrat.value.second)} WR).",
                    isPositive = false,
                    evidenceCount = lossStrat.value.first
                )
            )
        }

        // 6. Weak weekday detection
        val losingWeekdays = weekdayStats.filter { it.tradeCount >= 2 && it.netPnl < 0 }
        val worstWd = losingWeekdays.minByOrNull { it.netPnl }
        if (worstWd != null) {
            insights.add(
                SmartPerformanceInsight(
                    id = "weekday_weak_${worstWd.weekday.name}",
                    category = "Day of Week",
                    title = "Weak Performance on ${worstWd.weekday.label}",
                    description = "You perform poorly on ${worstWd.weekday.label}s: ${TradingAnalytics.formatCurrency(worstWd.netPnl, displayCurrency)} net across ${worstWd.tradeCount} trades (${String.format(Locale.US, "%.0f%%", worstWd.winRatePercent)} WR).",
                    isPositive = false,
                    evidenceCount = worstWd.tradeCount
                )
            )
        }

        // 7. Holding duration comparison (Win vs Loss hold time)
        val avgW = durationAnalysis.avgWinningHoldMillis
        val avgL = durationAnalysis.avgLosingHoldMillis
        if (avgW != null && avgL != null && durationAnalysis.tradesWithDurationCount >= 2) {
            if (avgL > avgW) {
                insights.add(
                    SmartPerformanceInsight(
                        id = "duration_loss_longer",
                        category = "Trade Duration",
                        title = "Average Losing Hold Exceeds Winning Hold",
                        description = "Your average losing trade duration (${TradeDateTimeHelper.formatDuration(avgL)}) is higher than your average winning trade duration (${TradeDateTimeHelper.formatDuration(avgW)}). Consider cutting losing setups earlier.",
                        isPositive = false,
                        evidenceCount = durationAnalysis.tradesWithDurationCount
                    )
                )
            } else {
                insights.add(
                    SmartPerformanceInsight(
                        id = "duration_win_longer",
                        category = "Trade Duration",
                        title = "Strong Winner-to-Loser Hold Ratio",
                        description = "Your winning trades are held ${TradeDateTimeHelper.formatDuration(avgW)} on average while losses are cut in ${TradeDateTimeHelper.formatDuration(avgL)}.",
                        isPositive = true,
                        evidenceCount = durationAnalysis.tradesWithDurationCount
                    )
                )
            }
        }

        return insights
    }

    fun buildCalendarMonth(
        enrichedTrades: List<EnrichedTrade>,
        year: Int,
        monthZeroBased: Int,
        timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID
    ): CalendarMonthData {
        val cal = Calendar.getInstance(TradeDateTimeHelper.resolveTimeZone(timezoneId)).apply {
            clear()
            set(year, monthZeroBased.coerceIn(0, 11), 1, 12, 0, 0)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2..Sat=7
        val mondayOffset = (firstDayOfWeek + 5) % 7 // Mon->0, Tue->1, ..., Sun->6

        val titleFmt = SimpleDateFormat("MMMM yyyy", Locale.US).apply {
            timeZone = TradeDateTimeHelper.resolveTimeZone(timezoneId)
        }
        val monthTitle = titleFmt.format(cal.time)
        val monthPrefix = String.format(Locale.US, "%04d-%02d", year, monthZeroBased + 1)
        val monthTradesByDate = enrichedTrades
            .filter { it.effectiveDateIso.startsWith(monthPrefix) }
            .groupBy { it.effectiveDateIso }

        val days = (1..daysInMonth).map { day ->
            val dateIso = String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, day)
            val list = monthTradesByDate[dateIso].orEmpty()
            val net = list.sumOf { it.convertedPnl }
            val w = list.count { it.trade.resultEnum == TradeResult.WIN }
            val l = list.count { it.trade.resultEnum == TradeResult.LOSS }
            val be = list.count { it.trade.resultEnum == TradeResult.BREAK_EVEN }
            val status = when {
                list.isEmpty() -> CalendarDayStatus.NONE
                net > 0.0 -> CalendarDayStatus.PROFIT
                net < 0.0 -> CalendarDayStatus.LOSS
                else -> CalendarDayStatus.FLAT
            }
            CalendarDayCell(
                dayOfMonth = day,
                dateIso = dateIso,
                netPnl = net,
                tradeCount = list.size,
                winCount = w,
                lossCount = l,
                breakEvenCount = be,
                status = status,
                trades = list
            )
        }

        return CalendarMonthData(
            year = year,
            monthZeroBased = monthZeroBased,
            monthTitle = monthTitle,
            firstWeekdayOffsetMondayZero = mondayOffset,
            days = days,
            monthNetPnl = days.sumOf { it.netPnl },
            monthTotalTrades = days.sumOf { it.tradeCount },
            profitableDays = days.count { it.status == CalendarDayStatus.PROFIT },
            losingDays = days.count { it.status == CalendarDayStatus.LOSS }
        )
    }

    fun buildPeriodReport(
        trades: List<TradeEntity>,
        periodType: AnalysisPeriodType,
        anchorMillis: Long,
        displayCurrency: TradingCurrency,
        timezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
        customSessions: List<CustomTradingSession> = CustomTradingSession.DEFAULT_SESSIONS
    ): PeriodPerformanceReport {
        val tz = TradeDateTimeHelper.resolveTimeZone(timezoneId)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = anchorMillis }
        val (startIso, endIso, title) = when (periodType) {
            AnalysisPeriodType.DAILY -> {
                val iso = TradeDateTimeHelper.formatDateIso(cal.timeInMillis, timezoneId)
                Triple(iso, iso, "Daily Report • $iso")
            }
            AnalysisPeriodType.WEEKLY -> {
                val window = SmartDisciplineEngine.weekWindowForMillis(anchorMillis)
                Triple(window.startIso, window.endIso, "Weekly Report • ${window.weekKey}")
            }
            AnalysisPeriodType.MONTHLY -> {
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH)
                val (s, e) = TradeDateTimeHelper.monthRangeIso(y, m)
                val label = SimpleDateFormat("MMMM yyyy", Locale.US).apply { timeZone = tz }.format(cal.time)
                Triple(s, e, "Monthly Report • $label")
            }
        }

        val allEnriched = enrichTrades(trades, displayCurrency, timezoneId, customSessions)
        val periodEnriched = allEnriched.filter {
            it.effectiveDateIso >= startIso && it.effectiveDateIso <= endIso
        }
        val raw = periodEnriched.map { it.trade }
        val metrics = TradingAnalytics.calculateDashboardMetrics(raw, displayCurrency)

        val timeGroups = periodEnriched
            .filter { it.entryHour != null }
            .groupBy { String.format(Locale.US, "%02d:00–%02d:59", it.entryHour, it.entryHour) }
            .mapValues { (_, list) -> list.sumOf { it.convertedPnl } }
        val bestTime = timeGroups.maxByOrNull { it.value }?.let {
            "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})"
        } ?: "No timed trades"
        val worstTime = timeGroups.minByOrNull { it.value }?.takeIf { timeGroups.size > 1 || it.value < 0 }?.let {
            "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})"
        } ?: "N/A"

        val instGroups = periodEnriched
            .groupBy { it.trade.instrument.trim().uppercase(Locale.US).ifEmpty { "OTHER" } }
            .mapValues { (_, list) -> list.sumOf { it.convertedPnl } }
        val bestInst = instGroups.maxByOrNull { it.value }?.let {
            "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})"
        } ?: "N/A"
        val worstInst = instGroups.minByOrNull { it.value }?.takeIf { instGroups.size > 1 || it.value < 0 }?.let {
            "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})"
        } ?: "N/A"

        val stratGroups = periodEnriched
            .groupBy { it.trade.strategy.trim().ifEmpty { "General" } }
            .mapValues { (_, list) -> list.sumOf { it.convertedPnl } }
        val bestStrat = stratGroups.maxByOrNull { it.value }?.let {
            "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})"
        } ?: "N/A"
        val worstStrat = stratGroups.minByOrNull { it.value }?.takeIf { stratGroups.size > 1 || it.value < 0 }?.let {
            "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})"
        } ?: "N/A"

        val tfGroups = periodEnriched
            .groupBy { it.trade.timeframeEnum.label }
            .mapValues { (_, list) -> list.sumOf { it.convertedPnl } }
        val bestTf = tfGroups.maxByOrNull { it.value }?.let {
            "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})"
        } ?: "N/A"
        val worstTf = tfGroups.minByOrNull { it.value }?.takeIf { tfGroups.size > 1 || it.value < 0 }?.let {
            "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})"
        } ?: "N/A"

        return PeriodPerformanceReport(
            periodType = periodType,
            periodTitle = title,
            dateRangeIso = "$startIso to $endIso",
            displayCurrency = displayCurrency,
            totalTrades = metrics.totalTrades,
            winningTrades = metrics.winningTrades,
            losingTrades = metrics.losingTrades,
            breakEvenTrades = metrics.breakEvenTrades,
            winRatePercent = metrics.winRatePercent,
            grossProfit = metrics.totalProfit,
            grossLoss = metrics.totalLoss,
            netPnl = metrics.netPnl,
            averageWin = metrics.averageWin,
            averageLoss = metrics.averageLoss,
            bestTradingTime = bestTime,
            worstTradingTime = worstTime,
            bestInstrument = bestInst,
            worstInstrument = worstInst,
            bestStrategy = bestStrat,
            worstStrategy = worstStrat,
            bestTimeframe = bestTf,
            worstTimeframe = worstTf,
            dashboardMetrics = metrics,
            trades = periodEnriched.sortedByDescending { it.trade.effectiveEntryMillis }
        )
    }
}
