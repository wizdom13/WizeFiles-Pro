package com.wisso.wizefiles.feature.share

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalShareWebAppSourceTest {
    @Test fun `browser app is offline responsive and resumable`() {val source=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalShareWebApp.kt");assertTrue(source.contains("webkitdirectory"));assertTrue(source.contains("Upload-Offset"));assertTrue(source.contains("localStorage.setItem"));assertTrue(source.contains("/api/zip"));assertTrue(source.contains("e.key==='Delete'"));assertFalse(source.contains("src=\"http"));assertFalse(source.contains("analytics"));assertTrue(source.contains("class=pair-controls"));assertTrue(source.contains("gap:.55rem"));assertTrue(source.contains("text-align:center"));assertTrue(source.contains("justify-content:center"))}
    @Test fun `overwrite waits for phone approval after the temporary upload completes`() {val mutations=projectFile("src/main/java/com/wisso/wizefiles/feature/share/ShareMutations.kt");val server=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalHttpServer.kt");assertTrue(mutations.contains("waitingForApproval=true"));assertTrue(mutations.contains("finalizeUpload(requireNotNull(ShareDatabase.upload(action.uploadId))"));assertTrue(server.contains("waitingForApproval"));assertTrue(server.contains("onPendingAction"))}
    private fun projectFile(path:String):String=listOf(File(path),File("app/$path")).firstOrNull(File::exists)?.readText()?:error("Missing $path")
}
