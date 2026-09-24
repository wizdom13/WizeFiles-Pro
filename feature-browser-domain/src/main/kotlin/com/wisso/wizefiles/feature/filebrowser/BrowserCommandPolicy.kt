package com.wisso.wizefiles.feature.filebrowser

data class BrowserCommandContext(
    val selectionCount: Int,
    val isPicker: Boolean,
    val inRecycleBin: Boolean,
    val sourceReadOnly: Boolean,
    val selectionMutable: Boolean,
    val hasPasteFiles: Boolean,
    val currentLocationWritable: Boolean,
    val otherPaneWritable: Boolean
)

/** Pure safety policy shared by every browser command entry point. */
object BrowserCommandPolicy {
    fun isAvailable(command: BrowserCommand, context: BrowserCommandContext): Boolean =
        with(context) {
            val hasSelection = selectionCount > 0
            when (command) {
                BrowserCommand.COPY -> hasSelection && !isPicker && !inRecycleBin
                BrowserCommand.CUT ->
                    hasSelection && !isPicker && !inRecycleBin && !sourceReadOnly
                BrowserCommand.PASTE ->
                    !isPicker && hasPasteFiles && currentLocationWritable
                BrowserCommand.DELETE -> hasSelection && !isPicker && selectionMutable
                BrowserCommand.RENAME ->
                    selectionCount == 1 && !isPicker && !inRecycleBin && selectionMutable
                BrowserCommand.COPY_TO_OTHER_PANE ->
                    hasSelection && !isPicker && !inRecycleBin && otherPaneWritable
                BrowserCommand.MOVE_TO_OTHER_PANE ->
                    hasSelection && !isPicker && !inRecycleBin && !sourceReadOnly &&
                        otherPaneWritable
            }
        }
}

/** Executes an already-modelled command without coupling routing to Fragment internals. */
class BrowserCommandDispatcher(
    private val isAvailable: (BrowserCommand) -> Boolean,
    private val copy: () -> Unit,
    private val cut: () -> Unit,
    private val paste: () -> Unit,
    private val delete: () -> Unit,
    private val rename: () -> Unit,
    private val copyToOtherPane: () -> Unit,
    private val moveToOtherPane: () -> Unit
) {
    fun execute(command: BrowserCommand): Boolean {
        if (!isAvailable(command)) return false
        when (command) {
            BrowserCommand.COPY -> copy()
            BrowserCommand.CUT -> cut()
            BrowserCommand.PASTE -> paste()
            BrowserCommand.DELETE -> delete()
            BrowserCommand.RENAME -> rename()
            BrowserCommand.COPY_TO_OTHER_PANE -> copyToOtherPane()
            BrowserCommand.MOVE_TO_OTHER_PANE -> moveToOtherPane()
        }
        return true
    }
}

/** Immutable operation request created before UI effects or Android services are invoked. */
data class BrowserCommandPlan(
    val command: BrowserCommand,
    val selectionCount: Int
)

object BrowserCommandPlanner {
    fun create(command: BrowserCommand, context: BrowserCommandContext): BrowserCommandPlan? =
        if (BrowserCommandPolicy.isAvailable(command, context)) {
            BrowserCommandPlan(command, context.selectionCount)
        } else {
            null
        }
}
