package com.binisog.amharickeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GifLinksTest {
    @Test fun giphyPageAndMediaLinksBecomeDirectGifs() {
        val want = "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/200.gif"
        assertEquals(want, GifLinks.giphyDirect("https://giphy.com/gifs/happy-dance-3o7TKSjRrfIPjeiVyM"))
        assertEquals(want, GifLinks.giphyDirect("https://giphy.com/gifs/3o7TKSjRrfIPjeiVyM"))
        assertEquals(want, GifLinks.giphyDirect("https://media4.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif?cid=abc&rid=giphy.gif"))
        assertEquals(want, GifLinks.giphyDirect("https://i.giphy.com/3o7TKSjRrfIPjeiVyM.webp"))
        assertNull(GifLinks.giphyDirect("https://tenor.com/view/cat-gif-12345"))
    }

    @Test fun pageImagePrefersAGif() {
        val html = """
            <html><head>
            <meta property="og:image" content="https://media.tenor.com/abc/cat.png">
            <meta content="https://media.tenor.com/abc/cat.gif?x=1&amp;y=2" property='og:image'>
            </head></html>
        """
        assertEquals("https://media.tenor.com/abc/cat.gif?x=1&y=2", GifLinks.pageImage(html, "https://tenor.com/view/cat-gif-1"))
    }

    @Test fun pageImageResolvesRelativeLinksAndGiphy() {
        assertEquals("https://example.com/img/a.gif",
            GifLinks.pageImage("""<meta name="twitter:image" content="/img/a.gif">""", "https://example.com/page"))
        assertEquals("https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/200.gif",
            GifLinks.pageImage("""<meta property="og:image" content="https://media2.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif">""", "https://gph.is/x"))
        assertNull(GifLinks.pageImage("<html><title>no image</title></html>", "https://example.com"))
    }
}
