package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.VoiceAiProcessor
import com.example.audio.VoiceRecorderManager
import com.example.data.CapResolution
import com.example.data.ParsedVoiceReminder
import com.example.data.Reminder
import com.example.data.ReminderCategory
import com.example.data.ReminderRepository
import com.example.data.UiState
import com.example.notifications.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CaptureMode {
    data object Idle : CaptureMode
    data class Recording(
        val elapsedSeconds: Int = 0,
        val amplitudes: List<Float> = List(WAVEFORM_BARS) { 0.1f }
    ) : CaptureMode

    data object Processing : CaptureMode
    data object Typing : CaptureMode
}

data class AutoSaveBanner(
    val reminder: Reminder,
    val evictedCompletedTopic: String? = null
)

data class CapBlockedDialogState(
    val draft: ParsedVoiceReminder,
    val oldestActiveReminder: Reminder
)

private const val WAVEFORM_BARS = 28

class ReminderViewModel(
    private val repository: ReminderRepository,
    private val aiProcessor: VoiceAiProcessor,
    private val recorderManager: VoiceRecorderManager,
    private val scheduler: ReminderScheduler,
    private val currentUserId: String
) : ViewModel() {

    val remindersState: StateFlow<UiState<List<Reminder>>> =
        repository.observeReminders(currentUserId)
            .onEach { reminders ->
                scheduler.syncAllReminders(reminders)
            }
            .map<List<Reminder>, UiState<List<Reminder>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing reminders", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to sync reminders"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _captureMode = MutableStateFlow<CaptureMode>(CaptureMode.Idle)
    val captureMode: StateFlow<CaptureMode> = _captureMode.asStateFlow()

    private val _typedDraft = MutableStateFlow("")
    val typedDraft: StateFlow<String> = _typedDraft.asStateFlow()

    private val _autoSaveBanner = MutableStateFlow<AutoSaveBanner?>(null)
    val autoSaveBanner: StateFlow<AutoSaveBanner?> = _autoSaveBanner.asStateFlow()

    private val _capBlockedState = MutableStateFlow<CapBlockedDialogState?>(null)
    val capBlockedState: StateFlow<CapBlockedDialogState?> = _capBlockedState.asStateFlow()

    private val _editingReminder = MutableStateFlow<Reminder?>(null)
    val editingReminder: StateFlow<Reminder?> = _editingReminder.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var recordingJob: Job? = null
    private var bannerDismissJob: Job? = null

    fun startVoiceCapture() {
        if (_captureMode.value is CaptureMode.Recording || _captureMode.value is CaptureMode.Processing) {
            return
        }
        _actionError.value = null
        val started = recorderManager.startRecording()
        if (!started) {
            _actionError.value = "Unable to access microphone. Please check audio permissions."
            return
        }

        _captureMode.value = CaptureMode.Recording(
            elapsedSeconds = 0,
            amplitudes = List(WAVEFORM_BARS) { 0.1f }
        )

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            val startMillis = System.currentTimeMillis()
            while (isActive) {
                delay(75L)
                val elapsedSec = ((System.currentTimeMillis() - startMillis) / 1000L).toInt()
                val amp = recorderManager.sampleNormalizedAmplitude()
                _captureMode.update { mode ->
                    if (mode is CaptureMode.Recording) {
                        mode.copy(
                            elapsedSeconds = elapsedSec,
                            amplitudes = (mode.amplitudes.drop(1) + amp)
                        )
                    } else {
                        mode
                    }
                }
            }
        }
    }

    fun stopAndProcessVoiceCapture() {
        if (_captureMode.value !is CaptureMode.Recording) return
        recordingJob?.cancel()
        recordingJob = null

        val recorded = recorderManager.stopRecording()
        if (recorded == null || recorded.durationMillis < 350L) {
            recorded?.file?.delete()
            _captureMode.value = CaptureMode.Idle
            _actionError.value = "Recording was too short. Tap the mic and speak clearly."
            return
        }

        _captureMode.value = CaptureMode.Processing
        viewModelScope.launch {
            try {
                val audioBytes = withContext(Dispatchers.IO) {
                    try {
                        recorded.file.readBytes()
                    } finally {
                        recorded.file.delete()
                    }
                }

                val aiResult = aiProcessor.processVoiceAudio(audioBytes)
                aiResult.fold(
                    onSuccess = { parsed ->
                        saveParsedDraft(parsed, forceReplaceOldestActive = false)
                    },
                    onFailure = { error ->
                        _actionError.value = error.localizedMessage
                            ?: "Could not process voice note. Please try again."
                    }
                )
            } finally {
                _captureMode.value = CaptureMode.Idle
            }
        }
    }

    fun cancelVoiceCapture() {
        recordingJob?.cancel()
        recordingJob = null
        recorderManager.cancelRecording()
        _captureMode.value = CaptureMode.Idle
    }

    fun openTypingMode(prefill: String = "") {
        if (_captureMode.value is CaptureMode.Recording) {
            cancelVoiceCapture()
        }
        if (prefill.isNotBlank()) {
            _typedDraft.value = prefill
        }
        _captureMode.value = CaptureMode.Typing
    }

    fun closeTypingMode() {
        _captureMode.value = CaptureMode.Idle
    }

    fun updateTypedDraft(value: String) {
        _typedDraft.value = value
    }

    fun submitTypedInstruction() {
        val text = _typedDraft.value.trim()
        if (text.isBlank() || _captureMode.value is CaptureMode.Processing) return

        _actionError.value = null
        _captureMode.value = CaptureMode.Processing
        viewModelScope.launch {
            try {
                val aiResult = aiProcessor.processTextInstruction(text)
                aiResult.fold(
                    onSuccess = { parsed ->
                        _typedDraft.value = ""
                        saveParsedDraft(parsed, forceReplaceOldestActive = false)
                    },
                    onFailure = { error ->
                        _actionError.value = error.localizedMessage
                            ?: "Could not organize note. Please try again."
                    }
                )
            } finally {
                _captureMode.value = CaptureMode.Idle
            }
        }
    }

    private suspend fun saveParsedDraft(
        draft: ParsedVoiceReminder,
        forceReplaceOldestActive: Boolean
    ) {
        repository.saveParsedReminder(
            userId = currentUserId,
            draft = draft,
            forceReplaceOldestActive = forceReplaceOldestActive
        ).fold(
            onSuccess = { resolution ->
                when (resolution) {
                    is CapResolution.Saved -> {
                        scheduler.scheduleReminder(resolution.reminder)
                        showAutoSaveBanner(
                            AutoSaveBanner(
                                reminder = resolution.reminder,
                                evictedCompletedTopic = resolution.evictedCompletedTopic
                            )
                        )
                    }

                    is CapResolution.CapBlocked -> {
                        _capBlockedState.value = CapBlockedDialogState(
                            draft = resolution.draft,
                            oldestActiveReminder = resolution.oldestActiveReminder
                        )
                    }
                }
            },
            onFailure = { error ->
                _actionError.value = error.localizedMessage ?: "Failed to save reminder to cloud."
            }
        )
    }

    fun confirmReplaceOldestActive() {
        val blocked = _capBlockedState.value ?: return
        _capBlockedState.value = null
        _captureMode.value = CaptureMode.Processing
        viewModelScope.launch {
            try {
                scheduler.cancelReminder(blocked.oldestActiveReminder.id)
                saveParsedDraft(blocked.draft, forceReplaceOldestActive = true)
            } finally {
                _captureMode.value = CaptureMode.Idle
            }
        }
    }

    fun dismissCapBlocked() {
        _capBlockedState.value = null
    }

    fun undoLastSaved() {
        val banner = _autoSaveBanner.value ?: return
        dismissBanner()
        viewModelScope.launch {
            scheduler.cancelReminder(banner.reminder.id)
            repository.deleteReminder(currentUserId, banner.reminder.id).onFailure { error ->
                _actionError.value = error.localizedMessage ?: "Failed to undo saved reminder."
            }
        }
    }

    fun openEditSheet(reminder: Reminder) {
        _editingReminder.value = reminder
    }

    fun openReminderById(reminderId: String) {
        val currentList = (remindersState.value as? UiState.Success)?.data.orEmpty()
        val found = currentList.firstOrNull { it.id == reminderId }
        if (found != null) {
            _editingReminder.value = found
            return
        }
        viewModelScope.launch {
            repository.getReminder(currentUserId, reminderId).onSuccess { loaded ->
                if (loaded != null) {
                    _editingReminder.value = loaded
                }
            }
        }
    }

    fun closeEditSheet() {
        _editingReminder.value = null
    }

    fun saveEditedReminder(
        reminderId: String,
        topic: String,
        organizedNote: String,
        category: ReminderCategory,
        scheduledAtEpochMillis: Long?
    ) {
        viewModelScope.launch {
            repository.updateReminderContent(
                userId = currentUserId,
                reminderId = reminderId,
                topic = topic,
                organizedNote = organizedNote,
                category = category,
                scheduledAtEpochMillis = scheduledAtEpochMillis
            ).fold(
                onSuccess = {
                    val existing = _editingReminder.value
                    val updated = existing?.copy(
                        topic = topic.trim().ifBlank { "Voice Reminder" },
                        organizedNote = organizedNote.trim(),
                        category = category.label,
                        scheduledAtEpochMillis = scheduledAtEpochMillis
                    )
                    if (updated != null) {
                        scheduler.scheduleReminder(updated)
                    }
                    _editingReminder.value = null
                },
                onFailure = { error ->
                    _actionError.value = error.localizedMessage ?: "Failed to update reminder."
                }
            )
        }
    }

    fun toggleCompleted(reminder: Reminder) {
        val nextCompleted = !reminder.completed
        viewModelScope.launch {
            repository.toggleCompleted(currentUserId, reminder.id, nextCompleted).fold(
                onSuccess = {
                    if (nextCompleted) {
                        scheduler.cancelReminder(reminder.id)
                    } else {
                        scheduler.scheduleReminder(reminder.copy(completed = false))
                    }
                },
                onFailure = { error ->
                    _actionError.value = error.localizedMessage ?: "Failed to update status."
                }
            )
        }
    }

    fun deleteReminder(reminder: Reminder) {
        if (_editingReminder.value?.id == reminder.id) {
            _editingReminder.value = null
        }
        if (_autoSaveBanner.value?.reminder?.id == reminder.id) {
            dismissBanner()
        }
        viewModelScope.launch {
            scheduler.cancelReminder(reminder.id)
            repository.deleteReminder(currentUserId, reminder.id).onFailure { error ->
                _actionError.value = error.localizedMessage ?: "Failed to delete reminder."
            }
        }
    }

    fun dismissBanner() {
        bannerDismissJob?.cancel()
        _autoSaveBanner.value = null
    }

    fun clearError() {
        _actionError.value = null
    }

    private fun showAutoSaveBanner(banner: AutoSaveBanner) {
        _autoSaveBanner.value = banner
        bannerDismissJob?.cancel()
        bannerDismissJob = viewModelScope.launch {
            delay(8_000L)
            _autoSaveBanner.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        bannerDismissJob?.cancel()
        recorderManager.cancelRecording()
    }

    private companion object {
        const val TAG = "ReminderViewModel"
    }
}
