package com.kienhoang.dualsubreplay.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationEngineTest {
    @Test fun googleIsTheDefault() {
        assertEquals(TranslationEngine.GOOGLE_WEB, storedTranslationEngine(null))
        assertEquals(TranslationEngine.GOOGLE_WEB, storedTranslationEngine("unknown"))
        assertEquals(TranslationEngine.GOOGLE_WEB, storedTranslationEngine("google_web"))
    }

    @Test fun onDeviceStaysWhenChosen() {
        assertEquals(TranslationEngine.ON_DEVICE, storedTranslationEngine("on_device"))
    }
}
