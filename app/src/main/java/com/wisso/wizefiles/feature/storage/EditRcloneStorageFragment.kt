package com.wisso.wizefiles.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.databinding.FragmentEditRcloneStorageBinding
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.provider.rclone.RcloneConfigOption
import com.wisso.wizefiles.provider.rclone.RcloneConfigStep
import com.wisso.wizefiles.provider.rclone.RcloneEngine
import com.wisso.wizefiles.provider.rclone.RcloneProviderDefinition
import com.wisso.wizefiles.provider.rclone.shouldShowRcloneOption
import com.wisso.wizefiles.ui.UnfilteredArrayAdapter
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.showToast
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

class EditRcloneStorageFragment : Fragment() {
    private val args by args<Args>()
    private val editorState by viewModels<RcloneStorageEditorViewModel>()
    private lateinit var binding: FragmentEditRcloneStorageBinding
    private var importedConfiguration: String?
        get() = editorState.importedConfiguration
        set(value) { editorState.importedConfiguration = value }
    private var importedRemotes: List<ImportedRemote>
        get() = editorState.importedRemotes
        set(value) { editorState.importedRemotes = value }
    private var allProviderChoices: List<ProviderChoice>
        get() = editorState.allProviderChoices
        set(value) { editorState.allProviderChoices = value }
    private var providerChoices: List<ProviderChoice> = emptyList()
    private var providersExpanded: Boolean
        get() = editorState.providersExpanded
        set(value) { editorState.providersExpanded = value }
    private var selectedProviderKey: String
        get() = editorState.selectedProviderKey
        set(value) { editorState.selectedProviderKey = value }
    private var suggestedAccountName: String?
        get() = editorState.suggestedAccountName
        set(value) { editorState.suggestedAccountName = value }
    private val dynamicFields = linkedMapOf<String, DynamicField>()
    private var renderedProviderKey: String? = null

    private val importConfigurationLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            runCatching {
                requireContext().contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Unable to read configuration")
            }.onSuccess(::onConfigurationImported)
                .onFailure { showToast(getString(R.string.rclone_import_failed, it.message)) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentEditRcloneStorageBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        activity.setTitle(
            if (args.storage == null) {
                R.string.rclone_add_cloud_title
            } else {
                R.string.rclone_edit_cloud_title
            }
        )

        allProviderChoices = fallbackRcloneProviderChoices(requireContext())
        refreshProviderChoices()
        binding.providerLayout.setStartIconTintList(binding.providerEdit.textColors)
        applyProviderSelection(
            args.storage?.providerType ?: RcloneProviderSchema.DEFAULT_PROVIDER_TYPE,
            updateAccountName = args.storage == null && savedInstanceState == null
        )
        binding.providerEdit.setOnItemClickListener { _, _, position, _ ->
            val choice = providerChoices[position]
            if (choice.isOther) {
                providersExpanded = true
                refreshProviderChoices()
                binding.providerEdit.setText(selectedProvider().title, false)
                binding.providerEdit.post { binding.providerEdit.showDropDown() }
            } else {
                applyProviderSelection(choice.key, updateAccountName = true)
            }
        }
        binding.powerUserSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !ensureProAccess(ProFeature.RCLONE_POWER_USER)) {
                binding.powerUserSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            renderedProviderKey = null
            updateFieldVisibility()
        }
        binding.importButton.setOnClickListener {
            importConfigurationLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
        }
        binding.saveButton.setOnClickListener { save() }
        binding.cancelButton.setOnClickListener { finish() }
        binding.removeButton.isVisible = args.storage != null
        binding.removeButton.setOnClickListener { remove() }

