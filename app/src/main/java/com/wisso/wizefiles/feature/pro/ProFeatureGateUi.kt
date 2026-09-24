package com.wisso.wizefiles.feature.pro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.entitlement.ProFeatureAccess

fun Context.ensureProAccess(feature: ProFeature): Boolean {
    if (ProFeatureAccess.isAllowed(feature)) return true
    Toast.makeText(this, R.string.pro_feature_required, Toast.LENGTH_SHORT).show()
    val intent = ProPurchaseActivity.createIntent(this)
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    return false
}

fun Fragment.ensureProAccess(feature: ProFeature): Boolean =
    requireContext().ensureProAccess(feature)
