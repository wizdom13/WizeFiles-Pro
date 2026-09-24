package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toUriString

class XapkSignVerifyActivity : SplitSetSignVerifyActivity(SplitSetPackageKind.XAPK) {
    companion object {
        fun createSignIntent(context: Context, source: AppPath): Intent =
            Intent(context, XapkSignVerifyActivity::class.java)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_MODE, SplitSetSigningMode.SIGN.name)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_SOURCE_URI, source.toUriString())

        fun createVerifyIntent(context: Context, source: AppPath): Intent =
            Intent(context, XapkSignVerifyActivity::class.java)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_MODE, SplitSetSigningMode.VERIFY.name)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_SOURCE_URI, source.toUriString())

        fun createResumeIntent(context: Context, operationId: String): Intent =
            Intent(context, XapkSignVerifyActivity::class.java)
                .putExtra(SplitSetSignVerifyActivity.EXTRA_OPERATION_ID, operationId)
    }
}
