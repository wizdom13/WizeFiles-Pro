// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WzfResolverIsolationTest {

    @Test
    fun contentWzfWithWildcardMimeRoutesThroughExternalRouter() {
        assertAliasesRouteThroughExternalRouter(
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setDataAndType(Uri.parse("content://example.provider/document/settings.wzf"), "*/*")
        )
    }

    @Test
    fun contentWzfWithOctetStreamMimeRoutesThroughExternalRouter() {
        assertAliasesRouteThroughExternalRouter(
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setDataAndType(
                    Uri.parse("content://example.provider/document/settings.wzf"),
                    "application/octet-stream"
                )
        )
    }

    @Test
    fun contentWzfWithBackupMimeRoutesThroughExternalRouter() {
        assertAliasesRouteThroughExternalRouter(
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setDataAndType(
                    Uri.parse("content://example.provider/document/settings.wzf"),
                    "application/x-wizefiles-backup"
                )
        )
    }

    @Test
    fun fileWzfWithBackupMimeRoutesThroughExternalRouter() {
        assertAliasesRouteThroughExternalRouter(
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setDataAndType(
                    Uri.parse("file:///sdcard/Download/settings.wzf"),
                    "application/x-wizefiles-backup"
                )
        )
    }

    @Test
    fun fileWzfWithWildcardMimeRoutesThroughExternalRouter() {
        assertAliasesRouteThroughExternalRouter(
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setDataAndType(Uri.parse("file:///sdcard/Download/settings.wzf"), "*/*")
        )
    }

    @Test
    fun fileWzfWithOctetStreamMimeRoutesThroughExternalRouter() {
        assertAliasesRouteThroughExternalRouter(
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setDataAndType(
                    Uri.parse("file:///sdcard/Download/settings.wzf"),
                    "application/octet-stream"
                )
        )
    }

    private fun assertAliasesRouteThroughExternalRouter(intent: Intent) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        intent.`package` = context.packageName
        val resolveInfos = queryResolveInfos(context.packageManager, intent)

        assertFalse("Expected at least one WizeFiles resolver for $intent", resolveInfos.isEmpty())
        val activityNames = resolveInfos.mapNotNull { it.activityInfo?.name }.sorted()
        assertTrue(
            "Expected Backup Restore among WizeFiles resolvers for $intent, got $activityNames",
            activityNames.contains(EXTERNAL_BACKUP_ALIAS)
        )
        val bypassesRouter = resolveInfos.filterNot { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo
            activityInfo?.targetActivity == EXTERNAL_ROUTER_CLASS ||
                activityInfo?.name == EXTERNAL_ROUTER_CLASS
        }
        assertTrue(
            "Every public WizeFiles resolver must route through $EXTERNAL_ROUTER_CLASS; got " +
                bypassesRouter.mapNotNull { it.activityInfo?.name },
            bypassesRouter.isEmpty()
        )
    }

    private fun queryResolveInfos(
        packageManager: PackageManager,
        intent: Intent
    ): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

    companion object {
        private const val EXTERNAL_ROUTER_CLASS =
            "com.wisso.wizefiles.feature.filebrowser.ExternalViewRouterActivity"
        private const val EXTERNAL_BACKUP_ALIAS =
            "com.wisso.wizefiles.feature.filebrowser.ExternalBackupRestore"
    }
}
