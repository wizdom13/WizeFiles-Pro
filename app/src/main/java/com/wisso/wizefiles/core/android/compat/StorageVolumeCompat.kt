package com.wisso.wizefiles.core.android.compat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import java.io.File

val StorageVolume.pathFileCompat: File?
    get() = resolveStorageVolumePathFileCompat(
        directoryProvider = { directory },
        primaryDirectoryProvider = {
            if (isPrimaryCompat) {
                @Suppress("DEPRECATION")
                Environment.getExternalStorageDirectory()
            } else {
                null
            }
        }
    )

val StorageVolume.pathCompat: String?
    get() = pathFileCompat?.path

val StorageVolume.directoryCompat: File?
    get() = runCatching { directory }.getOrNull()

@SuppressLint("NewApi")
fun StorageVolume.getDescriptionCompat(context: Context): String = getDescription(context)

val StorageVolume.isPrimaryCompat: Boolean
    @SuppressLint("NewApi")
    get() = isPrimary

val StorageVolume.isRemovableCompat: Boolean
    @SuppressLint("NewApi")
    get() = isRemovable

val StorageVolume.isEmulatedCompat: Boolean
    @SuppressLint("NewApi")
    get() = isEmulated

val StorageVolume.uuidCompat: String?
    @SuppressLint("NewApi")
    get() = uuid

val StorageVolume.stateCompat: String
    @SuppressLint("NewApi")
    get() = state

fun StorageVolume.createOpenDocumentTreeIntentCompat(): Intent =
    createOpenDocumentTreeIntent()

internal fun resolveStorageVolumePathFileCompat(
    directoryProvider: () -> File?,
    primaryDirectoryProvider: () -> File?
): File? =
    runCatching(directoryProvider).getOrNull()
        ?: runCatching(primaryDirectoryProvider).getOrNull()
