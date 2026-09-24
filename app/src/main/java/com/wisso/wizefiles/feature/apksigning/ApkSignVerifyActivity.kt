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
import androidx.activity.viewModels
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

class ApkSignVerifyActivity : PackageSigningActivity() {
    private lateinit var root: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var scrollView: NestedScrollView
    private lateinit var content: LinearLayout
    private lateinit var progress: CircularProgressIndicator
    private lateinit var resultText: TextView
    private lateinit var storePassword: TextInputEditText
    private lateinit var keyPassword: TextInputEditText
    private lateinit var alias: TextInputEditText
    private lateinit var subject: TextInputEditText
    private lateinit var minimumSdk: TextInputEditText
    private lateinit var formatMenu: MaterialAutoCompleteTextView
    private lateinit var v1: MaterialCheckBox
    private lateinit var v2: MaterialCheckBox
    private lateinit var v3: MaterialCheckBox
    private lateinit var v4: MaterialCheckBox
    private lateinit var keepBoth: MaterialCheckBox

    private val state by viewModels<ApkSignVerifyViewModel>()
    private val keyCoordinator = SigningKeyCoordinator()
    private val signingExecutor = ApkSigningExecutor()
    private val verificationExecutor = ApkVerificationExecutor()
    private var mode: ApkSignVerifyMode
        get() = state.mode
        set(value) { state.mode = value }
    private var source: AppPath?
        get() = state.source
        set(value) { state.source = value }
    private var output: AppPath?
        get() = state.output
        set(value) { state.output = value }
    private var keyStore: AppPath?
        get() = state.keyStore
        set(value) { state.keyStore = value }
    private var detachedV4: AppPath?
        get() = state.detachedV4
        set(value) { state.detachedV4 = value }
    private var keyStoreFormat: ApkKeyStoreFormat
        get() = state.keyStoreFormat
        set(value) { state.keyStoreFormat = value }
    private var pendingGeneration: Boolean
        get() = state.pendingGeneration
        set(value) { state.pendingGeneration = value }
    private var resumeOperationId: String?
        get() = state.resumeOperationId
        set(value) { state.resumeOperationId = value }

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

    private val keyStorePicker = registerForActivityResult(FileListActivity.OpenFileContract()) { path ->
        if (path != null) {
            keyStore = path
            pendingGeneration = false
            render()
        }
    }

    private val generatedKeyStorePicker = registerForActivityResult(
        FileListActivity.CreateFileContract()
    ) { path ->
        if (path != null) {
            keyStore = path
            keyStoreFormat = ApkKeyStoreFormat.PKCS12
            pendingGeneration = true
            render()
        }
    }

