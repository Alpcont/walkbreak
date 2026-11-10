package com.alp.walkbreak

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Runs the focus/walk loop in the foreground so it keeps going with the screen off.
 * Focus (default 25 min) -> Walk (default 5 min) -> Focus -> ... until stopped.
 */
class TimerService : Service() {

    enum class Phase { FOCUS, WALK }

    companion object {
        const val ACTION_START = "com.alp.walkbreak.START"
        const val ACTION_STOP = "com.alp.walkbreak.STOP"
        const val ACTION_SKIP = "com.alp.walkbreak.SKIP"
        const val EXTRA_FOCUS_MIN = "focus_min"
        const val EXTRA_WALK_MIN = "walk_min"

        private const val CH_ONGOING = "ongoing"
        private const val CH_ALERT = "alert"
        private const val ID_ONGOING = 1
        private const val ID_ALERT = 2

        // Simple shared state the screen reads to draw the countdown.
        @Volatile var running = false; private set
        @Volatile var phase = Phase.FOCUS; private set
        @Volatile var endTimeMillis = 0L; private set
        @Volatile var completedWalks = 0; private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusMs = 25 * 60_000L
    private var walkMs = 5 * 60_000L

    private val switchPhase = Runnable { onPhaseFinished() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                focusMs = intent.getIntExtra(EXTRA_FOCUS_MIN, 25).coerceAtLeast(1) * 60_000L
                walkMs = intent.getIntExtra(EXTRA_WALK_MIN, 5).coerceAtLeast(1) * 60_000L
                completedWalks = 0
                running = true
                startForegroundCompat()
                acquireWakeLock()
                beginPhase(Phase.FOCUS, alert = false)
            }
            ACTION_SKIP -> if (running) onPhaseFinished()
            ACTION_STOP -> stopEverything()
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun beginPhase(newPhase: Phase, alert: Boolean) {
        phase = newPhase
        val length = if (newPhase == Phase.FOCUS) focusMs else walkMs
        endTimeMillis = System.currentTimeMillis() + length
        handler.removeCallbacks(switchPhase)
        handler.postDelayed(switchPhase, length)
        nm().notify(ID_ONGOING, buildOngoing())
        if (alert) nm().notify(ID_ALERT, buildAlert(newPhase))
    }

    private fun onPhaseFinished() {
        if (phase == Phase.WALK) completedWalks++
        beginPhase(if (phase == Phase.FOCUS) Phase.WALK else Phase.FOCUS, alert = true)
    }

    private fun stopEverything() {
        running = false
        handler.removeCallbacks(switchPhase)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        nm().cancel(ID_ALERT)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(switchPhase)
        wakeLock?.let { if (it.isHeld) it.release() }
        running = false
        super.onDestroy()
    }

    // A partial wake lock keeps the CPU awake so the switch happens on time,
    // even in deep sleep. Capped at 8 hours as a safety net.
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WalkBreak::timer").apply {
            acquire(8 * 60 * 60_000L)
        }
    }

    private fun startForegroundCompat() {
        val n = buildOngoing()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_ONGOING, n)
        }
    }

    // ---------- Notifications ----------

    private fun nm() = getSystemService(NotificationManager::class.java)

    private fun createChannels() {
        val ongoing = NotificationChannel(CH_ONGOING, "Timer running", NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }

        val alert = NotificationChannel(CH_ALERT, "Walk / focus reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm().createNotificationChannel(ongoing)
        nm().createNotificationChannel(alert)
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun serviceAction(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, TimerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildOngoing(): Notification {
        val title = if (phase == Phase.FOCUS) "📚 Focus time" else "🚶 Walk time"
        return Notification.Builder(this, CH_ONGOING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Walks done: $completedWalks")
            .setWhen(endTimeMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)   // live countdown with no per-second updates
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(Notification.Action.Builder(null, "Skip", serviceAction(ACTION_SKIP, 1)).build())
            .addAction(Notification.Action.Builder(null, "Stop", serviceAction(ACTION_STOP, 2)).build())
            .build()
    }

    private fun buildAlert(newPhase: Phase): Notification {
        val (title, text) = if (newPhase == Phase.WALK)
            "Time to walk! 🚶" to "Stand up and walk for ${walkMs / 60_000} minutes."
        else
            "Back to studying 📚" to "Next walk in ${focusMs / 60_000} minutes."
        return Notification.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
    }
}
