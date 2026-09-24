// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileSettableInformation
import com.wisso.wizefiles.provider.smb.client.PathInformation
import com.wisso.wizefiles.provider.smb.client.SmbClient
import com.wisso.wizefiles.provider.smb.client.SymbolicLinkReparseData

/** Concrete SMB protocol seam used by mutation code and deterministic fault tests. */
internal interface SmbMutationOperations {
    fun getPathInformation(path: SmbPath, openReparsePoint: Boolean): PathInformation?
    fun delete(path: SmbPath)
    fun copyFile(
        source: SmbPath,
        target: SmbPath,
        copyAttributes: Boolean,
        openReparsePoint: Boolean,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    )
    fun createDirectory(path: SmbPath, fileAttributes: Set<FileAttributes>)
    fun readSymbolicLink(path: SmbPath): SymbolicLinkReparseData
    fun createSymbolicLink(
        path: SmbPath,
        reparseData: SymbolicLinkReparseData,
        fileAttributes: Set<FileAttributes>
    )
    fun setFileInformation(
        path: SmbPath,
        openReparsePoint: Boolean,
        fileInformation: FileSettableInformation
    )
    fun rename(source: SmbPath, target: SmbPath)

    companion object {
        val DEFAULT: SmbMutationOperations = object : SmbMutationOperations {
            override fun getPathInformation(path: SmbPath, openReparsePoint: Boolean) =
                SmbClient.getPathInformation(path, openReparsePoint)
            override fun delete(path: SmbPath) = SmbClient.delete(path)
            override fun copyFile(
                source: SmbPath,
                target: SmbPath,
                copyAttributes: Boolean,
                openReparsePoint: Boolean,
                intervalMillis: Long,
                listener: ((Long) -> Unit)?
            ) = SmbClient.copyFile(
                source, target, copyAttributes, openReparsePoint, intervalMillis, listener
            )
            override fun createDirectory(path: SmbPath, fileAttributes: Set<FileAttributes>) =
                SmbClient.createDirectory(path, fileAttributes)
            override fun readSymbolicLink(path: SmbPath) = SmbClient.readSymbolicLink(path)
            override fun createSymbolicLink(
                path: SmbPath,
                reparseData: SymbolicLinkReparseData,
                fileAttributes: Set<FileAttributes>
            ) = SmbClient.createSymbolicLink(path, reparseData, fileAttributes)
            override fun setFileInformation(
                path: SmbPath,
                openReparsePoint: Boolean,
                fileInformation: FileSettableInformation
            ) = SmbClient.setFileInformation(path, openReparsePoint, fileInformation)
            override fun rename(source: SmbPath, target: SmbPath) =
                SmbClient.rename(source, target)
        }
    }
}
