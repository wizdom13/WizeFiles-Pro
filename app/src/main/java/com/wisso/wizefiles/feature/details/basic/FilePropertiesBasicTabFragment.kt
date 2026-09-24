package com.wisso.wizefiles.feature.details.basic

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.model.asFileSize
import com.wisso.wizefiles.core.files.extensions.fileSize
import com.wisso.wizefiles.core.files.extensions.formatLong
import com.wisso.wizefiles.feature.filebrowser.getMimeTypeName
import com.wisso.wizefiles.feature.filebrowser.name
import com.wisso.wizefiles.feature.filebrowser.toUserFriendlyString
import com.wisso.wizefiles.feature.details.FilePropertiesFileViewModel
import com.wisso.wizefiles.feature.details.FilePropertiesTabFragment
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.getQuantityString
import com.wisso.wizefiles.util.viewModels
import java.io.IOException

class FilePropertiesBasicTabFragment : FilePropertiesTabFragment() {
    private val viewModel by viewModels<FilePropertiesFileViewModel>({ requireParentFragment() })

    private var contentJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.fileLiveData.observe(viewLifecycleOwner) { onFileChanged(it) }
    }

    override fun refresh() {
        viewModel.reload()
    }

    private fun onFileChanged(stateful: Stateful<FileItem>) {
        contentJob?.cancel()
        contentJob = null
        bindView(stateful) { file ->
            addItemView(R.string.file_properties_basic_name, file.name)
            val path = file.path
            val legacyPath = path.toLegacyPathOrNull()
            if (legacyPath?.isArchivePath == true) {
                val archiveFile = legacyPath.archiveFile
                val archiveFileText =
                    archiveFile.toLegacyPathOrNull()?.toUserFriendlyString() ?: archiveFile.rawPath
                addItemView(
                    R.string.file_properties_basic_archive_file, archiveFileText
                )
                val entryName = legacyPath.toString().replace('\\', '/').trim('/')
                addItemView(R.string.file_properties_basic_archive_entry, entryName)
            } else {
                val parentPath = legacyPath?.parent
                if (parentPath != null) {
                    addItemView(
                        R.string.file_properties_basic_parent_directory, parentPath.toString()
                    )
                }
            }
            addItemView(R.string.file_properties_basic_type, getTypeText(file))
            val symbolicLinkTarget = file.symbolicLinkTarget
            if (symbolicLinkTarget != null) {
                addItemView(R.string.file_properties_basic_symbolic_link_target, symbolicLinkTarget)
            }
            if (file.attributes.isDirectory) {
                val textView = addItemView(
                    R.string.file_properties_basic_contents, getDirectoryContentsText(0, 0)
                )
                if (legacyPath != null) {
                    contentJob = viewLifecycleOwner.lifecycleScope.launch {
                        getDirectoryContents(
                            legacyPath,
                            GET_DIRECTORY_CONTENTS_INTERVAL_MILLIS
                        ) { (count, size) -> textView.text = getDirectoryContentsText(count, size) }
                    }
                } else {
                    textView.text = getString(R.string.unknown)
                }
            } else {
                addItemView(R.string.file_properties_basic_size, getSizeText(file))
            }
            val lastModificationTime = file.attributes.lastModifiedTime().toInstant().formatLong()
            addItemView(R.string.file_properties_basic_last_modification_time, lastModificationTime)
        }
    }

    private fun getTypeText(file: FileItem): String {
        val typeFormatRes = if (file.attributesNoFollowLinks.isSymbolicLink
            && !file.isSymbolicLinkBroken) {
            R.string.file_properties_basic_type_symbolic_link_format
        } else {
            R.string.file_properties_basic_type_format
        }
        return getString(typeFormatRes, file.getMimeTypeName(requireContext()), file.mimeType.value)
    }

    private suspend fun getDirectoryContents(
        directory: Path,
        intervalMillis: Long,
        listener: (DirectoryContentsSnapshot) -> Unit
    ) = coroutineScope {
        val updates = Channel<DirectoryContentsSnapshot>(Channel.CONFLATED)
        val reportingJob = launch(Dispatchers.Main.immediate) {
            for (snapshot in updates) listener(snapshot)
        }
        try {
            withContext(Dispatchers.IO) {
                val progress = DirectoryContentsProgress(intervalMillis)
                Files.walkFileTree(directory, object : FileVisitor<Path> {
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes
                    ): FileVisitResult = visit(directory, attributes, null)

                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes
                    ): FileVisitResult = visit(file, attributes, null)

                    override fun visitFileFailed(
                        file: Path,
                        exception: IOException
                    ): FileVisitResult = visit(file, null, exception)

                    override fun postVisitDirectory(
                        directory: Path,
                        exception: IOException?
                    ): FileVisitResult = visit(null, null, exception)

                    private fun visit(
                        path: Path?,
                        attributes: BasicFileAttributes?,
                        exception: IOException?
                    ): FileVisitResult {
                        if (!isActive) return FileVisitResult.TERMINATE
                        if (path == directory) return FileVisitResult.CONTINUE
                        exception?.let {
                            com.wisso.wizefiles.util.AppLog.e(
                                "Error",
                                "Unexpected failure",
                                it
                            )
                        }
                        progress.record(
                            includePath = path != null,
                            byteSize = attributes?.size()
                        )?.let(updates::trySend)
                        return FileVisitResult.CONTINUE
                    }
                })
                updates.send(progress.snapshot())
            }
        } finally {
            updates.close()
            reportingJob.join()
        }
    }

    private fun getDirectoryContentsText(count: Int, size: Long): String =
        if (count == 0) {
            getString(R.string.empty)
        } else {
            val fileSize = size.asFileSize()
            val context = requireContext()
            val sizeText = if (fileSize.isHumanReadableInBytes) {
                fileSize.formatInBytes(context)
            } else {
                fileSize.formatHumanReadable(context)
            }
            getQuantityString(
                R.plurals.file_properties_basic_contents_format, count, count, sizeText
            )
        }

    private fun getSizeText(file: FileItem): String {
        val size = file.attributes.fileSize
        val context = requireContext()
        val sizeInBytes = size.formatInBytes(context)
        return if (size.isHumanReadableInBytes) {
            sizeInBytes
        } else {
            val humanReadableSize = size.formatHumanReadable(context)
            getString(
                R.string.file_properties_basic_size_with_human_readable_format, humanReadableSize,
                sizeInBytes
            )
        }
    }

    companion object {
        private const val GET_DIRECTORY_CONTENTS_INTERVAL_MILLIS = 200L
    }
}
