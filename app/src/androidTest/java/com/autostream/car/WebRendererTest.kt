package com.autostream.car

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.car.app.SurfaceContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Simuliert das Auto-Display: Die "Surface des Autos" ist hier ein ImageReader,
 * aus dem wir die gerenderten Bilder auslesen.
 */
@RunWith(AndroidJUnit4::class)
class WebRendererTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val w = 800
    private val h = 480
    private var reader: ImageReader? = null
    private var renderer: WebRenderer? = null
    private var lastFrame: Bitmap? = null

    private fun page(body: String) =
        "data:text/html;base64," + Base64.encodeToString(
            "<html><head><meta name='viewport' content='width=device-width'></head>$body</html>".toByteArray(),
            Base64.NO_WRAP
        )

    private val redPageGreenOnClick = page(
        "<body style='margin:0;background:#ff0000;height:100vh' " +
            "onclick=\"document.body.style.background='#00ff00'\"></body>"
    )

    private fun start(url: String, state: MotionState = MotionState.STOPPED): WebRenderer {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        reader = r
        return WebRenderer(context, url, true).also {
            renderer = it
            it.onSurfaceAvailable(SurfaceContainer(r.surface, w, h, 160))
            it.setMotionState(state)
        }
    }

    @After fun tearDown() {
        renderer?.release()
        SystemClock.sleep(500)
        reader?.close()
    }

    private fun frame(): Bitmap? {
        val image = reader?.acquireLatestImage() ?: return lastFrame
        image.use {
            val plane = it.planes[0]
            val rowPixels = plane.rowStride / plane.pixelStride
            val bmp = Bitmap.createBitmap(rowPixels, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            lastFrame = Bitmap.createBitmap(bmp, 0, 0, w, h)
        }
        return lastFrame
    }

    private fun centerColor(): Int? = frame()?.getPixel(w / 2, h / 2)

    private fun near(c: Int?, r: Int, g: Int, b: Int, tol: Int = 50) = c != null &&
        Math.abs(Color.red(c) - r) < tol && Math.abs(Color.green(c) - g) < tol && Math.abs(Color.blue(c) - b) < tol

    private fun waitFor(what: String, timeoutMs: Long = 20_000, check: (Int?) -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (check(centerColor())) return
            SystemClock.sleep(200)
        }
        val c = centerColor()
        save("FAIL_$what")
        throw AssertionError("$what: Mittelpunkt-Farbe = ${c?.let { Integer.toHexString(it) }}")
    }

    private fun save(name: String) {
        val bmp = frame() ?: return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/autostream-test")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "Screenshot gespeichert: $name.png")
    }

    private fun js(code: String): String? {
        var result: String? = null
        val latch = CountDownLatch(1)
        renderer!!.evaluate(code) { result = it; latch.countDown() }
        latch.await(10, TimeUnit.SECONDS)
        return result
    }

    @Test fun pageIsDrawnOnCarSurface() {
        start(redPageGreenOnClick)
        waitFor("rot sichtbar") { near(it, 255, 0, 0) }
        save("01_seite_auf_autodisplay")
    }

    @Test fun tapOnCarScreenReachesPage() {
        val r = start(redPageGreenOnClick)
        waitFor("rot sichtbar") { near(it, 255, 0, 0) }
        r.onClick(w / 2f, h / 2f)
        waitFor("nach Tippen grün") { near(it, 0, 255, 0) }
        save("02_nach_tippen")
    }

    @Test fun scrollOnCarScreenScrollsPage() {
        val r = start(page(
            "<body style='margin:0'><div style='height:100vh;background:#ff0000'></div>" +
                "<div style='height:300vh;background:#0000ff'></div></body>"
        ))
        waitFor("rot sichtbar") { near(it, 255, 0, 0) }
        repeat(15) {
            r.onScroll(0f, 40f)
            SystemClock.sleep(16)
        }
        waitFor("nach Wischen blau") { near(it, 0, 0, 255) }
        save("03_nach_wischen")
    }

    @Test fun movingCarCoversVideoAndIgnoresTaps() {
        val r = start(redPageGreenOnClick)
        waitFor("rot sichtbar") { near(it, 255, 0, 0) }
        r.setMotionState(MotionState.MOVING)
        waitFor("während Fahrt abgedeckt") { c -> c != null && Color.red(c) < 60 && Color.green(c) < 60 }
        save("04_sperre_waehrend_fahrt")
        r.onClick(w / 2f, h / 2f)
        SystemClock.sleep(1000)
        r.setMotionState(MotionState.STOPPED)
        waitFor("nach Anhalten wieder sichtbar") { near(it, 255, 0, 0) || near(it, 0, 255, 0) }
        SystemClock.sleep(1500)
        assertFalse("Tippen während der Fahrt darf nicht ankommen", near(centerColor(), 0, 255, 0))
    }

    @Test fun unknownMotionStateBlocks() {
        start(redPageGreenOnClick, MotionState.UNKNOWN)
        SystemClock.sleep(3000)
        val c = centerColor()
        save("05_sperre_ohne_daten")
        assertTrue("Ohne Geschwindigkeitsdaten muss gesperrt sein", c == null || (Color.red(c) < 60))
    }

    /** Info-Test: Welche Widevine-Stufen bietet die WebView auf dem Autodisplay? */
    @Test fun reportDrmSupport() {
        start("https://example.com")
        SystemClock.sleep(8000)
        js("""
            (function(){window.__drm='pending';
              if(!navigator.requestMediaKeySystemAccess){window.__drm='kein EME (kein sicherer Kontext?)';return;}
              var res=[];
              function t(rob){
                var cfg=[{initDataTypes:['cenc'],videoCapabilities:[{contentType:'video/mp4; codecs="avc1.42E01E"',robustness:rob}]}];
                return navigator.requestMediaKeySystemAccess('com.widevine.alpha',cfg)
                  .then(function(a){return a.createMediaKeys().then(function(){res.push(rob+': OK');});})
                  .catch(function(e){res.push(rob+': '+e.name+' '+e.message);});
              }
              t('SW_SECURE_CRYPTO').then(function(){return t('SW_SECURE_DECODE');})
                .then(function(){return t('HW_SECURE_ALL');})
                .then(function(){window.__drm=res.join(' | ');});
            })();
        """.trimIndent())
        var result: String? = null
        repeat(40) {
            result = js("window.__drm")
            if (result != null && result != "\"pending\"" && result != "null") return@repeat
            SystemClock.sleep(500)
        }
        Log.i(TAG, "DRM-ERGEBNIS: $result")
    }

    /** Info-Test: Screenshots echter Seiten auf dem simulierten Autodisplay. */
    @Test fun screenshotsOfRealSites() {
        for ((name, url) in listOf(
            "10_youtube" to "https://www.youtube.com",
            "11_netflix" to "https://www.netflix.com/de/",
        )) {
            start(url)
            SystemClock.sleep(15_000)
            save(name)
            renderer?.release()
            SystemClock.sleep(500)
            reader?.close()
            lastFrame = null
        }
        renderer = null
        reader = null
    }

    companion object {
        private const val TAG = "AutoStreamTest"
    }
}
