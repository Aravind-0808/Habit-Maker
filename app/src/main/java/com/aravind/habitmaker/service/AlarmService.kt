package com.aravind.habitmaker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.aravind.habitmaker.App
import com.aravind.habitmaker.R
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.prefs.FlashMode
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.core.routine.RoutineAlarms
import com.aravind.habitmaker.core.util.Dates
import com.aravind.habitmaker.data.db.RoutineLog
import com.aravind.habitmaker.feature.prep.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The ringing wake-up alarm: chosen sound, vibration and flashlight blink, until Stop or Snooze. */
class AlarmService : Service() {
    data class Ringing(val blockId: Long, val title: String, val since: Long = System.currentTimeMillis())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var flashJob: Job? = null
    private var torchId: String? = null
    private var autoStop: Job? = null
    private var settings = Settings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { finish(RoutineLog.DONE); return START_NOT_STICKY }
            ACTION_SNOOZE -> { snooze(); return START_NOT_STICKY }
        }
        val ring = Ringing(intent?.getLongExtra(EXTRA_ID, -1) ?: -1, intent?.getStringExtra(EXTRA_TITLE) ?: "Wake up")
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(ring), type)
        stopEffects()
        _ringing.value = ring
        scope.launch {
            settings = app.prefs.current()
            startSound()
            if (settings.alarmVibrate) startVibration()
            if (settings.alarmFlash) startFlash(settings.alarmFlashMode)
        }
        autoStop?.cancel()
        autoStop = scope.launch { delay(AUTO_STOP_MS); finish(RoutineLog.MISSED) } // nobody answered: not woken up
        return START_NOT_STICKY
    }

    private fun notification(r: Ringing): Notification {
        val full = PendingIntent.getActivity(
            this, 3, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, AlarmService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, App.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(r.title)
            .setContentText("Good morning! Tap to open.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .addAction(0, "Snooze", action(ACTION_SNOOZE, 11))
            .addAction(0, "Stop", action(ACTION_STOP, 12))
            .build()
    }

    // --- sound ---

    private fun soundUri(): Uri? = settings.alarmSound.takeIf { it.isNotEmpty() }?.toUri()
        ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun startSound() {
        val tryPlay = { uri: Uri? ->
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setDataSource(this@AlarmService, uri!!)
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        // A custom file may have been deleted: fall back to the phone's alarm.
        player = tryPlay(soundUri()) ?: tryPlay(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        val p = player ?: return
        if (settings.alarmRamp) {
            scope.launch {
                var v = 0.1f
                while (isActive && v < 1f) { runCatching { p.setVolume(v, v) }; delay(1000); v += 0.03f }
                runCatching { p.setVolume(1f, 1f) }
            }
        }
    }

    // --- vibration ---

    @SuppressLint("MissingPermission") // VIBRATE is a normal permission, declared in the manifest
    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator = v
        val pattern = longArrayOf(0, 800, 600)
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
    }

    // --- flashlight blink (no camera permission needed for the torch) ---

    private fun startFlash(mode: FlashMode) {
        val cm = getSystemService(CameraManager::class.java)
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return
        torchId = id
        val steps: List<Pair<Boolean, Long>> = when (mode) {
            FlashMode.FAST -> listOf(true to 200L, false to 200L)
            FlashMode.SLOW -> listOf(true to 700L, false to 700L)
            // ... --- ...
            FlashMode.SOS -> listOf(200L, 200L, 200L, 600L, 600L, 600L, 200L, 200L, 200L)
                .flatMap { listOf(true to it, false to 200L) } + (false to 1000L)
        }
        flashJob = scope.launch {
            while (isActive) for ((on, ms) in steps) { runCatching { cm.setTorchMode(id, on) }; delay(ms) }
        }
    }

    private fun stopEffects() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        flashJob?.cancel()
        flashJob = null
        torchId?.let { id -> runCatching { getSystemService(CameraManager::class.java).setTorchMode(id, false) } }
        torchId = null
    }

    /** [status]: DONE = woke up (Stop), MISSED = nobody answered, null = snoozed (decided later). */
    private fun finish(status: Int?) {
        val r = _ringing.value
        stopEffects()
        autoStop?.cancel()
        _ringing.value = null
        if (status != null && r != null && r.blockId > 0) {
            val app = app
            scope.launch(Dispatchers.IO) { app.db.routine().upsertLog(RoutineLog(Dates.today(), r.blockId, status)) }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun snooze() {
        val r = _ringing.value
        if (r != null && r.blockId > 0) RoutineAlarms.snooze(this, r.blockId, settings.alarmSnoozeMin)
        finish(null)
    }

    override fun onDestroy() {
        stopEffects()
        _ringing.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 77
        private const val AUTO_STOP_MS = 10 * 60_000L
        const val ACTION_STOP = "com.aravind.habitmaker.alarm.STOP"
        const val ACTION_SNOOZE = "com.aravind.habitmaker.alarm.SNOOZE"
        private const val EXTRA_ID = "block_id"
        private const val EXTRA_TITLE = "title"

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

        /** Starts ringing. [blockId] = -1 for a test alarm. */
        fun start(context: Context, blockId: Long, title: String) {
            ContextCompat.startForegroundService(
                context, Intent(context, AlarmService::class.java).putExtra(EXTRA_ID, blockId).putExtra(EXTRA_TITLE, title),
            )
        }

        fun stop(context: Context) { context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP)) }
        fun snooze(context: Context) { context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE)) }
    }
}
