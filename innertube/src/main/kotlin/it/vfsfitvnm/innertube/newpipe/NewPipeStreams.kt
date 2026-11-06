package it.vfsfitvnm.innertube.newpipe

import it.vfsfitvnm.innertube.models.PlayerResponse
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Resolves playable audio stream URLs through NewPipeExtractor.
 *
 * YouTube no longer serves streams to the old ANDROID_MUSIC 5.28.1 /
 * TVHTML5_SIMPLY_EMBEDDED_PLAYER clients the app used, and the Piped fallback
 * host is gone. NewPipeExtractor is actively maintained and handles client
 * selection, signature deciphering and throttling-parameter decoding.
 *
 * The result is mapped onto the existing [PlayerResponse] model so every
 * caller (PlayerService, StatsForNerds) keeps working unchanged.
 */
internal object NewPipeStreams {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    @Volatile
    private var initialized = false

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(OkHttpDownloader)
                initialized = true
            }
        }
    }

    fun playerResponse(videoId: String): PlayerResponse {
        ensureInitialized()

        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: AgeRestrictedContentException) {
            return statusOnly(videoId, "LOGIN_REQUIRED")
        } catch (e: ContentNotAvailableException) {
            return statusOnly(videoId, "UNPLAYABLE")
        }

        val durationMs = info.duration.takeIf { it > 0 }?.times(1000)

        val formats = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            // Skip dubbed / descriptive tracks: keep the original audio.
            .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .map { it.toAdaptiveFormat(durationMs) }

        return PlayerResponse(
            playabilityStatus = PlayerResponse.PlayabilityStatus(
                status = if (formats.isEmpty()) "UNPLAYABLE" else "OK"
            ),
            playerConfig = null,
            streamingData = PlayerResponse.StreamingData(adaptiveFormats = formats),
            videoDetails = PlayerResponse.VideoDetails(videoId = videoId)
        )
    }

    private fun AudioStream.toAdaptiveFormat(durationMs: Long?): PlayerResponse.StreamingData.AdaptiveFormat {
        val itagItem = itagItem
        val avgBitrate = averageBitrate.takeIf { it > 0 }?.times(1000L)
        val mime = buildString {
            append(format?.mimeType ?: "audio/webm")
            codec?.takeIf { it.isNotBlank() }?.let { append("; codecs=\"").append(it).append('"') }
        }

        return PlayerResponse.StreamingData.AdaptiveFormat(
            itag = itag,
            mimeType = mime,
            bitrate = itagItem?.bitrate?.takeIf { it > 0 }?.toLong() ?: bitrate.takeIf { it > 0 }?.toLong() ?: avgBitrate,
            averageBitrate = itagItem?.averageBitrate?.takeIf { it > 0 }?.toLong() ?: avgBitrate,
            contentLength = itagItem?.contentLength?.takeIf { it > 0 },
            audioQuality = null,
            approxDurationMs = itagItem?.approxDurationMs?.takeIf { it > 0 } ?: durationMs,
            lastModified = null,
            loudnessDb = null,
            audioSampleRate = itagItem?.sampleRate?.takeIf { it > 0 },
            url = content
        )
    }

    private fun statusOnly(videoId: String, status: String) = PlayerResponse(
        playabilityStatus = PlayerResponse.PlayabilityStatus(status = status),
        playerConfig = null,
        streamingData = null,
        videoDetails = PlayerResponse.VideoDetails(videoId = videoId)
    )

    private object OkHttpDownloader : Downloader() {
        private val client = OkHttpClient.Builder()
            .connectTimeout(16, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        override fun execute(request: Request): Response {
            val method = request.httpMethod()
            val body = request.dataToSend()?.toRequestBody()
                ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody() else null

            val builder = okhttp3.Request.Builder()
                .url(request.url())
                .method(method, body)
                .header("User-Agent", USER_AGENT)

            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }

            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha challenge requested", request.url())
                }
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString()
                )
            }
        }
    }
}
