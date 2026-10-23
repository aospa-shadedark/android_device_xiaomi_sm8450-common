/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.thermal

import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import co.aospa.xiaomiparts.R
import co.aospa.xiaomiparts.thermal.ThermalUtils.ThermalState
import co.aospa.xiaomiparts.ui.PartsChoice
import co.aospa.xiaomiparts.ui.PartsChoiceAdapter
import co.aospa.xiaomiparts.utils.dlog
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import java.text.Collator

/** Thermal preferences and searchable per-app profiles in one scrolling list. */
class ThermalSettingsFragment : SettingsBasePreferenceFragment() {

    private lateinit var launcherApps: LauncherApps
    private lateinit var thermalUtils: ThermalUtils
    private val appsAdapter = AppsAdapter()
    private val sectionAdapter = AppsSectionAdapter()
    private val statusAdapter = StatusAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val handlerThread = HandlerThread(TAG).apply { start() }
    private val bgHandler = Handler(handlerThread.looper)
    private var entries: List<AppEntry> = emptyList()
    private var visibleEntries: List<AppEntry> = emptyList()
    private var searchQuery = ""
    private var customizedOnly = false
    private var isLoaded = false
    private var isLoading = false
    private var loadFailed = false
    private var appsDirty = true
    private var loadGeneration = 0

