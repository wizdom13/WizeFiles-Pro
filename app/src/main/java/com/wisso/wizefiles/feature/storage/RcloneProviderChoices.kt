// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.provider.rclone.RcloneProviderDefinition

internal data class ProviderChoice(
    val title: String,
    val backendType: String?,
    val definition: RcloneProviderDefinition? = null,
    val isImport: Boolean = false,
    val isOther: Boolean = false
) {
    val key: String
        get() = when {
            isOther -> OTHER_CLOUD_PROVIDERS_KEY
            isImport -> IMPORT_PROVIDER_KEY
            else -> checkNotNull(backendType)
        }
}

internal class ProviderChoiceAdapter(
    context: Context,
    providerChoices: List<ProviderChoice>
) : ArrayAdapter<ProviderChoice>(
    context,
    R.layout.item_add_storage,
    providerChoices
) {
    private val unfilteredFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence): FilterResults = FilterResults()

        override fun publishResults(
            constraint: CharSequence,
            results: FilterResults
        ) = Unit

        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? ProviderChoice)?.title ?: resultValue?.toString().orEmpty()
    }

    override fun getFilter(): Filter = unfilteredFilter

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_add_storage, parent, false)
        val choice = getItem(position) ?: return view
        val iconRes = when {
            choice.isOther -> R.drawable.ic_more_horizontal_white_24dp
            choice.isImport -> R.drawable.ic_download_white_24dp
            else -> rcloneProviderIconRes(choice.backendType.orEmpty())
        }
        view.findViewById<ImageView>(R.id.iconImage).setImageResource(iconRes)
        view.findViewById<TextView>(R.id.titleText).text = choice.title
        return view
    }
}

internal fun rcloneProviderTitle(
    context: Context,
    definition: RcloneProviderDefinition
): String = when (definition.backendType) {
    "drive" -> context.getString(R.string.rclone_provider_google_drive)
    "onedrive" -> context.getString(R.string.rclone_provider_onedrive)
    "dropbox" -> context.getString(R.string.rclone_provider_dropbox)
    "box" -> context.getString(R.string.rclone_provider_box)
    "pcloud" -> context.getString(R.string.rclone_provider_pcloud)
    "mega" -> context.getString(R.string.rclone_provider_mega)
    "webdav" -> context.getString(R.string.rclone_provider_webdav)
    "s3" -> context.getString(R.string.rclone_provider_s3)
    else -> definition.description
}

internal fun fallbackRcloneProviderChoices(context: Context): List<ProviderChoice> = listOf(
    ProviderChoice(context.getString(R.string.rclone_provider_google_drive), "drive"),
    ProviderChoice(context.getString(R.string.rclone_provider_onedrive), "onedrive"),
    ProviderChoice(context.getString(R.string.rclone_provider_dropbox), "dropbox"),
    ProviderChoice(context.getString(R.string.rclone_provider_box), "box"),
    ProviderChoice(context.getString(R.string.rclone_provider_pcloud), "pcloud"),
    ProviderChoice(context.getString(R.string.rclone_provider_mega), "mega"),
    ProviderChoice(context.getString(R.string.rclone_provider_webdav), "webdav"),
    ProviderChoice(context.getString(R.string.rclone_provider_s3), "s3"),
    ProviderChoice(
        context.getString(R.string.rclone_provider_import),
        null,
        isImport = true
    )
)

private const val IMPORT_PROVIDER_KEY = "__import__"
