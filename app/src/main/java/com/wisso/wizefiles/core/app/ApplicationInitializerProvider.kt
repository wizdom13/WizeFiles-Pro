package com.wisso.wizefiles.core.app

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.wisso.wizefiles.core.billing.AppBilling
import com.wisso.wizefiles.core.entitlement.AppEntitlements
import com.wisso.wizefiles.util.AppLog

private var applicationHolder: Application? = null

val application: Application
    get() = applicationHolder ?: resolveApplicationFromRuntime()
    ?: throw NullPointerException("Application is not initialized")

internal fun setGlobalApplicationForTests(application: Application) {
    applicationHolder = application
}

private fun resolveApplicationFromRuntime(): Application? {
    val activityThreadApplication = runCatching {
        val clazz = Class.forName("android.app.ActivityThread")
        val method = clazz.getDeclaredMethod("currentApplication")
        method.isAccessible = true
        method.invoke(null) as? Application
    }.getOrNull()
    if (activityThreadApplication != null) {
        applicationHolder = activityThreadApplication
        return activityThreadApplication
    }
    val appGlobalsApplication = runCatching {
        val clazz = Class.forName("android.app.AppGlobals")
        val method = clazz.getDeclaredMethod("getInitialApplication")
        method.isAccessible = true
        method.invoke(null) as? Application
    }.getOrNull()
    if (appGlobalsApplication != null) {
        applicationHolder = appGlobalsApplication
    }
    return appGlobalsApplication
}

class ApplicationInitializerProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        applicationHolder = context as Application
        val tasks = listOf(
            AppInitializerTask("initializeAppLog") { AppLog.initialize(application) },
            AppInitializerTask("initializeEntitlements") { AppEntitlements.initialize(application) },
            AppInitializerTask("initializeBilling") { AppBilling.initialize(application) }
        ) + appInitializerTasks

        safeInfo("Application initialization started")
        val failures = runAppInitializerTasks(
            tasks = tasks,
            onStarted = { safeInfo("Running initializer=$it") },
            onCompleted = { safeInfo("Initializer completed=$it") },
            onFailed = { failure ->
                safeError("Initializer failed=${failure.taskName}", failure.exception)
            }
        )
        AppInitializationHealth.replaceFailures(failures)
        safeInfo("Application initialization completed failures=${failures.size}")
        return true
    }

    private fun safeInfo(message: String) {
        try {
            AppLog.i(TAG, message)
        } catch (exception: Exception) {
            Log.i(TAG, message)
        }
    }

    private fun safeError(message: String, exception: Exception) {
        try {
            AppLog.e(TAG, message, exception)
        } catch (loggingException: Exception) {
            Log.e(TAG, message, exception)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? {
        throw UnsupportedOperationException()
    }

    override fun getType(uri: Uri): String? {
        throw UnsupportedOperationException()
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int {
        throw UnsupportedOperationException()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int {
        throw UnsupportedOperationException()
    }

    private companion object {
        const val TAG = "ApplicationInitializerProvider"
    }
}
