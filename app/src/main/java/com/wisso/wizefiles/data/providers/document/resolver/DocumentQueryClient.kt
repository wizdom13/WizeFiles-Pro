package com.wisso.wizefiles.provider.document.resolver

import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import com.wisso.wizefiles.provider.content.resolver.Resolver
import com.wisso.wizefiles.provider.content.resolver.ResolverException
import com.wisso.wizefiles.util.AbstractLocalCursor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal object DocumentQueryClient {
    fun query(uri: Uri, projection: Array<out String?>?, sortOrder: String?): Cursor =
        ExternalStorageProviderHacks.transformQueryResult(
            uri,
            Resolver.query(uri, projection, null, null, sortOrder)
        )

    fun waitUntilChanged(cursor: Cursor) {
        try {
            val changed = runBlocking {
                withTimeoutOrNull(LOADING_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val finished = AtomicBoolean(false)
                        val observer = object : ContentObserver(null) {
                            override fun onChange(selfChange: Boolean) {
                                if (finished.compareAndSet(false, true)) {
                                    runCatching { cursor.unregisterContentObserver(this) }
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                        }
                        cursor.registerContentObserver(observer)
                        continuation.invokeOnCancellation {
                            if (finished.compareAndSet(false, true)) {
                                runCatching { cursor.unregisterContentObserver(observer) }
                            }
                        }
                    }
                    true
                } ?: false
            }
            if (!changed) throw ResolverException("Timed out while waiting for the document provider")
        } catch (failure: InterruptedException) {
            throw ResolverException(failure)
        }
    }

    fun rowSnapshot(cursor: Cursor): Cursor {
        val names = cursor.columnNames
        val values = Array<Any?>(names.size) { index ->
            when (val type = cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                else -> throw ResolverException("Unknown cursor column type $type")
            }
        }
        return RowCursor(names, values)
    }

    private class RowCursor(
        private val names: Array<String>,
        private val values: Array<Any?>
    ) : AbstractLocalCursor() {
        override fun getCount(): Int = 1
        override fun getColumnNames(): Array<String> = names
        override fun getObject(columnIndex: Int): Any? = values[columnIndex]
    }

    private const val LOADING_TIMEOUT_MILLIS = 30_000L
}

internal object DocumentRetryPolicy {
    fun decide(loading: Boolean, error: String?, refreshCount: Int): DocumentQueryPolicy.Decision =
        DocumentQueryPolicy.decide(loading, error, refreshCount)
}
