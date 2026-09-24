package com.wisso.wizefiles.storage

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.files.uri.asExternalStorageUri
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.provider.document.resolver.ExternalStorageProviderHacks
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.finish
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.startActivitySafe

class AddStorageDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val entries = buildStorageEntries()
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.storage_add_storage_title)
            .apply {
                setAdapter(StorageEntryAdapter(requireContext(), entries)) { _, which ->
                    val entry = entries[which]
                    if (entry.isRemote && !Storages.canAddRemoteStorage()) {
                        ensureProAccess(ProFeature.UNLIMITED_REMOTE_CONNECTIONS)
                        return@setAdapter
                    }
                    startActivitySafe(entry.intent)
                    finish()
                }
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    private fun buildStorageEntries(): List<StorageEntry> = buildList {
        add(
            StorageEntry(
                getString(R.string.storage_add_storage_external_drive),
                R.drawable.ic_sd_card_white_24dp,
                AddDocumentTreeActivity::class.createIntent()
                    .putArgs(AddDocumentTreeActivity.Args())
            )
        )
        add(
            StorageEntry(
                getString(R.string.storage_add_storage_rclone),
                R.drawable.ic_cloud_white_24dp,
                EditRcloneStorageActivity::class.createIntent()
                    .putArgs(EditRcloneStorageFragment.Args()),
                isRemote = true
            )
        )
        add(
            StorageEntry(
                getString(R.string.storage_add_storage_saf_folder),
                R.drawable.ic_shared_directory_white_24dp,
                AddSafProviderDialogActivity::class.createIntent()
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(
                StorageEntry(
                    getString(R.string.storage_add_storage_android_data),
                    R.drawable.ic_android_white_24dp,
                    AddExternalStorageShortcutActivity::class.createIntent().putArgs(
                        AddExternalStorageShortcutFragment.Args(
                            R.string.storage_add_storage_android_data,
                            ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_DATA.asExternalStorageUri()
                        )
                    )
                )
            )
            add(
                StorageEntry(
                    getString(R.string.storage_add_storage_android_obb),
                    R.drawable.ic_android_white_24dp,
                    AddExternalStorageShortcutActivity::class.createIntent().putArgs(
                        AddExternalStorageShortcutFragment.Args(
                            R.string.storage_add_storage_android_obb,
                            ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_OBB.asExternalStorageUri()
                        )
                    )
                )
            )
        }
        add(
            StorageEntry(
                getString(R.string.storage_add_storage_ftp_server),
                R.drawable.ic_ftp_white_24dp,
                EditFtpServerSettingsActivity::class.createIntent()
                    .putArgs(EditFtpServerScreenFragment.Args()),
                isRemote = true
            )
        )
        add(
            StorageEntry(
                getString(R.string.storage_add_storage_sftp_server),
                R.drawable.ic_lock_white_24dp,
                EditSftpServerActivity::class.createIntent()
                    .putArgs(EditSftpServerFragment.Args()),
                isRemote = true
            )
        )
        add(
            StorageEntry(
                getString(R.string.storage_add_storage_smb_server),
                R.drawable.ic_computer_white_24dp,
                AddLanSmbServerActivity::class.createIntent(),
                isRemote = true
            )
        )
    }

    private data class StorageEntry(
        val title: CharSequence,
        @DrawableRes val iconRes: Int,
        val intent: Intent,
        val isRemote: Boolean = false
    )

    private class StorageEntryAdapter(
        context: Context,
        entries: List<StorageEntry>
    ) : ArrayAdapter<StorageEntry>(context, R.layout.item_add_storage, entries) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_add_storage, parent, false)
            val entry = getItem(position) ?: return view
            view.findViewById<ImageView>(R.id.iconImage).setImageResource(entry.iconRes)
            view.findViewById<TextView>(R.id.titleText).text = entry.title
            return view
        }
    }
}
