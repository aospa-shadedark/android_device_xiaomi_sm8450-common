/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.display

import android.util.Log
import co.aospa.xiaomiparts.display.DisplayFeatureHidl
import co.aospa.xiaomiparts.utils.dlog

/** Convenient wrapper around xiaomi displayfeature interface. */
object DisplayFeatureWrapper {
    private const val TAG = "DisplayFeatureWrapper"

    fun setFeature(mode: Int, value: Int, cookie: Int) {
        dlog(TAG, "setFeature: mode=$mode, value=$value, cookie=$cookie")

        try {
            DisplayFeatureHidl.setFeature(
                displayId = 0,
                caseId = mode,
                modeId = value,
                cookie = cookie,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set display feature", e)
        }
    }
}