    private val launcherAppsCallback =
        object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = reloadApps(user)
            override fun onPackageAdded(packageName: String, user: UserHandle) = reloadApps(user)
            override fun onPackageChanged(packageName: String, user: UserHandle) = reloadApps(user)
            override fun onPackagesAvailable(
                packageNames: Array<String>, user: UserHandle, replacing: Boolean
            ) = reloadApps(user)
            override fun onPackagesUnavailable(
                packageNames: Array<String>, user: UserHandle, replacing: Boolean
            ) = reloadApps(user)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        searchQuery = savedInstanceState?.getString(STATE_QUERY).orEmpty()
        customizedOnly = savedInstanceState?.getBoolean(STATE_CUSTOMIZED) ?: false
        super.onCreate(savedInstanceState)
        launcherApps = requireContext().getSystemService(LauncherApps::class.java)!!
        childFragmentManager.setFragmentResultListener(PROFILE_RESULT, this) { _, result ->
            val packageName = result.getString(PROFILE_PACKAGE) ?: return@setFragmentResultListener
            val profile = result.getInt(PROFILE_STATE, AUTOMATIC_PROFILE)
            if (profile == AUTOMATIC_PROFILE) {
                thermalUtils.removePackage(packageName)
            } else {
                thermalUtils.writePackage(packageName, profile)
            }
            refreshProfiles()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.thermal_settings)
        thermalUtils = ThermalUtils.getInstance(requireContext())
        findPreference<MainSwitchPreference>(THERMAL_ENABLE_KEY)!!.apply {
            isChecked = thermalUtils.enabled
            setOnPreferenceChangeListener { _, newValue ->
                thermalUtils.enabled = newValue as Boolean
                if (thermalUtils.enabled && (!isLoaded || appsDirty) && !isLoading) {
                    loadApps()
                } else {
                    updateAppContent()
                }
                true
            }
        }
        updateAppContent()
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> =
        ConcatAdapter(super.onCreateAdapter(preferenceScreen), sectionAdapter, statusAdapter, appsAdapter)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.apply {
            clipToPadding = false
            setPaddingRelative(
                paddingStart, paddingTop, paddingEnd,
                (24 * resources.displayMetrics.density).toInt()
            )
        }
        launcherApps.registerCallback(launcherAppsCallback, mainHandler)
        if (thermalUtils.enabled && (!isLoaded || appsDirty)) loadApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_QUERY, searchQuery)
        outState.putBoolean(STATE_CUSTOMIZED, customizedOnly)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        // Default browser/dialer roles can change while another settings page is open.
        // Reclassify in the background so Automatic continues to show the effective profile.
        if (thermalUtils.enabled && isLoaded) loadApps()
    }

    override fun onDestroyView() {
        launcherApps.unregisterCallback(launcherAppsCallback)
        ++loadGeneration
        bgHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        if (isLoading) appsDirty = true
        isLoading = false
        super.onDestroyView()
    }

    override fun onDestroy() {
        handlerThread.quitSafely()
        super.onDestroy()
    }

    private fun reloadApps(user: UserHandle) {
        if (user != Process.myUserHandle()) return
        appsDirty = true
        if (thermalUtils.enabled && view != null) loadApps()
    }

    /** Refresh only assignments; cached app metadata and automatic classification stay intact. */
    fun refreshProfiles() {
        val overrides = thermalUtils.getPackageOverrides()
        entries = entries.map { it.copy(overrideState = overrides[it.packageName]) }
        updateAppContent()
    }

    private fun updateAppContent() {
        val query = searchQuery.trim()
        visibleEntries = entries.filter { entry ->
            (!customizedOnly || entry.overrideState != null) &&
                (query.isEmpty() || entry.label.contains(query, ignoreCase = true) ||
                    entry.packageName.contains(query, ignoreCase = true))
        }
        appsAdapter.stateRestorationPolicy =
            if (thermalUtils.enabled && !isLoaded && !loadFailed) {
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT
            } else {
                RecyclerView.Adapter.StateRestorationPolicy.ALLOW
            }
        sectionAdapter.update()
        statusAdapter.update()
        appsAdapter.updateEntries(
            if (thermalUtils.enabled && isLoaded) visibleEntries else emptyList()
        )
    }

    private fun loadApps() {
        val generation = ++loadGeneration
        val locale = resources.configuration.locales[0]
        isLoading = true
        loadFailed = false
        updateAppContent()
        bgHandler.removeCallbacksAndMessages(null)
        bgHandler.post {
            val result = runCatching {
                val collator = Collator.getInstance(locale)
                launcherApps.getActivityList(null, Process.myUserHandle())
                    .distinctBy { it.componentName.packageName }
                    .map { it.toAppEntry() }
                    .sortedWith { first, second ->
                        collator.compare(first.label, second.label).takeIf { it != 0 }
                            ?: first.packageName.compareTo(second.packageName)
                    }
            }
            mainHandler.post applyApps@{
                if (view == null || generation != loadGeneration) return@applyApps
                isLoading = false
                result.onSuccess { loaded ->
                    dlog(TAG, "loaded ${loaded.size} apps")
                    entries = loaded
                    isLoaded = true
                    appsDirty = false
                    // An assignment may have changed while app metadata was being loaded.
                    refreshProfiles()
                }.onFailure {
                    Log.e(TAG, "Unable to load apps", it)
                    loadFailed = true
                    updateAppContent()
                }
            }
        }
    }

    private fun LauncherActivityInfo.toAppEntry(): AppEntry {
        val packageName = componentName.packageName
        return AppEntry(
            packageName = packageName,
            label = label.toString(),
            icon = getIcon(0),
            defaultState = thermalUtils.getDefaultStateForPackage(packageName),
        )
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable,
        val defaultState: ThermalState,
        val overrideState: ThermalState? = null,
    )

    private fun profileLabel(entry: AppEntry): String = entry.overrideState?.let {
        getString(R.string.thermal_custom_profile, getString(it.label))
    } ?: getString(R.string.thermal_automatic_profile, getString(entry.defaultState.label))

    private fun chooseProfile(entry: AppEntry) {
        if (childFragmentManager.isStateSaved ||
            childFragmentManager.findFragmentByTag(PROFILE_DIALOG) != null) return
        ThermalProfileDialogFragment.newInstance(
            entry.packageName, entry.label, entry.defaultState.id,
            entry.overrideState?.id ?: AUTOMATIC_PROFILE,
        ).show(childFragmentManager, PROFILE_DIALOG)
    }

    private inner class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val search: EditText = view.findViewById(R.id.thermal_app_search)
        val clear: ImageButton = view.findViewById(R.id.thermal_clear_search)
        val customized: CheckBox = view.findViewById(R.id.thermal_customized_only)
        val count: TextView = view.findViewById(R.id.thermal_apps_count)

        init {
            // Keep the field's own saved text from overwriting the fragment's query on rotation.
            search.isSaveEnabled = false
            customized.isSaveEnabled = false
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString().orEmpty()
                    if (searchQuery == query) return
                    searchQuery = query
                    updateAppContent()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            search.setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                search.context.getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(search.windowToken, 0)
                search.clearFocus()
                true
            }
            clear.setOnClickListener { search.text.clear() }
            customized.setOnCheckedChangeListener { _, checked ->
                if (customizedOnly != checked) {
                    customizedOnly = checked
                    updateAppContent()
                }
            }
        }
    }

    private inner class AppsSectionAdapter : RecyclerView.Adapter<SectionViewHolder>() {
        private var shown = false

        override fun getItemCount() = if (shown) 1 else 0

        fun update() {
            val wasShown = shown
            shown = thermalUtils.enabled
            when {
                shown && !wasShown -> notifyItemInserted(0)
                !shown && wasShown -> notifyItemRemoved(0)
                shown -> notifyItemChanged(0, HEADER_CONTENT)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            SectionViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.thermal_apps_header, parent, false))

        override fun onBindViewHolder(
            holder: SectionViewHolder, position: Int, payloads: MutableList<Any>
        ) = onBindViewHolder(holder, position)

        override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
            if (holder.search.text.toString() != searchQuery) {
                holder.search.setText(searchQuery)
                holder.search.setSelection(searchQuery.length)
            }
            holder.clear.isVisible = searchQuery.isNotEmpty()
            holder.customized.isChecked = customizedOnly
            holder.count.text = getString(R.string.thermal_app_count, visibleEntries.size, entries.size)
            holder.count.isVisible = isLoaded
        }
    }

    private enum class Status { DISABLED, LOADING, REFRESHING, ERROR, REFRESH_ERROR, EMPTY, NO_RESULTS }

    private fun status(): Status? = when {
        !thermalUtils.enabled -> Status.DISABLED
        isLoading && !isLoaded -> Status.LOADING
        isLoading -> Status.REFRESHING
        loadFailed && !isLoaded -> Status.ERROR
        loadFailed -> Status.REFRESH_ERROR
        isLoaded && entries.isEmpty() -> Status.EMPTY
        isLoaded && visibleEntries.isEmpty() -> Status.NO_RESULTS
        else -> null
    }

    private class StatusViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val progress: View = view.findViewById(R.id.thermal_loading_progress)
        val message: TextView = view.findViewById(R.id.thermal_loading_message)
        val action: Button = view.findViewById(R.id.thermal_status_action)
    }

    private inner class StatusAdapter : RecyclerView.Adapter<StatusViewHolder>() {
        private var currentStatus: Status? = null

        override fun getItemCount() = if (currentStatus != null) 1 else 0

        fun update() {
            val previous = currentStatus
            currentStatus = status()
            when {
                previous == null && currentStatus != null -> notifyItemInserted(0)
                previous != null && currentStatus == null -> notifyItemRemoved(0)
                previous != currentStatus -> notifyItemChanged(0, STATUS_CONTENT)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            StatusViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.thermal_loading, parent, false))

        override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
            val current = currentStatus ?: return
            holder.progress.isVisible = current == Status.LOADING || current == Status.REFRESHING
            holder.message.setText(when (current) {
                Status.DISABLED -> R.string.thermal_profiles_disabled
                Status.LOADING -> R.string.thermal_loading
                Status.REFRESHING -> R.string.thermal_refreshing
                Status.ERROR -> R.string.thermal_apps_load_error
                Status.REFRESH_ERROR -> R.string.thermal_apps_refresh_error
                Status.EMPTY -> R.string.thermal_no_apps
                Status.NO_RESULTS -> R.string.thermal_no_matches
            })
            val retry = current == Status.ERROR || current == Status.REFRESH_ERROR || current == Status.EMPTY
            holder.action.isVisible = retry || current == Status.NO_RESULTS
            holder.action.setText(if (retry) R.string.thermal_retry else R.string.thermal_clear_filters)
            holder.action.setOnClickListener {
                if (retry) {
                    loadApps()
                } else {
                    searchQuery = ""
                    customizedOnly = false
                    updateAppContent()
                }
            }
        }
    }

    private inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.app_name)
        val packageName: TextView = view.findViewById(R.id.app_package)
        val profile: TextView = view.findViewById(R.id.app_profile)
        val icon: ImageView = view.findViewById(R.id.app_icon)

        init {
            view.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            }
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>() {
        private var items: List<AppEntry> = emptyList()

        override fun getItemCount() = items.size

        fun updateEntries(updated: List<AppEntry>) {
            val previous = items
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = previous.size
                override fun getNewListSize() = updated.size
                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                    previous[oldPosition].packageName == updated[newPosition].packageName
                override fun areContentsTheSame(oldPosition: Int, newPosition: Int) =
                    previous[oldPosition] == updated[newPosition]
            })
            items = updated
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AppViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.thermal_list_item, parent, false))

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val entry = items[position]
            holder.title.text = entry.label
            holder.packageName.text = entry.packageName
            holder.profile.text = profileLabel(entry)
            holder.profile.isActivated = entry.overrideState != null
            holder.icon.setImageDrawable(entry.icon)
            holder.itemView.contentDescription =
                getString(R.string.thermal_app_accessibility, entry.label, entry.packageName)
            holder.itemView.stateDescription = profileLabel(entry)
            holder.itemView.setOnClickListener { chooseProfile(entry) }
        }

        override fun onViewRecycled(holder: AppViewHolder) {
            holder.itemView.setOnClickListener(null)
            super.onViewRecycled(holder)
        }
    }

    companion object {
        private const val TAG = "ThermalSettingsFragment"
        private const val THERMAL_ENABLE_KEY = "thermal_enable"
        private const val STATE_QUERY = "thermal_search_query"
        private const val STATE_CUSTOMIZED = "thermal_customized_only"
        private const val PROFILE_DIALOG = "thermal_profile_dialog"
        private const val HEADER_CONTENT = "thermal_header_content"
        private const val STATUS_CONTENT = "thermal_status_content"
        internal const val PROFILE_RESULT = "thermal_profile_result"
        internal const val PROFILE_PACKAGE = "thermal_profile_package"
        internal const val PROFILE_STATE = "thermal_profile_state"
        internal const val AUTOMATIC_PROFILE = -1
    }
}

