package it.sjmusic.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.sjmusic.innertube.Innertube
import it.sjmusic.innertube.models.SearchSuggestionsResponse
import it.sjmusic.innertube.models.bodies.SearchSuggestionsBody
import it.sjmusic.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.searchSuggestions(body: SearchSuggestionsBody) = runCatchingNonCancellable {
    val response = client.post(searchSuggestions) {
        setBody(body)
        mask("contents.searchSuggestionsSectionRenderer.contents.searchSuggestionRenderer.navigationEndpoint.searchEndpoint.query")
    }.body<SearchSuggestionsResponse>()

    response
        .contents
        ?.firstOrNull()
        ?.searchSuggestionsSectionRenderer
        ?.contents
        ?.mapNotNull { content ->
            content
                .searchSuggestionRenderer
                ?.navigationEndpoint
                ?.searchEndpoint
                ?.query
        }
}
