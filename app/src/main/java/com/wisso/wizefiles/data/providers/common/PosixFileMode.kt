package com.wisso.wizefiles.provider.common

import android.system.OsConstants
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

enum class PosixFileModeBit {
    SET_USER_ID,
    SET_GROUP_ID,
    STICKY,
    OWNER_READ,
    OWNER_WRITE,
    OWNER_EXECUTE,
    GROUP_READ,
    GROUP_WRITE,
    GROUP_EXECUTE,
    OTHERS_READ,
    OTHERS_WRITE,
    OTHERS_EXECUTE
}

object PosixFileMode {
    private val systemMasks: Map<PosixFileModeBit, Int> = linkedMapOf(
        PosixFileModeBit.SET_USER_ID to OsConstants.S_ISUID,
        PosixFileModeBit.SET_GROUP_ID to OsConstants.S_ISGID,
        PosixFileModeBit.STICKY to OsConstants.S_ISVTX,
        PosixFileModeBit.OWNER_READ to OsConstants.S_IRUSR,
        PosixFileModeBit.OWNER_WRITE to OsConstants.S_IWUSR,
        PosixFileModeBit.OWNER_EXECUTE to OsConstants.S_IXUSR,
        PosixFileModeBit.GROUP_READ to OsConstants.S_IRGRP,
        PosixFileModeBit.GROUP_WRITE to OsConstants.S_IWGRP,
        PosixFileModeBit.GROUP_EXECUTE to OsConstants.S_IXGRP,
        PosixFileModeBit.OTHERS_READ to OsConstants.S_IROTH,
        PosixFileModeBit.OTHERS_WRITE to OsConstants.S_IWOTH,
        PosixFileModeBit.OTHERS_EXECUTE to OsConstants.S_IXOTH
    )

    val CREATE_DIRECTORY_DEFAULT: Set<PosixFileModeBit> =
        fromInt(OsConstants.S_IRWXU or OsConstants.S_IRWXG or OsConstants.S_IRWXO)

    val CREATE_FILE_DEFAULT: Set<PosixFileModeBit> = fromInt(
        OsConstants.S_IRUSR or OsConstants.S_IWUSR or
            OsConstants.S_IRGRP or OsConstants.S_IWGRP or
            OsConstants.S_IROTH or OsConstants.S_IWOTH
    )

    val DIRECTORY_DEFAULT: Set<PosixFileModeBit> = fromInt(
        OsConstants.S_IRWXU or OsConstants.S_IRGRP or OsConstants.S_IXGRP or
            OsConstants.S_IROTH or OsConstants.S_IXOTH
    )

    val FILE_DEFAULT: Set<PosixFileModeBit> = fromInt(
        OsConstants.S_IRUSR or OsConstants.S_IWUSR or
            OsConstants.S_IRGRP or OsConstants.S_IROTH
    )

    val SYMBOLIC_LINK_DEFAULT: Set<PosixFileModeBit> =
        fromInt(OsConstants.S_IRWXU or OsConstants.S_IRWXG or OsConstants.S_IRWXO)

    fun fromAttribute(attribute: FileAttribute<*>): Set<PosixFileModeBit> {
        require(attribute.name() == ModeAttribute.NAME) {
            "Unsupported file attribute: " + attribute.name()
        }
        val supplied = attribute.value() as? Set<*>
            ?: throw IllegalArgumentException("POSIX mode must be represented by a set")
        val mode = EnumSet.noneOf(PosixFileModeBit::class.java)
        supplied.forEach { item ->
            mode += item as? PosixFileModeBit
                ?: throw IllegalArgumentException("Invalid POSIX mode member: " + item)
        }
        return mode
    }

    fun fromAttributes(attributes: Array<out FileAttribute<*>>): Set<PosixFileModeBit>? {
        var selected: Set<PosixFileModeBit>? = null
        attributes.forEach { selected = fromAttribute(it) }
        return selected
    }

    fun fromInt(modeInt: Int): Set<PosixFileModeBit> {
        val result = EnumSet.noneOf(PosixFileModeBit::class.java)
        systemMasks.forEach { (bit, mask) ->
            if (modeInt and mask == mask) result += bit
        }
        return result
    }

    internal fun maskFor(bit: PosixFileModeBit): Int = checkNotNull(systemMasks[bit])
}

fun Set<PosixFilePermission>.toMode(): Set<PosixFileModeBit> {
    val result = EnumSet.noneOf(PosixFileModeBit::class.java)
    forEach { permission -> result += permission.modeBit }
    return result
}

fun Set<PosixFileModeBit>.toAttribute(): FileAttribute<Set<PosixFileModeBit>> =
    ModeAttribute(this)

