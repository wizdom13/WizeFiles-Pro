package com.wisso.wizefiles.feature.filebrowser

enum class BrowserDirectSelectionAction {
    OPEN, CREATE, OPEN_WITH, EDIT, RESTORE, EXTRACT, ARCHIVE, ENCRYPT, DECRYPT,
    SHARE, SEND_NEARBY, BATCH_RENAME, COPY_PATH, ADD_BOOKMARK, CREATE_SHORTCUT,
    PROPERTIES, SELECT_ALL
}

sealed interface BrowserSelectionAction {
    data class Command(val command: BrowserCommand) : BrowserSelectionAction
    data class Direct(val action: BrowserDirectSelectionAction) : BrowserSelectionAction
}

/** Pure cardinality rule used before Android-specific selection effects are invoked. */
object BrowserSelectionPolicy {
    private val singleItemActions = setOf(
        BrowserDirectSelectionAction.CREATE,
        BrowserDirectSelectionAction.OPEN_WITH,
        BrowserDirectSelectionAction.EDIT,
        BrowserDirectSelectionAction.COPY_PATH,
        BrowserDirectSelectionAction.ADD_BOOKMARK,
        BrowserDirectSelectionAction.CREATE_SHORTCUT,
        BrowserDirectSelectionAction.PROPERTIES
    )

    fun isAvailable(action: BrowserSelectionAction, selectionCount: Int): Boolean = when (action) {
        is BrowserSelectionAction.Command -> true
        is BrowserSelectionAction.Direct -> when (action.action) {
            BrowserDirectSelectionAction.SELECT_ALL -> true
            in singleItemActions -> selectionCount == 1
            else -> selectionCount > 0
        }
    }
}
