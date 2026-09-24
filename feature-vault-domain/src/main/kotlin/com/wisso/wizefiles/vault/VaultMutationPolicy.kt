// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

enum class VaultMutationKind { CREATE, RENAME, MOVE, DELETE, IMPORT, EXPORT }

data class VaultMutationContext(
    val unlocked: Boolean,
    val sourceExists: Boolean = true,
    val destinationNameAvailable: Boolean = true,
    val destinationWritable: Boolean = true
)

sealed interface VaultMutationDecision {
    data object Proceed : VaultMutationDecision
    data object RequiresUnlock : VaultMutationDecision
    data object SourceMissing : VaultMutationDecision
    data object NameConflict : VaultMutationDecision
    data object ReadOnlyDestination : VaultMutationDecision
}

/** Provider-free safety policy evaluated before a vault mutation reaches storage or crypto adapters. */
object VaultMutationPolicy {
    fun decide(kind: VaultMutationKind, context: VaultMutationContext): VaultMutationDecision = when {
        !context.unlocked -> VaultMutationDecision.RequiresUnlock
        kind != VaultMutationKind.CREATE && !context.sourceExists -> VaultMutationDecision.SourceMissing
        kind in setOf(VaultMutationKind.CREATE, VaultMutationKind.RENAME, VaultMutationKind.MOVE, VaultMutationKind.IMPORT) &&
            !context.destinationNameAvailable -> VaultMutationDecision.NameConflict
        kind in setOf(VaultMutationKind.CREATE, VaultMutationKind.RENAME, VaultMutationKind.MOVE, VaultMutationKind.DELETE, VaultMutationKind.IMPORT) &&
            !context.destinationWritable -> VaultMutationDecision.ReadOnlyDestination
        else -> VaultMutationDecision.Proceed
    }
}

/** Minimal identity view needed by provider-neutral Vault naming policy. */
interface VaultEntryIdentity {
    val id: String
    val parentId: String?
    val name: String
}

/** Name uniqueness is a vault invariant, independent of JSON and Android storage models. */
object VaultEntryNamePolicy {
    fun hasConflict(
        entries: Iterable<VaultEntryIdentity>,
        parentId: String?,
        name: String,
        excludedEntryId: String? = null
    ): Boolean = entries.any {
        it.parentId == parentId && it.id != excludedEntryId && it.name == name
    }

    fun requireAvailable(
        entries: Iterable<VaultEntryIdentity>,
        parentId: String?,
        name: String,
        excludedEntryId: String? = null
    ) {
        if (hasConflict(entries, parentId, name, excludedEntryId)) {
            throw VaultEntryNameConflictException()
        }
    }
}

class VaultEntryNameConflictException : IllegalStateException("An entry with this name already exists")

enum class VaultRecoveryState { CLEAN, STAGED_WRITE, COMMITTED, ROLLBACK_REQUIRED, CORRUPT }
sealed interface VaultRecoveryEvent {
    data object BeginWrite : VaultRecoveryEvent
    data object CommitSucceeded : VaultRecoveryEvent
    data object CommitFailed : VaultRecoveryEvent
    data object RollbackSucceeded : VaultRecoveryEvent
    data object IntegrityCheckFailed : VaultRecoveryEvent
}

/** Models recovery without filesystem, database, Android, or Keystore dependencies. */
object VaultRecoveryReducer {
    fun reduce(state: VaultRecoveryState, event: VaultRecoveryEvent): VaultRecoveryState = when (event) {
        VaultRecoveryEvent.BeginWrite -> requireState(state, VaultRecoveryState.CLEAN, VaultRecoveryState.STAGED_WRITE)
        VaultRecoveryEvent.CommitSucceeded -> requireState(state, VaultRecoveryState.STAGED_WRITE, VaultRecoveryState.COMMITTED)
        VaultRecoveryEvent.CommitFailed -> requireState(state, VaultRecoveryState.STAGED_WRITE, VaultRecoveryState.ROLLBACK_REQUIRED)
        VaultRecoveryEvent.RollbackSucceeded -> requireState(state, VaultRecoveryState.ROLLBACK_REQUIRED, VaultRecoveryState.CLEAN)
        VaultRecoveryEvent.IntegrityCheckFailed -> VaultRecoveryState.CORRUPT
    }

    private fun requireState(actual: VaultRecoveryState, expected: VaultRecoveryState, next: VaultRecoveryState): VaultRecoveryState {
        require(actual == expected) { "Expected $expected but was $actual" }
        return next
    }
}
