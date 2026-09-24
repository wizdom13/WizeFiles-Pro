package com.wisso.wizefiles.feature.transfer

import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.file.Path

internal fun resolveOpenDestination(operation: TransferOperationRecord): Path? {
    val resolvedPath = operation.destinationUri
        .toAppPathOrNull()
        ?.toLegacyPathOrNull()
    return resolvedPath?.takeIf {
        shouldOfferOpenDestination(operation.type, resolvedPath)
    }
}

internal fun shouldOfferOpenDestination(
    type: TransferOperationType,
    resolvedPath: Path?
): Boolean = type !in setOf(
    TransferOperationType.NEARBY_SEND,
    TransferOperationType.DELETE
) && resolvedPath != null
