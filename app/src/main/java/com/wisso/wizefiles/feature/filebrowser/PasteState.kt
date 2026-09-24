package com.wisso.wizefiles.feature.filebrowser

// TODO: Make immutable?
class PasteState(
    var copy: Boolean = false,
    val files: FileItemSet = fileItemSetOf()
)
