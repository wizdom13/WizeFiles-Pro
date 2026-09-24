// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.core.os.BundleCompat

class SettingsRegressionSourceTest {

    @Test
    fun `secret setting avoids constructor-time virtual dispatch pattern`() {
        val settingLiveDatas =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingLiveDatas.kt")
        val secretSetting = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SecretSettingLiveData.kt")
        val settingsKt = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/Settings.kt")

        assertTrue(settingLiveDatas.contains("class StringSettingLiveData("))
        assertFalse(settingLiveDatas.contains("open class StringSettingLiveData("))
        assertTrue(secretSetting.contains(") : SettingLiveData<String>(keyRes, defaultValueRes)"))
        assertFalse(secretSetting.contains(": StringSettingLiveData("))

        val keyDeclarationIndex = secretSetting.indexOf("private val secretKey")
        val initCallIndex = secretSetting.indexOf("init()")
        assertTrue(keyDeclarationIndex in 0 until initCallIndex)
        assertTrue(secretSetting.contains("secretStoreProvider: () -> SecretStore = { globalSecretStore }"))
        assertFalse(secretSetting.contains("private val secretStore: SecretStore = globalSecretStore"))
        assertTrue(secretSetting.contains("val secretStore = secretStoreProvider()"))
        assertTrue(settingsKt.contains("val STORAGES: SettingLiveData<List<Storage>> by lazy"))
        assertTrue(settingsKt.contains("val FILE_LIST_DEFAULT_DIRECTORY: SettingLiveData<Path> by lazy"))
        assertTrue(settingsKt.contains("val FILE_LIST_VIEW_TYPE: SettingLiveData<FileViewType> by lazy"))
        assertTrue(settingsKt.contains("val FILE_LIST_SORT_OPTIONS: SettingLiveData<FileSortOptions> by lazy"))
        assertTrue(settingsKt.contains("Environment.getExternalStorageDirectory()?.absolutePath"))
        val eagerSettingDeclarationRegex =
            Regex("""val\s+\w+:\s+SettingLiveData<[^>]+>\s*=\s*""")
        assertFalse(eagerSettingDeclarationRegex.containsMatchIn(settingsKt))
    }

    @Test
    fun `startup settings initialization path still touches settings live data`() {
        val appInitializers = sourceFile("src/main/java/com/wisso/wizefiles/core/app/AppInitializerRegistry.kt")

        assertTrue(appInitializers.contains("Settings.FILE_LIST_DEFAULT_DIRECTORY.value"))
    }

    @Test
    fun `parcel setting read path does not write back through putValue`() {
        val settingLiveDatas = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingLiveDatas.kt")
        val getValueStart = settingLiveDatas.indexOf("override fun getValue(")
        val parcelClassStart = settingLiveDatas.indexOf("class ParcelValueSettingLiveData")
        val decodeCurrentSortOptionsStart = settingLiveDatas.indexOf("private fun decodeCurrentFileSortOptions(")
        val decodeStableSortOptionsStart = settingLiveDatas.indexOf(
            "private fun decodeStableFileSortOptions(",
            startIndex = decodeCurrentSortOptionsStart
        )
        val putValueStart = settingLiveDatas.indexOf("override fun putValue", startIndex = parcelClassStart)
        val getValueBody = settingLiveDatas.substring(getValueStart, putValueStart)
        val decodeCurrentSortOptionsBody = settingLiveDatas.substring(
            decodeCurrentSortOptionsStart,
            decodeStableSortOptionsStart
        )

        assertFalse(getValueBody.contains("putValue(sharedPreferences, key, value)"))
        assertFalse(decodeCurrentSortOptionsBody.contains("toRawParcelValue()"))
        assertTrue(settingLiveDatas.contains("private val secretStoreProvider: () -> SecretStore = { globalSecretStore }"))
        assertTrue(settingLiveDatas.contains("secretStoreProvider = secretStoreProvider"))
        assertTrue(settingLiveDatas.contains("decodeSortOptionsFromStringScan"))
        assertTrue(settingLiveDatas.contains("normalized.contains(it.name)"))
    }

