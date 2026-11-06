package it.vfsfitvnm.vimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached resolution of one curated Explore entry (album or artist) to a real
 * YouTube Music item. `browseId == null` records "searched, nothing matched",
 * so the search is not repeated on every launch.
 */
@Immutable
@Entity(indices = [Index(value = ["category", "kind"])])
data class CatalogItem(
    /** "<category>/<kind>/<curated name>" */
    @PrimaryKey val key: String,
    val category: String,
    /** [KIND_ALBUM] or [KIND_ARTIST] */
    val kind: String,
    /** Curated order within the category. */
    val position: Int,
    val browseId: String?,
    val title: String?,
    /** Album: artists. Artist: audience text ("655M monthly audience"). */
    val subtitle: String?,
    val year: String?,
    val thumbnailUrl: String?,
    val resolvedAt: Long,
) {
    companion object {
        const val KIND_ALBUM = "album"
        const val KIND_ARTIST = "artist"
    }
}
