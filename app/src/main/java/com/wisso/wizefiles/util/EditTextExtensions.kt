// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.widget.EditText

fun EditText.setTextWithSelection(text: CharSequence?) {
    setText(text)
    setSelection(0, this.text.length)
}
