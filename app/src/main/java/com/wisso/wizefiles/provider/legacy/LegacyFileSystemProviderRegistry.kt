package com.wisso.wizefiles.provider.legacy

import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap

private val providersByScheme = ConcurrentHashMap<String, Any>()

internal fun installDefaultFileSystemProvider(provider: Any) {
    installFileSystemProvider(provider)
}

internal fun installFileSystemProvider(provider: Any) {
    val scheme = providerScheme(provider)
    providersByScheme[scheme.lowercase()] = provider
}

internal fun installFileTypeDetector(detector: Any) {
    // On newer runtimes we can no longer install detectors via hidden static APIs.
    // Keep this as a no-op to preserve startup behavior without reflection crashes.
}

internal fun getInstalledFileSystemProvider(scheme: String): Any {
    providersByScheme[scheme.lowercase()]?.let { return it }
    FileSystemProvider.installedProviders().firstOrNull {
        it.scheme.equals(scheme, ignoreCase = true)
    }?.let { return it }
    throw IllegalStateException("Provider not found: $scheme")
}

private fun providerScheme(provider: Any): String = when (provider) {
    is FileSystemProvider -> provider.scheme
    else -> provider.javaClass.getMethod("getScheme").invoke(provider) as? String
} ?: throw IllegalStateException("Provider does not expose scheme: ${provider.javaClass.name}")
