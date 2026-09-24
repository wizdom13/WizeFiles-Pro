package com.wisso.wizefiles.beta

import android.os.Bundle
import android.text.format.DateFormat
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import java.util.Date

class BetaExpiredActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BetaExpiry.isExpired()) {
            finish()
            return
        }

        setContentView(FrameLayout(this))
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            }
        )

        val expiryDate = DateFormat.getMediumDateFormat(this)
            .format(Date(BetaExpiry.expiresAtEpochMillis))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.beta_expired_title)
            .setMessage(getString(R.string.beta_expired_message, expiryDate))
            .setPositiveButton(R.string.beta_expired_close) { _, _ ->
                finishAndRemoveTask()
            }
            .setCancelable(false)
            .show()
    }
}
