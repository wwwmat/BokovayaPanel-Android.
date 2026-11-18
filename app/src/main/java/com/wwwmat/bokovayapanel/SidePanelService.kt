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
    private var opened = false
    private val channelId = "side_panel_service"

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startNotification()
        showHandle()
    }

    private fun startNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Боковая панель", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId)
            else @Suppress("DEPRECATION") Notification.Builder(this)
        val notification = builder.setContentTitle("Боковая панель активна")
            .setContentText("Проведите по зелёной полоске справа влево")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7, notification)
    }

    private fun windowParams(width: Int, height: Int, gravity: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { this.gravity = gravity }

    private fun showHandle() {
        val prefs = getSharedPreferences("panel_settings", MODE_PRIVATE)
        val width = dp(prefs.getInt("handle_width", 18))
        val height = dp(prefs.getInt("handle_height", 112))
        val opacity = prefs.getInt("opacity", 92).coerceIn(0, 100)
        val position = prefs.getInt("position", 50).coerceIn(0, 100)
        val colorAlpha = (210 * opacity / 100).coerceIn(0, 255)
        val strip = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(colorAlpha, 38, 166, 91))
                cornerRadii = floatArrayOf(dp(12).toFloat(), dp(12).toFloat(), 0f, 0f, 0f, 0f, dp(12).toFloat(), dp(12).toFloat())
            }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; true }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - downX
                        if (dx < -dp(20)) showTray()
                        else if (dx > dp(20)) hideTray()
                        else if (abs(dx) < dp(12)) {
                            if (opened) hideTray() else showTray()
                        }
                        true
                    }
                    else -> true
                }
            }
        }
        handle = strip
        val screenHeight = resources.displayMetrics.heightPixels
        val y = ((screenHeight - height) * position / 100).coerceAtLeast(0)
        val p = windowParams(width, height, Gravity.RIGHT or Gravity.TOP).apply { this.y = y }
        wm.addView(strip, p)
    }

    private fun showTray() {
        if (opened) return
        opened = true
        val prefs = getSharedPreferences("panel_settings", MODE_PRIVATE)
        val count = prefs.getInt("panel_count", 3).coerceIn(1, 10)
        val opacity = prefs.getInt("opacity", 92).coerceIn(0, 100)
        val backgroundAlpha = (244 * opacity / 100).coerceIn(0, 255)
        val screenWidth = resources.displayMetrics.widthPixels
        val pageWidth = (screenWidth - dp(28)).coerceAtLeast(dp(220))

        val pages = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        for (index in 0 until count) {
            val page = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                alpha = 0f
                translationX = dp(28).toFloat()
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.argb(backgroundAlpha, 27, 33, 30))
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), Color.argb(100, 120, 190, 145))
                }
            }
            page.addView(TextView(this).apply {
                text = "ПАНЕЛЬ " + (index + 1) + " / " + count
                textSize = 11f
                setTextColor(Color.rgb(170, 220, 185))
                setPadding(0, 0, 0, dp(8))
            })
            val appRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val packages = prefs.getStringSet("apps_panel_" + index, emptySet())?.toList() ?: emptyList()
            var shown = 0
            for (pkg in packages) {
                val launch = packageManager.getLaunchIntentForPackage(pkg) ?: continue
                val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { continue }
                val icon = try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { continue }
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(9), dp(2), dp(9), dp(2))
                    addView(ImageView(this@SidePanelService).apply {
                        setImageDrawable(icon)
                        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                    })
                    addView(TextView(this@SidePanelService).apply {
                        text = label
                        textSize = 10f
                        setTextColor(Color.WHITE)
                        maxWidth = dp(72)
                        maxLines = 1
                        gravity = Gravity.CENTER
                    })
                    setOnClickListener {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launch)
                        hideTray()
                    }
                }
                appRow.addView(item)
                shown++
            }
            if (shown == 0) {
                page.addView(TextView(this).apply {
                    text = "Здесь пока нет ярлыков. Открой настройки и добавь приложения в эту панель."
                    textSize = 13f
                    setTextColor(Color.LTGRAY)
                })
            } else {
                val scrollApps = HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(appRow)
                }
                page.addView(scrollApps)
            }
            val pageParams = LinearLayout.LayoutParams(pageWidth, dp(126))
            pageParams.marginEnd = dp(8)
            pages.addView(page, pageParams)
            page.post {
                page.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setStartDelay(index * 85L)
                    .setDuration(220)
                    .start()
            }
        }

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(pages)
        }
        tray = horizontal
        val p = windowParams(WindowManager.LayoutParams.MATCH_PARENT, dp(150), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        wm.addView(horizontal, p)
        horizontal.alpha = 0f
        horizontal.translationY = dp(26).toFloat()
        horizontal.animate().alpha(1f).translationY(0f).setDuration(230).start()

    }

    private fun hideTray() {
        val view = tray ?: run { opened = false; return }
        view.animate().alpha(0f).translationY(dp(22).toFloat()).setDuration(170).withEndAction {
            try { wm.removeView(view) } catch (_: Exception) {}
            tray = null
            opened = false
        }.start()
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
