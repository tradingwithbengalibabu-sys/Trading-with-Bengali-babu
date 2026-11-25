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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AnnouncementEntity
import com.example.data.model.SmartDisciplineAlertEntity
import com.example.data.model.StudentWarningEntity
import com.example.data.model.TradingCurrency
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.model.WeeklyReportEntity
import com.example.domain.DashboardMetrics
import com.example.domain.DisciplineMetrics
import com.example.domain.SmartPerformanceInsight
import com.example.domain.TradingAnalytics
import com.example.ui.components.ActiveBreakCooldownCard
import com.example.ui.components.BrandLogoBadge
import com.example.ui.components.CategoryBreakdownChart
import com.example.ui.components.DailyPnlBarChart
import com.example.ui.components.DisplayCurrencySelectorBar
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.EquityCurveChart
import com.example.ui.components.FlashingSmartAlertBanner
import com.example.ui.components.GradientButton
import com.example.ui.components.GradientKpiCard
import com.example.ui.components.MonthlyPerformanceChart
import com.example.ui.components.RoleBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.components.WinLossDonutChart
import com.example.ui.theme.LocalTradingColors
import java.util.Locale

@Composable
fun DashboardScreen(
    user: UserEntity,
    metrics: DashboardMetrics,
    discipline: DisciplineMetrics,
    displayCurrency: TradingCurrency,
    liveUsdToInrRate: Double,
    isRefreshingRate: Boolean,
    unreadMessagesCount: Int = 0,
    unreadWarningsCount: Int = 0,
    unreadAnnouncementsCount: Int = 0,
    latestUnreadWarning: StudentWarningEntity? = null,
    latestAnnouncement: AnnouncementEntity? = null,
    activeSmartAlerts: List<SmartDisciplineAlertEntity> = emptyList(),
    latestWeeklyReport: WeeklyReportEntity? = null,
    unreadWeeklyReportsCount: Int = 0,
    smartInsights: List<SmartPerformanceInsight> = emptyList(),
    activeSignalsCount: Int = 0,
    isSignalAccessBlocked: Boolean = false,
    isClassroomLive: Boolean = false,
    classroomParticipantCount: Int = 0,
    onSelectDisplayCurrency: (TradingCurrency) -> Unit,
    onRefreshRate: () -> Unit,
    onAddTradeClick: () -> Unit,
    onOpenContactAdmin: () -> Unit = {},
    onOpenWarnings: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
    onAcknowledgeSmartAlert: (SmartDisciplineAlertEntity, Int) -> Unit = { _, _ -> },
    onOpenWeeklyReports: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenTimeAnalytics: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenTradeSignals: () -> Unit = {},
    onOpenLiveClassroom: () -> Unit = {},
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

                            DisplayCurrencySelectorBar(
                                selectedCurrency = displayCurrency,
                                onSelectCurrency = onSelectDisplayCurrency,
                                liveUsdToInrRate = liveUsdToInrRate,
                                isRefreshingRate = isRefreshingRate,
                                onRefreshRate = onRefreshRate,
                                onHeroSurface = true,
                                modifier = Modifier.testTag("dashboard_currency_selector")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "NET PROFIT / LOSS (${displayCurrency.code})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.72f)
                                    )
                                    val netColor = if (metrics.netPnl >= 0) ext.profit else ext.loss
                                    Text(
                                        text = TradingAnalytics.formatCurrency(metrics.netPnl, displayCurrency),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = netColor
                                    )
                                    if (metrics.inrTradeCount > 0 && metrics.usdTradeCount > 0) {
                                        Text(
                                            text = "${metrics.inrTradeCount} INR + ${metrics.usdTradeCount} USD trades converted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.gold.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                                GradientButton(
                                    text = "Log Trade",
                                    icon = Icons.Rounded.Add,
                                    onClick = onAddTradeClick,
                                    colors = listOf(ext.gold, Color(0xFFD97706)),
                                    modifier = Modifier.testTag("dashboard_add_trade_button")
                                )
                            }

                            // Communication Quick Actions Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onOpenContactAdmin,
                                    border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.65f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("dashboard_contact_admin_button")
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
                                        Icon(
                                            imageVector = Icons.Rounded.Chat,
                                            contentDescription = null,
                                            tint = ext.gold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (user.roleEnum == UserRole.ADMIN) "Admin Inbox" else "Contact Admin",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val alertsTotal = unreadWarningsCount + unreadAnnouncementsCount
                                OutlinedButton(
                                    onClick = if (unreadWarningsCount > 0) onOpenWarnings else onOpenAnnouncements,
                                    border = BorderStroke(
                                        1.dp,
                                        if (unreadWarningsCount > 0) ext.loss.copy(alpha = 0.8f)
                                        else Color.White.copy(alpha = 0.35f)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (unreadWarningsCount > 0) ext.loss.copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("dashboard_alerts_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (alertsTotal > 0) {
                                                Badge(containerColor = ext.loss, contentColor = Color.White) {
                                                    Text(alertsTotal.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (unreadWarningsCount > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.Campaign,
                                            contentDescription = "Alerts and Announcements",
                                            tint = if (unreadWarningsCount > 0) Color(0xFFFCA5A5) else ext.gold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (user.roleEnum == UserRole.ADMIN) "Broadcast" else "Alerts",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Admin Trade Signals & Live Online Classroom Quick Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val signalTint = if (isSignalAccessBlocked && user.roleEnum != UserRole.ADMIN) {
                                    Color(0xFFFCA5A5)
                                } else {
                                    ext.gold
                                }
                                OutlinedButton(
                                    onClick = onOpenTradeSignals,
                                    border = BorderStroke(1.dp, signalTint.copy(alpha = 0.65f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("dashboard_trade_signals_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (user.roleEnum != UserRole.ADMIN && isSignalAccessBlocked) {
                                                Badge(containerColor = ext.loss, contentColor = Color.White) {
                                                    Text("!")
                                                }
                                            } else if (activeSignalsCount > 0) {
                                                Badge(containerColor = ext.profit, contentColor = Color.White) {
                                                    Text(activeSignalsCount.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (user.roleEnum != UserRole.ADMIN && isSignalAccessBlocked) {
                                                Icons.Rounded.Lock
                                            } else {
                                                Icons.Rounded.ShowChart
                                            },
                                            contentDescription = "Trade Signals",
                                            tint = signalTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (user.roleEnum == UserRole.ADMIN) "Trade Desk" else "Trade Signals",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = onOpenLiveClassroom,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isClassroomLive) ext.profit.copy(alpha = 0.9f) else ext.electricBlue.copy(alpha = 0.65f)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isClassroomLive) ext.profit.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("dashboard_live_classroom_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (isClassroomLive) {
                                                Badge(containerColor = ext.loss, contentColor = Color.White) {
                                                    Text("LIVE")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LiveTv,
                                            contentDescription = "Live Classroom",
                                            tint = if (isClassroomLive) Color(0xFF86EFAC) else ext.electricBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            user.roleEnum == UserRole.ADMIN && isClassroomLive -> "Live ($classroomParticipantCount)"
                                            user.roleEnum == UserRole.ADMIN -> "Start Live Class"
                                            isClassroomLive -> "Join Live Class"
                                            else -> "Live Class"
                                        },
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Student First-Login "Complete Your Profile" Banner & Separate Student Profile Card
                if (user.roleEnum == UserRole.STUDENT) {
                    if (user.hasIncompleteProfile) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = ext.goldContainer),
                            border = BorderStroke(1.5.dp, ext.gold.copy(alpha = 0.75f)),
                            modifier = Modifier.fillMaxWidth().testTag("dashboard_complete_profile_card")
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
                                        photoUrl = user.profilePhotoUrl,
                                        name = user.name,
                                        size = 52.dp,
                                        borderColor = ext.gold
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Complete Your Profile (${user.profileCompletionPercent}%)",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ext.gold
                                        )
                                        Text(
                                            text = "Upload your profile photo and add mobile, WhatsApp, DOB, gender & full address.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { user.profileCompletionPercent / 100f },
                                    color = ext.gold,
                                    trackColor = ext.gold.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                )
                                Button(
                                    onClick = onOpenProfile,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ext.gold,
                                        contentColor = Color(0xFF0A1628)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("dashboard_complete_profile_button")
                                ) {
                                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Complete Your Profile",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    } else {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                            border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().testTag("dashboard_student_profile_card")
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
                                        photoUrl = user.profilePhotoUrl,
                                        name = user.name,
                                        size = 56.dp,
                                        borderColor = ext.electricBlue
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = user.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Mobile: ${user.mobileNumber} • WhatsApp: ${user.whatsappNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.electricBlue,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onOpenProfile,
                                        border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(48.dp).testTag("dashboard_edit_profile_button")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Profile", tint = ext.electricBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Profile")
                                    }
                                }
                                HorizontalDivider(color = ext.cardBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Cake, contentDescription = null, tint = ext.gold, modifier = Modifier.size(15.dp))
                                        Text(
                                            text = "DOB: ${user.dateOfBirth}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = ext.purple, modifier = Modifier.size(15.dp))
                                        Text(
                                            text = "Gender: ${user.gender}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Home, contentDescription = null, tint = ext.teal, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = user.fullAddress,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Flashing Smart Discipline Warnings (SL Streak & Strategy SL Warnings — stay visible until acknowledged)
                activeSmartAlerts.forEach { alert ->
                    FlashingSmartAlertBanner(
                        alert = alert,
                        onAcknowledge = { breakDays -> onAcknowledgeSmartAlert(alert, breakDays) }
                    )
                }

                // Active Mental Reset Break Reminder (2-7 day break)
                ActiveBreakCooldownCard(untilMillis = user.breakRecommendedUntilMillis)

                // New Weekly Performance Report Banner (Admin & Student)
                if (latestWeeklyReport != null && unreadWeeklyReportsCount > 0) {
                    Surface(
                        onClick = onOpenWeeklyReports,
                        color = ext.cardSurface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.5.dp, ext.electricBlue.copy(alpha = 0.75f)),
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_weekly_report_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = "Weekly Report", tint = ext.electricBlue)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Weekly Mistake Analysis Ready (${latestWeeklyReport.weekKey})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ext.electricBlue
                                )
                                Text(
                                    text = latestWeeklyReport.summaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ext.gold
                            )
                        }
                    }
                }

                // Unread Admin Warning Alert Banner (Student)
                if (user.roleEnum == UserRole.STUDENT && latestUnreadWarning != null) {
                    Surface(
                        onClick = onOpenWarnings,
                        color = ext.lossContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.5.dp, ext.loss.copy(alpha = 0.75f)),
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_unread_warning_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = "Admin Warning", tint = ext.loss)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${latestUnreadWarning.severityEnum.label}: ${latestUnreadWarning.title}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ext.loss
                                )
                                Text(
                                    text = latestUnreadWarning.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "View",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ext.loss
                            )
                        }
                    }
                }

                // Unread Announcement Banner (Student)
                if (user.roleEnum == UserRole.STUDENT && unreadAnnouncementsCount > 0 && latestAnnouncement != null) {
                    Surface(
                        onClick = onOpenAnnouncements,
                        color = ext.goldContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_unread_announcement_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Campaign, contentDescription = "Announcement", tint = ext.gold)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Announcement: ${latestAnnouncement.title}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ext.gold
                                )
                                Text(
                                    text = latestAnnouncement.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$unreadAnnouncementsCount New",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ext.gold
                            )
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
                                    text = "Today: ${discipline.todayTradeCount}/${user.maxTradesPerDay} trades • Net ${TradingAnalytics.formatCurrency(discipline.todayNetPnl, displayCurrency)} (Limit -${TradingAnalytics.formatPlainCurrency(discipline.convertedDailyMaxLoss, displayCurrency)})",
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
                        subtitle = "Log INR or USD trades and switch your Display Currency anytime. All 16 KPIs and 6 charts convert automatically using historical trade rates.",
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
                        title = "Total Profit (${displayCurrency.code})",
                        value = TradingAnalytics.formatPlainCurrency(metrics.totalProfit, displayCurrency),
                        subtitle = "Avg Win: ${TradingAnalytics.formatPlainCurrency(metrics.averageWin, displayCurrency)}",
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                        gradientColors = listOf(baseSurface, ext.profit.copy(alpha = if (ext.isDark) 0.20f else 0.12f)),
                        accentColor = ext.profit,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Total Loss (${displayCurrency.code})",
                        value = TradingAnalytics.formatPlainCurrency(metrics.totalLoss, displayCurrency),
                        subtitle = "Avg Loss: ${TradingAnalytics.formatPlainCurrency(metrics.averageLoss, displayCurrency)}",
                        icon = Icons.AutoMirrored.Rounded.TrendingDown,
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
                val zeroPlaceholder = TradingAnalytics.formatPlainCurrency(0.0, displayCurrency)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientKpiCard(
                        title = "Best Trading Day",
                        value = metrics.bestTradingDay?.let { TradingAnalytics.formatCurrency(it.netPnl, displayCurrency) } ?: zeroPlaceholder,
                        subtitle = metrics.bestTradingDay?.dateIso ?: "No days yet",
                        icon = Icons.Rounded.EmojiEvents,
                        gradientColors = listOf(baseSurface, ext.profit.copy(alpha = 0.14f)),
                        accentColor = ext.profit,
                        modifier = Modifier.weight(1f)
                    )
                    GradientKpiCard(
                        title = "Worst Trading Day",
                        value = metrics.worstTradingDay?.let { TradingAnalytics.formatCurrency(it.netPnl, displayCurrency) } ?: zeroPlaceholder,
                        subtitle = metrics.worstTradingDay?.dateIso ?: "No days yet",
                        icon = Icons.Rounded.Bolt,
                        gradientColors = listOf(baseSurface, ext.loss.copy(alpha = 0.14f)),
                        accentColor = ext.loss,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Time-Based Analytics, Smart Insights & Calendar Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_time_analytics_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ext.gold, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Time, Session & Overtrading Insights",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        if (smartInsights.isNotEmpty()) {
                            smartInsights.take(2).forEach { insight ->
                                val col = if (insight.isPositive) ext.profit else ext.loss
                                Surface(
                                    color = col.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(insight.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = col)
                                        Text(insight.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Explore hourly edge, Mon–Sun weekday breakdown, trading sessions, holding duration, overtrading gaps & interactive Trade Calendar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onOpenTimeAnalytics,
                                border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("dashboard_open_time_analytics")
                            ) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = ext.electricBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Time Insights", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onOpenCalendar,
                                border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("dashboard_open_trade_calendar")
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = ext.gold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Trade Calendar", maxLines = 1)
                            }
                        }
                    }
                }

                // All 6 Interactive Charts
                EquityCurveChart(curve = metrics.equityCurve, displayCurrency = displayCurrency)
                DailyPnlBarChart(series = metrics.dailyPnlSeries, displayCurrency = displayCurrency)
                WinLossDonutChart(
                    wins = metrics.winningTrades,
                    losses = metrics.losingTrades,
                    breakEvens = metrics.breakEvenTrades,
                    winRatePercent = metrics.winRatePercent
                )
                MonthlyPerformanceChart(series = metrics.monthlySeries, displayCurrency = displayCurrency)
                CategoryBreakdownChart(
                    title = "Performance by Instrument (${displayCurrency.code})",
                    subtitle = "Net P&L and win rate per trading symbol",
                    accentColor = ext.electricBlue,
                    items = metrics.instrumentPerformance,
                    displayCurrency = displayCurrency
                )
                CategoryBreakdownChart(
                    title = "Performance by Timeframe (${displayCurrency.code})",
                    subtitle = "Net P&L breakdown across M1 to D1",
                    accentColor = ext.purple,
                    items = metrics.timeframePerformance,
                    displayCurrency = displayCurrency
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
