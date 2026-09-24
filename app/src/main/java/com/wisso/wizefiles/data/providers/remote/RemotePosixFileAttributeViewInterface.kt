// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import com.wisso.wizefiles.provider.common.ParcelableFileTime
import com.wisso.wizefiles.provider.common.ParcelablePosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileAttributeView
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser

class RemotePosixFileAttributeViewInterface(
    private val attributeView: PosixFileAttributeView
) : IPosixAttributesBridge.Stub() {
    override fun loadPosixAttributes(exception: BridgeFailure): BridgeObject? =
        serveBridge(exception) { attributeView.readAttributes().toParcelable() }

    override fun updateTimes(
        lastModifiedTime: ParcelableFileTime?,
        lastAccessTime: ParcelableFileTime?,
        createTime: ParcelableFileTime?,
        exception: BridgeFailure
    ) {
        serveBridge(exception) {
            attributeView.setTimes(
                lastModifiedTime?.value, lastAccessTime?.value, createTime?.value
            )
        }
    }

    override fun updateOwner(owner: PosixUser, exception: BridgeFailure) {
        serveBridge(exception) { attributeView.setOwner(owner) }
    }

    override fun updateGroup(group: PosixGroup, exception: BridgeFailure) {
        serveBridge(exception) { attributeView.setGroup(group) }
    }

    override fun updateMode(mode: ParcelablePosixFileMode, exception: BridgeFailure) {
        serveBridge(exception) { attributeView.setMode(mode.value) }
    }

    override fun updateSeLinuxContext(context: BridgeObject, exception: BridgeFailure) {
        serveBridge(exception) { attributeView.setSeLinuxContext(context.value()) }
    }

    override fun resetSeLinuxContext(exception: BridgeFailure) {
        serveBridge(exception) { attributeView.restoreSeLinuxContext() }
    }
}
