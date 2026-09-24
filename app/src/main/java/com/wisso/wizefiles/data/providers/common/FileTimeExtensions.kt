package com.wisso.wizefiles.provider.common

import java.time.Instant
import java.nio.file.attribute.FileTime
import kotlin.reflect.KClass

val KClass<FileTime>.EPOCH: FileTime
    get() = FileTime.from(Instant.EPOCH)