    @Test
    fun `recycle bin preference is wired in settings and resources`() {
        val settingsKt = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/Settings.kt")
        val settingsXml = sourceFile("src/main/res/xml/settings.xml")
        val prefsXml = sourceFile("src/main/res/values/donottranslate_strings.xml")
        val navItemsKt = sourceFile("src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt")

        assertTrue(settingsKt.contains("val RECYCLE_BIN: SettingLiveData<Boolean>"))
        assertTrue(settingsXml.contains("@string/pref_key_recycle_bin"))
        assertTrue(prefsXml.contains("name=\"pref_key_recycle_bin\""))
        assertTrue(navItemsKt.contains("Settings.RECYCLE_BIN.valueCompat"))
    }

    @Test
    fun `settings root forwards navigation bar inset to outer scroller`() {
        val settingsFragment =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsFragment.kt")
        val settingsPreferenceFragment =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsPreferenceFragment.kt")

        assertTrue(
            settingsFragment.contains("ViewCompat.setOnApplyWindowInsetsListener(root)")
        )
        assertTrue(
            settingsFragment.contains("initialScrollPaddingBottom + navigationBars.bottom")
        )
        assertTrue(
            settingsFragment.contains("WindowInsetsCompat.Type.navigationBars()")
        )
        assertFalse(
            settingsFragment.contains("root.updatePadding(")
        )
        assertFalse(
            settingsFragment.contains(
                "binding.settingsScrollView.applyInsetPadding(applyBottom = true)"
            )
        )
        assertFalse(
            settingsPreferenceFragment.contains(
                "bottom = initialBottomPadding + systemBarsInsets.bottom"
            )
        )
        assertTrue(settingsPreferenceFragment.contains("clipToPadding = false"))
    }

    @Test
    fun `open source licenses uses checked in offline html resource instead of Gradle generation`() {
        val appBuildGradle = sourceFile("build.gradle")
        val licensesActivity =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/about/OpenSourceLicensesActivity.kt")
        val offlineHtml = sourceFile("src/main/res/raw/open_libs.html")

        assertFalse(appBuildGradle.contains("com.google.android.gms.oss-licenses-plugin"))
        assertFalse(appBuildGradle.contains("GenerateOpenSourceLicensesHtmlTask"))
        assertFalse(appBuildGradle.contains("generated/third_party_licenses/"))
        assertFalse(appBuildGradle.contains("generated/open_source_licenses/"))

        assertTrue(licensesActivity.contains("R.raw.open_libs"))
        assertTrue(offlineHtml.contains("<!DOCTYPE html>"))
        assertTrue(offlineHtml.contains("<title>Open Source Licenses</title>"))
    }

    @Test
    fun `open source licenses offline html keeps vendored libarchive entries`() {
        val licensesActivity =
            sourceFile("src/main/java/com/wisso/wizefiles/feature/about/OpenSourceLicensesActivity.kt")
        val offlineHtml = sourceFile("src/main/res/raw/open_libs.html")
        val wrapperLicense = sourceFile("src/main/licenses/android-libarchive-wrapper-apache-2.0.txt")
        val libarchiveLicense = sourceFile("src/main/licenses/libarchive-new-bsd-license.txt")

        assertTrue(licensesActivity.contains("R.raw.open_libs"))
        assertTrue(offlineHtml.contains("vendored:android-libarchive-wrapper:local"))
        assertTrue(offlineHtml.contains("app/src/main/licenses/android-libarchive-wrapper-apache-2.0.txt"))
        assertTrue(offlineHtml.contains("vendored:libarchive:local"))
        assertTrue(offlineHtml.contains("app/src/main/licenses/libarchive-new-bsd-license.txt"))
        assertTrue(offlineHtml.contains("New BSD License"))

        assertTrue(wrapperLicense.contains("Apache License"))
        assertTrue(libarchiveLicense.contains("New BSD License"))
    }


