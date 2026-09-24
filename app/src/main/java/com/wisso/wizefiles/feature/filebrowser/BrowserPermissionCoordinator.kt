package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi
import com.wisso.wizefiles.core.android.compat.checkSelfPermissionCompat
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.util.checkSelfPermission
import com.wisso.wizefiles.util.createManageAppAllFilesAccessPermissionIntent
import com.wisso.wizefiles.util.supportsExternalStorageManager

internal class BrowserPermissionCoordinator(
    private val fragment: FileListPermissionFragment,
    private val viewModel: FileListViewModel,
    private val launchAllFilesAccess: () -> Unit,
    private val launchStoragePermission: () -> Unit,
    private val launchStorageSettings: () -> Unit,
    private val launchNotificationPermission: () -> Unit,
    private val launchNotificationSettings: () -> Unit,
    private val refresh: () -> Unit
) {
    fun ensureStorageAccess() {
        if (viewModel.isStorageAccessRequested) return
        if (Environment::class.supportsExternalStorageManager()) {
            if (!Environment.isExternalStorageManager()) {
                ShowRequestAllFilesAccessRationaleDialogFragment.show(fragment)
                viewModel.isStorageAccessRequested = true
            }
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            fragment.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            if (fragment.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                ShowRequestStoragePermissionRationaleDialogFragment.show(fragment)
            } else {
                launchStoragePermission()
            }
            viewModel.isStorageAccessRequested = true
        }
    }

    fun onAllFilesAccessRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            launchAllFilesAccess()
        } else {
            viewModel.isStorageAccessRequested = false
            ensureNotificationPermission()
        }
    }

    fun onAllFilesAccessResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) refresh()
    }

    fun onStoragePermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) launchStoragePermission()
        else viewModel.isStorageAccessRequested = false
    }

    fun onStoragePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isStorageAccessRequested = false
            refresh()
        } else if (fragment.shouldShowRequestPermissionRationale(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            ShowRequestStoragePermissionRationaleDialogFragment.show(fragment)
        } else {
            ShowRequestStoragePermissionInSettingsRationaleDialogFragment.show(fragment)
        }
    }

    fun onStorageSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) launchStorageSettings()
        else viewModel.isStorageAccessRequested = false
    }

    fun onStorageSettingsResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) refresh()
    }

    fun ensureNotificationPermission() {
        if (viewModel.isNotificationPermissionRequested) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            fragment.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            if (fragment.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                ShowRequestNotificationPermissionRationaleDialogFragment.show(fragment)
            } else {
                launchNotificationPermission()
            }
            viewModel.isNotificationPermissionRequested = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onNotificationPermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) launchNotificationPermission()
        else viewModel.isNotificationPermissionRequested = false
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        } else if (fragment.shouldShowRequestPermissionRationale(
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            ShowRequestNotificationPermissionRationaleDialogFragment.show(fragment)
        } else {
            ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.show(fragment)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onNotificationSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) launchNotificationSettings()
        else viewModel.isNotificationPermissionRequested = false
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun onNotificationSettingsResult(isGranted: Boolean) {
        if (isGranted) viewModel.isNotificationPermissionRequested = false
    }
}

internal class RequestAllFilesAccessContract : ActivityResultContract<Unit, Boolean>() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun createIntent(context: Context, input: Unit): Intent =
        Environment::class.createManageAppAllFilesAccessPermissionIntent(context.packageName)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        Environment.isExternalStorageManager()
}

internal class RequestPermissionInSettingsContract(private val permissionName: String) :
    ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        application.checkSelfPermissionCompat(permissionName) ==
            PackageManager.PERMISSION_GRANTED
}
