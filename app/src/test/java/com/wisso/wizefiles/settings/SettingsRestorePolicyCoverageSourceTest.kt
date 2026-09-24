package com.wisso.wizefiles.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRestorePolicyCoverageSourceTest {

    @Test
    fun `backup registry and restore policies stay aligned`() {
        val registrySource = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/settings/SettingsBackupModels.kt"
        )
        val policySource = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/settings/SettingsRestoreValidator.kt"
        )
        val registryBlock = registrySource
            .substringAfter("object SettingsBackupRegistry")
            .substringAfter("setOf(")
            .substringBefore("\n    )")
        val policyBlock = policySource
            .substringAfter("private fun buildPolicies(): Map<String, RestorePolicy> = mapOf(")
            .substringBefore("\n    )\n\n    private fun securityBooleanPolicy")
        val keyPattern = Regex("""R\.string\.(pref_key_[a-z0-9_]+)""")

        val registryKeys = keyPattern.findAll(registryBlock).map { it.groupValues[1] }.toSet()
        val policyKeys = keyPattern.findAll(policyBlock).map { it.groupValues[1] }.toSet()

        assertEquals("Every backup key must have exactly one restore policy", registryKeys, policyKeys)
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) return direct.readText()
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        throw java.io.FileNotFoundException(path)
    }
}
