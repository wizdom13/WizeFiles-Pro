package com.wisso.wizefiles.beta

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.wisso.wizefiles.BuildConfig
import java.lang.ref.WeakReference

internal object BetaExpiry {
    val expiresAtEpochMillis: Long
        get() = BuildConfig.BETA_EXPIRY_EPOCH_MILLIS

    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMillis > 0L && nowEpochMillis >= expiresAtEpochMillis
}

class BetaExpiryProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        application.registerActivityLifecycleCallbacks(BetaExpiryActivityCallbacks)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int = 0
}

private object BetaExpiryActivityCallbacks : Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())
    private var resumedActivity = WeakReference<Activity>(null)
    private val expiryCheck = Runnable {
        resumedActivity.get()?.let(::redirectIfExpired)
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        redirectIfExpired(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is BetaExpiredActivity) {
            return
        }
        resumedActivity = WeakReference(activity)
        handler.removeCallbacks(expiryCheck)
        if (!redirectIfExpired(activity)) {
            val delay = BetaExpiry.expiresAtEpochMillis - System.currentTimeMillis()
            if (delay > 0L) {
                handler.postDelayed(expiryCheck, delay)
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity.get() === activity) {
            resumedActivity.clear()
            handler.removeCallbacks(expiryCheck)
        }
    }

    private fun redirectIfExpired(activity: Activity): Boolean {
        if (
            activity is BetaExpiredActivity ||
            activity.isFinishing ||
            !BetaExpiry.isExpired()
        ) {
            return false
        }
        activity.startActivity(
            Intent(activity, BetaExpiredActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        activity.finish()
        return true
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
