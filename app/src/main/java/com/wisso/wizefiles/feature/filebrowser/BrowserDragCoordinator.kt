// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.DragEvent
import android.view.View
import androidx.core.view.ViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.feature.pro.ensureProAccess
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.archive.editor.ArchiveEditCapabilities
import java.nio.file.Path
import java.nio.file.Files
import java.util.UUID
import java.util.WeakHashMap

enum class BrowserDragInput { TOUCH, MOUSE }
internal enum class BrowserDropAction { COPY, MOVE }

internal data class BrowserDropValidation(
    val valid: Boolean,
    val copyAllowed: Boolean,
    val moveAllowed: Boolean,
    val defaultAction: BrowserDropAction,
    val reason: String? = null
)

internal object BrowserTransferPolicy {
    fun validate(
        sources: List<Path>,
        destination: Path,
        requireExistingSources: Boolean = false
    ): BrowserDropValidation {
        if (sources.isEmpty()) return invalid("No files selected")
        if (destination.fileSystem.isReadOnly &&
            !ArchiveEditCapabilities.isEditableLocation(destination)) {
            return invalid("Destination is read-only")
        }
        if (requireExistingSources && sources.any { !Files.exists(it) }) {
            return invalid("A selected item is no longer available")
        }
        if (sources.any { it == destination || it.parent == destination }) {
            return invalid("Files are already in this folder")
        }
        if (sources.any { source ->
                runCatching { destination.startsWith(source) }.getOrDefault(false)
            }) {
            return invalid("A folder cannot be placed inside itself")
        }
        val moveAllowed = sources.none { it.fileSystem.isReadOnly || it.isArchivePath }
        val sameProvider = destination.fileSystem.provider().scheme.equals("file", true) &&
            sources.all { source ->
            runCatching { source.fileSystem == destination.fileSystem }.getOrDefault(false)
            }
        return BrowserDropValidation(
            valid = true,
            copyAllowed = true,
            moveAllowed = moveAllowed,
            defaultAction = if (sameProvider && moveAllowed) {
                BrowserDropAction.MOVE
            } else {
                BrowserDropAction.COPY
            }
        )
    }

    private fun invalid(reason: String) = BrowserDropValidation(
        valid = false,
        copyAllowed = false,
        moveAllowed = false,
        defaultAction = BrowserDropAction.COPY,
        reason = reason
    )
}

internal class BrowserDragCoordinator(private val activity: FileListActivity) {
    private data class Session(
        val id: String,
        val source: FileListFragment,
        val files: List<Path>,
        val input: BrowserDragInput,
        val anchor: View,
        val shadow: MultiFileDragShadow,
        val originalAnchorAlpha: Float
    )

    private var session: Session? = null
    private var ctrlPressed = false
    private var shiftPressed = false
    private val originalAccessibility = WeakHashMap<View, Pair<CharSequence?, Int>>()

    val isDragging: Boolean get() = session != null

    fun accepts(event: DragEvent): Boolean = session != null && isInternal(event)

    fun updateModifiers(ctrl: Boolean, shift: Boolean) {
        ctrlPressed = ctrl
        shiftPressed = shift
        session?.let { active ->
            active.shadow.action = when {
                ctrl -> BrowserDropAction.COPY
                shift -> BrowserDropAction.MOVE
                else -> null
            }
            active.anchor.updateDragShadow(active.shadow)
        }
    }

    fun start(
        source: FileListFragment,
        anchor: View,
        files: List<Path>,
        input: BrowserDragInput
    ): Boolean {
        if (files.isEmpty() || source.isInPickFlow()) return false
        val id = UUID.randomUUID().toString()
        val shadow = MultiFileDragShadow(anchor, files.size)
        val originalAlpha = anchor.alpha
        session = Session(id, source, files.toList(), input, anchor, shadow, originalAlpha)
        val clip = ClipData(
            ClipDescription("WizeFiles internal drag", arrayOf(INTERNAL_MIME)),
            ClipData.Item(id)
        )
        val started = ViewCompat.startDragAndDrop(
            anchor,
            clip,
            shadow,
            id,
            0
        )
        if (started) {
            anchor.alpha = SOURCE_ALPHA
        } else {
            session = null
        }
        return started
    }

    fun handleTarget(
        owner: FileListFragment,
        destination: Path,
        target: View,
        event: DragEvent
    ): Boolean {
        if (event.action == DragEvent.ACTION_DRAG_STARTED) return isInternal(event)
        if (event.action == DragEvent.ACTION_DRAG_ENDED) {
            target.alpha = 1f
            finishSession()
            return true
        }
        val active = session ?: return false
        if (!isInternal(event) || event.localState != active.id) return false
        when (event.action) {
            DragEvent.ACTION_DRAG_ENTERED -> {
                owner.activateForExternalInput()
                val validation = BrowserTransferPolicy.validate(active.files, destination)
                active.shadow.valid = validation.valid
                target.alpha = if (validation.valid) TARGET_ALPHA else INVALID_TARGET_ALPHA
                originalAccessibility.putIfAbsent(
                    target,
                    target.contentDescription to target.accessibilityLiveRegion
                )
                target.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
                target.contentDescription = if (validation.valid) {
                        activity.getString(R.string.file_list_drag_target, destination.fileName ?: destination)
                    } else {
                        validation.reason
                    }
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                target.alpha = 1f
                restoreAccessibility(target)
            }
            DragEvent.ACTION_DRAG_LOCATION -> {
                owner.autoScrollDuringDrag(target, event)
                val validation = BrowserTransferPolicy.validate(active.files, destination)
                active.shadow.valid = validation.valid
                active.shadow.action = when {
                    ctrlPressed -> BrowserDropAction.COPY
                    shiftPressed && validation.moveAllowed -> BrowserDropAction.MOVE
                    else -> validation.defaultAction.takeIf { validation.valid }
                }
                active.anchor.updateDragShadow(active.shadow)
            }
            DragEvent.ACTION_DROP -> {
                target.alpha = 1f
                restoreAccessibility(target)
                if (owner !== active.source &&
                    !activity.ensureProAccess(ProFeature.CROSS_PANE_DRAG_AND_DROP)
                ) {
                    return true
                }
                completeDrop(active, destination)
            }
        }
        return true
    }

