// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.transfer.TransferCenterActivity
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.storage.path.toUriString

class ApkmImportActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var keepBoth: MaterialCheckBox
    private var source: AppPath? = null
    private var output: AppPath? = null

    private val sourcePicker = registerForActivityResult(FileListActivity.OpenFileContract()) { path ->
        path?.let {
            source = it
            output = null
            render()
        }
    }
    private val outputPicker = registerForActivityResult(FileListActivity.CreateFileContract()) {
        it?.let { path -> output = path; render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = savedInstanceState?.getString(STATE_SOURCE)?.toAppPathOrNull()
            ?: intent.getStringExtra(EXTRA_SOURCE_URI)?.toAppPathOrNull()
        output = savedInstanceState?.getString(STATE_OUTPUT)?.toAppPathOrNull()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(28))
        }
        val scroll = NestedScrollView(this).apply {
            isFillViewport = true
            addView(content, matchWrap())
        }
        root.addView(toolbar, matchWrap())
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(root)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.apkm_import_title)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, right = bars.right)
            scroll.updatePadding(bottom = maxOf(
                bars.bottom,
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            ))
            insets
        }
        ViewCompat.requestApplyInsets(root)
        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SOURCE, source?.toUriString())
        outState.putString(STATE_OUTPUT, output?.toUriString())
    }

    private fun render() {
        content.removeAllViews()
        content.addView(textView(getString(R.string.apkm_import_heading)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleLarge)
        }, margins(bottom = 6))
        content.addView(textView(getString(R.string.apkm_import_explanation)), margins(bottom = 8))
        pathRow(R.string.apkm_import_input, source, R.string.apkm_import_choose_input) {
            sourcePicker.launch(listOf(MimeType.GENERIC))
        }
        pathRow(
            R.string.apkm_import_output,
            output,
            R.string.apk_signing_choose_destination,
            importedName(source?.name.orEmpty())
        ) {
            outputPicker.launch(Triple(
                MimeType.GENERIC,
                importedName(source?.name.orEmpty()),
                suggestedOutputDirectory()
            ))
        }
        val assurance = MaterialCardView(this)
        val assuranceBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
            addView(textView(getString(R.string.apkm_import_assurance_heading)).apply {
                setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleMedium)
            }, matchWrap())
            addView(textView(getString(R.string.apkm_import_assurance)), margins(top = 6))
        }
        assurance.addView(assuranceBody, matchWrap())
        content.addView(assurance, margins(top = 16))
        keepBoth = MaterialCheckBox(this).apply {
            setText(R.string.apk_signing_keep_both)
            isChecked = true
        }
        content.addView(keepBoth, margins(top = 12))
        content.addView(MaterialButton(this).apply {
            setText(R.string.apkm_import_start)
            isAllCaps = false
            setOnClickListener { startImport() }
        }, margins(top = 12))
    }

    private fun startImport() {
        val input = source ?: return message(R.string.apkm_import_choose_input_first)
        val target = output ?: return message(R.string.apkm_import_choose_output_first)
        val spec = runCatching {
            ApkmImportWorkflowSpec(
                sourceUri = input.toUriString(),
                outputUri = target.toUriString(),
                conflictPolicy = if (keepBoth.isChecked) {
                    ApkSigningOutputConflictPolicy.KEEP_BOTH
                } else {
                    ApkSigningOutputConflictPolicy.FAIL
                }
            )
        }.getOrElse { return message(R.string.apkm_import_invalid_request) }
        runCatching { FileOperationService.importApkm(spec, this) }
            .onSuccess {
                Toast.makeText(this, R.string.apkm_import_started, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, TransferCenterActivity::class.java))
                finish()
            }
            .onFailure { message(R.string.apkm_import_start_failed) }
    }

    private fun pathRow(
        label: Int,
        path: AppPath?,
        action: Int,
        suggested: String? = null,
        choose: () -> Unit
    ) {
        val card = MaterialCardView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        body.addView(textView(getString(label)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
        }, matchWrap())
        body.addView(textView(path?.name ?: suggested ?: getString(
            R.string.apk_signing_not_selected
        )), margins(top = 4))
        body.addView(MaterialButton(
            this,
            null,
            MaterialR.attr.materialButtonOutlinedStyle
        ).apply {
            setText(action)
            isAllCaps = false
            setOnClickListener { choose() }
        }, margins(top = 10))
        card.addView(body, matchWrap())
        content.addView(card, margins(top = 8, bottom = 4))
    }

    private fun textView(value: CharSequence) = TextView(this).apply {
        text = value
        setTextIsSelectable(true)
    }

    private fun importedName(name: String): String {
        val base = name.ifBlank { "packages.apkm" }.removeSuffix(".apkm").removeSuffix(".APKM")
        return "$base-imported.apks"
    }

    private fun suggestedOutputDirectory(): AppPath? = source?.let { path ->
        runCatching { path.toLegacyPathOrNull()?.parent?.toAppPath() }.getOrNull()
    }

    private fun message(resource: Int) {
        Toast.makeText(this, resource, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    private fun margins(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    companion object {
        private const val EXTRA_SOURCE_URI = "apkm_import.source_uri"
        private const val STATE_SOURCE = "apkm_import.state.source"
        private const val STATE_OUTPUT = "apkm_import.state.output"

        fun createIntent(context: Context, source: AppPath): Intent =
            Intent(context, ApkmImportActivity::class.java)
                .putExtra(EXTRA_SOURCE_URI, source.toUriString())
    }
}
