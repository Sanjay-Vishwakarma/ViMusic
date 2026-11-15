package it.sjmusic.innertube.models.bodies

import it.sjmusic.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class QueueBody(
    val context: Context = Context.DefaultWeb,
    val videoIds: List<String>? = null,
    val playlistId: String? = null,
)