    @Test
    fun `backup and restore preferences are wired and browse flow keeps dialog open`() {
        val settingsXml = sourceFile("src/main/res/xml/settings.xml")
        val prefsXml = sourceFile("src/main/res/values/donottranslate_strings.xml")
        val stringsXml = sourceFile("src/main/res/values/strings.xml")
        val backupDialog = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/BackupSettingsDialogFragment.kt")
        val restoreDialog = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/RestoreSettingsDialogFragment.kt")
        val backupDialogLayout = sourceFile("src/main/res/layout/dialog_settings_backup.xml")
        val restoreDialogLayout = sourceFile("src/main/res/layout/dialog_settings_restore.xml")

        assertTrue(settingsXml.contains("@string/settings_backup_restore_title"))
        assertTrue(settingsXml.contains("@string/pref_key_backup_settings"))
        assertTrue(settingsXml.contains("@string/pref_key_restore_settings"))
        assertTrue(prefsXml.contains("name=\"pref_key_backup_settings\""))
        assertTrue(prefsXml.contains("name=\"pref_key_restore_settings\""))
        assertTrue(settingsXml.contains("@string/settings_backup_settings_summary"))
        assertTrue(settingsXml.contains("@string/settings_restore_settings_summary"))
        assertTrue(stringsXml.contains("name=\"settings_backup_settings_summary\""))
        assertTrue(stringsXml.contains("name=\"settings_restore_settings_summary\""))
        assertTrue(stringsXml.contains("name=\"settings_backup_encrypt_backup\""))
        assertTrue(backupDialog.contains("setPositiveButton(android.R.string.ok, null)"))
        assertTrue(backupDialog.contains("setNeutralButton(R.string.share, null)"))
        assertTrue(backupDialog.contains("BUTTON_POSITIVE"))
        assertTrue(backupDialog.contains("BUTTON_NEUTRAL"))
        assertTrue(backupDialog.contains("R.layout.dialog_settings_backup"))
        assertTrue(backupDialog.contains("R.id.backup_file_name_input"))
        assertTrue(backupDialog.contains("R.id.backup_encrypt_backup"))
        assertTrue(backupDialog.contains("R.id.backup_password_input"))
        assertTrue(backupDialog.contains("R.id.backup_confirm_password_input"))
        assertTrue(backupDialog.contains("R.id.backup_show_password"))
        assertTrue(backupDialog.contains("settings_security_password_required"))
        assertTrue(backupDialog.contains("settings_security_password_mismatch"))
        assertTrue(backupDialog.contains("backupActionForTest?.invoke(fileName, encrypt"))
        assertTrue(restoreDialog.contains("R.layout.dialog_settings_restore"))
        assertTrue(restoreDialog.contains("R.id.restore_file_path_input"))
        assertTrue(restoreDialog.contains("R.id.restore_password_input"))
        assertTrue(restoreDialog.contains("R.id.restore_show_password"))
        assertTrue(restoreDialog.contains("createRestoreBackupPickerContract()"))
        assertTrue(restoreDialog.contains("manager.inspectBackupEncryption(backup.uri)"))
        assertTrue(restoreDialog.contains("manager.inspectBackupEncryption(backup.path)"))
        assertTrue(restoreDialog.contains("encryptionInfo.isEncrypted -> !passwordInput.text.isNullOrBlank()"))
        assertTrue(backupDialogLayout.contains("@dimen/screen_edge_margin"))
        assertTrue(restoreDialogLayout.contains("@dimen/screen_edge_margin"))
        assertTrue(restoreDialog.contains("fun show(fragment: SettingsPreferenceFragment, initialUri: Uri? = null)"))
        assertTrue(restoreDialog.contains("putParcelable(ARG_INITIAL_URI, initialUri)"))
        assertTrue(restoreDialog.contains("BundleCompat.getParcelable"))
        assertTrue(restoreDialog.contains("ARG_INITIAL_URI"))
        assertTrue(restoreDialog.contains("Uri::class.java"))
        assertTrue(restoreDialog.contains("setPositiveButton(android.R.string.ok, null)"))
        assertTrue(restoreDialog.contains("setNeutralButton(R.string.settings_restore_settings_browse, null)"))
        assertTrue(restoreDialog.contains("BUTTON_POSITIVE"))
        assertTrue(restoreDialog.contains("setOnClickListener { restoreSettings() }"))
        assertTrue(restoreDialog.contains("BUTTON_NEUTRAL"))
        assertTrue(restoreDialog.contains("setOnClickListener { browseFile() }"))
    }


