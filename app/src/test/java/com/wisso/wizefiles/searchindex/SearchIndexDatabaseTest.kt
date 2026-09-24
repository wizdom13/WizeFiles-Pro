package com.wisso.wizefiles.searchindex

import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.app.setGlobalApplicationForTests
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchIndexDatabaseTest {
    private val root = "/storage/emulated/0"

    @Before
    fun setUp() {
        setGlobalApplicationForTests(ApplicationProvider.getApplicationContext())
        SearchIndexDatabase.clear()
    }

    @After
    fun tearDown() {
        SearchIndexDatabase.clear()
    }

    @Test
    fun `trigram search preserves substring unicode punctuation and extension matching`() {
        val generation = 1L
        index(
            generation,
            record("Documents/Annual_Report.PDF", generation),
            record("Documents/مبيعات-تموز.xlsx", generation),
            record("Documents/C#_guide.txt", generation)
        )

        assertEquals(
            listOf("Annual_Report.PDF"),
            search("/storage/emulated/0/Documents", "PORT").map(SearchIndexRecord::name)
        )
        assertEquals(
            listOf("مبيعات-تموز.xlsx"),
            search("/storage/emulated/0/Documents", "تموز").map(SearchIndexRecord::name)
        )
        assertEquals(
            listOf("C#_guide.txt"),
            search("/storage/emulated/0/Documents", "#_").map(SearchIndexRecord::name)
        )
        assertEquals(
            listOf("Annual_Report.PDF"),
            search("/storage/emulated/0", ".pdf").map(SearchIndexRecord::name)
        )
    }

    @Test
    fun `subtree and hidden filters cannot leak sibling results`() {
        val generation = 2L
        index(
            generation,
            record("Pictures/trip/photo.jpg", generation),
            record("Pictures/trip/.private-photo.jpg", generation, hidden = true),
            record("Pictures/trip-old/photo-old.jpg", generation),
            record("Download/photo-download.jpg", generation)
        )

        val visible = search("/storage/emulated/0/Pictures/trip", "photo", includeHidden = false)
        assertEquals(listOf("photo.jpg"), visible.map(SearchIndexRecord::name))
        val all = search("/storage/emulated/0/Pictures/trip", "photo", includeHidden = true)
        assertEquals(setOf("photo.jpg", ".private-photo.jpg"), all.map(SearchIndexRecord::name).toSet())
    }

    @Test
    fun `interrupted scan keeps old rows and completed generation removes stale rows`() {
        index(10L, record("a.txt", 10L), record("b.txt", 10L))

        SearchIndexDatabase.beginRootScan(root, 11L)
        SearchIndexDatabase.upsertBatch(listOf(record("a.txt", 11L)))
        SearchIndexDatabase.failRootScan(root)
        assertEquals(2, search(root, ".txt").size)

        SearchIndexDatabase.beginRootScan(root, 12L)
        SearchIndexDatabase.upsertBatch(listOf(record("a.txt", 12L)))
        SearchIndexDatabase.completeRootScan(root, 12L, 1L)
        assertEquals(listOf("a.txt"), search(root, ".txt").map(SearchIndexRecord::name))
    }

    @Test
    fun `exact and prefix matches rank before other substrings and paginate deterministically`() {
        val generation = 20L
        val records = buildList {
            add(record("report", generation))
            add(record("report-final.pdf", generation))
            repeat(250) { add(record("archive-$it-report.txt", generation, modified = it.toLong())) }
        }
        index(generation, *records.toTypedArray())

        val first = search(root, "report", limit = 200, offset = 0)
        val second = search(root, "report", limit = 200, offset = 200)
        assertEquals(200, first.size)
        assertEquals(52, second.size)
        assertEquals("report", first[0].name)
        assertEquals("report-final.pdf", first[1].name)
        assertEquals(252, (first + second).map(SearchIndexRecord::path).distinct().size)
    }

    @Test
    fun `like escaping treats percent underscore and backslash literally`() {
        assertEquals("100\\%\\_done\\\\ok", escapeLike("100%_done\\ok"))
        assertEquals("root/", "root".withTrailingSeparator())
        assertEquals("root/", "root/".withTrailingSeparator())
        assertEquals("/storage/emulated/00", subtreeUpperBound("/storage/emulated/0"))
        assertEquals("0", subtreeUpperBound("/"))
        assertEquals("image.jpg", normalizeSearchText("IMAGE.JPG"))
    }

    private fun index(generation: Long, vararg records: SearchIndexRecord) {
        SearchIndexDatabase.beginRootScan(root, generation)
        SearchIndexDatabase.upsertBatch(records.toList())
        SearchIndexDatabase.completeRootScan(root, generation, records.size.toLong())
        assertEquals(root, SearchIndexDatabase.readyRootFor("$root/Documents"))
    }

    private fun search(
        directory: String,
        query: String,
        includeHidden: Boolean = true,
        limit: Int = 200,
        offset: Int = 0
    ): List<SearchIndexRecord> = SearchIndexDatabase.search(
        root,
        directory,
        query,
        includeHidden,
        limit,
        offset
    )

    private fun record(
        relativePath: String,
        generation: Long,
        hidden: Boolean = false,
        modified: Long = 0L
    ): SearchIndexRecord {
        val path = "$root/$relativePath"
        return SearchIndexRecord(
            rootPath = root,
            path = path,
            parentPath = File(path).parent.orEmpty(),
            name = File(path).name,
            isDirectory = false,
            sizeBytes = 42L,
            modifiedMillis = modified,
            isHidden = hidden,
            mimeType = "application/octet-stream",
            generation = generation
        )
    }
}
