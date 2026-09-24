package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.storage.FileOperationFailure
import com.wisso.wizefiles.storage.ProviderFailureMapper
import com.wisso.wizefiles.storage.local.toProviderFailureSignal
import java.io.IOException

/** Translates provider/JVM exceptions once, before UI retry policy is applied. */
internal object ProviderFailureBoundary {
    fun map(exception: IOException, mutationStarted: Boolean = false): FileOperationFailure {
        val signal = exception.toProviderFailureSignal()
        return ProviderFailureMapper.map(signal, exception.message, mutationStarted)
    }
}
