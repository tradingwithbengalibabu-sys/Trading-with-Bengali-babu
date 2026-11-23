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
import androidx.compose.material.icons.rounded.MarkEmailUnread
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountStatus
import com.example.data.model.StudentInvite
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
    pendingInvites: List<StudentInvite>,
    combinedMetrics: DashboardMetrics,
    onOpenStudentDetail: (String) -> Unit,
    onCreateInvite: (
        name: String, email: String,
        dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Int, maxRisk: Double,
        onDone: () -> Unit
    ) -> Unit,
    onUpdateInvite: (
        invite: StudentInvite, name: String, email: String,
        dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Int, maxRisk: Double,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteInvite: (String) -> Unit,
    onUpdateStudent: (
        student: UserEntity, name: String, email: String,
        dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Int, maxRisk: Double,
        onDone: () -> Unit
    ) -> Unit,
    onToggleSuspend: (UserEntity, Boolean) -> Unit,
    onDeleteStudent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalTradingColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showCreateInviteDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<UserEntity?>(null) }
    var editingInvite by remember { mutableStateOf<StudentInvite?>(null) }
    var deletingStudent by remember { mutableStateOf<UserEntity?>(null) }

    val filtered = remember(summaries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) summaries
        else summaries.filter {
            it.student.name.contains(q, ignoreCase = true) ||
                it.student.email.contains(q, ignoreCase = true)
        }
    }

    val filteredInvites = remember(pendingInvites, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) pendingInvites
        else pendingInvites.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.email.contains(q, ignoreCase = true)
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
                onClick = { showCreateInviteDialog = true },
                containerColor = ext.gold,
                contentColor = Color(0xFF0A1628),
                modifier = Modifier.testTag("admin_add_student_fab")
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = "Invite Student")
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
                            title = "Registered Students",
                            value = summaries.size.toString(),
                            subtitle = "${summaries.count { it.student.statusEnum == AccountStatus.ACTIVE }} Active • ${summaries.count { it.student.statusEnum == AccountStatus.SUSPENDED }} Susp • ${pendingInvites.size} Invited",
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
                        placeholder = { Text("Search students or invites by name or email...") },
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

            if (filteredInvites.isNotEmpty()) {
                item {
                    Text(
                        text = "Invited Students (Awaiting Google Sign-In)",
                        style = MaterialTheme.typography.titleSmall,
                        color = ext.gold,
                        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()
                    )
                }
                items(filteredInvites, key = { "inv_${it.id}" }) { invite ->
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        InviteCard(
                            invite = invite,
                            onEdit = { editingInvite = invite },
                            onDelete = { onDeleteInvite(invite.id) }
                        )
                    }
                }
            }

            if (filtered.isEmpty() && filteredInvites.isEmpty()) {
                item {
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        EmptyTradingState(
                            title = if (summaries.isEmpty()) "No Students Registered Yet" else "No Matching Students",
                            subtitle = if (summaries.isEmpty()) "Pre-register a student by Google email using +, or students can sign in directly with Google." else "Adjust your search query.",
                            icon = Icons.Rounded.Groups,
                            actionLabel = if (summaries.isEmpty()) "Invite Student" else null,
                            onAction = if (summaries.isEmpty()) { { showCreateInviteDialog = true } } else null
                        )
                    }
                }
            } else if (filtered.isNotEmpty()) {
                item {
                    Text(
                        text = "Active & Monitored Students (${filtered.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()
                    )
                }
                items(filtered, key = { it.student.id }) { summary ->
                    Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                        StudentMonitoringCard(
                            summary = summary,
                            onOpenDetail = { onOpenStudentDetail(summary.student.id) },
                            onEdit = { editingStudent = summary.student },
                            onToggleSuspend = {
                                onToggleSuspend(summary.student, summary.student.statusEnum == AccountStatus.ACTIVE)
                            },
                            onDelete = { deletingStudent = summary.student }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCreateInviteDialog) {
        StudentConfigDialog(
            title = "Invite Student (Google Email)",
            confirmLabel = "Send Invite",
            initialName = "",
            initialEmail = "",
            initialGoal = 250.0,
            initialMaxLoss = 100.0,
            initialMaxTrades = 5,
            initialMaxRisk = 50.0,
            onDismiss = { showCreateInviteDialog = false },
            onSave = { name, email, goal, maxLoss, maxTrades, maxRisk ->
                onCreateInvite(name, email, goal, maxLoss, maxTrades, maxRisk) {
                    showCreateInviteDialog = false
                }
            }
        )
    }

    editingInvite?.let { inv ->
        StudentConfigDialog(
            title = "Edit Student Invite",
            confirmLabel = "Save Invite",
            initialName = inv.name,
            initialEmail = inv.email,
            initialGoal = inv.dailyProfitGoal,
            initialMaxLoss = inv.dailyMaxLossLimit,
            initialMaxTrades = inv.maxTradesPerDay,
            initialMaxRisk = inv.maxRiskPerTrade,
            onDismiss = { editingInvite = null },
            onSave = { name, email, goal, maxLoss, maxTrades, maxRisk ->
                onUpdateInvite(inv, name, email, goal, maxLoss, maxTrades, maxRisk) {
                    editingInvite = null
                }
            }
        )
    }

    editingStudent?.let { st ->
        StudentConfigDialog(
            title = "Edit Student Profile",
            confirmLabel = "Save Changes",
            initialName = st.name,
            initialEmail = st.email,
            initialGoal = st.dailyProfitGoal,
            initialMaxLoss = st.dailyMaxLossLimit,
            initialMaxTrades = st.maxTradesPerDay,
            initialMaxRisk = st.maxRiskPerTrade,
            onDismiss = { editingStudent = null },
            onSave = { name, email, goal, maxLoss, maxTrades, maxRisk ->
                onUpdateStudent(st, name, email, goal, maxLoss, maxTrades, maxRisk) {
                    editingStudent = null
                }
            }
        )
    }

    deletingStudent?.let { st ->
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text("Delete Student Account?") },
            text = { Text("This permanently deletes ${st.name} (${st.email}), all of their cloud trade records, and discipline reviews.") },
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
private fun InviteCard(
    invite: StudentInvite,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalTradingColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
        border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.MarkEmailUnread, contentDescription = null, tint = ext.gold)
                Column {
                    Text(invite.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(invite.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(color = ext.goldContainer, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "Goal $${invite.dailyProfitGoal.toInt()} • Max ${invite.maxTradesPerDay} trades/day",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.gold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit invite", tint = ext.electricBlue)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete invite", tint = ext.loss)
                }
            }
        }
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
                StatusBadge(st.statusEnum)
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
                            if (st.statusEnum == AccountStatus.ACTIVE) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                            contentDescription = if (st.statusEnum == AccountStatus.ACTIVE) "Suspend student" else "Activate student",
                            tint = if (st.statusEnum == AccountStatus.ACTIVE) ext.gold else ext.profit
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
private fun StudentConfigDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialEmail: String,
    initialGoal: Double,
    initialMaxLoss: Double,
    initialMaxTrades: Int,
    initialMaxRisk: Double,
    onDismiss: () -> Unit,
    onSave: (
        name: String, email: String,
        dailyGoal: Double, dailyMaxLoss: Double, maxTrades: Int, maxRisk: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var dailyGoal by remember { mutableStateOf(initialGoal.toString()) }
    var dailyMaxLoss by remember { mutableStateOf(initialMaxLoss.toString()) }
    var maxTrades by remember { mutableStateOf(initialMaxTrades.toString()) }
    var maxRisk by remember { mutableStateOf(initialMaxRisk.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Student Name") }, singleLine = true)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Student Google Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
                text = confirmLabel,
                onClick = {
                    onSave(
                        name,
                        email,
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
