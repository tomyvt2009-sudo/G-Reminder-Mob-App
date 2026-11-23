package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.Reminder

class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun scheduleReminder(reminder: Reminder) {
        if (reminder.id.isBlank()) return
        val scheduledAt = reminder.scheduledAtEpochMillis
        if (reminder.completed || scheduledAt == null || scheduledAt <= System.currentTimeMillis()) {
            cancelReminder(reminder.id)
            return
        }

        val manager = alarmManager ?: return
        val pendingIntent = buildAlarmPendingIntent(
            reminderId = reminder.id,
            topic = reminder.topic,
            note = reminder.organizedNote,
            category = reminder.category
        )

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                manager.canScheduleExactAlarms()

            if (canExact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledAt,
                    pendingIntent
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledAt,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alert for '${reminder.topic}' at $scheduledAt (exact=$canExact)")
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied; falling back to inexact alarm", se)
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledAt,
                pendingIntent
            )
        }
    }

    fun cancelReminder(reminderId: String) {
        if (reminderId.isBlank()) return
        val manager = alarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_SHOW_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            manager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        NotificationHelper.dismissNotification(context, reminderId)
    }

    fun syncAllReminders(reminders: List<Reminder>) {
        reminders.forEach { reminder ->
            scheduleReminder(reminder)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() ?: false
    }

    private fun buildAlarmPendingIntent(
        reminderId: String,
        topic: String,
        note: String,
        category: String
    ): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_SHOW_REMINDER
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationHelper.EXTRA_TOPIC, topic)
            putExtra(NotificationHelper.EXTRA_NOTE, note)
            putExtra(NotificationHelper.EXTRA_CATEGORY, category)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val TAG = "ReminderScheduler"
    }
}
