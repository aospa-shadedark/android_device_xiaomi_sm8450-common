/*
 * SPDX-FileCopyrightText: 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import co.aospa.xiaomiparts.R
import com.android.settingslib.widget.GroupSectionDividerMixin
import com.android.settingslib.widget.NormalPaddingMixin

/** An informational section header that follows the system's expressive Settings theme. */
class PartsIntroPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes),
    GroupSectionDividerMixin,
    NormalPaddingMixin {

    init {
        layoutResource = R.layout.parts_intro
        isSelectable = false
        isPersistent = false
        isIconSpaceReserved = false
        setSingleLineTitle(false)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false
        holder.findViewById(R.id.parts_intro_icon_badge)?.visibility =
            if (icon == null) View.GONE else View.VISIBLE
    }
}
