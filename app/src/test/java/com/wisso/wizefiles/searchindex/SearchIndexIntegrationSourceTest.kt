// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.searchindex

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexIntegrationSourceTest {
    @Test
    fun `search uses indexed pages and retains live provider fallback`() {
        val search = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/SearchFileListLiveData.kt"
        ).readText()
        val viewModel = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListViewModel.kt"
        ).readText()
        val searchController = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListSearchController.kt"
        ).readText()
        val fragment = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ).readText()
        val adapter = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt"
        ).readText()

        assertTrue(search.contains("SearchIndexManager.search("))
        assertTrue(search.contains("private const val PAGE_SIZE = 200"))
        assertTrue(search.contains("searchLive(normalizedQuery, loadGeneration)"))
        assertTrue(search.contains("path.search(normalizedQuery"))
        assertTrue(viewModel.contains("loadMoreSearchResults"))
        assertTrue(fragment.contains("adapter.itemCount - 30"))
        assertTrue(searchController.contains("DebouncedRunnable(Handler(Looper.getMainLooper()), 150)"))
        assertTrue(searchController.contains("query.trim().length >= MINIMUM_QUERY_LENGTH"))
        assertTrue(adapter.contains("legacyPath?.parent?.toUserFriendlyString()"))
    }

    @Test
    fun `index is generation safe private and excludes unsupported scopes`() {
        val database = File(
            "src/main/java/com/wisso/wizefiles/searchindex/SearchIndexDatabase.kt"
        ).readText()
        val worker = File(
            "src/main/java/com/wisso/wizefiles/searchindex/SearchIndexWorker.kt"
        ).readText()
        val manager = File(
            "src/main/java/com/wisso/wizefiles/searchindex/SearchIndexManager.kt"
        ).readText()

        assertTrue(database.contains("application.getDatabasePath(DATABASE_NAME)"))
        assertTrue(database.contains("scan_generation<>?"))
        assertTrue(database.contains("tokenize='trigram case_sensitive 0'"))
        assertTrue(worker.contains("attributes.isSymbolicLink"))
        assertTrue(worker.contains("RecycleBinManager.recycleBinRootPath"))
        assertTrue(worker.contains("markUnavailableRootsExcept"))
        assertTrue(manager.contains("!path.isArchivePath"))
        assertTrue(manager.contains("!path.isRclonePath"))
        assertTrue(manager.contains("!path.isSftpPath"))
        assertTrue(manager.contains("registerContentObserver("))
        assertTrue(manager.contains("StorageVolumeListLiveData.observeForever"))
    }

    @Test
    fun `settings and successful file operations maintain the index`() {
        val settings = File("src/main/res/xml/settings.xml").readText()
        val initializer = File(
            "src/main/java/com/wisso/wizefiles/core/app/AppInitializerRegistry.kt"
        ).readText()
        val operations = File(
            "src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt"
        ).readText()
        val fileList = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListLiveData.kt"
        ).readText()
        val build = File("build.gradle").readText()

        assertTrue(settings.contains("pref_key_indexed_search"))
        assertTrue(settings.contains("pref_key_search_index_status"))
        assertTrue(initializer.contains("::initializeSearchIndex"))
        assertTrue(operations.contains("SearchIndexManager.scheduleRepairAfterFileOperation()"))
        assertTrue(fileList.contains("SearchIndexManager.reconcileDirectory(path, fileList)"))
        assertTrue(build.contains("androidx.sqlite:sqlite-bundled:2.7.0"))
        assertTrue(build.contains("androidx.work:work-runtime-ktx:2.11.2"))
    }
}
