package com.binisog.amharickeyboard.core

/**
 * Turns the links people copy from GIF sites into links to the picture itself.
 *  - GIPHY: `giphy.com/gifs/name-ID`, `media.giphy.com/media/ID/…` → a small GIF of that ID.
 *  - Any other web page (Tenor, …): its `og:image` / `twitter:image`, preferring a GIF.
 */
object GifLinks {
    private val giphyPage = Regex("""^https?://(?:www\.)?giphy\.com/(?:gifs|stickers|embed)/(?:[^/?#]*-)?([A-Za-z0-9]{8,})(?:[/?#].*)?$""")
    private val giphyMedia = Regex("""^https?://(?:media\d*|i)\.giphy\.com/(?:media/(?:v1\.[^/]+/)?)?([A-Za-z0-9]{8,})(?:\.\w+)?(?:/[^?#]*)?(?:[?#].*)?$""")
    private val metaTag = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attr = Regex("""(\w[\w:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")
    private val imageKeys = setOf("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src")

    /** A direct GIF link for a GIPHY page or media link, or null for other links. */
    fun giphyDirect(link: String): String? {
        val l = link.trim()
        val id = giphyPage.matchEntire(l)?.groupValues?.get(1) ?: giphyMedia.matchEntire(l)?.groupValues?.get(1) ?: return null
        // "200.gif" is GIPHY's 200px-high rendition: small enough to send quickly.
        return "https://media.giphy.com/media/$id/200.gif"
    }

    /** The preview picture a web page declares for sharing, preferring a GIF; null if none. */
    fun pageImage(html: String, base: String): String? {
        val found = metaTag.findAll(html).mapNotNull { m ->
            val attrs = attr.findAll(m.value).associate { a ->
                a.groupValues[1].lowercase() to (a.groups[3]?.value ?: a.groups[4]?.value ?: "")
            }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: return@mapNotNull null
            if (key !in imageKeys) return@mapNotNull null
            attrs["content"]?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }
        }.map { resolve(base, it) }.toList()
        if (found.isEmpty()) return null
        val gif = found.firstOrNull { it.substringBefore('?').endsWith(".gif", ignoreCase = true) }
        return (gif ?: found.first()).let { giphyDirect(it) ?: it }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")

    private fun resolve(base: String, link: String): String = when {
        link.startsWith("http://") || link.startsWith("https://") -> link
        link.startsWith("//") -> "https:$link"
        else -> runCatching { java.net.URI(base).resolve(link).toString() }.getOrDefault(link)
    }
}
