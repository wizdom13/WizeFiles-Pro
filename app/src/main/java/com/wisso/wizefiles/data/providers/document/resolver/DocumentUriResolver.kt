package com.wisso.wizefiles.provider.document.resolver

import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.wisso.wizefiles.provider.content.resolver.ResolverException
import com.wisso.wizefiles.provider.content.resolver.requireString
import java.io.FileNotFoundException
import java.util.Collections
import java.util.WeakHashMap

internal class DocumentUriResolver(
    private val query: (Uri, Array<out String?>?, String?) -> Cursor
) {
    private val documentIds = Collections.synchronizedMap(
        WeakHashMap<DocumentResolver.Path, String>()
    )

    fun invalidate(path: DocumentResolver.Path) {
        documentIds -= path
    }

    fun remember(path: DocumentResolver.Path, documentId: String) {
        documentIds[path] = documentId
    }

    fun documentUri(path: DocumentResolver.Path): Uri =
        DocumentsContract.buildDocumentUriUsingTree(path.treeUri, documentId(path))

    fun childrenUri(path: DocumentResolver.Path): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(path.treeUri, documentId(path))

    fun documentId(path: DocumentResolver.Path): String {
        documentIds[path]?.let { return it }
        val id = path.parent?.let { parent ->
            childDocumentId(parent, requireNotNull(path.displayName), path.treeUri)
        } ?: requireNotNull(DocumentsContract.getTreeDocumentId(path.treeUri))
        documentIds[path] = id
        return id
    }

    private fun childDocumentId(
        parent: DocumentResolver.Path,
        displayName: String,
        treeUri: Uri
    ): String {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId(parent))
        query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.requireString(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val childName = cursor.requireString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val child = parent.resolve(childName)
                documentIds[child] = childId
                if (childName == displayName) return childId
            }
        }
        throw ResolverException(
            FileNotFoundException("Cannot find document ID for ${parent.resolve(displayName)}")
        )
    }
}
