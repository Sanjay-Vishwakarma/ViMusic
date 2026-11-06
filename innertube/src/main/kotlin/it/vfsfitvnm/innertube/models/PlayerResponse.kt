package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String?
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            private val loudnessDb: Double?
        ) {
            // For music clients only
            val normalizedLoudnessDb: Float?
                get() = loudnessDb?.plus(7)?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat>?
    ) {
        // Best audio first: Opus (itag 251/250/249, or 774 for Premium) beats
        // AAC (140/139) at the same bitrate, then the highest bitrate wins.
        // Only formats the app's extractors can play (WebM/Matroska, MP4).
        val highestQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats
                ?.filter { it.url != null && (it.mimeType.startsWith("audio/webm") || it.mimeType.startsWith("audio/mp4")) }
                ?.maxWithOrNull(
                    compareBy<AdaptiveFormat>(
                        { if (it.mimeType.contains("opus")) 1 else 0 },
                        { it.bitrate ?: it.averageBitrate ?: 0L }
                    )
                )

        @Serializable
        data class AdaptiveFormat(
            val itag: Int,
            val mimeType: String,
            val bitrate: Long?,
            val averageBitrate: Long?,
            val contentLength: Long?,
            val audioQuality: String?,
            val approxDurationMs: Long?,
            val lastModified: Long?,
            val loudnessDb: Double?,
            val audioSampleRate: Int?,
            val url: String?,
        )
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?
    )
}
