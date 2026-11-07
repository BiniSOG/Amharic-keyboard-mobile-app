package com.binisog.amharickeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

/**
 * The keyboard's optional background picture. It is decoded, rotated upright, scaled to at
 * most [MAX_SIDE] px and stored as a JPEG in device-protected storage, so the keyboard can
 * draw it even before the first unlock. All functions here do I/O: call them off the main thread.
 */
object BackgroundImage {
    private const val MAX_SIDE = 1600
    private const val MAX_BYTES = 25 * 1024 * 1024
    private const val FILE = "keyboard_background.jpg"

    fun file(ctx: Context): File = File(DirectBoot.deviceProtected(ctx).filesDir, FILE)

    fun exists(ctx: Context): Boolean = file(ctx).isFile

    /** Imports an image picked from the gallery. */
    fun importFromUri(ctx: Context, uri: Uri) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { readLimited(it) } ?: throw IOException("Cannot open image")
        save(ctx, bytes)
    }

    /** Downloads an image from an http(s) link. */
    fun importFromUrl(ctx: Context, link: String) {
        val url = java.net.URI(link.trim()).toURL()
        if (url.protocol != "https" && url.protocol != "http") throw IOException("Only http(s) links are supported")
        var conn = url.openConnection() as HttpURLConnection
        var redirects = 0
        while (true) {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "image/*")
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) { // e.g. http → https redirects are not followed automatically
                val next = conn.getHeaderField("Location") ?: break
                val target = conn.url.toURI().resolve(next).toURL()
                conn.disconnect()
                if (target.protocol != "https" && target.protocol != "http") throw IOException("Unsupported redirect")
                conn = target.openConnection() as HttpURLConnection
                redirects++
                continue
            }
            if (code !in 200..299) throw IOException("Server answered $code")
            break
        }
        val bytes = conn.inputStream.use { readLimited(it) }
        conn.disconnect()
        save(ctx, bytes)
    }

    fun remove(ctx: Context) { file(ctx).delete() }

    /** Decodes the stored picture, or null when there is none. */
    fun load(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.isFile) return null
        return runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) throw IOException("Image is larger than 25 MB")
        }
        return out.toByteArray()
    }

    private fun save(ctx: Context, bytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Cannot decode image")

        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val bmp = if (rotation != 0f || scale < 1f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true) else decoded

        val f = file(ctx)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
