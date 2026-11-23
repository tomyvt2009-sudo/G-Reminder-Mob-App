package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOut
import com.example.data.Reminder
import com.example.data.ReminderRepository
import com.example.data.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun HomeScreen(
    viewModel: ReminderViewModel,
    pendingReminderId: String?,
    onPendingReminderHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()
    val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
    val typedDraft by viewModel.typedDraft.collectAsStateWithLifecycle()
    val autoSaveBanner by viewModel.autoSaveBanner.collectAsStateWithLifecycle()
    val capBlockedState by viewModel.capBlockedState.collectAsStateWithLifecycle()
    val editingReminder by viewModel.editingReminder.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    var showCompletedSection by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var permissionHint by remember { mutableStateOf<String?>(null) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionHint = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.startVoiceCapture()
        } else {
            permissionHint = "Microphone permission is needed for voice notes. You can still type notes below."
        }
    }

    LaunchedEffect(pendingReminderId) {
        if (!pendingReminderId.isNullOrBlank()) {
            viewModel.openReminderById(pendingReminderId)
            onPendingReminderHandled()
        }
    }

    fun onMicTap() {
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (audioGranted) {
            permissionHint = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.startVoiceCapture()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val currentUser = Firebase.auth.currentUser
    val userInitial = (currentUser?.displayName ?: currentUser?.email ?: "E")
        .firstOrNull()?.uppercaseChar()?.toString() ?: "E"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                QuickCaptureDock(
                    captureMode = captureMode,
                    typedDraft = typedDraft,
                    onMicTap = ::onMicTap,
                    onStopRecording = viewModel::stopAndProcessVoiceCapture,
                    onCancelRecording = viewModel::cancelVoiceCapture,
                    onOpenTyping = { viewModel.openTypingMode() },
                    onCloseTyping = viewModel::closeTypingMode,
                    onTypedDraftChange = viewModel::updateTypedDraft,
                    onSubmitTyped = viewModel::submitTypedInstruction,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                )
            }
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
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                val reminderCount = (remindersState as? UiState.Success)?.data?.size ?: 0

                TopHeaderBar(
                    reminderCount = reminderCount,
                    userInitial = userInitial,
                    userEmail = currentUser?.email.orEmpty(),
                    showAccountMenu = showAccountMenu,
                    onToggleAccountMenu = { showAccountMenu = !showAccountMenu },
                    onDismissAccountMenu = { showAccountMenu = false },
                    onSignOut = {
                        showAccountMenu = false
                        signOut(credentialManager, scope)
                    }
                )

                // Notification permission nudge if denied on Android 13+
                AnimatedVisibility(visible = !hasNotificationPermission && reminderCount > 0) {
                    NotificationPermissionNudge(
                        onEnableClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                // Inline error or permission hint
                AnimatedVisibility(
                    visible = !actionError.isNullOrBlank() || !permissionHint.isNullOrBlank(),
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val message = actionError ?: permissionHint.orEmpty()
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .testTag("error_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    viewModel.clearError()
                                    permissionHint = null
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss message",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Auto-Save Confirmation Banner with Undo & Edit
                AnimatedVisibility(
                    visible = autoSaveBanner != null,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    autoSaveBanner?.let { banner ->
                        AutoSaveConfirmationBanner(
                            banner = banner,
                            onEdit = {
                                viewModel.dismissBanner()
                                viewModel.openEditSheet(banner.reminder)
                            },
                            onUndo = viewModel::undoLastSaved,
                            onDismiss = viewModel::dismissBanner
                        )
                    }
                }

                when (val state = remindersState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Sync Interrupted",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    is UiState.Success -> {
                        val allReminders = state.data
                        val upcoming = remember(allReminders) { allReminders.filter { !it.completed } }
                        val completed = remember(allReminders) { allReminders.filter { it.completed } }

                        if (allReminders.isEmpty()) {
                            EmptyRemindersState(
                                onExampleTap = { example ->
                                    viewModel.openTypingMode(example)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 8.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (upcoming.isNotEmpty()) {
                                    item(key = "header_upcoming") {
                                        SectionHeader(
                                            title = stringResource(R.string.home_section_upcoming),
                                            count = upcoming.size
                                        )
                                    }
                                    items(
                                        items = upcoming,
                                        key = { it.id }
                                    ) { reminder ->
                                        ReminderCard(
                                            reminder = reminder,
                                            onToggleComplete = { viewModel.toggleCompleted(reminder) },
                                            onClick = { viewModel.openEditSheet(reminder) },
                                            onDelete = { viewModel.deleteReminder(reminder) }
                                        )
                                    }
                                }

                                if (completed.isNotEmpty()) {
                                    item(key = "header_completed") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showCompletedSection = !showCompletedSection }
                                                .padding(top = 12.dp, bottom = 4.dp)
                                                .testTag("completed_section_toggle"),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SectionHeader(
                                                title = stringResource(R.string.home_section_completed),
                                                count = completed.size
                                            )
                                            Icon(
                                                imageVector = if (showCompletedSection) {
                                                    Icons.Outlined.ExpandLess
                                                } else {
                                                    Icons.Outlined.ExpandMore
                                                },
                                                contentDescription = "Toggle completed reminders",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (showCompletedSection) {
                                        items(
                                            items = completed,
                                            key = { it.id }
                                        ) { reminder ->
                                            ReminderCard(
                                                reminder = reminder,
                                                onToggleComplete = { viewModel.toggleCompleted(reminder) },
                                                onClick = { viewModel.openEditSheet(reminder) },
                                                onDelete = { viewModel.deleteReminder(reminder) }
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
    }

    editingReminder?.let { reminder ->
        ReminderEditSheet(
            reminder = reminder,
            onDismiss = viewModel::closeEditSheet,
            onSave = viewModel::saveEditedReminder,
            onDelete = viewModel::deleteReminder
        )
    }

    capBlockedState?.let { blocked ->
        CapReachedDialog(
            state = blocked,
            onReplaceOldest = viewModel::confirmReplaceOldestActive,
            onDismiss = viewModel::dismissCapBlocked
        )
    }
}

@Composable
private fun TopHeaderBar(
    reminderCount: Int,
    userInitial: String,
    userEmail: String,
    showAccountMenu: Boolean,
    onToggleAccountMenu: () -> Unit,
    onDismissAccountMenu: () -> Unit,
    onSignOut: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EchoNoteMark(modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val nearCap = reminderCount >= ReminderRepository.MAX_REMINDERS - 2
            Surface(
                color = if (nearCap) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .testTag("capacity_pill")
            ) {
                Text(
                    text = "$reminderCount / ${ReminderRepository.MAX_REMINDERS}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nearCap) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Box {
                Surface(
                    onClick = onToggleAccountMenu,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .testTag("account_menu_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userInitial,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                DropdownMenu(
                    expanded = showAccountMenu,
                    onDismissRequest = onDismissAccountMenu
                ) {
                    if (userEmail.isNotBlank()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_sign_out)) },
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionNudge(onEnableClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Allow notifications so scheduled alerts can chime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            TextButton(onClick = onEnableClick) {
                Text("Allow")
            }
        }
    }
}

@Composable
private fun AutoSaveConfirmationBanner(
    banner: AutoSaveBanner,
    onEdit: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.medium
            )
            .testTag("auto_save_banner")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = banner.reminder.topic,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("banner_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_edit))
                    }
                    TextButton(
                        onClick = onUndo,
                        modifier = Modifier.testTag("banner_undo_button")
                    ) {
                        Text(stringResource(R.string.action_undo))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Dismiss banner",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            val timeSummary = if (banner.reminder.scheduledAtEpochMillis != null) {
                "Alert scheduled • ${formatScheduledTime(banner.reminder.scheduledAtEpochMillis)}"
            } else {
                "Saved as organized note (no alert time detected)"
            }
            Text(
                text = timeSummary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
            if (!banner.evictedCompletedTopic.isNullOrBlank()) {
                Text(
                    text = "Archived completed note \"${banner.evictedCompletedTopic}\" to keep 20-note limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = reminder.completed
    val isOverdue = !isDone &&
        reminder.scheduledAtEpochMillis != null &&
        reminder.scheduledAtEpochMillis < System.currentTimeMillis()

    Surface(
        onClick = onClick,
        color = if (isDone) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium
            )
            .testTag("reminder_card_${reminder.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_complete_${reminder.id}")
            ) {
                Icon(
                    imageVector = if (isDone) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = if (isDone) "Mark incomplete" else "Mark completed",
                    tint = if (isDone) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = reminder.topic,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isDone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (reminder.organizedNote.isNotBlank()) {
                    Text(
                        text = reminder.organizedNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Text(
                            text = reminder.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (reminder.scheduledAtEpochMillis != null) {
                        Surface(
                            color = when {
                                isDone -> MaterialTheme.colorScheme.surfaceVariant
                                isOverdue -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = CircleShape
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDone) {
                                        Icons.Outlined.Schedule
                                    } else {
                                        Icons.Outlined.NotificationsActive
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                        isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatScheduledTime(reminder.scheduledAtEpochMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                        isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_card_${reminder.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete reminder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyRemindersState(
    onExampleTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EchoNoteMark(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            onClick = {
                onExampleTap("Remind me in 30 minutes to review the quarterly budget and email Sarah")
            },
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                .testTag("empty_example_chip")
        ) {
            Text(
                text = "Try: \"Remind me in 30 minutes to review the quarterly budget…\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun QuickCaptureDock(
    captureMode: CaptureMode,
    typedDraft: String,
    onMicTap: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onOpenTyping: () -> Unit,
    onCloseTyping: () -> Unit,
    onTypedDraftChange: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.extraLarge
            )
    ) {
        AnimatedContent(
            targetState = captureMode,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessMedium))
            },
            contentKey = { it::class },
            label = "QuickCaptureDock"
        ) { mode ->
            when (mode) {
                is CaptureMode.Idle -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onOpenTyping,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("keyboard_entry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Keyboard,
                                contentDescription = "Type reminder instead",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = stringResource(R.string.home_mic_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onMicTap() }
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onMicTap)
                                .testTag("mic_capture_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Start voice recording",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                is CaptureMode.Recording -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onCancelRecording,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("cancel_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cancel recording",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LiveWaveformView(
                            amplitudes = mode.amplitudes,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        )

                        Text(
                            text = formatElapsedSeconds(mode.elapsedSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Button(
                            onClick = onStopRecording,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("stop_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done")
                        }
                    }
                }

                is CaptureMode.Processing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.home_processing_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is CaptureMode.Typing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onCloseTyping,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("switch_to_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Switch back to voice mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = typedDraft,
                            onValueChange = onTypedDraftChange,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.home_type_placeholder),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSubmitTyped() }),
                            shape = MaterialTheme.shapes.large,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("typed_instruction_input")
                        )

                        IconButton(
                            onClick = onSubmitTyped,
                            enabled = typedDraft.isNotBlank(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(
                                    if (typedDraft.isNotBlank()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .testTag("submit_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Organize and save instruction",
                                tint = if (typedDraft.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveWaveformView(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (amplitudes.isEmpty()) return@Canvas
        val barCount = amplitudes.size
        val slotWidth = size.width / barCount
        val barWidth = (slotWidth * 0.55f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        amplitudes.forEachIndexed { index, amp ->
            val clamped = amp.coerceIn(0.08f, 1f)
            val barHeight = (size.height * clamped).coerceAtLeast(6f)
            val left = index * slotWidth + (slotWidth - barWidth) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
