// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.rclone

/** Concrete rclone RPC seam used by filesystem mutations and deterministic fault tests. */
internal interface RcloneMutationOperations {
    fun stat(remoteName: String, path: String): RcloneEntry?
    fun createDirectory(remoteName: String, path: String)
    fun deleteFile(remoteName: String, path: String)
    fun deleteEmptyDirectory(remoteName: String, path: String)
    fun copy(sourceRemote: String, sourcePath: String, targetRemote: String, targetPath: String)
    fun move(sourceRemote: String, sourcePath: String, targetRemote: String, targetPath: String)

    companion object {
        val DEFAULT: RcloneMutationOperations = object : RcloneMutationOperations {
            override fun stat(remoteName: String, path: String) =
                RcloneEngine.stat(remoteName, path)
            override fun createDirectory(remoteName: String, path: String) =
                RcloneEngine.createDirectory(remoteName, path)
            override fun deleteFile(remoteName: String, path: String) =
                RcloneEngine.deleteFile(remoteName, path)
            override fun deleteEmptyDirectory(remoteName: String, path: String) =
                RcloneEngine.deleteEmptyDirectory(remoteName, path)
            override fun copy(
                sourceRemote: String,
                sourcePath: String,
                targetRemote: String,
                targetPath: String
            ) = RcloneEngine.copy(sourceRemote, sourcePath, targetRemote, targetPath)
            override fun move(
                sourceRemote: String,
                sourcePath: String,
                targetRemote: String,
                targetPath: String
            ) = RcloneEngine.move(sourceRemote, sourcePath, targetRemote, targetPath)
        }
    }
}
