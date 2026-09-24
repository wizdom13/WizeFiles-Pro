package com.wisso.wizefiles.provider.common

import java.nio.file.DirectoryStream
import java.nio.file.Path

class PathListDirectoryStream(
    paths: List<Path>,
    filter: DirectoryStream.Filter<in Path>
) : PathIteratorDirectoryStream(paths.iterator(), null, filter)
