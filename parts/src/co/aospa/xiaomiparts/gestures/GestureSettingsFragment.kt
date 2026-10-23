/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.gestures

import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import co.aospa.xiaomiparts.R
import co.aospa.xiaomiparts.utils.dlog

/** Settings for the gesture hardware available on this device. */
class GestureSettingsFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private val fpDoubleTapPref by lazy { findPreference<ListPreference>(KEY_FP_DOUBLE_TAP)!! }
    private val backDoubleTapPref by lazy { findPreference<ListPreference>(KEY_BACK_DOUBLE_TAP)!! }
    private val backTripleTapPref by lazy { findPreference<ListPreference>(KEY_BACK_TRIPLE_TAP)!! }
    private val actionValues by lazy {
        resources.getStringArray(R.array.gesture_action_values).toSet()
    }
    private var isFingerprintAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.setFragmentResultListener(
            GestureActionDialogFragment.RESULT_KEY, this,
        ) { _, result ->
            val key = result.getString(GestureActionDialogFragment.RESULT_PREFERENCE)
                ?: return@setFragmentResultListener
            val value = result.getString(GestureActionDialogFragment.RESULT_VALUE)
                ?: return@setFragmentResultListener
            if (key !in ACTION_KEYS || value !in actionValues) {
                return@setFragmentResultListener
            }
            findPreference<ListPreference>(key)?.let { preference ->
                // Keep each ListPreference's change listener and persistence policy as the
                // only write path, including the non-persistent fingerprint preference.
                if (preference.callChangeListener(value)) preference.value = value
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.gesture_settings)

        isFingerprintAvailable = GestureUtils.isFpDoubleTapAvailable(requireContext())
        val isBackTapAvailable = GestureUtils.isBackTapAvailable(requireContext())
        findPreference<Preference>(KEY_FINGERPRINT_CATEGORY)!!.isVisible = isFingerprintAvailable
        findPreference<Preference>(KEY_BACK_CATEGORY)!!.isVisible = isBackTapAvailable

        findPreference<Preference>(KEY_INTRO)!!.setSummary(
            when {
                isFingerprintAvailable && isBackTapAvailable -> R.string.gestures_intro
                isFingerprintAvailable -> R.string.gestures_intro_fingerprint
                isBackTapAvailable -> R.string.gestures_intro_back
                else -> R.string.gestures_unavailable
            }
        )

        findPreference<Preference>(KEY_FOOTER)!!.apply {
            isVisible = isFingerprintAvailable || isBackTapAvailable
            setTitle(
                when {
                    isFingerprintAvailable && isBackTapAvailable -> R.string.gestures_footer_text
                    isFingerprintAvailable -> R.string.gestures_footer_fingerprint
                    else -> R.string.gestures_footer_back
                }
            )
        }

        listOf(fpDoubleTapPref, backDoubleTapPref, backTripleTapPref).forEach {
            it.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            it.onPreferenceChangeListener = this
        }

        if (isBackTapAvailable) {
            // Recover an obsolete or invalid saved action without showing an empty summary.
            listOf(backDoubleTapPref, backTripleTapPref).forEach {
                if (it.value !in actionValues) it.value = ACTION_NONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isFingerprintAvailable) return

        // Fingerprint actions are stored in Settings.System, not SharedPreferences. Updating a
        // non-persistent ListPreference only refreshes its selection and does not write settings.
        fpDoubleTapPref.value =
            if (GestureUtils.isFpDoubleTapEnabled(requireContext())) {
                GestureUtils.getFpDoubleTapAction(requireContext()).toString()
                    .takeIf { it in actionValues } ?: ACTION_NONE
            } else {
                ACTION_NONE
            }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference !is ListPreference || preference.key !in ACTION_KEYS) {
            super.onDisplayPreferenceDialog(preference)
            return
        }
        if (childFragmentManager.isStateSaved ||
            childFragmentManager.findFragmentByTag(GestureActionDialogFragment.TAG) != null) {
            return
        }
        GestureActionDialogFragment.newInstance(
            preference.key,
            preference.dialogTitle ?: preference.title ?: "",
            preference.value ?: ACTION_NONE,
        ).show(childFragmentManager, GestureActionDialogFragment.TAG)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val value = newValue as? String ?: return false
        val action = value.toIntOrNull()?.takeIf { value in actionValues } ?: return false
        return when (preference.key) {
            KEY_FP_DOUBLE_TAP -> {
                val enabled = action > 0
                dlog(TAG, "onPreferenceChange: action=$action")
                Settings.System.putIntForUser(
                    requireActivity().contentResolver,
                    GestureUtils.SETTING_KEY_FP_DOUBLE_TAP_ENABLE,
                    if (enabled) 1 else 0,
                    UserHandle.USER_CURRENT,
                )
                Settings.System.putIntForUser(
                    requireActivity().contentResolver,
                    GestureUtils.SETTING_KEY_FP_DOUBLE_TAP_ACTION,
                    action,
                    UserHandle.USER_CURRENT,
                )
                GestureUtils.setFingerprintNavigation(enabled)
                true
            }
            KEY_BACK_DOUBLE_TAP,
            KEY_BACK_TRIPLE_TAP -> {
                // BackTapService reads shared prefs directly so we don't need to do anything here
                true
            }
            else -> false
        }
    }

    companion object {
        private const val TAG = "GestureSettingsFragment"
        private const val ACTION_NONE = "0"
        private const val KEY_INTRO = "gesture_intro"
        private const val KEY_FOOTER = "gestures_footer"
        private const val KEY_FINGERPRINT_CATEGORY = "gesture_fingerprint_category"
        private const val KEY_BACK_CATEGORY = "gesture_back_category"
        private const val KEY_FP_DOUBLE_TAP = "fp_double_tap"
        private const val KEY_BACK_DOUBLE_TAP = "back_double_tap"
        private const val KEY_BACK_TRIPLE_TAP = "back_triple_tap"
        private val ACTION_KEYS = setOf(KEY_FP_DOUBLE_TAP, KEY_BACK_DOUBLE_TAP, KEY_BACK_TRIPLE_TAP)
    }
}
