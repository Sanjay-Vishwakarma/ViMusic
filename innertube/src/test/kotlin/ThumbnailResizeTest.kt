import it.vfsfitvnm.innertube.models.Thumbnail.Companion.resizeThumbnailUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailResizeTest {
    @Test
    fun replacesSizeOnNewYt3Host() = assertEquals(
        "https://yt3.googleusercontent.com/abc_DEF-123=w1080-h1080-l90-rj",
        resizeThumbnailUrl("https://yt3.googleusercontent.com/abc_DEF-123=w60-h60-l90-rj", 1080)
    )

    @Test
    fun replacesSizeOnLegacyLh3Host() = assertEquals(
        "https://lh3.googleusercontent.com/xyz=w544-h544-l90-rj",
        resizeThumbnailUrl("https://lh3.googleusercontent.com/xyz=w120-h120-l90-rj", 544)
    )

    @Test
    fun handlesArtistGgphtUrls() = assertEquals(
        "https://yt3.ggpht.com/art=w400-h400-l90-rj",
        resizeThumbnailUrl("https://yt3.ggpht.com/art=s120-c-k-c0x00ffffff-no-rj", 400)
    )

    @Test
    fun addsSizeWhenMissing() = assertEquals(
        "https://lh3.googleusercontent.com/xyz=w300-h300-l90-rj",
        resizeThumbnailUrl("https://lh3.googleusercontent.com/xyz", 300)
    )

    @Test
    fun leavesYtimgUntouched() {
        val url = "https://i.ytimg.com/vi/hoNb6HuNmU0/hqdefault.jpg?sqp=abc"
        assertEquals(url, resizeThumbnailUrl(url, 1080))
    }
}
