package com.wisso.wizefiles.util

import android.content.Intent
import android.net.Uri
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.core.android.compat.DocumentsContractCompat
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toUriString

private const val EXTRA_PATH_URI = "${BuildConfig.APPLICATION_ID}.extra.PATH_URI"

var Intent.extraPath: AppPath?
    get() {
        val extraPathUri = getStringExtra(EXTRA_PATH_URI)
        extraPathUri?.let { return it.toAppPathOrNull() }
        data?.toAppPathOrNull()?.let { return it }
        val extraInitialUri = getParcelableExtraSafe<Uri>(DocumentsContractCompat.EXTRA_INITIAL_URI)
        extraInitialUri?.toAppPathOrNull()?.let { return it }
        val extraAbsolutePath = getStringExtra("org.openintents.extra.ABSOLUTE_PATH")
            ?.takeIfNotEmpty()
        extraAbsolutePath?.let { return it.toAppPathOrNull() }
        return null
    }
    set(value) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        // We cannot put URI into intent here either, because ShortcutInfo uses PersistableBundle
        // which doesn't support Serializable.
        putExtra(EXTRA_PATH_URI, value?.toUriString())
    }

val Intent.saveAsPath: AppPath?
    get() {
        val uri =
            when (action) {
                Intent.ACTION_VIEW -> data
                Intent.ACTION_SEND -> getParcelableExtraSafe<Uri>(Intent.EXTRA_STREAM)
                else -> null
            }
        return uri?.toAppPathOrNull()
    }

private const val EXTRA_PATH_URI_LIST = "${BuildConfig.APPLICATION_ID}.extra.PATH_URI_LIST"

var Intent.extraPathList: List<AppPath>
    get() {
        val extraPathUris = extras?.requireSerializableList(EXTRA_PATH_URI_LIST, String::class.java)
            ?.takeIfNotEmpty()
        extraPathUris?.let { return it.mapNotNull(String::toAppPathOrNull) }
        return listOfNotNull(extraPath)
    }
    set(value) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        val pathUris = value.map { it.toUriString() }
        putExtra(EXTRA_PATH_URI_LIST, ArrayList(pathUris))
    }
