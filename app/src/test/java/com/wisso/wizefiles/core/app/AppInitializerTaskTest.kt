// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInitializerTaskTest {
    @Test
    fun `name is stable without rendering the action`() {
        var invoked = false
        val action = object : () -> Unit {
            override fun invoke() {
                invoked = true
            }

            override fun toString(): String {
                throw AssertionError("Initializer action must not be rendered")
            }
        }
        val task = AppInitializerTask("stableInitializerName", action)

        assertEquals("stableInitializerName", task.name)
        task.run()

        assertTrue(invoked)
    }

    @Test
    fun `registry exposes explicit unique initializer names`() {
        val expectedNames = listOf(
            "initializeUncaughtExceptionLogging",
            "registerActivityLifecycleLogger",
            "disableHiddenApiChecks",
            "initializeWebViewDebugging",
            "initializeCoil",
            "initializeFileSystemProviders",
            "repairVaultStorageEntries",
            "initializeLiveDataObjects",
            "initializeSearchIndex",
            "initializeCustomTheme",
            "initializeNightMode",
            "createNotificationChannels"
        )
        val actualNames = appInitializerTasks.map(AppInitializerTask::name)

        assertEquals(expectedNames, actualNames)
        assertEquals(actualNames.size, actualNames.toSet().size)
    }

    @Test
    fun `ordinary failure is recorded and later initializers still run`() {
        val expected = IllegalStateException("broken optional integration")
        val executed = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val reported = mutableListOf<AppInitializerFailure>()
        val tasks = listOf(
            AppInitializerTask("first") { executed += "first" },
            AppInitializerTask("failing") { throw expected },
            AppInitializerTask("last") { executed += "last" }
        )

        val failures = runAppInitializerTasks(
            tasks = tasks,
            onCompleted = completed::add,
            onFailed = reported::add
        )

        assertEquals(listOf("first", "last"), executed)
        assertEquals(listOf("first", "last"), completed)
        assertEquals(1, failures.size)
        assertEquals(failures, reported)
        assertEquals("failing", failures.single().taskName)
        assertSame(expected, failures.single().exception)
    }

    @Test(expected = AssertionError::class)
    fun `fatal errors are not converted into recoverable failures`() {
        runAppInitializerTasks(
            listOf(AppInitializerTask("fatal") { throw AssertionError("fatal") })
        )
    }
}
