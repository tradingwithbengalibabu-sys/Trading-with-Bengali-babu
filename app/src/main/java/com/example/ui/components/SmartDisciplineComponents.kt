package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.SmartAlertType
import com.example.data.model.SmartDisciplineAlertEntity
import com.example.data.model.TradingCurrency
import com.example.data.model.WeeklyReportEntity
import com.example.domain.SmartDisciplineEngine
import com.example.domain.TradingAnalytics
import com.example.ui.theme.LocalTradingColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

private val EmergencyRed = Color(0xFFDC2626)
private val EmergencyOrange = Color(0xFFEA580C)

private fun formatIstDateTime(millis: Long): String {
    if (millis <= 0L) return "Recent"
    val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(SmartDisciplineEngine.IST_TIMEZONE_ID)
    }
    return "${fmt.format(Date(millis))} IST"
}

private fun alertBadgeLabel(alert: SmartDisciplineAlertEntity): String = when (alert.alertTypeEnum) {
    SmartAlertType.WEEKLY_LOSS_LIMIT -> "WEEKLY LOSS LIMIT (> $200 EQ.)"
    SmartAlertType.STOP_LOSS_STREAK -> "10 CONSECUTIVE STOP LOSSES"
    SmartAlertType.STRATEGY_LOSS_LIMIT -> "STRATEGY 3-SL WARNING"
}

private fun alertDetailsLine(alert: SmartDisciplineAlertEntity): String = when (alert.alertTypeEnum) {
    SmartAlertType.WEEKLY_LOSS_LIMIT -> {
        val usdText = TradingAnalytics.formatPlainCurrency(alert.lossAmountUsd, TradingCurrency.USD)
        val inrText = TradingAnalytics.formatPlainCurrency(alert.lossAmountInr, TradingCurrency.INR)
        val fx = String.format(Locale.US, "%.2f", alert.exchangeRateUsed.takeIf { it > 0.0 } ?: TradingAnalytics.DEFAULT_USD_TO_INR_RATE)
        "Weekly Net Loss: -$usdText (-$inrText @ ₹$fx/$)"
    }
    SmartAlertType.STOP_LOSS_STREAK -> {
        "Consecutive Stop Loss Count: ${alert.stopLossCount} / 10"
    }
    SmartAlertType.STRATEGY_LOSS_LIMIT -> {
        val strat = alert.strategyName.ifBlank { "General" }
        "Strategy: $strat • ${alert.stopLossCount} Consecutive Stop Losses"
    }
}

