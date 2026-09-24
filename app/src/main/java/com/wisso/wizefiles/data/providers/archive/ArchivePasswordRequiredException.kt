package com.wisso.wizefiles.provider.archive

import android.content.Context
import java.nio.file.Path
import com.wisso.wizefiles.feature.fileactions.ArchivePasswordDialogActivity
import com.wisso.wizefiles.feature.fileactions.ArchivePasswordDialogFragment
import com.wisso.wizefiles.provider.common.UserAction
import com.wisso.wizefiles.provider.common.UserActionRequiredException
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.storage.path.toAppPath
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class ArchivePasswordRequiredException(
    private val file: Path,
    reason: String?
) :
    UserActionRequiredException(file.toString(), null, reason) {

    override fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction {
        return UserAction(
            ArchivePasswordDialogActivity::class.createIntent().putArgs(
                ArchivePasswordDialogFragment.Args(file) { continuation.resume(it) }
            ), ArchivePasswordDialogFragment.getTitle(context),
            ArchivePasswordDialogFragment.getMessage(file.toAppPath(), context)
        )
    }
}
