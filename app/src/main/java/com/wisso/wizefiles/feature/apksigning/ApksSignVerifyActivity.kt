// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toUriString

class ApksSignVerifyActivity : SplitSetSignVerifyActivity(SplitSetPackageKind.APKS) {
    companion object {
        fun createSignIntent(context: Context, source: AppPath): Intent =
            Intent(context, ApksSignVerifyActivity::class.java)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_MODE, SplitSetSigningMode.SIGN.name)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_SOURCE_URI, source.toUriString())

        fun createVerifyIntent(context: Context, source: AppPath): Intent =
            Intent(context, ApksSignVerifyActivity::class.java)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_MODE, SplitSetSigningMode.VERIFY.name)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_SOURCE_URI, source.toUriString())

        fun createResumeIntent(context: Context, operationId: String): Intent =
            Intent(context, ApksSignVerifyActivity::class.java)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_OPERATION_ID, operationId)
    }
}
