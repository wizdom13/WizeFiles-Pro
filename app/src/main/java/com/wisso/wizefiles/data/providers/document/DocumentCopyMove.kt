// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.document

import android.net.Uri
import java.nio.file.FileAlreadyExistsException
import java.nio.file.StandardCopyOption
import com.wisso.wizefiles.provider.common.CopyOptions
import com.wisso.wizefiles.provider.content.resolver.ResolverException
import com.wisso.wizefiles.provider.document.resolver.DocumentResolver
import java.io.IOException
import java.io.InterruptedIOException

internal object DocumentCopyMove {
    @Throws(IOException::class)
    fun copy(source: DocumentPath, target: DocumentPath, copyOptions: CopyOptions): Uri {
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        if (source == target) {
            val targetUri = try {
                DocumentResolver.getDocumentUri(target)
            } catch (e: ResolverException) {
                throw e.toFileSystemException(target.toString())
            }
            copyOptions.progressListener?.invokeWithSize(targetUri)
            return targetUri
        }
        val targetExists = DocumentResolver.exists(target)
        if (targetExists) {
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(target.toString())
            }
            try {
                DocumentResolver.remove(target)
            } catch (e: ResolverException) {
                throw e.toFileSystemException(target.toString())
            }
        }
        return try {
            DocumentResolver.copy(
                source, target, copyOptions.progressIntervalMillis, copyOptions.progressListener
            )
        } catch (e: ResolverException) {
            (e.cause as? InterruptedIOException)?.let { throw it }
            throw e.toFileSystemException(source.toString(), target.toString())
        }
    }

    @Throws(IOException::class)
    fun move(source: DocumentPath, target: DocumentPath, copyOptions: CopyOptions): Uri {
        if (source == target) {
            val targetUri = try {
                DocumentResolver.getDocumentUri(target)
            } catch (e: ResolverException) {
                throw e.toFileSystemException(target.toString())
            }
            copyOptions.progressListener?.invokeWithSize(targetUri)
            return targetUri
        }
        val targetExists = DocumentResolver.exists(target)
        if (targetExists) {
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(target.toString())
            }
            try {
                DocumentResolver.remove(target)
            } catch (e: ResolverException) {
                throw e.toFileSystemException(target.toString())
            }
        }
        return try {
            DocumentResolver.move(
                source, target, copyOptions.atomicMove, copyOptions.progressIntervalMillis,
                copyOptions.progressListener
            )
        } catch (e: ResolverException) {
            (e.cause as? InterruptedIOException)?.let { throw it }
            throw e.toFileSystemException(source.toString(), target.toString())
        }
    }

    private fun ((Long) -> Unit).invokeWithSize(uri: Uri) {
        val size = try {
            DocumentResolver.getSize(uri)
        } catch (e: ResolverException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            return
        } ?: return
        this(size)
    }
}
