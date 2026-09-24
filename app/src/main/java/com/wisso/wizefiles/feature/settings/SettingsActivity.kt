package com.wisso.wizefiles.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.add
import androidx.fragment.app.commit
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.wisso.wizefiles.core.app.BaseThemedActivity
import com.wisso.wizefiles.theme.custom.CustomThemeHelper.OnThemeChangedListener
import com.wisso.wizefiles.theme.night.NightModeHelper.OnNightModeChangedListener
import com.wisso.wizefiles.util.BundleParceler
import com.wisso.wizefiles.util.ParcelableArgs
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.getArgsOrNull
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.startActivitySafe

class SettingsActivity : BaseThemedActivity(), OnThemeChangedListener, OnNightModeChangedListener {
    private var isRestarting = false
    private var pendingRestoreUri: Uri? = null
    private val Intent.restoreSettingsBackupUri: Uri?
        get() = SettingsBackupViewIntent.findBackupUri(this) {
            SettingsBackupViewIntent.resolveDisplayName(this@SettingsActivity, it)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val args = intent.extras?.getArgsOrNull<Args>()
        val savedInstanceState = savedInstanceState ?: args?.savedInstanceState
        super.onCreate(savedInstanceState)
        pendingRestoreUri = intent.restoreSettingsBackupUri

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit { add<SettingsFragment>(android.R.id.content) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRestoreUri = intent.restoreSettingsBackupUri
        showPendingRestoreDialogIfNeeded()
    }

    override fun onPostResume() {
        super.onPostResume()
        showPendingRestoreDialogIfNeeded()
    }

    private fun showPendingRestoreDialogIfNeeded() {
        val uri = pendingRestoreUri ?: return
        val settingsFragment = supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().firstOrNull()
        if (settingsFragment?.showRestoreSettingsDialog(uri) == true) {
            pendingRestoreUri = null
        }
    }

    fun setApplicationLocalesPre33(locales: LocaleListCompat) {
        // HACK: Prevent this activity from being recreated due to locale change.
        delegate.onDestroy()
        AppCompatDelegate.setApplicationLocales(locales)
        restart()
    }

    override fun onThemeChanged(@StyleRes theme: Int) {
        // ActivityCompat.recreate() may call ActivityRecreator.recreate() without calling
        // Activity.recreate(), so we cannot simply override it. To work around this, we just
        // manually call restart().
        restart()
    }

    override fun onNightModeChangedFromHelper(nightMode: Int) {
        // ActivityCompat.recreate() may call ActivityRecreator.recreate() without calling
        // Activity.recreate(), so we cannot simply override it. To work around this, we just
        // manually call restart().
        restart()
    }

    private fun restart() {
        val savedInstanceState = Bundle().apply {
            onSaveInstanceState(this)
        }
        finish()
        val intent = SettingsActivity::class.createIntent().putArgs(Args(savedInstanceState))
        startActivitySafe(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        isRestarting = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return isRestarting || super.dispatchKeyEvent(event)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        return isRestarting || super.dispatchKeyShortcutEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return isRestarting || super.dispatchTouchEvent(event)
    }

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean {
        return isRestarting || super.dispatchTrackballEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return isRestarting || super.dispatchGenericMotionEvent(event)
    }

    @Parcelize
    class Args(val savedInstanceState: @WriteWith<BundleParceler> Bundle?) : ParcelableArgs
}
