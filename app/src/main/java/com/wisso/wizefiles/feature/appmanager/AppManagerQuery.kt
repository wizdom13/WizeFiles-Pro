package com.wisso.wizefiles.feature.appmanager

import java.text.Collator
import java.util.Locale

internal fun queryInstalledApps(
    apps: List<InstalledApp>,
    query: String,
    filter: AppManagerFilter,
    sort: AppManagerSort,
    order: AppManagerSortOrder,
    locale: Locale = Locale.getDefault()
): List<InstalledApp> {
    val normalizedQuery = query.trim()
    val collator = Collator.getInstance(locale).apply {
        strength = Collator.PRIMARY
    }
    val labelComparator = Comparator<InstalledApp> { first, second ->
        collator.compare(first.label, second.label)
            .takeIf { it != 0 }
            ?: collator.compare(first.packageName, second.packageName)
    }
    val primaryComparator = when (sort) {
        AppManagerSort.NAME -> labelComparator
        AppManagerSort.APK_SIZE -> compareBy(InstalledApp::totalApkBytes)
        AppManagerSort.INSTALLED_DATE -> compareBy(InstalledApp::firstInstallTimeMillis)
        AppManagerSort.UPDATED_DATE -> compareBy(InstalledApp::lastUpdateTimeMillis)
        AppManagerSort.PACKAGE_NAME -> Comparator { first, second ->
            collator.compare(first.packageName, second.packageName)
        }
    }
    val comparator = primaryComparator
        .then(labelComparator)
        .let { if (order == AppManagerSortOrder.DESCENDING) it.reversed() else it }
    return apps.asSequence()
        .filter { app ->
            when (filter) {
                AppManagerFilter.ALL -> true
                AppManagerFilter.USER -> !app.isSystem
                AppManagerFilter.SYSTEM -> app.isSystem
                AppManagerFilter.DISABLED -> !app.isEnabled
            }
        }
        .filter { app ->
            normalizedQuery.isEmpty() ||
                app.label.contains(normalizedQuery, ignoreCase = true) ||
                app.packageName.contains(normalizedQuery, ignoreCase = true) ||
                app.versionName.contains(normalizedQuery, ignoreCase = true)
        }
        .sortedWith(comparator)
        .toList()
}
