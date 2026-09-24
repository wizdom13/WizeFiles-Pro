package com.wisso.wizefiles.storage

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.hierynomus.sshj.common.KeyDecryptionFailedException
import java.nio.file.Path
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.FragmentEditSftpServerBinding
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.provider.sftp.client.Authority
import com.wisso.wizefiles.provider.sftp.client.SftpHostKeyMismatchException
import com.wisso.wizefiles.provider.sftp.client.SftpHostKeyVerificationException
import com.wisso.wizefiles.provider.sftp.client.SftpUnknownHostKeyException
import com.wisso.wizefiles.provider.sftp.client.SftpPresentedHostKey
import com.wisso.wizefiles.provider.sftp.client.PasswordAuthentication
import com.wisso.wizefiles.provider.sftp.client.PublicKeyAuthentication
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.ui.UnfilteredArrayAdapter
import com.wisso.wizefiles.util.ActionState
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.fadeToVisibilityUnsafe
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.getTextArray
import com.wisso.wizefiles.util.hideTextInputLayoutErrorOnTextChange
import com.wisso.wizefiles.util.isReady
import com.wisso.wizefiles.util.launchSafe
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.takeIfNotEmpty
import com.wisso.wizefiles.util.viewModels
import java.net.URI

class EditSftpServerFragment : Fragment() {
    private val openPrivateKeyFileLauncher = registerForActivityResult(
        FileListActivity.OpenFileContract(), this::onOpenPrivateKeyFileResult
    )

    private val args by args<Args>()

    private val viewModel by viewModels { { EditSftpServerViewModel() } }

