package com.wisso.wizefiles.feature.packageinstaller

import java.util.Locale
import kotlin.math.abs

data class DeviceApkConfiguration(
    val supportedAbis: List<String>,
    val densityDpi: Int,
    val locales: List<String>
) {
    init {
        require(supportedAbis.isNotEmpty())
        require(densityDpi > 0)
    }
}

data class PackageSplitSelection(
    val selected: List<PackageApk>,
    val excluded: List<PackageApk>,
    val reasons: Map<String, String>
)

/**
 * Chooses device configuration APKs without interpreting untrusted names as paths. Required
 * feature splits remain selected; only well-known ABI, density, and locale configuration splits
 * are filtered. Package identity and dependency validation is performed before this selector.
 */
class PackageSplitSelector {
    fun select(
        apks: List<PackageApk>,
        device: DeviceApkConfiguration
    ): PackageSplitSelection {
        require(apks.count(PackageApk::isBase) == 1) {
            "A split set must contain exactly one base APK"
        }
        val normalizedAbis = device.supportedAbis.map(::normalize)
        val availableAbis = ABI_TOKENS.filter { abi ->
            apks.any { containsToken(it, abi) }
        }
        val selectedAbi = normalizedAbis.firstOrNull { it in availableAbis }
        val availableDensities = DENSITIES.filterKeys { density ->
            apks.any { containsToken(it, density) }
        }
        val selectedDensity = availableDensities.minByOrNull { (_, dpi) ->
            abs(dpi - device.densityDpi)
        }?.key
        val locales = device.locales.mapNotNull(::normalizeLocale).toSet()

        val selected = mutableListOf<PackageApk>()
        val excluded = mutableListOf<PackageApk>()
        val reasons = linkedMapOf<String, String>()
        apks.forEach { apk ->
            val abi = ABI_TOKENS.firstOrNull { containsToken(apk, it) }
            val density = DENSITIES.keys.firstOrNull { containsToken(apk, it) }
            val locale = localeToken(apk)
            val reason = when {
                abi != null && selectedAbi == null -> "No compatible ABI is available"
                abi != null && abi != selectedAbi -> "ABI $abi does not match this device"
                density != null && density != "nodpi" && density != selectedDensity ->
                    "Density $density is not the closest device match"
                locale != null && locales.isNotEmpty() && locale !in locales &&
                    locale.substringBefore('-') !in locales.map { it.substringBefore('-') } ->
                    "Language $locale is not active on this device"
                else -> null
            }
            if (reason == null) {
                selected += apk
            } else {
                excluded += apk
                reasons[apk.entryName] = reason
            }
        }
        require(selected.count(PackageApk::isBase) == 1) {
            "Split selection removed the base APK"
        }
        require(selectedAbi != null || availableAbis.isEmpty()) {
            "The package has no APK for a supported device ABI"
        }
        return PackageSplitSelection(selected, excluded, reasons)
    }

    private fun containsToken(apk: PackageApk, token: String): Boolean {
        val value = normalize("${apk.entryName}.${apk.splitName.orEmpty()}")
        return TOKEN_BOUNDARY.replace(token, "_").let { normalizedToken ->
            value.split('_').windowed(normalizedToken.split('_').size).any {
                it.joinToString("_") == normalizedToken
            }
        }
    }

    private fun localeToken(apk: PackageApk): String? {
        val value = "${apk.entryName.substringAfterLast('/')}.${apk.splitName.orEmpty()}"
            .lowercase(Locale.ROOT)
        val match = LOCALE_PATTERN.find(value) ?: return null
        val language = match.groupValues[1]
        if (language in NON_LOCALE_TOKENS) return null
        val region = match.groupValues[2].removePrefix("r").takeIf(String::isNotBlank)
        return if (region == null) language else "$language-${region.uppercase(Locale.ROOT)}"
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('-', '_')
        .replace('.', '_')

    private fun normalizeLocale(value: String): String? {
        val normalized = value.replace('_', '-').trim()
        if (normalized.isBlank()) return null
        val parts = normalized.split('-')
        val language = parts.first().lowercase(Locale.ROOT)
        if (language.length !in 2..3) return null
        val region = parts.getOrNull(1)?.takeIf { it.length == 2 }
        return if (region == null) language else "$language-${region.uppercase(Locale.ROOT)}"
    }

    private companion object {
        val TOKEN_BOUNDARY = Regex("[-.]")
        val ABI_TOKENS = listOf("arm64_v8a", "armeabi_v7a", "x86_64", "x86")
        val DENSITIES = linkedMapOf(
            "ldpi" to 120,
            "mdpi" to 160,
            "hdpi" to 240,
            "xhdpi" to 320,
            "xxhdpi" to 480,
            "xxxhdpi" to 640,
            "nodpi" to 0
        )
        val LOCALE_PATTERN = Regex(
            "(?:config[._-]|base[._-])([a-z]{2,3})(?:[._-](r?[a-z]{2}))?(?:[._-]|\\.apk|$)"
        )
        val NON_LOCALE_TOKENS = setOf(
            "apk", "arm", "base", "config", "dpi", "master", "split", "xhd", "xxh", "xxx"
        )
    }
}
