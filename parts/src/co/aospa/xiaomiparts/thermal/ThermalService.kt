/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.thermal

import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import co.aospa.xiaomiparts.utils.dlog

/** Service to monitor current top (foreground) app and set thermal profile accordingly. */
class ThermalService : Service() {

    private lateinit var thermalUtils: ThermalUtils
    private lateinit var powerManager: PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var active = false
    private var currentApp = ""
    private var screenOn = true

    private val taskListener =
        object : TaskStackListener() {
            override fun onTaskStackChanged() {
                mainHandler.post {
                    if (active) refreshThermalProfile()
                }
            }

            override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {
                if (focused) onTaskStackChanged()
            }
        }

    private val intentReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!active) return
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        setThermalProfile()
                    }
                    Intent.ACTION_SCREEN_ON -> refreshThermalProfile()
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        dlog(TAG, "Creating service")
        thermalUtils = ThermalUtils.getInstance(this)
        powerManager = getSystemService(PowerManager::class.java)!!
        active = true
        runCatching { ActivityTaskManager.getService().registerTaskStackListener(taskListener) }
        registerReceiver(
            intentReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )
    }

    override fun onDestroy() {
        dlog(TAG, "Destroying service")
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(intentReceiver)
        runCatching { ActivityTaskManager.getService().unregisterTaskStackListener(taskListener) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        dlog(TAG, "Starting service")
        if (!thermalUtils.enabled) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        refreshThermalProfile()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshThermalProfile() {
        screenOn = powerManager.isInteractive
        runCatching { ActivityTaskManager.getService().focusedRootTaskInfo }
            .onSuccess { currentApp = it?.topActivity?.packageName.orEmpty() }
        setThermalProfile()
    }

    private fun setThermalProfile() {
        if (!thermalUtils.enabled) return
        if (screenOn && currentApp.isNotEmpty()) {
            thermalUtils.setThermalProfile(currentApp)
        } else {
            thermalUtils.setDefaultThermalProfile()
        }
    }

    companion object {
        private const val TAG = "ThermalService"
    }
}