    private lateinit var binding: FragmentEditSftpServerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentEditSftpServerBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        activity.setTitle(
            if (args.server != null) {
                R.string.storage_edit_sftp_server_title_edit
            } else {
                R.string.storage_edit_sftp_server_title_add
            }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.readPrivateKeyFileState.collect { onReadPrivateKeyFileStateChanged(it) } }
                launch { viewModel.connectState.collect { onConnectStateChanged(it) } }
            }
        }

        binding.hostEdit.hideTextInputLayoutErrorOnTextChange(binding.hostLayout)
        binding.hostEdit.doAfterTextChanged { updateNamePlaceholder() }
        binding.portEdit.hideTextInputLayoutErrorOnTextChange(binding.portLayout)
        binding.portEdit.doAfterTextChanged { updateNamePlaceholder() }
        binding.pathEdit.doAfterTextChanged { updateNamePlaceholder() }
        binding.authenticationTypeEdit.setAdapter(
            UnfilteredArrayAdapter(
                binding.authenticationTypeEdit.context, R.layout.item_dropdown,
                objects = getTextArray(R.array.storage_edit_sftp_server_authentication_type_entries)
            )
        )
        authenticationType = AuthenticationType.PASSWORD
        binding.authenticationTypeEdit.doAfterTextChanged {
            onAuthenticationTypeChanged(authenticationType)
        }
        binding.usernameEdit.hideTextInputLayoutErrorOnTextChange(binding.usernameLayout)
        binding.usernameEdit.doAfterTextChanged { updateNamePlaceholder() }
        binding.privateKeyLayout.setEndIconOnClickListener { onOpenPrivateKeyFile() }
        binding.privateKeyEdit.hideTextInputLayoutErrorOnTextChange(
            binding.privateKeyLayout, binding.privateKeyPasswordLayout
        )
        binding.privateKeyPasswordEdit.hideTextInputLayoutErrorOnTextChange(
            binding.privateKeyLayout, binding.privateKeyPasswordLayout
        )
        binding.saveOrConnectAndAddButton.setText(
            if (args.server != null) {
                R.string.save
            } else {
                R.string.storage_edit_sftp_server_connect_and_add
            }
        )
        binding.saveOrConnectAndAddButton.setOnClickListener {
            if (args.server != null) {
                saveOrAdd()
            } else {
                connectAndAdd()
            }
        }
        binding.cancelButton.setOnClickListener { finish() }
        binding.removeOrAddButton.setText(
            if (args.server != null) R.string.remove else R.string.storage_edit_sftp_server_add
        )
        binding.removeOrAddButton.setOnClickListener {
            if (args.server != null) {
                remove()
            } else {
                saveOrAdd()
            }
        }

        if (savedInstanceState == null) {
            val server = args.server
            if (server != null) {
                val authority = server.authority
                binding.hostEdit.setText(authority.host)
                if (authority.port != Authority.DEFAULT_PORT) {
                    binding.portEdit.setText(authority.port.toString())
                }
                binding.usernameEdit.setText(authority.username)
                when (val authentication = server.authentication) {
                    is PasswordAuthentication -> {
                        authenticationType = AuthenticationType.PASSWORD
                        binding.passwordEdit.setText(authentication.password)
                    }
                    is PublicKeyAuthentication -> {
                        authenticationType = AuthenticationType.PUBLIC_KEY
                        binding.privateKeyEdit.setText(authentication.privateKey)
                        binding.privateKeyPasswordEdit.setText(authentication.privateKeyPassword)
                    }
                }
                binding.pathEdit.setText(server.relativePath)
                binding.nameEdit.setText(server.customName)
            }
        }
    }

    private fun updateNamePlaceholder() {
        val host = binding.hostEdit.text.toString().takeIfNotEmpty()
        val port = binding.portEdit.text.toString().takeIfNotEmpty()?.toIntOrNull()
            ?: Authority.DEFAULT_PORT
        val path = binding.pathEdit.text.toString().trim()
        val username = binding.usernameEdit.text.toString()
        binding.nameLayout.placeholderText = if (host != null) {
            val authority = Authority(host, port, username)
            if (path.isNotEmpty()) "$authority/$path" else authority.toString()
        } else {
            getString(R.string.storage_edit_sftp_server_name_placeholder)
        }
    }

    private var authenticationType: AuthenticationType
        get() {
            val adapter = binding.authenticationTypeEdit.adapter
            val items = List(adapter.count) { adapter.getItem(it) as CharSequence }
            val selectedItem = binding.authenticationTypeEdit.text
            val selectedIndex = items.indexOfFirst { TextUtils.equals(it, selectedItem) }
            return AuthenticationType.entries[selectedIndex]
        }
        set(value) {
            val adapter = binding.authenticationTypeEdit.adapter
            val item = adapter.getItem(value.ordinal) as CharSequence
            binding.authenticationTypeEdit.setText(item, false)
            onAuthenticationTypeChanged(value)
        }

    private fun onAuthenticationTypeChanged(authenticationType: AuthenticationType) {
        binding.passwordLayout.isVisible = authenticationType == AuthenticationType.PASSWORD
        binding.publicKeyAuthenticationLayout.isVisible =
            authenticationType == AuthenticationType.PUBLIC_KEY
    }

    private fun onOpenPrivateKeyFile() {
        if (!viewModel.readPrivateKeyFileState.value.isReady) {
            return
        }
        openPrivateKeyFileLauncher.launchSafe(listOf(MimeType.ANY), this)
    }

    private fun onOpenPrivateKeyFileResult(result: AppPath?) {
        val path = result?.toLegacyPathOrNull() ?: return
        viewModel.readPrivateKeyFile(path)
    }

    private fun onReadPrivateKeyFileStateChanged(state: ActionState<Path, String>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> {
                val isReading = state is ActionState.Running
                binding.privateKeyLayout.placeholderText =
                    if (isReading) getString(R.string.loading) else null
                if (isReading) {
                    binding.privateKeyEdit.text = null
                }
            }
            is ActionState.Success -> {
                binding.privateKeyEdit.setText(state.result)
                viewModel.finishReadingPrivateKeyFile()
            }
            is ActionState.Error -> {
                val throwable = state.throwable
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", throwable)
                showToast(throwable.toString())
                viewModel.finishReadingPrivateKeyFile()
            }
        }
    }

    private fun saveOrAdd() {
        val server = getServerOrSetError() ?: return
        Storages.addOrReplace(server)
        finish()
    }

    private fun connectAndAdd() {
        if (!viewModel.connectState.value.isReady) {
            return
        }
        val server = getServerOrSetError() ?: return
        viewModel.connect(server)
    }

    private fun onConnectStateChanged(state: ActionState<SftpServer, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> {
                val isConnecting = state is ActionState.Running
                binding.progress.fadeToVisibilityUnsafe(isConnecting)
                binding.scrollView.fadeToVisibilityUnsafe(!isConnecting)
                binding.saveOrConnectAndAddButton.isEnabled = !isConnecting
                binding.removeOrAddButton.isEnabled = !isConnecting
            }
            is ActionState.Success -> {
                Storages.addOrReplace(state.argument)
                finish()
            }
            is ActionState.Error -> {
                val throwable = state.throwable
                when (val hostKeyException = throwable.findSftpHostKeyVerificationException()) {
                    is SftpUnknownHostKeyException -> {
                        showUnknownHostKeyDialog(state.argument, hostKeyException)
                    }
                    is SftpHostKeyMismatchException -> {
                        showHostKeyMismatchDialog(hostKeyException)
                    }
                    else -> {
                        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", throwable)
                        showToast(getString(R.string.storage_edit_sftp_server_connect_error_generic))
                    }
                }
                viewModel.finishConnecting()
            }
        }
    }

    private fun showUnknownHostKeyDialog(server: SftpServer, exception: SftpUnknownHostKeyException) {
        val canTrust = exception.presentedEncodedKeyBase64.isNotBlank()
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.storage_edit_sftp_server_host_key_unknown_title)
            .setMessage(
                getString(
                    R.string.storage_edit_sftp_server_host_key_unknown_message,
                    exception.host,
                    exception.port,
                    exception.algorithm,
                    exception.presentedSha256Fingerprint
                )
            )
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                showToast(getString(R.string.storage_edit_sftp_server_host_key_not_trusted))
            }
        if (!canTrust) {
            builder.setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val hostKey = SftpPresentedHostKey(
            algorithm = exception.algorithm,
            encodedKeyBase64 = exception.presentedEncodedKeyBase64,
            sha256Fingerprint = exception.presentedSha256Fingerprint
        )
        builder
            .setNeutralButton(R.string.storage_edit_sftp_server_host_key_trust_once) { _, _ ->
                if (viewModel.connectState.value.isReady) {
                    viewModel.trustHostKeyOnceAndConnect(server = server, hostKey = hostKey)
                }
            }
            .setPositiveButton(R.string.storage_edit_sftp_server_host_key_trust_and_save) { _, _ ->
                if (viewModel.connectState.value.isReady) {
                    viewModel.trustHostKeyAndSaveAndConnect(server = server, hostKey = hostKey)
                }
            }
            .show()
    }

    private fun showHostKeyMismatchDialog(exception: SftpHostKeyMismatchException) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.storage_edit_sftp_server_host_key_mismatch_title)
            .setMessage(
                getString(
                    R.string.storage_edit_sftp_server_host_key_mismatch_message,
                    exception.host,
                    exception.port,
                    exception.algorithm,
                    exception.expectedSha256Fingerprint,
                    exception.presentedSha256Fingerprint
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun Throwable.findSftpHostKeyVerificationException(): SftpHostKeyVerificationException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is SftpHostKeyVerificationException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun remove() {
        Storages.remove(args.server!!)
        finish()
    }

    private fun getServerOrSetError(): SftpServer? {
        var errorEdit: TextInputEditText? = null
        val host = binding.hostEdit.text.toString().takeIfNotEmpty()
            ?.let { URI::class.canonicalizeHost(it) }
        if (host == null) {
            binding.hostLayout.error =
                getString(R.string.storage_edit_sftp_server_host_error_empty)
            if (errorEdit == null) {
                errorEdit = binding.hostEdit
            }
        } else if (!URI::class.isValidHost(host)) {
            binding.hostLayout.error =
                getString(R.string.storage_edit_sftp_server_host_error_invalid)
            if (errorEdit == null) {
                errorEdit = binding.hostEdit
            }
        }
        val port = binding.portEdit.text.toString().takeIfNotEmpty()
            .let { if (it != null) it.toIntOrNull() else Authority.DEFAULT_PORT }
        if (port == null) {
            binding.portLayout.error = getString(R.string.storage_edit_sftp_server_port_error_invalid)
            if (errorEdit == null) {
                errorEdit = binding.portEdit
            }
        }
        val path = binding.pathEdit.text.toString().trim()
        val name = binding.nameEdit.text.toString().takeIfNotEmpty()
        val username = binding.usernameEdit.text.toString().takeIfNotEmpty()
        if (username == null) {
            binding.usernameLayout.error =
                getString(R.string.storage_edit_sftp_server_username_error_empty)
            if (errorEdit == null) {
                errorEdit = binding.usernameEdit
            }
        }
        val authentication = when (authenticationType) {
            AuthenticationType.PASSWORD -> {
                val password = binding.passwordEdit.text.toString()
                PasswordAuthentication(password)
            }
            AuthenticationType.PUBLIC_KEY -> {
                val privateKey = binding.privateKeyEdit.text.toString().takeIfNotEmpty()
                val privateKeyPassword =
                    binding.privateKeyPasswordEdit.text.toString().takeIfNotEmpty()
                if (privateKey == null) {
                    binding.privateKeyLayout.error =
                        getString(R.string.storage_edit_sftp_server_private_key_error_empty)
                    if (errorEdit == null) {
                        errorEdit = binding.privateKeyEdit
                    }
                } else {
                    val exception = PublicKeyAuthentication.validate(privateKey, privateKeyPassword)
                    if (exception != null) {
                        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", exception)
                        if (exception is KeyDecryptionFailedException) {
                            binding.privateKeyPasswordLayout.error = getString(
                                R.string.storage_edit_sftp_server_private_key_password_error_invalid
                            )
                            if (errorEdit == null) {
                                errorEdit = binding.privateKeyPasswordEdit
                            }
                        } else {
                            binding.privateKeyLayout.error = getString(
                                R.string.storage_edit_sftp_server_private_key_error_invalid
                            )
                            if (errorEdit == null) {
                                errorEdit = binding.privateKeyEdit
                            }
                        }
                    }
                }
                if (errorEdit == null) {
                    PublicKeyAuthentication(privateKey!!, privateKeyPassword)
                } else {
                    null
                }
            }
        }
        if (errorEdit != null) {
            errorEdit.requestFocus()
            return null
        }
        val authority = Authority(host!!, port!!, username!!)
        return SftpServer(args.server?.id, name, authority, authentication!!, path)
    }

    @Parcelize
    class Args(val server: SftpServer? = null) : ParcelableArgs

    private enum class AuthenticationType {
        PASSWORD,
        PUBLIC_KEY
    }
}
