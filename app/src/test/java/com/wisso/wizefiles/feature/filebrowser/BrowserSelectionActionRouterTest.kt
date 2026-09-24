package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserSelectionActionRouterTest {
    @Test fun `command menu items resolve to browser commands`() {
        val expected = mapOf(
            R.id.action_cut to BrowserCommand.CUT,
            R.id.action_copy to BrowserCommand.COPY,
            R.id.action_delete to BrowserCommand.DELETE,
            R.id.action_rename to BrowserCommand.RENAME,
            R.id.action_copy_to_other_pane to BrowserCommand.COPY_TO_OTHER_PANE,
            R.id.action_move_to_other_pane to BrowserCommand.MOVE_TO_OTHER_PANE
        )

        expected.forEach { (itemId, command) ->
            assertEquals(
                BrowserSelectionAction.Command(command),
                BrowserSelectionActionRouter.resolve(itemId)
            )
        }
    }

    @Test fun `direct menu items resolve to stable selection actions`() {
        val expected = mapOf(
            R.id.action_open to BrowserDirectSelectionAction.OPEN,
            R.id.action_create to BrowserDirectSelectionAction.CREATE,
            R.id.action_open_with to BrowserDirectSelectionAction.OPEN_WITH,
            R.id.action_edit to BrowserDirectSelectionAction.EDIT,
            R.id.action_restore to BrowserDirectSelectionAction.RESTORE,
            R.id.action_extract to BrowserDirectSelectionAction.EXTRACT,
            R.id.action_archive to BrowserDirectSelectionAction.ARCHIVE,
            R.id.action_encrypt to BrowserDirectSelectionAction.ENCRYPT,
            R.id.action_decrypt to BrowserDirectSelectionAction.DECRYPT,
            R.id.action_share to BrowserDirectSelectionAction.SHARE,
            R.id.action_send_nearby to BrowserDirectSelectionAction.SEND_NEARBY,
            R.id.action_batch_rename to BrowserDirectSelectionAction.BATCH_RENAME,
            R.id.action_copy_path to BrowserDirectSelectionAction.COPY_PATH,
            R.id.action_add_bookmark to BrowserDirectSelectionAction.ADD_BOOKMARK,
            R.id.action_create_shortcut to BrowserDirectSelectionAction.CREATE_SHORTCUT,
            R.id.action_properties to BrowserDirectSelectionAction.PROPERTIES,
            R.id.action_select_all to BrowserDirectSelectionAction.SELECT_ALL
        )

        expected.forEach { (itemId, action) ->
            assertEquals(
                BrowserSelectionAction.Direct(action),
                BrowserSelectionActionRouter.resolve(itemId)
            )
        }
    }

    @Test fun `unknown menu item is not handled`() {
        assertNull(BrowserSelectionActionRouter.resolve(Int.MIN_VALUE))
    }
}
