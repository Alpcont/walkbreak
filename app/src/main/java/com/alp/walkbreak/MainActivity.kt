package com.alp.walkbreak

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var phaseText: TextView
    private lateinit var timeText: TextView
    private lateinit var walksText: TextView
    private lateinit var focusInput: EditText
    private lateinit var walkInput: EditText
    private lateinit var startStop: Button
    private lateinit var skip: Button

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        phaseText = findViewById(R.id.phaseText)
        timeText = findViewById(R.id.timeText)
        walksText = findViewById(R.id.walksText)
        focusInput = findViewById(R.id.focusInput)
        walkInput = findViewById(R.id.walkInput)
        startStop = findViewById(R.id.startStop)
        skip = findViewById(R.id.skip)

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
        focusInput.isEnabled = !running
        walkInput.isEnabled = !running
        skip.isEnabled = running
        startStop.text = if (running) "Stop" else "Start"

        if (!running) {
            phaseText.text = "Ready"
            timeText.text = "%02d:00".format(focusInput.text.toString().toIntOrNull() ?: 25)
            walksText.text = ""
            root.setBackgroundColor(Color.parseColor("#F4F1EA"))
            return
        }

        val walking = TimerService.phase == TimerService.Phase.WALK
        phaseText.text = if (walking) "🚶 Go for a walk" else "📚 Focus"
        root.setBackgroundColor(Color.parseColor(if (walking) "#D8F0DC" else "#F4F1EA"))

        val left = ((TimerService.endTimeMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        timeText.text = "%02d:%02d".format(left / 60, left % 60)
        walksText.text = "Walks completed: ${TimerService.completedWalks}"
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
