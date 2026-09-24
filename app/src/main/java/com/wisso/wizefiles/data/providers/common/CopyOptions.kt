package com.wisso.wizefiles.provider.common

import com.wisso.wizefiles.storage.MetadataPreservationReport
import java.nio.file.CopyOption
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

class MetadataPreservationCopyOption(
    val listener: (MetadataPreservationReport) -> Unit
) : CopyOption

class CopyOptions(
    val replaceExisting: Boolean,
    val copyAttributes: Boolean,
    val atomicMove: Boolean,
    val noFollowLinks: Boolean,
    val progressIntervalMillis: Long,
    val progressListener: ((Long) -> Unit)?,
    val metadataListener: ((MetadataPreservationReport) -> Unit)? = null
) {
    fun toArray(): Array<CopyOption> = buildList {
        if (replaceExisting) add(StandardCopyOption.REPLACE_EXISTING)
        if (copyAttributes) add(StandardCopyOption.COPY_ATTRIBUTES)
        if (atomicMove) add(StandardCopyOption.ATOMIC_MOVE)
        if (noFollowLinks) add(LinkOption.NOFOLLOW_LINKS)
        progressListener?.let { add(ProgressCopyOption(progressIntervalMillis, it)) }
        metadataListener?.let { add(MetadataPreservationCopyOption(it)) }
    }.toTypedArray()
}

fun Array<out CopyOption>.toCopyOptions(): CopyOptions {
    val standard = linkedSetOf<StandardCopyOption>()
    var noFollowLinks = false
    var progress: ProgressCopyOption? = null
    var metadata: MetadataPreservationCopyOption? = null

    forEach { option ->
        when (option) {
            is StandardCopyOption -> standard += option
            LinkOption.NOFOLLOW_LINKS -> noFollowLinks = true
            is ProgressCopyOption -> progress = option
            is MetadataPreservationCopyOption -> metadata = option
            else -> throw UnsupportedOperationException(option.toString())
        }
    }

    return CopyOptions(
        replaceExisting = StandardCopyOption.REPLACE_EXISTING in standard,
        copyAttributes = StandardCopyOption.COPY_ATTRIBUTES in standard,
        atomicMove = StandardCopyOption.ATOMIC_MOVE in standard,
        noFollowLinks = noFollowLinks,
        progressIntervalMillis = progress?.intervalMillis ?: 0L,
        progressListener = progress?.listener,
        metadataListener = metadata?.listener
    )
}
