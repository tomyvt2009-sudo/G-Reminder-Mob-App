package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ReminderRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val MAX_REMINDERS = 20
        private const val MAX_TOPIC_CHARS = 100
        private const val MAX_TEXT_CHARS = 2000
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun remindersCollection(uid: String) =
        db.collection("users").document(uid).collection("reminders")

    fun observeReminders(userId: String): Flow<List<Reminder>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "users/$uid/reminders"

        emitAll(
            remindersCollection(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Reminder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<Reminder> { it.completed }
                            .thenBy { it.scheduledAtEpochMillis ?: Long.MAX_VALUE }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun fetchRemindersOnce(userId: String): Result<List<Reminder>> {
        val path = "users/$userId/reminders"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            val snapshot = remindersCollection(uid).get().await()
            val items = snapshot.toObjects(
                Reminder::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getReminder(userId: String, reminderId: String): Result<Reminder?> {
        val path = "users/$userId/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            require(reminderId.isNotBlank()) { "Reminder ID must not be blank." }
            val doc = remindersCollection(uid).document(reminderId).get().await()
            val reminder = if (doc.exists()) {
                doc.toObject(
                    Reminder::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(reminder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveParsedReminder(
        userId: String,
        draft: ParsedVoiceReminder,
        forceReplaceOldestActive: Boolean = false
    ): Result<CapResolution> {
        var currentPath = "users/$userId/reminders"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }

            val existingSnapshot = remindersCollection(uid).get().await()
            val existingReminders = existingSnapshot.toObjects(
                Reminder::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            var evictedTopic: String? = null
            if (existingReminders.size >= MAX_REMINDERS) {
                val oldestCompleted = existingReminders
                    .filter { it.completed }
                    .minByOrNull {
                        it.completedAtEpochMillis
                            ?: it.createdAt?.toDate()?.time
                            ?: 0L
                    }

                val victim = oldestCompleted ?: if (forceReplaceOldestActive) {
                    existingReminders.minByOrNull { it.createdAt?.toDate()?.time ?: 0L }
                } else {
                    null
                }

                if (victim == null) {
                    val oldestActive = existingReminders
                        .minByOrNull { it.createdAt?.toDate()?.time ?: 0L }
                        ?: existingReminders.first()
                    return Result.success(
                        CapResolution.CapBlocked(
                            draft = draft,
                            oldestActiveReminder = oldestActive
                        )
                    )
                }

                val deletePath = "users/$uid/reminders/${victim.id}"
                currentPath = deletePath
                remindersCollection(uid).document(victim.id).delete().await()
                evictedTopic = victim.topic
            }

            val docRef = remindersCollection(uid).document()
            currentPath = docRef.path

            val safeTopic = draft.topic.trim().ifBlank { "Voice Reminder" }.take(MAX_TOPIC_CHARS)
            val safeNote = draft.organizedNote.trim().take(MAX_TEXT_CHARS)
            val safeTranscript = draft.rawTranscript.trim().take(MAX_TEXT_CHARS)

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "topic" to safeTopic,
                "organizedNote" to safeNote,
                "rawTranscript" to safeTranscript,
                "category" to draft.category.label,
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            draft.scheduledAtEpochMillis?.takeIf { it >= 0L }?.let {
                payload["scheduledAtEpochMillis"] = it
            }

            docRef.set(payload).await()

            val savedReminder = Reminder(
                id = docRef.id,
                userId = uid,
                topic = safeTopic,
                organizedNote = safeNote,
                rawTranscript = safeTranscript,
                category = draft.category.label,
                scheduledAtEpochMillis = draft.scheduledAtEpochMillis?.takeIf { it >= 0L },
                completed = false,
                completedAtEpochMillis = null,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            Result.success(CapResolution.Saved(savedReminder, evictedTopic))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun updateReminderContent(
        userId: String,
        reminderId: String,
        topic: String,
        organizedNote: String,
        category: ReminderCategory,
        scheduledAtEpochMillis: Long?
    ): Result<Unit> {
        val path = "users/$userId/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            require(reminderId.isNotBlank()) { "Reminder ID must not be blank." }

            val safeTopic = topic.trim().ifBlank { "Voice Reminder" }.take(MAX_TOPIC_CHARS)
            val safeNote = organizedNote.trim().take(MAX_TEXT_CHARS)

            val updates = mapOf<String, Any>(
                "topic" to safeTopic,
                "organizedNote" to safeNote,
                "category" to category.label,
                "scheduledAtEpochMillis" to (scheduledAtEpochMillis?.takeIf { it >= 0L }
                    ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            remindersCollection(uid).document(reminderId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleCompleted(
        userId: String,
        reminderId: String,
        completed: Boolean
    ): Result<Unit> {
        val path = "users/$userId/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            require(reminderId.isNotBlank()) { "Reminder ID must not be blank." }

            val updates = mapOf<String, Any>(
                "completed" to completed,
                "completedAtEpochMillis" to (if (completed) System.currentTimeMillis() else FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            remindersCollection(uid).document(reminderId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> {
        val path = "users/$userId/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            require(reminderId.isNotBlank()) { "Reminder ID must not be blank." }
            remindersCollection(uid).document(reminderId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
