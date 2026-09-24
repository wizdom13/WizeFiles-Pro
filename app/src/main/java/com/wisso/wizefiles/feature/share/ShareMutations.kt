package com.wisso.wizefiles.feature.share

import com.wisso.wizefiles.feature.transfer.LongRunningOperationLimiter
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toUriString
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

internal data class TrackedShareTransfer(val operationId:String,val itemId:Long)

internal object ShareTransferTracker {
    fun beginDownload(source:ResolvedSharePath,size:Long):TrackedShareTransfer {
        val uri=source.path.toAppPath().toUriString()
        val op=TransferRepository.enqueue(TransferOperationSpec(
            type=TransferOperationType.COPY,
            sourceUris=listOf(uri),
            destinationUri="share://pc/download"
        ))
        TransferRepository.transition(op.id,TransferOperationState.PLANNING);TransferRepository.updatePlanSummary(op.id,1,size);TransferRepository.transition(op.id,TransferOperationState.RUNNING)
        val item=TransferDatabase.beginItem(op.id,uri,"share://pc/download",source.relativePath,false,size,0,fingerprint(source.path))
        return TrackedShareTransfer(op.id,item.id)
    }
    fun complete(transfer:TrackedShareTransfer,result:String){TransferDatabase.completeItem(transfer.itemId,result);TransferRepository.transition(transfer.operationId,TransferOperationState.COMPLETED)}
    fun fail(transfer:TrackedShareTransfer,t:Throwable){TransferDatabase.failItem(transfer.itemId,t.javaClass.simpleName,t.message.orEmpty());runCatching{TransferRepository.transition(transfer.operationId,TransferOperationState.FAILED,errorCategory=t.javaClass.simpleName,errorMessage=t.message.orEmpty())}}
}

