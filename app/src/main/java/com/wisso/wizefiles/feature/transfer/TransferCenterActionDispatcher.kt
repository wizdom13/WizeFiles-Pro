package com.wisso.wizefiles.feature.transfer

internal fun resolveTransferCenterPrimaryAction(
    operation: TransferOperationRecord,
    canOpenResult: Boolean
): TransferCenterPrimaryAction {
    val action = TransferCenterPolicy.primaryAction(operation.type, operation.state)
    return if (action == TransferCenterPrimaryAction.OPEN_RESULT && !canOpenResult) {
        TransferCenterPrimaryAction.NONE
    } else {
        action
    }
}

/** Executes neutral Transfer Center actions through app-owned effects. */
internal class TransferCenterActionDispatcher(
    private val pauseFileOperation: (String) -> Unit,
    private val resumeFileOperation: (String) -> Unit,
    private val cancelFileOperation: (String) -> Unit,
    private val pauseNearby: (String) -> Unit,
    private val cancelNearby: (String) -> Unit,
    private val openNearbyRecovery: (String) -> Unit,
    private val openSigningRecovery: (TransferOperationType, String) -> Unit,
    private val openResult: (TransferOperationRecord) -> Unit,
    private val openDetails: (String) -> Unit
) {
    fun primary(operation: TransferOperationRecord, action: TransferCenterPrimaryAction): Boolean {
        when (action) {
            TransferCenterPrimaryAction.NONE -> return false
            TransferCenterPrimaryAction.PAUSE ->
                if (TransferCenterPolicy.isNearby(operation.type)) pauseNearby(operation.id)
                else pauseFileOperation(operation.id)
            TransferCenterPrimaryAction.RESUME,
            TransferCenterPrimaryAction.RETRY -> resumeFileOperation(operation.id)
            TransferCenterPrimaryAction.OPEN_RESULT -> openResult(operation)
            TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW ->
                if (TransferCenterPolicy.isNearby(operation.type)) openNearbyRecovery(operation.id)
                else if (TransferCenterPolicy.isSigning(operation.type)) {
                    openSigningRecovery(operation.type, operation.id)
                } else {
                    return false
                }
        }
        return true
    }

    fun secondary(operation: TransferOperationRecord): Boolean {
        when (TransferCenterPolicy.secondaryAction(operation.state)) {
            TransferCenterSecondaryAction.CANCEL ->
                if (TransferCenterPolicy.isNearby(operation.type)) cancelNearby(operation.id)
                else cancelFileOperation(operation.id)
            TransferCenterSecondaryAction.DETAILS -> openDetails(operation.id)
        }
        return true
    }
}
