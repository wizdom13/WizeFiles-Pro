package com.wisso.wizefiles.provider.root

import com.wisso.wizefiles.provider.common.PosixFileAttributeView
import com.wisso.wizefiles.provider.remote.BinderEndpoint
import com.wisso.wizefiles.provider.remote.RemotePosixFileAttributeView

open class RootPosixFileAttributeView(
    attributeView: PosixFileAttributeView
) : RemotePosixFileAttributeView(
    BinderEndpoint { RootFileService.connectPosixAttributes(attributeView) }
) {
    override fun name(): String {
        throw AssertionError()
    }
}
