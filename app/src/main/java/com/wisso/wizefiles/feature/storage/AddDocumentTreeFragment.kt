package com.wisso.wizefiles.storage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.uri.DocumentTreeUri
import com.wisso.wizefiles.core.files.uri.asDocumentTreeUriOrNull
import com.wisso.wizefiles.core.files.uri.takePersistablePermission
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.getArgsOrNull
import com.wisso.wizefiles.util.launchSafe
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.valueCompat

class AddDocumentTreeFragment : Fragment() {
    private val args: AddDocumentTreeActivity.Args by lazy {
        arguments?.getArgsOrNull(AddDocumentTreeActivity.Args::class) ?: AddDocumentTreeActivity.Args()
    }
    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val treeUri = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.asDocumentTreeUriOrNull()
        } else {
            null
        }
        if (treeUri != null) {
            addDocumentTree(treeUri)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val initialUri = args.initialUriString?.let(Uri::parse)
                ?: InstalledSafProviders.resolveInitialUri(requireContext(), args.providerAuthority)
            if (args.providerAuthority != null && initialUri == null) {
                showToast(
                    getString(
                        R.string.storage_select_saf_provider_manual_pick_hint,
                        args.providerLabel ?: getString(R.string.storage_add_storage_saf_folder)
                    )
                )
            }
            openDocumentTreeLauncher.launchSafe(createOpenDocumentTreeIntent(initialUri), this)
        }
    }

    private fun createOpenDocumentTreeIntent(initialUri: Uri?): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            if (initialUri != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
        }
    }

    private fun addDocumentTree(treeUri: DocumentTreeUri) {
        treeUri.takePersistablePermission()
        val existingTree = Settings.STORAGES.valueCompat
            .filterIsInstance<DocumentTree>()
            .firstOrNull { it.uri == treeUri }
        val suggestedCustomName = InstalledSafProviders.buildSuggestedCustomName(
            requireContext(),
            treeUri,
            args.providerLabel
        )
        val documentTree = when {
            existingTree == null -> DocumentTree(null, suggestedCustomName, treeUri)
            existingTree.customName == null && suggestedCustomName != null ->
                existingTree.copy(customName = suggestedCustomName)
            else -> existingTree
        }
        Storages.addOrReplace(documentTree)
    }
}
