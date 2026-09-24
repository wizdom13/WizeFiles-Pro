// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.sync.SyncPathResolver
import com.wisso.wizefiles.feature.transfer.TransferCenterActivity
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.feature.transfer.resolveOpenDestination
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toUriString
import com.wisso.wizefiles.ui.MaterialFeatureScreen
import com.wisso.wizefiles.ui.MaterialFeatureScreen.ButtonKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption

class NearbyTransferActivity : AppCompatActivity() {
    private lateinit var screen: MaterialFeatureScreen
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAction: PendingAction? = null
    private var selectedSources: List<AppPath> = emptyList()
    private var pendingOffer: NearbyOffer? = null
    private var pendingResumeOperationId = ""
    private var permissionExplanationShown = false
    private val qrScanner by lazy {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options)
    }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it } && NearbyPermissions.granted(this)) {
            executePendingAction()
        } else {
            Toast.makeText(this, "Nearby device permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    private val sourcesPicker = registerForActivityResult(FileListActivity.OpenPathContract()) { paths ->
        if (paths.isNotEmpty()) {
            selectedSources = paths
            beginWithPermissions(PendingAction.SEND)
        }
    }

    private val destinationPicker = registerForActivityResult(
        FileListActivity.OpenDirectoryContract()
    ) { destination ->
        if (destination != null) {
            val selection = resolvePickedDestinationOffer(
                destination = destination,
                pendingOffer = pendingOffer,
                serviceOffer = NearbyTransferService.snapshot.offer
            )
            if (selection == null) {
                Toast.makeText(
                    this,
                    R.string.nearby_transfer_offer_unavailable,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                pendingOffer = selection.second
                inspectConflicts(selection.second, selection.first)
            }
        }
    }

    private val refresh = object : Runnable {
        override fun run() {
            render(NearbyTransferService.snapshot)
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = MaterialFeatureScreen(this, R.string.nearby_transfer_title)
        screen.install()
        val sources = intent.getStringArrayListExtra(EXTRA_SOURCE_URIS).orEmpty()
        if (sources.isNotEmpty()) {
            selectedSources = sources.mapNotNull { it.toAppPathOrNull() }
            beginWithPermissions(PendingAction.SEND)
        }
        intent.getStringExtra(EXTRA_OPERATION_ID)?.let {
            pendingAction = PendingAction.RESUME
            pendingResumeOperationId = it
            beginWithPermissions(PendingAction.RESUME)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun beginWithPermissions(action: PendingAction) {
        if (!NearbyPermissions.playServicesAvailable(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Google Play services required")
                .setMessage("Nearby Transfer uses Google Play services to discover and connect directly to nearby WizeFiles devices.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        pendingAction = action
        if (NearbyPermissions.granted(this)) {
            executePendingAction()
        } else if (!permissionExplanationShown) {
            permissionExplanationShown = true
            MaterialAlertDialogBuilder(this)
                .setTitle("Allow nearby transfer")
                .setMessage("Bluetooth is used to find and authenticate nearby devices. Wi‑Fi is used for a fast direct transfer. These permissions are requested only when you start Nearby Transfer.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Continue") { _, _ -> permissions.launch(NearbyPermissions.requiredAtRuntime()) }
                .show()
        } else {
            permissions.launch(NearbyPermissions.requiredAtRuntime())
        }
    }

    private fun executePendingAction() {
        when (pendingAction) {
            PendingAction.SEND -> NearbyTransferService.command(
                this,
                NearbyTransferService.ACTION_SEND
            ) {
                putStringArrayListExtra(
                    NearbyTransferService.EXTRA_SOURCE_URIS,
                    ArrayList(selectedSources.map { it.toUriString() })
                )
            }
            PendingAction.RECEIVE -> NearbyTransferService.command(
                this,
                NearbyTransferService.ACTION_RECEIVE
            )
            PendingAction.RESUME -> NearbyTransferService.command(
                this,
                NearbyTransferService.ACTION_RESUME
            ) { putExtra(NearbyTransferService.EXTRA_OPERATION_ID, pendingResumeOperationId) }
            null -> Unit
        }
        pendingAction = null
    }

    private fun render(state: NearbyUiSnapshot) {
        if (state.phase == NearbyPhase.AUTH_QR_VISIBLE) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        screen.clear()
        screen.intro(
            "Send files and complete folders directly between WizeFiles devices. " +
                "Sending always copies; it never deletes the originals.",
            R.drawable.ic_nearby_transfer_control_normal_24dp
        )
        when (state.phase) {
            NearbyPhase.IDLE, NearbyPhase.ERROR, NearbyPhase.CANCELLED,
            NearbyPhase.COMPLETED -> renderIdleOrTerminal(state)
            NearbyPhase.PLANNING -> progress(state.message)
            NearbyPhase.DISCOVERING -> {
                heading("Nearby devices")
                text(state.message)
                if (state.endpoints.isEmpty()) progress("Searching…")
                state.endpoints.forEach { endpoint ->
                    button(endpoint.name) {
                        NearbyTransferService.command(this, NearbyTransferService.ACTION_CONNECT) {
                            putExtra(NearbyTransferService.EXTRA_ENDPOINT_ID, endpoint.id)
                        }
                    }
                }
                countdown(state.visibilityEndsAtMillis)
                stopButton()
            }
            NearbyPhase.ADVERTISING -> {
                heading("Ready to receive")
                text("Visible only to nearby WizeFiles devices for two minutes.")
                countdown(state.visibilityEndsAtMillis)
                progress("Waiting for a sender…")
                stopButton()
            }
            NearbyPhase.AUTH_APPROVAL_REQUIRED -> {
                heading(state.peerName + " wants to connect")
                text("Allow this device and show a one-time verification QR?")
                button("Allow and show QR") {
                    NearbyTransferService.command(this, NearbyTransferService.ACTION_AUTH_APPROVE)
                }
                button("Decline", ButtonKind.OUTLINED) {
                    NearbyTransferService.command(this, NearbyTransferService.ACTION_AUTH_REJECT)
                }
            }
            NearbyPhase.AUTH_QR_VISIBLE -> {
                heading("Verification QR")
                text("Ask " + state.peerName + " to scan this one-time QR.")
                if (state.authenticationQr.isNotEmpty()) qrImage(state.authenticationQr)
                authenticationCountdown(state.authenticationExpiresAtMillis)
                button("Cancel", ButtonKind.OUTLINED) {
                    NearbyTransferService.command(this, NearbyTransferService.ACTION_AUTH_REJECT)
                }
            }
            NearbyPhase.AUTH_SCAN_REQUIRED -> {
                heading("Verify " + state.peerName)
                text("Scan the one-time QR shown on the receiving device.")
                if (state.message.isNotBlank()) text(state.message)
                authenticationCountdown(state.authenticationExpiresAtMillis)
                button("Scan verification QR") { scanVerificationQr() }
                button("Cancel", ButtonKind.OUTLINED) {
                    NearbyTransferService.command(this, NearbyTransferService.ACTION_AUTH_REJECT)
                }
            }
            NearbyPhase.CONNECTED -> progress(state.message)
            NearbyPhase.OFFER_PENDING -> {
                val offer = state.offer ?: return
                pendingOffer = offer
                heading("${state.peerName} wants to send")
                text("${offer.entries.size} items • ${formatBytes(offer.totalBytes)}")
                offer.entries.take(6).forEach { text("• ${it.relativePath}") }
                if (offer.entries.size > 6) text("…and ${offer.entries.size - 6} more")
                button("Accept and choose folder") { destinationPicker.launch(null) }
                button("Decline", ButtonKind.OUTLINED) {
                    NearbyTransferService.command(this, NearbyTransferService.ACTION_REJECT_OFFER)
                }
            }
            NearbyPhase.TRANSFERRING -> {
                heading(if (state.role == NearbyRole.SEND) "Sending to ${state.peerName}" else "Receiving from ${state.peerName}")
                text(state.message)
                val progress = if (state.totalBytes > 0) {
                    ((state.transferredBytes * 100) / state.totalBytes).toInt().coerceIn(0, 100)
                } else 0
                screen.horizontalProgress(progress)
                text("${formatBytes(state.transferredBytes)} / ${formatBytes(state.totalBytes)}")
                button("Pause") { commandForOperation(NearbyTransferService.ACTION_PAUSE, state.operationId) }
                button("Open Transfer Center", ButtonKind.OUTLINED) { startActivity(Intent(this@NearbyTransferActivity, TransferCenterActivity::class.java)) }
                button("Cancel", ButtonKind.OUTLINED) { commandForOperation(NearbyTransferService.ACTION_CANCEL, state.operationId) }
            }
            NearbyPhase.PAUSED, NearbyPhase.RECOVERABLE -> {
                heading(if (state.phase == NearbyPhase.PAUSED) "Transfer paused" else "Connection interrupted")
                text(state.message)
                button("Reconnect and resume") {
                    pendingResumeOperationId = state.operationId
                    beginWithPermissions(PendingAction.RESUME)
                }
                button("Open Transfer Center", ButtonKind.OUTLINED) { startActivity(Intent(this@NearbyTransferActivity, TransferCenterActivity::class.java)) }
                button("Cancel", ButtonKind.OUTLINED) { commandForOperation(NearbyTransferService.ACTION_CANCEL, state.operationId) }
            }
        }
    }

    private fun renderIdleOrTerminal(state: NearbyUiSnapshot) {
        if (state.phase == NearbyPhase.COMPLETED) {
            heading("Transfer completed")
            text(state.message)
            state.operationId.takeIf(String::isNotEmpty)?.let { operationId ->
                TransferRepository.operation(operationId)
                    ?.let(::resolveOpenDestination)
                    ?.let { path ->
                        button("Open destination") {
                            startActivity(FileListActivity.createViewIntent(path.toAppPath()))
                        }
                    }
            }
        } else if (state.phase in setOf(NearbyPhase.ERROR, NearbyPhase.CANCELLED) &&
            state.message.isNotBlank()
        ) {
            heading("Transfer stopped")
            text(state.message)
        }
        screen.card {
            button("Send files or folders") { sourcesPicker.launch(listOf(MimeType.ANY)) }
            button("Receive", ButtonKind.OUTLINED) { beginWithPermissions(PendingAction.RECEIVE) }
            button("Open Transfer Center", ButtonKind.OUTLINED) {
                startActivity(Intent(this@NearbyTransferActivity, TransferCenterActivity::class.java))
            }
        }
    }

    private fun scanVerificationQr() {
        qrScanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                if (value.isNullOrBlank()) {
                    Toast.makeText(this, "The scanned QR is empty", Toast.LENGTH_LONG).show()
                } else {
                    NearbyTransferService.command(this, NearbyTransferService.ACTION_AUTH_SCAN) {
                        putExtra(NearbyTransferService.EXTRA_AUTH_QR, value)
                    }
                }
            }
            .addOnCanceledListener { Unit }
            .addOnFailureListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("QR scanner is not ready")
                    .setMessage(
                        "Connect to the internet once so Google Play services can prepare the " +
                            "QR scanner, then try again. Nearby Transfer works offline afterward."
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Try again") { _, _ -> scanVerificationQr() }
                    .show()
            }
    }

    private fun inspectConflicts(offer: NearbyOffer, destination: AppPath) {
        lifecycleScope.launch {
            val destinationUri = destination.toUriString()
            val conflictCount = runCatching {
                withContext(Dispatchers.IO) {
                    val root = SyncPathResolver.resolve(destinationUri)
                        ?.takeIf { Files.isDirectory(it) && Files.isWritable(it) }
                        ?: return@withContext null
                    offer.entries.count {
                        Files.exists(
                            NearbyPathSecurity.resolveInside(root, it.relativePath),
                            LinkOption.NOFOLLOW_LINKS
                        )
                    }
                }
            }.getOrNull()
            if (conflictCount == null) {
                Toast.makeText(
                    this@NearbyTransferActivity,
                    R.string.file_list_location_unavailable,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            if (conflictCount == 0) {
                acceptOffer(destinationUri, NearbyConflictPolicy.KEEP_BOTH)
            } else {
                MaterialAlertDialogBuilder(this@NearbyTransferActivity)
                    .setTitle("$conflictCount existing items")
                    .setMessage("Choose how WizeFiles should handle existing names. This applies to this transfer only.")
                    .setItems(arrayOf("Keep both", "Skip existing", "Replace existing")) { _, which ->
                        val policy = when (which) {
                            1 -> NearbyConflictPolicy.SKIP
                            2 -> NearbyConflictPolicy.REPLACE
                            else -> NearbyConflictPolicy.KEEP_BOTH
                        }
                        if (policy == NearbyConflictPolicy.REPLACE) confirmReplace(destinationUri, conflictCount)
                        else acceptOffer(destinationUri, policy)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun confirmReplace(destinationUri: String, conflictCount: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Replace $conflictCount existing items?")
            .setMessage("Existing files with matching names will be replaced only after each incoming file is completely received.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Replace") { _, _ ->
                acceptOffer(destinationUri, NearbyConflictPolicy.REPLACE)
            }
            .show()
    }

    private fun acceptOffer(destinationUri: String, policy: NearbyConflictPolicy) {
        NearbyTransferService.command(this, NearbyTransferService.ACTION_ACCEPT_OFFER) {
            putExtra(NearbyTransferService.EXTRA_DESTINATION_URI, destinationUri)
            putExtra(NearbyTransferService.EXTRA_CONFLICT_POLICY, policy.name)
        }
    }

    private fun commandForOperation(action: String, operationId: String) {
        NearbyTransferService.command(this, action) {
            putExtra(NearbyTransferService.EXTRA_OPERATION_ID, operationId)
        }
    }

    private fun stopButton() = button("Stop", ButtonKind.OUTLINED) {
        NearbyTransferService.command(this, NearbyTransferService.ACTION_STOP)
        finish()
    }

    private fun countdown(end: Long) {
        val seconds = ((end - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
        text("Visibility ends in ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}")
    }

    private fun heading(value: String) = screen.heading(value)

    private fun text(value: String) = screen.text(value)

    private fun qrImage(value: String) {
        val size = dp(300)
        screen.addCentered(ImageView(this).apply {
            setImageBitmap(qrBitmap(value))
            contentDescription = "One-time Nearby verification QR"
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, size, topMargin = 12)
    }

    private fun qrBitmap(value: String): Bitmap {
        val pixels = 720
        val matrix = QRCodeWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            pixels,
            pixels,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2
            )
        )
        return Bitmap.createBitmap(pixels, pixels, Bitmap.Config.RGB_565).also { bitmap ->
            for (y in 0 until pixels) {
                for (x in 0 until pixels) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt()
                    )
                }
            }
        }
    }

    private fun authenticationCountdown(end: Long) {
        val seconds = ((end - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
        text("QR expires in " + seconds + " seconds")
    }

    private fun progress(label: String) {
        text(label)
        screen.indeterminateProgress()
    }

    private fun button(
        label: String,
        kind: ButtonKind = ButtonKind.PRIMARY,
        action: () -> Unit
    ) = screen.button(label, kind, action)

    private fun formatBytes(value: Long): String = Formatter.formatFileSize(this, value.coerceAtLeast(0))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private enum class PendingAction { SEND, RECEIVE, RESUME }

    companion object {
        private const val EXTRA_SOURCE_URIS = "nearby.source_uris"
        private const val EXTRA_OPERATION_ID = "nearby.operation_id"
        fun createIntent(context: Context, sources: List<AppPath> = emptyList()): Intent =
            Intent(context, NearbyTransferActivity::class.java).apply {
                if (sources.isNotEmpty()) putStringArrayListExtra(
                    EXTRA_SOURCE_URIS,
                    ArrayList(sources.map { it.toUriString() })
                )
            }

        fun createResumeIntent(context: Context, operationId: String): Intent =
            Intent(context, NearbyTransferActivity::class.java)
                .putExtra(EXTRA_OPERATION_ID, operationId)
    }
}

internal fun resolvePickedDestinationOffer(
    destination: AppPath?,
    pendingOffer: NearbyOffer?,
    serviceOffer: NearbyOffer?
): Pair<AppPath, NearbyOffer>? {
    val selectedDestination = destination ?: return null
    val offer = serviceOffer ?: pendingOffer ?: return null
    return selectedDestination to offer
}
