package com.binisog.amharickeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.binisog.amharickeyboard.core.GifLinks
import java.io.File
import java.io.IOException

/**
 * Third-party emoticons the user adds from the internet:
 *  - a text pack (a plain-text file with one emoticon per line, e.g. kaomoji), or
 *  - a sticker (a PNG / JPEG / GIF / WebP picture), sent as an image to apps that accept them.
 * Stored privately in the app's files; nothing is shared unless the user picks it.
 */
object CustomEmoticons {
    private const val MAX_TEXT_BYTES = 256 * 1024
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_PAGE_BYTES = 3 * 1024 * 1024
    private const val MAX_TEXT_ITEMS = 500
    private const val MAX_STICKERS = 200

    enum class Added { TEXT, STICKER }

    private fun dir(ctx: Context) = File(ctx.filesDir, "emoticons").apply { mkdirs() }
    fun stickerDir(ctx: Context) = File(dir(ctx), "stickers").apply { mkdirs() }
    private fun textFile(ctx: Context) = File(dir(ctx), "custom.txt")

    fun texts(ctx: Context): List<String> =
        textFile(ctx).takeIf { it.isFile }?.readLines(Charsets.UTF_8)?.filter { it.isNotBlank() } ?: emptyList()

    fun stickers(ctx: Context): List<File> =
        stickerDir(ctx).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun isEmpty(ctx: Context) = texts(ctx).isEmpty() && stickers(ctx).isEmpty()

    /** Downloads [link] and adds what it contains. Call off the main thread. Returns what was added and how many. */
    fun importFromUrl(ctx: Context, link: String): Pair<Added, Int> {
        // GIPHY links (page or media) go straight to the GIF file.
        var res = Downloads.fetch(GifLinks.giphyDirect(link) ?: link, maxOf(MAX_IMAGE_BYTES, MAX_PAGE_BYTES))
        if (imageExtension(res.bytes) == null && looksLikeWebPage(res)) {
            // A web page (Tenor, a GIF site, …): take the picture it declares for sharing.
            val html = String(res.bytes, 0, minOf(res.bytes.size, MAX_PAGE_BYTES), Charsets.UTF_8)
            GifLinks.pageImage(html, link)?.let { res = Downloads.fetch(it, MAX_IMAGE_BYTES) }
        }
        val ext = imageExtension(res.bytes)
        if (ext != null) return Added.STICKER to addSticker(ctx, res.bytes, ext)
        if (res.contentType.startsWith("image/")) throw IOException("Unsupported picture format")
        if (looksLikeWebPage(res)) throw IOException(
            "that link is a web page, not a picture. Long-press the picture on the page, choose \"Copy image link\" (or \"Open image in new tab\" and copy that address), and add that link",
        )
        if (res.bytes.size > MAX_TEXT_BYTES) throw IOException("Text pack is too large")
        val items = String(res.bytes, Charsets.UTF_8).lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 60 && !it.startsWith("#") && !it.startsWith("<") }
            .distinct().take(MAX_TEXT_ITEMS).toList()
        if (items.isEmpty()) throw IOException("No emoticons found in that file")
        val merged = (texts(ctx) + items).distinct().take(MAX_TEXT_ITEMS)
        textFile(ctx).writeText(merged.joinToString("\n"), Charsets.UTF_8)
        return Added.TEXT to items.size
    }

    private fun addSticker(ctx: Context, bytes: ByteArray, ext: String): Int {
        if (stickers(ctx).size >= MAX_STICKERS) throw IOException("You already have $MAX_STICKERS stickers")
        val f = File(stickerDir(ctx), "sticker_${System.currentTimeMillis()}.$ext")
        f.writeBytes(bytes)
        return 1
    }

    fun removeSticker(file: File) { file.delete() }

    fun clear(ctx: Context) { dir(ctx).deleteRecursively() }

    fun mimeType(file: File): String = when (file.extension.lowercase()) {
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "image/png"
    }

    private fun looksLikeWebPage(res: Downloads.Result): Boolean {
        if (res.contentType.contains("html") || res.contentType.contains("xml") ||
            res.contentType.contains("javascript") || res.contentType.contains("json")) return true
        val head = String(res.bytes, 0, minOf(res.bytes.size, 2048), Charsets.UTF_8).trimStart().lowercase()
        return head.startsWith("<") || head.contains("<html") || head.contains("<!doctype") || head.startsWith("{")
    }

    /** Detects a supported picture by its first bytes (the server's content type is not trusted). */
    private fun imageExtension(b: ByteArray): String? = when {
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() -> "png"
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
        b.size > 6 && String(b, 0, 3, Charsets.US_ASCII) == "GIF" -> "gif"
        b.size > 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> null
    }

    fun isAnimated(file: File) = file.extension.equals("gif", true) || file.extension.equals("webp", true)

    /** Small thumbnail for the emoji panel. */
    fun thumbnail(file: File, sizePx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
