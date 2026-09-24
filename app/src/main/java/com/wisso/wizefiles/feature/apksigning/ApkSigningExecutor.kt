package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import com.wisso.wizefiles.feature.filejobs.FileOperationService

internal class ApkSigningExecutor {
    fun start(spec: ApkSigningWorkflowSpec, secrets: ApkSigningSecrets, context: Context): Result<String> =
        runCatching { FileOperationService.signApk(spec, secrets, context) }

    fun resume(operationId: String, secrets: ApkSigningSecrets, context: Context): Boolean =
        runCatching { FileOperationService.resumeApkSigning(operationId, secrets, context) }
            .getOrDefault(false)
}
