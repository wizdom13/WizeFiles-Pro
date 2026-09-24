package com.wisso.wizefiles.storage

/** Capabilities exposed without coupling consumers to a concrete remote provider. */
data class ProviderCapabilities(
    val canRead: Boolean = true,
    val canWrite: Boolean,
    val canMoveAtomically: Boolean,
    val canWatch: Boolean,
    val preservesModifiedTime: Boolean
)

/** Minimal provider boundary for discovery and routing. */
interface FileProviderDescriptor {
    val id: String
    val displayName: String
    val capabilities: ProviderCapabilities
}