    fun cancel() {
        if (session != null) {
            runCatching { activity.findViewById<View>(android.R.id.content).cancelDragAndDrop() }
        }
        finishSession()
    }

    private fun finishSession() {
        session?.let { it.anchor.alpha = it.originalAnchorAlpha }
        session = null
        ctrlPressed = false
        shiftPressed = false
        originalAccessibility.keys.toList().forEach(::restoreAccessibility)
    }

    private fun restoreAccessibility(view: View) {
        val original = originalAccessibility.remove(view) ?: return
        view.contentDescription = original.first
        view.accessibilityLiveRegion = original.second
    }

    private fun isInternal(event: DragEvent): Boolean =
        event.clipDescription?.hasMimeType(INTERNAL_MIME) == true && event.localState is String

    private fun completeDrop(active: Session, destination: Path) {
        val validation = BrowserTransferPolicy.validate(
            active.files,
            destination,
            requireExistingSources = true
        )
        if (!validation.valid) {
            activity.showBrowserDragMessage(validation.reason ?: activity.getString(R.string.error))
            return
        }
        if (active.input == BrowserDragInput.TOUCH) {
            val labels = mutableListOf<String>()
            val actions = mutableListOf<BrowserDropAction>()
            if (validation.copyAllowed) {
                labels += activity.getString(R.string.file_list_drag_copy_here)
                actions += BrowserDropAction.COPY
            }
            if (validation.moveAllowed) {
                labels += activity.getString(R.string.file_list_drag_move_here)
                actions += BrowserDropAction.MOVE
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.file_list_drag_choose_action)
                .setItems(labels.toTypedArray()) { _, which ->
                    execute(active, destination, actions[which])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val requested = when {
            ctrlPressed -> BrowserDropAction.COPY
            shiftPressed -> BrowserDropAction.MOVE
            else -> validation.defaultAction
        }
        val resolved = if (requested == BrowserDropAction.MOVE && !validation.moveAllowed) {
            BrowserDropAction.COPY
        } else {
            requested
        }
        execute(active, destination, resolved)
    }

    private fun execute(active: Session, destination: Path, action: BrowserDropAction) {
        when (action) {
            BrowserDropAction.COPY -> FileOperationService.copy(active.files, destination, activity)
            BrowserDropAction.MOVE -> FileOperationService.move(active.files, destination, activity)
        }
        active.source.finishInternalDragSelection()
        activity.showBrowserDragMessage(
            activity.resources.getQuantityString(
                if (action == BrowserDropAction.COPY) {
                    R.plurals.file_list_drag_copy_started
                } else {
                    R.plurals.file_list_drag_move_started
                },
                active.files.size,
                active.files.size
            )
        )
    }

    private class MultiFileDragShadow(view: View, private val count: Int) :
        View.DragShadowBuilder(view) {
        var action: BrowserDropAction? = null
        var valid: Boolean? = null
        private val density = view.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val width = (116 * density).toInt()
        private val height = (62 * density).toInt()

        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            size.set(width, height)
            touch.set(width / 2, height / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            paint.color = Color.argb(235, 48, 52, 60)
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 16f * density, 16f * density, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = Color.WHITE
            repeat(minOf(3, count)) { index ->
                val offset = index * 4f * density
                canvas.drawRoundRect(
                    RectF(18f * density + offset, 16f * density - offset, 48f * density + offset, 46f * density - offset),
                    4f * density,
                    4f * density,
                    paint
                )
            }
            paint.style = Paint.Style.FILL
            paint.textSize = 18f * density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(count.toString(), 84f * density, 38f * density, paint)
            action?.let {
                paint.color = if (it == BrowserDropAction.COPY) Color.rgb(60, 130, 246) else Color.rgb(34, 170, 100)
                canvas.drawCircle(104f * density, 13f * density, 11f * density, paint)
                paint.color = Color.WHITE
                paint.textSize = 11f * density
                canvas.drawText(if (it == BrowserDropAction.COPY) "C" else "M", 104f * density, 17f * density, paint)
            }
            if (valid == false) {
                paint.color = Color.rgb(220, 65, 65)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * density
                canvas.drawLine(96f * density, 5f * density, 112f * density, 21f * density, paint)
                canvas.drawLine(112f * density, 5f * density, 96f * density, 21f * density, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    companion object {
        const val INTERNAL_MIME = "application/vnd.wizefiles.internal-drag"
        private const val TARGET_ALPHA = 0.72f
        private const val INVALID_TARGET_ALPHA = 0.42f
        private const val SOURCE_ALPHA = 0.64f
    }
}
