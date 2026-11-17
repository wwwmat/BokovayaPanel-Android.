package com.wwwmat.bokovayapanel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class SidePanelService : Service() {
    private lateinit var wm: WindowManager
    private var handle: View? = null
    private var tray: View? = null
    private var downX = 0f
    private var downY = 0f
    private val channelId = "side_panel_service"

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotification()
        showHandle()
    }

    private fun createNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Боковая панель", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setContentTitle("Боковая панель активна")
            .setContentText("Проведите по полоске справа влево")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7, notification)
        }
    }

    private fun params(width: Int, height: Int, gravity: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity }

    private fun showHandle() {
        val strip = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(210, 40, 170, 95))
                cornerRadii = floatArrayOf(12f,12f,0f,0f,0f,0f,12f,12f)
            }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; true }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - downX
                        if (dx < -25f) showTray() else if (abs(dx) < 12f) showTray()
                        true
                    }
                    else -> true
                }
            }
        }
        handle = strip
        val p = params(dp(18), dp(112), Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        wm.addView(strip, p)
    }

    private fun showTray() {
        if (tray != null) { hideTray(); return }
        handle?.visibility = View.GONE
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 28, 32, 38))
                cornerRadius = dp(18).toFloat()
            }
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        val apps = packageManager.getInstalledApplications(0)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
            .take(40)
        for (app in apps) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                addView(ImageView(this@SidePanelService).apply {
                    setImageDrawable(packageManager.getApplicationIcon(app))
                    layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                })
                addView(TextView(this@SidePanelService).apply {
                    text = packageManager.getApplicationLabel(app).toString()
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    maxWidth = dp(68)
                    maxLines = 1
                })
                setOnClickListener {
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launch)
                    }
                    hideTray()
                }
            }
            row.addView(item)
        }
        tray = scroll
        val p = params(WindowManager.LayoutParams.MATCH_PARENT, dp(112), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        wm.addView(scroll, p)
        scroll.alpha = 0f
        scroll.translationY = dp(30).toFloat()
        scroll.animate().alpha(1f).translationY(0f).setDuration(220).start()
        scroll.setOnClickListener { /* tap outside an icon keeps the tray open */ }
    }

    private fun hideTray() {
        tray?.let { v ->
            v.animate().alpha(0f).translationY(dp(24).toFloat()).setDuration(160).withEndAction {
                try { wm.removeView(v) } catch (_: Exception) {}
                tray = null
                handle?.visibility = View.VISIBLE
            }.start()
        } ?: run { handle?.visibility = View.VISIBLE }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        try { handle?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { tray?.let { wm.removeView(it) } } catch (_: Exception) {}
        handle = null
        tray = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
