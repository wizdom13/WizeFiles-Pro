// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.content.res.TypedArrayUtils
import androidx.core.content.res.use
import androidx.preference.Preference
import com.takisoft.preferencex.PreferenceActivityResultListener
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.wisso.wizefiles.feature.filebrowser.toUserFriendlyString
import com.wisso.wizefiles.navigation.NavigationRootMapLiveData
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.ui.PreferenceFragmentCompat as WizePreferenceFragmentCompat
import com.wisso.wizefiles.util.valueCompat

abstract class PathPreference : Preference, PreferenceActivityResultListener {
    var path: AppPath = persistedPath
        set(value) {
            if (field == value) {
                return
            }
            field = value
            persistedPath = value
            notifyChanged()
        }

    constructor(context: Context) : super(context) {
        init(null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
        init(attrs, defStyleAttr, 0)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs, defStyleAttr, defStyleRes)
    }

    @SuppressLint("PrivateResource", "RestrictedApi")
    private fun init(attrs: AttributeSet?, @AttrRes defStyleAttr: Int, @StyleRes defStyleRes: Int) {
        isPersistent = false
        context.obtainStyledAttributes(
            attrs, androidx.preference.R.styleable.EditTextPreference, defStyleAttr, defStyleRes
        ).use {
            if (TypedArrayUtils.getBoolean(
                it, androidx.preference.R.styleable.EditTextPreference_useSimpleSummaryProvider,
                androidx.preference.R.styleable.EditTextPreference_useSimpleSummaryProvider, false
            )) {
                summaryProvider = SimpleSummaryProvider
            }
        }
    }

    override fun onPreferenceClick(fragment: PreferenceFragmentCompat, preference: Preference) {
        check(fragment is WizePreferenceFragmentCompat) {
            "PathPreference requires ${WizePreferenceFragmentCompat::class.java.simpleName}"
        }
        fragment.launchOpenDirectory(path) { result ->
            if (result != null) {
                this.path = result
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) = Unit

    protected abstract var persistedPath: AppPath

    object SimpleSummaryProvider : SummaryProvider<PathPreference> {
        override fun provideSummary(preference: PathPreference): CharSequence? {
            val path = preference.path
            val legacyPath = path.toLegacyPathOrNull()
            if (legacyPath != null) {
                val navigationRoot = NavigationRootMapLiveData.valueCompat[legacyPath]
                return navigationRoot?.getName(preference.context) ?: legacyPath.toUserFriendlyString()
            }
            return path.rawPath
        }
    }
}
