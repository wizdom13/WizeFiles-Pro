package com.wisso.wizefiles.feature.share

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

internal data class BrowserSession(val id:String,val csrf:String,val expiresAtMillis:Long,val clientId:String)

internal class ShareAuthenticator(
    private val lifetimeMillis:Long=2*60_000,
    private val sessionMillis:Long=12*60*60_000,
    private val now:()->Long=System::currentTimeMillis,
    private val random:SecureRandom=SecureRandom()
) {
    private val limiter=PairingRateLimiter(now=now)
    private val pairingCode=(100000+random.nextInt(900000)).toString()
    private val oneTimeToken=randomToken(32)
    @Volatile private var tokenConsumed=false
    private val pairingExpiresAt=now()+lifetimeMillis
    private val sessions=ConcurrentHashMap<String,BrowserSession>()
    private val ftpPassword=randomToken(18)

    fun visiblePairingCode()=pairingCode
    fun qrToken()=oneTimeToken
    fun ftpCredentials()="wizefiles" to ftpPassword

    fun pair(client:String,credential:String):BrowserSession? {
        if(!limiter.canAttempt(client) || now()>pairingExpiresAt) return null
        val tokenMatch=!tokenConsumed&&constantTimeEquals(credential,oneTimeToken)
        val valid=constantTimeEquals(credential,pairingCode) || tokenMatch
        if(!valid){limiter.failed(client);return null}
        limiter.succeeded(client);if(tokenMatch)tokenConsumed=true
        return BrowserSession(randomToken(32),randomToken(24),now()+sessionMillis,"pc-${randomToken(6)}").also { sessions[it.id]=it }
    }

    fun authenticate(cookie:String?):BrowserSession? {
        val id=cookie?.split(';')?.map(String::trim)?.firstOrNull { it.startsWith("WIZESESSION=") }?.substringAfter('=') ?: return null
        val session=sessions[id] ?: return null
        if(session.expiresAtMillis<=now()){sessions.remove(id);return null}
        return session
    }
    fun revoke(id:String){sessions.remove(id)}
    fun revokeClient(clientId:String){sessions.entries.removeIf{it.value.clientId==clientId}}
    fun revokeAll(){sessions.clear()}
    fun activeSessionCount():Int {sessions.entries.removeIf{it.value.expiresAtMillis<=now()};return sessions.size}
    fun activeClients():List<String>{activeSessionCount();return sessions.values.map(BrowserSession::clientId).distinct()}

    private fun randomToken(bytes:Int)=ByteArray(bytes).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun constantTimeEquals(left:String,right:String)=MessageDigest.isEqual(left.toByteArray(),right.toByteArray())
}
