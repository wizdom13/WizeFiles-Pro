// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.app.Application
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.PreferenceManagerCompat
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.util.valueCompat
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingLiveDataRegressionTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var application: Application
    private lateinit var defaultSharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        setGlobalApplicationForTests(application)
        defaultSharedPreferences =
            application.getSharedPreferences(
                PreferenceManagerCompat.getDefaultSharedPreferencesName(application),
                PreferenceManagerCompat.defaultSharedPreferencesMode
            )
        defaultSharedPreferences.edit().clear().commit()
    }

    @Test
    fun archiveFileNameEncodingInitializationFromBackgroundThreadDoesNotCrash() {
        val liveDataRef = AtomicReference<StringSettingLiveData>()
        val failureRef = AtomicReference<Throwable?>()

        val thread = Thread {
            runCatching {
                StringSettingLiveData(
                    R.string.pref_key_archive_file_name_encoding,
                    R.string.pref_default_value_archive_file_name_encoding
                )
            }.onSuccess {
                liveDataRef.set(it)
            }.onFailure {
                failureRef.set(it)
            }
        }
        thread.start()
        thread.join()

        assertNull(failureRef.get())
        assertEquals(
            application.getString(R.string.pref_default_value_archive_file_name_encoding),
            liveDataRef.get().valueCompat
        )
    }

    @Test
    fun fileListSortOptionsUsesDefaultForPlainLegacyTokenPayload() {
        val key = application.getString(R.string.pref_key_file_list_sort_options)
        defaultSharedPreferences.edit().putString(key, "NAME").commit()
        val defaultSortOptions = FileSortOptions(
            by = FileSortOptions.By.SIZE,
            order = FileSortOptions.Order.DESCENDING,
            isDirectoriesFirst = false
        )

        val liveData = ParcelValueSettingLiveData(
            R.string.pref_key_file_list_sort_options,
            defaultSortOptions
        )

        assertEquals(defaultSortOptions, liveData.value)
    }

    @Test
    fun putValueUpdatesInMemoryStateImmediatelyOnMainThread() {
        val liveData = BooleanSettingLiveData(
            R.string.pref_key_file_list_show_hidden_files,
            R.bool.pref_default_value_file_list_show_hidden_files
        )

        liveData.putValue(true)

        assertTrue(liveData.currentValueCompat() == true)
        assertTrue(liveData.valueCompat)
    }

    @Test
    fun putValueUpdatesInMemoryStateImmediatelyOnBackgroundThread() {
        val liveData = BooleanSettingLiveData(
            R.string.pref_key_file_list_show_hidden_files,
            R.bool.pref_default_value_file_list_show_hidden_files
        )
        liveData.putValue(false)
        assertFalse(liveData.valueCompat)

        val thread = Thread { liveData.putValue(true) }
        thread.start()
        thread.join()

        assertTrue(liveData.currentValueCompat() == true)
        assertTrue(liveData.valueCompat)
    }
}