@Composable
fun FlashingSmartAlertBanner(
    alert: SmartDisciplineAlertEntity,
    onAcknowledge: ((breakDays: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val isWeeklyOrStreak = alert.alertTypeEnum == SmartAlertType.WEEKLY_LOSS_LIMIT ||
        alert.alertTypeEnum == SmartAlertType.STOP_LOSS_STREAK
    val accent = if (isWeeklyOrStreak) EmergencyRed else EmergencyOrange
    val secondaryAccent = if (isWeeklyOrStreak) EmergencyOrange else ext.gold
    val bgTint = if (isWeeklyOrStreak) ext.lossContainer else ext.goldContainer

    val infiniteTransition = rememberInfiniteTransition(label = "smart_alert_flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash_alpha"
    )

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bgTint),
        border = BorderStroke(2.dp, accent.copy(alpha = flashAlpha)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("flashing_smart_alert_${alert.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = flashAlpha * 0.14f),
                            secondaryAccent.copy(alpha = flashAlpha * 0.09f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.ReportProblem,
                    contentDescription = alert.title,
                    tint = accent,
                    modifier = Modifier.size(28.dp).alpha(0.50f + 0.50f * flashAlpha)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = accent.copy(alpha = flashAlpha.coerceAtLeast(0.75f)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = alertBadgeLabel(alert),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = accent
                    )
                }
            }

            // Metrics Highlight Pill Box
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.50f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = alertDetailsLine(alert),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                    Text(
                        text = "Triggered: ${formatIstDateTime(alert.effectiveTimestampMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (onAcknowledge != null) {
                Button(
                    onClick = { onAcknowledge(0) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ack_smart_alert_${alert.id}")
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "I Understand",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                val statusText = when {
                    alert.resolved -> "Condition resolved after trade update"
                    alert.acknowledged -> "Acknowledged by trader"
                    else -> "Active — awaiting trader acknowledgement"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
        }
    }
}

@Composable
fun EmergencyRiskWarningDialog(
    alert: SmartDisciplineAlertEntity,
    alertIndex: Int,
    totalAlerts: Int,
    onUnderstand: () -> Unit,
    onMinimize: () -> Unit
) {
    val ext = LocalTradingColors.current
    val accent = when (alert.alertTypeEnum) {
        SmartAlertType.WEEKLY_LOSS_LIMIT -> EmergencyRed
        SmartAlertType.STOP_LOSS_STREAK -> EmergencyRed
        SmartAlertType.STRATEGY_LOSS_LIMIT -> EmergencyOrange
    }

    val infiniteTransition = rememberInfiniteTransition(label = "emergency_dialog_flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emergency_dialog_alpha"
    )

    Dialog(
        onDismissRequest = onMinimize,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("emergency_risk_warning_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
            border = BorderStroke(2.5.dp, accent.copy(alpha = flashAlpha))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.22f * flashAlpha),
                                ext.cardSurface
                            )
                        )
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accent.copy(alpha = flashAlpha.coerceAtLeast(0.8f)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "EMERGENCY RISK WARNING",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                        }
                    }
                    if (totalAlerts > 1) {
                        Text(
                            text = "Alert $alertIndex of $totalAlerts",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EmergencyOrange
                        )
                    }
                }

                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = accent
                )

                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = alertBadgeLabel(alert),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = alertDetailsLine(alert),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                        Text(
                            text = "Triggered: ${formatIstDateTime(alert.effectiveTimestampMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = onUnderstand,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("emergency_dialog_understand")
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("I Understand", fontWeight = FontWeight.ExtraBold)
                }

                TextButton(
                    onClick = onMinimize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("emergency_dialog_minimize")
                ) {
                    Text(
                        text = "Keep Banner on Dashboard & Close Popup",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveBreakCooldownCard(
    untilMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    if (untilMillis <= nowMillis) return
    val ext = LocalTradingColors.current
    val remainingHours = ceil((untilMillis - nowMillis) / 3_600_000.0).toInt().coerceAtLeast(1)
    val days = remainingHours / 24
    val hours = remainingHours % 24
    val untilLabel = remember(untilMillis) {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(untilMillis))
    }

    Surface(
        color = ext.goldContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, ext.gold.copy(alpha = 0.75f)),
        modifier = modifier.fillMaxWidth().testTag("active_break_cooldown_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Bedtime, contentDescription = "Trading Break Active", tint = ext.gold)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mental Reset Break Recommended (Until $untilLabel)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = ext.gold
                )
                Text(
                    text = "Remaining cooldown: ${days}d ${hours}h. Step away from live orders, backtest calmly, and avoid revenge trading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun SmartAlertHistoryItem(
    alert: SmartDisciplineAlertEntity,
    onUnderstand: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    val dateLabel = remember(alert.effectiveTimestampMillis) {
        formatIstDateTime(alert.effectiveTimestampMillis)
    }
    val statusLabel = when {
        alert.resolved -> "Resolved"
        alert.acknowledged -> "Acknowledged"
        else -> "Active"
    }
    val statusBg = when {
        alert.resolved -> ext.electricBlue.copy(alpha = 0.16f)
        alert.acknowledged -> ext.profitContainer
        else -> ext.lossContainer
    }
    val statusColor = when {
        alert.resolved -> ext.electricBlue
        alert.acknowledged -> ext.profit
        else -> EmergencyRed
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, if (alert.isActiveUnacknowledged) EmergencyRed.copy(alpha = 0.7f) else ext.cardBorder),
        modifier = modifier.fillMaxWidth().testTag("smart_alert_history_${alert.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (alert.isActiveUnacknowledged) EmergencyRed else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = alertDetailsLine(alert),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ext.gold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (alert.isActiveUnacknowledged && onUnderstand != null) {
                Button(
                    onClick = onUnderstand,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("history_understand_${alert.id}")
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("I Understand", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WeeklyPerformanceReportCard(
    report: WeeklyReportEntity,
    isLiveCurrentWeek: Boolean = false,
    expandedByDefault: Boolean = false,
    onMarkRead: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    var expanded by remember(report.id, expandedByDefault) { mutableStateOf(expandedByDefault) }
    val scoreColor = when {
        report.disciplineScore >= 80 -> ext.profit
        report.disciplineScore >= 55 -> ext.gold
        else -> ext.loss
    }
    val pnlColor = if (report.netPnl >= 0) ext.profit else ext.loss

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(
            width = if (!report.readByUser && !isLiveCurrentWeek) 1.5.dp else 1.dp,
            color = if (!report.readByUser && !isLiveCurrentWeek) ext.gold else ext.cardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                if (expanded && !report.readByUser && !isLiveCurrentWeek) {
                    onMarkRead?.invoke()
                }
            }
            .testTag("weekly_report_card_${report.weekKey}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AutoGraph, contentDescription = null, tint = ext.gold)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Week ${report.weekKey}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (isLiveCurrentWeek) {
                                Surface(color = ext.electricBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(999.dp)) {
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ext.electricBlue,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (!report.readByUser) {
                                Surface(color = ext.goldContainer, shape = RoundedCornerShape(999.dp)) {
                                    Text(
                                        text = "NEW REPORT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ext.gold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${report.weekStartIso} to ${report.weekEndIso}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = scoreColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = scoreColor, modifier = Modifier.size(14.dp))
                            Text(
                                text = "${report.disciplineScore}/100",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = scoreColor
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse report" else "Expand report",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ReportMiniStat("Trades", "${report.totalTrades} (${report.winningTrades}W/${report.losingTrades}L)")
                ReportMiniStat("SL Hits", "${report.stopLossHits}", valueColor = if (report.stopLossHits >= 5) ext.loss else MaterialTheme.colorScheme.onSurface)
                ReportMiniStat("Win Rate", String.format(Locale.US, "%.0f%%", report.winRatePercent), valueColor = ext.electricBlue)
                ReportMiniStat("Net P&L", TradingAnalytics.formatCurrency(report.netPnl, report.currencyEnum), valueColor = pnlColor)
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = ext.cardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ReportMiniStat("Overtrade Days", report.overtradingDays.toString(), if (report.overtradingDays > 0) ext.loss else ext.profit)
                        ReportMiniStat("Revenge Trades", report.revengeTradingCount.toString(), if (report.revengeTradingCount > 0) ext.loss else ext.profit)
                        ReportMiniStat("No-SL Trades", report.noStopLossCount.toString(), if (report.noStopLossCount > 0) ext.loss else ext.profit)
                        ReportMiniStat("Rule Issues", report.ruleViolationsCount.toString(), if (report.ruleViolationsCount > 0) ext.gold else ext.profit)
                    }

                    if (report.repeatedMistakes.isNotEmpty()) {
                        ReportBulletSection(
                            title = "Repeated & Flagged Mistakes",
                            items = report.repeatedMistakes,
                            accent = ext.loss
                        )
                    }

                    if (report.poorStrategies.isNotEmpty()) {
                        ReportBulletSection(
                            title = "Underperforming Strategies",
                            items = report.poorStrategies,
                            accent = ext.gold
                        )
                    }

                    if (report.ruleViolationDetails.isNotEmpty()) {
                        ReportBulletSection(
                            title = "Trades Violating Rules",
                            items = report.ruleViolationDetails,
                            accent = ext.loss
                        )
                    }

                    if (report.improvementAreas.isNotEmpty()) {
                        ReportBulletSection(
                            title = "Areas Needing Improvement",
                            items = report.improvementAreas,
                            accent = ext.electricBlue
                        )
                    }

                    if (report.personalizedSuggestions.isNotEmpty()) {
                        Surface(
                            color = ext.goldContainer,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.TipsAndUpdates, contentDescription = null, tint = ext.gold, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Personalized Action Suggestions",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ext.gold
                                    )
                                }
                                report.personalizedSuggestions.forEach { tip ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = ext.gold, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                        Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!expanded) {
                Text(
                    text = "Tap to view mistakes, rule violations, strategy breakdown & suggestions",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.gold
                )
            }
        }
    }
}

@Composable
private fun ReportMiniStat(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}

@Composable
private fun ReportBulletSection(
    title: String,
    items: List<String>,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
        items.forEach { line ->
            Box(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
