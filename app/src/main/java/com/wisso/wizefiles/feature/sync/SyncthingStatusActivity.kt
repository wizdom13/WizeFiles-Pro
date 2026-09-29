// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wisso.wizefiles.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class SyncthingStatusActivity : AppCompatActivity() {
    private val profileId by lazy { requireNotNull(intent.getStringExtra("profile")) }
    private lateinit var ui: SyncthingUi
    private lateinit var details: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SyncthingUi(this, SyncRepository.profile(profileId)?.name ?: getString(R.string.syncthing_status))
        ui.button(R.string.sync_run_now) {
            SyncthingRunWorker.enqueue(this, profileId,
                SyncRepository.runs(profileId).firstOrNull { it.state == SyncRunState.PAUSED }?.id)
        }
        ui.button(R.string.transfer_pause) { control(false) }
        ui.button(android.R.string.cancel) { control(true) }
        ui.button(R.string.syncthing_recovery) {
            startActivity(SyncthingVersionsActivity.createIntent(this, profileId))
        }
        ui.button(R.string.syncthing_battery_settings) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        details = ui.text("")
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { SyncRecoveryManager.reconcileDetachedRun(profileId) }
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val snapshot = withContext(Dispatchers.IO) {
                        runCatching { Triple(SyncthingProgressJournal.read(profileId), SyncthingSessionHistory.records(profileId),
                            SyncthingSessionHistory.milestones(profileId)) }
                            .getOrDefault(Triple(null, emptyList(), JSONObject()))
                    }
                    val content = render(snapshot.first, snapshot.second, snapshot.third)
                    if (details.text.toString() != content) details.text = content
                    delay(2_000)
                }
            }
        }
    }

    private fun control(cancel: Boolean) {
        ui.action(idle = false, block = { SyncthingRunControls.stop(this, profileId, cancel) }) { }
    }

    private fun render(snapshot: JSONObject?, history: List<JSONObject>, milestones: JSONObject): String = buildString {
        val live = snapshot?.optBoolean("active") == true && SyncthingRuntime.get(this@SyncthingStatusActivity).isRunning()
        append(getString(if (live) R.string.syncthing_live else R.string.syncthing_status_stopped)).append('\n')
        history.firstOrNull()?.let { append(label(it.optString("outcome"))).append('\n') }
        if (snapshot != null) {
            append(getString(R.string.syncthing_sampled_at, time(snapshot.optLong("sampledAt")))).append('\n')
            append(label(snapshot.optString("state"))).append('\n')
            append(getString(R.string.syncthing_pending, snapshot.optLong("pendingItems"),
                bytes(snapshot.optLong("pendingBytes")))).append('\n')
            append(getString(R.string.syncthing_wire_bytes, bytes(snapshot.optLong("receivedBytes")),
                bytes(snapshot.optLong("sentBytes")))).append('\n')
            append(getString(R.string.syncthing_dataset_bytes, bytes(snapshot.optLong("localBytes")),
                bytes(snapshot.optLong("remoteBytes")))).append("\n\n")
            files(this, R.string.syncthing_current_files, snapshot.optJSONArray("current"))
            files(this, R.string.syncthing_queued_files, snapshot.optJSONArray("queued"))
            files(this, R.string.syncthing_completed_files, snapshot.optJSONArray("completed"))
            files(this, R.string.syncthing_file_errors, snapshot.optJSONArray("errors"))
            snapshot.optJSONArray("peers")?.objects()?.forEach { peer ->
                append(peer.optString("name").ifBlank { peer.optString("id") }).append('\n')
                append(getString(if (!live) R.string.syncthing_status_stopped else
                    if (peer.optBoolean("connected")) R.string.syncthing_online else R.string.syncthing_offline)).append('\n')
                append(peer.optString("address")).append(' ').append(peer.optString("type")).append('\n')
                append(getString(R.string.syncthing_pending, peer.optLong("pendingItems"), bytes(peer.optLong("pendingBytes"))))
                    .append('\n')
                files(this, R.string.syncthing_peer_files, peer.optJSONArray("files"))
            }
            append(getString(R.string.syncthing_file_sample_help)).append("\n\n")
        }
        append(getString(R.string.syncthing_last_success, time(milestones.optLong("success")))).append('\n')
        append(getString(R.string.syncthing_last_failure, time(milestones.optLong("failure")))).append("\n\n")
        append(getString(R.string.sync_history)).append('\n')
        history.forEach { row ->
            append(time(row.optLong("startedAt"))).append(" · ").append(label(row.optString("outcome"))).append('\n')
            append(getString(R.string.syncthing_wire_bytes, bytes(row.optLong("receivedBytes")),
                bytes(row.optLong("sentBytes")))).append('\n')
            row.optString("message").takeIf(String::isNotBlank)?.let { append(it).append('\n') }
            if (row.optLong("nextRetryAt") > 0) append(getString(R.string.syncthing_next_retry,
                time(row.optLong("nextRetryAt")))).append('\n')
        }
        val restricted = getSystemService(ActivityManager::class.java).isBackgroundRestricted ||
            !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        if (restricted) append('\n').append(getString(R.string.syncthing_battery_help))
    }

    private fun files(out: StringBuilder, heading: Int, entries: JSONArray?) {
        if (entries == null || entries.length() == 0) return
        out.append(getString(heading)).append('\n')
        entries.objects().forEach { row ->
            out.append(row.optString("name"))
            row.optString("error").takeIf(String::isNotBlank)?.let { out.append(": ").append(it) }
            out.append('\n')
        }
        out.append('\n')
    }
    private fun label(state: String): String = getString(when (state) {
        "COMPLETED" -> R.string.syncthing_completed
        "IDLE" -> R.string.syncthing_idle
        "PENDING" -> R.string.syncthing_pending_state
        "DISCONNECTED", "WAITING_FOR_PEER" -> R.string.syncthing_waiting
        "INTERRUPTED" -> R.string.syncthing_interrupted
        "PAUSED" -> R.string.sync_paused
        "CANCELLED" -> R.string.sync_cancelled
        "FAILED", "ERROR" -> R.string.sync_failed
        "SCANNING" -> R.string.syncthing_scanning
        else -> R.string.sync_running
    })
    private fun bytes(value: Long) = Formatter.formatFileSize(this, value.coerceAtLeast(0))
    private fun time(value: Long): String = if (value > 0) DateFormat.getDateTimeInstance().format(Date(value)) else "—"
    companion object {
        fun createIntent(context: Context, profileId: String) = Intent(context, SyncthingStatusActivity::class.java)
            .putExtra("profile", profileId)
    }
}
