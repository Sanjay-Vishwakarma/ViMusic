package it.vfsfitvnm.innertube.utils

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.Runs

/*
 * Endpoints are type-checked instead of cast. YouTube now links many playlist
 * rows' titles to a browse page (not a watch endpoint), and the old unchecked
 * cast threw ClassCastException, failing the whole playlist page. For those
 * rows the real videoId comes from playlistItemData.
 */
internal fun Runs.Run.watchInfo(fallbackVideoId: String?): Innertube.Info<NavigationEndpoint.Endpoint.Watch> =
    Innertube.Info(
        name = text,
        endpoint = navigationEndpoint?.endpoint as? NavigationEndpoint.Endpoint.Watch
            ?: fallbackVideoId?.let { NavigationEndpoint.Endpoint.Watch(videoId = it) }
    )

internal fun Runs.Run.browseInfo(): Innertube.Info<NavigationEndpoint.Endpoint.Browse> =
    Innertube.Info(
        name = text,
        endpoint = navigationEndpoint?.endpoint as? NavigationEndpoint.Endpoint.Browse
    )

fun Innertube.SongItem.Companion.from(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
    val fallbackVideoId = renderer.playlistItemData?.videoId

    return Innertube.SongItem(
        info = renderer
            .flexColumns
            .getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.watchInfo(fallbackVideoId),
        authors = renderer
            .flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.map { it.browseInfo() }
            ?.takeIf(List<Any>::isNotEmpty),
        durationText = renderer
            .fixedColumns
            ?.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.text,
        album = renderer
            .flexColumns
            .getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.browseInfo(),
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull()
    ).takeIf { it.info?.endpoint?.videoId != null }
}
