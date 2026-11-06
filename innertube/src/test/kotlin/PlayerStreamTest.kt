import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.requests.player
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live network test: resolves real stream URLs and downloads the first
 * chunk exactly the way PlayerService's DefaultHttpDataSource does
 * (same User-Agent, 512 KiB Range request).
 */
class PlayerStreamTest {
    private val exoUserAgent = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"

    private val videoIds = listOf(
        "hoNb6HuNmU0", // Tum Hi Ho (song, music label)
        "kJQP7kiw5Fk", // Despacito (official music video)
        "fJ9rUzIMcZQ", // Bohemian Rhapsody
    )

    @Test
    fun resolvesAndStreamsBestAudio() = runBlocking {
        for (videoId in videoIds) {
            val started = System.currentTimeMillis()
            val response = Innertube.player(PlayerBody(videoId = videoId))!!.getOrThrow()
            val resolveMs = System.currentTimeMillis() - started

            assertEquals("OK", response.playabilityStatus?.status)
            assertEquals(videoId, response.videoDetails?.videoId)

            val formats = response.streamingData?.adaptiveFormats.orEmpty()
            println("[$videoId] resolved in ${resolveMs}ms, audio formats: " +
                formats.joinToString { "${it.itag}(${it.mimeType.substringAfter("codecs=")}, ${(it.bitrate ?: 0) / 1000}kbps)" })

            val best = response.streamingData?.highestQualityFormat
            assertNotNull("no playable format for $videoId", best)
            best!!

            val conn = URL(best.url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", exoUserAgent)
            conn.setRequestProperty("Range", "bytes=0-524287")
            conn.connectTimeout = 16000
            conn.readTimeout = 8000
            val code = conn.responseCode
            val bytes = if (code in 200..299) conn.inputStream.use { it.readBytes().size } else 0
            println("[$videoId] BEST itag=${best.itag} ${best.mimeType} ${(best.bitrate ?: 0) / 1000}kbps " +
                "sampleRate=${best.audioSampleRate} length=${best.contentLength} -> HTTP $code, $bytes bytes")

            assertTrue("stream fetch failed for $videoId: HTTP $code", code == 206 || code == 200)
            assertTrue("short read for $videoId", bytes > 100_000)
        }
    }
}
