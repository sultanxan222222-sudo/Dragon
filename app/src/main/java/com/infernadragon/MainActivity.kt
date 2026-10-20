package com.infernadragon

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(19, 8, 10))
        }
        val title = TextView(this).apply {
            text = "INFERNO DRAGON"
            textSize = 28f
            setTextColor(Color.rgb(255, 91, 35))
            gravity = Gravity.CENTER
        }
        val desc = TextView(this).apply {
            text = "Живой дракон поверх приложений\n\nДракон и искры анимируются поверх экрана, но не перехватывают касания: кнопки в других приложениях продолжают работать. Для демонстрации укуса используй отдельную кнопку ниже. Для запуска нужен доступ «Поверх других приложений»."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 28)
        }
        val start = Button(this).apply {
            text = "ВКЛЮЧИТЬ ДРАКОН"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    Toast.makeText(this@MainActivity, "Разреши показ поверх других приложений, затем снова нажми кнопку", Toast.LENGTH_LONG).show()
                } else {
                    startService(Intent(this@MainActivity, DragonOverlayService::class.java))
                    Toast.makeText(this@MainActivity, "Inferno Dragon запущен", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val bite = Button(this).apply {
            text = "ПРОВЕРИТЬ УКУС В ЦЕНТРЕ"
            setOnClickListener {
                startService(Intent(this@MainActivity, DragonOverlayService::class.java).apply {
                    action = DragonOverlayService.ACTION_BITE_CENTER
                })
            }
        }
        val stop = Button(this).apply {
            text = "ОСТАНОВИТЬ ДРАКОН"
            setOnClickListener { stopService(Intent(this@MainActivity, DragonOverlayService::class.java)) }
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(desc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bite, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }
}
