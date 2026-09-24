// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.share

import java.util.UUID

internal const val SHARE_PATH_SCHEMA_VERSION = 1

enum class ShareProtocol { BROWSER, FTP, FTPS }

enum class SharePermission {
    VIEW_AND_DOWNLOAD,
    EXCHANGE_FILES,
    FULL_MANAGEMENT;

    fun allows(required: ShareCapability): Boolean = when (required) {
        ShareCapability.READ -> true
        ShareCapability.CREATE -> this != VIEW_AND_DOWNLOAD
        ShareCapability.UPDATE, ShareCapability.DELETE -> this == FULL_MANAGEMENT
    }
}

enum class ShareCapability { READ, CREATE, UPDATE, DELETE }

enum class ShareSessionState { STARTING, ACTIVE, STOPPED, FAILED }

enum class ShareStopReason {
    USER,
    INACTIVITY,
    NETWORK_CHANGED,
    PROCESS_TERMINATED,
    ERROR
}

data class ShareProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocols: Set<ShareProtocol> = setOf(ShareProtocol.BROWSER),
    val browserPort: Int = 8080,
    val ftpPort: Int = 2121,
    val passivePortStart: Int = 50000,
    val passivePortEnd: Int = 50009,
    val permission: SharePermission = SharePermission.EXCHANGE_FILES,
    val approveDestructiveInBrowser: Boolean = true,
    val ftpWritable: Boolean = false,
    val inactivityMinutes: Int = 15,
    val certificateAlias: String = "",
    val pathSchemaVersion: Int = SHARE_PATH_SCHEMA_VERSION,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis
) {
    init {
        require(name.isNotBlank())
        require(protocols.isNotEmpty())
        require(browserPort in 1..65535 && ftpPort in 1..65535)
        require(passivePortStart in 1024..65535 && passivePortEnd in passivePortStart..65535)
        require(inactivityMinutes == 0 || inactivityMinutes in 5..1440)
        require(pathSchemaVersion > 0)
    }
}

data class ShareRoot(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val alias: String,
    val appPathUri: String,
    val providerIdentity: String,
    val readable: Boolean = true,
    val creatable: Boolean = true,
    val updatable: Boolean = true,
    val deletable: Boolean = true,
    val pathSchemaVersion: Int = SHARE_PATH_SCHEMA_VERSION
) {
    init {
        require(alias.isNotBlank() && appPathUri.isNotBlank() && providerIdentity.isNotBlank())
    }
}

data class ShareSession(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val networkIdentity: String,
    val state: ShareSessionState = ShareSessionState.STARTING,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val endedAtMillis: Long = 0,
    val stopReason: ShareStopReason? = null,
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val clientCount: Int = 0
)

data class ShareAuditEvent(
    val id: Long = 0,
    val sessionId: String,
    val clientId: String,
    val clientAddress: String,
    val operation: String,
    val friendlyPath: String,
    val result: String,
    val errorCategory: String = "",
    val transferOperationId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

internal data class ResolvedSharePath(
    val root: ShareRoot,
    val relativePath: String,
    val path: java.nio.file.Path
)

enum class PendingShareActionType { OVERWRITE, RENAME, MOVE, DELETE }
enum class PendingShareActionState { WAITING, APPROVED, REJECTED, COMPLETED, FAILED }

data class PendingShareAction(
    val id:String=UUID.randomUUID().toString(),
    val sessionId:String,
    val clientId:String,
    val type:PendingShareActionType,
    val sourceRootId:String,
    val sourceRelativePath:String,
    val targetRootId:String="",
    val targetRelativePath:String="",
    val expectedRevision:String,
    val uploadId:String="",
    val state:PendingShareActionState=PendingShareActionState.WAITING,
    val createdAtMillis:Long=System.currentTimeMillis()
)

data class ShareUploadCheckpoint(
    val id:String=UUID.randomUUID().toString(),
    val sessionId:String,
    val rootId:String,
    val relativePath:String,
    val temporaryUri:String,
    val expectedBytes:Long,
    val completedBytes:Long,
    val transferOperationId:String,
    val updatedAtMillis:Long=System.currentTimeMillis(),
    val waitingForApproval:Boolean=false
)
