// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path

internal class VaultImportController(
    private val activity:AppCompatActivity,
    private val scope:CoroutineScope,
    private val operations:VaultActivityOperations,
    private val setImporting:(Boolean)->Unit,
    private val refresh:()->Unit
) {
    fun importPaths(parentId:String?,paths:List<Path>) {
        if(paths.isEmpty()) return
        scope.launch {
            setImporting(true)
            try {
                val result=operations.importPaths(parentId,paths)
                refresh()
                when {
                    result.importedSources.isEmpty() -> Toast.makeText(
                        activity,
                        R.string.vault_import_failed,
                        Toast.LENGTH_LONG
                    ).show()
                    result.failedSources.isNotEmpty() -> {
                        Toast.makeText(
                            activity,
                            activity.getString(
                                R.string.vault_import_partial,
                                result.importedSources.size,
                                paths.size
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                        showDeleteOriginalsPrompt(result.importedSources)
                    }
                    else -> {
                        Toast.makeText(
                            activity,
                            activity.getString(
                                R.string.vault_import_success_count,
                                result.importedSources.size
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                        showDeleteOriginalsPrompt(result.importedSources)
                    }
                }
            } finally {
                setImporting(false)
            }
        }
    }

    private fun showDeleteOriginalsPrompt(importedSources:List<Path>) {
        if(importedSources.isEmpty()) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.vault_delete_originals_title)
            .setMessage(R.string.vault_delete_originals_message)
            .setNegativeButton(R.string.vault_keep_originals,null)
            .setPositiveButton(R.string.vault_delete_originals_confirm) { _,_ ->
                deleteOriginals(importedSources)
            }
            .show()
    }

    private fun deleteOriginals(importedSources:List<Path>) {
        scope.launch {
            val result=operations.deleteOriginals(importedSources)
            if(result.failedPaths.isEmpty()) {
                Toast.makeText(
                    activity,
                    R.string.vault_delete_originals_success,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.vault_delete_originals_partial,
                        result.deletedCount,
                        result.totalCount
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
