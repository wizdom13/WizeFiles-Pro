package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCommandPolicyTest {
    @Test fun `fully writable browser context enables every command`() {
        val context = availableContext()

        BrowserCommand.entries.forEach { command ->
            assertTrue(command.name, BrowserCommandPolicy.isAvailable(command, context))
        }
    }

    @Test fun `picker context disables every command`() {
        val context = availableContext().copy(isPicker = true)

        BrowserCommand.entries.forEach { command ->
            assertFalse(command.name, BrowserCommandPolicy.isAvailable(command, context))
        }
    }

    @Test fun `recycle bin keeps delete and paste but blocks transfer and rename commands`() {
        val context = availableContext().copy(inRecycleBin = true)

        assertTrue(BrowserCommandPolicy.isAvailable(BrowserCommand.DELETE, context))
        assertTrue(BrowserCommandPolicy.isAvailable(BrowserCommand.PASTE, context))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.COPY, context))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.CUT, context))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.RENAME, context))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.COPY_TO_OTHER_PANE, context))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.MOVE_TO_OTHER_PANE, context))
    }

    @Test fun `read only source blocks cut and move but permits copy operations`() {
        val context = availableContext().copy(sourceReadOnly = true)

        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.CUT, context))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.MOVE_TO_OTHER_PANE, context))
        assertTrue(BrowserCommandPolicy.isAvailable(BrowserCommand.COPY, context))
        assertTrue(BrowserCommandPolicy.isAvailable(BrowserCommand.COPY_TO_OTHER_PANE, context))
    }

    @Test fun `selection mutation and cardinality guard delete and rename`() {
        val immutable = availableContext().copy(selectionMutable = false)
        val multiple = availableContext().copy(selectionCount = 2)

        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.DELETE, immutable))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.RENAME, immutable))
        assertTrue(BrowserCommandPolicy.isAvailable(BrowserCommand.DELETE, multiple))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.RENAME, multiple))
    }

    @Test fun `paste requires both files and a writable destination`() {
        assertFalse(
            BrowserCommandPolicy.isAvailable(
                BrowserCommand.PASTE,
                availableContext().copy(hasPasteFiles = false)
            )
        )
        assertFalse(
            BrowserCommandPolicy.isAvailable(
                BrowserCommand.PASTE,
                availableContext().copy(currentLocationWritable = false)
            )
        )
    }

    @Test fun `other pane commands require a writable destination`() {
        val context = availableContext().copy(otherPaneWritable = false)

        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.COPY_TO_OTHER_PANE, context))
        assertFalse(BrowserCommandPolicy.isAvailable(BrowserCommand.MOVE_TO_OTHER_PANE, context))
    }

    @Test fun `dispatcher runs only the selected available action`() {
        val executed = mutableListOf<BrowserCommand>()
        val dispatcher = BrowserCommandDispatcher(
            isAvailable = { it != BrowserCommand.DELETE },
            copy = { executed += BrowserCommand.COPY },
            cut = { executed += BrowserCommand.CUT },
            paste = { executed += BrowserCommand.PASTE },
            delete = { executed += BrowserCommand.DELETE },
            rename = { executed += BrowserCommand.RENAME },
            copyToOtherPane = { executed += BrowserCommand.COPY_TO_OTHER_PANE },
            moveToOtherPane = { executed += BrowserCommand.MOVE_TO_OTHER_PANE }
        )

        assertTrue(dispatcher.execute(BrowserCommand.RENAME))
        assertFalse(dispatcher.execute(BrowserCommand.DELETE))
        assertEquals(listOf(BrowserCommand.RENAME), executed)
    }

    private fun availableContext() = BrowserCommandContext(
        selectionCount = 1,
        isPicker = false,
        inRecycleBin = false,
        sourceReadOnly = false,
        selectionMutable = true,
        hasPasteFiles = true,
        currentLocationWritable = true,
        otherPaneWritable = true
    )
}
