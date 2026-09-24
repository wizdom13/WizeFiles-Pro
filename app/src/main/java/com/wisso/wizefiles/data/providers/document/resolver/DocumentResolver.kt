// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.document.resolver

import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import java.nio.file.NoSuchFileException
import com.wisso.wizefiles.core.app.contentResolver
import com.wisso.wizefiles.core.android.compat.DocumentsContractCompat
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.provider.content.resolver.Resolver
import com.wisso.wizefiles.provider.content.resolver.ResolverException
import com.wisso.wizefiles.provider.content.resolver.getLong
import com.wisso.wizefiles.provider.content.resolver.getString
import com.wisso.wizefiles.provider.content.resolver.moveToFirstOrThrow
import com.wisso.wizefiles.provider.content.resolver.requireString
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.WeakHashMap

object DocumentResolver {
    // @see com.android.shell.BugreportStorageProvider#AUTHORITY
    private const val BUGREPORT_STORAGE_PROVIDER_AUTHORITY = "com.android.shell.documents"
    // @see com.android.mtp.MtpDocumentsProvider#AUTHORITY
    private const val MTP_DOCUMENTS_PROVIDER_AUTHORITY = "com.android.mtp.documents"

    private val LOCAL_AUTHORITIES = setOf(
        BUGREPORT_STORAGE_PROVIDER_AUTHORITY,
        DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
        MTP_DOCUMENTS_PROVIDER_AUTHORITY
    )
    private val COPY_UNSUPPORTED_AUTHORITIES = setOf(
        BUGREPORT_STORAGE_PROVIDER_AUTHORITY,
        DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
        MTP_DOCUMENTS_PROVIDER_AUTHORITY
    )
    private val MOVE_UNSUPPORTED_AUTHORITIES = setOf(
        MTP_DOCUMENTS_PROVIDER_AUTHORITY
    )
    private val REMOVE_UNSUPPORTED_AUTHORITIES = setOf(
        BUGREPORT_STORAGE_PROVIDER_AUTHORITY,
        DocumentsContractCompat.EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
        MTP_DOCUMENTS_PROVIDER_AUTHORITY
    )

    private val uriResolver = DocumentUriResolver(::query)
    private val metadataMapper = DocumentMetadataMapper(::query)

    private val directoryCursorCache = Collections.synchronizedMap(WeakHashMap<Path, Cursor>())

    @Throws(ResolverException::class)
    fun checkExistence(path: Path) {
        // Prevent cache from interfering with our check. Cache will be added again if
        // queryDocumentId() succeeds.
        uriResolver.invalidate(path)
        uriResolver.documentId(path)
    }

