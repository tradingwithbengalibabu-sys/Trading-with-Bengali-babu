package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GradientButton
import com.example.ui.theme.LocalTradingColors

@Composable
fun AuthScreen(
    hasAdmin: Boolean?,
    isDarkMode: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onToggleTheme: (Boolean) -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onRegisterStudent: (name: String, email: String, password: String) -> Unit,
    onRegisterAdmin: (name: String, email: String, password: String) -> Unit,
    onClearError: () -> Unit
) {
    val ext = LocalTradingColors.current
    var selectedTab by remember(hasAdmin) {
        mutableIntStateOf(if (hasAdmin == false) 2 else 0)
    }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("Bangali Babu") }
    var adminEmail by remember { mutableStateOf("tradingwithbengalibabu@gmail.com") }
    var adminPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ext.heroStart, ext.heroMid, MaterialTheme.colorScheme.background)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        IconButton(
            onClick = { onToggleTheme(!isDarkMode) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .testTag("auth_theme_toggle")
        ) {
            Icon(
                imageVector = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = "Toggle Dark or Light Mode",
                tint = ext.gold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A1628))
                        .border(2.5.dp, ext.gold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.brand_logo),
                        contentDescription = "Trading with Bangali Babu Brand Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TRADING WITH BANGALI BABU",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = ext.gold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Professional Trading Journal & Student Analytics Platform",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val tabs = if (hasAdmin == false) {
                            listOf("Sign In", "Student Sign Up", "Admin Setup")
                        } else {
                            listOf("Sign In", "Student Sign Up")
                        }
                        if (selectedTab >= tabs.size) selectedTab = 0

                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = ext.cardElevated,
                            contentColor = ext.gold,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            tabs.forEachIndexed { idx, title ->
                                Tab(
                                    selected = selectedTab == idx,
                                    onClick = {
                                        selectedTab = idx
                                        onClearError()
                                    },
                                    text = {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                            Surface(
                                color = ext.lossContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.loss,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        when (selectedTab) {
                            0 -> {
                                Text(
                                    text = "Admin & Student Secure Login",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it; onClearError() },
                                    label = { Text("Email Address") },
                                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth().testTag("login_email_input")
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it; onClearError() },
                                    label = { Text("Password") },
                                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("login_password_input")
                                )
                                if (isBusy) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    GradientButton(
                                        text = "Sign In to Platform",
                                        onClick = { onLogin(email, password) },
                                        colors = listOf(ext.electricBlue, ext.purple),
                                        modifier = Modifier.fillMaxWidth().testTag("login_submit_button")
                                    )
                                }
                            }
                            1 -> {
                                Text(
                                    text = "Create Private Student Account",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Your trading journal is strictly isolated and private from other students.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it; onClearError() },
                                    label = { Text("Full Name") },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("register_name_input")
                                )
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it; onClearError() },
                                    label = { Text("Email Address") },
                                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth().testTag("register_email_input")
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it; onClearError() },
                                    label = { Text("Password (min 6 chars)") },
                                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("register_password_input")
                                )
                                if (isBusy) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    GradientButton(
                                        text = "Register as Student",
                                        onClick = { onRegisterStudent(name, email, password) },
                                        colors = listOf(ext.teal, ext.electricBlue),
                                        modifier = Modifier.fillMaxWidth().testTag("register_submit_button")
                                    )
                                }
                            }
                            2 -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = ext.gold)
                                    Text(
                                        text = "First-Time Master Admin Setup",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ext.gold
                                    )
                                }
                                Text(
                                    text = "Initialize your Admin owner account once. You will have full control over students, monitoring, and database backups.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = adminName,
                                    onValueChange = { adminName = it; onClearError() },
                                    label = { Text("Admin Name") },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("admin_setup_name_input")
                                )
                                OutlinedTextField(
                                    value = adminEmail,
                                    onValueChange = { adminEmail = it; onClearError() },
                                    label = { Text("Admin Email") },
                                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth().testTag("admin_setup_email_input")
                                )
                                OutlinedTextField(
                                    value = adminPassword,
                                    onValueChange = { adminPassword = it; onClearError() },
                                    label = { Text("Create Master Password (min 6 chars)") },
                                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("admin_setup_password_input")
                                )
                                if (isBusy) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    GradientButton(
                                        text = "Create Admin Account",
                                        onClick = { onRegisterAdmin(adminName, adminEmail, adminPassword) },
                                        colors = listOf(ext.gold, Color(0xFFD97706)),
                                        modifier = Modifier.fillMaxWidth().testTag("admin_setup_submit_button")
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Security,
                                contentDescription = null,
                                tint = ext.profit,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "Salted PBKDF2 Auth • Role-Isolated Data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
