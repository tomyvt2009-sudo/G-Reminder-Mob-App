package com.example.notifications

import android.app.AlarmManager
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

class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            return
        }

        val uid = Firebase.auth.currentUser?.uid ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ReminderRepository(context.applicationContext)
                val scheduler = ReminderScheduler(context.applicationContext)
                repo.fetchRemindersOnce(uid).onSuccess { reminders ->
                    scheduler.syncAllReminders(reminders)
                    Log.d(TAG, "Rescheduled ${reminders.size} reminders on $action")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule reminders after $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootRescheduleReceiver"
    }
}