    @Throws(ResolverException::class)
    fun copy(
        sourcePath: Path,
        targetPath: Path,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && sourcePath.hasSameAuthority(targetPath) && !sourcePath.isCopyUnsupported) {
            copyApi24(sourcePath, targetPath, intervalMillis, listener)
        } else {
            copyManually(sourcePath, targetPath, intervalMillis, listener)
        }
    }

    private val Path.isCopyUnsupported: Boolean
        get() = treeUri.authority in COPY_UNSUPPORTED_AUTHORITIES

    @RequiresApi(Build.VERSION_CODES.N)
    @Throws(ResolverException::class)
    private fun copyApi24(
        sourcePath: Path,
        targetPath: Path,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    ): Uri {
        val sourceUri = getDocumentUri(sourcePath)
        val targetParentUri = getDocumentUri(targetPath.requireParent())
        val copiedTargetUri = try {
            // This doesn't support progress interval millis and interruption.
            DocumentsContract.copyDocument(contentResolver, sourceUri, targetParentUri)
        } catch (e: UnsupportedOperationException) {
            // Ignored.
            return copyManually(sourcePath, targetPath, intervalMillis, listener)
        } catch (e: Exception) {
            throw ResolverException(e)
        } ?: throw ResolverException(
            "DocumentsContract.copyDocument() with $sourceUri and $targetParentUri returned null"
        )
        val sourceDisplayName = sourcePath.displayName
        val targetDisplayName = targetPath.displayName
        if (sourceDisplayName == targetDisplayName) {
            listener?.invokeWithSize(copiedTargetUri)
            return copiedTargetUri
        }
        val renamedTargetUri = try {
            rename(copiedTargetUri, targetDisplayName!!)
        } catch (e: ResolverException) {
            try {
                remove(copiedTargetUri, targetParentUri)
            } catch (e2: ResolverException) {
                e.addSuppressed(e2)
            }
            throw e
        }
        listener?.invokeWithSize(renamedTargetUri)
        return renamedTargetUri
    }

    @Throws(ResolverException::class)
    private fun copyManually(
        sourcePath: Path,
        targetPath: Path,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    ): Uri {
        val sourceUri = getDocumentUri(sourcePath)
        val mimeType = try {
            getMimeType(sourceUri)
        } catch (e: ResolverException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            null
        } ?: MimeType.GENERIC.value
        if (mimeType == MimeType.DIRECTORY.value) {
            return create(targetPath, MimeType.DIRECTORY.value)
        }
        val targetUri = create(targetPath, mimeType)
        try {
            Resolver.openInputStream(sourceUri, "r").use { inputStream ->
                Resolver.openOutputStream(targetUri, "wt").use { outputStream ->
                    inputStream.copyTo(outputStream, intervalMillis, listener)
                }
            }
        } catch (e: IOException) {
            val targetParentPath = targetPath.parent
            if (targetParentPath != null) {
                try {
                    val targetParentUri = getDocumentUri(targetParentPath)
                    remove(targetUri, targetParentUri)
                } catch (e2: ResolverException) {
                    e.addSuppressed(e2)
                }
            }
            throw ResolverException(e)
        }
        return targetUri
    }

    @Throws(ResolverException::class)
    fun create(path: Path, mimeType: String): Uri {
        val parentUri = getDocumentUri(path.requireParent())
        // The display name might have been changed so we cannot add the new URI to cache.
        return try {
            DocumentsContract.createDocument(
                contentResolver, parentUri, mimeType, path.displayName!!
            )
        } catch (e: Exception) {
            throw ResolverException(e)
        } ?: throw ResolverException(
            "DocumentsContract.createDocument() with $parentUri returned null"
        )
    }

    @Deprecated("", ReplaceWith("remove(path)"))
    @Throws(ResolverException::class)
    fun delete(path: Path) {
        deleteDocument(path)
    }

    @Deprecated("", ReplaceWith("remove(uri)"))
    @Throws(ResolverException::class)
    fun delete(uri: Uri) {
        deleteDocument(uri)
    }

    @Throws(ResolverException::class)
    private fun deleteDocument(path: Path) {
        val uri = getDocumentUri(path)
        // Always remove the path from cache, in case a deletion actually succeeded despite
        // exception being thrown.
        uriResolver.invalidate(path)
        directoryCursorCache -= path
        deleteDocument(uri)
    }

    @Throws(ResolverException::class)
    private fun deleteDocument(uri: Uri) {
        val deleted = try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (e: Exception) {
            throw ResolverException(e)
        }
        if (!deleted) {
            throw ResolverException("DocumentsContract.deleteDocument() with $uri returned false")
        }
    }

    fun exists(path: Path): Boolean =
        try {
            checkExistence(path)
            true
        } catch (e: ResolverException) {
            false
        }

    @Throws(ResolverException::class)
    fun getMimeType(path: Path): String? {
        val uri = getDocumentUri(path)
        return getMimeType(uri)
    }

    @Throws(ResolverException::class)
    fun getMimeType(uri: Uri): String? =
        metadataMapper.mimeType(uri)

    @Throws(ResolverException::class)
    fun getSize(path: Path): Long? {
        val uri = getDocumentUri(path)
        return getSize(uri)
    }

    @Throws(ResolverException::class)
    fun getSize(uri: Uri): Long? =
        metadataMapper.size(uri)

    @Throws(ResolverException::class)
    fun getThumbnail(path: Path, width: Int, height: Int, signal: CancellationSignal): Bitmap? {
        val uri = getDocumentUri(path)
        return try {
            DocumentsContract.getDocumentThumbnail(
                contentResolver, uri, Point(width, height), signal
            )
        } catch (e: Exception) {
            throw ResolverException(e)
        }
    }

    fun isLocal(path: Path): Boolean {
        val authority = path.treeUri.authority
        return authority in LOCAL_AUTHORITIES
    }

    @Throws(ResolverException::class)
    fun move(
        sourcePath: Path,
        targetPath: Path,
        moveOnly: Boolean,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    ): Uri {
        val sourceParentPath = sourcePath.requireParent()
        val targetParentPath = targetPath.requireParent()
        if (sourceParentPath == targetParentPath) {
            return rename(sourcePath, targetPath.displayName!!)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && sourcePath.hasSameAuthority(targetPath) && !sourcePath.isMoveUnsupported) {
            moveApi24(sourcePath, targetPath, moveOnly, intervalMillis, listener)
        } else {
            if (moveOnly) {
                // @see DocumentsProvider.moveDocument(String, String, String)
                throw ResolverException(UnsupportedOperationException("Move not supported"))
            }
            moveByCopy(sourcePath, targetPath, intervalMillis, listener)
        }
    }

    private fun Path.hasSameAuthority(other: Path): Boolean =
        treeUri.authority == other.treeUri.authority

    private val Path.isMoveUnsupported: Boolean
        get() = treeUri.authority in MOVE_UNSUPPORTED_AUTHORITIES

    @RequiresApi(Build.VERSION_CODES.N)
    @Throws(ResolverException::class)
    private fun moveApi24(
        sourcePath: Path,
        targetPath: Path,
        moveOnly: Boolean,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    ): Uri {
        val sourceParentUri = getDocumentUri(sourcePath.requireParent())
        val sourceUri = getDocumentUri(sourcePath)
        val targetParentUri = getDocumentUri(targetPath.requireParent())
        val movedTargetUri = try {
            // This doesn't support progress interval millis and interruption.
            DocumentsContract.moveDocument(
                contentResolver, sourceUri, sourceParentUri, targetParentUri
            )
        } catch (e: UnsupportedOperationException) {
            if (moveOnly) {
                throw ResolverException(e)
            }
            return moveByCopy(sourcePath, targetPath, intervalMillis, listener)
        } catch (e: Exception) {
            throw ResolverException(e)
        } ?: throw ResolverException(
            "DocumentsContract.moveDocument() with $sourceUri and $targetParentUri returned null"
        )
        val sourceDisplayName = sourcePath.displayName
        val targetDisplayName = targetPath.displayName
        if (sourceDisplayName == targetDisplayName) {
            listener?.invokeWithSize(movedTargetUri)
            return movedTargetUri
        }
        val renamedTargetUri = rename(movedTargetUri, targetDisplayName!!)
        listener?.invokeWithSize(renamedTargetUri)
        return renamedTargetUri
    }

    private fun ((Long) -> Unit).invokeWithSize(uri: Uri) {
        val size = try {
            getSize(uri)
        } catch (e: ResolverException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            return
        } ?: return
        this(size)
    }

    @Throws(ResolverException::class)
    private fun moveByCopy(
        sourcePath: Path,
        targetPath: Path,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    ): Uri {
        val targetUri = copy(sourcePath, targetPath, intervalMillis, listener)
        try {
            val sourceUri = getDocumentUri(sourcePath)
            val sourceParentUri = getDocumentUri(sourcePath.requireParent())
            remove(sourceUri, sourceParentUri)
        } catch (e: ResolverException) {
            if (e.toFileSystemException(sourcePath.toString()) !is NoSuchFileException) {
                try {
                    val targetParentUri = getDocumentUri(targetPath.requireParent())
                    remove(targetUri, targetParentUri)
                } catch (e2: ResolverException) {
                    e.addSuppressed(e2)
                }
            }
            throw e
        }
        return targetUri
    }

    @Throws(ResolverException::class)
    fun openInputStream(path: Path, mode: String): InputStream {
        val uri = getDocumentUri(path)
        return Resolver.openInputStream(uri, mode)
    }

    @Throws(ResolverException::class)
    fun openOutputStream(path: Path, mode: String): OutputStream {
        val uri = getDocumentUri(path)
        return Resolver.openOutputStream(uri, mode)
    }

    @Throws(ResolverException::class)
    fun openParcelFileDescriptor(
        path: Path,
        mode: String
    ): ParcelFileDescriptor {
        val uri = getDocumentUri(path)
        return Resolver.openParcelFileDescriptor(uri, mode)
    }

    @Throws(ResolverException::class)
    fun queryChildren(parentPath: Path): List<Path> {
        val parentDocumentId = uriResolver.documentId(parentPath)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentPath.treeUri, parentDocumentId
        )
        var refreshCount = 0
        while (true) {
            // A null projection means all supported columns should be included according to
            // [DocumentsProvider.queryChildDocuments]. This is fine for functionality and
            // performance as DocumentsProviderHelper in DocumentsUI is doing the same thing.
            query(childrenUri, null, null).use { cursor ->
                val decision = DocumentRetryPolicy.decide(
                    loading = cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING),
                    error = cursor.extras.getString(DocumentsContract.EXTRA_ERROR),
                    refreshCount = refreshCount
                )
                when (decision) {
                    DocumentQueryPolicy.Decision.ConsumeRows -> Unit
                    DocumentQueryPolicy.Decision.WaitAndRetry -> {
                        ++refreshCount
                        DocumentQueryClient.waitUntilChanged(cursor)
                        return@use
                    }
                    is DocumentQueryPolicy.Decision.Fail -> throw ResolverException(decision.message)
                }
                val childrenPaths = mutableListOf<Path>()
                while (cursor.moveToNext()) {
                    val childDocumentId = cursor.requireString(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    )
                    val childDisplayName = cursor.requireString(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                    val childPath = parentPath.resolve(childDisplayName)
                    uriResolver.remember(childPath, childDocumentId)
                    directoryCursorCache[childPath] = DocumentQueryClient.rowSnapshot(cursor)
                    childrenPaths += childPath
                }
                return childrenPaths
            }
        }
    }

    @Throws(ResolverException::class)
    fun queryDocument(path: Path, uri: Uri): Cursor {
        directoryCursorCache.remove(path)?.let { return it }
        // A null projection means all supported columns should be included according to
        // [DocumentsProvider.queryDocument]. This is fine for functionality and performance as
        // DocumentsProviderHelper in DocumentsUI is doing the same thing.
        return query(uri, null, null)
    }

    @Throws(ResolverException::class)
    fun remove(path: Path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isRemoveUnsupported(path)) {
            removeApi24(path)
        } else {
            deleteDocument(path)
        }
    }

    @Throws(ResolverException::class)
    fun remove(uri: Uri, parentUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isRemoveUnsupported(uri)) {
            removeApi24(uri, parentUri)
        } else {
            deleteDocument(uri)
        }
    }

    private fun isRemoveUnsupported(path: Path): Boolean = isRemoveUnsupported(path.treeUri)

    private fun isRemoveUnsupported(uri: Uri): Boolean =
        uri.authority in REMOVE_UNSUPPORTED_AUTHORITIES

    @RequiresApi(Build.VERSION_CODES.N)
    @Throws(ResolverException::class)
    private fun removeApi24(path: Path) {
        val uri = getDocumentUri(path)
        val parentUri = getDocumentUri(path.requireParent())
        // Always remove the path from cache, in case a removal actually succeeded despite exception
        // being thrown.
        uriResolver.invalidate(path)
        directoryCursorCache -= path
        removeApi24(uri, parentUri)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Throws(ResolverException::class)
    private fun removeApi24(uri: Uri, parentUri: Uri) {
        val removed = try {
            DocumentsContract.removeDocument(contentResolver, uri, parentUri)
        } catch (e: UnsupportedOperationException) {
            // Ignored.
            deleteDocument(uri)
            return
        } catch (e: Exception) {
            throw ResolverException(e)
        }
        if (!removed) {
            throw ResolverException("DocumentsContract.removeDocument() $uri returned false")
        }
    }

    @Throws(ResolverException::class)
    fun rename(path: Path, displayName: String): Uri {
        val uri = getDocumentUri(path)
        // Always remove the path from cache, in case a rename actually succeeded despite exception
        // being thrown.
        uriResolver.invalidate(path)
        directoryCursorCache -= path
        return rename(uri, displayName)
    }

    @Throws(ResolverException::class)
    fun rename(uri: Uri, displayName: String): Uri =
        try {
            DocumentsContract.renameDocument(contentResolver, uri, displayName)
        } catch (e: Exception) {
            throw ResolverException(e)
        } ?: throw ResolverException(
            "DocumentsContract.renameDocument() with $uri and $displayName returned null"
        )

    @Throws(ResolverException::class)
    fun getDocumentUri(path: Path): Uri = uriResolver.documentUri(path)

    fun getDocumentChildrenUri(path: Path): Uri = uriResolver.childrenUri(path)

    @Throws(ResolverException::class)
    fun query(uri: Uri, projection: Array<out String?>?, sortOrder: String?): Cursor =
        DocumentQueryClient.query(uri, projection, sortOrder)

    @Throws(ResolverException::class)
    private fun Path.requireParent(): Path =
        parent ?: throw ResolverException("Path.getParent() with $this returned null")

    interface Path {
        val treeUri: Uri
        val displayName: String?
        val parent: Path?
        fun resolve(other: String): Path
    }


}
