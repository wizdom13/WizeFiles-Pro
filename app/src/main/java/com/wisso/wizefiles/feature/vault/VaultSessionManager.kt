package com.wisso.wizefiles.vault

import java.util.concurrent.ConcurrentHashMap
import com.wisso.wizefiles.storage.VaultLockEvent
import com.wisso.wizefiles.storage.VaultLockReducer
import com.wisso.wizefiles.storage.VaultLockState

object VaultSessionManager {
    private val sessions = ConcurrentHashMap<String, ByteArray>()
    private val states = ConcurrentHashMap<String, VaultLockState>()

    fun putUnlockedKey(vaultId: String, vmk: ByteArray) {
        sessions[vaultId] = vmk
        states[vaultId] = VaultLockReducer.reduce(
            states[vaultId] ?: VaultLockState.LOCKED,
            VaultLockEvent.UnlockSucceeded
        )
    }

    fun getUnlockedKey(vaultId: String): ByteArray? = sessions[vaultId]

    fun isUnlocked(vaultId: String): Boolean =
        states[vaultId] != VaultLockState.LOCKED && sessions.containsKey(vaultId)

    fun markBackgrounded(vaultId: String) {
        states.compute(vaultId) { _, state ->
            VaultLockReducer.reduce(state ?: VaultLockState.LOCKED, VaultLockEvent.AppBackgrounded)
        }
    }

    fun enforceRelockDeadline(vaultId: String) {
        val next = states.compute(vaultId) { _, state ->
            VaultLockReducer.reduce(state ?: VaultLockState.LOCKED, VaultLockEvent.RelockDeadlineReached)
        }
        if (next == VaultLockState.LOCKED) sessions.remove(vaultId)?.let(VaultCrypto::zero)
    }

    fun lock(vaultId: String) {
        sessions.remove(vaultId)?.let(VaultCrypto::zero)
        states[vaultId] = VaultLockReducer.reduce(
            states[vaultId] ?: VaultLockState.LOCKED,
            VaultLockEvent.ExplicitLock
        )
    }

    fun clearAll() {
        sessions.values.forEach(VaultCrypto::zero)
        sessions.clear()
        states.clear()
    }
}
