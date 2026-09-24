package com.wisso.wizefiles.feature.packageinstaller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wisso.wizefiles.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class PackageInstallerActivity : AppCompatActivity() {
    private lateinit var screen: PackageInstallerScreen
    private lateinit var operationStore: PackageInstallOperationStore
    private lateinit var capabilities: PackageInstallCapabilities

    private var operationId: String? = null
    private var operationDirectory: File? = null
    private var plan: PackageInstallPlan? = null
    private var sessionId: Int? = null
    private var busy = false
    private var committed = false
    private var cancelRequested = false
    private var options = PackageInstallOptions(
        packageSource = PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        operationStore = PackageInstallOperationStore(this)
        capabilities = PackageInstallCapabilitiesDetector(this).detect()
        screen = PackageInstallerScreen(
            this,
            onNavigateBack = { if (!committed) cancelOperation() else finish() },
            onShowOptions = ::showOptions
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!committed) cancelOperation() else finish()
            }
        })
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == ACTION_INSTALL_STATUS) {
            val id = intent.getStringExtra(EXTRA_OPERATION_ID)
            val stored = id?.let(operationStore::load)
            if (id == null || stored == null) {
                renderFailure(getString(R.string.package_installer_operation_missing))
                return
            }
            operationId = id
            sessionId = stored.sessionId
            operationDirectory = File(stored.sourcePath).parentFile
            options = options.copy(
                installObb = stored.installObb,
                usePrivilegedInstaller = stored.usePrivilegedInstaller
            )
            committed = true
            handleInstallStatus(intent)
            return
        }
        val restoredId = intent.getStringExtra(EXTRA_OPERATION_ID)
        if (restoredId != null) {
            restoreOperation(restoredId)
            return
        }
        val source = intent.data
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        if (source == null || displayName.isNullOrBlank()) {
            renderInspectionFailure(getString(R.string.package_installer_invalid_source))
            return
        }
        beginPreparation(source, displayName)
    }

    private fun beginPreparation(source: Uri, displayName: String) {
        cancelRequested = false
        val id = UUID.randomUUID().toString()
        operationId = id
        val directory = File(noBackupFilesDir, "package-installer/staging/$id")
        operationDirectory = directory
        setBusy(getString(R.string.package_installer_materializing))
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (!directory.mkdirs()) throw IOException("Unable to create package staging")
                    val extension = AndroidPackageInstallerInput.extension(displayName)
                        ?: throw IOException("Unsupported Android package format")
                    val stagedSource = File(directory, "source.$extension")
                    operationStore.save(
                        StoredPackageInstallOperation(
                            id,
                            displayName,
                            stagedSource.path,
                            null,
                            PackageInstallStage.MATERIALIZING,
                            null,
                            null
                        )
                    )
                    PackageSourceMaterializer(contentResolver).materialize(source, stagedSource)
                    prepare(stagedSource, displayName, directory, id)
                }
            }.onSuccess { if (!cancelRequested) renderPlan(it) }
                .onFailure { if (!cancelRequested) renderInspectionFailure(message(it)) }
        }
    }

    private fun restoreOperation(id: String) {
        val stored = operationStore.load(id)
        if (stored == null) {
            renderFailure(getString(R.string.package_installer_operation_missing))
            return
        }
        operationId = id
        sessionId = stored.sessionId
        options = options.copy(
            installObb = stored.installObb,
            usePrivilegedInstaller = stored.usePrivilegedInstaller
        )
        committed = stored.stage.isPostCommit
        val source = File(stored.sourcePath)
        val directory = source.parentFile
        operationDirectory = directory
        if (stored.stage == PackageInstallStage.PARTIALLY_COMPLETED) {
            setBusy(getString(R.string.package_installer_preparing_retry))
        } else {
            setBusy(getString(R.string.package_installer_inspecting))
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    prepare(
                        source,
                        stored.displayName,
                        requireNotNull(directory),
                        id,
                        stored.stage.takeIf { it.isPostCommit }
                    )
                }
            }.onSuccess { restoredPlan ->
                plan = restoredPlan
                when (stored.stage) {
                    PackageInstallStage.PARTIALLY_COMPLETED -> renderPartial(
                        stored.statusMessage ?: getString(R.string.package_installer_obb_failed)
                    )
                    PackageInstallStage.INSTALLING_OBB -> completeAfterApkInstall()
                    PackageInstallStage.COMMITTING -> if (stored.usePrivilegedInstaller) {
                        completeAfterApkInstall()
                    } else {
                        setBusy(getString(R.string.package_installer_waiting_for_android))
                    }
                    PackageInstallStage.AWAITING_CONFIRMATION -> setBusy(
                        getString(R.string.package_installer_waiting_for_confirmation)
                    )
                    PackageInstallStage.COMPLETED -> renderSuccess()
                    else -> renderPlan(restoredPlan)
                }
            }.onFailure { if (!cancelRequested) renderInspectionFailure(message(it)) }
        }
    }

    private fun prepare(
        source: File,
        displayName: String,
        directory: File,
        id: String,
        preservedStage: PackageInstallStage? = null
    ): PackageInstallPlan {
        val current = operationStore.load(id)
        operationStore.save(
            current?.copy(
                displayName = displayName,
                sourcePath = source.path,
                stage = preservedStage ?: PackageInstallStage.INSPECTING,
                sessionId = sessionId,
                statusMessage = null
            ) ?: StoredPackageInstallOperation(
                id,
                displayName,
                source.path,
                null,
                preservedStage ?: PackageInstallStage.INSPECTING,
                sessionId,
                null
            )
        )
        return PackageInstallPreparer(this).prepare(source, displayName, directory, id).also {
            val inspecting = operationStore.load(id)
                ?: throw IOException("Package operation is missing")
            operationStore.save(
                inspecting.copy(
                    packageName = it.packageName,
                    stage = preservedStage ?: PackageInstallStage.REVIEWING,
                    sessionId = sessionId,
                    statusMessage = null
                )
            )
        }
    }

    private fun renderPlan(value: PackageInstallPlan) {
        plan = value
        busy = false
        screen.progress.visibility = View.GONE
        screen.actionButton.visibility = View.VISIBLE
        screen.cancelButton.visibility = View.VISIBLE
        screen.toolbar.title = getString(R.string.package_installer_title)
        val presentation = screen.renderPlanBody(value, capabilities, options) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${value.packageName}")
                )
            )
        }
        screen.actionButton.setText(presentation.labelResource)
        screen.actionButton.isEnabled = presentation.enabled
        screen.actionButton.setOnClickListener { startInstall(value) }
        screen.cancelButton.setOnClickListener { cancelOperation() }
    }

    private fun startInstall(value: PackageInstallPlan) {
        if (busy || committed) return
        setBusy(getString(R.string.package_installer_writing))
        val id = requireNotNull(operationId)
        val stored = operationStore.load(id) ?: run {
            renderFailure(getString(R.string.package_installer_operation_missing))
            return
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val onCreated: (Int) -> Unit = { createdSessionId ->
                            sessionId = createdSessionId
                            operationStore.save(
                                stored.copy(
                                    packageName = value.packageName,
                                    stage = PackageInstallStage.WRITING_APKS,
                                    sessionId = createdSessionId,
                                    installObb = options.installObb,
                                    usePrivilegedInstaller = options.usePrivilegedInstaller
                                )
                            )
                    }
                    val onProgress: (Long, Long) -> Unit = { written, total ->
                            runOnUiThread {
                                screen.progress.isIndeterminate = false
                                screen.progress.max = 1000
                                screen.progress.progress = if (total == 0L) 0 else
                                    ((written * 1000L) / total).toInt().coerceIn(0, 1000)
                            }
                    }
                    val createdSessionId = if (options.usePrivilegedInstaller) {
                        PrivilegedPackageInstallerBackend().install(
                            value,
                            options,
                            onCreated,
                            onProgress
                        )
                    } else {
                        PackageInstallerSessionBackend(this@PackageInstallerActivity).install(
                            value,
                            options,
                            statusSender(id),
                            onCreated,
                            onProgress
                        )
                    }
                    createdSessionId.also {
                        operationStore.save(
                            stored.copy(
                                packageName = value.packageName,
                                stage = PackageInstallStage.COMMITTING,
                                sessionId = it
                            )
                        )
                    }
                }
            }.onSuccess {
                if (cancelRequested) return@onSuccess
                if (options.usePrivilegedInstaller) {
                    committed = true
                    completeAfterApkInstall()
                } else {
                    committed = true
                    setBusy(getString(R.string.package_installer_waiting_for_android))
                }
            }.onFailure { if (!cancelRequested) renderFailure(message(it)) }
        }
    }

    private fun handleInstallStatus(statusIntent: Intent) {
        val status = statusIntent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    statusIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") statusIntent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmation == null) {
                    renderFailure(getString(R.string.package_installer_confirmation_missing))
                    return
                }
                updateStoredStage(PackageInstallStage.AWAITING_CONFIRMATION, null)
                committed = true
                setBusy(getString(R.string.package_installer_waiting_for_confirmation))
                startActivity(confirmation)
            }
            PackageInstaller.STATUS_SUCCESS -> completeAfterApkInstall()
            else -> {
                val detail = statusIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: getString(R.string.package_installer_android_failed)
                updateStoredStage(PackageInstallStage.FAILED, detail)
                renderFailure(detail)
            }
        }
    }

    private fun completeAfterApkInstall() {
        updateStoredStage(PackageInstallStage.INSTALLING_OBB, null)
        setBusy(getString(R.string.package_installer_installing_obb))
        lifecycleScope.launch {
            runCatching {
                val activePlan = plan ?: withContext(Dispatchers.IO) {
                    val stored = operationStore.load(requireNotNull(operationId))
                        ?: throw IOException("Package operation is missing")
                    val source = File(stored.sourcePath)
                    operationDirectory = source.parentFile
                    prepare(
                        source,
                        stored.displayName,
                        source.parentFile ?: throw IOException("Package staging is missing"),
                        stored.operationId,
                        stored.stage
                    )
                }
                plan = activePlan
                if (options.installObb && activePlan.expansions.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        if (options.usePrivilegedInstaller) {
                            PrivilegedObbInstallCoordinator().install(activePlan)
                        } else {
                            ObbInstallCoordinator().install(activePlan)
                        }
                    }
                }
            }.onSuccess {
                updateStoredStage(PackageInstallStage.COMPLETED, null)
                renderSuccess()
                cleanupStaging()
            }.onFailure { exception ->
                val detail = message(exception)
                updateStoredStage(PackageInstallStage.PARTIALLY_COMPLETED, detail)
                renderPartial(detail)
            }
        }
    }

    private fun retryObb() {
        val activePlan = plan ?: return
        updateStoredStage(PackageInstallStage.INSTALLING_OBB, null)
        setBusy(getString(R.string.package_installer_installing_obb))
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (options.usePrivilegedInstaller) {
                        PrivilegedObbInstallCoordinator().install(activePlan)
                    } else {
                        ObbInstallCoordinator().install(activePlan)
                    }
                }
            }.onSuccess {
                updateStoredStage(PackageInstallStage.COMPLETED, null)
                renderSuccess()
                cleanupStaging()
            }.onFailure { renderPartial(message(it)) }
        }
    }

    private fun renderSuccess() {
        busy = false
        val launchIntent = plan?.packageName?.let(::mainLauncherIntent)
        screen.renderSuccess(
            canOpen = launchIntent != null,
            onOpen = {
                launchIntent?.let { intent ->
                    if (runCatching { startActivity(intent) }.isSuccess) {
                        cleanupStaging()
                        finish()
                    }
                }
            },
            onDone = {
                cleanupStaging()
                finish()
            }
        )
    }

    private fun mainLauncherIntent(packageName: String): Intent? {
        val queryIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        @Suppress("DEPRECATION")
        val launcher = packageManager.queryIntentActivities(queryIntent, 0).firstOrNull()
            ?: return null
        val activityInfo = launcher.activityInfo ?: return null
        return Intent(queryIntent)
            .setClassName(activityInfo.packageName, activityInfo.name)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
    }

    private fun renderPartial(detail: String) {
        busy = false
        screen.renderPartial(
            detail,
            onKeep = { cleanupStaging(); finish() },
            onRetry = ::retryObb
        )
    }

    private fun renderFailure(detail: String) {
        busy = false
        screen.renderFailure(getString(R.string.package_installer_failed), detail) {
            cleanupStaging()
            finish()
        }
    }

    private fun renderInspectionFailure(detail: String) {
        busy = false
        screen.renderFailure(getString(R.string.package_installer_inspection_failed), detail) {
            cleanupStaging()
            finish()
        }
    }

    private fun setBusy(message: String) {
        busy = true
        screen.renderBusy(message) { if (!committed) cancelOperation() else finish() }
    }

    private fun cancelOperation() {
        if (committed) {
            finish()
            return
        }
        cancelRequested = true
        sessionId?.let {
            runCatching {
                if (options.usePrivilegedInstaller) {
                    PrivilegedPackageInstallerBackend().abandon(it)
                } else {
                    PackageInstallerSessionBackend(this).abandon(it)
                }
            }
        }
        updateStoredStage(PackageInstallStage.CANCELLED, null)
        cleanupStaging()
        finish()
    }

    private fun updateStoredStage(stage: PackageInstallStage, message: String?) {
        val id = operationId ?: return
        operationStore.load(id)?.let { operation ->
            runCatching {
                operationStore.save(
                    operation.copy(stage = stage, sessionId = sessionId, statusMessage = message)
                )
            }
        }
    }

    private fun cleanupStaging() {
        operationDirectory?.deleteRecursively()
        operationId?.let(operationStore::delete)
    }

    private fun statusSender(id: String) = PendingIntent.getActivity(
        this,
        id.hashCode(),
        Intent(this, PackageInstallerActivity::class.java)
            .setAction(ACTION_INSTALL_STATUS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPERATION_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    ).intentSender

    private fun showOptions() {
        PackageInstallOptionsDialog.show(this, plan, capabilities, options) { updated ->
            options = updated
            plan?.let(::renderPlan)
        }
    }

    private fun message(exception: Throwable): String = exception.message
        ?.takeIf(String::isNotBlank)
        ?: getString(R.string.package_installer_unknown_error)

    companion object {
        private const val ACTION_INSTALL_STATUS =
            "com.wisso.wizefiles.action.PACKAGE_INSTALL_STATUS"
        private const val EXTRA_DISPLAY_NAME =
            "com.wisso.wizefiles.extra.PACKAGE_DISPLAY_NAME"
        private const val EXTRA_OPERATION_ID =
            "com.wisso.wizefiles.extra.PACKAGE_OPERATION_ID"
        fun createIntent(context: Context, uri: Uri, displayName: String): Intent =
            Intent(context, PackageInstallerActivity::class.java)
                .setData(uri)
                .putExtra(EXTRA_DISPLAY_NAME, displayName)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
