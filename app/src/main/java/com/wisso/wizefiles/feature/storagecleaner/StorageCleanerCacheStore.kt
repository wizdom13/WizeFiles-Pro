package com.wisso.wizefiles.storagecleaner

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "storage_cleaner_cache"
private const val KEY_CACHE_JSON = "analysis_json"
private const val KEY_LAST_UPDATED = "last_updated"

data class CachedStorageAnalysis(
    val analysis: StorageAnalysisResult,
    val lastUpdatedMillis: Long,
    val filters: ScanFilters
)

interface StorageCleanerCacheStoreApi {
    fun load(): CachedStorageAnalysis?
    fun save(analysis: StorageAnalysisResult, filters: ScanFilters, lastUpdatedMillis: Long)
    fun clear()
}

open class StorageCleanerCacheStore(context: Context) : StorageCleanerCacheStoreApi {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): CachedStorageAnalysis? {
        val raw = prefs.getString(KEY_CACHE_JSON, null) ?: return null
        val lastUpdated = prefs.getLong(KEY_LAST_UPDATED, 0L)
        if (lastUpdated <= 0L) return null
        return runCatching {
            StorageCleanerCacheJson.decode(raw, lastUpdated)
        }.getOrNull()
    }

    override fun save(analysis: StorageAnalysisResult, filters: ScanFilters, lastUpdatedMillis: Long) {
        val payload = StorageCleanerCacheJson.encode(analysis, filters)
        prefs.edit()
            .putString(KEY_CACHE_JSON, payload)
            .putLong(KEY_LAST_UPDATED, lastUpdatedMillis)
            .apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_CACHE_JSON).remove(KEY_LAST_UPDATED).apply()
    }
}

internal object StorageCleanerCacheJson {
    fun encode(analysis: StorageAnalysisResult, filters: ScanFilters): String {
        return JSONObject().apply {
            put("filters", filters.toJson())
            put("analysis", analysis.toJson())
        }.toString()
    }

    fun decode(raw: String, lastUpdatedMillis: Long): CachedStorageAnalysis {
        val root = JSONObject(raw)
        return CachedStorageAnalysis(
            analysis = root.getJSONObject("analysis").toAnalysis(),
            filters = root.optJSONObject("filters")?.toScanFilters() ?: ScanFilters(),
            lastUpdatedMillis = lastUpdatedMillis
        )
    }

    private fun ScanFilters.toJson() = JSONObject().apply {
        put("minLargeFileBytes", minLargeFileBytes)
        put("oldFileDays", oldFileDays)
        put("includeNearDuplicates", includeNearDuplicates)
        put("maxFilesToHash", maxFilesToHash)
        put("minJunkFileAgeDays", minJunkFileAgeDays)
    }

    private fun JSONObject.toScanFilters() = ScanFilters(
        minLargeFileBytes = optLong("minLargeFileBytes", 100L * 1024L * 1024L),
        oldFileDays = optInt("oldFileDays", 120),
        includeNearDuplicates = optBoolean("includeNearDuplicates", false),
        maxFilesToHash = optInt("maxFilesToHash", 3000),
        minJunkFileAgeDays = optInt("minJunkFileAgeDays", 7)
    )

    private fun StorageAnalysisResult.toJson() = JSONObject().apply {
        put("compositionCategories", JSONArray().apply { compositionCategories.forEach { put(it.toJson()) } })
        put("recommendations", JSONArray().apply { recommendations.forEach { put(it.toJson()) } })
        put("progress", progress.toJson())
        put("missingCapabilities", JSONArray().apply { missingCapabilities.forEach { put(it) } })
        put("totalStorageBytes", totalStorageBytes)
        put("scanWasTruncated", scanWasTruncated)
        put("scannedFileCount", scannedFileCount)
        put("discoveredFileCountEstimate", discoveredFileCountEstimate)
    }

    private fun JSONObject.toAnalysis() = StorageAnalysisResult(
        compositionCategories = decodeCompositionCategories(),
        recommendations = optJSONArray("recommendations").toList { it as JSONObject }.map { it.toRecommendation() },
        progress = optJSONObject("progress")?.toScanProgress() ?: ScanProgress(0, 0, "Completed", true),
        missingCapabilities = optJSONArray("missingCapabilities").toList { it as String },
        totalStorageBytes = optLong("totalStorageBytes", 0L),
        scanWasTruncated = optBoolean("scanWasTruncated", false),
        scannedFileCount = optInt(
            "scannedFileCount",
            optJSONObject("progress")?.optInt("scannedItems", 0) ?: 0
        ),
        discoveredFileCountEstimate = optInt(
            "discoveredFileCountEstimate",
            optJSONObject("progress")?.optInt("totalItemsEstimate", 0) ?: 0
        )
    )

    private fun StorageCompositionSummary.toJson() = JSONObject().apply {
        put("category", category.name)
        put("bytes", bytes)
        put("itemCount", itemCount)
    }

    private fun JSONObject.toCompositionSummary() = StorageCompositionSummary(
        category = StorageCompositionCategory.valueOf(getString("category")),
        bytes = optLong("bytes", 0L),
        itemCount = optInt("itemCount", 0)
    )

    private fun JSONObject.decodeCompositionCategories(): List<StorageCompositionSummary> {
        val compositionArray = optJSONArray("compositionCategories")
        if (compositionArray != null) {
            return compositionArray.toList { it as JSONObject }.map { it.toCompositionSummary() }
        }
        return optJSONArray("categories")
            .toList { it as JSONObject }
            .mapNotNull { legacy ->
                val legacyCategory = runCatching { AnalysisCategory.valueOf(legacy.getString("category")) }.getOrNull()
                    ?: return@mapNotNull null
                legacyCategory.toCompositionCategoryOrNull()?.let { category ->
                    StorageCompositionSummary(
                        category = category,
                        bytes = legacy.optLong("bytes", 0L),
                        itemCount = legacy.optInt("itemCount", 0)
                    )
                }
            }
    }

