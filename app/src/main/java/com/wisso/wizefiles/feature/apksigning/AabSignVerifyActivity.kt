// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
import java.net.URI
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AabSignVerifyActivity : PackageSigningActivity() {
    private lateinit var root: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var scrollView: NestedScrollView
    private lateinit var content: LinearLayout
    private lateinit var progress: CircularProgressIndicator
    private lateinit var resultText: TextView
    private lateinit var storePassword: TextInputEditText
    private lateinit var keyPassword: TextInputEditText
    private lateinit var alias: TextInputEditText
    private lateinit var formatMenu: MaterialAutoCompleteTextView
    private lateinit var keySourceMenu: MaterialAutoCompleteTextView
    private lateinit var keepBoth: MaterialCheckBox

    private var mode = Mode.SIGN
    private var source: AppPath? = null
    private var output: AppPath? = null
    private var signingKey: AppPath? = null
    private var certificate: AppPath? = null
    private var keySource = AabSigningKeySource.KEY_STORE
    private var keyStoreFormat = ApkKeyStoreFormat.PKCS12
    private var resumeOperationId: String? = null

    private val sourcePicker = registerForActivityResult(FileListActivity.OpenFileContract()) { path ->
        if (path != null) {
            source = path
            output = null
            render()
        }
    }

    private val outputPicker = registerForActivityResult(FileListActivity.CreateFileContract()) { path ->
        if (path != null) {
            output = path
            render()
        }
    }

    private val signingKeyPicker = registerForActivityResult(
        FileListActivity.OpenFileContract()
    ) { path ->
        if (path != null) {
            signingKey = path
            render()
        }
    }

    private val certificatePicker = registerForActivityResult(
        FileListActivity.OpenFileContract()
    ) { path ->
        if (path != null) {
            certificate = path
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = savedInstanceState?.getString(STATE_MODE)?.let(Mode::valueOf)
            ?: intent.getStringExtra(EXTRA_MODE)?.let(Mode::valueOf)
            ?: Mode.SIGN
        source = restoredPath(savedInstanceState, STATE_SOURCE)
            ?: intent.getStringExtra(EXTRA_SOURCE_URI)?.toAppPathOrNull()
        output = restoredPath(savedInstanceState, STATE_OUTPUT)
        signingKey = restoredPath(savedInstanceState, STATE_SIGNING_KEY)
        certificate = restoredPath(savedInstanceState, STATE_CERTIFICATE)
        keySource = savedInstanceState?.getString(STATE_KEY_SOURCE)
            ?.let(AabSigningKeySource::valueOf) ?: AabSigningKeySource.KEY_STORE
        keyStoreFormat = savedInstanceState?.getString(STATE_KEY_STORE_FORMAT)
            ?.let(ApkKeyStoreFormat::valueOf) ?: ApkKeyStoreFormat.PKCS12
        resumeOperationId = intent.getStringExtra(EXTRA_OPERATION_ID)

        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolbar = MaterialToolbar(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(28))
        }
        scrollView = NestedScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content, matchWrap())
        }
        root.addView(toolbar, matchWrap())
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applySigningSystemBarInsets(root, toolbar, scrollView)
        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_MODE, mode.name)
        outState.putString(STATE_SOURCE, source?.toUriString())
        outState.putString(STATE_OUTPUT, output?.toUriString())
        outState.putString(STATE_SIGNING_KEY, signingKey?.toUriString())
        outState.putString(STATE_CERTIFICATE, certificate?.toUriString())
        outState.putString(STATE_KEY_SOURCE, keySource.name)
        outState.putString(STATE_KEY_STORE_FORMAT, keyStoreFormat.name)
    }

    override fun onDestroy() {
        clearPasswordFields()
        super.onDestroy()
    }

    private fun render() {
        clearPasswordFields()
        content.removeAllViews()
        supportActionBar?.setTitle(
            when {
                resumeOperationId != null -> R.string.aab_signing_resume_title
                mode == Mode.SIGN -> R.string.aab_signing_sign_title
                else -> R.string.aab_signing_verify_title
            }
        )
        if (resumeOperationId != null) {
            renderResume()
            return
        }
        addModeSelector()
        intro(
            if (mode == Mode.SIGN) R.string.aab_signing_sign_heading
            else R.string.aab_signing_verify_heading,
            if (mode == Mode.SIGN) R.string.aab_signing_sign_explanation
            else R.string.aab_signing_verify_explanation
        )
        if (mode == Mode.SIGN) renderSign() else renderVerify()
        addResultViews()
    }

    private fun addModeSelector() {
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val signButton = signingToggleButton(R.string.aab_signing_sign_tab)
        val verifyButton = signingToggleButton(R.string.aab_signing_verify_tab)
        group.addView(signButton, weighted())
        group.addView(verifyButton, weighted())
        group.check(if (mode == Mode.SIGN) signButton.id else verifyButton.id)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) switchMode(if (checkedId == signButton.id) Mode.SIGN else Mode.VERIFY)
        }
        content.addView(group, matchWrapMargins(bottom = 16))
    }

    private fun intro(headingRes: Int, explanationRes: Int) {
        content.addView(signingTextView(getString(headingRes)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleLarge)
        }, matchWrapMargins(bottom = 6))
        text(explanationRes)
    }

    private fun renderSign() {
        sectionHeading(R.string.aab_signing_section_input_output)
        addSigningPathRow(content,
            R.string.aab_signing_input_aab,
            source,
            R.string.aab_signing_choose_aab
        ) { sourcePicker.launch(listOf(MimeType.GENERIC)) }
        addSigningPathRow(content,
            R.string.aab_signing_output_aab,
            output,
            R.string.aab_signing_choose_destination,
            suggestedName = signedAabFileName(source?.name.orEmpty())
        ) {
            outputPicker.launch(
                Triple(
                    MimeType.GENERIC,
                    signedAabFileName(source?.name.orEmpty()),
                    suggestedOutputDirectory(source)
                )
            )
        }

        sectionHeading(R.string.aab_signing_section_upload_key)
        text(R.string.aab_signing_upload_key_explanation)
        addKeySourceMenu()
        if (keySource == AabSigningKeySource.KEY_STORE) {
            addSigningPathRow(content,
                R.string.aab_signing_key_store,
                signingKey,
                R.string.aab_signing_choose_key_store
            ) { signingKeyPicker.launch(listOf(MimeType.GENERIC)) }
            addFormatMenu()
            alias = addSigningEditText(content,R.string.aab_signing_key_alias, saveState = true)
            addPasswordFields(requireStorePassword = true)
            content.addView(
                signingButton(R.string.aab_signing_inspect_aliases, SigningButtonKind.OUTLINED) {
                    inspectAliases()
                },
                matchWrapMargins(top = 8)
            )
        } else {
            addSigningPathRow(content,
                R.string.aab_signing_pkcs8_key,
                signingKey,
                R.string.aab_signing_choose_private_key
            ) { signingKeyPicker.launch(listOf(MimeType.GENERIC)) }
            addSigningPathRow(content,
                R.string.aab_signing_x509_certificate,
                certificate,
                R.string.aab_signing_choose_certificate
            ) { certificatePicker.launch(listOf(MimeType.GENERIC)) }
            alias = detachedEditText()
            addPasswordFields(requireStorePassword = false)
        }

        sectionHeading(R.string.aab_signing_section_review)
        text(R.string.aab_signing_no_apk_schemes)
        keepBoth = signingCheckBox(R.string.aab_signing_keep_both, checked = true)
        content.addView(keepBoth, matchWrap())
        content.addView(
            signingButton(R.string.aab_signing_start, SigningButtonKind.PRIMARY) { startSigning() },
            matchWrapMargins(top = 12)
        )
    }

    private fun renderVerify() {
        sectionHeading(R.string.aab_signing_section_aab_to_verify)
        addSigningPathRow(content,
            R.string.aab_signing_input_aab,
            source,
            R.string.aab_signing_choose_aab
        ) { sourcePicker.launch(listOf(MimeType.GENERIC)) }
        content.addView(
            signingButton(R.string.aab_signing_verify, SigningButtonKind.PRIMARY) { verifyAab() },
            matchWrapMargins(top = 12)
        )
    }

    private fun renderResume() {
        val operationId = requireNotNull(resumeOperationId)
        val spec = AabSigningOperationStore.load(operationId)
        if (spec == null) {
            intro(
                R.string.aab_signing_resume_unavailable,
                R.string.aab_signing_resume_unavailable_explanation
            )
            return
        }
        intro(R.string.aab_signing_reenter_password, R.string.aab_signing_resume_explanation)
        sectionHeading(R.string.aab_signing_section_signing_details)
        addSigningPathValue(content,R.string.aab_signing_input_aab, spec.sourceUri)
        addSigningPathValue(content,R.string.aab_signing_output_aab, spec.outputUri)
        addSigningPathValue(content,
            if (spec.keySource == AabSigningKeySource.KEY_STORE) {
                R.string.aab_signing_key_store
            } else {
                R.string.aab_signing_pkcs8_key
            },
            spec.signingKeyUri
        )
        if (spec.certificateUri.isNotBlank()) {
            addSigningPathValue(content,R.string.aab_signing_x509_certificate, spec.certificateUri)
        }
        if (spec.keySource == AabSigningKeySource.KEY_STORE) {
            value(R.string.aab_signing_key_alias, spec.keyAlias.ifBlank {
                getString(R.string.apk_signing_alias_automatic)
            })
        }
        keySource = spec.keySource
        addPasswordFields(requireStorePassword = spec.keySource == AabSigningKeySource.KEY_STORE)
        content.addView(
            signingButton(R.string.aab_signing_resume_action, SigningButtonKind.PRIMARY) {
                resumeSigning(operationId)
            },
            matchWrapMargins(top = 12)
        )
        addResultViews()
    }

    private fun addResultViews() {
        progress = CircularProgressIndicator(this).apply { isVisible = false }
        content.addView(progress, centeredWrapMargins(top = 12))
        resultText = signingTextView("").apply {
            isVisible = false
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
        }
        content.addView(resultText, matchWrapMargins(top = 12))
    }

    private fun startSigning() {
        val input = source ?: return showSigningMessage(R.string.aab_signing_choose_input)
        val target = output ?: return showSigningMessage(R.string.aab_signing_choose_output)
        val key = signingKey ?: return showSigningMessage(R.string.aab_signing_choose_signing_key)
        val cert = certificate
        if (keySource == AabSigningKeySource.PKCS8_CERTIFICATE && cert == null) {
            return showSigningMessage(R.string.aab_signing_choose_x509_certificate)
        }
        val storeCharacters = characters(storePassword)
        val keyCharacters = characters(keyPassword).takeIf(CharArray::isNotEmpty)
        if (keySource == AabSigningKeySource.KEY_STORE && storeCharacters.isEmpty()) {
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            return showSigningMessage(R.string.apk_signing_password_required)
        }
        val spec = runCatching {
            AabSigningWorkflowSpec(
                sourceUri = input.toUriString(),
                outputUri = target.toUriString(),
                signingKeyUri = key.toUriString(),
                certificateUri = cert?.toUriString().orEmpty(),
                keySource = keySource,
                keyAlias = alias.text?.toString()?.trim().orEmpty(),
                keyStoreFormat = keyStoreFormat,
                conflictPolicy = if (keepBoth.isChecked) {
                    ApkSigningOutputConflictPolicy.KEEP_BOTH
                } else {
                    ApkSigningOutputConflictPolicy.FAIL
                }
            )
        }.getOrElse {
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            return showSigningMessage(R.string.aab_signing_invalid_request)
        }
        ApkSigningSecrets(storeCharacters, keyCharacters).use { secrets ->
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            runCatching { FileOperationService.signAab(spec, secrets, this) }
                .onSuccess {
                    clearPasswordFields()
                    Toast.makeText(this, R.string.aab_signing_started, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, TransferCenterActivity::class.java))
                    finish()
                }
                .onFailure {
                    clearPasswordFields()
                    showSigningMessage(R.string.aab_signing_start_failed)
                }
        }
    }

    private fun resumeSigning(operationId: String) {
        val storeCharacters = characters(storePassword)
        val keyCharacters = characters(keyPassword).takeIf(CharArray::isNotEmpty)
        if (keySource == AabSigningKeySource.KEY_STORE && storeCharacters.isEmpty()) {
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            return showSigningMessage(R.string.apk_signing_password_required)
        }
        ApkSigningSecrets(storeCharacters, keyCharacters).use { secrets ->
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            val resumed = runCatching {
                FileOperationService.resumeAabSigning(operationId, secrets, this)
            }.getOrDefault(false)
            clearPasswordFields()
            if (resumed) {
                Toast.makeText(this, R.string.aab_signing_resumed, Toast.LENGTH_LONG).show()
                finish()
            } else {
                showSigningMessage(R.string.aab_signing_resume_failed)
            }
        }
    }

    private fun inspectAliases() {
        val key = signingKey ?: return showSigningMessage(R.string.aab_signing_choose_signing_key)
        val path = key.toLegacyPathOrNull()
            ?: return showSigningMessage(R.string.apk_signing_path_unavailable)
        val storeCharacters = characters(storePassword)
        val keyCharacters = characters(keyPassword).takeIf(CharArray::isNotEmpty)
        if (storeCharacters.isEmpty()) {
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            return showSigningMessage(R.string.apk_signing_password_required)
        }
        val secrets = ApkSigningSecrets(storeCharacters, keyCharacters)
        storeCharacters.fill('\u0000')
        keyCharacters?.fill('\u0000')
        setBusy(true)
        lifecycleScope.launch {
            val aliases = try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        ProviderApkKeyStoreService().aliases(path, keyStoreFormat, secrets)
                    }
                }
            } finally {
                secrets.close()
            }
            clearPasswordFields()
            setBusy(false)
            aliases.onSuccess(::showAliases).onFailure {
                showSigningMessage(R.string.apk_signing_key_store_open_failed)
            }
        }
    }

    private fun showAliases(items: List<ApkSigningKeyAlias>) {
        if (items.isEmpty()) return showSigningMessage(R.string.apk_signing_no_aliases)
        val labels = items.map {
            getString(
                R.string.apk_signing_alias_summary,
                it.alias,
                it.subject,
                DateFormat.getDateInstance().format(Date(it.notAfterMillis))
            )
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.apk_signing_choose_alias)
            .setItems(labels) { _, index -> alias.setText(items[index].alias) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun verifyAab() {
        val input = source ?: return showSigningMessage(R.string.aab_signing_choose_input)
        val path = input.toLegacyPathOrNull()
            ?: return showSigningMessage(R.string.apk_signing_path_unavailable)
        setBusy(true)
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching { ProviderAabVerifier().verify(path) }
            }
            setBusy(false)
            report.onSuccess(::showVerificationReport).onFailure {
                showSigningMessage(R.string.aab_signing_verification_failed_to_run)
            }
        }
    }

    private fun showVerificationReport(report: AabVerificationReport) {
        resultText.isVisible = true
        resultText.contentDescription = getString(R.string.aab_signing_verification_report)
        resultText.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        resultText.text = buildString {
            appendLine(getString(
                if (report.verified) R.string.apk_signing_verified
                else R.string.apk_signing_not_verified
            ))
            appendLine()
            appendLine(getString(R.string.aab_signing_signed_entries, report.signedEntryCount))
            if (report.signerCertificates.isNotEmpty()) {
                appendLine(getString(R.string.aab_signing_upload_certificates))
                report.signerCertificates.forEach { item ->
                    appendLine("• ${item.sha256}")
                    appendLine("  ${item.subject}")
                    appendLine("  ${getString(R.string.aab_signing_expires, DateFormat.getDateInstance().format(Date(item.notAfterMillis)))}")
                }
            }
            if (report.errors.isNotEmpty()) {
                appendLine(getString(R.string.apk_signing_errors))
                report.errors.forEach { appendLine("• $it") }
            }
            if (report.warnings.isNotEmpty()) {
                appendLine(getString(R.string.apk_signing_warnings))
                report.warnings.forEach { appendLine("• $it") }
            }
        }.trim()
    }

    private fun addKeySourceMenu() {
        val sources = AabSigningKeySource.entries
        val labels = sources.map {
            getString(if (it == AabSigningKeySource.KEY_STORE) {
                R.string.aab_signing_key_source_store
            } else {
                R.string.aab_signing_key_source_pkcs8
            })
        }
        val layout = TextInputLayout(
            this,
            null,
            MaterialR.attr.textInputOutlinedExposedDropdownMenuStyle
        ).apply { hint = getString(R.string.aab_signing_key_source) }
        keySourceMenu = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(this@AabSignVerifyActivity, android.R.layout.simple_list_item_1, labels))
            setText(labels[keySource.ordinal], false)
            setOnItemClickListener { _, _, position, _ ->
                val selected = sources[position]
                if (selected != keySource) {
                    keySource = selected
                    signingKey = null
                    certificate = null
                    render()
                }
            }
            setOnClickListener { showDropDown() }
        }
        layout.addView(keySourceMenu, textInputChildLayoutParams())
        content.addView(layout, matchWrapMargins(top = 8))
    }

    private fun addFormatMenu() {
        val formats = ApkKeyStoreFormat.entries
        val labels = formats.map(::keyStoreFormatDisplayName)
        val layout = TextInputLayout(
            this,
            null,
            MaterialR.attr.textInputOutlinedExposedDropdownMenuStyle
        ).apply { hint = getString(R.string.apk_signing_key_store_format) }
        formatMenu = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(this@AabSignVerifyActivity, android.R.layout.simple_list_item_1, labels))
            setText(keyStoreFormatDisplayName(keyStoreFormat), false)
            setOnItemClickListener { _, _, position, _ -> keyStoreFormat = formats[position] }
            setOnClickListener { showDropDown() }
        }
        layout.addView(formatMenu, textInputChildLayoutParams())
        content.addView(layout, matchWrapMargins(top = 8))
    }

    private fun addPasswordFields(requireStorePassword: Boolean) {
        storePassword = addSigningEditText(content,
            if (requireStorePassword) R.string.apk_signing_store_password
            else R.string.aab_signing_private_key_password_optional,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            saveState = false,
            password = true
        )
        keyPassword = if (requireStorePassword) {
            addSigningEditText(content,
                R.string.apk_signing_key_password_optional,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                saveState = false,
                password = true
            )
        } else {
            detachedEditText()
        }
        text(R.string.apk_signing_password_security)
    }

    private fun value(labelRes: Int, value: String) {
        content.addView(signingTextView(getString(labelRes)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
        }, matchWrapMargins(top = 12))
        content.addView(signingTextView(value).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
        }, matchWrapMargins(top = 2, bottom = 4))
    }

    private fun sectionHeading(textRes: Int) {
        content.addView(signingTextView(getString(textRes)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleMedium)
        }, matchWrapMargins(top = 24, bottom = 4))
    }

    private fun text(textRes: Int): TextView = signingTextView(getString(textRes)).apply {
        setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
        content.addView(this, matchWrapMargins(bottom = 4))
    }

    private fun clearPasswordFields() {
        if (::storePassword.isInitialized) storePassword.text?.clear()
        if (::keyPassword.isInitialized) keyPassword.text?.clear()
    }

    private fun setBusy(busy: Boolean) {
        progress.isVisible = busy
        setEnabledRecursively(content, !busy)
    }

    private fun switchMode(target: Mode) {
        if (mode == target) return
        mode = target
        output = null
        signingKey = null
        certificate = null
        render()
    }

    private fun signedAabFileName(name: String): String {
        val decoded = friendlyDecode(name).ifBlank { "app.aab" }
        val base = decoded.removeSuffix(".aab").removeSuffix(".AAB")
        return "$base-signed.aab"
    }

    private enum class Mode { SIGN, VERIFY }

    companion object {
        private const val EXTRA_MODE = "aab_signing.mode"
        private const val EXTRA_SOURCE_URI = "aab_signing.source_uri"
        private const val EXTRA_OPERATION_ID = "aab_signing.operation_id"
        private const val STATE_MODE = "aab_signing.state.mode"
        private const val STATE_SOURCE = "aab_signing.state.source"
        private const val STATE_OUTPUT = "aab_signing.state.output"
        private const val STATE_SIGNING_KEY = "aab_signing.state.signing_key"
        private const val STATE_CERTIFICATE = "aab_signing.state.certificate"
        private const val STATE_KEY_SOURCE = "aab_signing.state.key_source"
        private const val STATE_KEY_STORE_FORMAT = "aab_signing.state.key_store_format"

        fun createSignIntent(context: Context, source: AppPath): Intent =
            Intent(context, AabSignVerifyActivity::class.java)
                .putExtra(EXTRA_MODE, Mode.SIGN.name)
                .putExtra(EXTRA_SOURCE_URI, source.toUriString())

        fun createVerifyIntent(context: Context, source: AppPath): Intent =
            Intent(context, AabSignVerifyActivity::class.java)
                .putExtra(EXTRA_MODE, Mode.VERIFY.name)
                .putExtra(EXTRA_SOURCE_URI, source.toUriString())

        fun createResumeIntent(context: Context, operationId: String): Intent =
            Intent(context, AabSignVerifyActivity::class.java)
                .putExtra(EXTRA_OPERATION_ID, operationId)
    }
}
