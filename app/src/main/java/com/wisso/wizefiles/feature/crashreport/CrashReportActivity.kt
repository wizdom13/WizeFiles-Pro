package com.wisso.wizefiles.feature.crashreport

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.databinding.ActivityCrashReportBinding
import com.wisso.wizefiles.util.AppLog

class CrashReportActivity : BaseThemedActivity() {
    private lateinit var binding: ActivityCrashReportBinding
    private lateinit var baseReport: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseReport = CrashReportStore.read(this) ?: run {
            finish()
            return
        }

        binding = ActivityCrashReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        CrashReportNotification.cancel(this)

        binding.reportPreview.text = baseReport
        binding.copyButton.setOnClickListener { copyReport() }
        binding.shareButton.setOnClickListener { shareReport() }
        binding.emailButton.setOnClickListener { emailReport() }
        binding.discardButton.setOnClickListener { markReportHandled() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun buildUserApprovedReport(): String {
        val comment = CrashReportSanitizer.sanitize(
            binding.userComment.text?.toString().orEmpty()
        )
        val diagnosticLog = if (binding.includeDiagnosticLog.isChecked) {
            CrashReportSanitizer.sanitize(
                AppLog.readRecentForCrashReport(MAX_DIAGNOSTIC_LOG_CHARS)
            )
        } else {
            ""
        }
        return CrashReportFormatter.appendUserDetails(baseReport, comment, diagnosticLog)
    }

    private fun copyReport() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.crash_report_title), buildUserApprovedReport())
        )
        Toast.makeText(this, R.string.crash_report_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, buildUserApprovedReport())
        try {
            startActivity(
                Intent.createChooser(intent, getString(R.string.crash_report_share_chooser))
            )
            markReportHandled()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun emailReport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_email_subject))
            putExtra(Intent.EXTRA_TEXT, buildUserApprovedReport())
        }
        try {
            startActivity(intent)
            markReportHandled()
        } catch (_: ActivityNotFoundException) {
            shareReport()
        }
    }

    private fun markReportHandled() {
        CrashReportStore.delete(this)
        CrashReportNotification.cancel(this)
        finish()
    }

    companion object {
        private const val SUPPORT_EMAIL = "support@wizesoft.me"
        private const val MAX_DIAGNOSTIC_LOG_CHARS = 128 * 1024

        fun createIntent(context: Context): Intent =
            Intent(context, CrashReportActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
