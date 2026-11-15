package it.sjmusic.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.sjmusic.innertube.Innertube
import it.sjmusic.innertube.models.ContinuationResponse
import it.sjmusic.innertube.models.MusicShelfRenderer
import it.sjmusic.innertube.models.SearchResponse
import it.sjmusic.innertube.models.bodies.ContinuationBody
import it.sjmusic.innertube.models.bodies.SearchBody
import it.sjmusic.innertube.utils.runCatchingNonCancellable

suspend fun <T : Innertube.Item> Innertube.searchPage(
    body: SearchBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?
) = runCatchingNonCancellable {
    val response = client.post(search) {
        setBody(body)
        mask("contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents.musicShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask)")
    }.body<SearchResponse>()

    response
        .contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.lastOrNull()
        ?.musicShelfRenderer
        ?.toItemsPage(fromMusicShelfRendererContent)
}

suspend fun <T : Innertube.Item> Innertube.searchPage(
    body: ContinuationBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?
) = runCatchingNonCancellable {
    val response = client.post(search) {
        setBody(body)
        mask("continuationContents.musicShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask)")
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toItemsPage(fromMusicShelfRendererContent)
}

private fun <T : Innertube.Item> MusicShelfRenderer?.toItemsPage(mapper: (MusicShelfRenderer.Content) -> T?) =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(mapper),
        continuation = this
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    )
