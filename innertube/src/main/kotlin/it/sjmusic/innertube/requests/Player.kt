package it.sjmusic.innertube.requests

import it.sjmusic.innertube.Innertube
import it.sjmusic.innertube.models.bodies.PlayerBody
import it.sjmusic.innertube.newpipe.NewPipeStreams
import it.sjmusic.innertube.utils.runCatchingNonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Same signature and return type as before, so PlayerService and
 * StatsForNerds are untouched. Stream URLs now come from NewPipeExtractor:
 * the ANDROID_MUSIC / TVHTML5 clients and the Piped fallback this used to
 * call have all been shut off by YouTube.
 */
suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    withContext(Dispatchers.IO) {
        NewPipeStreams.playerResponse(body.videoId)
    }
}
