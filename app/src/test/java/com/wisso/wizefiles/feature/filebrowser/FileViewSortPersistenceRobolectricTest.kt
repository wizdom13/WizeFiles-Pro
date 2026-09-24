package com.wisso.wizefiles.feature.filebrowser

import android.app.Application
import android.content.SharedPreferences
import android.os.Environment
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowEnvironment
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.PreferenceManagerCompat
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import com.wisso.wizefiles.settings.EnumSettingLiveData
import com.wisso.wizefiles.settings.ParcelValueSettingLiveData
import com.wisso.wizefiles.settings.PathSettings
import com.wisso.wizefiles.settings.Settings

@RunWith(RobolectricTestRunner::class)
class FileViewSortPersistenceRobolectricTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var application: Application
    private lateinit var defaultSharedPreferences: SharedPreferences
    private lateinit var pathSharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        ShadowEnvironment.setExternalStorageDirectory(File("/storage/emulated/0").toPath())
        application = RuntimeEnvironment.getApplication()
        setGlobalApplicationForTests(application)
        clearPreferences(application)
        defaultSharedPreferences =
            application.getSharedPreferences(
                PreferenceManagerCompat.getDefaultSharedPreferencesName(application),
                PreferenceManagerCompat.defaultSharedPreferencesMode
            )
        pathSharedPreferences =
            application.getSharedPreferences(
                "${PreferenceManagerCompat.getDefaultSharedPreferencesName(application)}_path",
                PreferenceManagerCompat.defaultSharedPreferencesMode
            )
    }

    @Test
    fun globalViewAndSortSurviveSaveAndRestartWhenPathSpecificIsOff() {
        val path = Paths.get("/storage/emulated/0/Download")
        val expectedViewType = FileViewType.GRID
        val expectedSortOptions = FileSortOptions(
            by = FileSortOptions.By.SIZE,
            order = FileSortOptions.Order.DESCENDING,
            isDirectoriesFirst = false
        )

        val keyViewType = application.getString(R.string.pref_key_file_list_view_type)
        val keySortOptions = application.getString(R.string.pref_key_file_list_sort_options)
        val keyPathSpecific = application.getString(R.string.pref_key_file_list_view_sort_path_specific)
        val pathSuffix = path.toString()

        // This mirrors the effective global write target used when "Only for this folder" is OFF.
        PathSettings.getFileListViewSortPathSpecific(path).putValue(false)
        Settings.FILE_LIST_VIEW_TYPE.putValue(expectedViewType)
        Settings.FILE_LIST_SORT_OPTIONS.putValue(expectedSortOptions)

        // Prove exact SharedPreferences destination and keys written before process exit.
        assertEquals(expectedViewType.ordinal.toString(), defaultSharedPreferences.getString(keyViewType, null))
        assertNotNull(defaultSharedPreferences.getString(keySortOptions, null))
        assertFalse(pathSharedPreferences.getBoolean("${keyPathSpecific}_${pathSuffix}", true))

        val globalViewReader = EnumSettingLiveData(
            R.string.pref_key_file_list_view_type,
            R.string.pref_default_value_file_list_view_type,
            FileViewType::class.java
        )
        val globalSortReader = ParcelValueSettingLiveData(
            R.string.pref_key_file_list_sort_options,
            FileSortOptions(FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true)
        )
        assertEquals(expectedViewType, globalViewReader.value)
        assertEquals(expectedSortOptions, globalSortReader.value)

        // Simulate cold start/readers after process recreation.
        val restartedPathLiveData = MutableLiveData(path)
        val restartedViewTypeLiveData = FileViewTypeLiveData(restartedPathLiveData)
        val restartedSortOptionsLiveData = FileSortOptionsLiveData(restartedPathLiveData)
        val restartedPathSpecificLiveData = FileViewSortPathSpecificLiveData(restartedPathLiveData)
        val restartedViewObserver = Observer<FileViewType> {}
        val restartedSortObserver = Observer<FileSortOptions> {}
        val restartedPathSpecificObserver = Observer<Boolean> {}
        restartedViewTypeLiveData.observeForever(restartedViewObserver)
        restartedSortOptionsLiveData.observeForever(restartedSortObserver)
        restartedPathSpecificLiveData.observeForever(restartedPathSpecificObserver)
        try {
            assertFalse(restartedPathSpecificLiveData.value == true)
            assertEquals(expectedViewType, restartedViewTypeLiveData.value)
            assertEquals(expectedSortOptions, restartedSortOptionsLiveData.value)
        } finally {
            restartedViewTypeLiveData.removeObserver(restartedViewObserver)
            restartedSortOptionsLiveData.removeObserver(restartedSortObserver)
            restartedPathSpecificLiveData.removeObserver(restartedPathSpecificObserver)
        }

        // Prove a subsequent read cycle still preserves the persisted global values.
        val subsequentViewReader = EnumSettingLiveData(
            R.string.pref_key_file_list_view_type,
            R.string.pref_default_value_file_list_view_type,
            FileViewType::class.java
        )
        val subsequentSortReader = ParcelValueSettingLiveData(
            R.string.pref_key_file_list_sort_options,
            FileSortOptions(FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true)
        )
        assertEquals(expectedViewType, subsequentViewReader.value)
        assertEquals(expectedSortOptions, subsequentSortReader.value)
    }

    @Test
    fun pathSpecificValuesRemainScopedAndGlobalValuesRemainAvailable() {
        val globalViewWriter = EnumSettingLiveData(
            R.string.pref_key_file_list_view_type,
            R.string.pref_default_value_file_list_view_type,
            FileViewType::class.java
        )
        val globalSortWriter = ParcelValueSettingLiveData(
            R.string.pref_key_file_list_sort_options,
            FileSortOptions(FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true)
        )

        val globalView = FileViewType.GRID
        val globalSort = FileSortOptions(
            by = FileSortOptions.By.SIZE,
            order = FileSortOptions.Order.DESCENDING,
            isDirectoriesFirst = false
        )
        globalViewWriter.putValue(globalView)
        globalSortWriter.putValue(globalSort)

        val folderA = Paths.get("/storage/emulated/0/Download")
        val folderB = Paths.get("/storage/emulated/0/Documents")
        val folderAView = FileViewType.LIST
        val folderASort = FileSortOptions(
            by = FileSortOptions.By.LAST_MODIFIED,
            order = FileSortOptions.Order.ASCENDING,
            isDirectoriesFirst = true
        )
        PathSettings.getFileListViewSortPathSpecific(folderA).putValue(true)
        PathSettings.getFileListViewType(folderA).putValue(folderAView)
        PathSettings.getFileListSortOptions(folderA).putValue(folderASort)

        assertTrue(PathSettings.getFileListViewSortPathSpecific(folderA).value == true)
        assertFalse(PathSettings.getFileListViewSortPathSpecific(folderB).value == true)

        val pathLiveData = MutableLiveData(folderA)
        val viewTypeLiveData = FileViewTypeLiveData(pathLiveData)
        val sortOptionsLiveData = FileSortOptionsLiveData(pathLiveData)
        val pathSpecificLiveData = FileViewSortPathSpecificLiveData(pathLiveData)
        val viewObserver = Observer<FileViewType> {}
        val sortObserver = Observer<FileSortOptions> {}
        val pathSpecificObserver = Observer<Boolean> {}
        viewTypeLiveData.observeForever(viewObserver)
        sortOptionsLiveData.observeForever(sortObserver)
        pathSpecificLiveData.observeForever(pathSpecificObserver)
        try {
            assertTrue(pathSpecificLiveData.value == true)
            assertEquals(folderAView, viewTypeLiveData.value)
            assertEquals(folderASort, sortOptionsLiveData.value)

            pathLiveData.value = folderB
            assertFalse(pathSpecificLiveData.value == true)
            assertEquals(globalView, viewTypeLiveData.value)
            assertEquals(globalSort, sortOptionsLiveData.value)
        } finally {
            viewTypeLiveData.removeObserver(viewObserver)
            sortOptionsLiveData.removeObserver(sortObserver)
            pathSpecificLiveData.removeObserver(pathSpecificObserver)
        }
    }

    @Test
    fun stalePathOverridesDoNotReEnablePathSpecificModeWhenExplicitFlagIsOff() {
        val path = Paths.get("/storage/emulated/0/Download")
        val globalView = FileViewType.GRID
        val globalSort = FileSortOptions(
            by = FileSortOptions.By.SIZE,
            order = FileSortOptions.Order.DESCENDING,
            isDirectoriesFirst = false
        )
        Settings.FILE_LIST_VIEW_TYPE.putValue(globalView)
        Settings.FILE_LIST_SORT_OPTIONS.putValue(globalSort)

        // Stale path overrides exist, but explicit path-specific mode remains OFF.
        val stalePathView = FileViewType.LIST
        val stalePathSort = FileSortOptions(
            by = FileSortOptions.By.LAST_MODIFIED,
            order = FileSortOptions.Order.ASCENDING,
            isDirectoriesFirst = true
        )
        PathSettings.getFileListViewSortPathSpecific(path).putValue(false)
        PathSettings.getFileListViewType(path).putValue(stalePathView)
        PathSettings.getFileListSortOptions(path).putValue(stalePathSort)

        val restartedPathLiveData = MutableLiveData(path)
        val restartedViewTypeLiveData = FileViewTypeLiveData(restartedPathLiveData)
        val restartedSortOptionsLiveData = FileSortOptionsLiveData(restartedPathLiveData)
        val restartedPathSpecificLiveData = FileViewSortPathSpecificLiveData(restartedPathLiveData)
        val restartedViewObserver = Observer<FileViewType> {}
        val restartedSortObserver = Observer<FileSortOptions> {}
        val restartedPathSpecificObserver = Observer<Boolean> {}
        restartedViewTypeLiveData.observeForever(restartedViewObserver)
        restartedSortOptionsLiveData.observeForever(restartedSortObserver)
        restartedPathSpecificLiveData.observeForever(restartedPathSpecificObserver)
        try {
            assertFalse(restartedPathSpecificLiveData.value == true)
            assertEquals(globalView, restartedViewTypeLiveData.value)
            assertEquals(globalSort, restartedSortOptionsLiveData.value)
        } finally {
            restartedViewTypeLiveData.removeObserver(restartedViewObserver)
            restartedSortOptionsLiveData.removeObserver(restartedSortObserver)
            restartedPathSpecificLiveData.removeObserver(restartedPathSpecificObserver)
        }
    }

    @Test
    fun gridOverridesAreAvailableWhenViewTypeEmitsFirstAtStartup() {
        val path = Paths.get("/storage/emulated/0/Download")
        val expected = GridColumnOverrides(compact = 3, wide = 5)
        Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.putValue(expected)

        val viewModel = FileListViewModel()
        viewModel.resetTo(path)
        val viewTypeObserver = Observer<FileViewType> {}
        viewModel.viewTypeLiveData.observeForever(viewTypeObserver)
        try {
            assertEquals(expected, viewModel.gridColumnOverrides)
        } finally {
            viewModel.viewTypeLiveData.removeObserver(viewTypeObserver)
        }
    }

    @Test
    fun viewTypeIsAvailableWhenGridOverridesEmitFirstAtStartup() {
        val path = Paths.get("/storage/emulated/0/Documents")
        val expected = FileViewType.GRID
        Settings.FILE_LIST_VIEW_TYPE.putValue(expected)
        PathSettings.getFileListViewSortPathSpecific(path).putValue(false)

        val viewModel = FileListViewModel()
        viewModel.resetTo(path)
        val gridObserver = Observer<GridColumnOverrides> {
            assertEquals(expected, viewModel.viewType)
        }
        viewModel.gridColumnOverridesLiveData.observeForever(gridObserver)
        try {
            assertEquals(expected, viewModel.viewType)
        } finally {
            viewModel.gridColumnOverridesLiveData.removeObserver(gridObserver)
        }
    }

    private fun clearPreferences(application: Application) {
        val defaultName = PreferenceManagerCompat.getDefaultSharedPreferencesName(application)
        application.getSharedPreferences(defaultName, PreferenceManagerCompat.defaultSharedPreferencesMode)
            .edit()
            .clear()
            .commit()
        application.getSharedPreferences("${defaultName}_path", PreferenceManagerCompat.defaultSharedPreferencesMode)
            .edit()
            .clear()
            .commit()
    }

}
