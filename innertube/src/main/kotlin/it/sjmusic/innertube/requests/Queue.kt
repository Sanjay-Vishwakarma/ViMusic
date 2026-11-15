package it.sjmusic.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.sjmusic.innertube.Innertube
import it.sjmusic.innertube.models.GetQueueResponse
import it.sjmusic.innertube.models.bodies.QueueBody
import it.sjmusic.innertube.utils.from
import it.sjmusic.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.queue(body: QueueBody) = runCatchingNonCancellable {
    val response = client.post(queue) {
        setBody(body)
        mask("queueDatas.content.$playlistPanelVideoRendererMask")
    }.body<GetQueueResponse>()

    response
        .queueDatas
        ?.mapNotNull { queueData ->
            queueData
                .content
                ?.playlistPanelVideoRenderer
                ?.let(Innertube.SongItem::from)
        }
}

suspend fun Innertube.song(videoId: String): Result<Innertube.SongItem?>? =
    queue(QueueBody(videoIds = listOf(videoId)))?.map { it?.firstOrNull() }
