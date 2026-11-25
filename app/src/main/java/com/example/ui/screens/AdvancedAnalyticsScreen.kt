package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomTradingSession
import com.example.data.model.TradingCurrency
import com.example.domain.AdvancedAnalysisResult
import com.example.domain.AdvancedTradeFilter
import com.example.domain.CalendarDayCell
import com.example.domain.CalendarMonthData
import com.example.domain.EnrichedTrade
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.ui.components.AdvancedFilterPanelCard
import com.example.ui.components.CalendarDayTradesDialog
import com.example.ui.components.DisplayCurrencySelectorBar
import com.example.ui.components.DurationAnalysisCard
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.EntryExitAnalysisCard
import com.example.ui.components.FrequencyOvertradingCard
import com.example.ui.components.GradientKpiCard
import com.example.ui.components.HourlyPnlChartCard
import com.example.ui.components.ScreenshotLightboxDialog
import com.example.ui.components.SessionPerformanceCard
import com.example.ui.components.SmartPerformanceInsightsCard
import com.example.ui.components.TimeOfDayPerformanceCard
import com.example.ui.components.TimezoneAndSessionConfigDialog
import com.example.ui.components.TradeCalendarMonthCard
import com.example.ui.components.TradeDetailDialog
import com.example.ui.components.WeekdayPerformanceCard
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedAnalyticsScreen(
    traderTitle: String,
    analysis: AdvancedAnalysisResult,
    calendarMonth: CalendarMonthData,
    filter: AdvancedTradeFilter,
    displayCurrency: TradingCurrency,
    liveUsdToInrRate: Double,
    isRefreshingRate: Boolean,
    selectedTimezoneId: String,
    customSessions: List<CustomTradingSession>,
    initialTab: Int = 0,
    onSelectDisplayCurrency: (TradingCurrency) -> Unit,
    onRefreshRate: () -> Unit,
    onUpdateFilter: ((AdvancedTradeFilter) -> AdvancedTradeFilter) -> Unit,
    onClearFilter: () -> Unit,
    onSelectTimezone: (String) -> Unit,
    onSaveCustomSessions: (List<CustomTradingSession>) -> Unit,
    onResetDefaultSessions: () -> Unit,
    onShiftCalendarMonth: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val ext = LocalTradingColors.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 4)) }
    var showTzSessionDialog by remember { mutableStateOf(false) }
    var selectedCalendarDay by remember { mutableStateOf<CalendarDayCell?>(null) }
    var inspectedEnrichedTrade by remember { mutableStateOf<EnrichedTrade?>(null) }
    var previewScreenshot by remember { mutableStateOf<String?>(null) }

    val tabs = listOf(
        "Insights & Time",
        "Day & Session",
        "Duration & Freq",
        "Entry & Exit",
        "Trade Calendar"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Advanced Trade Analysis", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "$traderTitle • ${TradeDateTimeHelper.shortTimezoneTag(selectedTimezoneId)} ($selectedTimezoneId)",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.gold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("analytics_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTzSessionDialog = true },
                        modifier = Modifier.testTag("analytics_settings_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Timezone & Trading Sessions", tint = ext.gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ext.gold,
                edgePadding = 12.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        modifier = Modifier.testTag("analytics_tab_$index")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DisplayCurrencySelectorBar(
                            selectedCurrency = displayCurrency,
                            onSelectCurrency = onSelectDisplayCurrency,
                            liveUsdToInrRate = liveUsdToInrRate,
                            isRefreshingRate = isRefreshingRate,
                            onRefreshRate = onRefreshRate
                        )

                        AdvancedFilterPanelCard(
                            filter = filter,
                            availableInstruments = analysis.availableInstruments,
                            availableStrategies = analysis.availableStrategies,
                            availableSessions = analysis.availableSessions,
                            timezoneId = selectedTimezoneId,
                            matchingCount = analysis.enrichedTrades.size,
                            totalCount = analysis.totalSourceTrades,
                            onUpdateFilter = onUpdateFilter,
                            onClearFilter = onClearFilter
                        )

                        val m = analysis.dashboardMetrics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientKpiCard(
                                title = "Filtered Net P&L (${displayCurrency.code})",
                                value = TradingAnalytics.formatCurrency(m.netPnl, displayCurrency),
                                subtitle = "${m.totalTrades} trades • PF ${String.format(Locale.US, "%.2f", m.profitFactor)}",
                                icon = Icons.Rounded.AccountBalanceWallet,
                                gradientColors = listOf(
                                    ext.cardSurface,
                                    (if (m.netPnl >= 0) ext.profit else ext.loss).copy(alpha = 0.18f)
                                ),
                                accentColor = if (m.netPnl >= 0) ext.profit else ext.loss,
                                modifier = Modifier.weight(1f)
                            )
                            GradientKpiCard(
                                title = "Win Rate",
                                value = String.format(Locale.US, "%.1f%%", m.winRatePercent),
                                subtitle = "${m.winningTrades}W / ${m.losingTrades}L / ${m.breakEvenTrades}BE",
                                icon = Icons.Rounded.Percent,
                                gradientColors = listOf(ext.cardSurface, ext.purple.copy(alpha = 0.18f)),
                                accentColor = ext.purple,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (analysis.totalSourceTrades == 0) {
                            EmptyTradingState(
                                title = "No Recorded Trades Yet",
                                subtitle = "Log trades with entry & exit timestamps to unlock time-of-day, session, holding duration, overtrading, and calendar insights."
                            )
                        } else {
                            when (selectedTab) {
                                0 -> {
                                    SmartPerformanceInsightsCard(
                                        insights = analysis.smartInsights,
                                        exactTimestampCount = analysis.exactTimestampCount,
                                        legacyTimestampCount = analysis.legacyTimestampCount
                                    )
                                    TimeOfDayPerformanceCard(analysis = analysis)
                                    HourlyPnlChartCard(
                                        hourlyStats = analysis.hourlyStats,
                                        displayCurrency = displayCurrency,
                                        timezoneId = selectedTimezoneId
                                    )
                                }
                                1 -> {
                                    WeekdayPerformanceCard(
                                        weekdayStats = analysis.weekdayStats,
                                        bestWeekday = analysis.bestWeekday,
                                        worstWeekday = analysis.worstWeekday,
                                        displayCurrency = displayCurrency
                                    )
                                    SessionPerformanceCard(
                                        sessionStats = analysis.sessionStats,
                                        bestSession = analysis.bestSession,
                                        worstSession = analysis.worstSession,
                                        displayCurrency = displayCurrency,
                                        onEditSessions = { showTzSessionDialog = true }
                                    )
                                }
                                2 -> {
                                    DurationAnalysisCard(
                                        duration = analysis.durationAnalysis,
                                        displayCurrency = displayCurrency
                                    )
                                    FrequencyOvertradingCard(
                                        freq = analysis.frequencyStats,
                                        displayCurrency = displayCurrency
                                    )
                                }
                                3 -> {
                                    EntryExitAnalysisCard(
                                        ee = analysis.entryExitAnalysis,
                                        totalTrades = analysis.enrichedTrades.size,
                                        displayCurrency = displayCurrency
                                    )
                                    HourlyPnlChartCard(
                                        hourlyStats = analysis.hourlyStats,
                                        displayCurrency = displayCurrency,
                                        timezoneId = selectedTimezoneId
                                    )
                                }
                                4 -> {
                                    TradeCalendarMonthCard(
                                        calendarMonth = calendarMonth,
                                        displayCurrency = displayCurrency,
                                        onPreviousMonth = { onShiftCalendarMonth(-1) },
                                        onNextMonth = { onShiftCalendarMonth(1) },
                                        onSelectDay = { cell -> selectedCalendarDay = cell }
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    if (showTzSessionDialog) {
        TimezoneAndSessionConfigDialog(
            selectedTimezoneId = selectedTimezoneId,
            customSessions = customSessions,
            onSelectTimezone = onSelectTimezone,
            onSaveSessions = onSaveCustomSessions,
            onResetDefaultSessions = onResetDefaultSessions,
            onDismiss = { showTzSessionDialog = false }
        )
    }

    selectedCalendarDay?.let { cell ->
        CalendarDayTradesDialog(
            dayCell = cell,
            displayCurrency = displayCurrency,
            timezoneId = selectedTimezoneId,
            onSelectTrade = { enriched ->
                selectedCalendarDay = null
                inspectedEnrichedTrade = enriched
            },
            onDismiss = { selectedCalendarDay = null }
        )
    }

    inspectedEnrichedTrade?.let { enriched ->
        TradeDetailDialog(
            trade = enriched.trade,
            displayCurrency = displayCurrency,
            canEditOrDelete = false,
            timezoneId = selectedTimezoneId,
            timeSincePrevTradeMillis = enriched.timeSincePrevTradeMillis,
            onDismiss = { inspectedEnrichedTrade = null },
            onViewScreenshot = { data -> previewScreenshot = data }
        )
    }

    previewScreenshot?.let { data ->
        ScreenshotLightboxDialog(
            screenshotData = data,
            onDismiss = { previewScreenshot = null }
        )
    }
}
