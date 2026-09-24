package com.wisso.wizefiles.storagecleaner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi

object AppStorageStatsCompat {
    fun getForApp(
        context: Context,
        applicationInfo: ApplicationInfo,
        sdkInt: Int = Build.VERSION.SDK_INT,
        api26Fetcher: (Context, ApplicationInfo) -> AppStorageBytes? = { appContext, info ->
            AppStorageStatsApi26.getForApp(appContext, info)
        }
    ): AppStorageBytes? {
        if (sdkInt < Build.VERSION_CODES.O) {
            return null
        }
        return api26Fetcher(context, applicationInfo)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
object AppStorageStatsApi26 {
    fun getForApp(context: Context, applicationInfo: ApplicationInfo): AppStorageBytes? {
        val manager = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
            ?: return null
        val stats = runCatching {
            manager.queryStatsForUid(
                applicationInfo.storageUuid ?: android.os.storage.StorageManager.UUID_DEFAULT,
                applicationInfo.uid
            )
        }.getOrNull() ?: return null
        return AppStorageBytes(
            appBytes = stats.appBytes,
            cacheBytes = stats.cacheBytes,
            dataBytes = stats.dataBytes
        )
    }
}
