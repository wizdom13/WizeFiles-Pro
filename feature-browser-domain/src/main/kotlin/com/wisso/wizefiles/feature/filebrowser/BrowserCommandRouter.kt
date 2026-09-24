package com.wisso.wizefiles.feature.filebrowser

/** Commands shared by the toolbar, item menus, keyboard and internal drag-and-drop. */
enum class BrowserCommand {
    COPY,
    CUT,
    PASTE,
    DELETE,
    RENAME,
    COPY_TO_OTHER_PANE,
    MOVE_TO_OTHER_PANE
}

interface BrowserCommandTarget {
    fun isBrowserCommandAvailable(command: BrowserCommand): Boolean
    fun performBrowserCommand(command: BrowserCommand): Boolean
}

/**
 * The activity owns one router so every input method observes the same active-pane and safety
 * rules. Targets still perform the existing dialogs and FileOperationService calls.
 */
class BrowserCommandRouter {
    fun isAvailable(target: BrowserCommandTarget?, command: BrowserCommand): Boolean =
        target?.isBrowserCommandAvailable(command) == true

    fun execute(target: BrowserCommandTarget?, command: BrowserCommand): Boolean {
        if (!isAvailable(target, command)) return false
        return target?.performBrowserCommand(command) == true
    }
}

/**
 * Owns command availability and execution for a browser surface.  UI containers supply snapshots
 * and side effects, but do not duplicate the command switch or its safety-policy invocation.
 */
class BrowserCommandCoordinator(
    private val context: () -> BrowserCommandContext,
    private val copy: () -> Unit,
    private val cut: () -> Unit,
    private val paste: () -> Unit,
    private val delete: () -> Unit,
    private val rename: () -> Unit,
    private val copyToOtherPane: () -> Unit,
    private val moveToOtherPane: () -> Unit
) {
    private val dispatcher = BrowserCommandDispatcher(
        isAvailable = ::isAvailable,
        copy = copy,
        cut = cut,
        paste = paste,
        delete = delete,
        rename = rename,
        copyToOtherPane = copyToOtherPane,
        moveToOtherPane = moveToOtherPane
    )

    fun isAvailable(command: BrowserCommand): Boolean =
        BrowserCommandPolicy.isAvailable(command, context())

    fun execute(command: BrowserCommand): Boolean {
        val plan = BrowserCommandPlanner.create(command, context()) ?: return false
        return dispatcher.execute(plan.command)
    }
}
