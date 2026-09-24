package com.wisso.wizefiles.feature.sync

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class SyncScopeLockRegistry {
    private val lock = ReentrantLock()
    private val heldScopes = linkedMapOf<String, List<String>>()

    fun tryAcquire(ownerId: String, scopes: Collection<String>): Boolean = lock.withLock {
        require(ownerId.isNotBlank())
        val normalized = scopes.map(SyncEndpointValidator::normalize).distinct()
        require(normalized.isNotEmpty())
        val otherScopes = heldScopes.filterKeys { it != ownerId }.values.flatten()
        if (normalized.any { requested ->
                otherScopes.any { held -> SyncEndpointValidator.scopesOverlap(requested, held) }
            }
        ) return false
        heldScopes[ownerId] = normalized
        true
    }

    fun release(ownerId: String) = lock.withLock {
        heldScopes.remove(ownerId)
        Unit
    }

    fun scopes(ownerId: String): List<String> = lock.withLock {
        heldScopes[ownerId].orEmpty().toList()
    }
}
