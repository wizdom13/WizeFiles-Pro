package com.wisso.wizefiles.storagecleaner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.clipboardManager
import com.wisso.wizefiles.databinding.ActivityStorageCleanerBinding
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.core.files.mime.iconRes
import com.wisso.wizefiles.core.files.mime.asMimeType
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.core.files.mime.isImage
import com.wisso.wizefiles.core.files.mime.isMedia
import com.wisso.wizefiles.core.files.mime.isPdf
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.filebrowser.OpenFileActivity
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.filebrowser.isRemotePath
import com.wisso.wizefiles.provider.common.AndroidFileTypeDetector
import com.wisso.wizefiles.provider.ftp.isFtpPath
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.settings.Settings as AppSettings
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.ui.FileIconShapeView
import com.wisso.wizefiles.util.copyText
import com.wisso.wizefiles.util.isGetPackageArchiveInfoCompatible
import com.wisso.wizefiles.util.isMediaMetadataRetrieverCompatible
import com.wisso.wizefiles.util.startActivitySafe
import com.wisso.wizefiles.util.valueCompat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

private const val MENU_IGNORED_ITEMS = 1001

internal fun buildStorageCleanerHintLines(
    missingCapabilities: List<String>,
    stateMessage: String?,
    hasUsageAccess: Boolean,
    usageAccessCapabilityPrefix: String,
    usageAccessMessage: String,
    limitedResultsPrefix: String
): List<String> {
    val normalizedUsagePrefix = usageAccessCapabilityPrefix.trim()
    fun isUsageAccessHint(text: String): Boolean {
        val matchesCapabilityPrefix =
            normalizedUsagePrefix.isNotEmpty() &&
                text.contains(normalizedUsagePrefix, ignoreCase = true)
        return matchesCapabilityPrefix ||
            text.contains("usage access", ignoreCase = true) ||
            text.contains("unused app", ignoreCase = true)
    }

    return buildList {
        addAll(
            missingCapabilities
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot(::isUsageAccessHint)
        )
        if (!hasUsageAccess) {
            add(usageAccessMessage)
        }
        stateMessage
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.takeUnless {
                it.startsWith(limitedResultsPrefix, ignoreCase = true)
            }
            ?.let(::add)
    }.distinctBy { it.lowercase() }
}

internal fun shouldShowStorageCleanerToast(
    message: String,
    limitedResultsPrefix: String
): Boolean = !message.startsWith(limitedResultsPrefix, ignoreCase = true)

internal data class MetadataTextSpec(
    val text: String,
    val sizeRange: IntRange?
)

internal fun metadataTextSpec(sizeText: String, filePath: String?): MetadataTextSpec {
    val normalizedSize = sizeText.trim()
    val normalizedPath = filePath?.takeIf { it.isNotBlank() }
    val details = listOfNotNull(normalizedSize.takeIf { it.isNotBlank() }, normalizedPath)
    if (details.isEmpty()) return MetadataTextSpec(text = "", sizeRange = null)

    val text = details.joinToString(" • ")
    val sizeRange = if (normalizedSize.isNotEmpty()) {
        0 until normalizedSize.length
    } else {
        null
    }
    return MetadataTextSpec(text = text, sizeRange = sizeRange)
}

