package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicShelfRenderer(
    val bottomEndpoint: NavigationEndpoint?,
    val contents: List<Content>?,
    val continuations: List<Continuation>?,
    val title: Runs?
) {
    // Old style (continuations[].nextContinuationData) or new style
    // (a trailing continuationItemRenderer).
    val continuationToken: String?
        get() = continuations?.firstOrNull()?.nextContinuationData?.continuation
            ?: contents?.lastOrNull()?.continuationItemRenderer
                ?.continuationEndpoint?.continuationCommand?.token

    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
        // Newer paging: the last item carries the next-page token
        val continuationItemRenderer: ContinuationItemRenderer?,
    ) {
        @Serializable
        data class ContinuationItemRenderer(
            val continuationEndpoint: ContinuationEndpoint?,
        ) {
            @Serializable
            data class ContinuationEndpoint(
                val continuationCommand: ContinuationCommand?,
            ) {
                @Serializable
                data class ContinuationCommand(
                    val token: String?,
                )
            }
        }

        val runs: Pair<List<Runs.Run>, List<List<Runs.Run>>>
            get() = (musicResponsiveListItemRenderer
                ?.flexColumns
                ?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?: emptyList()) to
                    (musicResponsiveListItemRenderer
                        ?.flexColumns
                        // Second column = "artists • album • duration". Song
                        // results now carry a third "442M plays" column, so
                        // taking the LAST column put play counts into the
                        // duration and lost the artists.
                        ?.let { it.getOrNull(1) ?: it.lastOrNull() }
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.splitBySeparator()
                        ?: emptyList()
                            )

        val thumbnail: Thumbnail?
            get() = musicResponsiveListItemRenderer
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.firstOrNull()
    }
}
