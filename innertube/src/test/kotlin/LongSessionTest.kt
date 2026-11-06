import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.requests.player
import it.vfsfitvnm.innertube.requests.relatedPage
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Simulates a long listening session: many songs back to back, each streamed
 * start-to-end in 512 KiB Range requests exactly like PlayerService's
 * DefaultHttpDataSource. Prints every failure (resolve error, HTTP status,
 * byte offset) instead of stopping at the first, to find what pauses playback.
 */
class LongSessionTest {
    private val exoUserAgent = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
    private val chunk = 512 * 1024L

    @Test
    fun manySongsStreamedFully() = runBlocking {
        val songs = Innertube.relatedPage(NextBody(videoId = "hoNb6HuNmU0"))!!.getOrThrow()!!
            .songs.orEmpty().take(12).map { it.key }
        println("session of ${songs.size} songs")

        var failures = 0
        songs.forEachIndexed { n, videoId ->
            val t0 = System.currentTimeMillis()
            val response = Innertube.player(PlayerBody(videoId = videoId))?.getOrNull()
            val status = response?.playabilityStatus?.status
            val format = response?.streamingData?.highestQualityFormat
            if (status != "OK" || format?.url == null) {
                failures++
                println("#$n $videoId RESOLVE FAILED status=$status")
                return@forEachIndexed
            }
            val resolveMs = System.currentTimeMillis() - t0

            val length = format.contentLength ?: Long.MAX_VALUE
            var offset = 0L
            var requests = 0
            var error: String? = null
            while (offset < length) {
                val conn = URL(format.url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", exoUserAgent)
                conn.setRequestProperty("Range", "bytes=$offset-${offset + chunk - 1}")
                conn.connectTimeout = 16000
                conn.readTimeout = 8000
                val code = try { conn.responseCode } catch (e: Exception) { -1 }
                if (code != 206 && code != 200) { error = "HTTP $code at offset $offset"; break }
                val read = try { conn.inputStream.use { it.readBytes().size } } catch (e: Exception) { error = "read ${e.javaClass.simpleName} at $offset"; break }
                requests++
                if (read == 0) break
                offset += read
                if (length == Long.MAX_VALUE && read < chunk) break
            }
            if (error != null) failures++
            println("#$n $videoId itag=${format.itag} resolve=${resolveMs}ms bytes=$offset/${format.contentLength} requests=$requests ${error ?: "OK"}")
        }
        println("\nFAILURES: $failures / ${songs.size}")
    }
}