    private val detachedV4Picker = registerForActivityResult(
        FileListActivity.OpenFileContract()
    ) { path ->
        if (path != null) {
            detachedV4 = path
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!state.initialized) {
            mode = savedInstanceState?.getString(STATE_MODE)?.let(ApkSignVerifyMode::valueOf)
                ?: intent.getStringExtra(EXTRA_MODE)?.let(ApkSignVerifyMode::valueOf)
                ?: ApkSignVerifyMode.SIGN
            source = restoredPath(savedInstanceState, STATE_SOURCE)
                ?: intent.getStringExtra(EXTRA_SOURCE_URI)?.toAppPathOrNull()
            output = restoredPath(savedInstanceState, STATE_OUTPUT)
            keyStore = restoredPath(savedInstanceState, STATE_KEY_STORE)
            detachedV4 = restoredPath(savedInstanceState, STATE_DETACHED_V4)
            keyStoreFormat = savedInstanceState?.getString(STATE_KEY_STORE_FORMAT)
                ?.let(ApkKeyStoreFormat::valueOf) ?: ApkKeyStoreFormat.PKCS12
            pendingGeneration = savedInstanceState?.getBoolean(STATE_PENDING_GENERATION) == true
            resumeOperationId = intent.getStringExtra(EXTRA_OPERATION_ID)
            state.initialized = true
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
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
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
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
        outState.putString(STATE_KEY_STORE, keyStore?.toUriString())
        outState.putString(STATE_DETACHED_V4, detachedV4?.toUriString())
        outState.putString(STATE_KEY_STORE_FORMAT, keyStoreFormat.name)
        outState.putBoolean(STATE_PENDING_GENERATION, pendingGeneration)
    }

    override fun onDestroy() {
        clearPasswordFields()
        super.onDestroy()
    }

    private fun render() {
        clearPasswordFields()
        content.removeAllViews()
        val titleRes = when {
            resumeOperationId != null -> R.string.apk_signing_resume_title
            mode == ApkSignVerifyMode.SIGN -> R.string.apk_signing_sign_title
            else -> R.string.apk_signing_verify_title
        }
        supportActionBar?.setTitle(titleRes)
        if (resumeOperationId != null) {
            renderResume()
            return
        }

        addModeSelector()
        intro(
            if (mode == ApkSignVerifyMode.SIGN) R.string.apk_signing_sign_heading
            else R.string.apk_signing_verify_heading,
            if (mode == ApkSignVerifyMode.SIGN) R.string.apk_signing_sign_explanation
            else R.string.apk_signing_verify_explanation
        )
        if (mode == ApkSignVerifyMode.SIGN) renderSign() else renderVerify()
        progress = CircularProgressIndicator(this).apply { isVisible = false }
        content.addView(progress, centeredWrapMargins(top = 12))
        resultText = signingTextView("").apply {
            isVisible = false
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
        }
        content.addView(resultText, matchWrapMargins(top = 12))
    }

    private fun addModeSelector() {
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val signButton = signingToggleButton(R.string.apk_signing_sign_tab)
        val verifyButton = signingToggleButton(R.string.apk_signing_verify_tab)
        group.addView(signButton, weighted())
        group.addView(verifyButton, weighted())
        group.check(if (mode == ApkSignVerifyMode.SIGN) signButton.id else verifyButton.id)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            switchMode(if (checkedId == signButton.id) ApkSignVerifyMode.SIGN else ApkSignVerifyMode.VERIFY)
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
        sectionHeading(R.string.apk_signing_section_input_output)
        addSigningPathRow(content,
            R.string.apk_signing_input_apk,
            source,
            R.string.apk_signing_choose_apk
        ) {
            sourcePicker.launch(listOf(MimeType.APK, MimeType.GENERIC))
        }
        addSigningPathRow(content,
            R.string.apk_signing_output_apk,
            output,
            R.string.apk_signing_choose_destination,
            suggestedName = signedApkFileName(source?.name.orEmpty())
        ) {
            outputPicker.launch(
                Triple(
                    MimeType.APK,
                    signedApkFileName(source?.name.orEmpty()),
                    suggestedOutputDirectory(source)
                )
            )
        }

        sectionHeading(R.string.apk_signing_section_signing_key)
        addSigningPathRow(content,
            R.string.apk_signing_key_store,
            keyStore,
            R.string.apk_signing_choose_key_store_action
        ) {
            keyStorePicker.launch(listOf(MimeType.GENERIC))
        }
        content.addView(
            signingButton(
                R.string.apk_signing_create_key_store_target,
                SigningButtonKind.OUTLINED
            ) {
                generatedKeyStorePicker.launch(
                    Triple(MimeType.GENERIC, "wizefiles-signing-key.p12", suggestedOutputDirectory(source))
                )
            },
            matchWrapMargins(top = 8)
        )

        addFormatMenu()
        alias = addSigningEditText(content,R.string.apk_signing_key_alias, saveState = true)
        if (pendingGeneration) {
            text(R.string.apk_signing_new_key_store_explanation)
            subject = addSigningEditText(content,
                R.string.apk_signing_certificate_subject,
                saveState = true
            ).apply {
                setText("CN=WizeFiles Signing Key")
            }
        } else {
            subject = detachedEditText()
        }
        addPasswordFields()
        content.addView(
            signingButton(
                if (pendingGeneration) {
                    R.string.apk_signing_generate_key_store
                } else {
                    R.string.apk_signing_inspect_aliases
                },
                SigningButtonKind.OUTLINED
            ) {
                if (pendingGeneration) generateKeyStore() else inspectAliases()
            },
            matchWrapMargins(top = 8)
        )

        sectionHeading(R.string.apk_signing_signature_schemes)
        v1 = signingCheckBox(R.string.apk_signing_scheme_v1, checked = true)
        v2 = signingCheckBox(R.string.apk_signing_scheme_v2, checked = true)
        v3 = signingCheckBox(R.string.apk_signing_scheme_v3, checked = true)
        v4 = signingCheckBox(R.string.apk_signing_scheme_v4, checked = false)
        listOf(v1, v2, v3, v4).forEach { content.addView(it, matchWrap()) }
        text(R.string.apk_signing_v4_explanation)
        minimumSdk = addSigningEditText(content,
            R.string.apk_signing_minimum_sdk,
            inputType = InputType.TYPE_CLASS_NUMBER,
            saveState = true
        )

        sectionHeading(R.string.apk_signing_section_review_sign)
        text(R.string.apk_signing_review_explanation)
        keepBoth = signingCheckBox(R.string.apk_signing_keep_both, checked = true)
        content.addView(keepBoth, matchWrap())
        content.addView(
            signingButton(R.string.apk_signing_start, SigningButtonKind.PRIMARY) { startSigning() },
            matchWrapMargins(top = 12)
        )
    }

    private fun renderVerify() {
        sectionHeading(R.string.apk_signing_section_apk_to_verify)
        addSigningPathRow(content,
            R.string.apk_signing_input_apk,
            source,
            R.string.apk_signing_choose_apk
        ) {
            sourcePicker.launch(listOf(MimeType.APK, MimeType.GENERIC))
        }

        sectionHeading(R.string.apk_signing_section_verification_options)
        addSigningPathRow(content,
            R.string.apk_signing_detached_v4,
            detachedV4,
            R.string.apk_signing_choose_idsig,
            optional = true
        ) {
            detachedV4Picker.launch(listOf(MimeType.GENERIC))
        }
        minimumSdk = addSigningEditText(content,
            R.string.apk_signing_minimum_sdk,
            inputType = InputType.TYPE_CLASS_NUMBER,
            saveState = true
        )
        content.addView(
            signingButton(R.string.apk_signing_verify, SigningButtonKind.PRIMARY) { verifyApk() },
            matchWrapMargins(top = 12)
        )
    }

    private fun renderResume() {
        val operationId = requireNotNull(resumeOperationId)
        val spec = ApkSigningOperationStore.load(operationId)
        if (spec == null) {
            intro(
                R.string.apk_signing_resume_unavailable,
                R.string.apk_signing_resume_unavailable_explanation
            )
            return
        }
        intro(R.string.apk_signing_reenter_password, R.string.apk_signing_resume_explanation)
        sectionHeading(R.string.apk_signing_section_signing_details)
        addSigningPathValue(content,R.string.apk_signing_input_apk, spec.sourceUri)
        addSigningPathValue(content,R.string.apk_signing_output_apk, spec.outputUri)
        addSigningPathValue(content,R.string.apk_signing_key_store, spec.keyStoreUri)
        value(R.string.apk_signing_key_alias, spec.keyAlias.ifBlank {
            getString(R.string.apk_signing_alias_automatic)
        })
        addPasswordFields()
        content.addView(
            signingButton(R.string.apk_signing_resume_action, SigningButtonKind.PRIMARY) {
                resumeSigning(operationId)
            },
            matchWrapMargins(top = 12)
        )
        progress = CircularProgressIndicator(this).apply { isVisible = false }
        content.addView(progress, centeredWrapMargins(top = 12))
        resultText = signingTextView("").apply { isVisible = false }
        content.addView(resultText, matchWrapMargins(top = 12))
    }

    private fun startSigning() {
        val input = source ?: return showSigningMessage(R.string.apk_signing_choose_input)
        val target = output ?: return showSigningMessage(R.string.apk_signing_choose_output)
        val store = keyStore ?: return showSigningMessage(R.string.apk_signing_choose_key_store)
        val storeCharacters = characters(storePassword)
        val keyCharacters = characters(keyPassword).takeIf(CharArray::isNotEmpty)
        if (storeCharacters.isEmpty()) {
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            return showSigningMessage(R.string.apk_signing_password_required)
        }
        val schemes = buildSet {
            if (v1.isChecked) add(ApkSignatureScheme.V1)
            if (v2.isChecked) add(ApkSignatureScheme.V2)
            if (v3.isChecked) add(ApkSignatureScheme.V3)
            if (v4.isChecked) add(ApkSignatureScheme.V4)
        }
        val validation = SigningInputValidator.validate(schemes, minimumSdk.text.toString())
        if (validation is SigningInputValidation.Invalid) {
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            return showSigningMessage(validation.error.messageResource())
        }
        val spec = runCatching {
            ApkSigningWorkflowSpec(
                sourceUri = input.toUriString(),
                outputUri = target.toUriString(),
                keyStoreUri = store.toUriString(),
                keyAlias = alias.text.toString().trim(),
                keyStoreFormat = keyStoreFormat,
                schemes = schemes,
                minSdkVersion = (validation as SigningInputValidation.Valid).minSdk,
                conflictPolicy = if (keepBoth.isChecked) {
                    ApkSigningOutputConflictPolicy.KEEP_BOTH
                } else {
                    ApkSigningOutputConflictPolicy.FAIL
                }
            )
        }.getOrElse {
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            return showSigningMessage(R.string.apk_signing_invalid_request)
        }
        ApkSigningSecrets(storeCharacters, keyCharacters).use { secrets ->
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            signingExecutor.start(spec, secrets, this)
                .onSuccess {
                    clearPasswordFields()
                    Toast.makeText(this, R.string.apk_signing_started, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, TransferCenterActivity::class.java))
                    finish()
                }
                .onFailure {
                    clearPasswordFields()
                    showSigningMessage(R.string.apk_signing_start_failed)
                }
        }
    }

