package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AnnouncementPriority
import com.example.data.model.ConversationThread
import com.example.data.model.DirectMessageEntity
import com.example.data.model.StudentWarningEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.model.WarningSeverity
import com.example.ui.components.BrandLogoBadge
import com.example.ui.components.EmptyTradingState
import com.example.ui.components.GradientButton
import com.example.ui.components.RoleBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.LocalTradingColors
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    currentUser: UserEntity,
    students: List<UserEntity>,
    conversations: List<ConversationThread>,
    warnings: List<StudentWarningEntity>,
    announcements: List<AnnouncementEntity>,
    unreadMessagesCount: Int,
    unreadWarningsCount: Int,
    unreadAnnouncementsCount: Int,
    initialTab: Int = 0,
    initialStudentId: String? = null,
    observeThreadMessages: (String) -> Flow<List<DirectMessageEntity>>,
    onSendMessageToAdmin: (String, () -> Unit) -> Unit,
    onSendMessageToStudent: (UserEntity, String, () -> Unit) -> Unit,
    onMarkConversationRead: (String) -> Unit,
    onSendWarning: (UserEntity, WarningSeverity, String, String, () -> Unit) -> Unit,
    onMarkWarningRead: (StudentWarningEntity) -> Unit,
    onDeleteWarning: (String) -> Unit,
    onPublishAnnouncement: (String, String, AnnouncementPriority, () -> Unit) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onMarkAnnouncementsRead: () -> Unit,
    onBack: () -> Unit
) {
    val ext = LocalTradingColors.current
    val isAdmin = currentUser.roleEnum == UserRole.ADMIN

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var selectedStudentId by remember(initialStudentId) { mutableStateOf(initialStudentId) }
    var openedDirectlyToStudent by remember(initialStudentId) { mutableStateOf(initialStudentId != null) }

    BackHandler {
        if (isAdmin && selectedTab == 0 && selectedStudentId != null && !openedDirectlyToStudent) {
            selectedStudentId = null
        } else {
            onBack()
        }
    }

    // Automatically clear announcement unread badge for students when viewing Announcements tab
    LaunchedEffect(selectedTab, announcements.size, isAdmin) {
        if (!isAdmin && selectedTab == 2 && announcements.isNotEmpty()) {
            onMarkAnnouncementsRead()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        topBar = {
            val activeChatStudent = if (isAdmin && selectedTab == 0 && selectedStudentId != null) {
                students.firstOrNull { it.id == selectedStudentId }
                    ?: conversations.firstOrNull { it.studentId == selectedStudentId }?.let { conv ->
                        UserEntity(id = conv.studentId, userId = conv.studentId, name = conv.studentName, email = conv.studentEmail)
                    }
            } else null

            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandLogoBadge(size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when {
                                    activeChatStudent != null -> activeChatStudent.name
                                    isAdmin -> "Admin Communication Hub"
                                    else -> "Contact Admin & Alerts"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    activeChatStudent != null -> activeChatStudent.email
                                    isAdmin -> "Inbox, Student Warnings & Announcements"
                                    else -> "Private messages with Bangali Babu"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.gold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isAdmin && selectedTab == 0 && selectedStudentId != null && !openedDirectlyToStudent) {
                                selectedStudentId = null
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("comm_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeChatStudent != null) {
                        IconButton(
                            onClick = {
                                openedDirectlyToStudent = false
                                selectedStudentId = activeChatStudent.id
                                selectedTab = 1
                            },
                            modifier = Modifier.testTag("comm_quick_warn_button")
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = "Send Warning to Student", tint = ext.gold)
                        }
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
            val tabs = if (isAdmin) {
                listOf(
                    Triple("Inbox", Icons.Rounded.Chat, unreadMessagesCount),
                    Triple("Warnings", Icons.Rounded.Warning, 0),
                    Triple("Announce", Icons.Rounded.Campaign, 0)
                )
            } else {
                listOf(
                    Triple("Contact Admin", Icons.Rounded.Chat, unreadMessagesCount),
                    Triple("Warnings", Icons.Rounded.Warning, unreadWarningsCount),
                    Triple("Updates", Icons.Rounded.Campaign, unreadAnnouncementsCount)
                )
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ext.gold,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                        color = ext.gold
                    )
                }
            ) {
                tabs.forEachIndexed { index, (label, icon, badgeCount) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        selectedContentColor = ext.gold,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("comm_tab_$index"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) {
                                            Badge(containerColor = ext.loss, contentColor = Color.White) {
                                                Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
                                }
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    if (isAdmin) {
                        val activeStudent = selectedStudentId?.let { targetId ->
                            students.firstOrNull { it.id == targetId }
                                ?: conversations.firstOrNull { it.studentId == targetId }?.let { conv ->
                                    UserEntity(
                                        id = conv.studentId,
                                        userId = conv.studentId,
                                        name = conv.studentName,
                                        email = conv.studentEmail
                                    )
                                }
                        }
                        if (activeStudent == null) {
                            AdminInboxListPane(
                                students = students,
                                conversations = conversations,
                                onSelectStudent = { studentId ->
                                    openedDirectlyToStudent = false
                                    selectedStudentId = studentId
                                }
                            )
                        } else {
                            val messages by observeThreadMessages(activeStudent.id)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val threadConv = conversations.firstOrNull { it.studentId == activeStudent.id }
                            LaunchedEffect(activeStudent.id, threadConv?.unreadByAdminCount) {
                                if ((threadConv?.unreadByAdminCount ?: 0) > 0) {
                                    onMarkConversationRead(activeStudent.id)
                                }
                            }
                            DirectChatPane(
                                currentUser = currentUser,
                                peerTitle = activeStudent.name,
                                peerSubtitle = "Private Admin–Student Thread • All conversations remain strictly confidential",
                                messages = messages,
                                showBackToInboxBar = true,
                                onBackToInbox = {
                                    openedDirectlyToStudent = false
                                    selectedStudentId = null
                                },
                                onSendMessage = { body, onDone ->
                                    onSendMessageToStudent(activeStudent, body, onDone)
                                }
                            )
                        }
                    } else {
                        val myId = currentUser.id.ifBlank { currentUser.userId }
                        val messages by observeThreadMessages(myId)
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        val myConv = conversations.firstOrNull()
                        LaunchedEffect(myId, myConv?.unreadByStudentCount) {
                            if ((myConv?.unreadByStudentCount ?: 0) > 0) {
                                onMarkConversationRead(myId)
                            }
                        }
                        DirectChatPane(
                            currentUser = currentUser,
                            peerTitle = "Bangali Babu (Admin)",
                            peerSubtitle = "Private 1:1 Conversation • Only you and Admin can view these messages",
                            messages = messages,
                            showBackToInboxBar = false,
                            onBackToInbox = {},
                            onSendMessage = onSendMessageToAdmin
                        )
                    }
                }
                1 -> {
                    WarningsPane(
                        isAdmin = isAdmin,
                        students = students,
                        warnings = warnings,
                        preselectedStudentId = selectedStudentId,
                        onSendWarning = onSendWarning,
                        onMarkWarningRead = onMarkWarningRead,
                        onDeleteWarning = onDeleteWarning
                    )
                }
                else -> {
                    AnnouncementsPane(
                        isAdmin = isAdmin,
                        announcements = announcements,
                        lastReadMillis = currentUser.lastAnnouncementReadMillis,
                        onPublishAnnouncement = onPublishAnnouncement,
                        onDeleteAnnouncement = onDeleteAnnouncement
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminInboxListPane(
    students: List<UserEntity>,
    conversations: List<ConversationThread>,
    onSelectStudent: (String) -> Unit
) {
    val ext = LocalTradingColors.current
    var searchQuery by remember { mutableStateOf("") }

    val mergedRows = remember(students, conversations, searchQuery) {
        val convByStudent = conversations.associateBy { it.studentId }
        val knownIds = students.map { it.id }.toSet()
        val extraStudentsFromConvs = conversations
            .filter { it.studentId !in knownIds }
            .map {
                UserEntity(
                    id = it.studentId,
                    userId = it.studentId,
                    name = it.studentName,
                    email = it.studentEmail
                )
            }
        val allStudents = (students + extraStudentsFromConvs).distinctBy { it.id }
        val q = searchQuery.trim()
        val filtered = if (q.isEmpty()) allStudents
        else allStudents.filter {
            it.name.contains(q, ignoreCase = true) || it.email.contains(q, ignoreCase = true)
        }
        filtered
            .map { st -> st to convByStudent[st.id] }
            .sortedWith(
                compareByDescending<Pair<UserEntity, ConversationThread?>> { it.second?.unreadByAdminCount ?: 0 }
                    .thenByDescending { it.second?.lastMessageAtMillis ?: 0L }
                    .thenBy { it.first.name.lowercase(Locale.US) }
            )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    modifier = Modifier.fillMaxWidth().testTag("admin_inbox_search_input")
                )

                Text(
                    text = "Student Direct Messages (${mergedRows.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = ext.gold
                )
            }
        }

        if (mergedRows.isEmpty()) {
            item {
                Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                    EmptyTradingState(
                        title = "No Student Conversations Found",
                        subtitle = "Registered students will appear here so you can send direct messages or reply to inquiries.",
                        icon = Icons.Rounded.Chat
                    )
                }
            }
        } else {
            items(mergedRows, key = { it.first.id }) { (student, conv) ->
                val unread = conv?.unreadByAdminCount ?: 0
                Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStudent(student.id) }
                            .testTag("inbox_student_${student.id}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(
                            width = if (unread > 0) 1.5.dp else 1.dp,
                            color = if (unread > 0) ext.gold else ext.cardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (unread > 0) ext.gold else ext.electricBlue.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = student.name.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (unread > 0) Color(0xFF0A1628) else ext.electricBlue
                                )
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = student.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        StatusBadge(student.statusEnum)
                                    }
                                    if (conv != null && conv.lastMessageAtMillis > 0L) {
                                        Text(
                                            text = formatShortDateTime(conv.lastMessageAtMillis),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (unread > 0) ext.gold else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = student.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (conv != null) {
                                        val prefix = if (conv.lastSenderRoleEnum == UserRole.ADMIN) "You: " else "${student.name}: "
                                        prefix + conv.lastMessageText
                                    } else {
                                        "No messages yet • Tap to send a personal message"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (unread > 0) {
                                Surface(
                                    color = ext.loss,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = unread.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectChatPane(
    currentUser: UserEntity,
    peerTitle: String,
    peerSubtitle: String,
    messages: List<DirectMessageEntity>,
    showBackToInboxBar: Boolean,
    onBackToInbox: () -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit
) {
    val ext = LocalTradingColors.current
    var draft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Privacy & Context Header
        Surface(
            color = ext.cardSurface,
            border = BorderStroke(1.dp, ext.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = ext.gold, modifier = Modifier.size(16.dp))
                    Column {
                        Text(peerTitle, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        Text(peerSubtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showBackToInboxBar) {
                    OutlinedButton(
                        onClick = onBackToInbox,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("All Chats", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Message History
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 1000.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyTradingState(
                        title = "Start Private Conversation",
                        subtitle = if (currentUser.roleEnum == UserRole.ADMIN) {
                            "Send a personalized coaching message, trade review, or feedback directly to $peerTitle."
                        } else {
                            "Ask Bangali Babu (Admin) about trade setups, risk limits, psychology, or account questions."
                        },
                        icon = Icons.Rounded.Chat
                    )
                }
            } else {
                items(messages, key = { it.id.ifBlank { "${it.sentAtMillis}_${it.senderId}" } }) { msg ->
                    val isMine = msg.senderId == currentUser.id || msg.senderRoleEnum == currentUser.roleEnum
                    val isAdminMsg = msg.senderRoleEnum == UserRole.ADMIN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 340.dp),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMine) 16.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 16.dp
                            ),
                            color = when {
                                isMine && isAdminMsg -> Color(0xFF142A4A)
                                isMine -> ext.electricBlue.copy(alpha = if (ext.isDark) 0.28f else 0.16f)
                                isAdminMsg -> ext.goldContainer
                                else -> ext.cardSurface
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isAdminMsg -> ext.gold.copy(alpha = 0.55f)
                                    isMine -> ext.electricBlue.copy(alpha = 0.55f)
                                    else -> ext.cardBorder
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isMine) "You (${msg.senderRoleEnum.label})" else msg.senderName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isAdminMsg) ext.gold else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = msg.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMine && isAdminMsg) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatFullDateTime(msg.effectiveTimestampMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMine && isAdminMsg) Color.White.copy(alpha = 0.72f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Compose Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ext.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 2000) draft = it },
                    placeholder = {
                        Text(
                            if (currentUser.roleEnum == UserRole.ADMIN) "Reply or message student..."
                            else "Write a message to Admin..."
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("comm_chat_input")
                )
                Button(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty() && !isSending) {
                            isSending = true
                            onSendMessage(trimmed) {
                                draft = ""
                                isSending = false
                            }
                        }
                    },
                    enabled = draft.trim().isNotEmpty() && !isSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.gold,
                        contentColor = Color(0xFF0A1628)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp).testTag("comm_chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send Message")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarningsPane(
    isAdmin: Boolean,
    students: List<UserEntity>,
    warnings: List<StudentWarningEntity>,
    preselectedStudentId: String?,
    onSendWarning: (UserEntity, WarningSeverity, String, String, () -> Unit) -> Unit,
    onMarkWarningRead: (StudentWarningEntity) -> Unit,
    onDeleteWarning: (String) -> Unit
) {
    val ext = LocalTradingColors.current
    var dropdownExpanded by remember { mutableStateOf(false) }
    var targetStudent by remember(students, preselectedStudentId) {
        mutableStateOf(
            students.firstOrNull { it.id == preselectedStudentId } ?: students.firstOrNull()
        )
    }
    var severity by remember { mutableStateOf(WarningSeverity.IMPORTANT) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = ext.loss)
                            Text(
                                text = "Send Warning / Alert to Student",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (students.isEmpty()) {
                            Text(
                                text = "No registered students yet. Students appear here after signing in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = targetStudent?.let { "${it.name} (${it.email})" } ?: "Select Student",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Recipient Student") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("warning_student_picker")
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    students.forEach { st ->
                                        DropdownMenuItem(
                                            text = { Text("${st.name} (${st.email})") },
                                            onClick = {
                                                targetStudent = st
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Text("Severity Level", style = MaterialTheme.typography.labelMedium)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WarningSeverity.entries.forEach { sev ->
                                    val sevColor = severityColor(sev)
                                    FilterChip(
                                        selected = severity == sev,
                                        onClick = { severity = sev },
                                        label = { Text(sev.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = sevColor.copy(alpha = 0.20f),
                                            selectedLabelColor = sevColor
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = title,
                                onValueChange = { if (it.length <= 120) title = it },
                                label = { Text("Warning Title (e.g. Overtrading Limit Alert)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("warning_title_input")
                            )

                            OutlinedTextField(
                                value = message,
                                onValueChange = { if (it.length <= 2000) message = it },
                                label = { Text("Detailed Warning / Action Required") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth().testTag("warning_message_input")
                            )

                            GradientButton(
                                text = if (isSubmitting) "Sending..." else "Send Warning Alert",
                                icon = Icons.Rounded.NotificationsActive,
                                enabled = targetStudent != null && title.trim().isNotEmpty() && message.trim().isNotEmpty() && !isSubmitting,
                                colors = listOf(ext.loss, Color(0xFFD97706)),
                                onClick = {
                                    val student = targetStudent ?: return@GradientButton
                                    isSubmitting = true
                                    onSendWarning(student, severity, title, message) {
                                        title = ""
                                        message = ""
                                        isSubmitting = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("send_warning_button")
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (isAdmin) "Sent Student Warnings (${warnings.size})" else "Your Warnings & Risk Alerts (${warnings.size})",
                style = MaterialTheme.typography.titleSmall,
                color = ext.gold,
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()
            )
        }

        if (warnings.isEmpty()) {
            item {
                Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                    EmptyTradingState(
                        title = if (isAdmin) "No Warnings Sent Yet" else "No Active Warnings",
                        subtitle = if (isAdmin) {
                            "Use the form above to send risk management or discipline alerts to an individual student."
                        } else {
                            "Great job maintaining risk discipline! Any personal alerts from Bangali Babu will appear here."
                        },
                        icon = Icons.Rounded.Warning
                    )
                }
            }
        } else {
            items(warnings, key = { it.id.ifBlank { "${it.sentAtMillis}_${it.studentId}" } }) { w ->
                val sevColor = severityColor(w.severityEnum)
                Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("warning_card_${w.id}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isAdmin && !w.readByStudent) sevColor.copy(alpha = 0.08f) else ext.cardSurface
                        ),
                        border = BorderStroke(
                            width = if (!w.readByStudent) 1.5.dp else 1.dp,
                            color = sevColor.copy(alpha = if (!w.readByStudent) 0.7f else 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = sevColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = w.severityEnum.label.uppercase(Locale.US),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = sevColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (!w.readByStudent) {
                                        Surface(color = ext.loss, shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                text = "UNREAD",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(color = ext.profitContainer, shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                text = "ACKNOWLEDGED",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = ext.profit,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = formatFullDateTime(w.effectiveTimestampMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = w.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = w.message,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            HorizontalDivider(color = ext.cardBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAdmin) "To: ${w.studentName}" else "From: ${w.senderAdminName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isAdmin) {
                                    IconButton(onClick = { onDeleteWarning(w.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete warning", tint = ext.loss)
                                    }
                                } else if (!w.readByStudent) {
                                    Button(
                                        onClick = { onMarkWarningRead(w) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = sevColor,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("ack_warning_${w.id}")
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Mark Read")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementsPane(
    isAdmin: Boolean,
    announcements: List<AnnouncementEntity>,
    lastReadMillis: Long,
    onPublishAnnouncement: (String, String, AnnouncementPriority, () -> Unit) -> Unit,
    onDeleteAnnouncement: (String) -> Unit
) {
    val ext = LocalTradingColors.current
    var priority by remember { mutableStateOf(AnnouncementPriority.NORMAL) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.gold.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Campaign, contentDescription = null, tint = ext.gold)
                            Text(
                                text = "Broadcast Announcement to All Students",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnnouncementPriority.entries.forEach { pr ->
                                val col = if (pr == AnnouncementPriority.IMPORTANT) ext.gold else ext.electricBlue
                                FilterChip(
                                    selected = priority == pr,
                                    onClick = { priority = pr },
                                    label = { Text(pr.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = col.copy(alpha = 0.18f),
                                        selectedLabelColor = col
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = title,
                            onValueChange = { if (it.length <= 120) title = it },
                            label = { Text("Announcement Headline") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("announcement_title_input")
                        )

                        OutlinedTextField(
                            value = message,
                            onValueChange = { if (it.length <= 2500) message = it },
                            label = { Text("Announcement Message for All Students") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth().testTag("announcement_message_input")
                        )

                        GradientButton(
                            text = if (isPublishing) "Publishing..." else "Publish Announcement",
                            icon = Icons.Rounded.Campaign,
                            enabled = title.trim().isNotEmpty() && message.trim().isNotEmpty() && !isPublishing,
                            colors = listOf(ext.gold, Color(0xFFD97706)),
                            onClick = {
                                isPublishing = true
                                onPublishAnnouncement(title, message, priority) {
                                    title = ""
                                    message = ""
                                    isPublishing = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("publish_announcement_button")
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Academy Announcements (${announcements.size})",
                style = MaterialTheme.typography.titleSmall,
                color = ext.gold,
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()
            )
        }

        if (announcements.isEmpty()) {
            item {
                Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                    EmptyTradingState(
                        title = "No Announcements Yet",
                        subtitle = if (isAdmin) {
                            "Publish live session reminders, market holiday updates, or academy rules to notify all students."
                        } else {
                            "Broadcast updates from Trading with Bangali Babu will appear here."
                        },
                        icon = Icons.Rounded.Campaign
                    )
                }
            }
        } else {
            items(announcements, key = { it.id.ifBlank { "${it.publishedAtMillis}_${it.title}" } }) { ann ->
                val isImportant = ann.priorityEnum == AnnouncementPriority.IMPORTANT
                val accent = if (isImportant) ext.gold else ext.electricBlue
                val isNewForStudent = !isAdmin && ann.effectiveTimestampMillis > lastReadMillis
                Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("announcement_card_${ann.id}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(1.dp, accent.copy(alpha = if (isImportant || isNewForStudent) 0.65f else 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = accent.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = ann.priorityEnum.label.uppercase(Locale.US),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    RoleBadge(UserRole.ADMIN)
                                }
                                Text(
                                    text = formatFullDateTime(ann.effectiveTimestampMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = ann.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = ann.message,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Posted by ${ann.authorName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isAdmin) {
                                    IconButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete announcement", tint = ext.loss)
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MarkChatRead,
                                        contentDescription = "Announcement viewed",
                                        tint = accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun severityColor(severity: WarningSeverity): Color {
    val ext = LocalTradingColors.current
    return when (severity) {
        WarningSeverity.ADVISORY -> ext.electricBlue
        WarningSeverity.IMPORTANT -> ext.gold
        WarningSeverity.CRITICAL -> ext.loss
    }
}

private fun formatShortDateTime(millis: Long): String {
    if (millis <= 0L) return ""
    val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)
    return fmt.format(Date(millis))
}

private fun formatFullDateTime(millis: Long): String {
    if (millis <= 0L) return "Just now"
    val fmt = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.US)
    return fmt.format(Date(millis))
}
