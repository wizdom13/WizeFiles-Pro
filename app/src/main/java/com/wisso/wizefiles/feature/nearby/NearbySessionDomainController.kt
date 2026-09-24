package com.wisso.wizefiles.feature.nearby

import com.wisso.wizefiles.storage.NearbySessionEvent
import com.wisso.wizefiles.storage.NearbySessionReducer
import com.wisso.wizefiles.storage.NearbySessionState
import com.wisso.wizefiles.feature.transfer.TransferOperationState

internal data class NearbySessionProjection(
    val phase: NearbyPhase,
    val operationState: TransferOperationState?
)

/** Keeps transport callbacks on the provider-neutral authentication/transfer state machine. */
internal class NearbySessionDomainController {
    var state: NearbySessionState = NearbySessionState.IDLE
        private set

    fun beginDiscovery() = transition(NearbySessionEvent.BeginDiscovery)
    fun peerFound() = transition(NearbySessionEvent.PeerFound)
    fun authenticated() = transition(NearbySessionEvent.AuthenticationAccepted)
    fun transferStarted() = transition(NearbySessionEvent.TransferStarted)
    fun pause() = transition(NearbySessionEvent.Pause)
    fun resumeTransfer() = transition(NearbySessionEvent.ResumeTransfer)
    fun completed() = transition(NearbySessionEvent.TransferCompleted)
    fun failed(recoverable: Boolean) = transition(NearbySessionEvent.ConnectionFailed(recoverable))
    fun cancelled() = transition(NearbySessionEvent.Cancel)

    fun reset() { state = NearbySessionState.IDLE }

    fun projection(): NearbySessionProjection = when (state) {
        NearbySessionState.IDLE -> NearbySessionProjection(NearbyPhase.IDLE, null)
        NearbySessionState.DISCOVERING -> NearbySessionProjection(NearbyPhase.DISCOVERING, TransferOperationState.QUEUED)
        NearbySessionState.AUTHENTICATING -> NearbySessionProjection(NearbyPhase.AUTH_SCAN_REQUIRED, TransferOperationState.WAITING_FOR_USER)
        NearbySessionState.CONNECTED -> NearbySessionProjection(NearbyPhase.CONNECTED, TransferOperationState.QUEUED)
        NearbySessionState.TRANSFERRING -> NearbySessionProjection(NearbyPhase.TRANSFERRING, TransferOperationState.RUNNING)
        NearbySessionState.PAUSED -> NearbySessionProjection(NearbyPhase.PAUSED, TransferOperationState.PAUSED)
        NearbySessionState.RECOVERABLE -> NearbySessionProjection(NearbyPhase.RECOVERABLE, TransferOperationState.RECOVERABLE)
        NearbySessionState.COMPLETED -> NearbySessionProjection(NearbyPhase.COMPLETED, TransferOperationState.COMPLETED)
        NearbySessionState.CANCELLED -> NearbySessionProjection(NearbyPhase.CANCELLED, TransferOperationState.CANCELLED)
        NearbySessionState.FAILED -> NearbySessionProjection(NearbyPhase.ERROR, TransferOperationState.FAILED)
    }

    /** Transport callbacks may be duplicated or arrive after cancellation; reject them without crashing. */
    private fun transition(event: NearbySessionEvent): Boolean {
        val next = runCatching { NearbySessionReducer.reduce(state, event) }.getOrNull() ?: return false
        state = next
        return true
    }
}
