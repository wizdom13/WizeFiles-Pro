// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.DialogFilePropertiesBinding
import com.wisso.wizefiles.databinding.ItemFilePropertiesSectionBinding
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.filebrowser.name
import com.wisso.wizefiles.feature.details.apk.FilePropertiesApkTabFragment
import com.wisso.wizefiles.feature.details.audio.FilePropertiesAudioTabFragment
import com.wisso.wizefiles.feature.details.basic.FilePropertiesBasicTabFragment
import com.wisso.wizefiles.feature.details.checksum.FilePropertiesChecksumTabFragment
import com.wisso.wizefiles.feature.details.image.FilePropertiesImageTabFragment
import com.wisso.wizefiles.feature.details.permission.FilePropertiesPermissionTabFragment
import com.wisso.wizefiles.feature.details.video.FilePropertiesVideoTabFragment
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.args
import com.wisso.wizefiles.util.layoutInflater
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.show
import com.wisso.wizefiles.util.viewModels
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull

class FilePropertiesDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { FilePropertiesFileViewModel(args.file) } }

    private lateinit var binding: DialogFilePropertiesBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_WizeFiles_FileProperties_Dialog
        )
            .apply {
                binding = DialogFilePropertiesBinding.inflate(context.layoutInflater)
                binding.titleText.text =
                    getString(R.string.file_properties_title_format, args.file.name)
                binding.okButton.setOnClickListener { dismiss() }
                setView(binding.root)
            }
            .create()

    // HACK: Work around child FragmentManager requiring a view.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the shared file view model before child fragments are created.
        viewModel.fileLiveData
        val legacyPath = args.file.path.toLegacyPathOrNull()
        val sections = mutableListOf<Pair<Int, () -> Fragment>>()
            .apply {
                add(R.string.file_properties_basic to { FilePropertiesBasicTabFragment() })
                if (FilePropertiesPermissionTabFragment.isAvailable(args.file)) {
                    add(
                        R.string.file_properties_permission
                            to { FilePropertiesPermissionTabFragment() }
                    )
                }
                if (legacyPath != null && FilePropertiesImageTabFragment.isAvailable(args.file)) {
                    add(
                        R.string.file_properties_image to {
                            FilePropertiesImageTabFragment().putArgs(
                                FilePropertiesImageTabFragment.Args(
                                    // Details tab contract still expects legacy Path.
                                    legacyPath, args.file.mimeType
                                )
                            )
                        }
                    )
                }
                if (legacyPath != null && FilePropertiesAudioTabFragment.isAvailable(args.file)) {
                    add(
                        R.string.file_properties_audio to {
                            FilePropertiesAudioTabFragment().putArgs(
                                FilePropertiesAudioTabFragment.Args(legacyPath)
                            )
                        }
                    )
                }
                if (legacyPath != null && FilePropertiesVideoTabFragment.isAvailable(args.file)) {
                    add(
                        R.string.file_properties_video to {
                            FilePropertiesVideoTabFragment().putArgs(
                                FilePropertiesVideoTabFragment.Args(legacyPath)
                            )
                        }
                    )
                }
                if (legacyPath != null && FilePropertiesApkTabFragment.isAvailable(args.file)) {
                    add(
                        R.string.file_properties_apk to {
                            FilePropertiesApkTabFragment().putArgs(
                                FilePropertiesApkTabFragment.Args(legacyPath)
                            )
                        }
                    )
                }
                if (legacyPath != null && FilePropertiesChecksumTabFragment.isAvailable(args.file)) {
                    add(
                        R.string.file_properties_checksum to {
                            FilePropertiesChecksumTabFragment().putArgs(
                                FilePropertiesChecksumTabFragment.Args(legacyPath)
                            )
                        }
                    )
                }
            }
        val sectionContainerIds = intArrayOf(
            R.id.file_properties_section_container_0,
            R.id.file_properties_section_container_1,
            R.id.file_properties_section_container_2,
            R.id.file_properties_section_container_3,
            R.id.file_properties_section_container_4,
            R.id.file_properties_section_container_5,
            R.id.file_properties_section_container_6
        )
        binding.sectionsContainer.removeAllViews()
        sections.forEachIndexed { index, section ->
            val sectionBinding = ItemFilePropertiesSectionBinding.inflate(layoutInflater)
            sectionBinding.titleText.text = getString(section.first)
            sectionBinding.fragmentContainer.id = sectionContainerIds[index]
            binding.sectionsContainer.addView(sectionBinding.root)
            if (childFragmentManager.findFragmentById(sectionContainerIds[index]) == null) {
                childFragmentManager.beginTransaction()
                    .replace(sectionContainerIds[index], section.second())
                    .commitNow()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // AlertDialog (its AlertController) adds FLAG_ALT_FOCUSABLE_IM when the initial custom
        // view doesn't have any view that returns true for onCheckIsTextEditor().
        requireDialog().window!!.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            FilePropertiesDialogFragment().putArgs(Args(file)).show(fragment)
        }

        fun show(file: FileItem, activity: FragmentActivity) {
            FilePropertiesDialogFragment()
                .putArgs(Args(file))
                .show(activity.supportFragmentManager, null)
        }
    }

    @Parcelize
    class Args(val file: FileItem): ParcelableArgs
}
