// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.packageinstaller

import android.content.pm.PackageManager
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.wisso.wizefiles.R
import java.util.Locale

internal class PackageInstallerScreen(
    private val activity: AppCompatActivity,
    onNavigateBack: () -> Unit,
    onShowOptions: () -> Unit
) {
    val toolbar = MaterialToolbar(activity).apply {
        title = activity.getString(R.string.package_installer_title)
        setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        setNavigationOnClickListener { onNavigateBack() }
        menu.add(activity.getString(R.string.package_installer_options)).apply {
            setIcon(R.drawable.ic_installer_options_24)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener { onShowOptions(); true }
        }
    }
    val progress = LinearProgressIndicator(activity).apply { isIndeterminate = true }
    val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(20), dp(24), dp(20))
    }
    val cancelButton = MaterialButton(
        activity,
        null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { setText(android.R.string.cancel) }
    val secondaryButton = MaterialButton(
        activity,
        null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { visibility = android.view.View.GONE }
    val actionButton = MaterialButton(activity).apply { setText(R.string.install) }

    init {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface,
                    0
                )
            )
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            progress,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            ScrollView(activity).apply { addView(content) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(16), dp(8), dp(16), dp(16))
            addView(cancelButton)
            addView(
                secondaryButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            )
            addView(
                actionButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            )
        }
        root.addView(actions)
        activity.setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    fun clear() = content.removeAllViews()

    fun renderBusy(message: String, onCancel: () -> Unit) {
        progress.visibility = android.view.View.VISIBLE
        progress.isIndeterminate = true
        actionButton.visibility = android.view.View.GONE
        secondaryButton.visibility = android.view.View.GONE
        cancelButton.visibility = android.view.View.VISIBLE
        cancelButton.isEnabled = true
        cancelButton.setText(android.R.string.cancel)
        cancelButton.setOnClickListener { onCancel() }
        clear()
        addBody(message)
    }

    fun renderSuccess(canOpen: Boolean, onOpen: () -> Unit, onDone: () -> Unit) {
        progress.visibility = android.view.View.GONE
        clear()
        addHeading(activity.getString(R.string.package_installer_complete))
        addBody(activity.getString(R.string.package_installer_complete_message))
        cancelButton.visibility = android.view.View.GONE
        secondaryButton.visibility = android.view.View.VISIBLE
        secondaryButton.isEnabled = true
        secondaryButton.setText(android.R.string.ok)
        secondaryButton.setOnClickListener { onDone() }
        actionButton.visibility = android.view.View.VISIBLE
        actionButton.isEnabled = canOpen
        actionButton.setText(R.string.app_manager_open)
        actionButton.setOnClickListener { onOpen() }
    }

    fun renderPartial(detail: String, onKeep: () -> Unit, onRetry: () -> Unit) {
        progress.visibility = android.view.View.GONE
        clear()
        addHeading(activity.getString(R.string.package_installer_partial))
        addBody(activity.getString(R.string.package_installer_partial_message))
        addBody(detail)
        secondaryButton.visibility = android.view.View.GONE
        cancelButton.visibility = android.view.View.VISIBLE
        cancelButton.isEnabled = true
        cancelButton.setText(R.string.package_installer_keep_app)
        cancelButton.setOnClickListener { onKeep() }
        actionButton.visibility = android.view.View.VISIBLE
        actionButton.isEnabled = true
        actionButton.setText(R.string.package_installer_retry_obb)
        actionButton.setOnClickListener { onRetry() }
    }

    fun renderFailure(title: String, detail: String, onDone: () -> Unit) {
        progress.visibility = android.view.View.GONE
        clear()
        addHeading(title)
        addBody(detail)
        cancelButton.visibility = android.view.View.GONE
        secondaryButton.visibility = android.view.View.GONE
        actionButton.visibility = android.view.View.VISIBLE
        actionButton.isEnabled = true
        actionButton.setText(android.R.string.ok)
        actionButton.setOnClickListener { onDone() }
    }

    fun renderPlanBody(
        plan: PackageInstallPlan,
        capabilities: PackageInstallCapabilities,
        options: PackageInstallOptions,
        onAppInfo: () -> Unit
    ): PackageInstallActionPresentation {
        secondaryButton.visibility = android.view.View.GONE
        cancelButton.isEnabled = true
        cancelButton.setText(android.R.string.cancel)
        clear()
        addPackageHeading(plan)
        addBody(plan.packageName)
        addBody(
            activity.getString(
                R.string.package_installer_version_format,
                plan.versionName ?: activity.getString(R.string.package_installer_unknown),
                plan.versionCode
            )
        )
        addBody(
            activity.getString(
                R.string.package_installer_sdk_format,
                plan.minimumSdk,
                plan.targetSdk
            )
        )
        val comparison = plan.comparison
        if (comparison.installedVersionCode != null) {
            addChanges(
                activity.getString(R.string.package_installer_version_changes),
                added = listOf("${plan.versionName.orEmpty()} (${plan.versionCode})"),
                removed = listOf(
                    "${comparison.installedVersionName.orEmpty()} " +
                        "(${comparison.installedVersionCode})"
                )
            )
            content.addView(
                MaterialButton(
                    activity,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    setText(R.string.package_installer_app_info)
                    setOnClickListener { onAppInfo() }
                }
            )
        }
        addSection(
            activity.getString(R.string.package_installer_warnings),
            comparison.warnings
        )
        if (!comparison.signerCompatible && options.allowSignatureMismatch) {
            addBody(activity.getString(R.string.package_installer_signature_mismatch_warning))
        }
        if (comparison.action == PackageInstallAction.DOWNGRADE &&
            comparison.signerCompatible
        ) {
            addBody(activity.getString(R.string.package_installer_downgrade_privileged))
        }
        addSection(
            activity.getString(R.string.package_installer_packages),
            listOf(
                activity.getString(
                    R.string.package_installer_package_count_format,
                    plan.apks.size,
                    plan.excludedApks.size
                )
            )
        )
        addChanges(
            activity.getString(R.string.package_installer_permissions),
            comparison.addedPermissions,
            comparison.removedPermissions
        )
        addChanges(
            activity.getString(R.string.package_installer_features),
            comparison.addedFeatures,
            comparison.removedFeatures
        )
        addChanges(
            activity.getString(R.string.package_installer_components),
            comparison.addedComponents,
            comparison.removedComponents
        )
        if (plan.expansions.isNotEmpty()) {
            addChanges(
                activity.getString(R.string.package_installer_expansion_files),
                added = plan.expansions.map { "${it.fileName} (${formatBytes(it.sizeBytes)})" },
                removed = emptyList()
            )
        }
        val actionLabel = when (comparison.action) {
            PackageInstallAction.INSTALL -> R.string.install
            PackageInstallAction.UPDATE -> R.string.package_installer_update
            PackageInstallAction.DOWNGRADE -> R.string.package_installer_downgrade
            PackageInstallAction.REINSTALL -> R.string.package_installer_reinstall
        }
        val enabled = PackageInstallActionPolicy.canProceed(
            comparison,
            capabilities,
            options
        )
        return PackageInstallActionPresentation(actionLabel, enabled)
    }

    fun addHeading(text: String) {
        content.addView(TextView(activity).apply {
            this.text = text
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurface,
                    0
                )
            )
        })
    }

    private fun addPackageHeading(plan: PackageInstallPlan) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val baseApk = plan.apks.single(PackageApk::isBase).stagedFile
        val icon = baseApk?.let { apk ->
            runCatching {
                val info = activity.packageManager.getPackageArchiveInfo(
                    apk.path,
                    PackageManager.GET_META_DATA
                )
                info?.applicationInfo?.let { applicationInfo ->
                    applicationInfo.sourceDir = apk.path
                    applicationInfo.publicSourceDir = apk.path
                    applicationInfo.loadIcon(activity.packageManager)
                }
            }.getOrNull()
        }
        if (icon != null) {
            row.addView(
                ImageView(activity).apply {
                    setImageDrawable(icon)
                    contentDescription = plan.label
                },
                LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(16) }
            )
        }
        row.addView(
            TextView(activity).apply {
                text = plan.label
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurface,
                        0
                    )
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        content.addView(row)
    }

    fun addBody(text: String) {
        content.addView(TextView(activity).apply {
            this.text = text
            textSize = 16f
            setPadding(0, dp(8), 0, 0)
        })
    }

    fun addSection(title: String, items: List<String>) {
        if (items.isEmpty()) return
        addSectionHeading(title)
        items.take(MAXIMUM_VISIBLE_CHANGES).forEach(::addBody)
        addOverflowCount(items.size)
    }

    fun addChanges(title: String, added: List<String>, removed: List<String>) {
        if (added.isEmpty() && removed.isEmpty()) return
        addSectionHeading(title)
        val changes = added.map { ChangeItem("+ $it", true) } +
            removed.map { ChangeItem("− $it", false) }
        changes.take(MAXIMUM_VISIBLE_CHANGES).forEach { change ->
            addChangeBody(change.text, change.isAdded)
        }
        addOverflowCount(changes.size)
    }

    private fun addSectionHeading(title: String) {
        content.addView(TextView(activity).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(4))
        })
    }

    private fun addChangeBody(text: String, isAdded: Boolean) {
        content.addView(TextView(activity).apply {
            this.text = text
            textSize = 16f
            setPadding(0, dp(8), 0, 0)
            setTextColor(
                if (isAdded) {
                    activity.getColor(R.color.package_installer_added)
                } else {
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorError,
                        0
                    )
                }
            )
        })
    }

    private fun addOverflowCount(itemCount: Int) {
        if (itemCount > MAXIMUM_VISIBLE_CHANGES) {
            addBody(
                activity.getString(
                    R.string.package_installer_more_items_format,
                    itemCount - MAXIMUM_VISIBLE_CHANGES
                )
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(
            Locale.getDefault(),
            bytes / (1024.0 * 1024.0 * 1024.0)
        )
        bytes >= 1024L * 1024L -> "%.1f MB".format(
            Locale.getDefault(),
            bytes / (1024.0 * 1024.0)
        )
        else -> "%.1f KB".format(Locale.getDefault(), bytes / 1024.0)
    }

    private companion object {
        const val MAXIMUM_VISIBLE_CHANGES = 100
    }
}

private data class ChangeItem(val text: String, val isAdded: Boolean)

internal data class PackageInstallActionPresentation(
    val labelResource: Int,
    val enabled: Boolean
)
