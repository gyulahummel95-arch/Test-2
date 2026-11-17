package com.autostream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File

/** Eigenes Hintergrundbild für die Startseite im Auto. */
object Backgrounds {
    private const val FILE = "background.jpg"
    private const val MAX_SIZE = 1920

    private fun file(c: Context) = File(c.filesDir, FILE)

    /** Bild aus der Galerie verkleinern und speichern. */
    fun saveFromUri(c: Context, uri: Uri): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIZE * 2 || bounds.outHeight / sample > MAX_SIZE * 2) sample *= 2
        val bmp = c.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        return save(c, bmp)
    }

    fun save(c: Context, bitmap: Bitmap): Boolean {
        val scale = minOf(1f, MAX_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
        ) else bitmap
        file(c).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return true
    }

    fun exists(c: Context) = file(c).exists()

    fun clear(c: Context) {
        file(c).delete()
    }

    /** Als data:-URL für die Startseite. */
    fun dataUrl(c: Context): String {
        val f = file(c)
        if (!f.exists()) return ""
        return "data:image/jpeg;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }
}
