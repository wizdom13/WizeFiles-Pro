package com.wisso.wizefiles.settings

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.takisoft.preferencex.EditTextPreference

class DefaultIfEmptyEditTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {
    private var fallbackText: String? = null

    override fun onGetDefaultValue(array: TypedArray, index: Int): Any? {
        val value = super.onGetDefaultValue(array, index)
        fallbackText = value as? String
        return value
    }

    override fun setDefaultValue(defaultValue: Any?) {
        fallbackText = defaultValue as? String
        super.setDefaultValue(defaultValue)
    }

    override fun setText(text: String?) {
        super.setText(text?.takeUnless(String::isEmpty) ?: fallbackText)
    }
}
