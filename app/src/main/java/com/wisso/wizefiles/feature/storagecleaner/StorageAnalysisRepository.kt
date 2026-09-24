package com.wisso.wizefiles.storagecleaner

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.wisso.wizefiles.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface StorageAnalysisDataSource {
    suspend fun analyze(filters: ScanFilters, ignoredIds: Set<String>): StorageAnalysisResult
}

open class StorageAnalysisRepository(private val context: Context) : StorageAnalysisDataSource {
    private val scanner = StorageScanner()
    private val largeFileScanner = LargeFileScanner()
    private val downloadScanner = DownloadScanner()
    private val duplicateDetector = DuplicateDetector()
    private val junkAnalyzer = JunkFileAnalyzer(
        JunkReasonStrings.fromContext(context)
    )
    private val recommendationEngine = CleanupRecommendationEngine(RecommendationStrings.fromContext(context))
    private val unusedAppsAnalyzer by lazy { UnusedAppsAnalyzer(context) }

    override suspend fun analyze(filters: ScanFilters, ignoredIds: Set<String>): StorageAnalysisResult =
        withContext(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            val scanLimit = filters.maxFilesToHash * 2
            val scanResult = runStep(
                warnings,
                context.getString(R.string.storage_cleaner_warning_storage_scan_limited, scanLimit)
            ) {
                scanner.scanAllFiles(scanLimit)
            }
            val files = scanResult?.files.orEmpty()
            if (scanResult?.scanWasTruncated == true) {
                warnings += context.getString(
                    R.string.storage_cleaner_warning_storage_scan_limited,
                    scanResult.scannedFileCount
                )
            }
            val largeFiles = runStep(warnings, context.getString(R.string.storage_cleaner_warning_large_file_analysis_limited)) {
                largeFileScanner.scan(files, filters)
            }.orEmpty()
            val duplicateGroups = runStep(warnings, context.getString(R.string.storage_cleaner_warning_duplicate_analysis_limited)) {
                duplicateDetector.detectExactDuplicates(files, filters.maxFilesToHash)
            }.orEmpty()
            val downloads = runStep(warnings, context.getString(R.string.storage_cleaner_warning_downloads_analysis_limited)) {
                downloadScanner.scan(filters)
            }.orEmpty()
            val junk = runStep(warnings, context.getString(R.string.storage_cleaner_warning_junk_analysis_limited)) {
                junkAnalyzer.scan(files, filters.minJunkFileAgeDays)
            }.orEmpty()
            val apkCandidates = files.filter { scanner.isApkInstallerFile(it) }
            val unusedResult = runStep(warnings, context.getString(R.string.storage_cleaner_warning_unused_apps_analysis_limited)) {
                unusedAppsAnalyzer.analyze(filters.oldFileDays)
            } ?: UnusedAppsAnalyzer.Result(emptyList(), context.getString(R.string.storage_cleaner_permission_hint_usage_access))
            val compositionByCategory = mutableMapOf<StorageCompositionCategory, Pair<Long, Int>>()
            files.forEach { file ->
                val category = scanner.compositionCategoryFor(file)
                val bytes = runCatching { file.length() }.getOrDefault(0L)
                val current = compositionByCategory[category] ?: (0L to 0)
                compositionByCategory[category] = (current.first + bytes) to (current.second + 1)
            }
            val appsWithStorage = unusedResult.apps.filter {
                listOfNotNull(it.appBytes, it.cacheBytes, it.dataBytes).sum() > 0L
            }
            val appStorageBytes = appsWithStorage.sumOf { app ->
                listOfNotNull(app.appBytes, app.cacheBytes, app.dataBytes).sum()
            }
            if (appStorageBytes > 0L) {
                compositionByCategory[StorageCompositionCategory.APP_STORAGE] =
                    appStorageBytes to appsWithStorage.size
            }
            val compositionCategories = StorageCompositionCategory.entries.mapNotNull { category ->
                val summary = compositionByCategory[category] ?: return@mapNotNull null
                StorageCompositionSummary(
                    category = category,
                    bytes = summary.first,
                    itemCount = summary.second
                )
            }.filter { it.bytes > 0L || it.itemCount > 0 }
            val totalStorageBytes = runCatching {
                val rootPath = Environment.getExternalStorageDirectory().absolutePath
                StatFs(rootPath).totalBytes
            }.getOrDefault(0L)
            val recommendations = recommendationEngine.build(
                largeFiles = largeFiles,
                downloadCandidates = downloads,
                duplicateGroups = duplicateGroups,
                apkCandidates = apkCandidates,
                junkCandidates = junk,
                unusedApps = unusedResult.apps,
                ignoredIds = ignoredIds
            )
            StorageAnalysisResult(
                compositionCategories = compositionCategories,
                recommendations = recommendations,
                progress = ScanProgress(
                    scannedItems = scanResult?.scannedFileCount ?: files.size,
                    totalItemsEstimate =
                        scanResult?.discoveredFileCountEstimate ?: files.size,
                    phase = context.getString(
                        if (scanResult?.scanWasTruncated == true) {
                            R.string.storage_cleaner_phase_partial
                        } else {
                            R.string.transfer_section_completed
                        }
                    ),
                    isFinished = true
                ),
                missingCapabilities = (warnings + listOfNotNull(unusedResult.missingCapabilityMessage))
                    .distinct(),
                totalStorageBytes = totalStorageBytes,
                scanWasTruncated = scanResult?.scanWasTruncated == true,
                scannedFileCount = scanResult?.scannedFileCount ?: files.size,
                discoveredFileCountEstimate =
                    scanResult?.discoveredFileCountEstimate ?: files.size
            )
        }

    private inline fun <T> runStep(
        warnings: MutableList<String>,
        warningMessage: String,
        block: () -> T
    ): T? {
        return runCatching(block).onFailure { warnings += warningMessage }.getOrNull()
    }
}
