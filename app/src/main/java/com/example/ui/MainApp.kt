package com.example.ui

import android.app.Application
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.SessionPreferences
import com.example.data.model.AccountStatus
import com.example.data.model.TradeEntity
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.ui.components.BrandLogoBadge
import com.example.ui.screens.AdminStudentsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DisciplineScreen
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.StudentDetailScreen
import com.example.ui.screens.TradeEntryScreen
import com.example.ui.theme.BangaliBabuTheme
import com.example.ui.theme.LocalTradingColors
import com.example.ui.viewmodel.TradingViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_JOURNAL = "journal"
const val ROUTE_DISCIPLINE = "discipline"
const val ROUTE_REPORTS = "reports"
const val ROUTE_STUDENTS = "students"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun TradingAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionPreferences = remember(context) { SessionPreferences(context.applicationContext) }
    val isDarkMode by sessionPreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentFirebaseUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentFirebaseUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    BangaliBabuTheme(darkTheme = isDarkMode) {
        val fbUser = currentFirebaseUser
        if (fbUser == null) {
            AuthScreen(
                isDarkMode = isDarkMode,
                onToggleTheme = { dark -> scope.launch { sessionPreferences.setDarkMode(dark) } },
                onAuthSuccess = { currentFirebaseUser = Firebase.auth.currentUser }
            )
        } else {
            val uid = fbUser.uid
            val db = remember(context) {
                FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
            }
            key(uid) {
                val viewModel: TradingViewModel = viewModel(
                    key = uid,
                    factory = TradingViewModel.provideFactory(
                        application = context.applicationContext as Application,
                        db = db,
                        uid = uid
                    )
                )
                AuthenticatedGate(
                    uid = uid,
                    isDarkMode = isDarkMode,
                    viewModel = viewModel,
                    onSignOut = {
                        signOutWithCleanup(credentialManager, scope) {
                            currentFirebaseUser = null
                        }
                    }
                )
            }
        }
    }
}

private fun signOutWithCleanup(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onComplete: () -> Unit
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onComplete()
            }
        }
    }
}

