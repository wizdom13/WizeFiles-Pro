package com.wisso.wizefiles.feature.share

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalShareIntegrationSourceTest {
    @Test fun `service is manual connected-device and never a boot receiver`() {val manifest=projectFile("src/main/AndroidManifest.xml");val service=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalShareService.kt");assertTrue(manifest.contains("FOREGROUND_SERVICE_CONNECTED_DEVICE"));assertTrue(manifest.contains("foregroundServiceType=\"connectedDevice\""));assertFalse(manifest.substringAfter("LocalShareService").substringBefore("</service>").contains("BOOT_COMPLETED"));assertTrue(service.contains("START_NOT_STICKY"));assertTrue(service.contains("ShareDatabase.markInterruptedSessions()"))}
    @Test fun `sharing accepts only Android verified LAN transports`() {val selector=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalNetworkSelector.kt");val service=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalShareService.kt");val activity=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalShareActivity.kt");assertTrue(selector.contains("manager.allNetworks"));assertTrue(selector.contains("TRANSPORT_WIFI"));assertTrue(selector.contains("TRANSPORT_ETHERNET"));assertTrue(selector.contains("TRANSPORT_CELLULAR"));assertTrue(selector.contains("TRANSPORT_VPN"));assertFalse(selector.contains("NetworkInterface"));assertFalse(service.contains("registerDefaultNetworkCallback"));assertTrue(service.contains("registerNetworkCallback"));assertTrue(service.contains("network==selected.network"));assertTrue(activity.contains("LocalNetworkSelector.select(this@LocalShareActivity)"))}
    @Test fun `credentials are memory only and omitted from persistent tables`() {val database=projectFile("src/main/java/com/wisso/wizefiles/feature/share/ShareDatabase.kt");assertFalse(database.contains("pairing_code"));assertFalse(database.contains("ftp_password"));assertFalse(database.contains("session_cookie"));assertFalse(database.contains("private_key"))}
    @Test fun `browser and ftp share one provider and transfer boundary`() {
        val http=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalHttpServer.kt")
        val ftp=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalFtpServer.kt")
        val mutations=projectFile("src/main/java/com/wisso/wizefiles/feature/share/ShareMutations.kt")
        assertTrue(http.contains("ShareMutationRunner"))
        assertTrue(http.contains("LongRunningOperationLimiter"))
        assertTrue(ftp.contains("ShareTransferTracker"))
        assertTrue(ftp.contains("LongRunningOperationLimiter"))
        assertTrue(ftp.contains("ShareGateway"))
        assertTrue(ftp.contains("mutations.delete(rootId,relativePath)"))
        assertTrue(ftp.contains("mutations.move(sourceRootId,sourcePath,targetRootId,targetPath)"))
        assertTrue(mutations.contains("trackedMutation(TransferOperationType.DELETE"))
        assertTrue(mutations.contains("trackedMutation(TransferOperationType.MOVE"))
    }
    @Test fun `drawer exposes active-folder local sharing`() {val menu=projectFile("src/main/res/menu/menu_file_list.xml");val navigation=projectFile("src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt");val fragment=projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt");assertFalse(menu.contains("action_local_sharing"));assertTrue(navigation.contains("NavigationAction.LOCAL_SHARING"));assertTrue(fragment.contains("LocalShareActivity.createIntent"));assertTrue(fragment.contains("activePaneFragment().viewModel.currentPath.toAppPath()"))}

    @Test fun `picker state and local share diagnostics stay safe`() {
        val activity=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalShareActivity.kt")
        val service=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalShareService.kt")
        assertTrue(activity.contains("val profileId=pendingProfileId;pendingProfileId=null;AppLog.i"))
        assertTrue(activity.contains("pendingPickedPath=path;pendingPickedProfileId=profileId"))
        assertTrue(activity.contains("override fun onWindowFocusChanged(hasFocus:Boolean)"))
        assertTrue(activity.contains("if(!hasWindowFocus()||!lifecycle.currentState.isAtLeast"))
        assertTrue(activity.contains("showPendingPickerDialogWhenReady()"))
        assertTrue(service.contains("AppLog.e(LOG_TAG,\"Local sharing failed to start\",it)"))
        assertTrue(service.contains("Local sharing active scheme="))
        assertTrue(service.contains("Stopping local sharing reason="))
        assertFalse(service.contains("AppLog.i(LOG_TAG,active.pairingCode)"))
        assertFalse(service.contains("AppLog.i(LOG_TAG,active.qrToken)"))
        assertFalse(service.contains("AppLog.i(LOG_TAG,active.ftpPassword)"))
    }

    @Test fun `folder zip and search results preserve hierarchy and metadata`() {
        val server=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalHttpServer.kt")
        assertTrue(server.contains("Files.walkFileTree(item.path"))
        assertTrue(server.contains("override fun preVisitDirectory"))
        assertTrue(server.contains("override fun visitFile"))
        assertTrue(server.contains("Files.isSymbolicLink(item.path)"))
        assertTrue(server.contains("\\\"directory\\\":\$directory"))
        assertTrue(server.contains("\\\"size\\\":\$size"))
        assertFalse(server.contains("\\\"directory\\\":false,\\\"size\\\":0"))
    }

    @Test fun `idle browser clients cannot escape the share executor`() {
        val server=projectFile("src/main/java/com/wisso/wizefiles/feature/share/LocalHttpServer.kt")
        val tryIndex=server.indexOf("try {",server.indexOf("private fun handle"))
        val parseIndex=server.indexOf("HttpRequest.parse(input)",tryIndex)
        assertTrue(tryIndex >= 0)
        assertTrue(parseIndex > tryIndex)
        assertTrue(server.contains("t !is SocketTimeoutException"))
        assertTrue(server.contains("runCatching {\n                    text(output,400"))
    }

    private fun projectFile(path:String):String=listOf(File(path),File("app/$path")).firstOrNull(File::exists)?.readText()?:error("Missing $path")
}
