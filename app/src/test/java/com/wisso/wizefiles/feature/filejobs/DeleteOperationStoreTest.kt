package com.wisso.wizefiles.feature.filejobs

import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeleteOperationStoreTest {
    private val operationId = "delete-store-test"

    @Before
    fun setUp() {
        setGlobalApplicationForTests(ApplicationProvider.getApplicationContext())
        DeleteOperationStore.delete(operationId)
    }

    @After
    fun tearDown() {
        DeleteOperationStore.delete(operationId)
        File(
            ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir,
            "delete-operations"
        ).deleteRecursively()
    }

    @Test
    fun `delete options survive recovery without session-only confirmation state`() {
        DeleteOperationStore.save(
            operationId,
            DeleteOptions(
                permanentDelete = true,
                skipConfirmationForSession = true,
                secureShred = true
            )
        )

        val restored = DeleteOperationStore.load(operationId)

        assertNotNull(restored)
        assertEquals(true, restored?.permanentDelete)
        assertEquals(false, restored?.skipConfirmationForSession)
        assertEquals(true, restored?.secureShred)

        DeleteOperationStore.delete(operationId)
        assertFalse(
            File(
                ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir,
                "delete-operations/$operationId.json"
            ).exists()
        )
    }
}
