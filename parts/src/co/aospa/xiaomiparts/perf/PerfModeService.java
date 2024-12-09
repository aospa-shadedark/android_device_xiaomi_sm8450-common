/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.perf;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;

import co.aospa.xiaomiparts.R;

public class PerfModeService extends Service {

    private static final String TAG = "PerfModeService";

    private PowerManager mPowerManager;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dlog("received intent " + intent.getAction());
            final PerfModeUtils utils = PerfModeUtils.getInstance(context);
            switch (intent.getAction()) {
                case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED:
                    if (mPowerManager.isPowerSaveMode()) {
                        if (utils.isPerformanceModeOn()) {
                            Log.i(TAG, "power saver activated, disabling perf mode");
                            utils.turnOffPerformanceMode();
                        } else {
                            SystemProperties.set(PerfModeUtils.SYS_PROP, "2");
                        }
                    } else {
                        SystemProperties.set(PerfModeUtils.SYS_PROP, "0");
                    }
                    break;
                case PerfModeUtils.ACTION_DISABLE_PERF_MODE:
                    utils.turnOffPerformanceMode();
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        dlog("onCreate");

        mPowerManager = getSystemService(PowerManager.class);

        final IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(PerfModeUtils.ACTION_DISABLE_PERF_MODE);
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public void onDestroy() {
        dlog("onDestroy");
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, PerfModeService.class), UserHandle.CURRENT);
    }

    private static void dlog(String msg) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, msg);
        }
    }
}
