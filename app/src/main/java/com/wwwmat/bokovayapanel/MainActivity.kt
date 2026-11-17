package com.wwwmat.bokovayapanel

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.pm.PackageManager

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("panel_settings", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var panelSpinner: Spinner
    private lateinit var appsContainer: LinearLayout
    private var selectedPanel = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(Color.rgb(246, 248, 247))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Боковая панель"
            textSize = 28f
            setTextColor(Color.rgb(25, 55, 39))
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Настрой панель и выбери приложения, которые будут открываться с края экрана."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(6), 0, dp(18))
        })

        addSection("Запуск")
        addButton("Разрешить отображение поверх окон") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        }
        addButton("Включить боковую панель") {
            if (Settings.canDrawOverlays(this)) {
                val i = Intent(this, SidePanelService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            }
        }
        addButton("Выключить боковую панель") { stopService(Intent(this, SidePanelService::class.java)) }

        addSection("Внешний вид и положение")
        addSetting("Толщина полоски", "handle_width", 8, 40, 18, "dp")
        addSetting("Высота полоски", "handle_height", 48, 240, 112, "dp")
        addSetting("Прозрачность панели", "opacity", 0, 100, 92, "%")
        addSetting("Положение по вертикали", "position", 0, 100, 50, "%")
        addSetting("Количество панелей", "panel_count", 1, 10, 3, "")

        addSection("Ярлыки приложений")
        root.addView(TextView(this).apply {
            text = "Выбери номер панели, затем отметь приложения ниже. Изменения сохраняются автоматически."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(8))
        })
        panelSpinner = Spinner(this)
        refreshPanelSpinner()
        root.addView(panelSpinner, matchWidth())
        panelSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedPanel = position
                refreshAppList()
            }
        }
        appsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appsContainer, matchWidth())
        refreshAppList()
        addButton("Применить настройки (перезапустить панель)") {
            stopService(Intent(this, SidePanelService::class.java))
            if (Settings.canDrawOverlays(this)) {
                val i = Intent(this, SidePanelService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            }
        }
    }

    private fun addSection(title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(35, 105, 65))
            setPadding(0, dp(18), 0, dp(8))
        })
    }

    private fun addButton(label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(40, 145, 82))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        lp.bottomMargin = dp(8)
        root.addView(b, lp)
    }

    private fun addSetting(label: String, key: String, min: Int, max: Int, initial: Int, unit: String) {
        val current = prefs.getInt(key, initial).coerceIn(min, max)
        val valueText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
            text = label + ": " + current + unit
        }
        root.addView(valueText)
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    valueText.text = label + ": " + value + unit
                    prefs.edit().putInt(key, value).apply()
                    if (key == "panel_count" && ::panelSpinner.isInitialized) refreshPanelSpinner()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(seek, matchWidth())
    }

    private fun refreshPanelSpinner() {
        val count = prefs.getInt("panel_count", 3).coerceIn(1, 10)
        val labels = (1..count).map { "Панель " + it }
        panelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        if (selectedPanel >= count) selectedPanel = count - 1
        panelSpinner.setSelection(selectedPanel)
    }

    private fun refreshAppList() {
        if (!::appsContainer.isInitialized) return
        appsContainer.removeAllViews()
        val key = "apps_panel_" + selectedPanel
        val selected = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        for (info in activities) {
            val pkg = info.activityInfo.packageName
            val check = CheckBox(this).apply {
                text = info.loadLabel(packageManager).toString()
                textSize = 14f
                setTextColor(Color.rgb(35, 45, 38))
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(40, 145, 82))
                isChecked = selected.contains(pkg)
                setCompoundDrawablesWithIntrinsicBounds(info.loadIcon(packageManager), null, null, null)
                compoundDrawablePadding = dp(12)
                setPadding(dp(4), dp(7), dp(4), dp(7))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(pkg) else selected.remove(pkg)
                    prefs.edit().putStringSet(key, selected.toSet()).apply()
                }
            }
            appsContainer.addView(check, matchWidth())
        }
        if (activities.isEmpty()) {
            appsContainer.addView(TextView(this).apply { text = "Не удалось получить список приложений." })
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
