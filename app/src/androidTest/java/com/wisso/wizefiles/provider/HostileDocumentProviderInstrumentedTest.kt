// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wisso.wizefiles.provider.document.resolver.DocumentQueryPolicy
import com.wisso.wizefiles.provider.document.resolver.DocumentResolver
import com.wisso.wizefiles.provider.content.resolver.ResolverException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostileDocumentProviderInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val providerUri: Uri = Uri.parse(
        "content://${instrumentation.context.packageName}.hostile_documents/document/hostile-id"
    )

    @Test
    fun oversizedCrossPackageDiagnosticsAreBoundedBeforeDisplay() {
        instrumentation.targetContext.contentResolver.query(providerUri, null, null, null)?.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(100_000, cursor.getString(cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )).length)
            val decision = DocumentQueryPolicy.decide(
                loading = cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING),
                error = cursor.extras.getString(DocumentsContract.EXTRA_ERROR),
                refreshCount = 0
            ) as DocumentQueryPolicy.Decision.Fail
            assertTrue(decision.message.length <= DocumentQueryPolicy.MAX_PROVIDER_MESSAGE_LENGTH)
            assertTrue(decision.message.startsWith("provider-error:"))
        } ?: error("Hostile provider did not return a cursor")
    }

    @Test
    fun permissionRevocationBetweenQueryAndOpenIsExplicit() {
        instrumentation.targetContext.contentResolver.query(providerUri, null, null, null)?.close()
        assertThrows(SecurityException::class.java) {
            instrumentation.targetContext.contentResolver.openFileDescriptor(providerUri, "r")
        }
    }

    @Test
    fun productionDocumentResolverBoundsProviderFailure() {
        val failure = assertThrows(ResolverException::class.java) {
            DocumentResolver.queryChildren(TestPath(treeUri()))
        }
        assertTrue(failure.message.orEmpty().length <= DocumentQueryPolicy.MAX_PROVIDER_MESSAGE_LENGTH)
        assertTrue(failure.message.orEmpty().startsWith("provider-error:"))
    }

    @Test
    fun productionDocumentResolverPreservesRevocationCause() {
        val failure = assertThrows(ResolverException::class.java) {
            DocumentResolver.openParcelFileDescriptor(TestPath(treeUri()), "r")
        }
        assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is SecurityException })
    }

    private fun treeUri(): Uri = DocumentsContract.buildTreeDocumentUri(
        providerUri.authority!!,
        "root"
    )

    private data class TestPath(
        override val treeUri: Uri,
        override val displayName: String? = null,
        override val parent: TestPath? = null
    ) : DocumentResolver.Path {
        override fun resolve(other: String): TestPath = TestPath(treeUri, other, this)
    }
}
