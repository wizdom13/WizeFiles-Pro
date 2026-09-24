package com.wisso.wizefiles.settings

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.takisoft.preferencex.EditTextPreference
import androidx.preference.EditTextPreference as AndroidXEditTextPreference

class PasswordPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {
    init {
        if (summaryProvider is AndroidXEditTextPreference.SimpleSummaryProvider) {
            summaryProvider = SimpleSummaryProvider
        }
    }

    object SimpleSummaryProvider : SummaryProvider<EditTextPreference> {
        override fun provideSummary(preference: EditTextPreference): CharSequence? {
            val password = preference.text
            if (password.isNullOrEmpty()) {
                return AndroidXEditTextPreference.SimpleSummaryProvider
                    .getInstance()
                    .provideSummary(preference)
            }
            return CharArray(password.length) { PASSWORD_GLYPH }.concatToString()
        }

        private const val PASSWORD_GLYPH = '\u2022'
    }
}
