package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.ReminderRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID).orEmpty()
        when (intent.action) {
            ACTION_SHOW_REMINDER -> {
                if (reminderId.isBlank()) return
                val topic = intent.getStringExtra(NotificationHelper.EXTRA_TOPIC) ?: "Reminder"
                val note = intent.getStringExtra(NotificationHelper.EXTRA_NOTE).orEmpty()
                val category = intent.getStringExtra(NotificationHelper.EXTRA_CATEGORY) ?: "General"
                NotificationHelper.showReminderNotification(
                    context = context,
                    reminderId = reminderId,
                    topic = topic,
                    note = note,
                    category = category
                )
            }

            ACTION_MARK_DONE -> {
                if (reminderId.isBlank()) return
                NotificationHelper.dismissNotification(context, reminderId)
                val uid = Firebase.auth.currentUser?.uid ?: return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = ReminderRepository(context.applicationContext)
                        repo.toggleCompleted(uid, reminderId, completed = true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to mark reminder done from notification", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        const val ACTION_SHOW_REMINDER = "com.example.echonote.ACTION_SHOW_REMINDER"
        const val ACTION_MARK_DONE = "com.example.echonote.ACTION_MARK_DONE"
    }
}
