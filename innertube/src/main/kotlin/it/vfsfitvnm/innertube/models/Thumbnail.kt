package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
) {
    val isResizable: Boolean
        get() = !url.startsWith("https://i.ytimg.com")

    fun size(size: Int): String = resizeThumbnailUrl(url, size)

    companion object {
        /**
         * Google image URLs carry their size after the last '=':
         *   https://yt3.googleusercontent.com/<id>=w60-h60-l90-rj
         * YouTube moved artwork from lh3.googleusercontent.com to
         * yt3.googleusercontent.com, which the old host check didn't match,
         * so the 60x60 original was stretched across the player. Any
         * *.googleusercontent.com / *.ggpht.com host is resized here, and the
         * existing size options are replaced rather than appended to.
         */
        fun resizeThumbnailUrl(url: String, size: Int): String {
            val host = url.substringAfter("://", "").substringBefore('/')
            val resizable = host.endsWith("googleusercontent.com") || host.endsWith("ggpht.com")
            if (!resizable || size <= 0) return url

            val path = url.substringAfter("://").substringAfter('/', "")
            val base = if (path.contains('=')) url.substringBeforeLast('=') else url
            return "$base=w$size-h$size-l90-rj"
        }
    }
}
