/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.thermal

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import co.aospa.xiaomiparts.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.ExpressiveDesignEnabledProvider

/** Thermal profile settings activity. */
class ThermalSettingsActivity : CollapsingToolbarBaseActivity(), ExpressiveDesignEnabledProvider {

    override fun isExpressiveDesignEnabled() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.setFragmentResultListener(RESET_RESULT, this) { _, _ ->
            ThermalUtils.getInstance(this).resetProfiles()
            (supportFragmentManager.findFragmentByTag(TAG) as? ThermalSettingsFragment)
                ?.refreshProfiles()
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    ThermalSettingsFragment(), TAG,
                )
                .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_RESET, Menu.NONE, R.string.thermal_reset)
            .setIcon(R.drawable.ic_thermal_reset)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_RESET -> {
            if (!supportFragmentManager.isStateSaved &&
                supportFragmentManager.findFragmentByTag(RESET_DIALOG) == null) {
                ThermalResetDialogFragment().show(supportFragmentManager, RESET_DIALOG)
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        private const val TAG = "thermal"
        private const val MENU_RESET = 1001
        private const val RESET_DIALOG = "thermal_reset_dialog"
        internal const val RESET_RESULT = "thermal_reset_result"
    }
}

/** The confirmation stays open, with its listeners restored, after a rotation. */
class ThermalResetDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.thermal_reset)
            .setMessage(R.string.thermal_reset_profiles_message)
            .setPositiveButton(R.string.thermal_reset_action) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    ThermalSettingsActivity.RESET_RESULT, bundleOf(),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
}