@Composable
private fun AuthenticatedGate(
    uid: String,
    isDarkMode: Boolean,
    viewModel: TradingViewModel,
    onSignOut: () -> Unit
) {
    val ext = LocalTradingColors.current
    val userState by viewModel.userState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, uid) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val state = userState) {
        UiState.Loading -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        BrandLogoBadge(size = 76.dp)
                        CircularProgressIndicator(color = ext.gold)
                        Text("Syncing cloud trading profile...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        is UiState.Error -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                        border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BrandLogoBadge(size = 64.dp)
                            Text("Unable to Load Profile", style = MaterialTheme.typography.titleLarge)
                            Text(state.message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = ext.loss)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = viewModel::retryProfileBootstrap) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry")
                                }
                                OutlinedButton(onClick = onSignOut) {
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }
            }
        }
        is UiState.Success -> {
            val user = state.data
            if (user.statusEnum == AccountStatus.SUSPENDED) {
                SuspendedAccountScreen(user = user, onSignOut = onSignOut)
            } else {
                AuthenticatedWorkspace(
                    user = user,
                    isDarkMode = isDarkMode,
                    snackbarHostState = snackbarHostState,
                    viewModel = viewModel,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun SuspendedAccountScreen(
    user: UserEntity,
    onSignOut: () -> Unit
) {
    val ext = LocalTradingColors.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = "Suspended",
                        tint = ext.loss,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "Account Suspended",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = ext.loss
                    )
                    Text(
                        text = "${user.name} (${user.email}), your Trading with Bangali Babu student access has been suspended by the Admin. Please contact your mentor to restore access.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWorkspace(
    user: UserEntity,
    isDarkMode: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: TradingViewModel,
    onSignOut: () -> Unit
) {
    val ext = LocalTradingColors.current
    val personalTrades by viewModel.personalTrades.collectAsStateWithLifecycle()
    val personalMetrics by viewModel.personalMetrics.collectAsStateWithLifecycle()
    val personalDiscipline by viewModel.personalDiscipline.collectAsStateWithLifecycle()
    val personalReviews by viewModel.personalReviews.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val pendingInvites by viewModel.pendingStudentInvites.collectAsStateWithLifecycle()
    val allPlatformTrades by viewModel.allPlatformTrades.collectAsStateWithLifecycle()
    val studentSummaries by viewModel.studentPerformanceSummaries.collectAsStateWithLifecycle()
    val combinedStudentsMetrics by viewModel.combinedStudentsMetrics.collectAsStateWithLifecycle()

    var showTradeEditor by remember { mutableStateOf(false) }
    var editingTrade by remember { mutableStateOf<TradeEntity?>(null) }
    var inspectedStudentId by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    if (showTradeEditor) {
        TradeEntryScreen(
            user = user,
            editingTrade = editingTrade,
            onEncodeScreenshot = viewModel::encodeScreenshot,
            onSave = { existingId, dateMillis, dateIso, instrument, dir, tf, ep, xp, sl, tp, lot, risk, pnl, res, strat, entryR, exitR, mistakes, lessons, shotData, notes ->
                viewModel.saveTrade(
                    existingId, dateMillis, dateIso, instrument, dir, tf,
                    ep, xp, sl, tp, lot, risk, pnl, res,
                    strat, entryR, exitR, mistakes, lessons, shotData, notes
                ) {
                    showTradeEditor = false
                    editingTrade = null
                }
            },
            onCancel = {
                showTradeEditor = false
                editingTrade = null
            }
        )
        return
    }

    if (user.roleEnum == UserRole.ADMIN && inspectedStudentId != null) {
        val summary = studentSummaries.firstOrNull { it.student.id == inspectedStudentId }
        if (summary != null) {
            val stTrades = remember(allPlatformTrades, summary.student.id) {
                allPlatformTrades.filter { it.userId == summary.student.id }
            }
            val stReviews by viewModel.observeStudentReviews(summary.student.id)
                .collectAsStateWithLifecycle(initialValue = emptyList())

            StudentDetailScreen(
                summary = summary,
                studentTrades = stTrades,
                studentReviews = stReviews,
                onBack = { inspectedStudentId = null }
            )
            return
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

    val destinations = remember(user.roleEnum) {
        buildList {
            add(NavDestination(ROUTE_DASHBOARD, "Dashboard", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard))
            add(NavDestination(ROUTE_JOURNAL, "Journal", Icons.Rounded.Book, Icons.Outlined.Book))
            add(NavDestination(ROUTE_DISCIPLINE, "Discipline", Icons.Rounded.Psychology, Icons.Outlined.Psychology))
            add(NavDestination(ROUTE_REPORTS, "Reports", Icons.Rounded.Assessment, Icons.Outlined.Assessment))
            if (user.roleEnum == UserRole.ADMIN) {
                add(NavDestination(ROUTE_STUDENTS, "Students", Icons.Rounded.Groups, Icons.Outlined.Groups))
            }
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandLogoBadge(size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Trading with Bangali Babu",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${user.name} (${user.roleEnum.label})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.gold,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleDarkMode(!isDarkMode) },
                            modifier = Modifier.testTag("top_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Switch Theme",
                                tint = ext.gold
                            )
                        }
                        IconButton(
                            onClick = { confirmLogout = true },
                            modifier = Modifier.testTag("top_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ext.gold,
                                    selectedTextColor = ext.gold,
                                    indicatorColor = ext.gold.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.padding(top = 8.dp))
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ext.gold,
                                    selectedTextColor = ext.gold,
                                    indicatorColor = ext.gold.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                user = user,
                                metrics = personalMetrics,
                                discipline = personalDiscipline,
                                onAddTradeClick = {
                                    editingTrade = null
                                    showTradeEditor = true
                                }
                            )
                        }
                        composable(ROUTE_JOURNAL) {
                            JournalScreen(
                                trades = personalTrades,
                                onAddTrade = {
                                    editingTrade = null
                                    showTradeEditor = true
                                },
                                onEditTrade = { trade ->
                                    editingTrade = trade
                                    showTradeEditor = true
                                },
                                onDeleteTrade = viewModel::deleteTrade
                            )
                        }
                        composable(ROUTE_DISCIPLINE) {
                            DisciplineScreen(
                                user = user,
                                discipline = personalDiscipline,
                                reviews = personalReviews,
                                onUpdateGoals = viewModel::updateOwnDisciplineGoals,
                                onSaveReview = viewModel::saveDisciplineReview
                            )
                        }
                        composable(ROUTE_REPORTS) {
                            ReportsScreen(
                                currentUser = user,
                                personalTrades = personalTrades,
                                students = students,
                                allPlatformTrades = allPlatformTrades,
                                onExportBackupJson = viewModel::exportBackupJson,
                                onRestoreBackupJson = viewModel::restoreBackupJson,
                                onShowMessage = viewModel::emitMessage
                            )
                        }
                        if (user.roleEnum == UserRole.ADMIN) {
                            composable(ROUTE_STUDENTS) {
                                AdminStudentsScreen(
                                    summaries = studentSummaries,
                                    pendingInvites = pendingInvites,
                                    combinedMetrics = combinedStudentsMetrics,
                                    onOpenStudentDetail = { studentId -> inspectedStudentId = studentId },
                                    onCreateInvite = viewModel::createStudentInvite,
                                    onUpdateInvite = viewModel::updateStudentInvite,
                                    onDeleteInvite = viewModel::deleteStudentInvite,
                                    onUpdateStudent = viewModel::updateStudent,
                                    onToggleSuspend = viewModel::toggleStudentSuspension,
                                    onDeleteStudent = viewModel::deleteStudent
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign Out?") },
            text = { Text("Your trades, screenshots, reviews, and discipline data remain securely synced in Firebase Cloud Firestore.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onSignOut()
                }) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            }
        )
    }
}
