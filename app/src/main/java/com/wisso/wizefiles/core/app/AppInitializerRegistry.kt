// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.app

import android.os.Build
import android.webkit.WebView
import jcifs.context.SingletonContext
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.core.imageloader.coil.initializeCoil
import com.wisso.wizefiles.feature.filejobs.fileJobNotificationTemplate
import com.wisso.wizefiles.vault.repairVaultStoragesFromRepository
import com.wisso.wizefiles.hiddenapi.HiddenApi
import com.wisso.wizefiles.provider.FileSystemProviders
import com.wisso.wizefiles.provider.rclone.RcloneEngine
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.storage.FtpServerAuthenticator
import com.wisso.wizefiles.storage.SftpServerAuthenticator
import com.wisso.wizefiles.storage.SmbServerAuthenticator
import com.wisso.wizefiles.storage.StorageVolumeListLiveData
import com.wisso.wizefiles.theme.custom.CustomThemeHelper
import com.wisso.wizefiles.theme.night.NightModeHelper
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.util.backgroundExecutor
import java.util.Properties
import com.wisso.wizefiles.provider.ftp.client.FtpClient as FtpClient
import com.wisso.wizefiles.provider.sftp.client.SftpClient as SftpClient
import com.wisso.wizefiles.provider.smb.client.SmbClient as SmbClient

internal class AppInitializerTask(
    val name: String,
    private val action: () -> Unit
) {
    init {
        require(name.isNotBlank()) { "Initializer name must not be blank" }
    }

    fun run() {
        action()
    }
}

internal data class AppInitializerFailure(
    val taskName: String,
    val exception: Exception
)

internal fun runAppInitializerTasks(
    tasks: List<AppInitializerTask>,
    onStarted: (String) -> Unit = {},
    onCompleted: (String) -> Unit = {},
    onFailed: (AppInitializerFailure) -> Unit = {}
): List<AppInitializerFailure> {
    val failures = mutableListOf<AppInitializerFailure>()
    tasks.forEach { task ->
        onStarted(task.name)
        try {
            task.run()
            onCompleted(task.name)
        } catch (exception: Exception) {
            val failure = AppInitializerFailure(task.name, exception)
            failures += failure
            onFailed(failure)
        }
    }
    return failures
}

internal object AppInitializationHealth {
    @Volatile
    private var failures: List<AppInitializerFailure> = emptyList()

    fun replaceFailures(newFailures: List<AppInitializerFailure>) {
        failures = newFailures.toList()
    }

    fun failures(): List<AppInitializerFailure> = failures
}

internal val appInitializerTasks = listOf(
    AppInitializerTask("initializeUncaughtExceptionLogging", ::initializeUncaughtExceptionLogging),
    AppInitializerTask("registerActivityLifecycleLogger", ::registerActivityLifecycleLogger),
    AppInitializerTask("disableHiddenApiChecks", ::disableHiddenApiChecks),
    AppInitializerTask("initializeWebViewDebugging", ::initializeWebViewDebugging),
    AppInitializerTask("initializeCoil", ::initializeCoil),
    AppInitializerTask("initializeFileSystemProviders", ::initializeFileSystemProviders),
    AppInitializerTask("repairVaultStorageEntries", ::repairVaultStorageEntries),
    AppInitializerTask("initializeLiveDataObjects", ::initializeLiveDataObjects),
    AppInitializerTask("initializeSearchIndex", ::initializeSearchIndex),
    AppInitializerTask("initializeCustomTheme", ::initializeCustomTheme),
    AppInitializerTask("initializeNightMode", ::initializeNightMode),
    AppInitializerTask("createNotificationChannels", ::createNotificationChannels)
)

private fun initializeUncaughtExceptionLogging() {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        AppLog.e("Crash", "Unhandled exception on thread=${thread.name}", throwable)
        previousHandler?.uncaughtException(thread, throwable)
    }
    AppLog.i("Crash", "Installed default uncaught exception handler")
}

private fun registerActivityLifecycleLogger() {
    ActivityLifecycleLogger.register(application)
}

private fun disableHiddenApiChecks() {
    if (!BuildConfig.ENABLE_HIDDEN_API_BYPASS) {
        AppLog.i("AppInit", "Hidden API bypass is disabled by build config")
        return
    }
    runCatching {
        HiddenApi.disableHiddenApiChecks()
    }.onSuccess {
        AppLog.i("AppInit", "Hidden API bypass initialized")
    }.onFailure {
        AppLog.w("AppInit", "Failed to initialize hidden API bypass", it)
    }
}

private fun initializeWebViewDebugging() {
    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}

private fun initializeFileSystemProviders() {
    AppLog.i("AppInit", "Initializing file system providers")
    RcloneEngine.initialize(application, secretStore)
    FileSystemProviders.install()
    FileSystemProviders.overflowWatchEvents = true
    backgroundExecutor.execute {
        SingletonContext.init(
            Properties().apply {
                setProperty("jcifs.netbios.cachePolicy", "0")
                setProperty("jcifs.smb.client.maxVersion", "SMB1")
            }
        )
    }
    FtpClient.authenticator = FtpServerAuthenticator
    SftpClient.authenticator = SftpServerAuthenticator
    SmbClient.authenticator = SmbServerAuthenticator
}

private fun repairVaultStorageEntries() {
    AppLog.i("AppInit", "Repairing vault storage entries")
    repairVaultStoragesFromRepository()
}

private fun initializeLiveDataObjects() {
    AppLog.i("AppInit", "Initializing LiveData objects")
    StorageVolumeListLiveData.value
    Settings.FILE_LIST_DEFAULT_DIRECTORY.value
}

private fun initializeSearchIndex() {
    backgroundExecutor.execute { SearchIndexManager.initialize() }
}

private fun initializeCustomTheme() {
    AppLog.i("AppInit", "Initializing custom theme")
    CustomThemeHelper.initialize(application)
}

private fun initializeNightMode() {
    AppLog.i("AppInit", "Initializing night mode")
    NightModeHelper.initialize(application)
}

private fun createNotificationChannels() {
    AppLog.i("AppInit", "Creating notification channels if needed")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannels(
            listOf(
                backgroundActivityStartNotificationTemplate.channelTemplate,
                fileJobNotificationTemplate.channelTemplate
            ).map { it.create(application) }
        )
    }
}
