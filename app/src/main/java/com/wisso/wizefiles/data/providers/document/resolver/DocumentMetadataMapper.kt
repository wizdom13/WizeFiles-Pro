package com.wisso.wizefiles.provider.document.resolver

import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.provider.content.resolver.getLong
import com.wisso.wizefiles.provider.content.resolver.getString
import com.wisso.wizefiles.provider.content.resolver.moveToFirstOrThrow

internal class DocumentMetadataMapper(
    private val query: (Uri, Array<out String?>?, String?) -> Cursor
) {
    fun mimeType(uri: Uri): String? =
        query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null).use { cursor ->
            cursor.moveToFirstOrThrow()
            cursor.getString(DocumentsContract.Document.COLUMN_MIME_TYPE)
        }?.takeIf { it.isNotEmpty() && it != MimeType.GENERIC.value }

    fun size(uri: Uri): Long? =
        query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null).use { cursor ->
            cursor.moveToFirstOrThrow()
            cursor.getLong(DocumentsContract.Document.COLUMN_SIZE)
        }
}
