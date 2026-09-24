package com.wisso.wizefiles.feature.share

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalHttpServerTest {
    @Test fun `request parser caps and normalizes headers`() {val request=HttpRequest.parse(BufferedInputStream(ByteArrayInputStream("GET /api/list?root=a&path=Travel%20Photos HTTP/1.1\r\nCookie: x\r\n\r\n".toByteArray())))!!;assertEquals("/api/list",request.path);assertEquals("Travel Photos",request.query["path"]);assertEquals("x",request.headers["cookie"])}
    @Test fun `range supports resumable suffix end`() {assertEquals(10L..99L,LocalHttpServer.parseRange("bytes=10-",100));assertEquals(10L..20L,LocalHttpServer.parseRange("bytes=10-20",100));assertNull(LocalHttpServer.parseRange(null,100));assertThrows(IllegalArgumentException::class.java){LocalHttpServer.parseRange("bytes=100-101",100)}}
    @Test fun `pairing secrets expire and are single-session credentials`() {var now=0L;val auth=ShareAuthenticator(lifetimeMillis=100,now={now});val session=auth.pair("pc",auth.visiblePairingCode())!!;assertEquals(session,auth.authenticate("WIZESESSION=${session.id}"));now=101;val expired=ShareAuthenticator(lifetimeMillis=100,now={now-101});now=202;assertNull(expired.pair("pc",expired.visiblePairingCode()))}
    @Test fun `paired clients receive revocable opaque identities`() {val auth=ShareAuthenticator();val session=auth.pair("192.168.1.2",auth.visiblePairingCode())!!;assertEquals(listOf(session.clientId),auth.activeClients());auth.revoke(session.id);assertEquals(emptyList<String>(),auth.activeClients())}
    @Test fun `active browser content is never rendered as a same-origin preview`() {assertEquals("application/octet-stream",LocalHttpServer.safePreviewMime("text/html"));assertEquals("application/octet-stream",LocalHttpServer.safePreviewMime("image/svg+xml"));assertEquals("image/jpeg",LocalHttpServer.safePreviewMime("image/jpeg"))}
}
