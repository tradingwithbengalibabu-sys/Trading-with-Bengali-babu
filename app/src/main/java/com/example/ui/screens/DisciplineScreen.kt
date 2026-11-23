package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DisciplineReviewEntity
import com.example.data.model.EmotionState
import com.example.data.model.ReviewPeriod
import com.example.data.model.UserEntity
import com.example.domain.DisciplineMetrics
import com.example.domain.TradingAnalytics
import com.example.ui.components.GradientButton
import com.example.ui.components.GradientKpiCard
import com.example.ui.theme.LocalTradingColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DisciplineScreen(
    user: UserEntity,
    discipline: DisciplineMetrics,
    reviews: List<DisciplineReviewEntity>,
    onUpdateGoals: (dailyGoal: Double, dailyMaxLoss: Double, maxTradesPerDay: Int, maxRiskPerTrade: Double) -> Unit,
    onSaveReview: (
        periodType: ReviewPeriod,
        periodKey: String,
        emotionState: EmotionState,
        psychologyNotes: String,
        slAdherenceRating: Int,
        lessonsSummary: String,
        nextActionPlan: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current

    var dailyGoal by remember(user) { mutableStateOf(user.dailyProfitGoal.toString()) }
    var dailyMaxLoss by remember(user) { mutableStateOf(user.dailyMaxLossLimit.toString()) }
    var maxTrades by remember(user) { mutableStateOf(user.maxTradesPerDay.toString()) }
    var maxRisk by remember(user) { mutableStateOf(user.maxRiskPerTrade.toString()) }

    val nowCal = remember { Calendar.getInstance() }
    val defaultWeekKey = remember {
        "${nowCal.get(Calendar.YEAR)}-W${String.format(Locale.US, "%02d", nowCal.get(Calendar.WEEK_OF_YEAR))}"
    }
    val defaultMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
    val defaultDayKey = remember { TradingAnalytics.todayIso() }

    var reviewPeriod by remember { mutableStateOf(ReviewPeriod.WEEKLY) }
    var periodKey by remember(reviewPeriod) {
        mutableStateOf(
            when (reviewPeriod) {
                ReviewPeriod.DAILY -> defaultDayKey
                ReviewPeriod.WEEKLY -> defaultWeekKey
                ReviewPeriod.MONTHLY -> defaultMonthKey
            }
        )
    }
    var emotionState by remember { mutableStateOf(EmotionState.CALM) }
    var slRating by remember { mutableIntStateOf(4) }
    var psychologyNotes by remember { mutableStateOf("") }
    var lessonsSummary by remember { mutableStateOf("") }
    var nextActionPlan by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Discipline KPI Cards
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientKpiCard(
                        title = "Stop-Loss Discipline",
                        value = String.format(Locale.US, "%.0f%%", discipline.stopLossDisciplinePercent),
                        subtitle = "Risk Compliance: ${String.format(Locale.US, "%.0f%%", discipline.riskCompliancePercent)}",
                        icon = Icons.Rounded.Shield,
                        gradientColors = listOf(ext.cardSurface, ext.teal.copy(alpha = 0.18f)),
                        accentColor = ext.teal,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Today's Activity",
                        value = "${discipline.todayTradeCount} / ${user.maxTradesPerDay}",
                        subtitle = if (discipline.isOvertradingToday) "Overtrading Alert!" else "Within Trade Limit",
                        icon = if (discipline.isOvertradingToday) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                        gradientColors = listOf(
                            ext.cardSurface,
                            (if (discipline.isOvertradingToday) ext.loss else ext.profit).copy(alpha = 0.18f)
                        ),
                        accentColor = if (discipline.isOvertradingToday) ext.loss else ext.profit,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Today's Daily Goal vs Max Loss Limit Status
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Risk Management & Daily Limits", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Today's Net P&L: ${TradingAnalytics.formatCurrency(discipline.todayNetPnl)}  •  Avg Risk/Trade: ${TradingAnalytics.formatPlainCurrency(discipline.averageRiskAmount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (discipline.todayNetPnl >= 0) ext.profit else ext.loss
                        )
                        val goalProgress = if (user.dailyProfitGoal > 0) {
                            (discipline.todayNetPnl / user.dailyProfitGoal).toFloat().coerceIn(0f, 1f)
                        } else 0f
                        Text(
                            "Daily Goal Progress (${TradingAnalytics.formatPlainCurrency(user.dailyProfitGoal)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { goalProgress },
                            color = ext.profit,
                            trackColor = ext.cardElevated,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Text(
                            text = "Historical Breaches: ${discipline.overtradingDaysCount} overtrading days • ${discipline.dailyLossBreachDaysCount} max-loss breach days",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (discipline.overtradingDaysCount > 0 || discipline.dailyLossBreachDaysCount > 0) ext.gold else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(color = ext.cardBorder)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = dailyGoal,
                                onValueChange = { dailyGoal = it },
                                label = { Text("Daily Goal ($)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dailyMaxLoss,
                                onValueChange = { dailyMaxLoss = it },
                                label = { Text("Daily Max Loss ($)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = maxTrades,
                                onValueChange = { maxTrades = it },
                                label = { Text("Max Trades/Day") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxRisk,
                                onValueChange = { maxRisk = it },
                                label = { Text("Max Risk/Trade ($)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        GradientButton(
                            text = "Save Discipline Limits",
                            icon = Icons.Rounded.Save,
                            onClick = {
                                onUpdateGoals(
                                    dailyGoal.toDoubleOrNull() ?: user.dailyProfitGoal,
                                    dailyMaxLoss.toDoubleOrNull() ?: user.dailyMaxLossLimit,
                                    maxTrades.toIntOrNull() ?: user.maxTradesPerDay,
                                    maxRisk.toDoubleOrNull() ?: user.maxRiskPerTrade
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_discipline_limits_button")
                        )
                    }
                }

                // Mistake Tracking Breakdown
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mistake Tracking Breakdown", style = MaterialTheme.typography.titleMedium)
                        if (discipline.mistakeCounts.isEmpty()) {
                            Text(
                                "No recurring trading mistakes flagged yet. Great discipline!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ext.profit
                            )
                        } else {
                            discipline.mistakeCounts.forEach { (mistake, count) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mistake, style = MaterialTheme.typography.bodyMedium)
                                    Surface(
                                        color = ext.lossContainer,
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = "$count×",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ext.loss,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Trading Psychology & Weekly/Monthly Self-Review Form
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Psychology, contentDescription = null, tint = ext.purple)
                            Text("Psychology & Self-Review", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewPeriod.entries.forEach { period ->
                                FilterChip(
                                    selected = reviewPeriod == period,
                                    onClick = { reviewPeriod = period },
                                    label = { Text(period.label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = periodKey,
                            onValueChange = { periodKey = it },
                            label = { Text("Period Label (e.g., 2026-W47 or 2026-11)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Emotional State", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EmotionState.entries.take(3).forEach { em ->
                                FilterChip(
                                    selected = emotionState == em,
                                    onClick = { emotionState = em },
                                    label = { Text(em.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EmotionState.entries.drop(3).forEach { em ->
                                FilterChip(
                                    selected = emotionState == em,
                                    onClick = { emotionState = em },
                                    label = { Text(em.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text("Stop-Loss Adherence Self Rating: $slRating / 5", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = slRating.toFloat(),
                            onValueChange = { slRating = it.toInt().coerceIn(1, 5) },
                            valueRange = 1f..5f,
                            steps = 3
                        )

                        OutlinedTextField(
                            value = psychologyNotes,
                            onValueChange = { psychologyNotes = it },
                            label = { Text("Trading Psychology Notes") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = lessonsSummary,
                            onValueChange = { lessonsSummary = it },
                            label = { Text("Key Lessons & Mistakes Summary") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = nextActionPlan,
                            onValueChange = { nextActionPlan = it },
                            label = { Text("Next Period Action Plan") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        GradientButton(
                            text = "Save ${reviewPeriod.label} Review",
                            icon = Icons.Rounded.Save,
                            colors = listOf(ext.purple, ext.electricBlue),
                            onClick = {
                                onSaveReview(
                                    reviewPeriod,
                                    periodKey,
                                    emotionState,
                                    psychologyNotes,
                                    slRating,
                                    lessonsSummary,
                                    nextActionPlan
                                )
                                psychologyNotes = ""
                                lessonsSummary = ""
                                nextActionPlan = ""
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_review_button")
                        )
                    }
                }
            }
        }

        if (reviews.isNotEmpty()) {
            item {
                Text(
                    text = "Saved Reviews (${reviews.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()
                )
            }
            items(reviews, key = { it.id }) { rev ->
                Card(
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${rev.periodType.label} • ${rev.periodKey}", style = MaterialTheme.typography.titleSmall, color = ext.gold)
                            Text("SL Rating: ${rev.slAdherenceRating}/5 • ${rev.emotionState.label}", style = MaterialTheme.typography.labelSmall, color = ext.electricBlue)
                        }
                        if (rev.psychologyNotes.isNotBlank()) {
                            Text("Psychology: ${rev.psychologyNotes}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (rev.lessonsSummary.isNotBlank()) {
                            Text("Lessons: ${rev.lessonsSummary}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (rev.nextActionPlan.isNotBlank()) {
                            Text("Action Plan: ${rev.nextActionPlan}", style = MaterialTheme.typography.bodySmall, color = ext.profit)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
