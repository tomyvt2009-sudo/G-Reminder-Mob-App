package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.VoiceAiProcessor
import com.example.audio.VoiceRecorderManager
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.data.ReminderRepository
import com.example.data.configureAppCheck
import com.example.notifications.NotificationHelper
import com.example.notifications.ReminderScheduler
import com.example.ui.AuthScreen
import com.example.ui.HomeScreen
import com.example.ui.ReminderViewModel
import com.example.ui.theme.EchoNoteTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private var pendingReminderId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)
        NotificationHelper.ensureChannel(this)
        pendingReminderId = intent?.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID)

        enableEdgeToEdge()
        setContent {
            EchoNoteTheme {
                AppNavigation(
                    pendingReminderId = pendingReminderId,
                    onPendingReminderHandled = { pendingReminderId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val reminderId = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID)
        if (!reminderId.isNullOrBlank()) {
            pendingReminderId = reminderId
        }
    }
}

@Composable
fun AppNavigation(
    pendingReminderId: String?,
    onPendingReminderHandled: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            val credentialManager = CredentialManager.create(context)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: ReminderViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        ReminderViewModel(
                            repository = ReminderRepository(db),
                            aiProcessor = VoiceAiProcessor(),
                            recorderManager = VoiceRecorderManager(app.applicationContext),
                            scheduler = ReminderScheduler(app.applicationContext),
                            currentUserId = uid
                        )
                    }
                }
            )
            HomeScreen(
                viewModel = viewModel,
                pendingReminderId = pendingReminderId,
                onPendingReminderHandled = onPendingReminderHandled
            )
        }
    }
}
