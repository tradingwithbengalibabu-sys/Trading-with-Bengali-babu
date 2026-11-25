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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DisciplineReviewEntity
import com.example.data.model.SignalAccess
import com.example.data.model.SignalAccessRecord
import com.example.data.model.SmartDisciplineAlertEntity
import com.example.data.model.TradeEntity
import com.example.data.model.TradingCurrency
import com.example.data.model.WeeklyReportEntity
import com.example.domain.SmartDisciplineEngine
import com.example.domain.StudentPerformanceSummary
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.ui.components.ActiveBreakCooldownCard
import com.example.ui.components.CategoryBreakdownChart
import com.example.ui.components.DailyPnlBarChart
import com.example.ui.components.DisplayCurrencySelectorBar
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.EquityCurveChart
import com.example.ui.components.FlashingSmartAlertBanner
import com.example.ui.components.GradientKpiCard
import com.example.ui.components.ScreenshotLightboxDialog
import com.example.ui.components.SmartAlertHistoryItem
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.components.TradeDetailDialog
import com.example.ui.components.WeeklyPerformanceReportCard
import com.example.ui.components.WinLossDonutChart
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    summary: StudentPerformanceSummary,
    studentTrades: List<TradeEntity>,
    studentReviews: List<DisciplineReviewEntity>,
    studentSmartAlerts: List<SmartDisciplineAlertEntity> = emptyList(),
    studentWeeklyReports: List<WeeklyReportEntity> = emptyList(),
    displayCurrency: TradingCurrency,
    liveUsdToInrRate: Double,
    isRefreshingRate: Boolean,
    selectedTimezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
    unreadMessagesCount: Int = 0,
    signalAccessRecord: SignalAccessRecord? = null,
    onSelectDisplayCurrency: (TradingCurrency) -> Unit,
    onRefreshRate: () -> Unit,
    onMessageStudent: () -> Unit = {},
    onWarnStudent: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenStudentAnalytics: (Int) -> Unit = {},
    onSyncStudentDiscipline: () -> Unit = {},
    onToggleSignalAccess: (SignalAccess) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val ext = LocalTradingColors.current
    val st = summary.student
    val m = summary.metrics
    val d = summary.discipline

    androidx.compose.runtime.LaunchedEffect(st.id, studentTrades.size) {
        onSyncStudentDiscipline()
    }

    val liveWeekReport = remember(st, studentTrades, displayCurrency) {
        val window = SmartDisciplineEngine.weekWindowForMillis(System.currentTimeMillis())
        val weekTrades = studentTrades.filter { it.tradeDateMillis in window.startMillis..window.endMillis }
        if (weekTrades.isEmpty()) null
        else SmartDisciplineEngine.generateWeeklyReport(st, window, weekTrades, studentTrades, displayCurrency)
    }

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
                    DisplayCurrencySelectorBar(
                        selectedCurrency = displayCurrency,
                        onSelectCurrency = onSelectDisplayCurrency,
                        liveUsdToInrRate = liveUsdToInrRate,
                        isRefreshingRate = isRefreshingRate,
                        onRefreshRate = onRefreshRate
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onMessageStudent,
                            border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("student_detail_message_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadMessagesCount > 0) {
                                        Badge(containerColor = ext.loss, contentColor = Color.White) {
                                            Text(unreadMessagesCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Chat, contentDescription = null, tint = ext.gold, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Message Student")
                        }
                        OutlinedButton(
                            onClick = onWarnStudent,
                            border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.loss),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("student_detail_warn_button")
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Send Warning")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onOpenStudentAnalytics(0) },
                            border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("student_detail_time_analytics")
                        ) {
                            Icon(Icons.Rounded.AutoGraph, contentDescription = null, tint = ext.electricBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Time Insights")
                        }
                        OutlinedButton(
                            onClick = { onOpenStudentAnalytics(4) },
                            border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("student_detail_calendar")
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = ext.gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Trade Calendar")
                        }
                    }

                    // Student Personal Profile & Photo Card (Admin View & Edit)
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().testTag("student_detail_profile_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudentAvatar(
                                    photoUrl = st.profilePhotoUrl,
                                    name = st.name,
                                    size = 64.dp,
                                    borderColor = ext.electricBlue
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = st.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = st.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (st.profileCompleted) "Profile Completed" else "Profile Incomplete (${st.profileCompletionPercent}%)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (st.profileCompleted) ext.profit else ext.gold
                                    )
                                }
                                OutlinedButton(
                                    onClick = onEditProfile,
                                    border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp).testTag("student_detail_edit_profile_button")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit student profile", tint = ext.electricBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit Profile")
                                }
                            }

                            HorizontalDivider(color = ext.cardBorder)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StudentProfileFieldRow(
                                    icon = Icons.Rounded.Phone,
                                    label = "Mobile",
                                    value = st.mobileNumber.ifBlank { "Not provided" },
                                    modifier = Modifier.weight(1f)
                                )
                                StudentProfileFieldRow(
                                    icon = Icons.Rounded.Phone,
                                    label = "WhatsApp",
                                    value = st.whatsappNumber.ifBlank { "Not provided" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StudentProfileFieldRow(
                                    icon = Icons.Rounded.Cake,
                                    label = "Date of Birth",
                                    value = st.dateOfBirth.ifBlank { "Not provided" },
                                    modifier = Modifier.weight(1f)
                                )
                                StudentProfileFieldRow(
                                    icon = Icons.Rounded.Person,
                                    label = "Gender",
                                    value = st.gender.ifBlank { "Not provided" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            StudentProfileFieldRow(
                                icon = Icons.Rounded.Home,
                                label = "Full Address",
                                value = st.fullAddress.ifBlank { "Not provided" },
                                modifier = Modifier.fillMaxWidth()
                            )
                            StudentProfileFieldRow(
                                icon = Icons.Rounded.Badge,
                                label = "Firebase UID",
                                value = st.userId.ifBlank { st.id },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    val isSignalsBlocked = st.signalAccessEnum == SignalAccess.BLOCKED ||
                        signalAccessRecord?.signalAccessEnum == SignalAccess.BLOCKED
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(1.dp, if (isSignalsBlocked) ext.loss.copy(alpha = 0.5f) else ext.profit.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Admin Trade Signal Access",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isSignalsBlocked) {
                                        "BLOCKED — Signals & alerts paused; Journal & Account Login remain active."
                                    } else {
                                        "ALLOWED — Student receives targeted Admin Trade Signals."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSignalsBlocked) ext.loss else ext.profit
                                )
                            }
                            Switch(
                                checked = !isSignalsBlocked,
                                onCheckedChange = { allowed ->
                                    onToggleSignalAccess(if (allowed) SignalAccess.ALLOWED else SignalAccess.BLOCKED)
                                },
                                modifier = Modifier.testTag("student_detail_signal_access_switch")
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradientKpiCard(
                            title = "Net P&L (${displayCurrency.code})",
                            value = TradingAnalytics.formatCurrency(m.netPnl, displayCurrency),
                            subtitle = "Profit ${TradingAnalytics.formatPlainCurrency(m.totalProfit, displayCurrency)} / Loss ${TradingAnalytics.formatPlainCurrency(m.totalLoss, displayCurrency)}",
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

                    // Active Smart Discipline Warnings & Break Status for Student
                    studentSmartAlerts.filter { !it.acknowledged }.forEach { alert ->
                        FlashingSmartAlertBanner(
                            alert = alert,
                            onAcknowledge = null
                        )
                    }
                    ActiveBreakCooldownCard(untilMillis = st.breakRecommendedUntilMillis)

                    if (liveWeekReport != null || studentWeeklyReports.isNotEmpty()) {
                        Text(
                            text = "Student Weekly Mistake Analysis & Reports",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (liveWeekReport != null) {
                        WeeklyPerformanceReportCard(
                            report = liveWeekReport,
                            isLiveCurrentWeek = true,
                            expandedByDefault = true
                        )
                    }
                    studentWeeklyReports.forEach { rep ->
                        WeeklyPerformanceReportCard(
                            report = rep,
                            isLiveCurrentWeek = false,
                            expandedByDefault = false
                        )
                    }

                    if (studentSmartAlerts.any { it.acknowledged }) {
                        Text(
                            text = "Past Acknowledged Smart Alerts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        studentSmartAlerts.filter { it.acknowledged }.forEach { alert ->
                            SmartAlertHistoryItem(alert = alert)
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

                    EquityCurveChart(curve = m.equityCurve, displayCurrency = displayCurrency)
                    DailyPnlBarChart(series = m.dailyPnlSeries, displayCurrency = displayCurrency)
                    WinLossDonutChart(m.winningTrades, m.losingTrades, m.breakEvenTrades, m.winRatePercent)
                    CategoryBreakdownChart("Student Instrument Breakdown (${displayCurrency.code})", "Performance per symbol", ext.electricBlue, m.instrumentPerformance, displayCurrency = displayCurrency)

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
                        TradeCardItem(
                            trade = trade,
                            displayCurrency = displayCurrency,
                            timezoneId = selectedTimezoneId,
                            onClick = { inspectedTrade = trade }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    inspectedTrade?.let { trade ->
        TradeDetailDialog(
            trade = trade,
            displayCurrency = displayCurrency,
            canEditOrDelete = false,
            timezoneId = selectedTimezoneId,
            onDismiss = { inspectedTrade = null },
            onViewScreenshot = { data -> previewScreenshotPath = data }
        )
    }

    previewScreenshotPath?.let { data ->
        ScreenshotLightboxDialog(screenshotData = data, onDismiss = { previewScreenshotPath = null })
    }
}

@Composable
private fun StudentProfileFieldRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(15.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
