package com.wisso.wizefiles.feature.share

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.wisso.wizefiles.feature.transfer.LongRunningOperationLimiter

internal class LocalHttpServer(
    private val bindAddress:InetAddress,
    private val requestedPort:Int,
    private val gateway:ShareGateway,
    private val authenticator:ShareAuthenticator,
    private val sessionId:String,
    private val onBytesDownloaded:(Long)->Unit={},
    private val onBytesUploaded:(Long)->Unit={},
    private val onPendingAction:(PendingShareAction)->Unit={},
    private val tlsContext:javax.net.ssl.SSLContext?=null
) : AutoCloseable {
    private val running=AtomicBoolean(false)
    private val acceptor=Executors.newSingleThreadExecutor()
    private val clients=BoundedShareClientPool(MAX_CLIENTS)
    private var socket:ServerSocket?=null
    val port:Int get()=socket?.localPort ?: requestedPort
    @Volatile var lastActivityMillis:Long=System.currentTimeMillis();private set
    val authenticatedClients:Int get()=authenticator.activeSessionCount()
    fun activeClientIds():List<String> = authenticator.activeClients()
    fun revokeClient(clientId:String)=authenticator.revokeClient(clientId)

    fun start() {
        check(running.compareAndSet(false,true))
        socket=if(tlsContext==null)ServerSocket(requestedPort,32,bindAddress) else (tlsContext.serverSocketFactory.createServerSocket(requestedPort,32,bindAddress) as javax.net.ssl.SSLServerSocket).apply{enabledProtocols=enabledProtocols.filterNot{it.contains("SSL")}.toTypedArray()}
        acceptor.execute {
            while(running.get()) {
                val client=runCatching { socket?.accept() }.getOrNull() ?: continue
                if(!clients.execute { handle(client) }) {
                    runCatching { client.close() }
                }
            }
        }
    }

    override fun close(){running.set(false);runCatching{socket?.close()};acceptor.shutdownNow();clients.close();authenticator.revokeAll()}

    private fun handle(client:Socket)=client.use { socket ->
        socket.soTimeout=30_000
        val input=BufferedInputStream(socket.getInputStream()); val output=BufferedOutputStream(socket.getOutputStream())
        val clientAddress=socket.inetAddress.hostAddress.orEmpty()
        var requestForAudit:HttpRequest?=null
        var authenticated:BrowserSession?=null
        try {
            val request=HttpRequest.parse(input) ?: return@use
            requestForAudit=request
            lastActivityMillis=System.currentTimeMillis()
            if(request.method=="POST" && request.path=="/pair") return@use pair(request,clientAddress,output)
            if(request.path=="/") return@use text(output,200,"text/html; charset=utf-8",LocalShareWebApp.HTML)
            val session=authenticator.authenticate(request.headers["cookie"]) ?: return@use text(output,401,"application/json","{\"error\":\"authentication_required\"}")
            authenticated=session
            when {
                request.method=="GET" && request.path=="/api/roots" -> roots(output)
                request.method=="GET" && request.path=="/api/list" -> list(request,output)
                request.method=="GET" && request.path=="/api/search" -> search(request,output)
                request.method=="GET" && request.path=="/api/download" -> download(request,output)
                request.method=="GET" && request.path=="/api/preview" -> preview(request,output)
                request.method=="GET" && request.path=="/api/zip" -> zip(request,output)
                request.method=="GET" && request.path=="/api/session" -> text(output,200,"application/json","{\"csrf\":\"${json(session.csrf)}\",\"permission\":\"${gateway.permission.name}\"}")
                request.method=="GET" && request.path=="/api/clients" -> text(output,200,"application/json",authenticator.activeClients().joinToString(prefix="[",postfix="]"){"\"${json(it)}\""})
                request.method=="POST" && request.path=="/api/folder" -> { requireCsrf(request,session); createFolder(request,output) }
                request.method=="PUT" && request.path=="/api/upload" -> { requireCsrf(request,session); upload(request,input,output,session) }
                request.method=="POST" && request.path=="/api/action" -> { requireCsrf(request,session); requestAction(request,clientAddress,output) }
                request.method=="POST" && request.path=="/api/copy" -> { requireCsrf(request,session); copy(request,output) }
                else -> text(output,404,"application/json","{\"error\":\"not_found\"}")
            }
            ShareDatabase.audit(ShareAuditEvent(sessionId=sessionId,clientId=session.clientId,clientAddress=clientAddress,operation="${request.method} ${request.path}",friendlyPath=request.query["path"].orEmpty(),result="SUCCESS"))
        } catch(t:Exception){
            runCatching {
                authenticated?.let { session ->
                    requestForAudit?.let { request ->
                        ShareDatabase.audit(
                            ShareAuditEvent(
                                sessionId=sessionId,
                                clientId=session.clientId,
                                clientAddress=clientAddress,
                                operation="${request.method} ${request.path}",
                                friendlyPath=request.query["path"].orEmpty(),
                                result="FAILED",
                                errorCategory=t.javaClass.simpleName
                            )
                        )
                    }
                }
            }
            // A browser can connect and then go idle before sending a complete request.
            // Treat that timeout as a normal disconnect, and never let a failed error
            // response escape the client executor.
            if(t !is SocketTimeoutException) {
                runCatching {
                    text(output,400,"application/json","{\"error\":\"${json(t.message?:"request_failed")}\"}")
                }
            }
        }
    }

    private fun pair(request:HttpRequest,client:String,output:BufferedOutputStream){
        val credential=request.query["code"].orEmpty().ifBlank { request.query["token"].orEmpty() }
        val session=authenticator.pair(client,credential) ?: return text(output,403,"application/json","{\"error\":\"pairing_failed\"}")
        val body="{\"csrf\":\"${session.csrf}\",\"permission\":\"${gateway.permission.name}\"}"
        response(output,200,"OK",mapOf("Content-Type" to "application/json","Set-Cookie" to "WIZESESSION=${session.id}; HttpOnly; SameSite=Strict; Path=/${if(tlsContext!=null)"; Secure" else ""}","Cache-Control" to "no-store","Content-Length" to body.toByteArray().size.toString()))
        output.write(body.toByteArray());output.flush()
    }

    private fun roots(output:BufferedOutputStream){
        val body=gateway.publicRoots().joinToString(prefix="[",postfix="]") { "{\"id\":\"${json(it.first)}\",\"name\":\"${json(it.second)}\"}" }
        text(output,200,"application/json",body)
    }

    private fun list(request:HttpRequest,output:BufferedOutputStream){
        val resolved=gateway.resolve(request.query["root"].orEmpty(),request.query["path"].orEmpty(),ShareCapability.READ)
        require(Files.isDirectory(resolved.path,LinkOption.NOFOLLOW_LINKS))
        val body=Files.newDirectoryStream(resolved.path).use { stream -> stream.toList().filterNot { it.fileName.toString().startsWith(".wizefiles-share-") || it.fileName.toString().startsWith(".wizefiles-part-") }.sortedBy { it.fileName.toString().lowercase() }.joinToString(prefix="[",postfix="]") { path ->
            val dir=Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS); val size=if(dir)0 else runCatching{Files.size(path)}.getOrDefault(0)
            "{\"name\":\"${json(path.fileName.toString())}\",\"directory\":$dir,\"size\":$size}"
        }}
        text(output,200,"application/json",body)
    }

    private fun search(request:HttpRequest,output:BufferedOutputStream){
        val resolved=gateway.resolve(request.query["root"].orEmpty(),request.query["path"].orEmpty(),ShareCapability.READ)
        val needle=request.query["q"].orEmpty()
        require(needle.isNotBlank())
        val matches=mutableListOf<String>()
        Files.walk(resolved.path).use { stream ->
            stream.filter { path ->
                path!=resolved.path &&
                    !Files.isSymbolicLink(path) &&
                    path.fileName.toString().contains(needle,ignoreCase=true)
            }.limit(500).forEach { path ->
                val relative=resolved.path.relativize(path).toString().replace('\\','/')
                val directory=Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)
                val size=if(directory) 0 else runCatching { Files.size(path) }.getOrDefault(0)
                matches+="{\"name\":\"${json(relative)}\",\"directory\":$directory,\"size\":$size}"
            }
        }
        text(output,200,"application/json",matches.joinToString(prefix="[",postfix="]"))
    }

    private fun preview(request:HttpRequest,output:BufferedOutputStream){val resolved=gateway.resolve(request.query["root"].orEmpty(),request.query["path"].orEmpty(),ShareCapability.READ);require(Files.isRegularFile(resolved.path));sendFile(output,resolved,false)}

    private fun zip(request:HttpRequest,output:BufferedOutputStream){
        val root=request.query["root"].orEmpty()
        val paths=request.query["paths"].orEmpty().split('|').filter(String::isNotBlank)
        require(paths.isNotEmpty())
        val temporary=Files.createTempFile("wizefiles-share-",".zip")
        LongRunningOperationLimiter.acquire()
        try {
            ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                val addedEntries=hashSetOf<String>()
                paths.forEach { relative ->
                    addZipItem(zip,gateway.resolve(root,relative,ShareCapability.READ),addedEntries)
                }
            }
            val synthetic=ResolvedSharePath(
                gateway.resolve(root,"",ShareCapability.READ).root,
                "WizeFiles.zip",
                temporary
            )
            sendFile(output,synthetic,true)
        } finally {
            Files.deleteIfExists(temporary)
            LongRunningOperationLimiter.release()
        }
    }

    private fun addZipItem(
        zip:ZipOutputStream,
        item:ResolvedSharePath,
        addedEntries:MutableSet<String>
    ) {
        val baseName=zipPath(
            listOf(item.root.alias,item.relativePath)
                .filter(String::isNotBlank)
                .joinToString("/")
        )
        require(baseName.isNotBlank()) { "The selected ZIP item has no safe name" }
        when {
            Files.isSymbolicLink(item.path) -> error("Symbolic links are not shared")
            Files.isRegularFile(item.path,LinkOption.NOFOLLOW_LINKS) -> {
                addZipFile(zip,item.path,baseName,addedEntries)
            }
            Files.isDirectory(item.path,LinkOption.NOFOLLOW_LINKS) -> {
                Files.walkFileTree(item.path,object:SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        directory:Path,
                        attributes:BasicFileAttributes
                    ):FileVisitResult {
                        require(!attributes.isSymbolicLink) { "Symbolic links are not shared" }
                        val suffix=if(directory==item.path) "" else
                            item.path.relativize(directory).toString()
                        val entryName=zipPath(
                            listOf(baseName,suffix).filter(String::isNotBlank).joinToString("/")
                        )+"/"
                        if(addedEntries.add(entryName)) {
                            zip.putNextEntry(ZipEntry(entryName))
                            zip.closeEntry()
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file:Path,
                        attributes:BasicFileAttributes
                    ):FileVisitResult {
                        require(!attributes.isSymbolicLink) { "Symbolic links are not shared" }
                        val entryName=zipPath(
                            "$baseName/${item.path.relativize(file)}"
                        )
                        addZipFile(zip,file,entryName,addedEntries)
                        return FileVisitResult.CONTINUE
                    }
                })
            }
            else -> error("The selected ZIP item is unavailable")
        }
    }

    private fun addZipFile(
        zip:ZipOutputStream,
        file:Path,
        entryName:String,
        addedEntries:MutableSet<String>
    ) {
        if(!addedEntries.add(entryName)) return
        zip.putNextEntry(ZipEntry(entryName))
        Files.newInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun zipPath(value:String):String =
        value.replace('\\','_')
            .split('/')
            .filter(String::isNotBlank)
            .joinToString("/") { segment ->
                val clean=segment.filterNot(Char::isISOControl)
                when(clean) {
                    ".", ".." -> "_$clean"
                    else -> clean
                }
            }

    private fun sendFile(output:BufferedOutputStream,resolved:ResolvedSharePath,attachment:Boolean){val size=Files.size(resolved.path);val detected=Files.probeContentType(resolved.path);val headers=linkedMapOf("Content-Type" to if(attachment)(detected?:"application/octet-stream") else safePreviewMime(detected),"Content-Length" to size.toString(),"Accept-Ranges" to "bytes","Content-Disposition" to "${if(attachment)"attachment" else "inline"}; filename*=UTF-8''${percent(resolved.path.fileName.toString())}");response(output,200,"OK",headers);Files.newInputStream(resolved.path).use{it.copyTo(output,64*1024)};output.flush()}

    private fun download(request:HttpRequest,output:BufferedOutputStream){
        val resolved=gateway.resolve(request.query["root"].orEmpty(),request.query["path"].orEmpty(),ShareCapability.READ)
        require(Files.isRegularFile(resolved.path,LinkOption.NOFOLLOW_LINKS))
        val total=Files.size(resolved.path); val range=parseRange(request.headers["range"],total)
        val start=range?.first?:0; val end=range?.last?:total-1; val length=(end-start+1).coerceAtLeast(0)
        val transfer=ShareTransferTracker.beginDownload(resolved,length)
        val headers=linkedMapOf("Content-Type" to (Files.probeContentType(resolved.path)?:"application/octet-stream"),"Content-Length" to length.toString(),"Accept-Ranges" to "bytes","Content-Disposition" to "attachment; filename*=UTF-8''${percent(resolved.path.fileName.toString())}")
        if(range!=null) headers["Content-Range"]="bytes $start-$end/$total"
        response(output,if(range==null)200 else 206,if(range==null)"OK" else "Partial Content",headers)
        LongRunningOperationLimiter.acquire()
        try {Files.newByteChannel(resolved.path,StandardOpenOption.READ).use { channel ->
            channel.position(start); val buffer=java.nio.ByteBuffer.allocate(64*1024); var remaining=length
            while(remaining>0){buffer.clear();buffer.limit(minOf(buffer.capacity().toLong(),remaining).toInt());val read=channel.read(buffer);if(read<0)break;output.write(buffer.array(),0,read);remaining-=read;onBytesDownloaded(read.toLong())}
        };output.flush();ShareTransferTracker.complete(transfer,"share://pc/download")}catch(t:Throwable){ShareTransferTracker.fail(transfer,t);throw t}finally{LongRunningOperationLimiter.release()}
    }

    private fun createFolder(request:HttpRequest,output:BufferedOutputStream){ShareMutationRunner(gateway,sessionId).createDirectory(request.query["root"].orEmpty(),request.query["path"].orEmpty());text(output,201,"application/json","{\"created\":true}")}

    private fun upload(request:HttpRequest,input:BufferedInputStream,output:BufferedOutputStream,session:BrowserSession){
        val length=request.headers["content-length"]?.toLongOrNull()?:error("Content-Length required")
        require(length in 0..16L*1024*1024){"Upload chunk is too large"}
        val total=request.headers["upload-length"]?.toLongOrNull()?:length
        val offset=request.headers["upload-offset"]?.toLongOrNull()?:0
        val checkpoint=ShareMutationRunner(gateway,sessionId).upload(request.headers["upload-id"],session.clientId,request.query["root"].orEmpty(),request.query["path"].orEmpty(),offset,total,length,input)
        onBytesUploaded(length)
        if(checkpoint.waitingForApproval)ShareDatabase.pendingActions(sessionId).lastOrNull{it.uploadId==checkpoint.id}?.let(onPendingAction)
        val body="{\"uploadId\":\"${checkpoint.id}\",\"offset\":${checkpoint.completedBytes},\"complete\":${checkpoint.completedBytes==checkpoint.expectedBytes&&!checkpoint.waitingForApproval},\"waitingForApproval\":${checkpoint.waitingForApproval}}"
        text(output,if(checkpoint.waitingForApproval)202 else if(checkpoint.completedBytes==checkpoint.expectedBytes)201 else 200,"application/json",body)
    }

    private fun requestAction(request:HttpRequest,client:String,output:BufferedOutputStream){
        val action=ShareMutationRunner(gateway,sessionId).requestDestructive(client,PendingShareActionType.valueOf(request.query["type"].orEmpty().uppercase()),request.query["root"].orEmpty(),request.query["path"].orEmpty(),request.query["targetRoot"].orEmpty(),request.query["targetPath"].orEmpty())
        if(gateway.destructiveApprovalRequired){onPendingAction(action);text(output,202,"application/json","{\"actionId\":\"${action.id}\",\"state\":\"waiting_for_phone\"}")}
        else {ShareDatabase.setPendingActionState(action.id,PendingShareActionState.APPROVED);ShareMutationRunner(gateway,sessionId).executeApproved(action);text(output,200,"application/json","{\"actionId\":\"${action.id}\",\"state\":\"completed\"}")}
    }

    private fun copy(request:HttpRequest,output:BufferedOutputStream){val operation=ShareMutationRunner(gateway,sessionId).copy(request.query["root"].orEmpty(),request.query["path"].orEmpty(),request.query["targetRoot"].orEmpty(),request.query["targetPath"].orEmpty());text(output,202,"application/json","{\"operationId\":\"$operation\"}")}

    private fun requireCsrf(request:HttpRequest,session:BrowserSession){require(request.headers["x-wize-csrf"]==session.csrf){"CSRF validation failed"};request.headers["origin"]?.let{origin->val expected="${if(tlsContext==null)"http" else "https"}://${bindAddress.hostAddress}:$port";require(origin==expected){"Origin validation failed"}}}

    private fun text(out:BufferedOutputStream,status:Int,type:String,body:String){val bytes=body.toByteArray();response(out,status,if(status<300)"OK" else "Error",mapOf("Content-Type" to type,"Content-Length" to bytes.size.toString(),"Cache-Control" to "no-store"));out.write(bytes);out.flush()}
    private fun response(out:BufferedOutputStream,status:Int,reason:String,headers:Map<String,String>){out.write("HTTP/1.1 $status $reason\r\n".toByteArray());mapOf("X-Content-Type-Options" to "nosniff","Referrer-Policy" to "no-referrer","Permissions-Policy" to "camera=(), microphone=(), geolocation=()","Content-Security-Policy" to "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'").plus(headers).forEach{(k,v)->out.write("$k: $v\r\n".toByteArray())};out.write("Connection: close\r\n\r\n".toByteArray())}

    companion object {
        private const val MAX_CLIENTS=8
        internal fun parseRange(value:String?,size:Long):LongRange? {if(value==null)return null;require(value.startsWith("bytes=")&&!value.contains(','));val (a,b)=value.removePrefix("bytes=").split('-',limit=2);val start=a.toLong();val end=b.toLongOrNull()?.coerceAtMost(size-1)?:size-1;require(start in 0 until size&&end>=start);return start..end}
        internal fun safePreviewMime(value:String?):String {val mime=value?.lowercase().orEmpty();return if(mime=="application/pdf"||mime=="text/plain"||mime=="application/json"||mime.startsWith("audio/")||mime.startsWith("video/")||mime.startsWith("image/")&&mime!="image/svg+xml")mime else "application/octet-stream"}
        private fun json(value:String)=value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","")
        private fun percent(value:String)=java.net.URLEncoder.encode(value,StandardCharsets.UTF_8.name()).replace("+","%20")
        private const val WEB_APP="""<!doctype html><html><head><meta charset=utf-8><meta name=viewport content="width=device-width"><title>WizeFiles PC Access</title><style>body{font:16px system-ui;margin:2rem;max-width:1100px}button,input{font:inherit;padding:.6rem}li{padding:.55rem;border-bottom:1px solid #ddd}.hint{color:#666}</style></head><body><h1>WizeFiles PC Access</h1><div id=pair><p>Enter the code shown on your phone.</p><input id=code inputmode=numeric autocomplete=one-time-code><button onclick=pair()>Connect</button></div><main hidden><select id=roots></select><button onclick=load('')>Open</button><ul id=files></ul></main><p class=hint>Trusted local networks only.</p><script>async function pair(){let r=await fetch('/pair?code='+encodeURIComponent(code.value),{method:'POST'});if(!r.ok)return alert('Pairing failed');pair.hidden=true;document.querySelector('main').hidden=false;let a=await(await fetch('/api/roots')).json();roots.innerHTML=a.map(x=>'<option value="'+x.id+'">'+esc(x.name)+'</option>').join('');load('')}async function load(p){let a=await(await fetch('/api/list?root='+encodeURIComponent(roots.value)+'&path='+encodeURIComponent(p))).json();files.innerHTML=a.map(x=>'<li>'+(x.directory?'📁':'📄')+' '+esc(x.name)+'</li>').join('')}function esc(s){let d=document.createElement('div');d.textContent=s;return d.innerHTML}</script></body></html>"""
    }
}

internal data class HttpRequest(val method:String,val path:String,val query:Map<String,String>,val headers:Map<String,String>){
    companion object { fun parse(input:BufferedInputStream):HttpRequest? {val raw=ByteArrayOutputStream();var previous=0;while(raw.size()<32*1024){val b=input.read();if(b<0)return null;raw.write(b);if(previous==13&&b==10&&raw.toString().endsWith("\r\n\r\n"))break;previous=b};val lines=raw.toString(StandardCharsets.ISO_8859_1.name()).split("\r\n");val first=lines.first().split(' ');if(first.size<2)return null;val target=first[1];val path=target.substringBefore('?');val query=target.substringAfter('?',"").split('&').filter{it.isNotBlank()}.associate{part->URLDecoder.decode(part.substringBefore('='),"UTF-8") to URLDecoder.decode(part.substringAfter('=',""),"UTF-8")};val headers=lines.drop(1).takeWhile{it.isNotEmpty()}.associate{it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()};return HttpRequest(first[0],path,query,headers)} }
}
