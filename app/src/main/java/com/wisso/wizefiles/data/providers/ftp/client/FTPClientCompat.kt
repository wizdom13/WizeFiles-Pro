// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp.client

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPFile
import java.io.IOException
import java.util.Calendar

@Throws(IOException::class)
fun FTPClient.mlistFileCompat(pathname: String): FTPFile? {
    if (hasFeature(FTPCmd.MLST)) return mlistFile(pathname)

    val lookup = splitLookupPath(pathname) ?: return newSyntheticRoot()
    return listFiles(lookup.directory)
        ?.asSequence()
        ?.filterNotNull()
        ?.firstOrNull { entry -> entry.name == lookup.name }
}

@Throws(IOException::class)
fun FTPClient.mlistDirCompat(pathname: String): Array<FTPFile>? {
    return if (hasFeature(FTPCmd.MLST)) mlistDir(pathname) else listFiles(pathname)
}

@Throws(IOException::class)
fun FTPClient.setModificationTimeCompat(pathname: String, timeval: String): Boolean {
    if (!hasFeature(FTPCmd.MFMT)) {
        throw IOException("FTP server does not advertise " + FTPCmd.MFMT.command)
    }
    return setModificationTime(pathname, timeval)
}

private fun splitLookupPath(pathname: String): LookupPath? {
    val normalized = pathname.trimEnd('/')
    if (normalized.isEmpty()) return null

    val separator = normalized.lastIndexOf('/')
    val directory = when {
        separator < 0 -> "."
        separator == 0 -> "/"
        else -> normalized.substring(0, separator)
    }
    val name = normalized.substring(separator + 1)
    return LookupPath(directory, name)
}

private fun newSyntheticRoot(): FTPFile =
    FTPFile().also { root ->
        root.rawListing = "Type=dir;Size=4096;Modify=19700101000000;Perm=cdeflmp; /"
        root.type = FTPFile.DIRECTORY_TYPE
        root.size = 4096
        root.timestamp = Calendar.getInstance().apply { timeInMillis = 0 }
        root.setPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION, true)
        root.setPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION, true)
        root.setPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION, true)
        root.name = "/"
    }

private data class LookupPath(val directory: String, val name: String)