    @Test
    fun `settings activity handles external wzf view intents for restore flow`() {
        val manifest = sourceFile("src/main/AndroidManifest.xml")
        val externalViewRouter = sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/ExternalViewRouterActivity.kt")
        val settingsActivity = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsActivity.kt")
        val settingsBackupViewIntent = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsBackupViewIntent.kt")
        val settingsFragment = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsFragment.kt")
        val settingsPreferenceFragment = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsPreferenceFragment.kt")

        assertTrue(manifest.contains("com.wisso.wizefiles.settings.SettingsActivity"))
        assertTrue(manifest.contains("com.wisso.wizefiles.feature.filebrowser.ExternalViewRouterActivity"))
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android:pathPattern=\".*\\.wzf\""))
        assertTrue(externalViewRouter.contains("ExternalViewTarget.SETTINGS_RESTORE"))
        assertTrue(externalViewRouter.contains("SettingsActivity::class.java"))
        assertTrue(settingsActivity.contains("pendingRestoreUri = intent.restoreSettingsBackupUri"))
        assertTrue(settingsActivity.contains("showPendingRestoreDialogIfNeeded()"))
        assertTrue(settingsActivity.contains("SettingsBackupViewIntent.findBackupUri"))
        assertTrue(settingsBackupViewIntent.contains("OpenableColumns.DISPLAY_NAME"))
        assertTrue(settingsBackupViewIntent.contains("URLDecoder.decode"))
        assertTrue(settingsFragment.contains("fun showRestoreSettingsDialog(initialUri: Uri): Boolean"))
        assertTrue(settingsPreferenceFragment.contains("fun showRestoreSettingsDialog(initialUri: Uri? = null)"))
    }

    @Test
    fun `settings xml keys are covered by backup registry except explicit exclusions`() {
        val settingsXml = sourceFile("src/main/res/xml/settings.xml")
        val backupStore = settingsBackupSources()

        val xmlKeys = extractPreferenceKeys(settingsXml)
        val explicitExclusions = setOf(
            "pref_key_backup_settings",
            "pref_key_restore_settings",
            "pref_key_storages",
            "pref_key_security_password",
            "pref_key_search_index_status"
        )
        val registryKeys = Regex("""R\.string\.(pref_key_[a-z0-9_]+)""")
            .findAll(
                backupStore.substring(
                    backupStore.indexOf("object SettingsBackupRegistry"),
                    backupStore.length
                )
            )
            .map { it.groupValues[1] }
            .toSet()

        val missing = xmlKeys.filterNot { it in registryKeys || it in explicitExclusions }
        assertTrue("Missing backup registry coverage for $missing", missing.isEmpty())
    }

    @Test
    fun `settings backup excludes storages and normalizes file sort options`() {
        val backupStore = settingsBackupSources()

        assertFalse(backupStore.contains("R.string.pref_key_storages"))
        assertTrue(backupStore.contains("TYPE_FILE_SORT_OPTIONS_V2"))
        assertTrue(backupStore.contains("SettingsBackupEncryptionInfo"))
        assertTrue(backupStore.contains("inspectBackupEncryption"))
        assertTrue(backupStore.contains("serializeFileSortOptions"))
        assertTrue(backupStore.contains("restoreFileSortOptions"))
        assertTrue(backupStore.contains("putString(key, restoreFileSortOptions(value))"))
    }


    @Test
    fun `security settings are wired in xml resources and fragment`() {
        val settingsXml = sourceFile("src/main/res/xml/settings.xml")
        val prefsXml = sourceFile("src/main/res/values/donottranslate_strings.xml")
        val stringsXml = sourceFile("src/main/res/values/strings.xml")
        val settingsFragment = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsPreferenceFragment.kt")

        assertTrue(settingsXml.contains("@string/settings_security_title"))
        assertTrue(settingsXml.contains("@string/pref_key_security_password"))
        assertTrue(settingsXml.contains("@string/pref_key_protect_browser"))
        assertTrue(settingsXml.contains("@string/pref_key_enable_biometric"))
        assertTrue(settingsXml.contains("@string/pref_key_time_to_relock"))
        assertTrue(settingsXml.contains("@array/settings_security_relock_entries"))
        assertTrue(settingsXml.contains("@array/pref_entry_values_security_relock"))

        assertTrue(prefsXml.contains("name=\"pref_key_security_password\""))
        assertTrue(prefsXml.contains("name=\"pref_key_protect_browser\""))
        assertTrue(prefsXml.contains("name=\"pref_key_enable_biometric\""))
        assertTrue(prefsXml.contains("name=\"pref_key_time_to_relock\""))

        assertTrue(stringsXml.contains("name=\"settings_security_password_title\""))
        assertTrue(stringsXml.contains("name=\"settings_security_time_to_relock_title\""))
        assertTrue(settingsFragment.contains("showSecurityPasswordDialog"))
        assertTrue(settingsFragment.contains("settings_security_confirm_password"))
    }

    @Test
    fun `backup registry includes non-secret security preferences only`() {
        val backupStore = settingsBackupSources()
        val registrySection = backupStore.substring(
            backupStore.indexOf("object SettingsBackupRegistry"),
            backupStore.length
        )

        assertTrue(registrySection.contains("R.string.pref_key_protect_browser"))
        assertTrue(registrySection.contains("R.string.pref_key_enable_biometric"))
        assertTrue(registrySection.contains("R.string.pref_key_time_to_relock"))
        assertFalse(registrySection.contains("R.string.pref_key_security_password"))
    }

    @Test
    fun `restore disables security toggles when password secret is missing`() {
        val backupStore = settingsBackupSources()

        assertTrue(backupStore.contains("enforceSecurityProtectionPrerequisites()"))
        assertTrue(backupStore.contains("AppSecurityManager.create(context, defaultSharedPreferences, secretStore).hasPassword()"))
        assertTrue(backupStore.contains("PBKDF2WithHmacSHA256"))
        assertTrue(backupStore.contains("AES/GCM/NoPadding"))
        assertTrue(backupStore.contains("pref_key_protect_browser"))
        assertTrue(backupStore.contains("pref_key_enable_biometric"))
        assertTrue(backupStore.contains("putBoolean(protectBrowserKey, false)"))
        assertTrue(backupStore.contains("putBoolean(enableBiometricKey, false)"))
        assertFalse(backupStore.contains("putString(timeToRelockKey"))
    }

    @Test
    fun `security toggles require password setup before enabling`() {
        val settingsFragment = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsPreferenceFragment.kt")

        assertTrue(settingsFragment.contains("setSecurityToggleListener(protectBrowserPreference, Settings.PROTECT_BROWSER)"))
        assertTrue(settingsFragment.contains("setSecurityToggleListener(enableBiometricPreference, Settings.ENABLE_BIOMETRIC)"))
        assertTrue(settingsFragment.contains("if (!shouldEnable || appSecurityManager.hasPassword())"))
        assertTrue(settingsFragment.contains("showSecurityPasswordDialog { success ->"))
        assertTrue(settingsFragment.contains("setting.putValue(success)"))
        assertTrue(settingsFragment.contains("preference.isChecked = success"))
    }

    private fun settingsBackupSources(): String =
        listOf(
            "SettingsBackupRestoreManager.kt",
            "SettingsBackupModels.kt",
            "SettingsBackupSerializer.kt",
            "SettingsBackupStore.kt",
            "SettingsRestoreValidator.kt"
        ).joinToString("\n") { fileName ->
            sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/$fileName")
        }

    private fun extractPreferenceKeys(xml: String): Set<String> =
        Regex("""android:key="@string/(pref_key_[a-z0-9_]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toSet()

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) {
            return direct.readText()
        }
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) {
            return fromRepoRoot.readText()
        }
        throw java.io.FileNotFoundException(path)
    }
    @Test
    fun `file list view sort settings are committed synchronously`() {
        val settingLiveDatas = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingLiveDatas.kt")

        assertTrue(settingLiveDatas.contains("private fun shouldCommitSynchronously(key: String): Boolean"))
        assertTrue(settingLiveDatas.contains("sharedPreferences.edit(commit = shouldCommitSynchronously(key))"))
    }

}