    private fun AnalysisCategory.toCompositionCategoryOrNull(): StorageCompositionCategory? = when (this) {
        AnalysisCategory.IMAGES -> StorageCompositionCategory.IMAGES
        AnalysisCategory.VIDEOS -> StorageCompositionCategory.VIDEOS
        AnalysisCategory.AUDIO -> StorageCompositionCategory.AUDIO
        AnalysisCategory.DOCUMENTS, AnalysisCategory.DOWNLOADS -> StorageCompositionCategory.DOCUMENTS
        AnalysisCategory.ARCHIVES -> StorageCompositionCategory.ARCHIVES
        AnalysisCategory.APKS -> StorageCompositionCategory.APKS
        AnalysisCategory.APP_STORAGE -> StorageCompositionCategory.APP_STORAGE
        AnalysisCategory.LARGE_FILES, AnalysisCategory.DUPLICATES, AnalysisCategory.JUNK -> null
    }

    private fun CleanupRecommendation.toJson() = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("reason", reason)
        put("reclaimableBytes", reclaimableBytes)
        put("path", path)
        put("uri", uri?.toString())
        put("packageName", packageName)
        put("duplicateGroup", duplicateGroup?.toJson())
        put("isDownloadRelated", isDownloadRelated)
        put("score", score.toJson())
        put("preselected", preselected)
    }

    private fun JSONObject.toRecommendation(): CleanupRecommendation {
        val type = RecommendationType.valueOf(getString("type"))
        val id = getString("id")
        val path = optStringOrNull("path")
        val isDownloadRelated = if (has("isDownloadRelated")) {
            optBoolean("isDownloadRelated", false)
        } else {
            type == RecommendationType.STALE_FILE && (
                id.startsWith("downloads:", ignoreCase = true) ||
                    path?.contains("/Download", ignoreCase = true) == true
                )
        }
        return CleanupRecommendation(
            id = id,
            type = type,
            title = getString("title"),
            reason = getString("reason"),
            reclaimableBytes = optLong("reclaimableBytes", 0L),
            path = path,
            uri = optStringOrNull("uri")?.let(Uri::parse),
            packageName = optStringOrNull("packageName"),
            duplicateGroup = optJSONObject("duplicateGroup")?.toDuplicateGroup(),
            isDownloadRelated = isDownloadRelated,
            score = optJSONObject("score")?.toScore() ?: RecommendationScore(0L, 0.0, 0.0, 0.0, 0.0, false),
            preselected = optBoolean("preselected", false)
        )
    }

    private fun DuplicateGroup.toJson() = JSONObject().apply {
        put("id", id)
        put("hash", hash)
        put("keepCandidatePath", keepCandidatePath)
        put("recommendedKeepCandidatePath", recommendedKeepCandidatePath)
        put("keepSelectionRequiresReview", keepSelectionRequiresReview)
        put("candidates", JSONArray().apply { candidates.forEach { put(it.toJson()) } })
    }

    private fun JSONObject.toDuplicateGroup() = DuplicateGroup(
        id = getString("id"),
        hash = getString("hash"),
        keepCandidatePath = getString("keepCandidatePath"),
        candidates = optJSONArray("candidates").toList { it as JSONObject }.map { it.toFileCandidate() },
        recommendedKeepCandidatePath = optString(
            "recommendedKeepCandidatePath",
            getString("keepCandidatePath")
        ),
        keepSelectionRequiresReview = optBoolean("keepSelectionRequiresReview", false)
    )

    private fun FileCandidate.toJson() = JSONObject().apply {
        put("path", path)
        put("size", size)
        put("modifiedTimeMillis", modifiedTimeMillis)
        put("width", width)
        put("height", height)
    }

    private fun JSONObject.toFileCandidate() = FileCandidate(
        path = getString("path"),
        size = optLong("size", 0L),
        modifiedTimeMillis = optLong("modifiedTimeMillis", 0L),
        width = optInt("width", 0),
        height = optInt("height", 0)
    )

    private fun RecommendationScore.toJson() = JSONObject().apply {
        put("reclaimableBytes", reclaimableBytes)
        put("confidence", confidence)
        put("safety", safety)
        put("staleness", staleness)
        put("duplicateCertainty", duplicateCertainty)
        put("ignored", ignored)
    }

    private fun JSONObject.toScore() = RecommendationScore(
        reclaimableBytes = optLong("reclaimableBytes", 0L),
        confidence = optDouble("confidence", 0.0),
        safety = optDouble("safety", 0.0),
        staleness = optDouble("staleness", 0.0),
        duplicateCertainty = optDouble("duplicateCertainty", 0.0),
        ignored = optBoolean("ignored", false)
    )

    private fun ScanProgress.toJson() = JSONObject().apply {
        put("scannedItems", scannedItems)
        put("totalItemsEstimate", totalItemsEstimate)
        put("phase", phase)
        put("isFinished", isFinished)
    }

    private fun JSONObject.toScanProgress() = ScanProgress(
        scannedItems = optInt("scannedItems", 0),
        totalItemsEstimate = optInt("totalItemsEstimate", 0),
        phase = optString("phase", "Completed"),
        isFinished = optBoolean("isFinished", true)
    )

    private fun <T> JSONArray?.toList(transform: (Any) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                val value = opt(index)
                if (value != null && value != JSONObject.NULL) {
                    add(transform(value))
                }
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key)) return null
        val rawValue = opt(key)
        if (rawValue == null || rawValue == JSONObject.NULL) return null
        val value = rawValue.toString()
        return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}
