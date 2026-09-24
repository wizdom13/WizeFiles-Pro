package com.wisso.wizefiles.feature.apksigning

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

abstract class SplitSetSignVerifyActivity(
    private val packageKind: SplitSetPackageKind
) : PackageSigningActivity() {
    private val strings: SplitSetSigningStrings
        get() = packageKind.strings
    private lateinit var root: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: LinearLayout
    private lateinit var progress: CircularProgressIndicator
    private lateinit var resultText: TextView
    private lateinit var storePassword: TextInputEditText
    private lateinit var keyPassword: TextInputEditText
    private lateinit var alias: TextInputEditText
    private lateinit var v1: MaterialCheckBox
    private lateinit var v2: MaterialCheckBox
    private lateinit var v3: MaterialCheckBox
    private lateinit var keepBoth: MaterialCheckBox

    private var mode = SplitSetSigningMode.SIGN
    private var source: AppPath? = null
    private var output: AppPath? = null
    private var signingKey: AppPath? = null
    private var certificate: AppPath? = null
    private var keySource = AabSigningKeySource.KEY_STORE
    private var keyStoreFormat = ApkKeyStoreFormat.PKCS12
    private var resumeOperationId: String? = null

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
    private val signingKeyPicker = registerForActivityResult(FileListActivity.OpenFileContract()) {
        it?.let { path -> signingKey = path; render() }
    }
    private val certificatePicker = registerForActivityResult(FileListActivity.OpenFileContract()) {
        it?.let { path -> certificate = path; render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = savedInstanceState?.getString(STATE_MODE)?.let(SplitSetSigningMode::valueOf)
            ?: intent.getStringExtra(EXTRA_MODE)?.let(SplitSetSigningMode::valueOf) ?: SplitSetSigningMode.SIGN
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
        val scroll = NestedScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
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
        supportActionBar?.setTitle(when {
            resumeOperationId != null -> strings.resumeTitle
            mode == SplitSetSigningMode.SIGN -> strings.signTitle
            else -> strings.verifyTitle
        })
        if (resumeOperationId != null) renderResume() else {
            addModeSelector()
            heading(if (mode == SplitSetSigningMode.SIGN) {
                strings.signHeading
            } else {
                strings.verifyHeading
            })
            text(if (mode == SplitSetSigningMode.SIGN) {
                strings.signExplanation
            } else {
                strings.verifyExplanation
            })
            if (mode == SplitSetSigningMode.SIGN) renderSign() else renderVerify()
            addResultViews()
        }
    }

    private fun addModeSelector() {
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val sign = toggle(R.string.apk_signing_sign_tab)
        val verify = toggle(R.string.apk_signing_verify_tab)
        group.addView(sign, weighted())
        group.addView(verify, weighted())
        group.check(if (mode == SplitSetSigningMode.SIGN) sign.id else verify.id)
        group.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                mode = if (id == sign.id) SplitSetSigningMode.SIGN else SplitSetSigningMode.VERIFY
                output = null
                render()
            }
        }
        content.addView(group, margins(bottom = 16))
    }

    private fun renderSign() {
        section(strings.sectionInputOutput)
        pathRow(strings.input, source, strings.chooseInput) {
            sourcePicker.launch(listOf(MimeType.GENERIC))
        }
        pathRow(
            strings.output,
            output,
            R.string.apk_signing_choose_destination,
            signedName(source?.name.orEmpty())
        ) {
            outputPicker.launch(Triple(
                MimeType.GENERIC,
                signedName(source?.name.orEmpty()),
                suggestedOutputDirectory(source)
            ))
        }

        section(strings.sectionKey)
        addKeySourceMenu()
        pathRow(
            if (keySource == AabSigningKeySource.KEY_STORE) {
                R.string.aab_signing_key_store
            } else {
                R.string.aab_signing_pkcs8_key
            },
            signingKey,
            if (keySource == AabSigningKeySource.KEY_STORE) {
                R.string.aab_signing_choose_key_store
            } else {
                R.string.aab_signing_choose_private_key
            }
        ) { signingKeyPicker.launch(listOf(MimeType.GENERIC)) }
        if (keySource == AabSigningKeySource.KEY_STORE) {
            addFormatMenu()
            alias = editText(R.string.apk_signing_key_alias, saveState = true)
            addPasswords(required = true)
        } else {
            alias = TextInputEditText(this)
            pathRow(
                R.string.aab_signing_x509_certificate,
                certificate,
                R.string.aab_signing_choose_certificate
            ) { certificatePicker.launch(listOf(MimeType.GENERIC)) }
            addPasswords(required = false)
        }

        section(R.string.apk_signing_signature_schemes)
        v1 = check(R.string.apk_signing_scheme_v1, true)
        v2 = check(R.string.apk_signing_scheme_v2, true)
        v3 = check(R.string.apk_signing_scheme_v3, true)
        listOf(v1, v2, v3).forEach { content.addView(it, matchWrap()) }
        text(strings.noV4)

        section(strings.sectionReview)
        keepBoth = check(R.string.apk_signing_keep_both, true)
        content.addView(keepBoth, matchWrap())
        content.addView(button(strings.start, true) { startSigning() }, margins(top = 12))
    }

    private fun renderVerify() {
        section(strings.sectionVerify)
        pathRow(strings.input, source, strings.chooseInput) {
            sourcePicker.launch(listOf(MimeType.GENERIC))
        }
        content.addView(button(strings.verify, true) { verifySplitSet() }, margins(top = 12))
    }

    private fun renderResume() {
        val operationId = requireNotNull(resumeOperationId)
        val spec = packageKind.load(operationId)
        if (spec == null) {
            heading(strings.resumeUnavailable)
            text(strings.resumeUnavailableExplanation)
            return
        }
        heading(strings.reenterPassword)
        text(strings.resumeExplanation)
        value(strings.input, spec.sourceUri)
        value(strings.output, spec.outputUri)
        value(strings.sectionKey, spec.signingKeyUri)
        keySource = spec.keySource
        addPasswords(required = spec.keySource == AabSigningKeySource.KEY_STORE)
        content.addView(button(strings.resumeAction, true) {
            resumeSigning(operationId)
        }, margins(top = 12))
        addResultViews()
    }

    private fun startSigning() {
        val input = source ?: return message(strings.chooseInputFirst)
        val target = output ?: return message(strings.chooseOutputFirst)
        val key = signingKey ?: return message(R.string.aab_signing_choose_signing_key)
        if (keySource == AabSigningKeySource.PKCS8_CERTIFICATE && certificate == null) {
            return message(R.string.aab_signing_choose_x509_certificate)
        }
        val store = characters(storePassword)
        val keyPasswordChars = characters(keyPassword).takeIf(CharArray::isNotEmpty)
        if (keySource == AabSigningKeySource.KEY_STORE && store.isEmpty()) {
            wipe(store, keyPasswordChars)
            return message(R.string.apk_signing_password_required)
        }
        val schemes = buildSet {
            if (v1.isChecked) add(ApkSignatureScheme.V1)
            if (v2.isChecked) add(ApkSignatureScheme.V2)
            if (v3.isChecked) add(ApkSignatureScheme.V3)
        }
        val request = runCatching {
            SplitSetSigningRequest(
                sourceUri = input.toUriString(),
                outputUri = target.toUriString(),
                signingKeyUri = key.toUriString(),
                certificateUri = certificate?.toUriString().orEmpty(),
                keySource = keySource,
                keyAlias = alias.text?.toString()?.trim().orEmpty(),
                keyStoreFormat = keyStoreFormat,
                schemes = schemes,
                conflictPolicy = if (keepBoth.isChecked) {
                    ApkSigningOutputConflictPolicy.KEEP_BOTH
                } else {
                    ApkSigningOutputConflictPolicy.FAIL
                }
            )
        }.getOrElse {
            wipe(store, keyPasswordChars)
            return message(strings.invalidRequest)
        }
        ApkSigningSecrets(store, keyPasswordChars).use { secrets ->
            wipe(store, keyPasswordChars)
            runCatching { packageKind.start(request, secrets, this) }
                .onSuccess {
                    clearPasswordFields()
                    Toast.makeText(this, strings.started, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, TransferCenterActivity::class.java))
                    finish()
                }
                .onFailure { message(strings.startFailed) }
        }
    }

    private fun resumeSigning(operationId: String) {
        val store = characters(storePassword)
        val keyPasswordChars = characters(keyPassword).takeIf(CharArray::isNotEmpty)
        if (keySource == AabSigningKeySource.KEY_STORE && store.isEmpty()) {
            wipe(store, keyPasswordChars)
            return message(R.string.apk_signing_password_required)
        }
        ApkSigningSecrets(store, keyPasswordChars).use { secrets ->
            wipe(store, keyPasswordChars)
            val resumed = runCatching {
                packageKind.resume(operationId, secrets, this)
            }.getOrDefault(false)
            clearPasswordFields()
            if (resumed) {
                Toast.makeText(this, strings.resumed, Toast.LENGTH_LONG).show()
                finish()
            } else message(strings.resumeFailed)
        }
    }

    private fun verifySplitSet() {
        val input = source ?: return message(strings.chooseInputFirst)
        val path = input.toLegacyPathOrNull()
            ?: return message(R.string.apk_signing_path_unavailable)
        setBusy(true)
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching { packageKind.verify(path) }
            }
            setBusy(false)
            report.onSuccess(::showReport)
                .onFailure { message(strings.verificationFailedToRun) }
        }
    }

    private fun showReport(report: AndroidSplitSetVerificationReport) {
        resultText.isVisible = true
        resultText.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        resultText.text = buildString {
            appendLine(getString(if (report.verified) {
                R.string.apk_signing_verified
            } else {
                R.string.apk_signing_not_verified
            }))
            appendLine()
            report.packageName?.let { appendLine(getString(strings.packageName, it)) }
            report.versionCode?.let { appendLine(getString(strings.version, it)) }
            appendLine(getString(strings.apkCount, report.apks.size))
            if (report.signerCertificateSha256.isNotEmpty()) {
                appendLine(getString(R.string.apk_signing_signer_certificates))
                report.signerCertificateSha256.forEach { appendLine("• $it") }
            }
            if (report.errors.isNotEmpty()) {
                appendLine(getString(R.string.apk_signing_errors))
                report.errors.forEach { appendLine("• $it") }
            }
        }.trim()
    }

    private fun addKeySourceMenu() {
        val values = AabSigningKeySource.entries
        val labels = values.map { getString(if (it == AabSigningKeySource.KEY_STORE) {
            R.string.aab_signing_key_source_store
        } else {
            R.string.aab_signing_key_source_pkcs8
        }) }
        val layout = TextInputLayout(this, null, MaterialR.attr.textInputOutlinedExposedDropdownMenuStyle)
            .apply { hint = getString(R.string.aab_signing_key_source) }
        val menu = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(this@SplitSetSignVerifyActivity, android.R.layout.simple_list_item_1, labels))
            setText(labels[values.indexOf(keySource)], false)
            setOnItemClickListener { _, _, index, _ -> keySource = values[index]; render() }
            setOnClickListener { showDropDown() }
        }
        layout.addView(menu, textInputChildLayoutParams())
        content.addView(layout, margins(top = 8))
    }

    private fun addFormatMenu() {
        val formats = ApkKeyStoreFormat.entries
        val labels = formats.map(ApkKeyStoreFormat::name)
        val layout = TextInputLayout(this, null, MaterialR.attr.textInputOutlinedExposedDropdownMenuStyle)
            .apply { hint = getString(R.string.apk_signing_key_store_format) }
        val menu = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(this@SplitSetSignVerifyActivity, android.R.layout.simple_list_item_1, labels))
            setText(keyStoreFormat.name, false)
            setOnItemClickListener { _, _, index, _ -> keyStoreFormat = formats[index] }
            setOnClickListener { showDropDown() }
        }
        layout.addView(menu, textInputChildLayoutParams())
        content.addView(layout, margins(top = 8))
    }

    private fun addPasswords(required: Boolean) {
        storePassword = editText(
            if (required) R.string.apk_signing_store_password
            else R.string.aab_signing_private_key_password_optional,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            saveState = false,
            password = true
        )
        keyPassword = if (required) editText(
            R.string.apk_signing_key_password_optional,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            saveState = false,
            password = true
        ) else TextInputEditText(this)
        text(R.string.apk_signing_password_security)
    }

    private fun editText(
        hintRes: Int,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        saveState: Boolean,
        password: Boolean = false
    ): TextInputEditText {
        val layout = TextInputLayout(this).apply {
            hint = getString(hintRes)
            if (password) {
                endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                isSaveEnabled = false
            }
        }
        val field = TextInputEditText(layout.context).apply {
            this.inputType = inputType
            isSaveEnabled = saveState
            if (!saveState && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
        }
        layout.addView(field, textInputChildLayoutParams())
        content.addView(layout, margins(top = 8))
        return field
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
        body.addView(labelText(getString(label)), matchWrap())
        body.addView(textView(path?.name ?: suggested ?: getString(R.string.apk_signing_not_selected)), margins(top = 4))
        body.addView(button(action, false, choose), margins(top = 10))
        card.addView(body, matchWrap())
        content.addView(card, margins(top = 8, bottom = 4))
    }

    private fun addResultViews() {
        progress = CircularProgressIndicator(this).apply { isVisible = false }
        content.addView(progress, centered(top = 12))
        resultText = textView("").apply { isVisible = false }
        content.addView(resultText, margins(top = 12))
    }

    private fun setBusy(busy: Boolean) {
        progress.isVisible = busy
        for (index in 0 until content.childCount) content.getChildAt(index).isEnabled = !busy
    }

    private fun heading(resource: Int) {
        content.addView(textView(getString(resource)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleLarge)
        }, margins(bottom = 6))
    }

    private fun section(resource: Int) {
        content.addView(textView(getString(resource)).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleMedium)
        }, margins(top = 24, bottom = 4))
    }

    private fun value(label: Int, value: String) {
        content.addView(labelText(getString(label)), margins(top = 12))
        content.addView(textView(value), margins(top = 2))
    }

    private fun text(resource: Int) {
        content.addView(textView(getString(resource)), margins(bottom = 4))
    }

    private fun labelText(value: String) = textView(value).apply {
        setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
    }

    private fun textView(value: CharSequence) = TextView(this).apply {
        text = value
        setTextIsSelectable(true)
    }

    private fun button(resource: Int, primary: Boolean, action: () -> Unit) =
        (if (primary) MaterialButton(this) else MaterialButton(
            this,
            null,
            MaterialR.attr.materialButtonOutlinedStyle
        )).apply {
            setText(resource)
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun toggle(resource: Int) = MaterialButton(
        this,
        null,
        MaterialR.attr.materialButtonOutlinedStyle
    ).apply {
        id = View.generateViewId()
        setText(resource)
        isCheckable = true
        isAllCaps = false
    }

    private fun check(resource: Int, checked: Boolean) = MaterialCheckBox(this).apply {
        setText(resource)
        isChecked = checked
    }

    private fun clearPasswordFields() {
        if (::storePassword.isInitialized) storePassword.text?.clear()
        if (::keyPassword.isInitialized) keyPassword.text?.clear()
    }

    private fun wipe(store: CharArray, key: CharArray?) {
        store.fill('\u0000')
        key?.fill('\u0000')
    }

    private fun message(resource: Int) {
        Toast.makeText(this, resource, Toast.LENGTH_LONG).show()
    }

    private fun signedName(name: String): String = packageKind.signedName(name)

    private fun margins(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun centered(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL; topMargin = dp(top) }
    companion object {
        internal const val EXTRA_MODE = "split_set_signing.mode"
        internal const val EXTRA_SOURCE_URI = "split_set_signing.source_uri"
        internal const val EXTRA_OPERATION_ID = "split_set_signing.operation_id"
        private const val STATE_MODE = "split_set_signing.state.mode"
        private const val STATE_SOURCE = "split_set_signing.state.source"
        private const val STATE_OUTPUT = "split_set_signing.state.output"
        private const val STATE_SIGNING_KEY = "split_set_signing.state.signing_key"
        private const val STATE_CERTIFICATE = "split_set_signing.state.certificate"
        private const val STATE_KEY_SOURCE = "split_set_signing.state.key_source"
        private const val STATE_KEY_STORE_FORMAT = "split_set_signing.state.key_store_format"
    }
}
