package com.wisso.wizefiles.provider.root

enum class RootStrategy {
    NEVER,
    AUTOMATIC,
    ALWAYS;

    internal fun selectsRoot(rootRequired: Boolean): Boolean = when (this) {
        NEVER -> false
        AUTOMATIC -> rootRequired
        ALWAYS -> true
    }
}
