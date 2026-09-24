package com.wisso.wizefiles.provider.common

import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption

class OpenOptions(
    val read: Boolean,
    val write: Boolean,
    val append: Boolean,
    val truncateExisting: Boolean,
    val create: Boolean,
    val createNew: Boolean,
    val deleteOnClose: Boolean,
    val sparse: Boolean,
    val sync: Boolean,
    val dsync: Boolean,
    val noFollowLinks: Boolean
)

fun Array<out OpenOption>.toOpenOptions(): OpenOptions = asIterable().toSet().toOpenOptions()

fun Set<OpenOption>.toOpenOptions(): OpenOptions {
    val standard = linkedSetOf<StandardOpenOption>()
    var noFollowLinks = false

    forEach { option ->
        when (option) {
            is StandardOpenOption -> standard += option
            LinkOption.NOFOLLOW_LINKS -> noFollowLinks = true
            else -> throw UnsupportedOperationException(option.toString())
        }
    }

    var read = StandardOpenOption.READ in standard
    var write = StandardOpenOption.WRITE in standard
    var append = StandardOpenOption.APPEND in standard
    var truncate = StandardOpenOption.TRUNCATE_EXISTING in standard
    var create = StandardOpenOption.CREATE in standard
    var createNew = StandardOpenOption.CREATE_NEW in standard
    val deleteOnClose = StandardOpenOption.DELETE_ON_CLOSE in standard

    if (!read && !write) {
        if (append) write = true else read = true
    }
    if (deleteOnClose) {
        noFollowLinks = true
    }

    check(!(read && append)) {
        StandardOpenOption.READ.toString() + " + " + StandardOpenOption.APPEND
    }
    check(!(append && truncate)) {
        StandardOpenOption.APPEND.toString() + " + " + StandardOpenOption.TRUNCATE_EXISTING
    }

    if (!write) {
        append = false
        truncate = false
        create = false
        createNew = false
    }

    return OpenOptions(
        read = read,
        write = write,
        append = append,
        truncateExisting = truncate,
        create = create,
        createNew = createNew,
        deleteOnClose = deleteOnClose,
        sparse = StandardOpenOption.SPARSE in standard,
        sync = StandardOpenOption.SYNC in standard,
        dsync = StandardOpenOption.DSYNC in standard,
        noFollowLinks = noFollowLinks
    )
}
