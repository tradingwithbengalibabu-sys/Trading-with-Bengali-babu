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
import androidx.compose.ui.unit.dp
import com.example.data.model.ReportRangeType
import com.example.data.model.TradeEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.domain.TradingAnalytics
import com.example.export.ReportExporter
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
    onExportBackupJson: suspend () -> Result<String>,
    onRestoreBackupJson: (String) -> Unit,
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
    val metrics = remember(filteredTrades) { TradingAnalytics.calculateDashboardMetrics(filteredTrades) }

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
                                trades = filteredTrades
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
                            ReportExporter.writeCsvReport(out, filteredTrades)
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
                            text = "Generate branded PDF and Excel-compatible CSV reports directly from verified cloud trades.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        title = "Period Net P&L",
                        value = TradingAnalytics.formatCurrency(metrics.netPnl),
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
                                                        trades = filteredTrades
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
                                                    ReportExporter.writeCsvReport(out, filteredTrades)
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
