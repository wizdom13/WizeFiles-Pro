package com.wisso.wizefiles.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import androidx.core.app.ShareCompat
import androidx.core.content.IntentCompat
import com.wisso.wizefiles.core.app.appClassLoader
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.core.android.compat.DocumentsContractCompat
import com.wisso.wizefiles.core.android.compat.removeFlagsCompat
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.intentType
import kotlin.reflect.KClass

fun <T : Context> KClass<T>.createIntent(): Intent = Intent(application, java)

fun CharSequence.createSendTextIntent(htmlText: String? = null): Intent =
    ShareCompat.IntentBuilder(application)
        .setType(MimeType.TEXT_PLAIN.value)
        .setText(this)
        .apply { htmlText?.let { setHtmlText(it) } }
        .intent
        .apply {
            @Suppress("DEPRECATION")
            removeFlagsCompat(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
        }

fun KClass<Intent>.createViewLocation(latitude: Float, longitude: Float, label: String): Intent =
    Uri.parse("geo:0,0?q=$latitude,$longitude(${Uri.encode(label)})").createViewIntent()

inline fun <reified T : Parcelable> Intent.getParcelableExtraSafe(key: String?): T? {
    setExtrasClassLoader(appClassLoader)
    return IntentCompat.getParcelableExtra(this, key, T::class.java)
}

fun <T : java.io.Serializable> Intent.getSerializableExtraSafe(key: String?, valueClass: Class<T>): T? {
    setExtrasClassLoader(appClassLoader)
    return IntentCompat.getSerializableExtra(this, key, valueClass)
}

inline fun <reified T : java.io.Serializable> Intent.getSerializableExtraSafe(key: String?): T? =
    getSerializableExtraSafe(key, T::class.java)

fun Intent.withChooser(title: CharSequence? = null, vararg initialIntents: Intent): Intent =
    Intent.createChooser(this, title).apply {
        putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents)
    }

fun Intent.withChooser(vararg initialIntents: Intent) = withChooser(null, *initialIntents)

internal const val EDIT_INTENT_PERMISSION_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

fun Uri.createEditIntent(mimeType: MimeType): Intent =
    Intent(Intent.ACTION_EDIT)
        .setDataAndType(this, mimeType.intentType)
        // ACTION_EDIT targets need to read current contents and write back edits.
        .addFlags(EDIT_INTENT_PERMISSION_FLAGS)

fun MimeType.createPickFileIntent(allowMultiple: Boolean = false) =
    Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(value)
        .apply {
            if (allowMultiple) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

fun Collection<MimeType>.createPickFileIntent(allowMultiple: Boolean = false): Intent =
    (singleOrNull() ?: MimeType.ANY).createPickFileIntent(allowMultiple)
        .apply {
            if (size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, map { it.value }.toTypedArray())
            }
        }

fun Uri.createSendImageIntent(text: CharSequence? = null): Intent =
    createSendStreamIntent(MimeType.IMAGE_ANY).apply {
        text?.let {
            putExtra(Intent.EXTRA_TEXT, it)
            putExtra(Intent.EXTRA_TITLE, it)
            putExtra(Intent.EXTRA_SUBJECT, it)
            putExtra("Kdescription", it)
        }
    }

fun Uri.createSendStreamIntent(mimeType: MimeType): Intent =
    listOf(this).createSendStreamIntent(listOf(mimeType))

fun Collection<Uri>.createSendStreamIntent(mimeTypes: Collection<MimeType>): Intent =
    ShareCompat.IntentBuilder(application)
        .setType(mimeTypes.intentType)
        .apply { forEach { addStream(it) } }
        .intent
        .apply {
            @Suppress("DEPRECATION")
            removeFlagsCompat(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
        }

fun Uri.createDocumentsUiViewDirectoryIntent(): Intent =
    createViewIntent(MimeType.DIRECTORY)
        .apply { DocumentsContractCompat.getDocumentsUiPackage()?.let { setPackage(it) } }

fun Uri.createViewIntent(): Intent = Intent(Intent.ACTION_VIEW, this)

fun Uri.createViewIntent(mimeType: MimeType): Intent =
    Intent(Intent.ACTION_VIEW)
        .setDataAndType(this, mimeType.intentType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

@Suppress("DEPRECATION")
fun Uri.createInstallPackageIntent(): Intent =
    Intent(Intent.ACTION_INSTALL_PACKAGE)
        .setDataAndType(this, MimeType.APK.value)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
