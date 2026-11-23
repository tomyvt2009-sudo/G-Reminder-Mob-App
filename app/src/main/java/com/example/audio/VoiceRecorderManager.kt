package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMillis: Long = 0L

    fun startRecording(): Boolean {
        stopAndReleaseSafely()
        return try {
            val file = File(context.cacheDir, "echonote_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context.applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = instance
            startTimeMillis = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            stopAndReleaseSafely()
            outputFile?.delete()
            outputFile = null
            false
        }
    }

    fun sampleNormalizedAmplitude(): Float {
        val current = recorder ?: return 0.08f
        return try {
            val raw = current.maxAmplitude.coerceAtLeast(0)
            // MediaRecorder.maxAmplitude ranges 0..32767; use square-root curve for responsive visual bars
            val normalized = kotlin.math.sqrt((raw / 32767f).coerceIn(0f, 1f))
            normalized.coerceIn(0.08f, 1f)
        } catch (_: Exception) {
            0.08f
        }
    }

    fun stopRecording(): RecordedAudio? {
        val current = recorder ?: return null
        val file = outputFile
        val elapsedMillis = (System.currentTimeMillis() - startTimeMillis).coerceAtLeast(0L)
        return try {
            current.stop()
            current.release()
            recorder = null
            outputFile = null
            if (file != null && file.exists() && file.length() > 0L) {
                RecordedAudio(file = file, durationMillis = elapsedMillis)
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaRecorder (recording may be too short)", e)
            stopAndReleaseSafely()
            file?.delete()
            outputFile = null
            null
        }
    }

    fun cancelRecording() {
        stopAndReleaseSafely()
        outputFile?.delete()
        outputFile = null
    }

    private fun stopAndReleaseSafely() {
        val current = recorder ?: return
        try {
            current.stop()
        } catch (_: Exception) {
            // Ignore stop failure if recorder never started cleanly
        }
        try {
            current.release()
        } catch (_: Exception) {
            // Ignore release error
        }
        recorder = null
    }

    private companion object {
        const val TAG = "VoiceRecorderManager"
    }
}

data class RecordedAudio(
    val file: File,
    val durationMillis: Long
)
