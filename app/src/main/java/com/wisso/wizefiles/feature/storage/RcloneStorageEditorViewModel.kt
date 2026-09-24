package com.wisso.wizefiles.storage

import androidx.lifecycle.ViewModel

internal class RcloneStorageEditorViewModel : ViewModel() {
    var importedConfiguration: String? = null
    var importedRemotes: List<ImportedRemote> = emptyList()
    var allProviderChoices: List<ProviderChoice> = emptyList()
    var providersExpanded: Boolean = false
    var selectedProviderKey: String = RcloneProviderSchema.DEFAULT_PROVIDER_TYPE
    var suggestedAccountName: String? = null
}

internal object RcloneProviderSchema {
    const val DEFAULT_PROVIDER_TYPE = "drive"
    val unsupportedProviderTypes = setOf("local", "memory")

    fun visibleChoices(choices: List<ProviderChoice>, expanded: Boolean): List<String> =
        cloudProviderMenuKeys(choices.map(ProviderChoice::key), expanded)
}
