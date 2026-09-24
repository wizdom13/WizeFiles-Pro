package com.wisso.wizefiles.settings

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.preference.ListPreference
import com.wisso.wizefiles.ui.FileIconShape

class IconShapePreference : ListPreference {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onSetInitialValue(defaultValue: Any?) {
        val defaultStableId = (defaultValue as? String)
            ?.takeIf { it in FileIconShape.stableIds }
            ?: FileIconShape.DEFAULT.stableId
        val persistedStableId = getPersistedString(defaultStableId)
        value = FileIconShape.fromStableId(persistedStableId).stableId
    }
}
