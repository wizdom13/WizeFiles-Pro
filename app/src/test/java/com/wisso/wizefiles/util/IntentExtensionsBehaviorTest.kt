package com.wisso.wizefiles.util

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentExtensionsBehaviorTest {

    @Test
    fun editIntentPermissionFlags_includeReadAndWrite() {
        assertTrue(EDIT_INTENT_PERMISSION_FLAGS and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(EDIT_INTENT_PERMISSION_FLAGS and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }
}