internal fun buildMetadataText(sizeText: String, filePath: String?): CharSequence {
    val spec = metadataTextSpec(sizeText, filePath)
    if (spec.text.isEmpty()) return ""
    val builder = SpannableStringBuilder(spec.text)
    spec.sizeRange?.let { range ->
        builder.setSpan(StyleSpan(Typeface.BOLD), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return builder
}

private data class RecommendationSection(
    val key: SectionKey,
    val recommendations: List<CleanupRecommendation>
)

internal fun List<CleanupRecommendation>.toSectionedList(
    expandedSections: Map<SectionKey, Boolean>,
    selectedIds: Set<String>,
    sectionSummary: (List<CleanupRecommendation>) -> String
): List<StorageCleanerListItem> {
    return toSections().flatMap { section ->
        val summary = sectionSummary(section.recommendations)
        val isExpanded = expandedSections[section.key] == true
        val childIds = section.recommendations
            .filter { it.isDeletionCandidate }
            .map { it.id }
        val hasChildren = childIds.isNotEmpty()
        val areAllChildrenSelected = hasChildren && childIds.all { it in selectedIds }
        val header = StorageCleanerListItem.SectionHeader(
            key = section.key,
            title = section.key.title,
            summary = summary,
            expanded = isExpanded,
            childRecommendationIds = childIds,
            areAllChildrenSelected = areAllChildrenSelected
        )
        if (isExpanded) {
            listOf(header) + section.recommendations.map { StorageCleanerListItem.RecommendationRow(it) }
        } else {
            listOf(header)
        }
    }
}

private fun List<CleanupRecommendation>.toSections(): List<RecommendationSection> {
    if (isEmpty()) return emptyList()
    val grouped = groupBy { it.toSectionKey() }
    val order = listOf(
        SectionKey.DUPLICATE_MEDIA,
        SectionKey.DUPLICATE_FILES,
        SectionKey.LARGE_FILES,
        SectionKey.APK_FILES,
        SectionKey.UNUSED_APPS,
        SectionKey.OLD_DOWNLOADS,
        SectionKey.JUNK_FILES,
        SectionKey.OLD_FILES,
        SectionKey.OTHER
    )
    return order.mapNotNull { key ->
        val recs = grouped[key].orEmpty()
        if (recs.isEmpty()) null else RecommendationSection(key, recs)
    }
}

internal fun CleanupRecommendation.toSectionKey(): SectionKey = when (type) {
    RecommendationType.DUPLICATE_MEDIA -> SectionKey.DUPLICATE_MEDIA
    RecommendationType.DUPLICATE_FILES -> SectionKey.DUPLICATE_FILES
    RecommendationType.DUPLICATE -> SectionKey.DUPLICATE_MEDIA
    RecommendationType.LARGE_FILE -> SectionKey.LARGE_FILES
    RecommendationType.APK_FILE -> SectionKey.APK_FILES
    RecommendationType.UNUSED_APP -> SectionKey.UNUSED_APPS
    RecommendationType.DOWNLOADS -> SectionKey.OLD_DOWNLOADS
    RecommendationType.JUNK -> SectionKey.JUNK_FILES
    RecommendationType.STALE_FILE -> {
        if (isDownloadRelated || path?.contains("/Download", ignoreCase = true) == true) {
            SectionKey.OLD_DOWNLOADS
        } else {
            SectionKey.OLD_FILES
        }
    }
}

internal data class RecommendationVisualSpec(
    val fallbackIconRes: Int,
    val shouldUsePackageIcon: Boolean
)

internal data class StorageCleanerFileVisual(
    val fallbackIconRes: Int,
    val shouldLoadPreview: Boolean,
    val isAppIcon: Boolean,
    val requestData: Pair<AppPath, FileMetadata>?
)

internal fun recommendationVisualSpec(item: CleanupRecommendation): RecommendationVisualSpec {
    return RecommendationVisualSpec(
        fallbackIconRes = iconForRecommendation(item),
        shouldUsePackageIcon = item.type == RecommendationType.UNUSED_APP && !item.packageName.isNullOrBlank()
    )
}

internal fun storageCleanerFileVisual(path: String?): StorageCleanerFileVisual? {
    val normalizedPath = path?.takeIf { it.isNotBlank() } ?: return null
    val parsedPath = runCatching { Paths.get(normalizedPath) }.getOrNull()
    val attributes = parsedPath?.let {
        runCatching { java.nio.file.Files.readAttributes(it, BasicFileAttributes::class.java) }.getOrNull()
    }
    val mimeType = if (parsedPath != null && attributes != null) {
        runCatching { AndroidFileTypeDetector.getMimeType(parsedPath, attributes).asMimeType() }
            .getOrElse { fallbackMimeTypeForPath(normalizedPath) }
    } else {
        fallbackMimeTypeForPath(normalizedPath)
    }
    val resolvedMimeType = if (mimeType == MimeType.GENERIC) fallbackMimeTypeForPath(normalizedPath) else mimeType
    val fallbackIconRes = resolvedMimeType.iconRes
    val supportsPreview = parsedPath != null && attributes != null && supportsThumbnail(parsedPath, resolvedMimeType)
    val requestData = if (supportsPreview) {
        val previewPath = requireNotNull(parsedPath)
        val previewAttributes = requireNotNull(attributes)
        previewPath.toAppPath() to FileMetadata(
            isDirectory = previewAttributes.isDirectory,
            sizeBytes = previewAttributes.size(),
            lastModifiedEpochMillis = previewAttributes.lastModifiedTime().toMillis(),
            isSymbolicLink = previewAttributes.isSymbolicLink
        )
    } else {
        null
    }
    return StorageCleanerFileVisual(
        fallbackIconRes = fallbackIconRes,
        shouldLoadPreview = requestData != null,
        isAppIcon = resolvedMimeType.isApk,
        requestData = requestData
    )
}


internal fun fallbackMimeTypeForPath(path: String): MimeType {
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (extension) {
        "apk", "apkm", "xapk" -> MimeType.APK
        "pdf" -> MimeType.PDF
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp" -> MimeType("video/*")
        "mp3", "wav", "ogg", "m4a", "flac", "aac" -> MimeType("audio/*")
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> MimeType("image/*")
        "zip", "rar", "7z", "tar", "gz", "xz" -> MimeType("application/zip")
        else -> runCatching { MimeType.guessFromPath(path) }.getOrDefault(MimeType.GENERIC)
    }
}

internal fun supportsThumbnail(path: Path, mimeType: MimeType): Boolean {
    if (path.isRemotePath) {
        val shouldReadRemotePath = !path.isFtpPath && AppSettings.READ_REMOTE_FILES_FOR_THUMBNAIL.valueCompat
        if (!shouldReadRemotePath) {
            return false
        }
    }
    val appPath = path.toAppPath()
    return when {
        mimeType.isApk && appPath.isGetPackageArchiveInfoCompatible -> true
        mimeType.isImage -> true
        mimeType.isMedia && appPath.isMediaMetadataRetrieverCompatible -> true
        mimeType.isPdf && path.isLinuxPath -> true
        else -> false
    }
}

internal fun iconForRecommendation(item: CleanupRecommendation): Int = when (item.toSectionKey()) {
    SectionKey.LARGE_FILES,
    SectionKey.OLD_FILES -> R.drawable.ic_file_type_generic
    SectionKey.UNUSED_APPS -> R.drawable.ic_bs_app_indicator_24dp
    SectionKey.OLD_DOWNLOADS -> R.drawable.ic_bs_download_24dp
    SectionKey.DUPLICATE_MEDIA -> R.drawable.ic_bs_images_24dp
    SectionKey.DUPLICATE_FILES -> R.drawable.ic_file_type_generic
    SectionKey.APK_FILES -> com.wisso.wizefiles.core.files.mime.MimeType.APK.iconRes
    SectionKey.JUNK_FILES -> R.drawable.ic_bs_trash_24dp
    SectionKey.OTHER -> R.drawable.ic_bs_info_circle_24dp
}

internal fun buildSectionSummary(
    resources: android.content.res.Resources,
    recommendations: List<CleanupRecommendation>,
    formatSize: (Long) -> String
): String {
    val totalSize = recommendations.sumOf { it.reclaimableBytes }
    val itemLabel = resources.getQuantityString(R.plurals.storage_cleaner_section_item_count, recommendations.size, recommendations.size)
    return "$itemLabel • ${formatSize(totalSize)}"
}

enum class SectionKey(val title: String) {
    DUPLICATE_MEDIA("Duplicate media"),
    DUPLICATE_FILES("Duplicate files"),
    LARGE_FILES("Large files"),
    APK_FILES("APK files"),
    UNUSED_APPS("Unused apps"),
    OLD_DOWNLOADS("Old downloads"),
    JUNK_FILES("Junk files"),
    OLD_FILES("Old files"),
    OTHER("Other findings")
}

internal fun SectionKey.localizedTitle(context: android.content.Context): String = when (this) {
    SectionKey.DUPLICATE_MEDIA -> context.getString(R.string.storage_cleaner_duplicate_media_title)
    SectionKey.DUPLICATE_FILES -> context.getString(R.string.storage_cleaner_duplicate_files_title)
    SectionKey.LARGE_FILES -> context.getString(R.string.storage_cleaner_large_files_title)
    SectionKey.APK_FILES -> context.getString(R.string.storage_cleaner_apk_files_title)
    SectionKey.UNUSED_APPS -> context.getString(R.string.storage_cleaner_unused_apps_title)
    SectionKey.OLD_DOWNLOADS -> context.getString(R.string.storage_cleaner_old_downloads_title)
    SectionKey.JUNK_FILES -> context.getString(R.string.storage_cleaner_junk_files_title)
    SectionKey.OLD_FILES -> context.getString(R.string.storage_cleaner_old_files_title)
    SectionKey.OTHER -> context.getString(R.string.storage_cleaner_other_findings_title)
}
