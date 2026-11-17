package com.autostream

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Handy-Oberfläche: Einstellungen und Browser (inkl. Statusleiste ab Android 15). */
@RunWith(AndroidJUnit4::class)
class PhoneUiTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val context = instr.targetContext

    private fun screenshot(name: String) {
        val bmp = instr.uiAutomation.takeScreenshot() ?: return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/autostream-test")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("AutoStreamTest", "Screenshot gespeichert: $name.png")
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    @Test fun settingsPageShowsAllServices() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2500)
            var result: String? = null
            val latch = CountDownLatch(1)
            scenario.onActivity { a ->
                a.webView.evaluateJavascript("document.querySelectorAll('.svc').length") {
                    result = it; latch.countDown()
                }
            }
            latch.await(5, TimeUnit.SECONDS)
            assertTrue("Dienste fehlen: $result", result == Shortcuts.load(context).size.toString())
            var top = -1
            scenario.onActivity { a -> top = IntArray(2).also { a.webView.getLocationOnScreen(it) }[1] }
            assertTrue("Einstellungen liegen unter der Statusleiste ($top < ${statusBarHeight()})",
                top >= statusBarHeight() - 1)
            screenshot("40_handy_einstellungen")
        }
    }

    @Test fun browserToolbarIsBelowStatusBar() {
        val page = "data:text/html,<body style='margin:0;background:%23ff0000'><h1>Test</h1></body>"
        val intent = Intent(context, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_URL, page)
        ActivityScenario.launch<LoginActivity>(intent).use { scenario ->
            SystemClock.sleep(2500)
            var toolbarTop = -1
            var webTop = -1
            var toolbarBottom = -1
            var url = ""
            scenario.onActivity { a ->
                val loc = IntArray(2)
                a.toolbar.getLocationOnScreen(loc); toolbarTop = loc[1]; toolbarBottom = loc[1] + a.toolbar.height
                a.webView.getLocationOnScreen(loc); webTop = loc[1]
                url = a.urlField.text.toString()
            }
            Log.i("AutoStreamTest", "BROWSER: Statusleiste=${statusBarHeight()} Toolbar=$toolbarTop..$toolbarBottom Web=$webTop")
            assertTrue("Adressleiste unter der Statusleiste ($toolbarTop)", toolbarTop >= statusBarHeight() - 1)
            assertTrue("Webseite überlappt die Adressleiste", webTop >= toolbarBottom)
            assertTrue("Adresse fehlt: $url", url.startsWith("data:"))
            screenshot("41_handy_browser")
        }
    }
}
