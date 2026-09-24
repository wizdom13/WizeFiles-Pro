package com.wisso.wizefiles.feature.filebrowser

import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment

abstract class FileListPermissionFragment : Fragment(),
    ShowRequestAllFilesAccessRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionRationaleDialogFragment.Listener,
    ShowRequestNotificationPermissionInSettingsRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionRationaleDialogFragment.Listener,
    ShowRequestStoragePermissionInSettingsRationaleDialogFragment.Listener {

    protected abstract val permissionViewModel:FileListViewModel
    protected abstract fun refreshAfterPermissionChange()

    private val requestAllFilesAccessLauncher:ActivityResultLauncher<Unit> =
        registerForActivityResult(
            RequestAllFilesAccessContract(),
            ::onRequestAllFilesAccessResult
        )
    private val requestStoragePermissionLauncher:ActivityResultLauncher<String> =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
            ::onRequestStoragePermissionResult
        )
    private val requestStoragePermissionInSettingsLauncher:ActivityResultLauncher<Unit> =
        registerForActivityResult(
            RequestPermissionInSettingsContract(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            ::onRequestStoragePermissionInSettingsResult
        )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionLauncher:ActivityResultLauncher<String> =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
            ::onRequestNotificationPermissionResult
        )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionInSettingsLauncher:ActivityResultLauncher<Unit> =
        registerForActivityResult(
            RequestPermissionInSettingsContract(
                android.Manifest.permission.POST_NOTIFICATIONS
            ),
            ::onRequestNotificationPermissionInSettingsResult
        )

    private val permissionCoordinator by lazy {
        BrowserPermissionCoordinator(
            fragment=this,
            viewModel=permissionViewModel,
            launchAllFilesAccess={requestAllFilesAccessLauncher.launch(Unit)},
            launchStoragePermission={
                requestStoragePermissionLauncher.launch(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            },
            launchStorageSettings={
                requestStoragePermissionInSettingsLauncher.launch(Unit)
            },
            launchNotificationPermission={
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermissionLauncher.launch(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            },
            launchNotificationSettings={
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermissionInSettingsLauncher.launch(Unit)
                }
            },
            refresh=::refreshAfterPermissionChange
        )
    }

    protected fun ensureStorageAccess() =
        permissionCoordinator.ensureStorageAccess()

    protected fun ensureNotificationPermission() =
        permissionCoordinator.ensureNotificationPermission()

    override fun onShowRequestAllFilesAccessRationaleResult(shouldRequest:Boolean) =
        permissionCoordinator.onAllFilesAccessRationaleResult(shouldRequest)

    private fun onRequestAllFilesAccessResult(isGranted:Boolean) =
        permissionCoordinator.onAllFilesAccessResult(isGranted)

    override fun onShowRequestStoragePermissionRationaleResult(shouldRequest:Boolean) =
        permissionCoordinator.onStoragePermissionRationaleResult(shouldRequest)

    private fun onRequestStoragePermissionResult(isGranted:Boolean) =
        permissionCoordinator.onStoragePermissionResult(isGranted)

    override fun onShowRequestStoragePermissionInSettingsRationaleResult(
        shouldRequest:Boolean
    ) = permissionCoordinator.onStorageSettingsRationaleResult(shouldRequest)

    private fun onRequestStoragePermissionInSettingsResult(isGranted:Boolean) =
        permissionCoordinator.onStorageSettingsResult(isGranted)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionRationaleResult(shouldRequest:Boolean) =
        permissionCoordinator.onNotificationPermissionRationaleResult(shouldRequest)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionResult(isGranted:Boolean) =
        permissionCoordinator.onNotificationPermissionResult(isGranted)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onShowRequestNotificationPermissionInSettingsRationaleResult(
        shouldRequest:Boolean
    ) = permissionCoordinator.onNotificationSettingsRationaleResult(shouldRequest)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionInSettingsResult(isGranted:Boolean) =
        permissionCoordinator.onNotificationSettingsResult(isGranted)
}
