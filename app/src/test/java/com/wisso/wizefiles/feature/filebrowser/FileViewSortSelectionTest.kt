package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class FileViewSortSelectionTest {

    @Test
    fun `global mode ignores stale path values`() {
        assertEquals(
            "global",
            FileViewSortPersistencePolicy.effective(
                pathSpecific = false,
                pathValue = "stale-path",
                globalValue = "global"
            )
        )
        assertEquals(
            FileViewSortPersistenceTarget.GLOBAL,
            FileViewSortPersistencePolicy.target(false)
        )
    }

    @Test
    fun `path mode uses its override and falls back to global when missing`() {
        assertEquals(
            "path",
            FileViewSortPersistencePolicy.effective(
                pathSpecific = true,
                pathValue = "path",
                globalValue = "global"
            )
        )
        assertEquals(
            "global",
            FileViewSortPersistencePolicy.effective(
                pathSpecific = true,
                pathValue = null,
                globalValue = "global"
            )
        )
        assertEquals(
            FileViewSortPersistenceTarget.PATH,
            FileViewSortPersistencePolicy.target(true)
        )
    }

    @Test
    fun `enabling path mode preserves an override or snapshots the global value`() {
        assertEquals(
            "existing",
            FileViewSortPersistencePolicy.initializeOverride("existing", "global")
        )
        assertEquals(
            "global",
            FileViewSortPersistencePolicy.initializeOverride(null, "global")
        )
    }

    @Test
    fun `every field uses the captured path-specific decision`() {
        listOf(false, true).forEach { pathSpecific ->
            val selection = FileViewSortSelection(
                viewType = FileViewType.GRID,
                sortBy = FileSortOptions.By.SIZE,
                sortOrder = FileSortOptions.Order.DESCENDING,
                directoriesFirst = false,
                pathSpecific = pathSpecific,
                gridColumns = 5
            )
            val events = mutableListOf<String>()

            FileViewSortSelectionApplier.apply(
                selection = selection,
                widthClass = GridWidthClass.WIDE,
                setPathSpecificMode = { events += "mode:$it" },
                setViewType = { value, routed -> events += "view:$value:$routed" },
                setGridColumns = { width, value, routed ->
                    events += "grid:$width:$value:$routed"
                },
                setSortBy = { value, routed -> events += "by:$value:$routed" },
                setSortOrder = { value, routed -> events += "order:$value:$routed" },
                setDirectoriesFirst = { value, routed ->
                    events += "directories:$value:$routed"
                }
            )

            assertEquals(
                listOf(
                    "mode:$pathSpecific",
                    "view:GRID:$pathSpecific",
                    "grid:WIDE:5:$pathSpecific",
                    "by:SIZE:$pathSpecific",
                    "order:DESCENDING:$pathSpecific",
                    "directories:false:$pathSpecific"
                ),
                events
            )
        }
    }
}
