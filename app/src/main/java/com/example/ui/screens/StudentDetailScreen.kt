package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DisciplineReviewEntity
import com.example.data.model.TradeEntity
import com.example.domain.StudentPerformanceSummary
import com.example.domain.TradingAnalytics
import com.example.ui.components.CategoryBreakdownChart
import com.example.ui.components.DailyPnlBarChart
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.EquityCurveChart
import com.example.ui.components.GradientKpiCard
import com.example.ui.components.ScreenshotLightboxDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.TradeDetailDialog
import com.example.ui.components.WinLossDonutChart
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    summary: StudentPerformanceSummary,
    studentTrades: List<TradeEntity>,
    studentReviews: List<DisciplineReviewEntity>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val ext = LocalTradingColors.current
    val st = summary.student
    val m = summary.metrics
    val d = summary.discipline

    var inspectedTrade by remember { mutableStateOf<TradeEntity?>(null) }
    var previewScreenshotPath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(st.name, style = MaterialTheme.typography.titleLarge)
                        Text(st.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("student_detail_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box(Modifier.padding(end = 12.dp)) { StatusBadge(st.statusEnum) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradientKpiCard(
                            title = "Net P&L",
                            value = TradingAnalytics.formatCurrency(m.netPnl),
                            subtitle = "Profit ${TradingAnalytics.formatPlainCurrency(m.totalProfit)} / Loss ${TradingAnalytics.formatPlainCurrency(m.totalLoss)}",
                            icon = Icons.Rounded.AccountBalanceWallet,
                            gradientColors = listOf(ext.cardSurface, (if (m.netPnl >= 0) ext.profit else ext.loss).copy(alpha = 0.18f)),
                            accentColor = if (m.netPnl >= 0) ext.profit else ext.loss,
                            modifier = Modifier.weight(1f)
                        )
                        GradientKpiCard(
                            title = "Win Rate",
                            value = String.format(Locale.US, "%.1f%%", m.winRatePercent),
                            subtitle = "${m.winningTrades}W • ${m.losingTrades}L • ${m.breakEvenTrades}BE",
                            icon = Icons.Rounded.Percent,
                            gradientColors = listOf(ext.cardSurface, ext.purple.copy(alpha = 0.18f)),
                            accentColor = ext.purple,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradientKpiCard(
                            title = "Total Trades",
                            value = m.totalTrades.toString(),
                            subtitle = "PF ${String.format(Locale.US, "%.2f", m.profitFactor)} • ${m.currentStreak.label}",
                            icon = Icons.Rounded.CandlestickChart,
                            gradientColors = listOf(ext.cardSurface, ext.electricBlue.copy(alpha = 0.18f)),
                            accentColor = ext.electricBlue,
                            modifier = Modifier.weight(1f)
                        )
                        GradientKpiCard(
                            title = "Discipline Score",
                            value = String.format(Locale.US, "%.0f%% SL", d.stopLossDisciplinePercent),
                            subtitle = "${d.overtradingDaysCount} overtrade days • ${d.dailyLossBreachDaysCount} loss breaches",
                            icon = Icons.Rounded.Shield,
                            gradientColors = listOf(ext.cardSurface, ext.teal.copy(alpha = 0.18f)),
                            accentColor = ext.teal,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (d.mistakeCounts.isNotEmpty()) {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                            border = BorderStroke(1.dp, ext.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Tracked Student Mistakes", style = MaterialTheme.typography.titleSmall, color = ext.gold)
                                Text(
                                    text = d.mistakeCounts.joinToString("  •  ") { "${it.first} (${it.second}×)" },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (studentReviews.isNotEmpty()) {
                        val latest = studentReviews.first()
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                            border = BorderStroke(1.dp, ext.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Latest Self-Review (${latest.periodTypeEnum.label} • ${latest.periodKey})", style = MaterialTheme.typography.titleSmall, color = ext.electricBlue)
                                Text("Emotion: ${latest.emotionStateEnum.label} • SL Rating: ${latest.slAdherenceRating}/5", style = MaterialTheme.typography.bodySmall)
                                if (latest.psychologyNotes.isNotBlank()) {
                                    Text(latest.psychologyNotes, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    EquityCurveChart(curve = m.equityCurve)
                    DailyPnlBarChart(series = m.dailyPnlSeries)
                    WinLossDonutChart(m.winningTrades, m.losingTrades, m.breakEvenTrades, m.winRatePercent)
                    CategoryBreakdownChart("Student Instrument Breakdown", "Performance per symbol", ext.electricBlue, m.instrumentPerformance)

                    Text(
                        text = "Complete Trade History (${studentTrades.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (studentTrades.isEmpty()) {
                item {
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        EmptyTradingState(
                            title = "No Student Trades Yet",
                            subtitle = "${st.name} hasn't logged any trades yet."
                        )
                    }
                }
            } else {
                items(studentTrades, key = { it.id }) { trade ->
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        TradeCardItem(trade = trade, onClick = { inspectedTrade = trade })
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    inspectedTrade?.let { trade ->
        TradeDetailDialog(
            trade = trade,
            canEditOrDelete = false,
            onDismiss = { inspectedTrade = null },
            onViewScreenshot = { data -> previewScreenshotPath = data }
        )
    }

    previewScreenshotPath?.let { data ->
        ScreenshotLightboxDialog(screenshotData = data, onDismiss = { previewScreenshotPath = null })
    }
}
