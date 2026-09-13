package com.custom.dynamicislandos

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val btnPermission = Button(this).apply {
            text = "1. Grant Overlay Permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, 100)
                } else {
                    Toast.makeText(this@MainActivity, "Overlay Granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnNotifyAuth = Button(this).apply {
            text = "2. Grant Notification Access"
            setOnClickListener {
                val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity)
                if (!enabledListeners.contains(packageName)) {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } else {
                    Toast.makeText(this@MainActivity, "Notification Access Granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnStart = Button(this).apply {
            text = "3. Start Dynamic Island"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    val serviceIntent = Intent(this@MainActivity, DynamicIslandService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "Grant Overlay permission first!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        layout.addView(btnPermission)
        layout.addView(btnNotifyAuth)
        layout.addView(btnStart)
        
        setContentView(layout)
    }
}
