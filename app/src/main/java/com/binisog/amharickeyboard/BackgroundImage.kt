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
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { Downloads.readLimited(it, MAX_BYTES) } ?: throw IOException("Cannot open image")
        save(ctx, bytes)
    }

    /** Downloads an image from an http(s) link. */
    fun importFromUrl(ctx: Context, link: String) {
        save(ctx, Downloads.fetch(link, MAX_BYTES, accept = "image/*").bytes)
    }

    fun remove(ctx: Context) { file(ctx).delete() }

    /** Decodes the stored picture, or null when there is none. */
    fun load(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.isFile) return null
        return runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
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
