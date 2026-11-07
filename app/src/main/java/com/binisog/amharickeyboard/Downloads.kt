package com.binisog.amharickeyboard

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** Small, size-limited HTTP(S) downloads for user-supplied links (pictures, emoticon packs). */
object Downloads {

    class Result(val bytes: ByteArray, val contentType: String)

    /** Downloads [link], following up to five redirects; at most [maxBytes]. Call off the main thread. */
    fun fetch(link: String, maxBytes: Int, accept: String = "*/*"): Result {
        var url = URI(link.trim()).toURL()
        if (url.protocol != "https" && url.protocol != "http") throw IOException("Only http(s) links are supported")
        var redirects = 0
        while (true) {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", accept)
            try {
                val code = conn.responseCode
                if (code in 300..399 && redirects < 5) {
                    val next = conn.getHeaderField("Location") ?: throw IOException("Broken redirect")
                    url = conn.url.toURI().resolve(next).toURL()
                    if (url.protocol != "https" && url.protocol != "http") throw IOException("Unsupported redirect")
                    redirects++
                    continue
                }
                if (code !in 200..299) throw IOException("Server answered $code")
                val type = conn.contentType.orEmpty().substringBefore(';').trim().lowercase()
                return Result(conn.inputStream.use { readLimited(it, maxBytes) }, type)
            } finally {
                conn.disconnect()
            }
        }
    }

    fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > maxBytes) throw IOException("File is larger than ${maxBytes / (1024 * 1024)} MB")
        }
        return out.toByteArray()
    }
}
