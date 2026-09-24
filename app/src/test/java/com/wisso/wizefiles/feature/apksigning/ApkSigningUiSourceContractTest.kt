package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSigningUiSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `password controls never save state or offer remember password`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApkSignVerifyActivity.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/PackageSigningActivity.kt"
        )
        assertTrue("isSaveEnabled = false" in activity)
        assertTrue("IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS" in activity)
        assertTrue("characters(" in activity)
        assertTrue("fill('\\u0000')" in activity)
        assertFalse("rememberPassword" in activity)
        assertFalse("SharedPreferences" in activity)
    }

    @Test
    fun `single apk actions and signing resume are wired`() {
        val browser = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserPackageSelectionActionHandler.kt"
        )
        val transferCenter = source(
            "app/src/main/java/com/wisso/wizefiles/feature/transfer/TransferCenterActivity.kt"
        )
        assertTrue("action_sign_apk" in browser)
        assertTrue("action_verify_apk" in browser)
        assertTrue("singleFile?.mimeType?.isApk == true" in browser)
        assertTrue("createResumeIntent" in transferCenter)
        assertTrue("TransferOperationType.APK_SIGN" in transferCenter)
    }

    @Test
    fun `screen uses the shared Material control language`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApkSignVerifyActivity.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/PackageSigningActivity.kt"
        )
        assertTrue("MaterialToolbar" in activity)
        assertTrue("MaterialButtonToggleGroup" in activity)
        assertTrue("MaterialButton" in activity)
        assertTrue("TextInputLayout" in activity)
        assertTrue("TextInputEditText" in activity)
        assertTrue("MaterialAutoCompleteTextView" in activity)
        assertTrue("MaterialCheckBox" in activity)
        assertFalse("android.widget.Button" in activity)
        assertFalse("android.widget.EditText" in activity)
        assertFalse("android.widget.Spinner" in activity)
        assertFalse("android.widget.CheckBox" in activity)
    }

    @Test
    fun `screen respects system bars and presents friendly provider paths`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApkSignVerifyActivity.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/PackageSigningActivity.kt"
        )
        assertTrue("WindowInsetsCompat.Type.statusBars()" in activity)
        assertTrue("WindowInsetsCompat.Type.navigationBars()" in activity)
        assertTrue("WindowInsetsCompat.Type.ime()" in activity)
        assertTrue("friendlyDecode" in activity)
        assertTrue("displayLocation" in activity)
        assertTrue("suggestedName = signedApkFileName" in activity)
        assertTrue("R.string.apk_signing_choose_destination" in activity)
        assertFalse("button(R.string.apk_signing_choose_file" in activity)
        assertFalse("path?.toUriString() ?: getString" in activity)
    }

    @Test
    fun `verification uses provider staging off the main thread`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApkSignVerifyActivity.kt"
        )
        val verification = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApkVerificationExecutor.kt"
        )
        val keys = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/SigningKeyCoordinator.kt"
        )
        assertTrue("verificationExecutor.verify" in activity)
        assertTrue("withContext(Dispatchers.IO)" in activity)
        assertTrue("ProviderApkVerifier" in verification)
        assertTrue("ProviderApkKeyStoreService" in keys)
    }

    @Test
    fun `apk screen delegates mutable workflows to focused collaborators`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApkSignVerifyActivity.kt"
        )
        listOf(
            "ApkSignVerifyViewModel",
            "SigningInputValidator",
            "SigningKeyCoordinator",
            "ApkSigningExecutor",
            "ApkVerificationExecutor",
            "SigningResultRenderer"
        ).forEach { assertTrue("Missing $it delegation", it in activity) }
    }

    @Test
    fun `all signing screens use compatible TextInputLayout child params`() {
        val helper = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/TextInputLayoutParams.kt"
        )
        assertTrue("LinearLayout.LayoutParams(" in helper)
        assertTrue("ViewGroup.LayoutParams.MATCH_PARENT" in helper)
        assertTrue("ViewGroup.LayoutParams.WRAP_CONTENT" in helper)

        listOf(
            "ApkSignVerifyActivity.kt",
            "AabSignVerifyActivity.kt",
            "ApksSignVerifyActivity.kt",
            "XapkSignVerifyActivity.kt"
        ).forEach { fileName ->
            val activity = source(
                "app/src/main/java/com/wisso/wizefiles/feature/apksigning/$fileName"
            ) + source(
                "app/src/main/java/com/wisso/wizefiles/feature/apksigning/PackageSigningActivity.kt"
            ) + source(
                "app/src/main/java/com/wisso/wizefiles/feature/apksigning/SplitSetSignVerifyActivity.kt"
            )
            assertTrue(
                "$fileName must use the shared TextInputLayout params",
                "textInputChildLayoutParams()" in activity
            )
            assertFalse(
                "$fileName must not pass generic params to TextInputLayout",
                Regex("layout\\.addView\\([^\\n]+, matchWrap\\(\\)\\)")
                    .containsMatchIn(activity)
            )
        }
    }

    @Test
    fun `shared signing UI and split set models stay separated`() {
        val base=source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/PackageSigningActivity.kt"
        )
        val apk=source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApkSignVerifyActivity.kt"
        )
        val aab=source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/AabSignVerifyActivity.kt"
        )
        val splitActivity=source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/SplitSetSignVerifyActivity.kt"
        )
        val splitModels=source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/SplitSetSigningModels.kt"
        )
        assertTrue("addSigningPathRow" in base)
        assertTrue("addSigningEditText" in base)
        assertTrue("addSigningPathRow(content" in apk)
        assertTrue("addSigningPathRow(content" in aab)
        assertFalse("private fun pathRow" in apk)
        assertFalse("private fun pathRow" in aab)
        assertFalse("enum class SplitSetPackageKind" in splitActivity)
        assertTrue("enum class SplitSetPackageKind" in splitModels)
    }

    private fun source(path: String): String = File(root, path).readText()
}
