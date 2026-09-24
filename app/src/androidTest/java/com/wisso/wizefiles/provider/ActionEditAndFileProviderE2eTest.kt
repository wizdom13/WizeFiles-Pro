// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.testsupport.ExternalEditProbeActivity
import com.wisso.wizefiles.util.createEditIntent
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionEditAndFileProviderE2eTest {

    @Test
    fun actionEditGrantAllowsExternalEditorReadAndWrite() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val file = File(appContext.cacheDir, "edit-e2e.txt").apply { writeText("original") }
        val uri = file.toPath().fileProviderUri
        val completion = CountDownLatch(1)
        val reportRef = AtomicReference<ProbeReport?>()
        val resultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                reportRef.set(
                    ProbeReport(
                        uri = resultData?.getString(ExternalEditProbeActivity.RESULT_URI),
                        readText = resultData?.getString(ExternalEditProbeActivity.RESULT_READ_TEXT),
                        writeSucceeded = resultData?.getBoolean(
                            ExternalEditProbeActivity.RESULT_WRITE_SUCCEEDED,
                            false
                        ) == true
                    )
                )
                completion.countDown()
            }
        }

        val intent = uri.createEditIntent(MimeType.TEXT_PLAIN)
            .setClassName(testContext.packageName, ExternalEditProbeActivity::class.java.name)
            .putExtra(ExternalEditProbeActivity.EXTRA_RESULT_RECEIVER, resultReceiver)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        appContext.startActivity(intent)

        assertTrue(
            "External edit probe should report its result",
            completion.await(5, TimeUnit.SECONDS)
        )
        val report = reportRef.get()
        assertNotNull(report)
        assertEquals(uri.toString(), report?.uri)
        assertEquals("original", report?.readText)
        assertTrue(report?.writeSucceeded == true)
        assertEquals("edited-by-probe", file.readText())
    }

    private data class ProbeReport(
        val uri: String?,
        val readText: String?,
        val writeSucceeded: Boolean
    )

    @Test
    fun fileProviderOpenFileDescriptorRespectsRepresentativeModes() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(appContext.cacheDir, "open-modes-e2e.txt").apply { writeText("seed") }
        val uri = file.toPath().fileProviderUri

        appContext.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            val read = FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
            assertEquals("seed", read)
        }

        appContext.contentResolver.openFileDescriptor(uri, "w")!!.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).bufferedWriter().use { it.write("write-only") }
        }
        assertEquals("write-only", file.readText())

        appContext.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
            val inputChannel = FileInputStream(pfd.fileDescriptor).channel
            val outputChannel = FileOutputStream(pfd.fileDescriptor).channel
            val buf = java.nio.ByteBuffer.allocate(10)
            inputChannel.read(buf)
            val read = String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8)
            assertEquals("write-only", read)
            outputChannel.position(0)
            outputChannel.write(java.nio.ByteBuffer.wrap("rw-mode".toByteArray(StandardCharsets.UTF_8)))
        }
        assertTrue(file.readText().startsWith("rw-mode"))
    }
}
