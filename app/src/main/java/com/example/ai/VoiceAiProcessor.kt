package com.example.ai

import android.util.Log
import com.example.data.ParsedVoiceReminder
import com.example.data.ReminderCategory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class VoiceAiProcessor {

    private val reminderSchema = Schema.obj(
        properties = mapOf(
            "topic" to Schema.string(
                description = "Short, clear 2-to-7 word headline suitable for a push notification title."
            ),
            "organizedNote" to Schema.string(
                description = "Clean, well-structured text note summarizing the instruction without filler words; use short bullet points (prefixed with '• ') if there are multiple items or steps."
            ),
            "rawTranscript" to Schema.string(
                description = "Accurate verbatim transcription of the spoken audio or original user instruction."
            ),
            "category" to Schema.enumeration(
                values = listOf("Work", "Personal", "Errand", "Health", "Finance", "General"),
                description = "Single best matching category."
            ),
            "scheduledAtIso" to Schema.string(
                description = "ISO-8601 date-time with timezone offset (e.g. 2026-11-23T14:30:00-08:00) resolved relative to the provided current local time if the user mentioned a time, date, or relative delay. Return null if no time was mentioned.",
                nullable = true
            )
        ),
        optionalProperties = listOf("scheduledAtIso")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = reminderSchema
            }
        )
    }

    suspend fun processVoiceAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4"
    ): Result<ParsedVoiceReminder> {
        return try {
            require(audioBytes.isNotEmpty()) { "Audio recording is empty." }
            val prompt = buildInstructionPrompt(isAudio = true)
            val response = withTimeout(TIMEOUT_MILLIS) {
                model.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))
            Result.success(parseStructuredJson(jsonText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice AI processing timed out", e)
            Result.failure(IllegalStateException("AI processing timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice AI processing failed", e)
            Result.failure(e)
        }
    }

    suspend fun processTextInstruction(instructionText: String): Result<ParsedVoiceReminder> {
        return try {
            val trimmed = instructionText.trim()
            require(trimmed.isNotBlank()) { "Instruction cannot be empty." }
            val prompt = buildInstructionPrompt(isAudio = false) + "\n\nUser instruction:\n$trimmed"
            val response = withTimeout(TIMEOUT_MILLIS) {
                model.generateContent(prompt)
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))
            val parsed = parseStructuredJson(jsonText)
            Result.success(
                if (parsed.rawTranscript.isBlank()) parsed.copy(rawTranscript = trimmed) else parsed
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Text AI processing timed out", e)
            Result.failure(IllegalStateException("AI processing timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Text AI processing failed", e)
            Result.failure(e)
        }
    }

    private fun buildInstructionPrompt(isAudio: Boolean): String {
        val now = Date()
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val dayFormatter = SimpleDateFormat("EEEE", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val currentIso = isoFormatter.format(now)
        val currentDay = dayFormatter.format(now)
        val tzId = TimeZone.getDefault().id

        val sourceDesc = if (isAudio) {
            "Listen carefully to the attached voice recording."
        } else {
            "Analyze the provided instruction."
        }

        return """
            $sourceDesc
            Current local date/time: $currentIso ($currentDay, timezone: $tzId).

            Tasks:
            1. Transcribe the instruction faithfully into `rawTranscript`.
            2. Create a concise, actionable push-notification title in `topic` (2 to 7 words, no trailing punctuation).
            3. Rewrite the content into a clean, organized note in `organizedNote` (removing hesitation/filler words, formatting multiple action items as concise '• ' bullets).
            4. Pick the single best category from: Work, Personal, Errand, Health, Finance, General.
            5. Extract any mentioned reminder time/date (e.g., "in 15 minutes", "at 5 PM", "tomorrow morning" -> 09:00, "tonight" -> 20:00, "Monday at 10") and resolve it to an exact future ISO-8601 timestamp with offset in `scheduledAtIso` using the current local date/time above. If no time or date is mentioned, set `scheduledAtIso` to null.
        """.trimIndent()
    }

    private fun parseStructuredJson(jsonString: String): ParsedVoiceReminder {
        val json = JSONObject(jsonString)
        val topic = json.optString("topic", "").trim().ifBlank { "Voice Reminder" }
        val organizedNote = json.optString("organizedNote", "").trim()
        val rawTranscript = json.optString("rawTranscript", "").trim()
        val category = ReminderCategory.fromRaw(json.optString("category", "General"))
        val scheduledIso = if (json.isNull("scheduledAtIso")) {
            null
        } else {
            json.optString("scheduledAtIso", "").trim().takeIf {
                it.isNotBlank() && !it.equals("null", ignoreCase = true)
            }
        }
        val scheduledMillis = scheduledIso?.let { parseIsoToEpochMillis(it) }

        return ParsedVoiceReminder(
            topic = topic,
            organizedNote = organizedNote.ifBlank { rawTranscript },
            rawTranscript = rawTranscript.ifBlank { topic },
            category = category,
            scheduledAtEpochMillis = scheduledMillis
        )
    }

    private fun parseIsoToEpochMillis(iso: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    if (pattern.endsWith("'Z'")) {
                        timeZone = TimeZone.getTimeZone("UTC")
                    } else {
                        timeZone = TimeZone.getDefault()
                    }
                }
                val parsed = sdf.parse(iso)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next ISO pattern
            }
        }
        Log.w(TAG, "Unable to parse scheduledAtIso: $iso")
        return null
    }

    private companion object {
        const val TAG = "VoiceAiProcessor"
        const val TIMEOUT_MILLIS = 60_000L
    }
}