/** FragmentManager restores the checked profile dialog across configuration changes. */
class ThermalProfileDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val states = ThermalState.values()
        val automaticState = states.first { it.id == args.getInt(ARG_AUTOMATIC) }
        val choices = listOf(
            PartsChoice(
                getString(R.string.thermal_automatic_profile, getString(automaticState.label)),
                getString(R.string.thermal_profile_automatic_summary),
            )
        ) + states.map { state ->
            val summary = when (state) {
                ThermalState.BENCHMARK -> R.string.thermal_profile_benchmark_summary
                ThermalState.BROWSER -> R.string.thermal_profile_browser_summary
                ThermalState.CAMERA -> R.string.thermal_profile_camera_summary
                ThermalState.DIALER -> R.string.thermal_profile_dialer_summary
                ThermalState.GAMING -> R.string.thermal_profile_gaming_summary
                ThermalState.NAVIGATION -> R.string.thermal_profile_navigation_summary
                ThermalState.VIDEOCALL -> R.string.thermal_profile_videocall_summary
                ThermalState.VIDEO -> R.string.thermal_profile_video_summary
                ThermalState.DEFAULT -> R.string.thermal_profile_default_summary
            }
            PartsChoice(getString(state.label), getString(summary))
        }
        val selected = args.getInt(ThermalSettingsFragment.PROFILE_STATE)
        val checked = if (selected == ThermalSettingsFragment.AUTOMATIC_PROFILE) 0 else
            states.indexOfFirst { it.id == selected } + 1
        return AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.thermal_profile_for_app, args.getString(ARG_LABEL)))
            .setSingleChoiceItems(PartsChoiceAdapter(requireContext(), choices), checked) { _, position ->
                parentFragmentManager.setFragmentResult(ThermalSettingsFragment.PROFILE_RESULT,
                    bundleOf(
                        ThermalSettingsFragment.PROFILE_PACKAGE to
                            args.getString(ThermalSettingsFragment.PROFILE_PACKAGE),
                        ThermalSettingsFragment.PROFILE_STATE to if (position == 0)
                            ThermalSettingsFragment.AUTOMATIC_PROFILE else states[position - 1].id,
                    ))
                dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        private const val ARG_LABEL = "app_label"
        private const val ARG_AUTOMATIC = "automatic_profile"

        fun newInstance(packageName: String, label: String, automaticState: Int, selectedState: Int) =
            ThermalProfileDialogFragment().apply {
                arguments = bundleOf(
                    ThermalSettingsFragment.PROFILE_PACKAGE to packageName,
                    ARG_LABEL to label,
                    ARG_AUTOMATIC to automaticState,
                    ThermalSettingsFragment.PROFILE_STATE to selectedState,
                )
            }
    }
}
