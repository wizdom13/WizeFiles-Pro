package com.wisso.wizefiles.feature.transfer

import java.util.concurrent.Semaphore

internal object LongRunningOperationLimiter {
    private val permits = Semaphore(TransferExecutionPolicy.MAXIMUM_CONCURRENT_TRANSFERS, true)

    fun acquire() = permits.acquire()

    fun release() = permits.release()

    fun availablePermitsForTests(): Int = permits.availablePermits()
}
