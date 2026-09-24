package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import com.wisso.wizefiles.core.app.appSecurityManager
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.security.AppUnlockPrompt
import com.wisso.wizefiles.security.ProtectedTarget
import com.wisso.wizefiles.settings.SettingsBackupViewIntent
import com.wisso.wizefiles.storage.path.AppPath

internal class BrowserIntentRouter(private val context: Context) {
    fun settingsRestore(intent: Intent, hasSavedState: Boolean): Intent? {
        if (hasSavedState) return null
        val uri = FileListLaunchRouting.findRestoreSettingsBackupUri(intent) {
            SettingsBackupViewIntent.resolveDisplayName(context, it)
        } ?: return null
        return FileListLaunchRouting.createRestoreSettingsIntent(intent, uri)
    }

    fun supportsTabs(intent: Intent): Boolean = FileListLaunchRouting.supportsTabs(intent.action)
}

internal class BrowserAccessCoordinator(private val activity: FileListActivity) {
    fun initialize(onAllowed: () -> Unit, onCancelled: () -> Unit) {
        if (!appSecurityManager.requiresUnlock(ProtectedTarget.BROWSER)) {
            onAllowed()
            return
        }
        AppUnlockPrompt.show(activity, appSecurityManager, onAllowed, onCancelled)
    }
}

internal object BrowserActivityResultDispatcher {
    fun openFile(context: Context, input: List<MimeType>): Intent =
        FileListContractIntents.openFile(context, input)

    fun openPath(context: Context, input: List<MimeType>): Intent =
        FileListContractIntents.openPath(context, input)

    fun createFile(context: Context, input: Triple<MimeType, String?, AppPath?>): Intent =
        FileListContractIntents.createFile(context, input)

    fun openDirectory(context: Context, input: Any?): Intent =
        FileListContractIntents.openDirectory(context, input)

    fun single(resultCode: Int, intent: Intent?): AppPath? =
        FileListContractIntents.parseSingle(resultCode, intent)

    fun multiple(resultCode: Int, intent: Intent?): List<AppPath> =
        FileListContractIntents.parseMultiple(resultCode, intent)
}

internal class BrowserDrawerCoordinator(private val persistentDrawerMinimumWidthDp: Int) {
    fun persistentDrawerAllowed(windowWidthDp: Int): Boolean =
        windowWidthDp >= persistentDrawerMinimumWidthDp
}

internal class BrowserWindowStateController {
    var widthDp: Int = 0
        private set
    var verticalHingeBounds: Rect? = null
        private set

    fun updateWidth(widthPixels: Int, density: Float): Boolean {
        val next = (widthPixels / density).toInt()
        if (next == widthDp) return false
        widthDp = next
        return true
    }

    fun updateLayout(layoutInfo: WindowLayoutInfo): Boolean {
        val next = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
            .firstOrNull {
                it.orientation == FoldingFeature.Orientation.VERTICAL && it.isSeparating
            }
            ?.bounds
        if (next == verticalHingeBounds) return false
        verticalHingeBounds = next
        return true
    }
}
