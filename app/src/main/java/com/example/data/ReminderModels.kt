package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ReminderCategory(val label: String) {
    Work("Work"),
    Personal("Personal"),
    Errand("Errand"),
    Health("Health"),
    Finance("Finance"),
    General("General");

    companion object {
        fun fromRaw(raw: String?): ReminderCategory {
            if (raw.isNullOrBlank()) return General
            return entries.firstOrNull { it.label.equals(raw.trim(), ignoreCase = true) } ?: General
        }
    }
}

@Keep
@IgnoreExtraProperties
data class Reminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val topic: String = "",
    val organizedNote: String = "",
    val rawTranscript: String = "",
    val category: String = ReminderCategory.General.label,
    val scheduledAtEpochMillis: Long? = null,
    val completed: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: ReminderCategory
        get() = ReminderCategory.fromRaw(category)
}

data class ParsedVoiceReminder(
    val topic: String,
    val organizedNote: String,
    val rawTranscript: String,
    val category: ReminderCategory,
    val scheduledAtEpochMillis: Long?
)

sealed interface CapResolution {
    data class Saved(
        val reminder: Reminder,
        val evictedCompletedTopic: String? = null
    ) : CapResolution

    data class CapBlocked(
        val draft: ParsedVoiceReminder,
        val oldestActiveReminder: Reminder
    ) : CapResolution
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