internal class ShareMutationRunner(
    private val gateway:ShareGateway,
    private val sessionId:String,
    private val storage:StorageFacade=StorageFacade()
) {
    fun createDirectory(rootId:String,relativePath:String){val target=gateway.resolve(rootId,relativePath,ShareCapability.CREATE);require(!Files.exists(target.path,LinkOption.NOFOLLOW_LINKS));target.path.parent?.let(storage::createDirectories);storage.createDirectory(target.path)}

    fun copy(sourceRootId:String,sourcePath:String,targetRootId:String,targetPath:String):String {
        val source=gateway.resolve(sourceRootId,sourcePath,ShareCapability.READ);val target=gateway.resolve(targetRootId,targetPath,ShareCapability.CREATE);require(!Files.exists(target.path,LinkOption.NOFOLLOW_LINKS)){"Destination already exists"};val size=if(Files.isDirectory(source.path))0 else Files.size(source.path)
        val sourceUri=source.path.toAppPath().toUriString();val targetUri=target.path.toAppPath().toUriString();val operation=TransferRepository.enqueue(TransferOperationSpec(type=TransferOperationType.COPY,sourceUris=listOf(sourceUri),destinationUri=targetUri));TransferRepository.transition(operation.id,TransferOperationState.PLANNING);TransferRepository.updatePlanSummary(operation.id,1,size);val item=TransferDatabase.beginItem(operation.id,sourceUri,targetUri,source.relativePath,Files.isDirectory(source.path),size,0,fingerprint(source.path));TransferRepository.transition(operation.id,TransferOperationState.RUNNING);LongRunningOperationLimiter.acquire()
        try {target.path.parent?.let(storage::createDirectories);storage.copy(source.path,target.path);TransferDatabase.completeItem(item.id,targetUri);TransferRepository.transition(operation.id,TransferOperationState.COMPLETED);return operation.id}catch(t:Throwable){TransferDatabase.failItem(item.id,t.javaClass.simpleName,t.message.orEmpty());runCatching{TransferRepository.transition(operation.id,TransferOperationState.FAILED,errorCategory=t.javaClass.simpleName,errorMessage=t.message.orEmpty())};throw t}finally{LongRunningOperationLimiter.release()}
    }

    fun delete(rootId:String,relativePath:String):String {
        val source=gateway.resolve(rootId,relativePath,ShareCapability.DELETE)
        return trackedMutation(TransferOperationType.DELETE,source,"share://pc/delete") {
            storage.delete(source.path)
        }
    }

    fun move(
        sourceRootId:String,
        sourcePath:String,
        targetRootId:String,
        targetPath:String
    ):String {
        val source=gateway.resolve(sourceRootId,sourcePath,ShareCapability.DELETE)
        val target=gateway.resolve(targetRootId,targetPath,ShareCapability.CREATE)
        require(!Files.exists(target.path,LinkOption.NOFOLLOW_LINKS)) {
            "Destination already exists"
        }
        val targetUri=target.path.toAppPath().toUriString()
        return trackedMutation(TransferOperationType.MOVE,source,targetUri) {
            target.path.parent?.let(storage::createDirectories)
            storage.move(source.path,target.path)
        }
    }

    fun upload(
        uploadId:String?,clientId:String,rootId:String,relativePath:String,offset:Long,totalBytes:Long,
        contentLength:Long,input:InputStream
    ):ShareUploadCheckpoint {
        require(totalBytes>=0&&contentLength>=0&&offset>=0&&offset+contentLength<=totalBytes)
        val target=gateway.resolve(rootId,relativePath,if(Files.exists(gateway.resolve(rootId,relativePath,ShareCapability.CREATE).path))ShareCapability.UPDATE else ShareCapability.CREATE)
        val previous=uploadId?.let(ShareDatabase::upload)
        require(previous==null || previous.rootId==rootId && previous.relativePath==target.relativePath) { "Upload checkpoint does not match this target" }
        val operation=previous?.transferOperationId?.let(TransferRepository::operation) ?: TransferRepository.enqueue(TransferOperationSpec(type=TransferOperationType.COPY,sourceUris=listOf("share://pc/upload/${target.path.fileName}"),destinationUri=target.path.toAppPath().toUriString()))
        val temporary=previous?.temporaryUri?.let(com.wisso.wizefiles.feature.sync.SyncPathResolver::resolve) ?: target.path.resolveSibling(".wizefiles-share-${operation.id.take(8)}.part")
        requireNotNull(temporary); val actual=if(Files.exists(temporary))Files.size(temporary) else 0L;require(actual==offset&&previous?.completedBytes?.let{it==offset}?:true){"Upload offset mismatch"}
        target.path.parent?.let(storage::createDirectories)
        if(previous==null){TransferRepository.transition(operation.id,TransferOperationState.PLANNING);TransferRepository.updatePlanSummary(operation.id,1,totalBytes);TransferRepository.transition(operation.id,TransferOperationState.RUNNING);TransferDatabase.beginItem(operation.id,"share://pc/upload",target.path.toAppPath().toUriString(),relativePath,false,totalBytes,0,"f:$totalBytes:0")}
        LongRunningOperationLimiter.acquire()
        try {Files.newOutputStream(temporary,StandardOpenOption.CREATE,StandardOpenOption.APPEND).use { out ->copyExactly(input,out,contentLength)} } finally {LongRunningOperationLimiter.release()}
        val completed=offset+contentLength
        val checkpoint=ShareUploadCheckpoint(uploadId?:operation.id,sessionId,rootId,relativePath,temporary.toAppPath().toUriString(),totalBytes,completed,operation.id)
        ShareDatabase.saveUpload(checkpoint)
        if(completed==totalBytes){
            if(Files.exists(target.path,LinkOption.NOFOLLOW_LINKS)&&gateway.destructiveApprovalRequired){
                requestDestructive(clientId,PendingShareActionType.OVERWRITE,rootId,relativePath,uploadId=checkpoint.id)
                return checkpoint.copy(waitingForApproval=true)
            }
            finalizeUpload(checkpoint,target.path)
        }
        return checkpoint
    }

    fun requestDestructive(clientId:String,type:PendingShareActionType,sourceRootId:String,sourcePath:String,targetRootId:String="",targetPath:String="",uploadId:String=""):PendingShareAction {
        val source=gateway.resolve(sourceRootId,sourcePath,when(type){PendingShareActionType.OVERWRITE->ShareCapability.UPDATE;else->ShareCapability.DELETE})
        val action=PendingShareAction(sessionId=sessionId,clientId=clientId,type=type,sourceRootId=sourceRootId,sourceRelativePath=sourcePath,targetRootId=targetRootId,targetRelativePath=targetPath,expectedRevision=fingerprint(source.path),uploadId=uploadId);ShareDatabase.savePendingAction(action);return action
    }

    fun executeApproved(action:PendingShareAction) {
        val source=gateway.resolve(action.sourceRootId,action.sourceRelativePath,if(action.type==PendingShareActionType.OVERWRITE)ShareCapability.UPDATE else ShareCapability.DELETE);require(fingerprint(source.path)==action.expectedRevision){"Item changed while approval was pending"}
        try {when(action.type){
            PendingShareActionType.DELETE->delete(action.sourceRootId,action.sourceRelativePath)
            PendingShareActionType.RENAME,PendingShareActionType.MOVE->move(
                action.sourceRootId,
                action.sourceRelativePath,
                action.targetRootId.ifBlank { action.sourceRootId },
                action.targetRelativePath
            )
            PendingShareActionType.OVERWRITE->finalizeUpload(requireNotNull(ShareDatabase.upload(action.uploadId)){"Upload checkpoint missing"},source.path)
        };ShareDatabase.setPendingActionState(action.id,PendingShareActionState.COMPLETED)}catch(t:Throwable){ShareDatabase.setPendingActionState(action.id,PendingShareActionState.FAILED);throw t}
    }

    private fun trackedMutation(
        type:TransferOperationType,
        source:ResolvedSharePath,
        destinationUri:String,
        block:()->Unit
    ):String {
        val sourceUri=source.path.toAppPath().toUriString()
        val isDirectory=Files.isDirectory(source.path,LinkOption.NOFOLLOW_LINKS)
        val size=if(isDirectory) 0 else Files.size(source.path)
        val operation=TransferRepository.enqueue(
            TransferOperationSpec(type=type,sourceUris=listOf(sourceUri),destinationUri=destinationUri)
        )
        TransferRepository.transition(operation.id,TransferOperationState.PLANNING)
        TransferRepository.updatePlanSummary(operation.id,1,size)
        val item=TransferDatabase.beginItem(
            operation.id,
            sourceUri,
            destinationUri,
            source.relativePath,
            isDirectory,
            size,
            0,
            fingerprint(source.path)
        )
        TransferRepository.transition(operation.id,TransferOperationState.RUNNING)
        LongRunningOperationLimiter.acquire()
        try {
            block()
            TransferDatabase.completeItem(item.id,destinationUri)
            TransferRepository.transition(operation.id,TransferOperationState.COMPLETED)
            return operation.id
        } catch(t:Throwable) {
            TransferDatabase.failItem(item.id,t.javaClass.simpleName,t.message.orEmpty())
            runCatching {
                TransferRepository.transition(
                    operation.id,
                    TransferOperationState.FAILED,
                    errorCategory=t.javaClass.simpleName,
                    errorMessage=t.message.orEmpty()
                )
            }
            throw t
        } finally {
            LongRunningOperationLimiter.release()
        }
    }

    private fun finalizeUpload(checkpoint:ShareUploadCheckpoint,target:java.nio.file.Path){
        val temporary=requireNotNull(com.wisso.wizefiles.feature.sync.SyncPathResolver.resolve(checkpoint.temporaryUri)){"Upload temporary item unavailable"}
        require(checkpoint.completedBytes==checkpoint.expectedBytes&&Files.size(temporary)==checkpoint.expectedBytes){"Upload is incomplete"}
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))storage.delete(target)
        storage.move(temporary,target,StandardCopyOption.REPLACE_EXISTING)
        val item=requireNotNull(TransferRepository.items(checkpoint.transferOperationId).firstOrNull());TransferDatabase.completeItem(item.id,target.toAppPath().toUriString());TransferRepository.transition(checkpoint.transferOperationId,TransferOperationState.COMPLETED);ShareDatabase.deleteUpload(checkpoint.id)
    }

    private fun copyExactly(input:InputStream,output:java.io.OutputStream,length:Long){val buffer=ByteArray(64*1024);var remaining=length;while(remaining>0){val read=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());check(read>0){"Upload ended early"};output.write(buffer,0,read);remaining-=read}}
}

internal fun fingerprint(path:java.nio.file.Path):String {val a=Files.readAttributes(path,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS);return "${if(a.isDirectory)"d" else "f"}:${if(a.isDirectory)0 else a.size()}:${a.lastModifiedTime().toMillis()}"}
