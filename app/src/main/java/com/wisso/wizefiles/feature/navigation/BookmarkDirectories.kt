package com.wisso.wizefiles.navigation

import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.removeFirst
import com.wisso.wizefiles.util.valueCompat

object BookmarkDirectories {
    fun add(bookmarkDirectory: BookmarkDirectory): Boolean {
        val bookmarkDirectories = Settings.BOOKMARK_DIRECTORIES.valueCompat.toMutableList()
            .apply { add(bookmarkDirectory) }
        Settings.BOOKMARK_DIRECTORIES.putValue(bookmarkDirectories)
        return Settings.BOOKMARK_DIRECTORIES.valueCompat.any { it.id == bookmarkDirectory.id }
    }

    fun move(fromPosition: Int, toPosition: Int) {
        val bookmarkDirectories = Settings.BOOKMARK_DIRECTORIES.valueCompat.toMutableList()
            .apply { add(toPosition, removeAt(fromPosition)) }
        Settings.BOOKMARK_DIRECTORIES.putValue(bookmarkDirectories)
    }

    fun replace(bookmarkDirectory: BookmarkDirectory) {
        val bookmarkDirectories = Settings.BOOKMARK_DIRECTORIES.valueCompat.toMutableList()
            .apply { this[indexOfFirst { it.id == bookmarkDirectory.id }] = bookmarkDirectory }
        Settings.BOOKMARK_DIRECTORIES.putValue(bookmarkDirectories)
    }

    fun remove(bookmarkDirectory: BookmarkDirectory) {
        val bookmarkDirectories = Settings.BOOKMARK_DIRECTORIES.valueCompat.toMutableList()
            .apply { removeFirst { it.id == bookmarkDirectory.id } }
        Settings.BOOKMARK_DIRECTORIES.putValue(bookmarkDirectories)
    }
}
