// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wisso.wizefiles.R
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBackupRestoreUiTest {

    @After
    fun tearDown() {
        BackupSettingsDialogFragment.managerFactoryForTest = null
        BackupSettingsDialogFragment.backupActionForTest = null
        RestoreSettingsDialogFragment.managerFactoryForTest = null
        RestoreSettingsDialogFragment.onBrowseForTest = null
        RestoreSettingsDialogFragment.backupEncryptionInfoForTest = null
        RestoreSettingsDialogFragment.previewForTest = null
        RestoreSettingsDialogFragment.applyPreviewForTest = null
        RestoreSettingsDialogFragment.resolveDisplayNameForTest = null
    }

    @Test
    fun settingsScreenExposesBackupAndRestorePreferences() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = requireSettingsPreferenceFragment(activity)
                val backup = requirePreference(fragment, R.string.pref_key_backup_settings)
                val restore = requirePreference(fragment, R.string.pref_key_restore_settings)
                assertEquals(activity.getString(R.string.settings_backup_settings_title), backup.title)
                assertEquals(activity.getString(R.string.settings_restore_settings_title), restore.title)
            }
        }
    }

    @Test
    fun backupDialogFlowShowsExpectedButtonsAndTriggersBackup() {
        val backupInvocations = AtomicInteger(0)
        val requestedEncryption = AtomicBoolean(false)
        val requestedPassword = AtomicReference<String?>()
        val backupCompleted = CountDownLatch(1)
        BackupSettingsDialogFragment.backupActionForTest = { fileName, encrypt, password ->
            backupInvocations.incrementAndGet()
            requestedEncryption.set(encrypt)
            requestedPassword.set(password?.concatToString())
            File.createTempFile(fileName.removeSuffix(".wzf"), ".wzf").also {
                backupCompleted.countDown()
            }
        }

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = requireSettingsPreferenceFragment(activity)
                requirePreference(fragment, R.string.pref_key_backup_settings).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val dialog = requireDialogFragment<BackupSettingsDialogFragment>(activity)
                val alertDialog = requireNotNull(dialog.dialog as? androidx.appcompat.app.AlertDialog)

                val fileNameInput = requireNotNull(alertDialog.findViewById<EditText>(R.id.backup_file_name_input))
                val passwordInput = requireNotNull(alertDialog.findViewById<EditText>(R.id.backup_password_input))
                val confirmInput = requireNotNull(alertDialog.findViewById<EditText>(R.id.backup_confirm_password_input))
                val positive = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                val neutral = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                assertTrue(fileNameInput.text.toString().endsWith(".wzf"))
                assertNotNull(positive)
                assertNotNull(neutral)

                passwordInput.setText("backup-pass")
                confirmInput.setText("backup-pass")
                positive.performClick()
            }

            assertTrue(
                "Backup action should complete",
                backupCompleted.await(5, TimeUnit.SECONDS)
            )
        }

        assertEquals(1, backupInvocations.get())
        assertTrue(requestedEncryption.get())
        assertEquals("backup-pass", requestedPassword.get())
    }

    @Test
    fun restoreDialogBrowseSelectionEnablesOkAndDoesNotDismiss() {
        val browseInvoked = AtomicBoolean(false)
        val inspectionCompleted = CountDownLatch(1)
        val previewCompleted = CountDownLatch(1)
        val previewInvocations = AtomicInteger(0)
        val previewPassword = AtomicReference<String?>()
        RestoreSettingsDialogFragment.onBrowseForTest = {
            browseInvoked.set(true)
            true
        }
        RestoreSettingsDialogFragment.backupEncryptionInfoForTest = {
            SettingsBackupEncryptionInfo(isEncrypted = false).also {
                inspectionCompleted.countDown()
            }
        }
        RestoreSettingsDialogFragment.resolveDisplayNameForTest = {
            "settings-backup.wzf"
        }
        RestoreSettingsDialogFragment.previewForTest = { _: Uri, password: CharArray? ->
            previewInvocations.incrementAndGet()
            previewPassword.set(password?.concatToString())
            previewCompleted.countDown()
            SettingsRestorePreview(2, emptyMap(), emptyMap(), emptyList())
        }

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = requireSettingsPreferenceFragment(activity)
                requirePreference(fragment, R.string.pref_key_restore_settings).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val dialog = requireDialogFragment<RestoreSettingsDialogFragment>(activity)
                val alertDialog = requireNotNull(dialog.dialog as? androidx.appcompat.app.AlertDialog)
                val ok = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                val browse = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)

                assertFalse(ok.isEnabled)
                browse.performClick()
                assertTrue(browseInvoked.get())
                assertTrue(alertDialog.isShowing)
                dialog.onFileSelected(Uri.parse("content://test/backup.wzf"))
            }

            assertTrue(
                "Backup inspection should complete",
                inspectionCompleted.await(5, TimeUnit.SECONDS)
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val dialog = requireDialogFragment<RestoreSettingsDialogFragment>(activity)
                val alertDialog = requireNotNull(dialog.dialog as? androidx.appcompat.app.AlertDialog)
                val ok = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                assertTrue(ok.isEnabled)
                ok.performClick()
            }

            assertTrue(
                "Restore preview should complete",
                previewCompleted.await(5, TimeUnit.SECONDS)
            )
        }

        assertEquals(1, previewInvocations.get())
        assertEquals(null, previewPassword.get())
    }

    @Test
    fun openingWzfIntentPrefillsRestoreDialogAndEnablesOkForPlainBackup() {
        val inspectionCompleted = CountDownLatch(1)
        val previewCompleted = CountDownLatch(1)
        val previewInvocations = AtomicInteger(0)
        val previewPassword = AtomicReference<String?>()
        RestoreSettingsDialogFragment.backupEncryptionInfoForTest = {
            SettingsBackupEncryptionInfo(isEncrypted = false).also {
                inspectionCompleted.countDown()
            }
        }
        RestoreSettingsDialogFragment.resolveDisplayNameForTest = {
            "settings-backup.wzf"
        }
        RestoreSettingsDialogFragment.previewForTest = { _: Uri, password: CharArray? ->
            previewInvocations.incrementAndGet()
            previewPassword.set(password?.concatToString())
            previewCompleted.countDown()
            SettingsRestorePreview(2, emptyMap(), emptyMap(), emptyList())
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backupUri = Uri.parse("content://test/settings-backup.wzf")
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(backupUri, "application/x-wizefiles-backup")
            .setClass(context, SettingsActivity::class.java)

        ActivityScenario.launch<SettingsActivity>(intent).use { scenario ->
            assertTrue(
                "Backup inspection should complete",
                inspectionCompleted.await(5, TimeUnit.SECONDS)
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val dialog = requireDialogFragment<RestoreSettingsDialogFragment>(activity)
                val alertDialog = requireNotNull(dialog.dialog as? androidx.appcompat.app.AlertDialog)
                val ok = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                val inputText = requireNotNull(alertDialog.findViewById<EditText>(R.id.restore_file_path_input)).text.toString()

                assertEquals("settings-backup.wzf", inputText)
                assertTrue(ok.isEnabled)
                ok.performClick()
            }

            assertTrue(
                "Restore preview should complete",
                previewCompleted.await(5, TimeUnit.SECONDS)
            )
        }

        assertEquals(1, previewInvocations.get())
        assertEquals(null, previewPassword.get())
    }

    @Test
    fun encryptedRestoreRequiresPasswordBeforeOkIsEnabled() {
        val inspectionCompleted = CountDownLatch(1)
        val previewCompleted = CountDownLatch(1)
        val previewInvocations = AtomicInteger(0)
        val previewPassword = AtomicReference<String?>()
        RestoreSettingsDialogFragment.backupEncryptionInfoForTest = {
            SettingsBackupEncryptionInfo(isEncrypted = true).also {
                inspectionCompleted.countDown()
            }
        }
        RestoreSettingsDialogFragment.previewForTest = { _: Uri, password: CharArray? ->
            previewInvocations.incrementAndGet()
            previewPassword.set(password?.concatToString())
            previewCompleted.countDown()
            SettingsRestorePreview(2, emptyMap(), emptyMap(), emptyList())
        }

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = requireSettingsPreferenceFragment(activity)
                requirePreference(fragment, R.string.pref_key_restore_settings).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val dialog = requireDialogFragment<RestoreSettingsDialogFragment>(activity)
                dialog.onFileSelected(Uri.parse("content://test/encrypted-backup.wzf"))
            }

            assertTrue(
                "Backup inspection should complete",
                inspectionCompleted.await(5, TimeUnit.SECONDS)
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val dialog = requireDialogFragment<RestoreSettingsDialogFragment>(activity)
                val alertDialog = requireNotNull(dialog.dialog as? androidx.appcompat.app.AlertDialog)
                val ok = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                val passwordInput = requireNotNull(alertDialog.findViewById<EditText>(R.id.restore_password_input))

                assertFalse(ok.isEnabled)
                passwordInput.setText("restore-pass")
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val dialog = requireDialogFragment<RestoreSettingsDialogFragment>(activity)
                val alertDialog = requireNotNull(dialog.dialog as? androidx.appcompat.app.AlertDialog)
                val ok = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                assertTrue(ok.isEnabled)
                ok.performClick()
            }

            assertTrue(
                "Restore preview should complete",
                previewCompleted.await(5, TimeUnit.SECONDS)
            )
        }

        assertEquals(1, previewInvocations.get())
        assertEquals("restore-pass", previewPassword.get())
    }

    @Test
    fun restoreAppliesValidBackupSkipsIncompatibleSettingsAndRejectsCorruptBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = SettingsBackupRestoreManager(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val keyNightMode = context.getString(R.string.pref_key_night_mode)
        val keyRecycleBin = context.getString(R.string.pref_key_recycle_bin)

        prefs.edit().putString(keyNightMode, "0").putBoolean(keyRecycleBin, true).apply()

        val validBackup = """
            {
              "formatVersion":2,
              "encrypted":false,
              "settings":{
                "$keyNightMode":{"type":"string","value":"2"},
                "$keyRecycleBin":{"type":"boolean","value":false}
              },
              "secretSettings":{}
            }
        """.trimIndent()
        val validFile = File(context.cacheDir, "valid-settings.wzf").apply { writeText(validBackup) }

        manager.restoreFromPath(validFile.absolutePath)
        assertEquals("2", prefs.getString(keyNightMode, null))
        assertFalse(prefs.getBoolean(keyRecycleBin, true))

        prefs.edit().putString(keyNightMode, "1").putBoolean(keyRecycleBin, true).apply()
        val compatibleSubsetBackup = """
            {
              "formatVersion":2,
              "encrypted":false,
              "settings":{
                "$keyNightMode":{"type":"string","value":"obsolete-mode"},
                "$keyRecycleBin":{"type":"boolean","value":false},
                "removed_preference":{"type":"string","value":"legacy-value"}
              },
              "secretSettings":{}
            }
        """.trimIndent()
        val compatibleSubsetFile = File(context.cacheDir, "compatible-subset-settings.wzf").apply {
            writeText(compatibleSubsetBackup)
        }

        manager.restoreFromPath(compatibleSubsetFile.absolutePath)
        assertEquals("1", prefs.getString(keyNightMode, null))
        assertFalse(prefs.getBoolean(keyRecycleBin, true))

        prefs.edit().putString(keyNightMode, "1").putBoolean(keyRecycleBin, true).apply()
        val invalidFile = File(context.cacheDir, "invalid-settings.wzf").apply { writeText("not-json") }

        runCatching { manager.restoreFromPath(invalidFile.absolutePath) }
        assertEquals("1", prefs.getString(keyNightMode, null))
        assertTrue(prefs.getBoolean(keyRecycleBin, false))
    }

    private inline fun <reified T : androidx.fragment.app.DialogFragment> requireDialogFragment(
        activity: SettingsActivity
    ): T {
        val fragmentManager = requireSettingsPreferenceFragment(activity).parentFragmentManager
        fragmentManager.executePendingTransactions()
        return requireNotNull(
            fragmentManager.fragments.filterIsInstance<T>().firstOrNull()
        )
    }

    private fun requireSettingsPreferenceFragment(activity: SettingsActivity): SettingsPreferenceFragment {
        val settingsHost = activity.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().first()
        val child = settingsHost.childFragmentManager.findFragmentById(R.id.preferenceFragment)
        return requireNotNull(child as? SettingsPreferenceFragment)
    }

    private fun requirePreference(fragment: SettingsPreferenceFragment, keyRes: Int): Preference {
        return requireNotNull(fragment.preferenceScreen.findPreference(fragment.getString(keyRes)))
    }
}
