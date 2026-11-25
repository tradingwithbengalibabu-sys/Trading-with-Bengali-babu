package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TradingCurrency
import com.example.domain.AdvancedAnalysisResult
import com.example.domain.CalendarDayCell
import com.example.domain.CalendarDayStatus
import com.example.domain.CalendarMonthData
import com.example.domain.DurationAnalysis
import com.example.domain.EnrichedTrade
import com.example.domain.EntryExitAnalysis
import com.example.domain.FrequencyOvertradingStats
import com.example.domain.HourlyPerformance
import com.example.domain.SessionPerformance
import com.example.domain.SmartPerformanceInsight
import com.example.domain.TimeOfDayPerformance
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.domain.WeekdayPerformance
import com.example.ui.theme.LocalTradingColors
import java.util.Locale
import kotlin.math.abs

@Composable
fun SmartPerformanceInsightsCard(
    insights: List<SmartPerformanceInsight>,
    exactTimestampCount: Int,
    legacyTimestampCount: Int,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("smart_insights_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ext.gold, modifier = Modifier.size(20.dp))
                    Text("Smart Performance Insights", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Text(
                    text = "${insights.size} Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = ext.gold
                )
            }

            if (legacyTimestampCount > 0) {
                Text(
                    text = "$exactTimestampCount trade(s) have exact entry timestamps; $legacyTimestampCount older trade(s) without clock time are excluded from time-of-day/duration metrics.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (insights.isEmpty()) {
                Surface(
                    color = ext.cardElevated.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = ext.electricBlue)
                        Text(
                            text = "Log at least 2 trades per time interval, strategy, or timeframe to unlock data-verified performance insights. Zero patterns are invented from sparse data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                insights.forEach { insight ->
                    val accent = if (insight.isPositive) ext.profit else ext.loss
                    Surface(
                        color = accent.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${insight.category.uppercase(Locale.US)} • ${insight.title}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = accent,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${insight.evidenceCount} trades",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = insight.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeOfDayPerformanceCard(
    analysis: AdvancedAnalysisResult,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val dc = analysis.displayCurrency
    Card(
        modifier = modifier.fillMaxWidth().testTag("time_of_day_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = ext.electricBlue, modifier = Modifier.size(20.dp))
                Text("Time-of-Day Performance (${dc.code})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightPill(
                    label = "Most Profitable Time",
                    value = analysis.mostProfitableTimeOfDay?.let {
                        "${it.interval.label}: ${TradingAnalytics.formatCurrency(it.netPnl, dc)}"
                    } ?: "No timed profit yet",
                    accent = ext.profit,
                    modifier = Modifier.weight(1f)
                )
                HighlightPill(
                    label = "Most Loss-Making Time",
                    value = analysis.mostLossMakingTimeOfDay?.let {
                        "${it.interval.label}: ${TradingAnalytics.formatCurrency(it.netPnl, dc)}"
                    } ?: "No timed loss",
                    accent = ext.loss,
                    modifier = Modifier.weight(1f)
                )
            }

            analysis.timeOfDayStats.forEach { stat ->
                TimeOfDayRow(stat = stat, displayCurrency = dc)
            }
        }
    }
}

@Composable
private fun TimeOfDayRow(stat: TimeOfDayPerformance, displayCurrency: TradingCurrency) {
    val ext = LocalTradingColors.current
    val netColor = when {
        stat.tradeCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        stat.netPnl > 0 -> ext.profit
        stat.netPnl < 0 -> ext.loss
        else -> ext.gold
    }
    Surface(
        color = ext.cardElevated.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${stat.interval.label} (${stat.interval.rangeLabel})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = if (stat.tradeCount == 0) "0 trades" else TradingAnalytics.formatCurrency(stat.netPnl, displayCurrency),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = netColor
                )
            }
            if (stat.tradeCount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${stat.tradeCount}T (${stat.winCount}W/${stat.lossCount}L) • WR ${String.format(Locale.US, "%.0f%%", stat.winRatePercent)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Avg +${TradingAnalytics.formatPlainCurrency(stat.avgProfit, displayCurrency)} / -${TradingAnalytics.formatPlainCurrency(stat.avgLoss, displayCurrency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyPnlChartCard(
    hourlyStats: List<HourlyPerformance>,
    displayCurrency: TradingCurrency,
    timezoneId: String,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Profit & Loss by Hour (${TradeDateTimeHelper.shortTimezoneTag(timezoneId)})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            if (hourlyStats.isEmpty()) {
                Text(
                    text = "Log trades with exact entry time to view hourly P&L distribution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxAbs = hourlyStats.maxOfOrNull { abs(it.netPnl) }?.coerceAtLeast(1.0) ?: 1.0
                val profitCol = ext.profit
                val lossCol = ext.loss
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    val midY = size.height / 2f
                    val count = hourlyStats.size.coerceAtLeast(1)
                    val slotW = size.width / count
                    val barW = (slotW * 0.62f).coerceAtLeast(6f)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.4f),
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.5f
                    )
                    hourlyStats.forEachIndexed { idx, item ->
                        val ratio = (abs(item.netPnl) / maxAbs).toFloat().coerceIn(0.05f, 1f)
                        val barH = ratio * (midY - 8f)
                        val left = idx * slotW + (slotW - barW) / 2f
                        val top = if (item.netPnl >= 0) midY - barH else midY
                        drawRoundRect(
                            color = if (item.netPnl >= 0) profitCol else lossCol,
                            topLeft = Offset(left, top),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
                hourlyStats.forEach { item ->
                    val col = if (item.netPnl >= 0) ext.profit else ext.loss
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${item.hourLabel} (${item.tradeCount}T • ${String.format(Locale.US, "%.0f%%", item.winRatePercent)} WR)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = TradingAnalytics.formatCurrency(item.netPnl, displayCurrency),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = col
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeekdayPerformanceCard(
    weekdayStats: List<WeekdayPerformance>,
    bestWeekday: WeekdayPerformance?,
    worstWeekday: WeekdayPerformance?,
    displayCurrency: TradingCurrency,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("weekday_analysis_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Day-Wise Performance (Mon–Sun)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightPill(
                    label = "Best Day",
                    value = bestWeekday?.let { "${it.weekday.label}: ${TradingAnalytics.formatCurrency(it.netPnl, displayCurrency)}" } ?: "N/A",
                    accent = ext.profit,
                    modifier = Modifier.weight(1f)
                )
                HighlightPill(
                    label = "Worst Day",
                    value = worstWeekday?.let { "${it.weekday.label}: ${TradingAnalytics.formatCurrency(it.netPnl, displayCurrency)}" } ?: "N/A",
                    accent = ext.loss,
                    modifier = Modifier.weight(1f)
                )
            }

            val maxAbs = weekdayStats.maxOfOrNull { abs(it.netPnl) }?.coerceAtLeast(1.0) ?: 1.0
            weekdayStats.forEach { day ->
                val color = when {
                    day.tradeCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    day.netPnl >= 0 -> ext.profit
                    else -> ext.loss
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${day.weekday.label} (${day.tradeCount}T • ${String.format(Locale.US, "%.0f%%", day.winRatePercent)} WR)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (day.tradeCount == 0) "—" else TradingAnalytics.formatCurrency(day.netPnl, displayCurrency),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = color
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (day.tradeCount == 0) 0f else (abs(day.netPnl) / maxAbs).toFloat().coerceIn(0.04f, 1f) },
                        color = color,
                        trackColor = ext.cardBorder.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun SessionPerformanceCard(
    sessionStats: List<SessionPerformance>,
    bestSession: SessionPerformance?,
    worstSession: SessionPerformance?,
    displayCurrency: TradingCurrency,
    onEditSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("session_analysis_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trading Session Analysis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                TextButton(onClick = onEditSessions, modifier = Modifier.testTag("customize_sessions_button")) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Custom Times")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightPill(
                    label = "Best Session",
                    value = bestSession?.let { "${it.sessionName}: ${TradingAnalytics.formatCurrency(it.netPnl, displayCurrency)}" } ?: "N/A",
                    accent = ext.profit,
                    modifier = Modifier.weight(1f)
                )
                HighlightPill(
                    label = "Worst Session",
                    value = worstSession?.let { "${it.sessionName}: ${TradingAnalytics.formatCurrency(it.netPnl, displayCurrency)}" } ?: "N/A",
                    accent = ext.loss,
                    modifier = Modifier.weight(1f)
                )
            }

            if (sessionStats.isEmpty()) {
                Text("No session-tagged trades yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sessionStats.forEach { s ->
                    val col = if (s.netPnl >= 0) ext.profit else ext.loss
                    Surface(
                        color = ext.cardElevated.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.sessionName, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = "${s.windowLabel} • ${s.tradeCount}T (${s.winCount}W/${s.lossCount}L) • ${String.format(Locale.US, "%.0f%%", s.winRatePercent)} WR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = TradingAnalytics.formatCurrency(s.netPnl, displayCurrency),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = col
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DurationAnalysisCard(
    duration: DurationAnalysis,
    displayCurrency: TradingCurrency,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("duration_analysis_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = ext.teal, modifier = Modifier.size(20.dp))
                Text("Detailed Trade Duration Analysis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightPill(
                    label = "Avg Winning Hold",
                    value = duration.avgWinningHoldMillis?.let { TradeDateTimeHelper.formatDuration(it) } ?: "No data",
                    accent = ext.profit,
                    modifier = Modifier.weight(1f)
                )
                HighlightPill(
                    label = "Avg Losing Hold",
                    value = duration.avgLosingHoldMillis?.let { TradeDateTimeHelper.formatDuration(it) } ?: "No data",
                    accent = ext.loss,
                    modifier = Modifier.weight(1f)
                )
            }

            duration.bucketPerformance.forEach { b ->
                val col = if (b.tradeCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else if (b.netPnl >= 0) ext.profit else ext.loss
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${b.bucket.label} • ${b.tradeCount}T (${String.format(Locale.US, "%.0f%%", b.winRatePercent)} WR)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (b.tradeCount == 0) "—" else TradingAnalytics.formatCurrency(b.netPnl, displayCurrency),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = col
                    )
                }
            }

            Surface(
                color = (if (duration.longerHoldsImproveProfitability == false) ext.loss else ext.teal).copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = duration.durationTrendConclusion,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun FrequencyOvertradingCard(
    freq: FrequencyOvertradingStats,
    displayCurrency: TradingCurrency,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("frequency_analysis_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, if (freq.overtradingDetected) ext.loss.copy(alpha = 0.6f) else ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (freq.overtradingDetected) Icons.Rounded.Warning else Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = if (freq.overtradingDetected) ext.loss else ext.electricBlue
                )
                Text("Trade Frequency & Overtrading", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightPill(
                    label = "Max Trades / Day",
                    value = "${freq.maxTradesInSingleDay} (${freq.maxTradesDateIso ?: "—"})",
                    accent = ext.gold,
                    modifier = Modifier.weight(1f)
                )
                HighlightPill(
                    label = "Peak Trades / Hour",
                    value = "${freq.peakTradesInSingleHour} (${freq.peakHourLabel ?: "—"})",
                    accent = ext.electricBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Avg ${String.format(Locale.US, "%.1f", freq.avgTradesPerDay)} trades/day across ${freq.activeTradingDays} active day(s) • Avg Gap: ${freq.avgGapBetweenSameDayTradesMillis?.let { TradeDateTimeHelper.formatDuration(it) } ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            freq.sequencePerformance.forEach { seq ->
                val col = if (seq.tradeCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else if (seq.netPnl >= 0) ext.profit else ext.loss
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${seq.label} (${seq.tradeCount}T • ${String.format(Locale.US, "%.0f%%", seq.winRatePercent)} WR)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (seq.tradeCount == 0) "—" else TradingAnalytics.formatCurrency(seq.netPnl, displayCurrency),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = col
                    )
                }
            }

            Surface(
                color = (if (freq.overtradingDetected) ext.loss else ext.profit).copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = freq.overtradingSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (freq.overtradingDetected) ext.loss else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun EntryExitAnalysisCard(
    ee: EntryExitAnalysis,
    totalTrades: Int,
    displayCurrency: TradingCurrency,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("entry_exit_analysis_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Entry & Exit Execution Analysis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HighlightPill(
                    label = "Best Entry Time",
                    value = ee.bestEntryHour?.let { "${it.hourLabel} (${TradingAnalytics.formatCurrency(it.netPnl, displayCurrency)})" } ?: "N/A",
                    accent = ext.profit,
                    modifier = Modifier.weight(1f)
                )
                HighlightPill(
                    label = "Worst Entry Time",
                    value = ee.worstEntryHour?.let { "${it.hourLabel} (${TradingAnalytics.formatCurrency(it.netPnl, displayCurrency)})" } ?: "N/A",
                    accent = ext.loss,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Avg P&L per entry: ${TradingAnalytics.formatCurrency(ee.avgPnlPerTrade, displayCurrency)} across $totalTrades trade(s)",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )
            HorizontalDivider(color = ext.cardBorder)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Stop Loss Hit Frequency", style = MaterialTheme.typography.bodySmall)
                Text("${ee.slHitCount} (${String.format(Locale.US, "%.1f%%", ee.slHitPercent)})", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = ext.loss)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Take Profit Hit Frequency", style = MaterialTheme.typography.bodySmall)
                Text("${ee.tpHitCount} (${String.format(Locale.US, "%.1f%%", ee.tpHitPercent)})", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = ext.profit)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Closure Mode: Manual vs Automatic", style = MaterialTheme.typography.bodySmall)
                Text("Manual ${ee.manualCloseCount} • Auto ${ee.autoCloseCount} (Trail ${ee.trailingSlCount}) • BE ${ee.breakevenCloseCount}", style = MaterialTheme.typography.labelSmall, color = ext.electricBlue)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Early Exits / Prolonged Loss Holds", style = MaterialTheme.typography.bodySmall)
                Text("Early ${ee.earlyExitCount} • Prolonged Losses ${ee.prolongedLossHoldCount}", style = MaterialTheme.typography.labelSmall, color = ext.gold)
            }
        }
    }
}

@Composable
fun TradeCalendarMonthCard(
    calendarMonth: CalendarMonthData,
    displayCurrency: TradingCurrency,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (CalendarDayCell) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("trade_calendar_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.testTag("calendar_prev_month")) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(calendarMonth.monthTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    val mColor = if (calendarMonth.monthNetPnl >= 0) ext.profit else ext.loss
                    Text(
                        text = "${TradingAnalytics.formatCurrency(calendarMonth.monthNetPnl, displayCurrency)} • ${calendarMonth.monthTotalTrades}T (${calendarMonth.profitableDays}G / ${calendarMonth.losingDays}R)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (calendarMonth.monthTotalTrades == 0) MaterialTheme.colorScheme.onSurfaceVariant else mColor
                    )
                }
                IconButton(onClick = onNextMonth, modifier = Modifier.testTag("calendar_next_month")) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val totalCells = calendarMonth.firstWeekdayOffsetMondayZero + calendarMonth.days.size
            val rowCount = (totalCells + 6) / 7
            for (r in 0 until rowCount) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val slot = r * 7 + c
                        val dayIndex = slot - calendarMonth.firstWeekdayOffsetMondayZero
                        if (dayIndex !in calendarMonth.days.indices) {
                            Spacer(modifier = Modifier.weight(1f).height(58.dp))
                        } else {
                            val cell = calendarMonth.days[dayIndex]
                            val bg = when (cell.status) {
                                CalendarDayStatus.PROFIT -> ext.profit.copy(alpha = 0.18f)
                                CalendarDayStatus.LOSS -> ext.loss.copy(alpha = 0.18f)
                                CalendarDayStatus.FLAT -> ext.gold.copy(alpha = 0.18f)
                                CalendarDayStatus.NONE -> ext.cardElevated.copy(alpha = 0.3f)
                            }
                            val borderCol = when (cell.status) {
                                CalendarDayStatus.PROFIT -> ext.profit.copy(alpha = 0.6f)
                                CalendarDayStatus.LOSS -> ext.loss.copy(alpha = 0.6f)
                                CalendarDayStatus.FLAT -> ext.gold.copy(alpha = 0.6f)
                                CalendarDayStatus.NONE -> ext.cardBorder.copy(alpha = 0.4f)
                            }
                            val textCol = when (cell.status) {
                                CalendarDayStatus.PROFIT -> ext.profit
                                CalendarDayStatus.LOSS -> ext.loss
                                CalendarDayStatus.FLAT -> ext.gold
                                CalendarDayStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                color = bg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, borderCol),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clickable(enabled = cell.tradeCount > 0) { onSelectDay(cell) }
                                    .testTag("calendar_day_${cell.dateIso}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(4.dp).fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = cell.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (cell.tradeCount > 0) {
                                        Text(
                                            text = TradingAnalytics.formatCompactCurrency(cell.netPnl, displayCurrency),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = textCol,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip
                                        )
                                        Text(
                                            text = "${cell.tradeCount}T",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayTradesDialog(
    dayCell: CalendarDayCell,
    displayCurrency: TradingCurrency,
    timezoneId: String,
    onSelectTrade: (EnrichedTrade) -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalTradingColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
            border = BorderStroke(1.dp, ext.cardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Trades on ${dayCell.dateIso}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = "${dayCell.tradeCount} trades (${dayCell.winCount}W / ${dayCell.lossCount}L) • Net ${TradingAnalytics.formatCurrency(dayCell.netPnl, displayCurrency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dayCell.netPnl >= 0) ext.profit else ext.loss
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = ext.cardBorder)
                dayCell.trades.forEach { item ->
                    val t = item.trade
                    val timeLabel = t.entryTimeMillis?.takeIf { it > 0L }?.let {
                        TradeDateTimeHelper.formatTime12h(it, timezoneId)
                    } ?: "Time not recorded"
                    val pnlCol = if (item.convertedPnl >= 0) ext.profit else ext.loss
                    Surface(
                        color = ext.cardElevated.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier.fillMaxWidth().clickable { onSelectTrade(item) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${t.instrument} • ${t.directionEnum.label} (${t.timeframeEnum.label})", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                Text("$timeLabel • ${item.resolvedSession} • ${item.holdingDurationMillis?.let { TradeDateTimeHelper.formatDuration(it) } ?: "No duration"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = TradingAnalytics.formatCurrency(item.convertedPnl, displayCurrency),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = pnlCol
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.11f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
