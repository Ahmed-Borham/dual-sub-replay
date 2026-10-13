package com.kienhoang.dualsubreplay.ui

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test

class OffScreenPlaybackWebViewTest {
    @Test
    fun hiddenWindowStaysVisibleOnlyWhileAVideoPlays() {
        assertEquals(View.VISIBLE, windowVisibilityForPage(View.GONE, keepPlaying = true))
        assertEquals(View.VISIBLE, windowVisibilityForPage(View.INVISIBLE, keepPlaying = true))
        assertEquals(View.GONE, windowVisibilityForPage(View.GONE, keepPlaying = false))
        assertEquals(View.VISIBLE, windowVisibilityForPage(View.VISIBLE, keepPlaying = false))
    }
}
