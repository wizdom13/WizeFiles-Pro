package com.wisso.wizefiles.core.imageloader.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.use
import com.wisso.wizefiles.core.files.extensions.lastModifiedInstant
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.core.files.mime.isApk
import com.wisso.wizefiles.core.files.mime.isImage
import com.wisso.wizefiles.core.files.mime.isMedia
import com.wisso.wizefiles.core.files.mime.isPdf
import com.wisso.wizefiles.core.files.mime.isVideo
import com.wisso.wizefiles.core.imageloader.coil.legacy.isDocumentUriLike
import com.wisso.wizefiles.core.imageloader.coil.legacy.isFtpUriLike
import com.wisso.wizefiles.core.imageloader.coil.legacy.isRemoteUriLike
import com.wisso.wizefiles.core.imageloader.coil.legacy.openInputStream
import com.wisso.wizefiles.core.imageloader.coil.legacy.openReadOnlyParcelFileDescriptor
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.util.getDimensionPixelSize
import com.wisso.wizefiles.util.getPackageArchiveInfoCompat
import com.wisso.wizefiles.util.isGetPackageArchiveInfoCompatible
import com.wisso.wizefiles.util.isMediaMetadataRetrieverCompatible
import com.wisso.wizefiles.util.runMediaProbeOrNull
import com.wisso.wizefiles.util.setDataSource
import com.wisso.wizefiles.util.valueCompat
import java.io.Closeable
import java.io.IOException
import okio.buffer
import okio.source
import com.wisso.wizefiles.util.setDataSource as appSetDataSource

class PathAttributesKeyer : Keyer<Pair<AppPath, FileMetadata>> {
    override fun key(data: Pair<AppPath, FileMetadata>, options: Options): String {
        val (path, attributes) = data
        return "$path:${attributes.sizeBytes}:${attributes.lastModifiedInstant.toEpochMilli()}:icons-v2"
    }
}

class PathAttributesFetcher(
    private val data: Pair<AppPath, FileMetadata>,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val appIconFetcherFactory: AppIconFetcher.Factory<AppPath>,
    private val videoFrameFetcherFactory: VideoFrameFetcher.Factory<AppPath>,
    private val pdfPageFetcherFactory: PdfPageFetcher.Factory<AppPath>
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val (appPath, _) = data
        val (width, height) = options.size
        val isThumbnail = width is Dimension.Pixels && width.px <= 512
            && height is Dimension.Pixels && height.px <= 384
        if (isThumbnail) {
            width as Dimension.Pixels
            height as Dimension.Pixels
            if (appPath.isRemoteUriLike) {
                val shouldReadRemotePath = !appPath.isFtpUriLike
                    && Settings.READ_REMOTE_FILES_FOR_THUMBNAIL.valueCompat
                if (!shouldReadRemotePath) {
                    error("Cannot read $appPath for thumbnail")
                }
            }
        }
        val packageContainerKind = AndroidPackageArchiveKind.fromPath(appPath.rawPath)
        if (packageContainerKind != null) {
            return AndroidPackageContainerIconFetcher(
                data,
                options,
                options.context.getDimensionPixelSize(R.dimen.large_icon_size)
            ).fetch()
        }
        val mimeType = MimeType.guessFromPath(appPath.rawPath)
        when {
            mimeType.isApk && appPath.isGetPackageArchiveInfoCompatible -> {
                try {
                    return appIconFetcherFactory.create(appPath, options, imageLoader).fetch()
                } catch (e: Exception) {
                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                }
            }
            mimeType.isImage || mimeType == MimeType.GENERIC -> {
                val inputStream = appPath.openInputStream(options.context)
                return SourceResult(
                    ImageSource(inputStream.source().buffer(), options.context),
                    if (mimeType != MimeType.GENERIC) mimeType.value else null, appPath.dataSource
                )
            }
            mimeType.isMedia && appPath.isMediaMetadataRetrieverCompatible -> {
                if (shouldProbeEmbeddedPicture(mimeType) && !EmbeddedPictureFailureCache.shouldSkip(appPath)) {
                    val embeddedPicture = MediaMetadataRetriever().use { retriever ->
                        runMediaProbeOrNull(
                            setDataSource = { retriever.setDataSource(appPath) },
                            readValue = { retriever.embeddedPicture }
                        )
                    }
                    if (embeddedPicture != null) {
                        EmbeddedPictureFailureCache.clear(appPath)
                        return SourceResult(
                            ImageSource(
                                embeddedPicture.inputStream().source().buffer(), options.context
                            ), null, appPath.dataSource
                        )
                    } else {
                        EmbeddedPictureFailureCache.markFailed(appPath)
                    }
                }
                if (mimeType.isVideo) {
                    if (VideoThumbnailFailureCache.shouldSkip(appPath)) {
                        return null
                    }
                    try {
                        val result = runRuntimeMediaProbeOrNull {
                            videoFrameFetcherFactory.create(appPath, options, imageLoader).fetch()
                        }
                        if (result == null) {
                            VideoThumbnailFailureCache.markFailed(appPath)
                        } else {
                            VideoThumbnailFailureCache.clear(appPath)
                        }
                        return result
                    } catch (_: Exception) {
                        VideoThumbnailFailureCache.markFailed(appPath)
                        return null
                    }
                }
            }
            mimeType.isPdf && (appPath.isDocumentUriLike || appPath.toString().startsWith("/")) -> {
                try {
                    return pdfPageFetcherFactory.create(appPath, options, imageLoader).fetch()
                } catch (e: Exception) {
                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                }
            }
        }
        return null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Pair<AppPath, FileMetadata>> {
        private val appIconFetcherFactory = object : AppIconFetcher.Factory<AppPath>(
            context.getDimensionPixelSize(R.dimen.large_icon_size), context
        ) {
            override fun getApplicationInfo(data: AppPath): Pair<ApplicationInfo, Closeable?> {
                val (packageInfo, closeable) =
                    context.packageManager.getPackageArchiveInfoCompat(data, 0)
                val applicationInfo = packageInfo?.applicationInfo
                if (applicationInfo == null) {
                    closeable?.close()
                    throw IOException("ApplicationInfo is null")
                }
                return applicationInfo to closeable
            }
        }

        private val videoFrameFetcherFactory = object : VideoFrameFetcher.Factory<AppPath>() {
            override fun MediaMetadataRetriever.setDataSource(data: AppPath) {
                appSetDataSource(data)
            }
        }

        private val pdfPageFetcherFactory = object : PdfPageFetcher.Factory<AppPath>() {
            override fun openParcelFileDescriptor(data: AppPath): ParcelFileDescriptor {
                return data.openReadOnlyParcelFileDescriptor(context)
            }
        }

        override fun create(
            data: Pair<AppPath, FileMetadata>,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher =
            PathAttributesFetcher(
                data, options, imageLoader, appIconFetcherFactory, videoFrameFetcherFactory,
                pdfPageFetcherFactory
            )
    }
}

internal fun shouldProbeEmbeddedPicture(mimeType: MimeType): Boolean =
    mimeType.isMedia && !mimeType.isVideo

internal inline fun <T> runRuntimeMediaProbeOrNull(readValue: () -> T): T? =
    try {
        readValue()
    } catch (_: RuntimeException) {
        null
    }
