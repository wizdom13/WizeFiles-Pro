package com.wisso.wizefiles.storage.path

import java.io.File

fun AppPath.asLocalAppPathOrNull(): LocalAppPath? = this as? LocalAppPath

fun AppPath.toLocalFileOrNull(): File? = asLocalAppPathOrNull()?.file
