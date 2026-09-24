package com.wisso.wizefiles.feature.about

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutFaqSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `about action icons use the Material primary color`() {
        val layout = source("app/src/main/res/layout/fragment_about.xml")

        assertFalse("app:tint=\"?colorControlNormal\"" in layout)
        assertTrue(
            layout.lineSequence().count { "app:tint=\"?attr/colorPrimary\"" in it } == 10
        )
    }

    @Test
    fun `faq covers current transfer connectivity search security backup and privacy features`() {
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/about/FaqFragment.kt"
        )
        val strings = source("app/src/main/res/values/strings.xml")
        val requiredTopics = listOf(
            "transfer_center",
            "instant_search",
            "sync_backup",
            "nearby_transfer",
            "security",
            "settings_backup",
            "crash_reports"
        )

        requiredTopics.forEach { topic ->
            assertTrue("faq_question_$topic" in fragment)
            assertTrue("name=\"faq_question_$topic\"" in strings)
            assertTrue("name=\"faq_answer_$topic\"" in strings)
        }
        assertTrue("Nothing is uploaded automatically" in strings)
    }

    private fun source(path: String): String = File(root, path).readText()
}
