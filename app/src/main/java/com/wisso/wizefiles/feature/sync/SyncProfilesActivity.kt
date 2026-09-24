package com.wisso.wizefiles.feature.sync

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ActivitySyncProfilesBinding
import com.wisso.wizefiles.databinding.DialogSyncProfileBinding
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.transfer.TransferDetailActivity
import com.wisso.wizefiles.feature.transfer.formatTransferPath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toUriString
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.util.AppLog
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.Executors

class SyncProfilesActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncProfilesBinding
    private lateinit var adapter: SyncProfilesAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingSource: AppPath? = null
    private var destinationLaunchPending = false
    private val destinationPickerLaunchRunnable = Runnable(::launchDestinationPickerIfReady)

    private val sourcePicker =
        registerForActivityResult(FileListActivity.OpenDirectoryContract()) { source ->
            pendingSource = source
            destinationLaunchPending = source != null
            if (source == null) {
                AppLog.i(TAG, "Source folder selection cancelled")
            } else {
                AppLog.i(TAG, "Source folder selected; destination picker queued")
                scheduleDestinationPickerLaunch()
            }
        }
    private val destinationPicker =
        registerForActivityResult(FileListActivity.OpenDirectoryContract()) { destination ->
            val source = pendingSource
            pendingSource = null
            destinationLaunchPending = false
            AppLog.i(
                TAG,
                "Destination picker completed: source=${source != null}, destination=${destination != null}"
            )
            if (source != null && destination != null) showEditor(null, source, destination)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSource = savedInstanceState
            ?.getString(STATE_PENDING_SOURCE_URI)
            ?.toAppPathOrNull()
        destinationLaunchPending =
            savedInstanceState?.getBoolean(STATE_DESTINATION_LAUNCH_PENDING) == true &&
                pendingSource != null

        binding = ActivitySyncProfilesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        adapter = SyncProfilesAdapter(
            onRun =(::planAndOpen),
            onEdit = { showEditor(it, null, null) },
            onToggle =(::toggleSchedule),
            onHistory =(::openHistory),
            onDelete =(::confirmDelete)
        )
        binding.profileList.layoutManager = LinearLayoutManager(this)
        binding.profileList.adapter = adapter
        binding.addProfile.setOnClickListener { launchSourcePicker() }
        val sourceUri = intent.getStringExtra(EXTRA_SOURCE_URI)
        val destinationUri = intent.getStringExtra(EXTRA_DESTINATION_URI)
        if (savedInstanceState == null && sourceUri != null && destinationUri != null) {
            val sourcePath = sourceUri.toAppPathOrNull()
            val destinationPath = destinationUri.toAppPathOrNull()
            if (sourcePath != null && destinationPath != null) {
                binding.root.post { showEditor(null, sourcePath, destinationPath) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPostResume() {
        super.onPostResume()
        scheduleDestinationPickerLaunch()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleDestinationPickerLaunch()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingSource?.let { outState.putString(STATE_PENDING_SOURCE_URI, it.toUriString()) }
        outState.putBoolean(STATE_DESTINATION_LAUNCH_PENDING, destinationLaunchPending)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.root.removeCallbacks(destinationPickerLaunchRunnable)
        }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun launchSourcePicker() {
        pendingSource = null
        destinationLaunchPending = false
        AppLog.i(TAG, "Launching source folder picker")
        sourcePicker.launch(
            FileListActivity.OpenDirectoryRequest(
                title = directoryPickerTitle(R.string.sync_side_source),
                confirmationLabel = getString(
                    R.string.file_list_use_current_directory_as_source
                )
            )
        )
    }

    private fun scheduleDestinationPickerLaunch() {
        if (
            ::binding.isInitialized &&
            destinationLaunchPending &&
            pendingSource != null
        ) {
            binding.root.removeCallbacks(destinationPickerLaunchRunnable)
            binding.root.post(destinationPickerLaunchRunnable)
        }
    }

    private fun launchDestinationPickerIfReady() {
        val source = pendingSource
        val isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (
            !shouldLaunchPendingDestinationPicker(
                hasPendingLaunch = destinationLaunchPending,
                hasPendingSource = source != null,
                isResumed = isResumed,
                hasWindowFocus = hasWindowFocus()
            )
        ) {
            AppLog.d(
                TAG,
                "Destination picker waiting: pending=$destinationLaunchPending, " +
                    "source=${source != null}, resumed=$isResumed, focus=${hasWindowFocus()}"
            )
            return
        }

        destinationLaunchPending = false
        AppLog.i(TAG, "Launching destination folder picker after focus-safe handoff")
        try {
            destinationPicker.launch(
                FileListActivity.OpenDirectoryRequest(
                    title = directoryPickerTitle(R.string.sync_side_destination),
                    confirmationLabel = getString(
                        R.string.file_list_use_current_directory_as_destination
                    )
                )
            )
        } catch (error: RuntimeException) {
            destinationLaunchPending = true
            AppLog.e(TAG, "Destination picker launch failed; keeping it queued", error)
        }
    }

    private fun directoryPickerTitle(sideLabel: Int): String {
        val side = getString(sideLabel).replaceFirstChar(Char::uppercase)
        return "${getString(R.string.file_list_choose_directory)} — $side"
    }

    private fun refresh() {
        val profiles = SyncRepository.profiles()
        adapter.submit(profiles)
        binding.emptyText.isVisible = profiles.isEmpty()
    }

    private fun showEditor(existing: SyncProfile?, source: AppPath?, destination: AppPath?) {
        val profile = existing
        val sourceUri = profile?.sourceUri ?: requireNotNull(source).toUriString()
        val destinationUri = profile?.destinationUri ?: requireNotNull(destination).toUriString()
        val schedule = profile?.let { SyncScheduleCodec.decode(it.scheduleJson) } ?: SyncSchedule()
        val currentFilters = profile?.let { SyncFilterCodec.decode(it.filtersJson) } ?: SyncFilterRules()
        val editor = DialogSyncProfileBinding.inflate(layoutInflater)

        editor.endpoints.text = getString(
            R.string.sync_endpoints_format,
            formatTransferPath(this, sourceUri),
            formatTransferPath(this, destinationUri)
        )
        editor.name.setText(profile?.name ?: getString(R.string.sync_default_profile_name))

        var selectedMode = profile?.mode ?: SyncMode.UPDATE_DESTINATION
        var selectedConflict = profile?.conflictPolicy ?: SyncConflictPolicy.KEEP_BOTH
        var selectedScheduleType = schedule.type
        val weekDays = DayOfWeek.values().toList()
        var selectedDay = schedule.daysOfWeek.firstOrNull() ?: DayOfWeek.SUNDAY

        editor.interval.setText(schedule.intervalMinutes.toString())
        editor.hour.setText(schedule.localTime.hour.toString())
        editor.minute.setText(schedule.localTime.minute.toString())
        editor.wifi.isChecked = schedule.wifiOnly
        editor.charging.isChecked = schedule.chargingOnly
        editor.versionProtection.isChecked =
            profile?.protectionJson?.contains("\"enabled\":true") == true
        editor.includeHidden.isChecked = currentFilters.includeHidden
        editor.excludedExtensions.setText(currentFilters.excludedExtensions.joinToString(","))

        fun updateScheduleVisibility() {
            editor.intervalLayout.isVisible = selectedScheduleType == SyncScheduleType.INTERVAL
            editor.hourLayout.isVisible =
                selectedScheduleType == SyncScheduleType.DAILY ||
                    selectedScheduleType == SyncScheduleType.WEEKLY
            editor.minuteLayout.isVisible = editor.hourLayout.isVisible
            editor.dayLayout.isVisible = selectedScheduleType == SyncScheduleType.WEEKLY
        }

        configureDropdown(
            editor.mode,
            SyncMode.entries,
            ::modeLabel,
            selectedMode
        ) { mode ->
            selectedMode = mode
            if (profile == null && (mode == SyncMode.MIRROR || mode == SyncMode.TWO_WAY)) {
                editor.versionProtection.isChecked = true
            }
        }
        configureDropdown(
            editor.conflict,
            SyncConflictPolicy.entries,
            ::conflictLabel,
            selectedConflict
        ) { selectedConflict = it }
        configureDropdown(
            editor.scheduleType,
            SyncScheduleType.entries,
            ::scheduleLabel,
            selectedScheduleType
        ) {
            selectedScheduleType = it
            updateScheduleVisibility()
        }
        configureDropdown(
            editor.day,
            weekDays,
            { day -> day.name.lowercase().replaceFirstChar(Char::uppercase) },
            selectedDay
        ) { selectedDay = it }
        updateScheduleVisibility()

        MaterialAlertDialogBuilder(this)
            .setTitle(if (profile == null) R.string.sync_create_profile else R.string.sync_edit_profile)
            .setView(editor.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val enabledProtection = editor.versionProtection.isChecked ||
                    profile == null &&
                    (selectedMode == SyncMode.MIRROR || selectedMode == SyncMode.TWO_WAY)
                val newSchedule = SyncSchedule(
                    type = selectedScheduleType,
                    intervalMinutes = editor.interval.text?.toString()?.toLongOrNull()
                        ?.coerceAtLeast(15) ?: 15,
                    localTime = LocalTime.of(
                        editor.hour.text?.toString()?.toIntOrNull()?.coerceIn(0, 23) ?: 2,
                        editor.minute.text?.toString()?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    ),
                    daysOfWeek = setOf(selectedDay),
                    wifiOnly = editor.wifi.isChecked,
                    chargingOnly = editor.charging.isChecked
                )
                val saved = (profile ?: SyncProfile(
                    name = editor.name.text?.toString().orEmpty(),
                    sourceUri = sourceUri,
                    destinationUri = destinationUri,
                    mode = selectedMode
                )).copy(
                    name = editor.name.text?.toString()
                        ?.ifBlank { getString(R.string.sync_default_profile_name) }
                        ?: getString(R.string.sync_default_profile_name),
                    mode = selectedMode,
                    conflictPolicy = selectedConflict,
                    propagateDeletions = selectedMode == SyncMode.TWO_WAY,
                    protectionJson = if (enabledProtection) {
                        "{\"enabled\":true,\"retentionDays\":30,\"versionsPerFile\":5}"
                    } else {
                        "{\"enabled\":false}"
                    },
                    filtersJson = SyncFilterCodec.encode(
                        SyncFilterRules(
                            includeHidden = editor.includeHidden.isChecked,
                            includeSymlinks = false,
                            excludedExtensions = editor.excludedExtensions.text?.toString().orEmpty()
                                .split(',').map(String::trim).filter(String::isNotEmpty).toSet()
                        )
                    ),
                    scheduleJson = SyncScheduleCodec.encode(newSchedule),
                    updatedAtMillis = System.currentTimeMillis()
                )
                SyncRepository.saveProfile(saved)
                SyncScheduler.apply(this, saved.id, newSchedule)
                refresh()
                if (profile == null) planAndOpen(saved)
            }
            .show()
    }

    private fun planAndOpen(profile: SyncProfile) {
        val existing = SyncRepository.runs(profile.id).firstOrNull { !it.state.isTerminal }
        if (existing != null) {
            if (existing.state == SyncRunState.PREVIEW_READY ||
                existing.state == SyncRunState.SAFETY_BLOCKED) {
                startActivity(SyncPreviewActivity.createIntent(this, existing.id))
            } else {
                Toast.makeText(this, R.string.sync_run_already_active, Toast.LENGTH_LONG).show()
            }
            return
        }
        Toast.makeText(this, R.string.sync_planning, Toast.LENGTH_SHORT).show()
        executor.execute {
            runCatching { SyncRunCoordinator().plan(profile.id, SyncRunTrigger.MANUAL) }
                .onSuccess { planned ->
                    runOnUiThread {
                        startActivity(SyncPreviewActivity.createIntent(this, planned.run.id))
                    }
                }
                .onFailure { error ->
                    AppLog.e(
                        TAG,
                        "Synchronization planning failed for profile " + profile.id,
                        error
                    )
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            R.string.sync_planning_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun confirmDelete(profile: SyncProfile) {
        val errorColor = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorError
        )
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sync_delete_profile_named, profile.name))
            .setMessage(
                getString(
                    R.string.sync_delete_profile_message,
                    formatTransferPath(this, profile.sourceUri),
                    formatTransferPath(this, profile.destinationUri)
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (SyncRepository.deleteProfile(profile.id)) {
                    SyncScheduler.cancel(this, profile.id)
                    refresh()
                    Snackbar.make(
                        binding.root,
                        R.string.sync_profile_deleted,
                        Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    Snackbar.make(
                        binding.root,
                        R.string.sync_delete_profile_active,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(errorColor)
    }

    private fun toggleSchedule(profile: SyncProfile) {
        val updated = profile.copy(
            enabled = !profile.enabled,
            updatedAtMillis = System.currentTimeMillis()
        )
        SyncRepository.saveProfile(updated)
        if (updated.enabled) {
            SyncScheduler.apply(this, updated.id, SyncScheduleCodec.decode(updated.scheduleJson))
        } else {
            SyncScheduler.cancel(this, updated.id)
        }
        refresh()
    }

    private fun openHistory(profile: SyncProfile) {
        SyncRepository.runs(profile.id).firstOrNull {
            it.transferOperationId.isNotBlank()
        }?.let { run ->
            startActivity(TransferDetailActivity.createIntent(this, run.transferOperationId))
        }
    }

    private fun <T> configureDropdown(
        view: AutoCompleteTextView,
        values: List<T>,
        label: (T) -> String,
        selected: T,
        onSelected: (T) -> Unit
    ) {
        val labels = values.map(label)
        view.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        )
        view.setText(label(selected), false)
        view.setOnItemClickListener { _, _, position, _ ->
            onSelected(values[position])
        }
    }

    private fun modeLabel(mode: SyncMode) = when (mode) {
        SyncMode.UPDATE_DESTINATION -> getString(R.string.sync_mode_update)
        SyncMode.MIRROR -> getString(R.string.sync_mode_mirror)
        SyncMode.TWO_WAY -> getString(R.string.sync_mode_two_way)
        SyncMode.MOVE_SOURCE -> getString(R.string.sync_mode_move)
    }

    private fun conflictLabel(policy: SyncConflictPolicy) =
        policy.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

    private fun scheduleLabel(type: SyncScheduleType) =
        type.name.lowercase().replaceFirstChar(Char::uppercase)

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    companion object {
        private const val TAG = "SyncProfiles"
        private const val EXTRA_SOURCE_URI = "sync_source_uri"
        private const val EXTRA_DESTINATION_URI = "sync_destination_uri"
        private const val STATE_PENDING_SOURCE_URI = "sync.pending_source_uri"
        private const val STATE_DESTINATION_LAUNCH_PENDING =
            "sync.destination_launch_pending"
        fun createIntent(context: android.content.Context) = Intent(context, SyncProfilesActivity::class.java)

        fun createIntent(
            context: android.content.Context,
            source: AppPath,
            destination: AppPath
        ) = createIntent(context)
            .putExtra(EXTRA_SOURCE_URI, source.toUriString())
            .putExtra(EXTRA_DESTINATION_URI, destination.toUriString())
    }
}

internal fun shouldLaunchPendingDestinationPicker(
    hasPendingLaunch: Boolean,
    hasPendingSource: Boolean,
    isResumed: Boolean,
    hasWindowFocus: Boolean
): Boolean = hasPendingLaunch && hasPendingSource && isResumed && hasWindowFocus
