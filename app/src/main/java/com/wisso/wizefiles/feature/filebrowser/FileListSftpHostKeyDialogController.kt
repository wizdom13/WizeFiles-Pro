// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.provider.sftp.client.SftpHostKeyMismatchException
import com.wisso.wizefiles.provider.sftp.client.SftpHostKeyTrustStore
import com.wisso.wizefiles.provider.sftp.client.SftpHostKeyVerificationException
import com.wisso.wizefiles.provider.sftp.client.SftpPresentedHostKey
import com.wisso.wizefiles.provider.sftp.client.SftpUnknownHostKeyException
import com.wisso.wizefiles.util.showToast

internal class FileListSftpHostKeyDialogController(
    private val fragment: Fragment,
    private val refresh: () -> Unit
) {
    fun showUnknown(exception: SftpUnknownHostKeyException) {
        if (!fragment.isAdded) {
            return
        }
        val canTrust = exception.presentedEncodedKeyBase64.isNotBlank()
        val builder = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.storage_edit_sftp_server_host_key_unknown_title)
            .setMessage(
                fragment.getString(
                    R.string.storage_edit_sftp_server_host_key_unknown_message,
                    exception.host,
                    exception.port,
                    exception.algorithm,
                    exception.presentedSha256Fingerprint
                )
            )
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                fragment.showToast(
                    fragment.getString(R.string.file_list_sftp_host_key_cancelled)
                )
            }
        if (!canTrust) {
            builder.setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val hostKey = SftpPresentedHostKey(
            algorithm = exception.algorithm,
            encodedKeyBase64 = exception.presentedEncodedKeyBase64,
            sha256Fingerprint = exception.presentedSha256Fingerprint
        )
        builder
            .setNeutralButton(R.string.storage_edit_sftp_server_host_key_trust_once) { _, _ ->
                SftpHostKeyTrustStore.appInstance.trustHostKeyOnce(
                    host = exception.host,
                    port = exception.port,
                    presentedHostKey = hostKey
                )
                refresh()
            }
            .setPositiveButton(R.string.storage_edit_sftp_server_host_key_trust_and_save) { _, _ ->
                SftpHostKeyTrustStore.appInstance.trustHostKey(
                    host = exception.host,
                    port = exception.port,
                    presentedHostKey = hostKey
                )
                refresh()
            }
            .show()
    }

    fun showMismatch(exception: SftpHostKeyMismatchException) {
        if (!fragment.isAdded) {
            return
        }
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.storage_edit_sftp_server_host_key_mismatch_title)
            .setMessage(
                fragment.getString(
                    R.string.storage_edit_sftp_server_host_key_mismatch_message,
                    exception.host,
                    exception.port,
                    exception.algorithm,
                    exception.expectedSha256Fingerprint,
                    exception.presentedSha256Fingerprint
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

internal fun Throwable.findSftpHostKeyVerificationException(): SftpHostKeyVerificationException? {
    var current: Throwable? = this
    while (current != null) {
        if (current is SftpHostKeyVerificationException) {
            return current
        }
        current = current.cause
    }
    return null
}
