package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountStatus
import com.example.data.model.UserEntity
import com.example.domain.CategoryPerformance
import com.example.domain.DashboardMetrics
import com.example.domain.StudentPerformanceSummary
import com.example.domain.TradingAnalytics
import com.example.ui.components.CategoryBreakdownChart
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.GradientButton
import com.example.ui.components.GradientKpiCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@Composable
fun AdminStudentsScreen(
    summaries: List<StudentPerformanceSummary>,
    combinedMetrics: DashboardMetrics,
    onOpenStudentDetail: (Long) -> Unit,
    onCreateStudent: (
        name: String, email: String, password: String,
        dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Int, maxRisk: Double,
        onDone: () -> Unit
    ) -> Unit,
    onUpdateStudent: (
        student: UserEntity, name: String, email: String, newPassword: String?,
        dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Int, maxRisk: Double,
        onDone: () -> Unit
    ) -> Unit,
    onToggleSuspend: (UserEntity, Boolean) -> Unit,
    onDeleteStudent: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<UserEntity?>(null) }
    var deletingStudent by remember { mutableStateOf<UserEntity?>(null) }

    val filtered = remember(summaries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) summaries
        else summaries.filter {
            it.student.name.contains(q, ignoreCase = true) ||
                it.student.email.contains(q, ignoreCase = true)
        }
    }

    val comparisonData = remember(summaries) {
        summaries.map {
            CategoryPerformance(
                category = it.student.name,
                tradeCount = it.metrics.totalTrades,
                winCount = it.metrics.winningTrades,
                netPnl = it.metrics.netPnl,
                winRate = it.metrics.winRatePercent
            )
        }.sortedByDescending { it.netPnl }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = ext.gold,
                contentColor = Color(0xFF0A1628),
                modifier = Modifier.testTag("admin_add_student_fab")
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Student")
            }
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
                            title = "Total Students",
                            value = summaries.size.toString(),
                            subtitle = "${summaries.count { it.student.status == AccountStatus.ACTIVE }} Active • ${summaries.count { it.student.status == AccountStatus.SUSPENDED }} Suspended",
                            icon = Icons.Rounded.Groups,
                            gradientColors = listOf(ext.cardSurface, ext.electricBlue.copy(alpha = 0.18f)),
                            accentColor = ext.electricBlue,
                            modifier = Modifier.weight(1f)
                        )
                        GradientKpiCard(
                            title = "Students Net P&L",
                            value = TradingAnalytics.formatCurrency(combinedMetrics.netPnl),
                            subtitle = "${combinedMetrics.totalTrades} Trades • ${String.format(Locale.US, "%.1f%%", combinedMetrics.winRatePercent)} WR",
                            icon = Icons.Rounded.TrendingUp,
                            gradientColors = listOf(
                                ext.cardSurface,
                                (if (combinedMetrics.netPnl >= 0) ext.profit else ext.loss).copy(alpha = 0.18f)
                            ),
                            accentColor = if (combinedMetrics.netPnl >= 0) ext.profit else ext.loss,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (comparisonData.isNotEmpty()) {
                        CategoryBreakdownChart(
                            title = "Student Performance Comparison",
                            subtitle = "Live Net P&L and Win Rate leaderboard",
                            accentColor = ext.gold,
                            items = comparisonData
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search students by name or email...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("admin_student_search_input")
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        EmptyTradingState(
                            title = if (summaries.isEmpty()) "No Students Registered Yet" else "No Matching Students",
                            subtitle = if (summaries.isEmpty()) "Add a student account with the + button, or students can register via Student Sign Up." else "Adjust your search query.",
                            icon = Icons.Rounded.Groups,
                            actionLabel = if (summaries.isEmpty()) "Add Student" else null,
                            onAction = if (summaries.isEmpty()) { { showCreateDialog = true } } else null
                        )
                    }
                }
            } else {
                items(filtered, key = { it.student.id }) { summary ->
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        StudentMonitoringCard(
                            summary = summary,
                            onOpenDetail = { onOpenStudentDetail(summary.student.id) },
                            onEdit = { editingStudent = summary.student },
                            onToggleSuspend = {
                                onToggleSuspend(summary.student, summary.student.status == AccountStatus.ACTIVE)
                            },
                            onDelete = { deletingStudent = summary.student }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCreateDialog) {
        StudentEditorDialog(
            existing = null,
            onDismiss = { showCreateDialog = false },
            onSave = { name, email, pass, goal, maxLoss, maxTrades, maxRisk ->
                onCreateStudent(name, email, pass.orEmpty(), goal, maxLoss, maxTrades, maxRisk) {
                    showCreateDialog = false
                }
            }
        )
    }

    editingStudent?.let { st ->
        StudentEditorDialog(
            existing = st,
            onDismiss = { editingStudent = null },
            onSave = { name, email, pass, goal, maxLoss, maxTrades, maxRisk ->
                onUpdateStudent(st, name, email, pass, goal, maxLoss, maxTrades, maxRisk) {
                    editingStudent = null
                }
            }
        )
    }

    deletingStudent?.let { st ->
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text("Delete Student Account?") },
            text = { Text("This permanently deletes ${st.name} (${st.email}) and all of their trade records.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteStudent(st.id)
                    deletingStudent = null
                }) { Text("Delete", color = ext.loss) }
            },
            dismissButton = {
                TextButton(onClick = { deletingStudent = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StudentMonitoringCard(
    summary: StudentPerformanceSummary,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onToggleSuspend: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalTradingColors.current
    val st = summary.student
    val m = summary.metrics
    val pnlColor = if (m.netPnl >= 0) ext.profit else ext.loss

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .testTag("student_card_${st.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(st.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(st.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(st.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Trades", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${m.totalTrades}", style = MaterialTheme.typography.titleSmall)
                }
                Column {
                    Text("Win Rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format(Locale.US, "%.1f%%", m.winRatePercent), style = MaterialTheme.typography.titleSmall, color = ext.electricBlue)
                }
                Column {
                    Text("SL Discipline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format(Locale.US, "%.0f%%", summary.discipline.stopLossDisciplinePercent), style = MaterialTheme.typography.titleSmall, color = ext.teal)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Net P&L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(TradingAnalytics.formatCurrency(m.netPnl), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = pnlColor)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap card for full performance & screenshots",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.gold
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit student", tint = ext.electricBlue)
                    }
                    IconButton(onClick = onToggleSuspend) {
                        Icon(
                            if (st.status == AccountStatus.ACTIVE) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                            contentDescription = if (st.status == AccountStatus.ACTIVE) "Suspend student" else "Activate student",
                            tint = if (st.status == AccountStatus.ACTIVE) ext.gold else ext.profit
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete student", tint = ext.loss)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentEditorDialog(
    existing: UserEntity?,
    onDismiss: () -> Unit,
    onSave: (
        name: String, email: String, password: String?,
        dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Int, maxRisk: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var dailyGoal by remember { mutableStateOf((existing?.dailyProfitGoal ?: 250.0).toString()) }
    var dailyMaxLoss by remember { mutableStateOf((existing?.dailyMaxLossLimit ?: 100.0).toString()) }
    var maxTrades by remember { mutableStateOf((existing?.maxTradesPerDay ?: 5).toString()) }
    var maxRisk by remember { mutableStateOf((existing?.maxRiskPerTrade ?: 50.0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Student Account" else "Edit Student Account") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Student Name") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Student Email") }, singleLine = true)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (existing == null) "Initial Password (min 6 chars)" else "Reset Password (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dailyGoal,
                        onValueChange = { dailyGoal = it },
                        label = { Text("Daily Goal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dailyMaxLoss,
                        onValueChange = { dailyMaxLoss = it },
                        label = { Text("Max Loss") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxTrades,
                        onValueChange = { maxTrades = it },
                        label = { Text("Max Trades") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxRisk,
                        onValueChange = { maxRisk = it },
                        label = { Text("Max Risk") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            GradientButton(
                text = if (existing == null) "Create Student" else "Save Changes",
                onClick = {
                    onSave(
                        name,
                        email,
                        password.ifBlank { null },
                        dailyGoal.toDoubleOrNull() ?: 250.0,
                        dailyMaxLoss.toDoubleOrNull() ?: 100.0,
                        maxTrades.toIntOrNull() ?: 5,
                        maxRisk.toDoubleOrNull() ?: 50.0
                    )
                },
                modifier = Modifier.width(150.dp)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
