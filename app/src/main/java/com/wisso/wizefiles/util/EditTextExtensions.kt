package com.wisso.wizefiles.util

import android.widget.EditText

fun EditText.setTextWithSelection(text: CharSequence?) {
    setText(text)
    setSelection(0, this.text.length)
}
