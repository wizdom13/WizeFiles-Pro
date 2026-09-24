// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.work.WorkInfo
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.core.app.appSecurityManager
import com.wisso.wizefiles.recyclebin.RecycleBinDisablePolicy
import com.wisso.wizefiles.recyclebin.RecycleBinManager
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.R
import com.wisso.wizefiles.theme.custom.CustomThemeHelper
import com.wisso.wizefiles.theme.night.NightMode
import com.wisso.wizefiles.theme.night.NightModeHelper
import com.wisso.wizefiles.ui.PreferenceFragmentCompat
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.searchindex.SearchIndexStatus
import java.text.NumberFormat

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference
    private lateinit var recycleBinPreference: SwitchPreferenceCompat
    private lateinit var securityPasswordPreference: Preference
    private lateinit var protectBrowserPreference: SwitchPreferenceCompat
    private lateinit var enableBiometricPreference: SwitchPreferenceCompat
    private lateinit var backupSettingsPreference: Preference
    private lateinit var restoreSettingsPreference: Preference
    private lateinit var indexedSearchPreference: SwitchPreferenceCompat
    private lateinit var searchIndexStatusPreference: Preference
    private var applyingSecurityPreferenceChange = false

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        recycleBinPreference = preferenceScreen.findPreference(getString(R.string.pref_key_recycle_bin))!!
        securityPasswordPreference = preferenceScreen.findPreference(getString(R.string.pref_key_security_password))!!
        protectBrowserPreference = preferenceScreen.findPreference(getString(R.string.pref_key_protect_browser))!!
        enableBiometricPreference = preferenceScreen.findPreference(getString(R.string.pref_key_enable_biometric))!!
        backupSettingsPreference = preferenceScreen.findPreference(getString(R.string.pref_key_backup_settings))!!
        restoreSettingsPreference = preferenceScreen.findPreference(getString(R.string.pref_key_restore_settings))!!
        indexedSearchPreference = preferenceScreen.findPreference(getString(R.string.pref_key_indexed_search))!!
        searchIndexStatusPreference = preferenceScreen.findPreference(getString(R.string.pref_key_search_index_status))!!
        disableEmptyIcons(preferenceScreen)
        securityPasswordPreference.setOnPreferenceClickListener {
            showSecurityPasswordDialog()
            true
        }
        backupSettingsPreference.setOnPreferenceClickListener {
            BackupSettingsDialogFragment.show(this)
            true
        }
        restoreSettingsPreference.setOnPreferenceClickListener {
            showRestoreSettingsDialog()
            true
        }
        indexedSearchPreference.setOnPreferenceChangeListener { _, value ->
            val enabled = value as Boolean
            Settings.INDEXED_SEARCH.putValue(enabled)
            indexedSearchPreference.isChecked = enabled
            SearchIndexManager.setEnabled(enabled)
            updateSearchIndexStatus()
            false
        }
        searchIndexStatusPreference.setOnPreferenceClickListener {
            showSearchIndexActions()
            true
        }
        recycleBinPreference.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                true
            } else {
                requestDisableRecycleBin()
                false
            }
        }
        setSecurityToggleListener(protectBrowserPreference, Settings.PROTECT_BROWSER)
        setSecurityToggleListener(enableBiometricPreference, Settings.ENABLE_BIOMETRIC)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity() as SettingsActivity
                activity.setApplicationLocalesPre33(locales)
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is IconShapePreference) {
            if (parentFragmentManager.findFragmentByTag(PREFERENCE_DIALOG_TAG) == null) {
                displayPreferenceDialog(
                    IconShapePreferenceDialogFragmentCompat(),
                    preference.key
                )
            }
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }



    private fun disableEmptyIcons(group: PreferenceGroup) {
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            if (preference.icon == null) {
                preference.isIconSpaceReserved = false
            }
            if (preference is PreferenceGroup) {
                disableEmptyIcons(preference)
            }
        }
    }

    fun showRestoreSettingsDialog(initialUri: Uri? = null) {
        RestoreSettingsDialogFragment.show(this, initialUri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<RecyclerView>(androidx.preference.R.id.recycler_view)?.apply {
            clipToPadding = false
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            updateLayoutParams<ViewGroup.LayoutParams> {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val initialTopPadding = 12.dp
            val initialBottomPadding = 32.dp
            updatePadding(top = initialTopPadding, bottom = initialBottomPadding)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        val owner = viewLifecycleOwner
        Settings.NIGHT_MODE.observe(owner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(owner, this::onBlackNightModeChanged)
        SearchIndexManager.workInfoLiveData().observe(owner) { updateSearchIndexStatus(it) }
        updateSearchIndexStatus()
    }

    private fun updateSearchIndexStatus(workInfos: List<WorkInfo>? = null) {
        if (!Settings.INDEXED_SEARCH.currentValueCompat().orDefaultTrue()) {
            searchIndexStatusPreference.summary = getString(R.string.settings_search_index_status_off)
            searchIndexStatusPreference.isEnabled = true
            return
        }
        val running = workInfos?.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        if (running != null) {
            val count = running.progress.getLong(com.wisso.wizefiles.searchindex.SearchIndexWorker.KEY_INDEXED_COUNT, 0L)
            searchIndexStatusPreference.summary = getString(
                R.string.settings_search_index_status_indexing,
                NumberFormat.getIntegerInstance().format(count)
            )
            return
        }
        Thread {
            val status = runCatching { SearchIndexManager.status() }.getOrNull()
            val size = runCatching { SearchIndexManager.databaseSizeBytes() }.getOrDefault(0L)
            activity?.runOnUiThread {
                if (!isAdded || status == null) return@runOnUiThread
                searchIndexStatusPreference.summary = when (status.state) {
                    SearchIndexStatus.State.EMPTY -> getString(R.string.settings_search_index_status_empty)
                    SearchIndexStatus.State.INDEXING -> getString(
                        R.string.settings_search_index_status_indexing,
                        NumberFormat.getIntegerInstance().format(status.itemCount)
                    )
                    SearchIndexStatus.State.FAILED -> getString(R.string.settings_search_index_status_failed)
                    SearchIndexStatus.State.READY -> listOf(
                        getString(
                            R.string.settings_search_index_status_ready,
                            NumberFormat.getIntegerInstance().format(status.itemCount),
                            Formatter.formatShortFileSize(requireContext(), size),
                            DateUtils.getRelativeTimeSpanString(status.lastUpdatedMillis)
                        ),
                        getString(
                            R.string.settings_search_index_roots,
                            status.indexedRoots.joinToString()
                        )
                    ).joinToString("\n")
                }
            }
        }.start()
    }

    private fun showSearchIndexActions() {
        val actions = arrayOf(
            getString(R.string.settings_search_index_update),
            getString(R.string.settings_search_index_rebuild),
            getString(R.string.settings_search_index_clear)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_search_index_actions_title)
            .setItems(actions) { _, index ->
                when (index) {
                    0 -> SearchIndexManager.updateIndex(userInitiated = true)
                    1 -> SearchIndexManager.updateIndex(rebuild = true, userInitiated = true)
                    2 -> confirmClearSearchIndex()
                }
                updateSearchIndexStatus()
            }
            .show()
    }

    private fun confirmClearSearchIndex() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_search_index_clear_confirm_title)
            .setMessage(R.string.settings_search_index_clear_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_search_index_clear) { _, _ ->
                Thread {
                    SearchIndexManager.clearIndex()
                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        showToast(R.string.settings_search_index_cleared)
                        updateSearchIndexStatus()
                    }
                }.start()
            }
            .show()
    }

    private fun Boolean?.orDefaultTrue(): Boolean = this ?: true

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun onNightModeChanged(nightMode: NightMode) {
        NightModeHelper.sync()
    }

    private fun onBlackNightModeChanged(blackNightMode: Boolean) {
        CustomThemeHelper.sync()
    }

    private fun requestDisableRecycleBin() {
        recycleBinPreference.isEnabled = false
        Thread {
            val hasContents = runCatching { RecycleBinManager.hasRecycleBinContents() }
            requireActivity().runOnUiThread {
                recycleBinPreference.isEnabled = true
                hasContents.onFailure {
                    showToast(it.message ?: getString(R.string.settings_recycle_bin_disable_failed))
                    recycleBinPreference.isChecked = true
                }.onSuccess {
                    if (RecycleBinDisablePolicy.shouldDisableImmediately(it)) {
                        Settings.RECYCLE_BIN.putValue(false)
                        recycleBinPreference.isChecked = false
                    } else {
                        showDisableRecycleBinConfirmationDialog()
                    }
                }
            }
        }.start()
    }

    private fun showDisableRecycleBinConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_recycle_bin_disable_confirmation_title)
            .setMessage(R.string.settings_recycle_bin_disable_confirmation_message)
            .setPositiveButton(R.string.settings_recycle_bin_disable_confirmation_confirm) { _, _ ->
                disableRecycleBinAfterClearing()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                recycleBinPreference.isChecked = true
            }
            .setOnCancelListener {
                recycleBinPreference.isChecked = true
            }
            .show()
    }

    private fun disableRecycleBinAfterClearing() {
        recycleBinPreference.isEnabled = false
        Thread {
            val result = runCatching { RecycleBinManager.clearRecycleBin() }
            requireActivity().runOnUiThread {
                recycleBinPreference.isEnabled = true
                result.onFailure {
                    showToast(it.message ?: getString(R.string.settings_recycle_bin_disable_failed))
                    recycleBinPreference.isChecked = true
                }.onSuccess { summary ->
                    if (!RecycleBinDisablePolicy.canDisableAfterClear(summary)) {
                        showToast(getString(R.string.file_list_recycle_bin_partial_failure, summary.failures.size))
                        recycleBinPreference.isChecked = true
                    } else {
                        Settings.RECYCLE_BIN.putValue(false)
                        recycleBinPreference.isChecked = false
                    }
                }
            }
        }.start()
    }


    private fun showSecurityPasswordDialog() {
        showSecurityPasswordDialog {}
    }

    private fun setSecurityToggleListener(
        preference: SwitchPreferenceCompat,
        setting: SettingLiveData<Boolean>
    ) {
        preference.setOnPreferenceChangeListener { _, newValue ->
            if (applyingSecurityPreferenceChange) {
                return@setOnPreferenceChangeListener true
            }
            val shouldEnable = newValue as Boolean
            if (!shouldEnable || appSecurityManager.hasPassword()) {
                return@setOnPreferenceChangeListener true
            }
            showSecurityPasswordDialog { success ->
                applyingSecurityPreferenceChange = true
                setting.putValue(success)
                preference.isChecked = success
                applyingSecurityPreferenceChange = false
            }
            false
        }
    }

    private fun showSecurityPasswordDialog(onComplete: (Boolean) -> Unit) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val passwordInput = EditText(context).apply {
            hint = getString(R.string.settings_security_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmInput = EditText(context).apply {
            hint = getString(R.string.settings_security_confirm_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val showPassword = CheckBox(context).apply {
            text = getString(R.string.settings_security_show_password)
            setOnCheckedChangeListener { _, checked ->
                val updatedType = if (checked) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                passwordInput.inputType = updatedType
                confirmInput.inputType = updatedType
                passwordInput.setSelection(passwordInput.text.length)
                confirmInput.setSelection(confirmInput.text.length)
            }
        }
        container.addView(passwordInput)
        container.addView(confirmInput)
        container.addView(showPassword)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_security_password_title)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onComplete(false) }
            .setOnCancelListener { onComplete(false) }
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = passwordInput.text?.toString().orEmpty()
            val confirmPassword = confirmInput.text?.toString().orEmpty()
            if (password.isEmpty()) {
                showToast(R.string.settings_security_password_required)
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                showToast(R.string.settings_security_password_mismatch)
                return@setOnClickListener
            }
            if (appSecurityManager.setPassword(password)) {
                showToast(R.string.settings_security_password_saved)
                dialog.dismiss()
                onComplete(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        updateSearchIndexStatus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localePreference.notifyChanged()
        }
    }

    companion object {
        private const val PREFERENCE_DIALOG_TAG =
            "androidx.preference.PreferenceFragment.DIALOG"
    }
}
