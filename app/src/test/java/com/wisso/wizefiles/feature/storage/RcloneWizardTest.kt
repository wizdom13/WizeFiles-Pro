// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import com.wisso.wizefiles.provider.rclone.appendRemoteSection
import com.wisso.wizefiles.provider.rclone.createRcloneRootPath
import com.wisso.wizefiles.provider.rclone.extractRemoteSection
import com.wisso.wizefiles.provider.rclone.parseRcloneConfigStep
import com.wisso.wizefiles.provider.rclone.parseRcloneProviders
import com.wisso.wizefiles.provider.rclone.shouldShowRcloneOption
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneWizardTest {
    @Test
    fun importedConfigurationFindsRemoteNamesAndTypes() {
        val configuration = """
            [personal-drive]
            type = drive
            token = {"access_token":"redacted"}

            [company-webdav]
            type = webdav
            url = https://cloud.example.test
        """.trimIndent()

        assertEquals(
            listOf(
                ImportedRemote("personal-drive", "drive"),
                ImportedRemote("company-webdav", "webdav")
            ),
            parseImportedRemotes(configuration)
        )
    }

    @Test
    fun advancedOptionsAcceptCommentsAndValuesContainingEquals() {
        assertEquals(
            listOf(
                "client_id" to "example",
                "token" to "a=b=c"
            ),
            parseAdvancedOptions(
                """
                    # shown only in Power user mode
                    client_id=example
                    token=a=b=c
                """.trimIndent()
            )
        )
    }

    @Test
    fun storageModelContainsNoCredentialFields() {
        val propertyNames = RcloneStorage::class.java.declaredFields.map { it.name }
        assertFalse(propertyNames.any { it.contains("password", ignoreCase = true) })
        assertFalse(propertyNames.any { it.contains("token", ignoreCase = true) })
        assertFalse(propertyNames.any { it.contains("secret", ignoreCase = true) })
        assertFalse(propertyNames.any { it.contains("accessKey", ignoreCase = true) })
    }

    @Test
    fun importedRemoteIsCopiedUnderStableInternalName() {
        val imported = """
            [Personal Drive]
            type = drive
            token = {"access_token":"private"}

            [Other]
            type = s3
        """.trimIndent()

        val section = extractRemoteSection(imported, "Personal Drive")!!
        val merged = appendRemoteSection("[existing]\ntype = webdav\n", "wf123", section)

        assertTrue(merged.contains("[existing]"))
        assertTrue(merged.contains("[wf123]"))
        assertTrue(merged.contains("type = drive"))
        assertFalse(merged.contains("[Other]"))
    }

    @Test
    fun storagePathUsesGeneratedRemoteAsStableAuthority() {
        val path = createRcloneRootPath("wf123").resolve("Documents/report.pdf")

        assertEquals("rclone://wf123/Documents/report.pdf", path.toUri().toString())
    }

    @Test
    fun providerSchemaParsesOptionsAndExamples() {
        val providers = parseRcloneProviders(
            JSONObject(
                """
                {
                  "providers": [{
                    "Name": "example",
                    "Description": "Example Cloud\nMore details",
                    "Options": [{
                      "Name": "region",
                      "Help": "Choose a region.",
                      "Default": "eu",
                      "Examples": [{"Value": "eu", "Help": "Europe"}],
                      "Required": true,
                      "IsPassword": false,
                      "Type": "string",
                      "Exclusive": true,
                      "Advanced": false,
                      "Hide": 0
                    }]
                  }]
                }
                """.trimIndent()
            )
        )

        assertEquals("example", providers.single().backendType)
        assertEquals("Example Cloud", providers.single().description)
        assertEquals("region", providers.single().options.single().name)
        assertEquals("Europe", providers.single().options.single().examples.single().label)
    }

    @Test
    fun regularModeHidesTechnicalAndAdvancedOptions() {
        val options = parseRcloneProviders(
            JSONObject(
                """
                {
                  "providers": [{
                    "Name": "example",
                    "Description": "Example",
                    "Options": [
                      {"Name":"username","Type":"string"},
                      {"Name":"client_id","Type":"string"},
                      {"Name":"chunk_size","Type":"SizeSuffix","Advanced":true}
                    ]
                  }]
                }
                """.trimIndent()
            )
        ).single().options.associateBy { it.name }

        assertTrue(shouldShowRcloneOption(options.getValue("username"), false))
        assertFalse(shouldShowRcloneOption(options.getValue("client_id"), false))
        assertFalse(shouldShowRcloneOption(options.getValue("chunk_size"), false))
        assertTrue(shouldShowRcloneOption(options.getValue("client_id"), true))
        assertTrue(shouldShowRcloneOption(options.getValue("chunk_size"), true))
    }

    @Test
    fun nonInteractiveStepCompletesOnlyWhenStateIsEmpty() {
        val question = parseRcloneConfigStep(
            JSONObject(
                """
                {
                  "State": "*oauth-islocal",
                  "Option": {
                    "Name": "config_is_local",
                    "Default": true,
                    "Type": "bool",
                    "Examples": [
                      {"Value": "true", "Help": "Yes"},
                      {"Value": "false", "Help": "No"}
                    ],
                    "Exclusive": true
                  },
                  "Error": ""
                }
                """.trimIndent()
            )
        )

        assertFalse(question.isComplete)
        assertEquals("config_is_local", question.option!!.name)
        assertTrue(parseRcloneConfigStep(JSONObject("""{"State":""}""")).isComplete)
    }

    @Test
    fun brandedProvidersUseSimpleRegularFlows() {
        assertEquals(RcloneRegularSetup.OAUTH, regularSetupFor("drive"))
        assertEquals(RcloneRegularSetup.OAUTH, regularSetupFor("onedrive"))
        assertEquals(RcloneRegularSetup.OAUTH, regularSetupFor("dropbox"))
        assertEquals(RcloneRegularSetup.OAUTH, regularSetupFor("box"))
        assertEquals(RcloneRegularSetup.OAUTH, regularSetupFor("pcloud"))
        assertEquals(RcloneRegularSetup.MEGA_CREDENTIALS, regularSetupFor("mega"))
        assertEquals(RcloneRegularSetup.GENERATED, regularSetupFor("protondrive"))
    }

    @Test
    fun regularOAuthFlowAutomaticallyUsesTheAndroidBrowser() {
        assertEquals("true", automaticRegularConfigAnswer("drive", "config_is_local"))
        assertEquals("true", automaticRegularConfigAnswer("dropbox", "config_refresh_token"))
        assertEquals(
            "false",
            automaticRegularConfigAnswer("drive", "config_change_team_drive")
        )
        assertEquals(
            null,
            automaticRegularConfigAnswer("dropbox", "config_change_team_drive")
        )
        assertEquals(null, automaticRegularConfigAnswer("drive", "drive_id"))
        assertEquals(null, automaticRegularConfigAnswer("mega", "config_is_local"))
    }

    @Test
    fun providerMenuStartsFocusedAndExpandsToEveryBackend() {
        val allProviders = listOf(
            "drive",
            "onedrive",
            "dropbox",
            "box",
            "pcloud",
            "mega",
            "webdav",
            "s3",
            "protondrive",
            "__import__"
        )

        assertEquals(
            listOf(
                "drive",
                "onedrive",
                "dropbox",
                "box",
                "pcloud",
                "mega",
                OTHER_CLOUD_PROVIDERS_KEY
            ),
            cloudProviderMenuKeys(allProviders, expanded = false)
        )
        assertEquals(allProviders, cloudProviderMenuKeys(allProviders, expanded = true))
    }

    @Test
    fun providersSupplyEditableAccountNameSuggestions() {
        assertEquals("Drive", defaultCloudAccountName("drive", "Google Drive"))
        assertEquals("Dropbox", defaultCloudAccountName("dropbox", "Dropbox"))
        assertEquals("WebDAV", defaultCloudAccountName("webdav", "WebDAV provider"))
        assertEquals(
            "Proton Drive",
            defaultCloudAccountName("protondrive", "Proton Drive")
        )
        assertEquals(null, defaultCloudAccountName(null, "Import"))
    }

    @Test
    fun brandedProvidersSortBeforeGeneratedProviders() {
        assertTrue(brandedProviderRank("drive") < brandedProviderRank("onedrive"))
        assertTrue(brandedProviderRank("mega") < brandedProviderRank("webdav"))
        assertEquals(Int.MAX_VALUE, brandedProviderRank("protondrive"))
    }
}
