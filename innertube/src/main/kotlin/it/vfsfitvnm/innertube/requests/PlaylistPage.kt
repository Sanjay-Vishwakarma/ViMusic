package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

/*
 * Album and playlist pages exist in two layouts:
 *  - old: header.musicDetailHeaderRenderer + singleColumnBrowseResultsRenderer
 *  - current (since 2024): twoColumnBrowseResultsRenderer, with the header
 *    (musicResponsiveHeaderRenderer) in tabs[0] and the songs in
 *    secondaryContents. Both are parsed; the current one wins.
 * Field-mask paths below were validated against the live API — an invalid
 * path makes YouTube reject the whole request with HTTP 400.
 */
suspend fun Innertube.playlistPage(body: BrowseBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        mask(
            "contents(" +
                "singleColumnBrowseResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents(musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask),musicCarouselShelfRenderer.contents.$musicTwoRowItemRendererMask)," +
                "twoColumnBrowseResultsRenderer(tabs.tabRenderer.content.sectionListRenderer.contents.musicResponsiveHeaderRenderer(title,subtitle,secondSubtitle,straplineTextOne,thumbnail),secondaryContents)" +
                "),header.musicDetailHeaderRenderer(title,subtitle,thumbnail),microformat"
        )
    }.body<BrowseResponse>()

    val twoColumn = response
        .contents
        ?.twoColumnBrowseResultsRenderer

    val responsiveHeader = twoColumn
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }

    val musicDetailHeaderRenderer = response
        .header
        ?.musicDetailHeaderRenderer

    val sectionListRendererContents = twoColumn
        ?.secondaryContents
        ?.sectionListRenderer
        ?.contents
        ?: response
            .contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents

    val musicShelfRenderer = sectionListRendererContents
        ?.firstNotNullOfOrNull { it.musicShelfRenderer }

    val musicCarouselShelfRenderer = sectionListRendererContents
        ?.firstNotNullOfOrNull { it.musicCarouselShelfRenderer }

    Innertube.PlaylistOrAlbumPage(
        title = responsiveHeader?.title?.text
            ?: musicDetailHeaderRenderer?.title?.text,
        thumbnail = (responsiveHeader?.thumbnail ?: musicDetailHeaderRenderer?.thumbnail)
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull(),
        // Current layout: artists are linked runs in straplineTextOne
        // ("Pritam & Amitabh Bhattacharya"). Old: "Album • <artists> • 2013".
        authors = responsiveHeader
            ?.straplineTextOne
            ?.runs
            ?.filter { it.navigationEndpoint != null }
            ?.takeIf { it.isNotEmpty() }
            ?.map(Innertube::Info)
            ?: musicDetailHeaderRenderer
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(1)
                ?.map(Innertube::Info),
        // Current subtitle: "Album • 2026"; old: "Album • <artists> • 2013".
        year = responsiveHeader
            ?.subtitle
            ?.splitBySeparator()
            ?.lastOrNull()
            ?.firstOrNull()
            ?.text
            ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            ?: musicDetailHeaderRenderer
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(2)
                ?.firstOrNull()
                ?.text,
        url = response
            .microformat
            ?.microformatDataRenderer
            ?.urlCanonical,
        songsPage = musicShelfRenderer
            ?.toSongsPage(),
        otherVersions = musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from)
    )
}

suspend fun Innertube.playlistPage(body: ContinuationBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        mask(
            "continuationContents.musicPlaylistShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask)," +
                "onResponseReceivedActions.appendContinuationItemsAction.continuationItems($musicResponsiveListItemRendererMask,continuationItemRenderer)"
        )
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toSongsPage()
        ?: response
            .onResponseReceivedActions
            ?.firstNotNullOfOrNull { it.appendContinuationItemsAction?.continuationItems }
            ?.let { items ->
                MusicShelfRenderer(
                    bottomEndpoint = null,
                    contents = items,
                    continuations = null,
                    title = null
                ).toSongsPage()
            }
}

private fun MusicShelfRenderer?.toSongsPage() =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        continuation = this
            ?.continuationToken
    )
