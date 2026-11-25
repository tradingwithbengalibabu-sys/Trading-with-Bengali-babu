package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.ui.components.BrandLogoBadge
import com.example.ui.components.GradientButton
import com.example.ui.components.RoleBadge
import com.example.ui.components.StatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.LocalTradingColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val GENDER_OPTIONS = listOf("Male", "Female", "Other", "Prefer not to say")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentProfileScreen(
    viewer: UserEntity,
    targetUser: UserEntity,
    isSaving: Boolean,
    isUploadingPhoto: Boolean,
    onUploadPhoto: (Uri) -> Unit,
    onDeletePhoto: () -> Unit,
    onSaveProfile: (
        fullName: String,
        mobileNumber: String,
        whatsappNumber: String,
        dateOfBirth: String,
        gender: String,
        fullAddress: String,
        editedEmail: String?
    ) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val ext = LocalTradingColors.current
    val isAdminEditingStudent = viewer.roleEnum == UserRole.ADMIN &&
        viewer.id.isNotBlank() &&
        viewer.id != targetUser.id

    var fullName by remember(targetUser.id, targetUser.name) { mutableStateOf(targetUser.name) }
    var mobileNumber by remember(targetUser.id, targetUser.mobileNumber) { mutableStateOf(targetUser.mobileNumber) }
    var whatsappNumber by remember(targetUser.id, targetUser.whatsappNumber) { mutableStateOf(targetUser.whatsappNumber) }
    var emailAddress by remember(targetUser.id, targetUser.email) { mutableStateOf(targetUser.email) }
    var dateOfBirth by remember(targetUser.id, targetUser.dateOfBirth) { mutableStateOf(targetUser.dateOfBirth) }
    var gender by remember(targetUser.id, targetUser.gender) { mutableStateOf(targetUser.gender) }
    var fullAddress by remember(targetUser.id, targetUser.fullAddress) { mutableStateOf(targetUser.fullAddress) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showDobPicker by remember { mutableStateOf(false) }
    var confirmDeletePhoto by remember { mutableStateOf(false) }
    var avatarLoadError by remember(targetUser.profilePhotoUrl) { mutableStateOf<String?>(null) }
    var avatarRetryKey by remember { mutableIntStateOf(0) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadPhoto(uri)
        }
    }

    val utcDateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BrandLogoBadge(size = 34.dp)
                        Column {
                            Text(
                                text = if (isAdminEditingStudent) "Student Profile (Admin)" else "My Student Profile",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Trading with Bangali Babu",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.gold,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (targetUser.hasIncompleteProfile && !isAdminEditingStudent) {
                    Surface(
                        color = ext.goldContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.5.dp, ext.gold.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth().testTag("profile_complete_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ext.gold)
                                Text(
                                    text = "Complete Your Student Profile (${targetUser.profileCompletionPercent}%)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ext.gold
                                )
                            }
                            Text(
                                text = "Add your profile photo and contact details below so your mentor can verify your student account and reach you cleanly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            LinearProgressIndicator(
                                progress = { targetUser.profileCompletionPercent / 100f },
                                color = ext.gold,
                                trackColor = ext.gold.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                        }
                    }
                }

                // Separate Student Profile Photo + Personal Summary Card (Distinct from Brand Logo)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            key(targetUser.profilePhotoUrl, avatarRetryKey) {
                                StudentAvatar(
                                    photoUrl = targetUser.profilePhotoUrl,
                                    name = fullName.ifBlank { targetUser.name },
                                    size = 88.dp,
                                    borderColor = ext.electricBlue,
                                    isLoading = isUploadingPhoto,
                                    showErrorIndicator = true,
                                    onLoadError = { err -> avatarLoadError = err },
                                    modifier = Modifier.testTag("profile_student_avatar")
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = fullName.ifBlank { targetUser.name.ifBlank { "Student" } },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = emailAddress.ifBlank { targetUser.email },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RoleBadge(targetUser.roleEnum)
                                    StatusBadge(targetUser.statusEnum)
                                    if (!targetUser.hasIncompleteProfile) {
                                        Surface(
                                            color = ext.profitContainer,
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = ext.profit,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Verified",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = ext.profit
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "UID: ${targetUser.userId.ifBlank { targetUser.id }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = ext.cardBorder)

                        Text(
                            text = "Profile Photo (Stored securely in Firebase Storage)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = ext.electricBlue
                        )

                        if (targetUser.profilePhotoUrl.isNotBlank() && !avatarLoadError.isNullOrBlank()) {
                            Surface(
                                color = ext.lossContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Could not display uploaded photo: $avatarLoadError",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ext.loss,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        avatarLoadError = null
                                        avatarRetryKey++
                                    }) {
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploadingPhoto && !isSaving,
                                border = BorderStroke(1.dp, ext.electricBlue.copy(alpha = 0.65f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("profile_upload_photo_button")
                            ) {
                                Icon(
                                    Icons.Rounded.PhotoCamera,
                                    contentDescription = null,
                                    tint = ext.electricBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (targetUser.profilePhotoUrl.isNotBlank()) "Change Photo" else "Upload Photo",
                                    maxLines = 1
                                )
                            }

                            if (targetUser.profilePhotoUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { confirmDeletePhoto = true },
                                    enabled = !isUploadingPhoto && !isSaving,
                                    border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.loss),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("profile_delete_photo_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete profile photo",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete Photo", maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Personal Information Form Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = ext.cardSurface),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Personal Details",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ext.gold
                        )

                        AnimatedVisibility(visible = !validationError.isNullOrBlank()) {
                            Surface(
                                color = ext.lossContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ext.loss.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = validationError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.loss,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = fullName,
                            onValueChange = {
                                fullName = it
                                validationError = null
                            },
                            label = { Text("Full Name *") },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                        )

                        OutlinedTextField(
                            value = mobileNumber,
                            onValueChange = {
                                mobileNumber = it
                                validationError = null
                            },
                            label = { Text("Mobile Number *") },
                            placeholder = { Text("+91 9876543210") },
                            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("profile_mobile_input")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = whatsappNumber,
                                onValueChange = {
                                    whatsappNumber = it
                                    validationError = null
                                },
                                label = { Text("WhatsApp Number *") },
                                placeholder = { Text("+91 9876543210") },
                                leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                                trailingIcon = {
                                    if (mobileNumber.isNotBlank() && whatsappNumber != mobileNumber) {
                                        TextButton(
                                            onClick = { whatsappNumber = mobileNumber },
                                            modifier = Modifier.testTag("profile_copy_mobile_to_whatsapp")
                                        ) {
                                            Text("Same", style = MaterialTheme.typography.labelSmall, color = ext.gold)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("profile_whatsapp_input")
                            )
                        }

                        OutlinedTextField(
                            value = emailAddress,
                            onValueChange = {
                                if (isAdminEditingStudent) {
                                    emailAddress = it
                                    validationError = null
                                }
                            },
                            readOnly = !isAdminEditingStudent,
                            enabled = isAdminEditingStudent,
                            label = { Text("Email Address") },
                            supportingText = {
                                Text(
                                    if (isAdminEditingStudent) "Admin can update registered email if needed"
                                    else "Linked to your Google Sign-In account"
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("profile_email_input")
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDobPicker = true }
                        ) {
                            OutlinedTextField(
                                value = dateOfBirth,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("Date of Birth (YYYY-MM-DD) *") },
                                placeholder = { Text("Tap to select Date of Birth") },
                                leadingIcon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("profile_dob_input")
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Gender *",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GENDER_OPTIONS.forEach { option ->
                                    val selected = gender == option
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            gender = option
                                            validationError = null
                                        },
                                        label = { Text(option) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ext.gold.copy(alpha = 0.22f),
                                            selectedLabelColor = ext.gold
                                        ),
                                        modifier = Modifier.testTag("profile_gender_${option.lowercase(Locale.US).replace(' ', '_')}")
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = fullAddress,
                            onValueChange = {
                                fullAddress = it
                                validationError = null
                            },
                            label = { Text("Full Address *") },
                            placeholder = { Text("House/Flat No, Street, City, State, PIN Code") },
                            leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("profile_address_input")
                        )

                        Spacer(Modifier.height(4.dp))

                        GradientButton(
                            text = when {
                                isSaving -> "Saving Profile..."
                                targetUser.hasIncompleteProfile -> "Complete & Save Profile"
                                else -> "Save Profile Changes"
                            },
                            icon = Icons.Rounded.Save,
                            enabled = !isSaving && !isUploadingPhoto,
                            colors = listOf(ext.gold, Color(0xFFD97706)),
                            onClick = {
                                val err = validateProfileInputs(
                                    fullName = fullName,
                                    mobileNumber = mobileNumber,
                                    whatsappNumber = whatsappNumber,
                                    dateOfBirth = dateOfBirth,
                                    gender = gender,
                                    fullAddress = fullAddress
                                )
                                if (err != null) {
                                    validationError = err
                                } else {
                                    onSaveProfile(
                                        fullName.trim(),
                                        mobileNumber.trim(),
                                        whatsappNumber.trim(),
                                        dateOfBirth.trim(),
                                        gender,
                                        fullAddress.trim(),
                                        if (isAdminEditingStudent) emailAddress.trim() else null
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_save_button")
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDobPicker) {
        val initialMillis = remember(dateOfBirth) {
            runCatching {
                if (dateOfBirth.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                    utcDateFormat.parse(dateOfBirth)?.time
                } else null
            }.getOrNull() ?: 946684800000L // 2000-01-01 default
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDobPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dateOfBirth = utcDateFormat.format(Date(millis))
                        validationError = null
                    }
                    showDobPicker = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDobPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (confirmDeletePhoto) {
        AlertDialog(
            onDismissRequest = { confirmDeletePhoto = false },
            title = { Text("Remove Profile Photo?") },
            text = { Text("Your uploaded profile photo will be deleted from Firebase Storage and replaced with your initials avatar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeletePhoto = false
                    onDeletePhoto()
                }) { Text("Delete Photo", color = ext.loss) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePhoto = false }) { Text("Cancel") }
            }
        )
    }
}

private fun validateProfileInputs(
    fullName: String,
    mobileNumber: String,
    whatsappNumber: String,
    dateOfBirth: String,
    gender: String,
    fullAddress: String
): String? {
    if (fullName.trim().length < 2) return "Full Name must be at least 2 characters."
    if (mobileNumber.count { it.isDigit() } !in 7..15) return "Enter a valid Mobile Number (7–15 digits)."
    if (whatsappNumber.count { it.isDigit() } !in 7..15) return "Enter a valid WhatsApp Number (7–15 digits)."
    if (!dateOfBirth.trim().matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return "Please select your Date of Birth."
    if (gender !in GENDER_OPTIONS) return "Please select your Gender."
    if (fullAddress.trim().length < 5) return "Full Address must be at least 5 characters."
    return null
}
