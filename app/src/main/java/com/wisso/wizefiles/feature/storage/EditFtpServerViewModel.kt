// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wisso.wizefiles.util.ActionState
import com.wisso.wizefiles.util.isFinished
import com.wisso.wizefiles.util.isReady
import java.nio.charset.Charset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditFtpServerViewModel : ViewModel() {
    val charsets: List<Charset> = Charset.availableCharsets().values.toList()

    private val mutableConnectState =
        MutableStateFlow<ActionState<FtpServer, Unit>>(ActionState.Ready())
    val connectState = mutableConnectState.asStateFlow()

    fun connect(server: FtpServer) {
        check(mutableConnectState.value.isReady)
        mutableConnectState.value = ActionState.Running(server)
        viewModelScope.launch {
            mutableConnectState.value = runCatching {
                probeRemoteServer(
                    server,
                    FtpServerAuthenticator::addTransientServer,
                    FtpServerAuthenticator::removeTransientServer,
                    FtpServer::path
                )
            }.fold(
                onSuccess = { ActionState.Success(server, Unit) },
                onFailure = { ActionState.Error(server, it) }
            )
        }
    }

    fun finishConnecting() {
        check(mutableConnectState.value.isFinished)
        mutableConnectState.value = ActionState.Ready()
    }
}
