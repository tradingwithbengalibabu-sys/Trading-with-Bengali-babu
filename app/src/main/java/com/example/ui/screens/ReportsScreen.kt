package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomTradingSession
import com.example.data.model.AnalysisPeriodType
import com.example.data.model.ReportRangeType
import com.example.data.model.TradeEntity
import com.example.data.model.TradingCurrency
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.domain.AdvancedTradeAnalytics
import com.example.domain.PeriodPerformanceReport
import com.example.domain.TradeDateTimeHelper
import com.example.domain.TradingAnalytics
import com.example.export.ReportExporter
import com.example.ui.components.DisplayCurrencySelectorBar
import com.example.ui.components.GradientButton
import com.example.ui.components.GradientKpiCard
import com.example.ui.theme.LocalTradingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    currentUser: UserEntity,
    personalTrades: List<TradeEntity>,
    students: List<UserEntity>,
    allPlatformTrades: List<TradeEntity>,
    displayCurrency: TradingCurrency,
    liveUsdToInrRate: Double,
    isRefreshingRate: Boolean,
    selectedTimezoneId: String = TradeDateTimeHelper.DEFAULT_TIMEZONE_ID,
    customSessions: List<CustomTradingSession> = CustomTradingSession.DEFAULT_SESSIONS,
    onSelectDisplayCurrency: (TradingCurrency) -> Unit,
    onRefreshRate: () -> Unit,
    onExportBackupJson: suspend () -> Result<String>,
    onRestoreBackupJson: (String) -> Unit,
    onOpenDeepAnalytics: () -> Unit = {},
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalTradingColors.current
    val isoFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    // For Admin: target can be Own Journal (null), All Students Combined ("__ALL__"), or a specific Student UID
    // For Student: strictly locked to own trades!
    var selectedStudentId by remember { mutableStateOf<String?>(null) }
    var rangeType by remember { mutableStateOf(ReportRangeType.MONTHLY) }
    var customStartMillis by remember { mutableLongStateOf(System.currentTimeMillis() - 30L * 86_400_000L) }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickingStartDate by remember { mutableStateOf<Boolean?>(null) }

    val sourceTrades = remember(currentUser, personalTrades, allPlatformTrades, students, selectedStudentId) {
        if (currentUser.roleEnum != UserRole.ADMIN) {
            personalTrades
        } else {
            when (selectedStudentId) {
                null -> personalTrades
                "__ALL__" -> {
                    val studentIds = students.map { it.id }.toSet()
                    allPlatformTrades.filter { it.userId in studentIds }
                }
                else -> allPlatformTrades.filter { it.userId == selectedStudentId }
            }
        }
    }

    val traderLabel = remember(currentUser, students, selectedStudentId) {
        if (currentUser.roleEnum != UserRole.ADMIN || selectedStudentId == null) currentUser.name
        else if (selectedStudentId == "__ALL__") "All Registered Students"
        else students.firstOrNull { it.id == selectedStudentId }?.name ?: "Student"
    }

    val (startIso, endIso) = remember(rangeType, customStartMillis, customEndMillis) {
        val cal = Calendar.getInstance()
        val today = isoFmt.format(cal.time)
        when (rangeType) {
            ReportRangeType.DAILY -> today to today
            ReportRangeType.WEEKLY -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                isoFmt.format(cal.time) to today
            }
            ReportRangeType.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                isoFmt.format(cal.time) to today
            }
            ReportRangeType.CUSTOM -> {
                val s = minOf(customStartMillis, customEndMillis)
                val e = maxOf(customStartMillis, customEndMillis)
                isoFmt.format(Date(s)) to isoFmt.format(Date(e))
            }
        }
    }

    val filteredTrades = remember(sourceTrades, startIso, endIso) {
        sourceTrades.filter { it.tradeDateIso >= startIso && it.tradeDateIso <= endIso }
    }
    val metrics = remember(filteredTrades, displayCurrency) {
        TradingAnalytics.calculateDashboardMetrics(filteredTrades, displayCurrency)
    }

    val periodHighlights: PeriodPerformanceReport = remember(
        filteredTrades, rangeType, displayCurrency, selectedTimezoneId, customSessions
    ) {
        val periodType = when (rangeType) {
            ReportRangeType.DAILY -> AnalysisPeriodType.DAILY
            ReportRangeType.WEEKLY -> AnalysisPeriodType.WEEKLY
            ReportRangeType.MONTHLY, ReportRangeType.CUSTOM -> AnalysisPeriodType.MONTHLY
        }
        val base = AdvancedTradeAnalytics.buildPeriodReport(
            trades = sourceTrades,
            periodType = periodType,
            anchorMillis = System.currentTimeMillis(),
            displayCurrency = displayCurrency,
            timezoneId = selectedTimezoneId,
            customSessions = customSessions
        )
        if (rangeType == ReportRangeType.CUSTOM) {
            val customEnriched = AdvancedTradeAnalytics.enrichTrades(
                filteredTrades, displayCurrency, selectedTimezoneId, customSessions
            )
            val customMetrics = TradingAnalytics.calculateDashboardMetrics(filteredTrades, displayCurrency)
            val timeGroups = customEnriched.filter { it.entryHour != null }
                .groupBy { String.format(Locale.US, "%02d:00–%02d:59", it.entryHour, it.entryHour) }
                .mapValues { (_, list) -> list.sumOf { it.convertedPnl } }
            val instGroups = customEnriched.groupBy { it.trade.instrument.ifBlank { "OTHER" } }
                .mapValues { (_, list) -> list.sumOf { it.convertedPnl } }
            val stratGroups = customEnriched.groupBy { it.trade.strategy.ifBlank { "General" } }
                .mapValues { (_, list) -> list.sumOf { it.convertedPnl } }
            val tfGroups = customEnriched.groupBy { it.trade.timeframeEnum.label }
                .mapValues { (_, list) -> list.sumOf { it.convertedPnl } }
            base.copy(
                periodTitle = "Custom Report • $startIso to $endIso",
                dateRangeIso = "$startIso to $endIso",
                totalTrades = customMetrics.totalTrades,
                winningTrades = customMetrics.winningTrades,
                losingTrades = customMetrics.losingTrades,
                breakEvenTrades = customMetrics.breakEvenTrades,
                winRatePercent = customMetrics.winRatePercent,
                grossProfit = customMetrics.totalProfit,
                grossLoss = customMetrics.totalLoss,
                netPnl = customMetrics.netPnl,
                averageWin = customMetrics.averageWin,
                averageLoss = customMetrics.averageLoss,
                bestTradingTime = timeGroups.maxByOrNull { it.value }?.let { "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})" } ?: "No timed trades",
                worstTradingTime = timeGroups.minByOrNull { it.value }?.takeIf { timeGroups.size > 1 || it.value < 0 }?.let { "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})" } ?: "N/A",
                bestInstrument = instGroups.maxByOrNull { it.value }?.let { "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})" } ?: "N/A",
                worstInstrument = instGroups.minByOrNull { it.value }?.takeIf { instGroups.size > 1 || it.value < 0 }?.let { "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})" } ?: "N/A",
                bestStrategy = stratGroups.maxByOrNull { it.value }?.let { "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})" } ?: "N/A",
                worstStrategy = stratGroups.minByOrNull { it.value }?.takeIf { stratGroups.size > 1 || it.value < 0 }?.let { "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})" } ?: "N/A",
                bestTimeframe = tfGroups.maxByOrNull { it.value }?.let { "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})" } ?: "N/A",
                worstTimeframe = tfGroups.minByOrNull { it.value }?.takeIf { tfGroups.size > 1 || it.value < 0 }?.let { "${it.key} (${TradingAnalytics.formatCurrency(it.value, displayCurrency)})" } ?: "N/A",
                dashboardMetrics = customMetrics,
                trades = customEnriched
            )
        } else {
            base
        }
    }

    // SAF Document Launchers
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            ReportExporter.writePdfReport(
                                context = context,
                                outputStream = out,
                                reportTitle = "${rangeType.label} Trading Performance Report",
                                traderLabel = traderLabel,
                                dateRangeLabel = "$startIso to $endIso",
                                metrics = metrics,
                                trades = filteredTrades,
                                periodHighlights = periodHighlights,
                                timezoneId = selectedTimezoneId
                            )
                        } ?: error("Cannot open output stream.")
                    }
                }.onSuccess { onShowMessage("PDF report saved.") }
                    .onFailure { onShowMessage("PDF save failed: ${it.message}") }
            }
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            ReportExporter.writePeriodAnalyticsCsvReport(
                                outputStream = out,
                                report = periodHighlights,
                                traderLabel = traderLabel,
                                timezoneId = selectedTimezoneId
                            )
                        } ?: error("Cannot open output stream.")
                    }
                }.onSuccess { onShowMessage("CSV / Excel report saved.") }
                    .onFailure { onShowMessage("CSV save failed: ${it.message}") }
            }
        }
    }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onExportBackupJson().onSuccess { json ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(json.toByteArray(Charsets.UTF_8))
                                out.flush()
                            } ?: error("Cannot open output stream.")
                        }
                    }.onSuccess { onShowMessage("Cloud backup saved.") }
                        .onFailure { onShowMessage("Backup save failed: ${it.message}") }
                }.onFailure { onShowMessage(it.message ?: "Backup export failed.") }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Unable to read backup file.")
                    }
                }.onSuccess { json -> onRestoreBackupJson(json) }
                    .onFailure { onShowMessage("Restore failed: ${it.message}") }
            }
        }
    }

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
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Trading Reports & Exports", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Generate branded PDF and Excel-compatible CSV reports with both original and converted ${displayCurrency.code} amounts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        DisplayCurrencySelectorBar(
                            selectedCurrency = displayCurrency,
                            onSelectCurrency = onSelectDisplayCurrency,
                            liveUsdToInrRate = liveUsdToInrRate,
                            isRefreshingRate = isRefreshingRate,
                            onRefreshRate = onRefreshRate
                        )

                        if (currentUser.roleEnum == UserRole.ADMIN) {
                            Text("Report Scope (Admin)", style = MaterialTheme.typography.labelMedium, color = ext.gold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedStudentId == null,
                                    onClick = { selectedStudentId = null },
                                    label = { Text("My Journal (${currentUser.name})") }
                                )
                                FilterChip(
                                    selected = selectedStudentId == "__ALL__",
                                    onClick = { selectedStudentId = "__ALL__" },
                                    label = { Text("All Students Combined") }
                                )
                                students.forEach { st ->
                                    FilterChip(
                                        selected = selectedStudentId == st.id,
                                        onClick = { selectedStudentId = st.id },
                                        label = { Text(st.name) }
                                    )
                                }
                            }
                        }

                        Text("Report Period", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportRangeType.entries.forEach { r ->
                                FilterChip(
                                    selected = rangeType == r,
                                    onClick = { rangeType = r },
                                    label = { Text(r.label) }
                                )
                            }
                        }

                        if (rangeType == ReportRangeType.CUSTOM) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { pickingStartDate = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.CalendarToday, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("From: $startIso")
                                }
                                OutlinedButton(
                                    onClick = { pickingStartDate = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.CalendarToday, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("To: $endIso")
                                }
                            }
                        }

                        Text(
                            text = "Period: $startIso to $endIso • Matching Trades: ${filteredTrades.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.electricBlue
                        )
                    }
                }

                // Live Preview Metrics
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientKpiCard(
                        title = "Period Net P&L (${displayCurrency.code})",
                        value = TradingAnalytics.formatCurrency(metrics.netPnl, displayCurrency),
                        subtitle = "${metrics.totalTrades} Trades • ${String.format(Locale.US, "%.1f%%", metrics.winRatePercent)} WR",
                        icon = Icons.Rounded.PictureAsPdf,
                        gradientColors = listOf(
                            ext.cardSurface,
                            (if (metrics.netPnl >= 0) ext.profit else ext.loss).copy(alpha = 0.18f)
                        ),
                        accentColor = if (metrics.netPnl >= 0) ext.profit else ext.loss,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Profit Factor",
                        value = String.format(Locale.US, "%.2f", metrics.profitFactor),
                        subtitle = "Win ${metrics.winningTrades} / Loss ${metrics.losingTrades}",
                        icon = Icons.Rounded.TableChart,
                        gradientColors = listOf(ext.cardSurface, ext.purple.copy(alpha = 0.18f)),
                        accentColor = ext.purple,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Period Performance Breakdown Card (Daily / Weekly / Monthly Highlights)
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("period_highlights_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${periodHighlights.periodTitle} Highlights",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = ext.gold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOpenDeepAnalytics, modifier = Modifier.testTag("reports_open_deep_analytics")) {
                                Icon(Icons.Rounded.AutoGraph, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("Time & Calendar")
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gross Profit: ${TradingAnalytics.formatPlainCurrency(periodHighlights.grossProfit, displayCurrency)}", style = MaterialTheme.typography.bodySmall, color = ext.profit)
                            Text("Gross Loss: ${TradingAnalytics.formatPlainCurrency(periodHighlights.grossLoss, displayCurrency)}", style = MaterialTheme.typography.bodySmall, color = ext.loss)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Avg Win: ${TradingAnalytics.formatPlainCurrency(periodHighlights.averageWin, displayCurrency)}", style = MaterialTheme.typography.bodySmall)
                            Text("Avg Loss: ${TradingAnalytics.formatPlainCurrency(periodHighlights.averageLoss, displayCurrency)}", style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider(color = ext.cardBorder)
                        Text("Best Trading Time: ${periodHighlights.bestTradingTime}", style = MaterialTheme.typography.bodySmall, color = ext.profit)
                        Text("Worst Trading Time: ${periodHighlights.worstTradingTime}", style = MaterialTheme.typography.bodySmall, color = ext.loss)
                        Text("Best Index/Instrument: ${periodHighlights.bestInstrument}  |  Worst: ${periodHighlights.worstInstrument}", style = MaterialTheme.typography.bodySmall)
                        Text("Best Strategy: ${periodHighlights.bestStrategy}  |  Worst: ${periodHighlights.worstStrategy}", style = MaterialTheme.typography.bodySmall)
                        Text("Best Timeframe: ${periodHighlights.bestTimeframe}  |  Worst: ${periodHighlights.worstTimeframe}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Export Actions Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Export & Share Reports", style = MaterialTheme.typography.titleMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GradientButton(
                                text = "Save PDF",
                                icon = Icons.Rounded.Download,
                                colors = listOf(ext.loss, ext.gold),
                                onClick = {
                                    savePdfLauncher.launch("TWBB_Report_${startIso}_${endIso}.pdf")
                                },
                                modifier = Modifier.weight(1f).testTag("export_pdf_button")
                            )
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                ReportExporter.createShareableReportFile(
                                                    context = context,
                                                    fileName = "TWBB_Report_${startIso}_${endIso}.pdf"
                                                ) { out ->
                                                    ReportExporter.writePdfReport(
                                                        context = context,
                                                        outputStream = out,
                                                        reportTitle = "${rangeType.label} Trading Report",
                                                        traderLabel = traderLabel,
                                                        dateRangeLabel = "$startIso to $endIso",
                                                        metrics = metrics,
                                                        trades = filteredTrades,
                                                        periodHighlights = periodHighlights,
                                                        timezoneId = selectedTimezoneId
                                                    )
                                                }
                                            }
                                        }.onSuccess { intent ->
                                            context.startActivity(Intent.createChooser(intent, "Share PDF Report"))
                                        }.onFailure { onShowMessage("Share failed: ${it.message}") }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share PDF")
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GradientButton(
                                text = "Save CSV",
                                icon = Icons.Rounded.TableChart,
                                colors = listOf(ext.profit, ext.teal),
                                onClick = {
                                    saveCsvLauncher.launch("TWBB_Trades_${startIso}_${endIso}.csv")
                                },
                                modifier = Modifier.weight(1f).testTag("export_csv_button")
                            )
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                ReportExporter.createShareableReportFile(
                                                    context = context,
                                                    fileName = "TWBB_Trades_${startIso}_${endIso}.csv"
                                                ) { out ->
                                                    ReportExporter.writePeriodAnalyticsCsvReport(
                                                        outputStream = out,
                                                        report = periodHighlights,
                                                        traderLabel = traderLabel,
                                                        timezoneId = selectedTimezoneId
                                                    )
                                                }
                                            }
                                        }.onSuccess { intent ->
                                            context.startActivity(Intent.createChooser(intent, "Share CSV Report"))
                                        }.onFailure { onShowMessage("Share failed: ${it.message}") }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share CSV")
                            }
                        }
                    }
                }

                // Database Backup & Restore Section
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Database Backup & Data Ownership", style = MaterialTheme.typography.titleMedium, color = ext.gold)
                        Text(
                            text = if (currentUser.roleEnum == UserRole.ADMIN) {
                                "Export a full JSON backup of all accounts, trades, and reviews from Firestore, or restore from a saved backup file."
                            } else {
                                "Export your own complete trading journal backup (JSON) at any time."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = ext.cardBorder)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GradientButton(
                                text = "Backup JSON",
                                icon = Icons.Rounded.Backup,
                                colors = listOf(ext.electricBlue, ext.purple),
                                onClick = {
                                    saveBackupLauncher.launch("TWBB_Backup_${TradingAnalytics.todayIso()}.json")
                                },
                                modifier = Modifier.weight(1f).testTag("backup_json_button")
                            )
                            if (currentUser.roleEnum == UserRole.ADMIN) {
                                OutlinedButton(
                                    onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                                    modifier = Modifier.weight(1f).height(50.dp).testTag("restore_json_button")
                                ) {
                                    Icon(Icons.Rounded.Restore, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore")
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    pickingStartDate?.let { isStart ->
        val initial = if (isStart) customStartMillis else customEndMillis
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        if (isStart) customStartMillis = it else customEndMillis = it
                    }
                    pickingStartDate = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
