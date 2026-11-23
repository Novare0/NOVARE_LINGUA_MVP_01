package com.novare.lingua

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioCaptureService : Service() {

    companion object {
        const val ACTION_STATUS = "com.novare.lingua.AUDIO_STATUS"
        const val ACTION_LEVEL = "com.novare.lingua.AUDIO_LEVEL"

        const val EXTRA_STATUS = "status"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "novare_audio_lab"
        private const val NOTIFICATION_ID = 501
    }

    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null

    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (Build.VERSION.SDK_INT < 29) {
            sendStatus("La captura requiere Android 10 o superior.")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0

        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultData == null) {
            sendStatus("No se recibió autorización de captura.")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("NOVARE LINGUA")
            .setContentText("AUDIO LAB: captura activa")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        try {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

            val manager = getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            projection = manager.getMediaProjection(resultCode, resultData)

            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopCapture()
                    }
                },
                null
            )

            startCapture()

        } catch (e: Exception) {
            sendStatus("Error de captura: ${e.localizedMessage ?: "desconocido"}")
            stopCapture()
        }

        return START_NOT_STICKY
    }

    private fun startCapture() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val minBuffer = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        check(minBuffer > 0) { "Formato de audio no compatible." }

        recorder = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer, 8192))
            .setAudioPlaybackCaptureConfig(config)
            .build()

        val audio = recorder!!

        check(audio.state == AudioRecord.STATE_INITIALIZED) {
            "No se pudo inicializar AudioRecord."
        }

        audio.startRecording()
        running = true

        sendStatus("Capturando audio multimedia permitido.")

        thread(name = "NovareAudioCapture") {
            val buffer = ShortArray(2048)

            while (running) {
                val count = audio.read(buffer, 0, buffer.size)

                if (count > 0) {
                    var sum = 0.0

                    for (i in 0 until count) {
                        val sample = buffer[i].toDouble() / 32768.0
                        sum += sample * sample
                    }

                    val rms = sqrt(sum / count) * 100.0

                    sendBroadcast(
                        Intent(ACTION_LEVEL)
                            .setPackage(packageName)
                            .putExtra(EXTRA_LEVEL, rms)
                    )
                } else if (count < 0) {
                    sendStatus("La lectura de audio terminó.")
                    break
                }
            }
        }
    }

    private fun sendStatus(message: String) {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, message)
        )
    }

    private fun stopCapture() {
        running = false

        try {
            recorder?.stop()
        } catch (_: Exception) {}

        try {
            recorder?.release()
        } catch (_: Exception) {}

        recorder = null

        val currentProjection = projection
        projection = null

        try {
            currentProjection?.stop()
        } catch (_: Exception) {}

        sendStatus("Captura detenida.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false

        try {
            recorder?.stop()
        } catch (_: Exception) {}

        try {
            recorder?.release()
        } catch (_: Exception) {}

        recorder = null

        val currentProjection = projection
        projection = null

        try {
            currentProjection?.stop()
        } catch (_: Exception) {}

        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOVARE AUDIO LAB",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
