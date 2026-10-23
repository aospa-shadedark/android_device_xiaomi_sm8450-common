/*
 * SPDX-FileCopyrightText: 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import co.aospa.xiaomiparts.R

data class PartsChoice(val title: CharSequence, val summary: CharSequence)

/** Native single-choice rows with wrapping supporting text and system tonal colors. */
class PartsChoiceAdapter(context: Context, choices: List<PartsChoice>) : BaseAdapter() {
    private val context = context
    private val inflater = LayoutInflater.from(context)
    private val choices = choices.toList()

    override fun getCount(): Int = choices.size

    override fun getItem(position: Int): PartsChoice = choices[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView as? CheckedTextView
            ?: inflater.inflate(R.layout.parts_choice_item, parent, false) as CheckedTextView
        val choice = getItem(position)
        row.text = SpannableStringBuilder().apply {
            append(choice.title)
            setSpan(
                TextAppearanceSpan(context, R.style.TextAppearance_Parts_ChoiceTitle),
                0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (choice.summary.isNotEmpty()) {
                append('\n')
                val summaryStart = length
                append(choice.summary)
                setSpan(
                    TextAppearanceSpan(context, R.style.TextAppearance_Parts_ChoiceSummary),
                    summaryStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        // AlertDialog's CHOICE_MODE_SINGLE ListView owns the CheckedTextView state,
        // including recycled rows, keyboard navigation and accessibility selection.
        return row
    }
}
