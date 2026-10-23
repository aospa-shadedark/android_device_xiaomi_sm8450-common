/*
 * SPDX-FileCopyrightText: 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.gestures

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import co.aospa.xiaomiparts.R
import co.aospa.xiaomiparts.ui.PartsChoice
import co.aospa.xiaomiparts.ui.PartsChoiceAdapter

/** Keeps the requested gesture and selection across activity recreation. */
class GestureActionDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val titles = resources.getTextArray(R.array.gesture_action_entries)
        val summaries = resources.getTextArray(R.array.gesture_action_summaries)
        val values = resources.getStringArray(R.array.gesture_action_values)
        val choices = titles.indices.map { PartsChoice(titles[it], summaries[it]) }
        val checked = values.indexOf(args.getString(ARG_SELECTED_VALUE)).coerceAtLeast(0)

        return AlertDialog.Builder(requireContext())
            .setTitle(args.getCharSequence(ARG_TITLE))
            .setSingleChoiceItems(PartsChoiceAdapter(requireContext(), choices), checked) { _, which ->
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    Bundle().apply {
                        putString(RESULT_PREFERENCE, args.getString(ARG_PREFERENCE))
                        putString(RESULT_VALUE, values[which])
                    },
                )
                dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        const val TAG = "gesture_action_dialog"
        const val RESULT_KEY = "gesture_action_result"
        const val RESULT_PREFERENCE = "preference"
        const val RESULT_VALUE = "value"
        private const val ARG_PREFERENCE = "preference_key"
        private const val ARG_TITLE = "title"
        private const val ARG_SELECTED_VALUE = "selected_value"

        fun newInstance(key: String, title: CharSequence, selectedValue: String) =
            GestureActionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PREFERENCE, key)
                    putCharSequence(ARG_TITLE, title)
                    putString(ARG_SELECTED_VALUE, selectedValue)
                }
            }
    }
}
