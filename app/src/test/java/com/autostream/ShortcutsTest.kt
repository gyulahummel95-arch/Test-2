package com.autostream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun defaultsContainAllServices() {
        val names = Shortcuts.load(context).map { it.name }
        assertEquals(
            listOf("Netflix", "YouTube", "Disney+", "Prime Video", "HBO Max", "Paramount+", "Twitch"),
            names
        )
        assertTrue(Shortcuts.load(context).all { it.url.startsWith("https://") })
    }

    @Test fun audioWhileDrivingDefaultsOnAndCanBeDisabled() {
        assertTrue(Shortcuts.audioWhileDriving(context))
        Shortcuts.setAudioWhileDriving(context, false)
        assertEquals(false, Shortcuts.audioWhileDriving(context))
    }

    @Test fun diagnosticsAreStoredAndCleared() {
        Diagnostics.set(context, Diagnostics.SURFACE, "1920 × 1080, 320 dpi")
        assertTrue(Diagnostics.report(context).contains("1920 × 1080"))
        Diagnostics.clear(context)
        assertTrue(Diagnostics.get(context, Diagnostics.SURFACE) == null)
    }
}
