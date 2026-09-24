package com.wisso.wizefiles.core.app

import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.storage.StorageManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.wisso.wizefiles.core.android.compat.getSystemServiceCompat
import com.wisso.wizefiles.core.android.compat.mainExecutorCompat
import com.wisso.wizefiles.security.AndroidKeyStoreSecretStore
import com.wisso.wizefiles.security.AppSecurityManager
import com.wisso.wizefiles.security.SecretStore
import okhttp3.OkHttpClient
import java.util.concurrent.Executor

val appClassLoader = ApplicationInitializerProvider::class.java.classLoader

val clipboardManager: ClipboardManager by lazy {
    application.getSystemServiceCompat(ClipboardManager::class.java)
}

val contentResolver: ContentResolver by lazy { application.contentResolver }

val defaultSharedPreferences: SharedPreferences by lazy {
    PreferenceManager.getDefaultSharedPreferences(application)
}

val secretStore: SecretStore by lazy {
    AndroidKeyStoreSecretStore.create(application)
}

val appSecurityManager: AppSecurityManager by lazy {
    AppSecurityManager.create(application, defaultSharedPreferences, secretStore)
}

val okHttpClient: OkHttpClient by lazy { OkHttpClient() }

val inputMethodManager: InputMethodManager by lazy {
    application.getSystemServiceCompat(InputMethodManager::class.java)
}

val mainExecutor: Executor by lazy { application.mainExecutorCompat }

val notificationManager: NotificationManagerCompat by lazy {
    NotificationManagerCompat.from(application)
}

val packageManager: PackageManager by lazy { application.packageManager }

val powerManager: PowerManager by lazy {
    application.getSystemServiceCompat(PowerManager::class.java)
}

val storageManager: StorageManager by lazy {
    application.getSystemServiceCompat(StorageManager::class.java)
}

val wifiManager: WifiManager by lazy {
    application.getSystemServiceCompat(WifiManager::class.java)
}
