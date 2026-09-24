package com.wisso.wizefiles.settings

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.valueCompat

class DefaultDirectoryPreference : PathPreference {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override var persistedPath: AppPath
        get() = Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat.toAppPath()
        set(value) {
            Settings.FILE_LIST_DEFAULT_DIRECTORY.putValue(checkNotNull(value.toLegacyPathOrNull()))
        }
}