private class ModeAttribute(mode: Set<PosixFileModeBit>) :
    FileAttribute<Set<PosixFileModeBit>> {
    private val snapshot = mode.toSet()

    override fun name(): String = NAME
    override fun value(): Set<PosixFileModeBit> = snapshot

    companion object {
        const val NAME = "posix:mode"
    }
}

fun Set<PosixFileModeBit>.toInt(): Int =
    fold(0) { value, bit -> value or PosixFileMode.maskFor(bit) }

fun Set<PosixFileModeBit>.toPermissions(): Set<PosixFilePermission> {
    val result = EnumSet.noneOf(PosixFilePermission::class.java)
    forEach { bit ->
        val permission = bit.permission
            ?: throw UnsupportedOperationException("Special mode bit has no NIO permission: " + bit)
        result += permission
    }
    return result
}

fun Set<PosixFileModeBit>.toModeString(): String {
    val output = CharArray(9) { '-' }
    applyAccessTriplet(output, 0, PosixFileModeBit.OWNER_READ, PosixFileModeBit.OWNER_WRITE,
        PosixFileModeBit.OWNER_EXECUTE, PosixFileModeBit.SET_USER_ID, 's', 'S')
    applyAccessTriplet(output, 3, PosixFileModeBit.GROUP_READ, PosixFileModeBit.GROUP_WRITE,
        PosixFileModeBit.GROUP_EXECUTE, PosixFileModeBit.SET_GROUP_ID, 's', 'S')
    applyAccessTriplet(output, 6, PosixFileModeBit.OTHERS_READ, PosixFileModeBit.OTHERS_WRITE,
        PosixFileModeBit.OTHERS_EXECUTE, PosixFileModeBit.STICKY, 't', 'T')
    return output.concatToString()
}

private fun Set<PosixFileModeBit>.applyAccessTriplet(
    output: CharArray,
    offset: Int,
    read: PosixFileModeBit,
    write: PosixFileModeBit,
    execute: PosixFileModeBit,
    special: PosixFileModeBit,
    executableSpecial: Char,
    nonExecutableSpecial: Char
) {
    if (read in this) output[offset] = 'r'
    if (write in this) output[offset + 1] = 'w'
    output[offset + 2] = when {
        execute in this && special in this -> executableSpecial
        execute in this -> 'x'
        special in this -> nonExecutableSpecial
        else -> '-'
    }
}

private val PosixFilePermission.modeBit: PosixFileModeBit
    get() = when (this) {
        PosixFilePermission.OWNER_READ -> PosixFileModeBit.OWNER_READ
        PosixFilePermission.OWNER_WRITE -> PosixFileModeBit.OWNER_WRITE
        PosixFilePermission.OWNER_EXECUTE -> PosixFileModeBit.OWNER_EXECUTE
        PosixFilePermission.GROUP_READ -> PosixFileModeBit.GROUP_READ
        PosixFilePermission.GROUP_WRITE -> PosixFileModeBit.GROUP_WRITE
        PosixFilePermission.GROUP_EXECUTE -> PosixFileModeBit.GROUP_EXECUTE
        PosixFilePermission.OTHERS_READ -> PosixFileModeBit.OTHERS_READ
        PosixFilePermission.OTHERS_WRITE -> PosixFileModeBit.OTHERS_WRITE
        PosixFilePermission.OTHERS_EXECUTE -> PosixFileModeBit.OTHERS_EXECUTE
    }

private val PosixFileModeBit.permission: PosixFilePermission?
    get() = when (this) {
        PosixFileModeBit.OWNER_READ -> PosixFilePermission.OWNER_READ
        PosixFileModeBit.OWNER_WRITE -> PosixFilePermission.OWNER_WRITE
        PosixFileModeBit.OWNER_EXECUTE -> PosixFilePermission.OWNER_EXECUTE
        PosixFileModeBit.GROUP_READ -> PosixFilePermission.GROUP_READ
        PosixFileModeBit.GROUP_WRITE -> PosixFilePermission.GROUP_WRITE
        PosixFileModeBit.GROUP_EXECUTE -> PosixFilePermission.GROUP_EXECUTE
        PosixFileModeBit.OTHERS_READ -> PosixFilePermission.OTHERS_READ
        PosixFileModeBit.OTHERS_WRITE -> PosixFilePermission.OTHERS_WRITE
        PosixFileModeBit.OTHERS_EXECUTE -> PosixFilePermission.OTHERS_EXECUTE
        PosixFileModeBit.SET_USER_ID,
        PosixFileModeBit.SET_GROUP_ID,
        PosixFileModeBit.STICKY -> null
    }
