package com.wisso.wizefiles.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

class AutoGoneTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}