        if (savedInstanceState == null) {
            args.storage?.let { storage ->
                binding.nameEdit.setText(storage.customName)
                binding.rootPathEdit.setText(storage.rootPath)
                selectedProviderKey = storage.providerType
                binding.providerEdit.setText(
                    allProviderChoices.firstOrNull { it.key == selectedProviderKey }?.title
                        ?: storage.providerType,
                    false
                )
            }
        }
        updateFieldVisibility()
        loadProviderDefinitions()
    }

    private fun updateFieldVisibility() {
        val provider = selectedProvider()
        val powerUser = binding.powerUserSwitch.isChecked
        val editing = args.storage != null
        val visibility = RcloneFormRenderer.visibility(provider, powerUser, editing)
        binding.regularModeExplanation.isVisible = !editing
        binding.providerLayout.isVisible = !editing
        binding.webDavFields.isVisible = visibility.webDav
        binding.s3Fields.isVisible = visibility.s3
        binding.importFields.isVisible = visibility.imported
        binding.oauthFields.isVisible = visibility.oauth
        binding.megaFields.isVisible = visibility.mega
        binding.powerUserSwitch.isVisible = !editing
        binding.powerUserWarning.isVisible = !editing
        binding.powerUserFields.isVisible = visibility.advanced
        binding.dynamicOptionsFields.isVisible = visibility.dynamic
        binding.saveButton.setText(
            if (!editing && !powerUser &&
                regularSetupFor(provider.backendType) == RcloneRegularSetup.OAUTH
            ) {
                R.string.rclone_sign_in_and_add
            } else {
                R.string.rclone_connect_and_add
            }
        )
        renderDynamicFields(provider, powerUser)
    }

    private fun selectedProvider(): ProviderChoice =
        allProviderChoices.firstOrNull { it.key == selectedProviderKey }
            ?: allProviderChoices.firstOrNull { it.backendType == RcloneProviderSchema.DEFAULT_PROVIDER_TYPE }
            ?: fallbackRcloneProviderChoices(requireContext()).first()

    private fun loadProviderDefinitions() {
        viewLifecycleOwner.lifecycleScope.launch {
            val definitions = try {
                withContext(Dispatchers.IO) { RcloneStorageRepository.providerDefinitions() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isAdded) {
                    return@launch
                }
                showToast(
                    getString(R.string.rclone_provider_load_failed, exception.message)
                )
                return@launch
            }
            if (!isAdded || view == null) {
                return@launch
            }
            val selectedKey = args.storage?.providerType ?: selectedProviderKey
            allProviderChoices = definitions
                .filterNot { it.backendType in RcloneProviderSchema.unsupportedProviderTypes }
                .sortedWith(
                    compareBy<RcloneProviderDefinition>(
                        { brandedProviderRank(it.backendType) },
                        { it.description.lowercase() }
                    )
                )
                .map { definition ->
                    ProviderChoice(
                        title = rcloneProviderTitle(requireContext(), definition),
                        backendType = definition.backendType,
                        definition = definition
                    )
                } + ProviderChoice(
                title = getString(R.string.rclone_provider_import),
                backendType = null,
                definition = null,
                isImport = true
            )
            refreshProviderChoices()
            val selected = allProviderChoices.firstOrNull { it.key == selectedKey }
                ?: allProviderChoices.firstOrNull {
                    it.backendType == RcloneProviderSchema.DEFAULT_PROVIDER_TYPE
                }
                ?: allProviderChoices.first()
            applyProviderSelection(
                selected.key,
                updateAccountName = args.storage == null
            )
        }
    }

    private fun refreshProviderChoices() {
        val choicesByKey = allProviderChoices.associateBy(ProviderChoice::key)
        providerChoices = RcloneProviderSchema.visibleChoices(allProviderChoices, providersExpanded).map { key ->
            if (key == OTHER_CLOUD_PROVIDERS_KEY) {
                ProviderChoice(
                    title = getString(R.string.rclone_provider_other),
                    backendType = null,
                    isOther = true
                )
            } else {
                checkNotNull(choicesByKey[key])
            }
        }
        binding.providerEdit.setAdapter(
            ProviderChoiceAdapter(requireContext(), providerChoices)
        )
    }

    private fun applyProviderSelection(key: String, updateAccountName: Boolean) {
        val choice = allProviderChoices.firstOrNull { it.key == key }
            ?: allProviderChoices.firstOrNull {
                it.backendType == RcloneProviderSchema.DEFAULT_PROVIDER_TYPE
            }
            ?: allProviderChoices.first()
        selectedProviderKey = choice.key
        binding.providerEdit.setText(choice.title, false)
        binding.providerLayout.setStartIconDrawable(
            if (choice.isImport) {
                R.drawable.ic_download_white_24dp
            } else {
                rcloneProviderIconRes(choice.backendType.orEmpty())
            }
        )
        renderedProviderKey = null
        if (updateAccountName) {
            updateSuggestedAccountName(choice)
        }
        updateFieldVisibility()
    }

    private fun updateSuggestedAccountName(provider: ProviderChoice) {
        val suggestion = defaultCloudAccountName(provider.backendType, provider.title)
        val current = binding.nameEdit.text.toString()
        if (current.isBlank() || current == suggestedAccountName) {
            binding.nameEdit.setText(suggestion.orEmpty())
        }
        suggestedAccountName = suggestion
    }

    private fun renderDynamicFields(provider: ProviderChoice, powerUser: Boolean) {
        val key = "${provider.backendType}:$powerUser"
        if (renderedProviderKey == key) {
            return
        }
        renderedProviderKey = key
        dynamicFields.clear()
        binding.dynamicOptionsFields.removeAllViews()
        provider.definition?.options
            ?.filter { shouldShowRcloneOption(it, powerUser) }
            ?.forEach(::addDynamicField)
    }

    private fun addDynamicField(option: RcloneConfigOption) {
        val layout = TextInputLayout(requireContext()).apply {
            hint = humanizeRcloneOptionName(option.name)
            helperText = option.help.lineSequence().firstOrNull().orEmpty()
            isErrorEnabled = option.required
        }
        val input: TextView
        if (option.examples.isNotEmpty() && option.exclusive) {
            input = AutoCompleteTextView(requireContext()).apply {
                inputType = InputType.TYPE_NULL
                setAdapter(
                    UnfilteredArrayAdapter(
                        requireContext(),
                        R.layout.item_dropdown,
                        objects = option.examples.map { it.label }
                    )
                )
                val defaultExample = option.examples.firstOrNull {
                    it.value == option.defaultValue
                }
                setText(defaultExample?.label ?: option.defaultValue, false)
            }
        } else {
            input = TextInputEditText(requireContext()).apply {
                inputType = rcloneInputTypeFor(option)
                setText(option.defaultValue)
            }
            if (option.isPassword) {
                layout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
        }
        layout.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        binding.dynamicOptionsFields.addView(
            layout,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        dynamicFields[option.name] = DynamicField(option, layout, input)
    }

    private fun onConfigurationImported(configuration: String) {
        val remotes = parseImportedRemotes(configuration)
        if (remotes.isEmpty()) {
            showToast(R.string.rclone_import_no_remotes)
            return
        }
        importedConfiguration = configuration
        importedRemotes = remotes
        binding.importedRemoteLayout.isVisible = true
        binding.importedRemoteEdit.setAdapter(
            UnfilteredArrayAdapter(
                requireContext(),
                R.layout.item_dropdown,
                objects = remotes.map { it.name }
            )
        )
        binding.importedRemoteEdit.setText(remotes.first().name, false)
        if (binding.nameEdit.text.isNullOrBlank()) {
            binding.nameEdit.setText(remotes.first().name)
        }
        showToast(resources.getQuantityString(R.plurals.rclone_imported_remote_count, remotes.size, remotes.size))
    }

    private fun save() {
        val existing = args.storage
        if (existing == null && !Storages.canAddRemoteStorage()) {
            ensureProAccess(ProFeature.UNLIMITED_REMOTE_CONNECTIONS)
            return
        }
        if (binding.powerUserSwitch.isChecked &&
            !ensureProAccess(ProFeature.RCLONE_POWER_USER)
        ) {
            return
        }
        val provider = selectedProvider()
        val displayName = binding.nameEdit.text.toString().trim()
        if (displayName.isBlank()) {
            binding.nameLayout.error = getString(R.string.rclone_required)
            return
        }
        binding.nameLayout.error = null
        val rootPath = binding.rootPathEdit.text.toString().trim().trim('/')

        if (existing != null && importedConfiguration == null) {
            Storages.replace(
                existing.copy(
                    customName = displayName,
                    rootPath = rootPath
                )
            )
            finish()
            return
        }

        val imported = if (provider.isImport) selectedImportedRemote() else null
        if (provider.isImport && imported == null) {
            showToast(R.string.rclone_import_required)
            return
        }
        val remoteName = newRemoteName()
        val backendType = imported?.type
            ?: binding.backendTypeEdit.text.toString().trim()
                .takeIf {
                    binding.powerUserSwitch.isChecked &&
                        provider.definition == null &&
                        it.isNotEmpty()
                }
            ?: provider.backendType
            ?: run {
                showToast(R.string.rclone_backend_type_required)
                return
            }
        val parameters = buildParameters(provider)
        if (parameters == null) {
            return
        }

        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                if (imported != null) {
                    withContext(Dispatchers.IO) {
                        RcloneStorageRepository.importRemote(
                            checkNotNull(importedConfiguration),
                            imported.name,
                            remoteName
                        )
                    }
                } else {
                    configureRemote(
                        remoteName = remoteName,
                        backendType = backendType,
                        parameters = parameters,
                        askAll = binding.powerUserSwitch.isChecked,
                        regularMode = !binding.powerUserSwitch.isChecked
                    )
                }
                try {
                    withContext(Dispatchers.IO) {
                        // A real listing verifies credentials and the selected root.
                        RcloneStorageRepository.verify(remoteName, rootPath)
                    }
                } catch (throwable: Throwable) {
                    withContext(Dispatchers.IO) {
                        runCatching { RcloneStorageRepository.deleteRemote(remoteName) }
                    }
                    throw throwable
                }
            }.onSuccess {
                RcloneStorageRepository.save(
                    RcloneStorage(
                        id = existing?.id,
                        customName = displayName,
                        remoteName = remoteName,
                        providerType = backendType,
                        rootPath = rootPath
                    )
                )
                finish()
            }.onFailure {
                setBusy(false)
                if (it !is RcloneConfigurationCancelledException) {
                    showToast(getString(R.string.rclone_connection_failed, it.message))
                }
            }
        }
    }

    private suspend fun configureRemote(
        remoteName: String,
        backendType: String,
        parameters: Map<String, String>,
        askAll: Boolean,
        regularMode: Boolean
    ) {
        var created = false
        try {
            var step = withContext(Dispatchers.IO) {
                RcloneEngine.beginCreateRemote(
                    remoteName = remoteName,
                    backendType = backendType,
                    parameters = parameters,
                    askAll = askAll
                )
            }
            created = true
            while (!step.isComplete) {
                val automaticAnswer = if (regularMode) {
                    automaticRegularConfigAnswer(
                        backendType,
                        step.option?.name.orEmpty()
                    )
                } else {
                    null
                }
                val answer = automaticAnswer ?: RcloneConfigurationQuestionDialog(requireContext()).ask(step)
                val opensOAuthBrowser =
                    automaticAnswer != null && step.option?.name == "config_is_local"
                val appContext = requireContext().applicationContext
                try {
                    if (opensOAuthBrowser) {
                        withContext(Dispatchers.IO) {
                            RcloneAuthenticationCoordinator.start(appContext, ::openOAuthURL)
                        }
                    }
                    step = withContext(Dispatchers.IO) {
                        RcloneEngine.continueCreateRemote(
                            remoteName = remoteName,
                            backendType = backendType,
                            parameters = parameters,
                            askAll = askAll,
                            state = step.state,
                            result = answer
                        )
                    }
                } finally {
                    if (opensOAuthBrowser) {
                        RcloneAuthenticationCoordinator.stop(appContext)
                    }
                }
            }
        } catch (throwable: Throwable) {
            if (created) {
                withContext(Dispatchers.IO) {
                    runCatching { RcloneEngine.deleteRemote(remoteName) }
                }
            }
            throw throwable
        }
    }

    private fun buildParameters(provider: ProviderChoice): Map<String, String>? {
        val parameters = linkedMapOf<String, String>()
        val powerUser = binding.powerUserSwitch.isChecked
        when {
            provider.backendType == "webdav" && !powerUser -> {
                val url = binding.webDavUrlEdit.text.toString().trim()
                if (url.isBlank()) {
                    binding.webDavUrlLayout.error = getString(R.string.rclone_required)
                    return null
                }
                parameters["url"] = url
                parameters["user"] = binding.usernameEdit.text.toString()
                parameters["pass"] = binding.passwordEdit.text.toString()
                parameters["vendor"] = binding.webDavVendorEdit.text.toString()
                    .trim()
                    .ifBlank { "other" }
            }
            provider.backendType == "s3" && !powerUser -> {
                val endpoint = binding.s3EndpointEdit.text.toString().trim()
                if (endpoint.isBlank()) {
                    binding.s3EndpointLayout.error = getString(R.string.rclone_required)
                    return null
                }
                parameters["provider"] = "Other"
                parameters["endpoint"] = endpoint
                parameters["access_key_id"] = binding.s3AccessKeyEdit.text.toString()
                parameters["secret_access_key"] = binding.s3SecretKeyEdit.text.toString()
                parameters["region"] = binding.s3RegionEdit.text.toString()
            }
            provider.backendType == "mega" && !powerUser -> {
                val username = binding.megaUsernameEdit.text.toString().trim()
                val password = binding.megaPasswordEdit.text.toString()
                if (username.isBlank()) {
                    binding.megaUsernameLayout.error = getString(R.string.rclone_required)
                    return null
                }
                if (password.isBlank()) {
                    binding.megaPasswordLayout.error = getString(R.string.rclone_required)
                    return null
                }
                binding.megaUsernameLayout.error = null
                binding.megaPasswordLayout.error = null
                parameters["user"] = username
                parameters["pass"] = password
            }
            regularSetupFor(provider.backendType) == RcloneRegularSetup.OAUTH && !powerUser -> {
                // rclone's provider defaults and OAuth continuation supply the credentials.
            }
            provider.isImport -> {}
            provider.definition != null -> {
                if (!collectDynamicParameters(parameters)) {
                    return null
                }
            }
        }
        if (powerUser && provider.definition == null) {
            parameters += RcloneConfigurationValidator.advancedOptions(
                binding.advancedOptionsEdit.text.toString()
            )
        }
        return parameters
    }

    private fun openOAuthURL(url: String): Boolean {
        val completed = CountDownLatch(1)
        var opened = false
        Handler(Looper.getMainLooper()).post {
            opened = try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            } catch (_: ActivityNotFoundException) {
                false
            } finally {
                completed.countDown()
            }
        }
        return completed.await(OAUTH_BROWSER_OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS) && opened
    }

    private fun collectDynamicParameters(parameters: MutableMap<String, String>): Boolean {
        var valid = true
        dynamicFields.forEach { (name, field) ->
            val displayed = field.input.text.toString()
            val value = if (field.input is AutoCompleteTextView) {
                field.option.examples.firstOrNull { it.label == displayed }?.value ?: displayed
            } else {
                displayed
            }.trim()
            if (field.option.required && value.isBlank()) {
                field.layout.error = getString(R.string.rclone_required)
                valid = false
            } else {
                field.layout.error = null
                if (value.isNotBlank() && value != field.option.defaultValue) {
                    parameters[name] = value
                }
            }
        }
        return valid
    }

    private fun selectedImportedRemote(): ImportedRemote? {
        val name = binding.importedRemoteEdit.text.toString()
        return importedRemotes.firstOrNull { it.name == name }
    }

    private fun remove() {
        val storage = args.storage ?: return
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    RcloneStorageRepository.deleteRemote(storage.remoteName)
                }
            }.onSuccess {
                RcloneStorageRepository.remove(storage)
                finish()
            }.onFailure {
                setBusy(false)
                showToast(getString(R.string.rclone_connection_failed, it.message))
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.isVisible = busy
        binding.scrollView.isVisible = !busy
        binding.bottomBar.isVisible = !busy
    }

    private fun newRemoteName(): String =
        "wf${UUID.randomUUID().toString().replace("-", "")}"

    @Parcelize
    data class Args(val storage: RcloneStorage? = null) : ParcelableArgs

    private data class DynamicField(
        val option: RcloneConfigOption,
        val layout: TextInputLayout,
        val input: TextView
    )

    companion object {
        private const val OAUTH_BROWSER_OPEN_TIMEOUT_SECONDS = 5L
    }
}
