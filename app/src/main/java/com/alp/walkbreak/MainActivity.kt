package com.alp.walkbreak

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var ring: ProgressRing
    private lateinit var phaseText: TextView
    private lateinit var timeText: TextView
    private lateinit var walksText: TextView
    private lateinit var settingsRow: View
    private lateinit var focusInput: EditText
    private lateinit var walkInput: EditText
    private lateinit var startStop: Button
    private lateinit var skip: Button

    // Colors for the current look; animated when the phase changes.
    private var bgNow = 0
    private var accentNow = 0
    private var lastWalking: Boolean? = null
    private var colorAnim: ValueAnimator? = null

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        ring = findViewById(R.id.ring)
        phaseText = findViewById(R.id.phaseText)
        timeText = findViewById(R.id.timeText)
        walksText = findViewById(R.id.walksText)
        settingsRow = findViewById(R.id.settingsRow)
        focusInput = findViewById(R.id.focusInput)
        walkInput = findViewById(R.id.walkInput)
        startStop = findViewById(R.id.startStop)
        skip = findViewById(R.id.skip)

        ring.trackColor = getColor(R.color.ring_track)
        ring.headColor = getColor(R.color.ring_head)

        bgNow = getColor(R.color.bg_focus)
        accentNow = getColor(R.color.accent_focus)
        applyColors(bgNow, accentNow)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        focusInput.setText(prefs.getInt("focus", 25).toString())
        walkInput.setText(prefs.getInt("walk", 5).toString())

        startStop.setOnClickListener {
            if (TimerService.running) send(TimerService.ACTION_STOP) else start()
        }
        skip.setOnClickListener { send(TimerService.ACTION_SKIP) }

        askNotificationPermission()
    }

    private fun start() {
        val focus = focusInput.text.toString().toIntOrNull()
        val walk = walkInput.text.toString().toIntOrNull()
        if (focus == null || walk == null || focus < 1 || walk < 1) {
            Toast.makeText(this, "Enter minutes (1 or more)", Toast.LENGTH_SHORT).show()
            return
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("focus", focus).putInt("walk", walk).apply()

        val i = Intent(this, TimerService::class.java)
            .setAction(TimerService.ACTION_START)
            .putExtra(TimerService.EXTRA_FOCUS_MIN, focus)
            .putExtra(TimerService.EXTRA_WALK_MIN, walk)
        startForegroundService(i)
    }

    private fun send(action: String) {
        startService(Intent(this, TimerService::class.java).setAction(action))
    }

    private fun render() {
        val running = TimerService.running
        val walking = running && TimerService.phase == TimerService.Phase.WALK

        settingsRow.visibility = if (running) View.GONE else View.VISIBLE
        skip.visibility = if (running) View.VISIBLE else View.INVISIBLE
        startStop.text = if (running) "Stop" else "Start"

        if (lastWalking != walking) {
            animateTo(walking)
            lastWalking = walking
        }

        if (!running) {
            phaseText.text = "Ready to focus"
            timeText.text = "%02d:00".format(focusInput.text.toString().toIntOrNull() ?: 25)
            val f = focusInput.text.toString().toIntOrNull() ?: 25
            val w = walkInput.text.toString().toIntOrNull() ?: 5
            walksText.text = "$f min focus · $w min walk"
            ring.progress = 1f
            return
        }

        phaseText.text = if (walking) "Time to walk 🚶" else "Focus 📚"
        val leftMs = (TimerService.endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val left = (leftMs + 999) / 1000
        timeText.text = "%02d:%02d".format(left / 60, left % 60)
        ring.progress = if (TimerService.phaseLengthMs > 0) leftMs.toFloat() / TimerService.phaseLengthMs else 0f

        val n = TimerService.completedWalks
        walksText.text = if (n == 1) "1 walk this session" else "$n walks this session"
    }

    /** Smoothly blends background and accent between focus (calm blue) and walk (green). */
    private fun animateTo(walking: Boolean) {
        val bgTarget = getColor(if (walking) R.color.bg_walk else R.color.bg_focus)
        val accTarget = getColor(if (walking) R.color.accent_walk else R.color.accent_focus)
        val bgFrom = bgNow
        val accFrom = accentNow
        val eval = ArgbEvaluator()
        colorAnim?.cancel()
        colorAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            addUpdateListener {
                val f = it.animatedFraction
                bgNow = eval.evaluate(f, bgFrom, bgTarget) as Int
                accentNow = eval.evaluate(f, accFrom, accTarget) as Int
                applyColors(bgNow, accentNow)
            }
            start()
        }
    }

    private fun applyColors(bg: Int, accent: Int) {
        root.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        ring.arcColor = accent
        startStop.backgroundTintList = ColorStateList.valueOf(accent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }
}
