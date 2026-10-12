package com.kienhoang.dualsubreplay.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The licence screen shows the licences of what the APK actually contains. */
class LicenseAssetsTest {
    @Test
    fun sharedLicenceFilesExistAndTheGplIsGone() {
        for (name in LICENSE_ASSETS) {
            assertTrue(name, File("src/main/assets/$name").isFile)
        }
        assertFalse(File("src/main/assets/licenses/GPL-3.0.txt").exists())
        assertTrue("Apache License" in File("src/main/assets/licenses/libraries.txt").readText())
    }

    @Test
    fun theTranslationNoticeNamesMlKit() {
        assertTrue("ML Kit" in File("src/main/assets/licenses/translation.txt").readText())
    }

    @Test
    fun noLicenceTextClaimsTheGpl() {
        val summaries = File("src/main/res").walk().filter { it.name == "strings_navigation.xml" }.toList()
        assertTrue(summaries.isNotEmpty())
        for (file in summaries) assertFalse(file.path, "GPL" in file.readText())
    }
}
