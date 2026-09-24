package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Path
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.model.loadFileItem
import com.wisso.wizefiles.provider.common.search
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.util.CloseableLiveData
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.util.backgroundExecutor
import com.wisso.wizefiles.core.app.mainExecutor
import java.io.IOException
import java.util.concurrent.Future

class SearchFileListLiveData(
    private val path: Path,
    private val query: String
) : CloseableLiveData<Stateful<List<FileItem>>>() {
    private var future: Future<Unit>? = null
    private val indexedFiles = mutableListOf<FileItem>()
    private var indexedSearch = false
    private var hasMoreIndexedResults = false
    private val generation = SearchGeneration()
    private val publisher = LatestGenerationPublisher<Stateful<List<FileItem>>>(
        generation,
        mainExecutor
    ) { state -> value = state }

    init {
        loadValue()
    }

    fun loadValue() {
        val loadGeneration = generation.next()
        future?.cancel(true)
        indexedFiles.clear()
        indexedSearch = false
        hasMoreIndexedResults = false
        value = Loading(emptyList())
        future = backgroundExecutor.submit<Unit> {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) {
                postIfCurrent(loadGeneration, Success(emptyList()))
                return@submit
            }
            try {
                val firstPage = runCatching {
                    SearchIndexManager.search(
                        path = path,
                        query = normalizedQuery,
                        includeHidden = Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat,
                        limit = PAGE_SIZE,
                        offset = 0
                    )
                }.onFailure {
                    com.wisso.wizefiles.util.AppLog.e(
                        "SearchIndex",
                        "Indexed search failed; falling back to live search",
                        it
                    )
                }.getOrNull()
                if (firstPage != null) {
                    val accepted = generation.runIfCurrent(loadGeneration) {
                        indexedSearch = true
                        indexedFiles += firstPage
                        hasMoreIndexedResults = firstPage.size == PAGE_SIZE
                        publish(loadGeneration, Success(indexedFiles.toList()))
                    }
                    if (!accepted) return@submit
                } else {
                    searchLive(normalizedQuery, loadGeneration)
                }
            } catch (e: Exception) {
                if (isCurrent(loadGeneration)) {
                    postIfCurrent(loadGeneration, Failure(valueCompat.value, e))
                }
            }
        }
    }

    fun loadNextPage() {
        if (!indexedSearch || !hasMoreIndexedResults || future?.isDone == false) return
        val loadGeneration = generation.current()
        val offset = indexedFiles.size
        future = backgroundExecutor.submit<Unit> {
            try {
                val page = SearchIndexManager.search(
                    path = path,
                    query = query.trim(),
                    includeHidden = Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat,
                    limit = PAGE_SIZE,
                    offset = offset
                ) ?: return@submit
                generation.runIfCurrent(loadGeneration) {
                    if (offset != indexedFiles.size) return@runIfCurrent
                    indexedFiles += page
                    hasMoreIndexedResults = page.size == PAGE_SIZE
                    publish(loadGeneration, Success(indexedFiles.toList()))
                }
            } catch (exception: Exception) {
                postIfCurrent(loadGeneration, Failure(indexedFiles.toList(), exception))
            }
        }
    }

    private fun searchLive(normalizedQuery: String, loadGeneration: Long) {
        val fileList = mutableListOf<FileItem>()
        path.search(normalizedQuery, INTERVAL_MILLIS) { paths: List<Path> ->
            if (!isCurrent(loadGeneration)) return@search
            for (path in paths) {
                val fileItem = try {
                    path.toAppPath().loadFileItem()
                } catch (e: IOException) {
                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                    continue
                }
                fileList.add(fileItem)
            }
            postIfCurrent(loadGeneration, Loading(fileList.toList()))
        }
        postIfCurrent(loadGeneration, Success(fileList))
    }

    private fun isCurrent(loadGeneration: Long): Boolean =
        generation.isCurrent(loadGeneration) && !Thread.currentThread().isInterrupted

    private fun postIfCurrent(loadGeneration: Long, state: Stateful<List<FileItem>>) =
        publish(loadGeneration, state)

    private fun publish(loadGeneration: Long, state: Stateful<List<FileItem>>) {
        publisher.publish(loadGeneration, state)
    }

    override fun close() {
        generation.next()
        future?.cancel(true)
    }

    companion object {
        private const val INTERVAL_MILLIS = 500L
        private const val PAGE_SIZE = 200
    }
}
