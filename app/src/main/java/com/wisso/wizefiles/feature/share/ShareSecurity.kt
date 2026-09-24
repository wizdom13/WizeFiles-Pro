package com.wisso.wizefiles.feature.share

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object SharePathSecurity {
    private val encodedDanger = Regex("(?i)%2e|%2f|%5c|%0[0-9a-f]|%1[0-9a-f]|%7f")
    private val privateLocations = listOf("/data/data/", "/data/user/", "/android/data/", "/android/obb/")

    fun canonicalRelativePath(value: String): String {
        require(!value.contains('\\')) { "Backslashes are not allowed" }
        require(!encodedDanger.containsMatchIn(value)) { "Encoded traversal characters are not allowed" }
        val decoded=value
        require(!decoded.startsWith('/') && decoded.none(Char::isISOControl)) { "Absolute or control-character path" }
        val parts=decoded.split('/').filter(String::isNotEmpty)
        require(parts.none { it=="." || it==".." }) { "Path traversal is not allowed" }
        return parts.joinToString("/")
    }

    fun requireShareableRoot(uri:String) {
        require(runCatching { java.net.URI(uri) }.getOrNull()?.userInfo == null) { "Credential-bearing paths cannot be shared" }
        val lower=URLDecoder.decode(uri, StandardCharsets.UTF_8.name()).lowercase()
        require(privateLocations.none(lower::contains)) { "Application-private locations cannot be shared" }
        require(!lower.contains("/.wizefiles") && !lower.contains("/vault")) { "Internal and Vault locations cannot be shared" }
        require(!lower.startsWith("root:") && !lower.startsWith("shizuku:")) { "Privileged locations cannot be shared" }
    }
}

internal class PairingRateLimiter(
    private val maximumFailures:Int=5,
    private val blockMillis:Long=5*60_000,
    private val now:()->Long=System::currentTimeMillis
) {
    private data class Attempts(var failures:Int=0,var blockedUntil:Long=0)
    private val clients=mutableMapOf<String,Attempts>()
    @Synchronized fun canAttempt(client:String)=clients[client]?.blockedUntil?.let { it<=now() } ?: true
    @Synchronized fun failed(client:String) {
        val a=clients.getOrPut(client,::Attempts); a.failures++
        if(a.failures>=maximumFailures){a.blockedUntil=now()+blockMillis;a.failures=0}
    }
    @Synchronized fun succeeded(client:String){clients.remove(client)}
}

internal class BoundedShareClientPool(maxClients:Int):AutoCloseable {
    init { require(maxClients>0) }
    private val executor=ThreadPoolExecutor(
        0,
        maxClients,
        30,
        TimeUnit.SECONDS,
        SynchronousQueue()
    )

    fun execute(block:()->Unit):Boolean = try {
        executor.execute(block)
        true
    } catch (_:RejectedExecutionException) {
        false
    }

    override fun close() {
        executor.shutdownNow()
    }
}
