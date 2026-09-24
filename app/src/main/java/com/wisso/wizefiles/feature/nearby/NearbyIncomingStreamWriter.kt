// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import com.google.android.gms.nearby.connection.Payload
import com.wisso.wizefiles.feature.sync.SyncPathResolver
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferItemRecord
import com.wisso.wizefiles.feature.transfer.TransferItemState
import com.wisso.wizefiles.feature.transfer.TransferProgress
import com.wisso.wizefiles.feature.transfer.TransferProgressCheckpoint
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toUriString
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal data class NearbyIncomingStream(
    val sessionId:String,
    val itemId:String,
    val payloadId:Long,
    val offset:Long,
    val length:Long
)

internal class NearbyIncomingStreamWriter(
    private val operationId:()->String,
    private val activeSessionId:()->String,
    private val conflictPolicy:()->NearbyConflictPolicy,
    private val totalBytes:()->Long,
    private val pauseRequested:()->Boolean,
    private val sendControl:(ByteArray)->Unit,
    private val onProgress:(TransferItemRecord,Long,Long)->Unit,
    private val onCompleted:(Boolean)->Unit,
    private val onFailure:(Throwable)->Unit
) {
    fun receive(payload:Payload,metadata:NearbyIncomingStream) {
        runCatching {
            val currentOperationId=operationId()
            val item=requireNotNull(
                NearbyTransferPlanner.itemForRemoteId(
                    currentOperationId,
                    metadata.sessionId,
                    metadata.itemId
                )
            ) { "Unknown incoming item" }
            require(!item.isDirectory && item.state!=TransferItemState.SKIPPED)
            require(metadata.offset+metadata.length==item.sizeBytes) {
                "Unexpected stream length"
            }
            val target=requireNotNull(SyncPathResolver.resolve(item.targetUri)) {
                "Destination vanished"
            }
            NearbyTransferPlanner.requireSafeReceivePath(item,target)
            target.parent?.let {
                NearbyTransferPlanner.requireSafeReceivePath(item,it)
                Files.createDirectories(it)
                NearbyTransferPlanner.requireSafeReceivePath(item,it)
            }
            val temporary=item.temporaryTargetUri.takeIf(String::isNotBlank)
                ?.let(SyncPathResolver::resolve)
                ?: target.resolveSibling(
                    ".wizefiles-part-${activeSessionId().take(8)}-${item.id}"
                ).also {
                    TransferDatabase.setTemporaryTarget(item.id,it.toAppPath().toUriString())
                }
            NearbyTransferPlanner.requireSafeReceivePath(item,temporary)
            if(metadata.offset==0L) Files.deleteIfExists(temporary)
            val existing=if(Files.exists(temporary)) Files.size(temporary) else 0L
            require(existing==metadata.offset) {
                "Resume checkpoint does not match temporary file"
            }
            requireNotNull(payload.asStream()).asInputStream().use { input ->
                Files.newOutputStream(
                    temporary,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                ).use { output ->
                    copyIncoming(input,output,item,metadata)
                }
            }
            NearbyTransferPlanner.finalizeReceived(item,temporary,conflictPolicy())
            sendControl(
                NearbyProtocol.fileComplete(activeSessionId(),metadata.itemId,item.sizeBytes)
            )
            val complete=TransferRepository.items(currentOperationId).all {
                it.state in setOf(TransferItemState.COPIED,TransferItemState.SKIPPED)
            }
            onCompleted(complete)
        }.onFailure {
            if(!pauseRequested()) onFailure(it)
        }
    }

    private fun copyIncoming(
        input:InputStream,
        output:java.io.OutputStream,
        item:TransferItemRecord,
        metadata:NearbyIncomingStream
    ) {
        val buffer=ByteArray(256*1024)
        var received=0L
        var acknowledged=metadata.offset
        while(received<metadata.length) {
            val read=input.read(
                buffer,
                0,
                minOf(buffer.size.toLong(),metadata.length-received).toInt()
            )
            check(read>0) { "Incoming stream ended early" }
            output.write(buffer,0,read)
            received+=read
            val offset=metadata.offset+received
            if(offset-acknowledged>=NEARBY_ACK_BYTES || received==metadata.length) {
                output.flush()
                checkpoint(item,offset)
                sendControl(NearbyProtocol.progress(activeSessionId(),metadata.itemId,offset))
                acknowledged=offset
            }
        }
    }

    private fun checkpoint(item:TransferItemRecord,offset:Long) {
        val currentOperationId=operationId()
        val operationBytes=TransferRepository.items(currentOperationId).sumOf {
            if(it.id==item.id) offset.coerceAtLeast(0) else it.bytesCompleted.coerceAtLeast(0)
        }
        val total=totalBytes()
        val speed=TransferProgress.tracker.sample(currentOperationId,operationBytes,total)
        TransferDatabase.checkpoint(
            TransferProgressCheckpoint(
                currentOperationId,
                item.id,
                offset,
                operationBytes,
                item.relativePath,
                speed.bytesPerSecond,
                speed.etaSeconds
            )
        )
        onProgress(item,operationBytes,total)
    }
}
