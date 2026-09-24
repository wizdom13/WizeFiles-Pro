package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.storage.ConflictPolicy
import com.wisso.wizefiles.storage.SyncDirection
import com.wisso.wizefiles.storage.SyncPlan as DomainSyncPlan

/** Maps persisted app profiles into the provider-neutral safety contract before planning. */
internal object SyncDomainPolicy {
    fun validate(profile: SyncProfile): DomainSyncPlan = DomainSyncPlan(
        sourceEndpointId = profile.sourceUri,
        targetEndpointId = profile.destinationUri,
        direction = if (profile.mode == SyncMode.TWO_WAY) SyncDirection.TWO_WAY else SyncDirection.PUSH,
        conflictPolicy = when (profile.conflictPolicy) {
            SyncConflictPolicy.ASK -> ConflictPolicy.ASK
            SyncConflictPolicy.PREFER_SOURCE,
            SyncConflictPolicy.PREFER_DESTINATION,
            SyncConflictPolicy.PREFER_NEWER,
            SyncConflictPolicy.PREFER_LARGER -> ConflictPolicy.REPLACE
            SyncConflictPolicy.KEEP_BOTH -> ConflictPolicy.KEEP_BOTH
            SyncConflictPolicy.SKIP -> ConflictPolicy.SKIP
        },
        // Baseline-tracked two-way deletion propagation is not "delete extraneous" mirror behavior.
        deleteExtraneous = profile.mode == SyncMode.MIRROR
    )
}
