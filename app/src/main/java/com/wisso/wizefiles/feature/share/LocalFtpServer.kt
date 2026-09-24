// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.share

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.wisso.wizefiles.feature.transfer.LongRunningOperationLimiter
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toUriString
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

internal class LocalFtpServer(
    private val address:InetAddress,
    private val port:Int,
    private val passiveRange:IntRange,
    private val gateway:ShareGateway,
    sessionId:String,
    private val password:String,
    private val tls:SSLContext?,
    private val requireTls:Boolean=false,
    private val onBytesDownloaded:(Long)->Unit={},
    private val onBytesUploaded:(Long)->Unit={}
):AutoCloseable{
    private val running=AtomicBoolean(false)
    private val acceptor=Executors.newSingleThreadExecutor()
    private val clients=BoundedShareClientPool(MAX_CLIENTS)
    private val connected=AtomicInteger()
    private var server:ServerSocket?=null
    private val storage=StorageFacade()
    private val mutations=ShareMutationRunner(gateway,sessionId)
    @Volatile var lastActivityMillis:Long=System.currentTimeMillis();private set
    val connectedClients:Int get()=connected.get()
    fun start(){
        check(running.compareAndSet(false,true))
        server=ServerSocket(port,16,address)
        acceptor.execute {
            while(running.get()) {
                val client=runCatching { server?.accept() }.getOrNull() ?: continue
                if(!clients.execute {
                    connected.incrementAndGet()
                    try { session(client) } finally { connected.decrementAndGet() }
                }) {
                    runCatching { client.close() }
                }
            }
        }
    }
    override fun close(){running.set(false);runCatching{server?.close()};acceptor.shutdownNow();clients.close()}

    private fun session(initial:Socket){
        initial.soTimeout=60_000
        var socket=initial;var reader=reader(socket);var writer=writer(socket);var authenticated=false;var authFailures=0;var userSeen=false;var cwd="/";var rest=0L;var passive:ServerSocket?=null;var protectedData=false;var renameFrom:String?=null;var tlsActive=false
        fun reply(code:Int,message:String){writer.write("$code ${ftpName(message)}\r\n");writer.flush()}
        fun dataSocket():Socket{val listener=passive?:error("Use PASV first");val plain=listener.accept();listener.close();passive=null;if(!protectedData)return plain;val ssl=requireNotNull(tls).socketFactory.createSocket(plain,plain.inetAddress.hostAddress,plain.port,true) as SSLSocket;ssl.useClientMode=false;ssl.startHandshake();return ssl}
        socket.use{
            reply(220,"WizeFiles local sharing ready")
            loop@ while(running.get()){
                val line=reader.readLine()?:break;lastActivityMillis=System.currentTimeMillis();val command=line.substringBefore(' ').uppercase();val argument=line.substringAfter(' ',"").trim()
                try {when(command){
                    "USER"->{if(requireTls&&!tlsActive){reply(534,"TLS is required")}else if(argument.equals("anonymous",true)){reply(530,"Anonymous access is disabled")}else{userSeen=argument=="wizefiles";reply(if(userSeen)331 else 530,if(userSeen)"Password required" else "Unknown user")}}
                    "PASS"->{authenticated=userSeen&&constant(argument,password);if(!authenticated&&++authFailures>=5){reply(421,"Too many authentication failures");break@loop};reply(if(authenticated)230 else 530,if(authenticated)"Logged in" else "Authentication failed")}
                    "AUTH"->{require(argument.equals("TLS",true)&&tls!=null);reply(234,"Starting TLS");val upgraded=tls.socketFactory.createSocket(socket,socket.inetAddress.hostAddress,socket.port,true) as SSLSocket;upgraded.useClientMode=false;upgraded.startHandshake();socket=upgraded;reader=reader(socket);writer=writer(socket);tlsActive=true}
                    "PBSZ"->{require(authenticated);reply(200,"PBSZ=0")}
                    "PROT"->{require(authenticated&&tls!=null);protectedData=argument.equals("P",true);reply(200,if(protectedData)"Private data channel" else "Clear data channel")}
                    "QUIT"->{reply(221,"Goodbye");break@loop}
                    "NOOP"->reply(200,"OK")
                    "SYST"->reply(215,"UNIX Type: L8")
                    "OPTS"->reply(200,"UTF8 enabled")
                    "FEAT"->{writer.write("211-Features\r\n UTF8\r\n REST STREAM\r\n MLSD\r\n${if(tls!=null)" AUTH TLS\r\n PBSZ\r\n PROT\r\n" else ""}211 End\r\n");writer.flush()}
                    else->{require(authenticated){"Authenticate first"};when(command){
                        "PWD"->reply(257,"\"$cwd\"")
                        "CWD"->{val target=ftpPath(cwd,argument);resolve(target,ShareCapability.READ);cwd=target;reply(250,"Directory changed")}
                        "CDUP"->{cwd=cwd.substringBeforeLast('/').ifBlank{"/"};reply(250,"Directory changed")}
                        "TYPE"->reply(200,"Binary mode")
                        "PASV"->{passive?.close();passive=openPassive();val bytes=address.address;val p=passive!!.localPort;reply(227,"Entering Passive Mode (${bytes.joinToString(","){(it.toInt() and 255).toString()}},${p/256},${p%256})")}
                        "EPSV"->{passive?.close();passive=openPassive();reply(229,"Entering Extended Passive Mode (|||${passive!!.localPort}|)")}
                        "PORT","EPRT"->reply(502,"Active mode is not supported")
                        "REST"->{rest=argument.toLong();require(rest>=0);reply(350,"Restart position accepted")}
                        "LIST","MLSD"->{reply(150,"Opening data connection");dataSocket().use{data->BufferedWriter(OutputStreamWriter(data.getOutputStream())).use{out->if(cwd=="/"&&argument.isBlank()){gateway.publicRoots().forEach{(_,alias)->val name=ftpName(alias);out.write(if(command=="MLSD")"type=dir;size=0; $name\r\n" else "drw-r--r-- 1 owner group 0 Jan 01 00:00 $name\r\n")}}else{val item=resolve(ftpPath(cwd,argument),ShareCapability.READ);Files.newDirectoryStream(item.path).use{stream->stream.filterNot{it.fileName.toString().startsWith(".wizefiles-")}.forEach{p->val dir=Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS);val size=if(dir)0 else Files.size(p);val name=ftpName(p.fileName.toString());out.write(if(command=="MLSD")"type=${if(dir)"dir" else "file"};size=$size; $name\r\n" else "${if(dir)"d" else "-"}rw-r--r-- 1 owner group $size Jan 01 00:00 $name\r\n")}}}}};reply(226,"Transfer complete")}
                        "SIZE"->{val item=resolve(ftpPath(cwd,argument),ShareCapability.READ);reply(213,Files.size(item.path).toString())}
                        "RETR"->{val item=resolve(ftpPath(cwd,argument),ShareCapability.READ);val size=Files.size(item.path);require(rest<=size);val transfer=ShareTransferTracker.beginDownload(item,size-rest);reply(150,"Opening data connection");LongRunningOperationLimiter.acquire();try{dataSocket().use{data->Files.newByteChannel(item.path,StandardOpenOption.READ).use{channel->channel.position(rest);java.nio.channels.Channels.newInputStream(channel).use{onBytesDownloaded(it.copyTo(data.getOutputStream(),64*1024))}}};ShareTransferTracker.complete(transfer,"share://ftp/download");rest=0;reply(226,"Transfer complete")}catch(t:Throwable){ShareTransferTracker.fail(transfer,t);throw t}finally{LongRunningOperationLimiter.release()}}
                        "STOR","APPE"->{require(gateway.ftpWritable){"FTP is read-only"};val candidate=resolve(ftpPath(cwd,argument),ShareCapability.CREATE);val item=if(Files.exists(candidate.path,LinkOption.NOFOLLOW_LINKS))resolve(ftpPath(cwd,argument),ShareCapability.UPDATE)else candidate;item.path.parent?.let(storage::createDirectories);val uri=item.path.toAppPath().toUriString();val operation=TransferRepository.enqueue(TransferOperationSpec(type=TransferOperationType.COPY,sourceUris=listOf("share://ftp/upload"),destinationUri=uri));TransferRepository.transition(operation.id,TransferOperationState.PLANNING);val record=TransferDatabase.beginItem(operation.id,"share://ftp/upload",uri,item.relativePath,false,0,0,"f:0:0");TransferRepository.transition(operation.id,TransferOperationState.RUNNING);reply(150,"Opening data connection");LongRunningOperationLimiter.acquire();try{dataSocket().use{data->Files.newOutputStream(item.path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,if(rest>0||command=="APPE")StandardOpenOption.APPEND else StandardOpenOption.TRUNCATE_EXISTING).use{out->onBytesUploaded(data.getInputStream().copyTo(out,64*1024))}};TransferDatabase.completeItem(record.id,uri);TransferRepository.transition(operation.id,TransferOperationState.COMPLETED);rest=0;reply(226,"Transfer complete")}catch(t:Throwable){TransferDatabase.failItem(record.id,t.javaClass.simpleName,t.message.orEmpty());runCatching{TransferRepository.transition(operation.id,TransferOperationState.FAILED,errorCategory=t.javaClass.simpleName,errorMessage=t.message.orEmpty())};throw t}finally{LongRunningOperationLimiter.release()}}
                        "MKD"->{require(gateway.ftpWritable);storage.createDirectory(resolve(ftpPath(cwd,argument),ShareCapability.CREATE).path);reply(257,"Created")}
                        "DELE","RMD"->{
                            require(gateway.ftpWritable)
                            val (rootId,relativePath)=mutationPath(ftpPath(cwd,argument))
                            mutations.delete(rootId,relativePath)
                            reply(250,"Deleted")
                        }
                        "RNFR"->{require(gateway.ftpWritable);resolve(ftpPath(cwd,argument),ShareCapability.DELETE);renameFrom=ftpPath(cwd,argument);reply(350,"Destination name required")}
                        "RNTO"->{
                            require(gateway.ftpWritable)
                            val (sourceRootId,sourcePath)=mutationPath(requireNotNull(renameFrom))
                            val (targetRootId,targetPath)=mutationPath(ftpPath(cwd,argument))
                            mutations.move(sourceRootId,sourcePath,targetRootId,targetPath)
                            renameFrom=null
                            reply(250,"Renamed")
                        }
                        "MODE","STRU"->reply(200,"OK")
                        else->reply(502,"Command not implemented")
                    }}
                }}catch(t:Throwable){reply(if(authenticated)550 else 530,t.message?:"Command failed")}
            }
        };passive?.close()
    }

    private fun resolve(path:String,capability:ShareCapability):ResolvedSharePath {
        val (rootId,relativePath)=mutationPath(path)
        return gateway.resolve(rootId,relativePath,capability)
    }
    private fun mutationPath(path:String):Pair<String,String> {
        val parts=path.trim('/').split('/',limit=2)
        require(parts.firstOrNull()?.isNotBlank()==true) { "Choose a shared root" }
        val rootId=requireNotNull(gateway.rootIdForToken(parts[0])) { "Unknown shared root" }
        return rootId to parts.getOrElse(1) { "" }
    }
    private fun ftpPath(cwd:String,value:String):String = normalizeFtpPath(cwd,value)
    private fun openPassive():ServerSocket {passiveRange.forEach{p->runCatching{return ServerSocket(p,1,address)}};error("No passive FTP port is available")}
    private fun reader(socket:Socket)=BufferedReader(InputStreamReader(socket.getInputStream(),Charsets.UTF_8))
    private fun writer(socket:Socket)=BufferedWriter(OutputStreamWriter(socket.getOutputStream(),Charsets.UTF_8))
    private fun constant(a:String,b:String)=java.security.MessageDigest.isEqual(a.toByteArray(),b.toByteArray())

    companion object {
        private const val MAX_CLIENTS=8
    }
}

internal fun normalizeFtpPath(cwd:String,value:String):String {val raw=if(value.isBlank())cwd else if(value.startsWith('/'))value else "$cwd/$value";return "/"+SharePathSecurity.canonicalRelativePath(raw.trimStart('/'))}
private fun ftpName(value:String)=value.replace('\r','?').replace('\n','?')
