package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UserEntity
import com.example.domain.DashboardMetrics
import com.example.domain.DisciplineMetrics
import com.example.domain.TradingAnalytics
import com.example.ui.components.BrandLogoBadge
import com.example.ui.components.CategoryBreakdownChart
import com.example.ui.components.DailyPnlBarChart
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.EquityCurveChart
import com.example.ui.components.GradientButton
import com.example.ui.components.GradientKpiCard
import com.example.ui.components.MonthlyPerformanceChart
import com.example.ui.components.RoleBadge
import com.example.ui.components.WinLossDonutChart
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@Composable
fun DashboardScreen(
    user: UserEntity,
    metrics: DashboardMetrics,
    discipline: DisciplineMetrics,
    onAddTradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current

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
                // Hero Banner
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(ext.heroStart, ext.heroMid, ext.heroEnd)
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    BrandLogoBadge(size = 52.dp)
                                    Column {
                                        Text(
                                            text = "Welcome back, ${user.name}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RoleBadge(user.roleEnum)
                                            Text(
                                                text = "Trading with Bangali Babu",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ext.gold
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "NET PROFIT / LOSS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.72f)
                                    )
                                    val netColor = if (metrics.netPnl >= 0) ext.profit else ext.loss
                                    Text(
                                        text = TradingAnalytics.formatCurrency(metrics.netPnl),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = netColor
                                    )
                                }
                                GradientButton(
                                    text = "Log Trade",
                                    icon = Icons.Rounded.Add,
                                    onClick = onAddTradeClick,
                                    colors = listOf(ext.gold, Color(0xFFD97706)),
                                    modifier = Modifier.testTag("dashboard_add_trade_button")
                                )
                            }
                        }
                    }
                }

                // Live Discipline Alert Banner
                if (discipline.isDailyLossLimitBreached || discipline.isOvertradingToday) {
                    Surface(
                        color = ext.lossContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = "Discipline Warning", tint = ext.loss)
                            Column {
                                Text(
                                    text = if (discipline.isDailyLossLimitBreached) "Daily Max Loss Limit Breached!" else "Overtrading Alert Today!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ext.loss
                                )
                                Text(
                                    text = "Today: ${discipline.todayTradeCount}/${user.maxTradesPerDay} trades • Net ${TradingAnalytics.formatCurrency(discipline.todayNetPnl)} (Limit -${TradingAnalytics.formatPlainCurrency(user.dailyMaxLossLimit)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (metrics.totalTrades == 0) {
                    EmptyTradingState(
                        title = "No Trades Logged Yet",
                        subtitle = "All 16 performance metrics and 6 live analytics charts compute automatically from your saved trades.",
                        actionLabel = "Log Your First Trade",
                        onAction = onAddTradeClick
                    )
                }

                // Primary Colorful Gradient KPI Cards (2-column pairs)
                val baseSurface = ext.cardSurface
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientKpiCard(
                        title = "Total Trades",
                        value = metrics.totalTrades.toString(),
                        subtitle = "${metrics.winningTrades}W • ${metrics.losingTrades}L • ${metrics.breakEvenTrades}BE",
                        icon = Icons.Rounded.CandlestickChart,
                        gradientColors = listOf(baseSurface, ext.electricBlue.copy(alpha = if (ext.isDark) 0.18f else 0.10f)),
                        accentColor = ext.electricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Win / Loss Rate",
                        value = String.format(Locale.US, "%.1f%%", metrics.winRatePercent),
                        subtitle = "Loss Rate: ${String.format(Locale.US, "%.1f%%", metrics.lossRatePercent)}",
                        icon = Icons.Rounded.Percent,
                        gradientColors = listOf(baseSurface, ext.purple.copy(alpha = if (ext.isDark) 0.20f else 0.10f)),
                        accentColor = ext.purple,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientKpiCard(
                        title = "Total Profit",
                        value = TradingAnalytics.formatPlainCurrency(metrics.totalProfit),
                        subtitle = "Avg Win: ${TradingAnalytics.formatPlainCurrency(metrics.averageWin)}",
                        icon = Icons.Rounded.TrendingUp,
                        gradientColors = listOf(baseSurface, ext.profit.copy(alpha = if (ext.isDark) 0.20f else 0.12f)),
                        accentColor = ext.profit,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Total Loss",
                        value = TradingAnalytics.formatPlainCurrency(metrics.totalLoss),
                        subtitle = "Avg Loss: ${TradingAnalytics.formatPlainCurrency(metrics.averageLoss)}",
                        icon = Icons.Rounded.TrendingDown,
                        gradientColors = listOf(baseSurface, ext.loss.copy(alpha = if (ext.isDark) 0.20f else 0.12f)),
                        accentColor = ext.loss,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientKpiCard(
                        title = "Profit Factor",
                        value = if (metrics.profitFactor >= 99.0 && metrics.totalLoss == 0.0 && metrics.totalProfit > 0.0) "∞"
                        else String.format(Locale.US, "%.2f", metrics.profitFactor),
                        subtitle = "Avg R:R ${String.format(Locale.US, "%.2f", metrics.averageRiskReward)}R",
                        icon = Icons.Rounded.AccountBalanceWallet,
                        gradientColors = listOf(baseSurface, ext.teal.copy(alpha = if (ext.isDark) 0.20f else 0.10f)),
                        accentColor = ext.teal,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Current Streak",
                        value = metrics.currentStreak.label,
                        subtitle = "SL Discipline ${String.format(Locale.US, "%.0f%%", discipline.stopLossDisciplinePercent)}",
                        icon = Icons.Rounded.LocalFireDepartment,
                        gradientColors = listOf(baseSurface, ext.gold.copy(alpha = if (ext.isDark) 0.22f else 0.12f)),
                        accentColor = ext.gold,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Best & Worst Trading Day Cards
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientKpiCard(
                        title = "Best Trading Day",
                        value = metrics.bestTradingDay?.let { TradingAnalytics.formatCurrency(it.netPnl) } ?: "$0.00",
                        subtitle = metrics.bestTradingDay?.dateIso ?: "No days yet",
                        icon = Icons.Rounded.EmojiEvents,
                        gradientColors = listOf(baseSurface, ext.profit.copy(alpha = 0.14f)),
                        accentColor = ext.profit,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Worst Trading Day",
                        value = metrics.worstTradingDay?.let { TradingAnalytics.formatCurrency(it.netPnl) } ?: "$0.00",
                        subtitle = metrics.worstTradingDay?.dateIso ?: "No days yet",
                        icon = Icons.Rounded.Bolt,
                        gradientColors = listOf(baseSurface, ext.loss.copy(alpha = 0.14f)),
                        accentColor = ext.loss,
                        modifier = Modifier.weight(1f)
                    )
                }

                // All 6 Interactive Charts
                EquityCurveChart(curve = metrics.equityCurve)
                DailyPnlBarChart(series = metrics.dailyPnlSeries)
                WinLossDonutChart(
                    wins = metrics.winningTrades,
                    losses = metrics.losingTrades,
                    breakEvens = metrics.breakEvenTrades,
                    winRatePercent = metrics.winRatePercent
                )
                MonthlyPerformanceChart(series = metrics.monthlySeries)
                CategoryBreakdownChart(
                    title = "Performance by Instrument",
                    subtitle = "Net P&L and win rate per trading symbol",
                    accentColor = ext.electricBlue,
                    items = metrics.instrumentPerformance
                )
                CategoryBreakdownChart(
                    title = "Performance by Timeframe",
                    subtitle = "Net P&L breakdown across M1 to D1",
                    accentColor = ext.purple,
                    items = metrics.timeframePerformance
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
