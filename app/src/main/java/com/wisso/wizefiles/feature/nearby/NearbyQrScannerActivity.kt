// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R

/** Fully offline QR scanning with Camera2 and the bundled Apache-licensed ZXing decoder. */
class NearbyQrScannerActivity : AppCompatActivity() {
    private lateinit var preview: TextureView
    private val main = Handler(Looper.getMainLooper())
    private var decodeThread: HandlerThread? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var surface: Surface? = null
    private var resumed = false
    private var opening = false
    private var permissionRequested = false
    private var errorVisible = false
    @Volatile private var generation = 0
    private var previewSize = Size(640, 480)
    private var sensorOrientation = 90
    private var frontCamera = false

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else showError(R.string.nearby_camera_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val toolbar = MaterialToolbar(this).apply {
            setTitle(R.string.nearby_scan_title)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar)
        root.addView(TextView(this).apply {
            setText(R.string.nearby_scan_help)
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        })
        preview = TextureView(this).apply {
            contentDescription = getString(R.string.nearby_scan_title)
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = openCamera()
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = transformPreview()
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { closeCamera(); return true }
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else if (!permissionRequested) {
            permissionRequested = true
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        resumed = false
        closeCamera()
        super.onPause()
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun openCamera() {
        if (!resumed || !preview.isAvailable || opening || camera != null || errorVisible ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(CameraManager::class.java)
        val epoch = ++generation
        try {
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull() ?: throw IllegalStateException("No camera")
            val characteristics = manager.getCameraCharacteristics(id)
            val map = requireNotNull(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
            val previewSizes = map.getOutputSizes(SurfaceTexture::class.java).toSet()
            val sizes = map.getOutputSizes(ImageFormat.YUV_420_888).filter { it in previewSizes && it.width <= 1920 && it.height <= 1920 }
            previewSize = sizes.filter { it.width * it.height >= 640 * 480 }.minByOrNull { it.width * it.height }
                ?: sizes.maxByOrNull { it.width * it.height } ?: throw IllegalStateException("No supported camera stream")
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            frontCamera = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            transformPreview()
            preview.surfaceTexture!!.setDefaultBufferSize(previewSize.width, previewSize.height)
            surface = Surface(preview.surfaceTexture)
            val thread = HandlerThread("nearby-qr-decode").apply { start() }
            decodeThread = thread
            reader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2).apply {
                var lastFrame = 0L
                setOnImageAvailableListener({ source ->
                    runCatching {
                        source.acquireLatestImage()?.use { image ->
                            val now = SystemClock.elapsedRealtime()
                            if (epoch != generation || now - lastFrame < 150) return@use
                            lastFrame = now
                            val plane = image.planes[0]
                            val value = NearbyQrDecoder.decode(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
                            if (!value.isNullOrBlank()) main.post {
                                if (epoch == generation && resumed && !isFinishing) {
                                    ++generation
                                    setResult(RESULT_OK, Intent().putExtra(EXTRA_QR, value))
                                    finish()
                                }
                            }
                        }
                    }
                }, Handler(thread.looper))
            }
            opening = true
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (epoch != generation || !resumed) { device.close(); return }
                    opening = false
                    camera = device
                    try {
                        val surfaces = listOf(requireNotNull(surface), requireNotNull(reader).surface)
                        device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(configured: CameraCaptureSession) {
                                if (epoch != generation || !resumed) { configured.close(); return }
                                session = configured
                                try {
                                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        surfaces.forEach(::addTarget)
                                        val modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                                        if (CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE in modes) {
                                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        }
                                    }
                                    configured.setRepeatingRequest(request.build(), null, main)
                                } catch (_: Exception) { showError(R.string.nearby_camera_unavailable) }
                            }
                            override fun onConfigureFailed(failed: CameraCaptureSession) {
                                failed.close()
                                if (epoch == generation) showError(R.string.nearby_camera_unavailable)
                            }
                        }, main)
                    } catch (_: Exception) { showError(R.string.nearby_camera_unavailable) }
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    if (epoch == generation) showError(R.string.nearby_camera_unavailable)
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    if (epoch == generation) showError(R.string.nearby_camera_unavailable)
                }
            }, main)
        } catch (_: Exception) { showError(R.string.nearby_camera_unavailable) }
    }

    private fun transformPreview() {
        if (preview.width == 0 || preview.height == 0) return
        val rotation = when (display?.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
        val degrees = (sensorOrientation - rotation * (if (frontCamera) -1 else 1) + 360) % 360
        val swap = degrees == 90 || degrees == 270
        val sourceWidth = if (swap) previewSize.height else previewSize.width
        val sourceHeight = if (swap) previewSize.width else previewSize.height
        val scale = maxOf(preview.width.toFloat() / sourceWidth, preview.height.toFloat() / sourceHeight)
        preview.setTransform(Matrix().apply {
            setScale(previewSize.width * scale / preview.width, previewSize.height * scale / preview.height, preview.width / 2f, preview.height / 2f)
            postRotate(degrees.toFloat(), preview.width / 2f, preview.height / 2f)
            if (frontCamera) postScale(-1f, 1f, preview.width / 2f, preview.height / 2f)
        })
    }

    private fun closeCamera() {
        ++generation
        opening = false
        runCatching { session?.close() }; session = null
        runCatching { camera?.close() }; camera = null
        runCatching { reader?.close() }; reader = null
        surface?.release(); surface = null
        decodeThread?.quitSafely(); decodeThread = null
    }

    private fun showError(message: Int) {
        closeCamera()
        if (isFinishing || errorVisible) return
        errorVisible = true
        MaterialAlertDialogBuilder(this).setTitle(R.string.nearby_scan_title).setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }

    companion object { const val EXTRA_QR = "nearby_verification_qr" }
}
