package it.sjmusic.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.sjmusic.innertube.Innertube
import it.sjmusic.innertube.models.BrowseResponse
import it.sjmusic.innertube.models.MusicCarouselShelfRenderer
import it.sjmusic.innertube.models.NextResponse
import it.sjmusic.innertube.models.bodies.BrowseBody
import it.sjmusic.innertube.models.bodies.NextBody
import it.sjmusic.innertube.utils.findSectionByStrapline
import it.sjmusic.innertube.utils.findSectionByTitle
import it.sjmusic.innertube.utils.from
import it.sjmusic.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.relatedPage(body: NextBody) = runCatchingNonCancellable {
    val nextResponse = client.post(next) {
        setBody(body)
        mask("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)")
    }.body<NextResponse>()

    val browseId = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        // YouTube inserted a "Comments" tab, pushing "Related" from index 2
        // to 3. Find it by its browseId prefix (MPTR…) instead of position.
        ?.map { it.tabRenderer?.endpoint?.browseEndpoint?.browseId }
        ?.let { ids -> ids.firstOrNull { it?.startsWith("MPTR") == true } ?: ids.getOrNull(2) }
        ?: return@runCatchingNonCancellable null

    val response = client.post(browse) {
        setBody(BrowseBody(browseId = browseId))
        mask("contents.sectionListRenderer.contents.musicCarouselShelfRenderer(header.musicCarouselShelfBasicHeaderRenderer(title,strapline),contents($musicResponsiveListItemRendererMask,$musicTwoRowItemRendererMask))")
    }.body<BrowseResponse>()

    val sectionListRenderer = response
        .contents
        ?.sectionListRenderer

    Innertube.RelatedPage(
        songs = sectionListRenderer
            ?.findSectionByTitle("You might also like")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        playlists = sectionListRenderer
            ?.findSectionByTitle("Recommended playlists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.PlaylistItem::from)
            ?.sortedByDescending { it.channel?.name == "YouTube Music" },
        albums = sectionListRenderer
            ?.findSectionByStrapline("MORE FROM")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from),
        artists = sectionListRenderer
            ?.findSectionByTitle("Similar artists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.ArtistItem::from),
    )
}