    private fun resumeSigning(operationId: String) {
        val storeCharacters = characters(storePassword)
        val keyCharacters = characters(keyPassword).takeIf(CharArray::isNotEmpty)
        if (storeCharacters.isEmpty()) {
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            return showSigningMessage(R.string.apk_signing_password_required)
        }
        ApkSigningSecrets(storeCharacters, keyCharacters).use { secrets ->
            storeCharacters.fill('\u0000')
            keyCharacters?.fill('\u0000')
            val resumed = signingExecutor.resume(operationId, secrets, this)
            clearPasswordFields()
            if (resumed) {
                Toast.makeText(this, R.string.apk_signing_resumed, Toast.LENGTH_LONG).show()
                finish()
            } else {
                showSigningMessage(R.string.apk_signing_resume_failed)
            }
        }
    }

    private fun inspectAliases() {
        val store = keyStore ?: return showSigningMessage(R.string.apk_signing_choose_key_store)
        val path = store.toLegacyPathOrNull()
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
                        keyCoordinator.aliases(path, keyStoreFormat, secrets).getOrThrow()
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
        if (items.isEmpty()) {
            showSigningMessage(R.string.apk_signing_no_aliases)
            return
        }
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

    private fun generateKeyStore() {
        val target = keyStore ?: return showSigningMessage(R.string.apk_signing_choose_key_store)
        val path = target.toLegacyPathOrNull()
            ?: return showSigningMessage(R.string.apk_signing_path_unavailable)
        val requestedAlias = alias.text.toString().trim()
        val requestedSubject = subject.text.toString().trim()
        if (requestedAlias.isEmpty() || requestedSubject.isEmpty()) {
            return showSigningMessage(R.string.apk_signing_key_identity_required)
        }
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
            val generated = try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        keyCoordinator.generate(
                            path, requestedAlias, requestedSubject, secrets
                        ).getOrThrow()
                    }
                }
            } finally {
                secrets.close()
            }
            clearPasswordFields()
            setBusy(false)
            generated.onSuccess {
                pendingGeneration = false
                keyStoreFormat = ApkKeyStoreFormat.PKCS12
                Toast.makeText(
                    this@ApkSignVerifyActivity,
                    R.string.apk_signing_key_store_generated,
                    Toast.LENGTH_LONG
                ).show()
                render()
            }.onFailure {
                showSigningMessage(R.string.apk_signing_key_store_generate_failed)
            }
        }
    }

    private fun verifyApk() {
        val input = source ?: return showSigningMessage(R.string.apk_signing_choose_input)
        val inputPath = input.toLegacyPathOrNull()
            ?: return showSigningMessage(R.string.apk_signing_path_unavailable)
        val v4Path = detachedV4?.let {
            it.toLegacyPathOrNull()
                ?: return showSigningMessage(R.string.apk_signing_path_unavailable)
        }
        val minimum = runCatching {
            SigningInputValidator.verificationMinimumSdk(minimumSdk.text.toString())
        }.getOrElse { return showSigningMessage(R.string.apk_signing_invalid_minimum_sdk) }
        setBusy(true)
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching {
                    verificationExecutor.verify(inputPath, v4Path, minimum).getOrThrow()
                }
            }
            setBusy(false)
            report.onSuccess(::showVerificationReport).onFailure {
                showSigningMessage(R.string.apk_signing_verification_failed_to_run)
            }
        }
    }

    private fun showVerificationReport(report: ApkVerificationReport) {
        val sections = SigningResultRenderer.sections(report)
        resultText.isVisible = true
        resultText.contentDescription = getString(R.string.apk_signing_verification_report)
        resultText.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        resultText.text = buildString {
            appendLine(
                getString(
                    if (sections.verified) R.string.apk_signing_verified
                    else R.string.apk_signing_not_verified
                )
            )
            appendLine()
            appendLine(
                getString(
                    R.string.apk_signing_verified_schemes,
                    sections.schemes
                        .ifBlank { getString(R.string.apk_signing_none) }
                )
            )
            if (sections.certificates.isNotEmpty()) {
                appendLine(getString(R.string.apk_signing_signer_certificates))
                sections.certificates.forEach { appendLine("• $it") }
            }
            if (sections.errors.isNotEmpty()) {
                appendLine(getString(R.string.apk_signing_errors))
                sections.errors.forEach { appendLine("• $it") }
            }
            if (sections.warnings.isNotEmpty()) {
                appendLine(getString(R.string.apk_signing_warnings))
                sections.warnings.forEach { appendLine("• $it") }
            }
        }.trim()
    }

    private fun addPasswordFields() {
        storePassword = addSigningEditText(content,
            R.string.apk_signing_store_password,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            saveState = false,
            password = true
        )
        keyPassword = addSigningEditText(content,
            R.string.apk_signing_key_password_optional,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            saveState = false,
            password = true
        )
        text(R.string.apk_signing_password_security)
    }

    private fun addFormatMenu() {
        val formats = ApkKeyStoreFormat.entries
        val labels = formats.map(::keyStoreFormatDisplayName)
        val layout = TextInputLayout(
            this,
            null,
            MaterialR.attr.textInputOutlinedExposedDropdownMenuStyle
        ).apply {
            hint = getString(R.string.apk_signing_key_store_format)
        }
        formatMenu = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            contentDescription = getString(R.string.apk_signing_key_store_format)
            setAdapter(
                ArrayAdapter(
                    this@ApkSignVerifyActivity,
                    android.R.layout.simple_list_item_1,
                    labels
                )
            )
            setText(keyStoreFormatDisplayName(keyStoreFormat), false)
            setOnItemClickListener { _, _, position, _ ->
                keyStoreFormat = formats[position]
            }
            setOnClickListener { showDropDown() }
        }
        layout.addView(formatMenu, textInputChildLayoutParams())
        content.addView(layout, matchWrapMargins(top = 8))
    }

    private fun clearPasswordFields() {
        if (::storePassword.isInitialized) storePassword.text?.clear()
        if (::keyPassword.isInitialized) keyPassword.text?.clear()
    }

    private fun setBusy(busy: Boolean) {
        progress.isVisible = busy
        setEnabledRecursively(content, !busy)
    }

    private fun switchMode(target: ApkSignVerifyMode) {
        if (mode == target) return
        state.switchMode(target)
        render()
    }

    private fun value(labelRes: Int, value: String) {
        label(labelRes)
        content.addView(signingTextView(value).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
        }, matchWrapMargins(top = 2, bottom = 4))
    }

    private fun sectionHeading(textRes: Int) {
        content.addView(signingTextView(getString(textRes)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleMedium)
        }, matchWrapMargins(top = 24, bottom = 4))
    }

    private fun label(textRes: Int) {
        content.addView(signingTextView(getString(textRes)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
        }, matchWrapMargins(top = 12))
    }

    private fun text(textRes: Int): TextView = signingTextView(getString(textRes)).apply {
        setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
        content.addView(this, matchWrapMargins(bottom = 4))
    }

    private fun ApkSigningUiValidationError.messageResource(): Int = when (this) {
        ApkSigningUiValidationError.EMBEDDED_SCHEME_REQUIRED ->
            R.string.apk_signing_embedded_scheme_required
        ApkSigningUiValidationError.V4_REQUIRES_V2_OR_V3 ->
            R.string.apk_signing_v4_requires_v2_or_v3
        ApkSigningUiValidationError.INVALID_MINIMUM_SDK ->
            R.string.apk_signing_invalid_minimum_sdk
    }

    companion object {
        private const val EXTRA_MODE = "apk_signing.mode"
        private const val EXTRA_SOURCE_URI = "apk_signing.source_uri"
        private const val EXTRA_OPERATION_ID = "apk_signing.operation_id"
        private const val STATE_MODE = "apk_signing.state.mode"
        private const val STATE_SOURCE = "apk_signing.state.source"
        private const val STATE_OUTPUT = "apk_signing.state.output"
        private const val STATE_KEY_STORE = "apk_signing.state.key_store"
        private const val STATE_DETACHED_V4 = "apk_signing.state.detached_v4"
        private const val STATE_KEY_STORE_FORMAT = "apk_signing.state.key_store_format"
        private const val STATE_PENDING_GENERATION = "apk_signing.state.pending_generation"

        fun createSignIntent(context: Context, source: AppPath): Intent =
            Intent(context, ApkSignVerifyActivity::class.java)
                .putExtra(EXTRA_MODE, ApkSignVerifyMode.SIGN.name)
                .putExtra(EXTRA_SOURCE_URI, source.toUriString())

        fun createVerifyIntent(context: Context, source: AppPath): Intent =
            Intent(context, ApkSignVerifyActivity::class.java)
                .putExtra(EXTRA_MODE, ApkSignVerifyMode.VERIFY.name)
                .putExtra(EXTRA_SOURCE_URI, source.toUriString())

        fun createResumeIntent(context: Context, operationId: String): Intent =
            Intent(context, ApkSignVerifyActivity::class.java)
                .putExtra(EXTRA_OPERATION_ID, operationId)
    }
}
