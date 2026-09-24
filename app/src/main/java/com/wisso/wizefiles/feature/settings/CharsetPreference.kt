package com.wisso.wizefiles.settings

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.preference.ListPreference
import java.nio.charset.Charset

class CharsetPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ListPreference(context, attrs, defStyleAttr, defStyleRes) {
    init {
        val available = Charset.availableCharsets()
        val labels = ArrayList<CharSequence>(available.size)
        val names = ArrayList<CharSequence>(available.size)
        available.forEach { (canonicalName, charset) ->
            names += canonicalName
            labels += charset.displayName()
        }
        entries = labels.toTypedArray()
        entryValues = names.toTypedArray()
    }
}
