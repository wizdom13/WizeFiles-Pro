// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

internal enum class SyncBackendKind {
    FILE_SYSTEM,
    SYNCTHING
}

internal object SyncBackendRouter {
    fun kind(profile: SyncProfile): SyncBackendKind =
        if (SyncthingEndpointCodec.isSyncthingUri(profile.destinationUri)) {
            SyncBackendKind.SYNCTHING
        } else {
            SyncBackendKind.FILE_SYSTEM
        }
}

internal enum class SyncthingFolderMode {
    SEND_RECEIVE,
    SEND_ONLY
}

internal object SyncthingProfilePolicy {
    fun validate(profile: SyncProfile): SyncthingEndpoint {
        require(SyncBackendRouter.kind(profile) == SyncBackendKind.SYNCTHING) {
            "Profile is not a Syncthing profile"
        }
        require(java.net.URI(profile.sourceUri).scheme == "file") {
            "Syncthing requires a directly accessible local folder"
        }
        require(profile.mode == SyncMode.TWO_WAY || profile.mode == SyncMode.MIRROR) {
            "Syncthing supports two-way and send-only sync; update-only and move-source are unsupported"
        }
        require(profile.conflictPolicy == SyncConflictPolicy.KEEP_BOTH) {
            "Syncthing uses conflict copies"
        }
        require(profile.propagateDeletions) { "Syncthing profiles must acknowledge deletion propagation" }
        return requireNotNull(SyncthingEndpointCodec.decode(profile.destinationUri)) {
            "Invalid Syncthing endpoint"
        }
    }

    fun validateUnique(id: String, sourceUri: String, endpoint: SyncthingEndpoint,
        profiles: List<SyncProfile>) {
        val local = java.io.File(java.net.URI(sourceUri)).canonicalFile.toURI().toString()
        require(profiles.none { other ->
            other.id != id && SyncBackendRouter.kind(other) == SyncBackendKind.SYNCTHING &&
                (SyncthingEndpointCodec.decode(other.destinationUri)?.folderId == endpoint.folderId ||
                    SyncEndpointValidator.scopesOverlap(local,
                        java.io.File(java.net.URI(other.sourceUri)).canonicalFile.toURI().toString()))
        }) { "Another Syncthing profile already uses this folder ID or an overlapping local folder" }
    }

    fun folderMode(profile: SyncProfile): SyncthingFolderMode {
        validate(profile)
        return when (profile.mode) {
            SyncMode.TWO_WAY -> SyncthingFolderMode.SEND_RECEIVE
            SyncMode.MIRROR -> SyncthingFolderMode.SEND_ONLY
            else -> error("Unsupported Syncthing sync mode")
        }
    }
}

/**
 * Boundary between WizeFiles' sync orchestration and an embedded Syncthing runtime.
 *
 * The engine owns Syncthing's database, block exchange, reconciliation, conflict files, and peer
 * protocol. WizeFiles owns profile persistence, scheduling, Android constraints, history, recovery,
 * and user-facing state.
 */
internal interface SyncthingEnginePort {
    fun ensureFolder(request: SyncthingFolderRequest): SyncthingEngineResult
    fun requestScan(profileId: String): SyncthingEngineResult
    fun folderStatus(profileId: String): SyncthingFolderStatus
    fun pause(profileId: String): SyncthingEngineResult
    fun resume(profileId: String): SyncthingEngineResult
    fun removeFolder(profileId: String): SyncthingEngineResult
}

internal data class SyncthingFolderRequest(
    val profileId: String,
    val localFolderUri: String,
    val endpoint: SyncthingEndpoint,
    val mode: SyncthingFolderMode,
    val ignorePatterns: List<String> = emptyList(),
    val keepVersions: Int = 5,
    val devices: List<SyncthingDevice>? = null,
    val folderOptions: String = "{}"
)

internal sealed interface SyncthingEngineResult {
    data object Success : SyncthingEngineResult
    data class Failure(
        val code: String,
        val message: String = ""
    ) : SyncthingEngineResult
}

internal enum class SyncthingFolderState {
    IDLE,
    SCANNING,
    SYNCING,
    PAUSED,
    DISCONNECTED,
    ERROR
}

internal data class SyncthingFolderStatus(
    val state: SyncthingFolderState,
    val localBytes: Long = 0,
    val remoteBytes: Long = 0,
    val pendingBytes: Long = 0,
    val pendingItems: Long = 0,
    val error: String = ""
) {
    init {
        require(localBytes >= 0)
        require(remoteBytes >= 0)
        require(pendingBytes >= 0)
        require(pendingItems >= 0)
    }
}
