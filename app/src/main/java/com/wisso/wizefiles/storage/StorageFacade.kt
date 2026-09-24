package com.wisso.wizefiles.storage

import com.wisso.wizefiles.provider.common.ForeignCopyMove
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.provider.common.moveTo
import com.wisso.wizefiles.provider.rclone.isRclonePath
import com.wisso.wizefiles.storage.legacy.LegacyRetrofileAdapter
import com.wisso.wizefiles.storage.path.toAppPath
import java.io.IOException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transitional facade that centralizes local-first routing and legacy fallback.
 */
class StorageFacade(
    private val router: StorageRouter = StorageRouter(),
    private val legacy: LegacyRetrofileAdapter = LegacyRetrofileAdapter()
) {
    @Throws(IOException::class)
    fun createDirectory(path: Path) {
        if (router.createDirectory(path) == null) {
            Files.createDirectory(path)
        }
    }

    @Throws(IOException::class)
    fun createDirectories(path: Path) {
        if (Files.exists(path)) return
        Files.createDirectories(path)
    }

    @Throws(IOException::class)
    fun delete(path: Path) {
        if (!router.delete(path)) {
            Files.delete(path)
        }
    }

    @Throws(IOException::class)
    fun deleteIfExists(path: Path): Boolean =
        if (router.exists(path) == false) {
            false
        } else {
            Files.deleteIfExists(path)
        }

    @Throws(IOException::class)
    fun copy(source: Path, target: Path, vararg options: CopyOption) {
        if (router.copy(source, target, *options)) {
            return
        }
        if (source.isRclonePath || target.isRclonePath) {
            if (source.fileSystem.provider() == target.fileSystem.provider()) {
                source.copyTo(target, *options)
            } else {
                ForeignCopyMove.copy(source, target, *options)
            }
        } else {
            legacy.copy(source.toAppPath(), target.toAppPath(), *options)
        }
    }

    @Throws(IOException::class)
    fun move(source: Path, target: Path, vararg options: CopyOption) {
        if (router.move(source, target, *options)) {
            return
        }
        if (source.isRclonePath || target.isRclonePath) {
            if (source.fileSystem.provider() == target.fileSystem.provider()) {
                source.moveTo(target, *options)
            } else {
                ForeignCopyMove.move(source, target, *options)
            }
        } else {
            legacy.move(source.toAppPath(), target.toAppPath(), *options)
        }
    }

    @Throws(IOException::class)
    fun renameLocal(path: Path, newName: String) = router.rename(path, newName)

    @Throws(IOException::class)
    fun listLocal(path: Path) = router.list(path)

    @Throws(IOException::class)
    fun statLocal(node: com.wisso.wizefiles.storage.local.LocalFileNode) = router.stat(node)

    @Throws(IOException::class)
    fun existsLocal(path: Path) = router.exists(path)
}
