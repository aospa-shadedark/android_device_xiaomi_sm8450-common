/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.gestures

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.ExpressiveDesignEnabledProvider
import com.android.settingslib.collapsingtoolbar.R

/** Device specific gestures, settings activity. */
class GestureSettingsActivity : CollapsingToolbarBaseActivity(), ExpressiveDesignEnabledProvider {

    override fun isExpressiveDesignEnabled() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.content_frame, GestureSettingsFragment(), TAG)
                .commit()
        }
    }

    companion object {
        private const val TAG = "gesture_settings"
    }
}
