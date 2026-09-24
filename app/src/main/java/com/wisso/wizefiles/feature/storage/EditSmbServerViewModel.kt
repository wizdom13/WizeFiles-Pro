package com.wisso.wizefiles.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wisso.wizefiles.util.ActionState
import com.wisso.wizefiles.util.isFinished
import com.wisso.wizefiles.util.isReady
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditSmbServerViewModel : ViewModel() {
    private val mutableConnectState =
        MutableStateFlow<ActionState<SmbServer, Unit>>(ActionState.Ready())
    val connectState = mutableConnectState.asStateFlow()

    fun connect(server: SmbServer) {
        check(mutableConnectState.value.isReady)
        mutableConnectState.value = ActionState.Running(server)
        viewModelScope.launch {
            mutableConnectState.value = runCatching {
                probeRemoteServer(
                    server,
                    SmbServerAuthenticator::addTransientServer,
                    SmbServerAuthenticator::removeTransientServer,
                    SmbServer::path
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
