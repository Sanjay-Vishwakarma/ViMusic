package it.sjmusic.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.sjmusic.innertube.Innertube
import it.sjmusic.innertube.models.BrowseResponse
import it.sjmusic.innertube.models.ContinuationResponse
import it.sjmusic.innertube.models.GridRenderer
import it.sjmusic.innertube.models.MusicResponsiveListItemRenderer
import it.sjmusic.innertube.models.MusicShelfRenderer
import it.sjmusic.innertube.models.MusicTwoRowItemRenderer
import it.sjmusic.innertube.models.bodies.BrowseBody
import it.sjmusic.innertube.models.bodies.ContinuationBody
import it.sjmusic.innertube.utils.runCatchingNonCancellable

suspend fun <T : Innertube.Item> Innertube.itemsPage(
    body: BrowseBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null },
) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
//        mask("contents.singleColumnBrowseResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents(musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask),gridRenderer(continuations,items.$musicTwoRowItemRendererMask))")
    }.body<BrowseResponse>()

    val sectionListRendererContent = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()

    itemsPageFromMusicShelRendererOrGridRenderer(
        musicShelfRenderer = sectionListRendererContent
            ?.musicShelfRenderer,
        gridRenderer = sectionListRendererContent
            ?.gridRenderer,
        fromMusicResponsiveListItemRenderer = fromMusicResponsiveListItemRenderer,
        fromMusicTwoRowItemRenderer = fromMusicTwoRowItemRenderer,
    )
}

suspend fun <T : Innertube.Item> Innertube.itemsPage(
    body: ContinuationBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null },
) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
//        mask("contents.singleColumnBrowseResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents(musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask),gridRenderer(continuations,items.$musicTwoRowItemRendererMask))")
    }.body<ContinuationResponse>()

    itemsPageFromMusicShelRendererOrGridRenderer(
        musicShelfRenderer = response
            .continuationContents
            ?.musicShelfContinuation,
        gridRenderer = null,
        fromMusicResponsiveListItemRenderer = fromMusicResponsiveListItemRenderer,
        fromMusicTwoRowItemRenderer = fromMusicTwoRowItemRenderer,
    )
}

private fun <T : Innertube.Item> itemsPageFromMusicShelRendererOrGridRenderer(
    musicShelfRenderer: MusicShelfRenderer?,
    gridRenderer: GridRenderer?,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T?,
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T?,
): Innertube.ItemsPage<T>? {
    return if (musicShelfRenderer != null) {
        Innertube.ItemsPage(
            continuation = musicShelfRenderer
                .continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation,
            items = musicShelfRenderer
                .contents
                ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                ?.mapNotNull(fromMusicResponsiveListItemRenderer)
        )
    } else if (gridRenderer != null) {
        Innertube.ItemsPage(
            continuation = null,
            items = gridRenderer
                .items
                ?.mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                ?.mapNotNull(fromMusicTwoRowItemRenderer)
        )
    } else {
        null
    }
}
