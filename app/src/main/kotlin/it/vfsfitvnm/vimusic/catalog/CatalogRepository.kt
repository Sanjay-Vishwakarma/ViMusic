package it.vfsfitvnm.vimusic.catalog

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.catalog.CatalogCategory
import it.vfsfitvnm.innertube.catalog.CatalogResolver
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.models.CatalogItem
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Explore data: curated entries resolved to real YouTube Music items.
 *
 * Room is the source of truth for albums/artists — the UI observes
 * [albums]/[artists] and rows appear as they resolve. [refresh] only
 * searches for entries that are missing or stale, so a normal launch makes
 * no network requests at all, and cached data still shows offline.
 */
object CatalogRepository {
    private const val FOUND_TTL_MS = 30L * 24 * 60 * 60 * 1000      // re-check matches monthly
    private const val NOT_FOUND_TTL_MS = 3L * 24 * 60 * 60 * 1000   // retry misses after 3 days
    private const val SONGS_TTL_MS = 6L * 60 * 60 * 1000            // popular songs: 6 hours

    private val refreshLocks = ConcurrentHashMap<String, Mutex>()
    private val searchPermits = Semaphore(3) // be gentle: at most 3 searches at once

    private data class CachedSongs(val songs: List<Innertube.SongItem>, val at: Long)
    private val songsCache = ConcurrentHashMap<String, CachedSongs>()

    fun albums(category: CatalogCategory): Flow<List<CatalogItem>> =
        Database.catalogItems(category.id, CatalogItem.KIND_ALBUM)

    fun artists(category: CatalogCategory): Flow<List<CatalogItem>> =
        Database.catalogItems(category.id, CatalogItem.KIND_ARTIST)

    /**
     * Resolves missing/stale entries. Returns false if any lookup failed on
     * the network (cached rows are kept; nothing is overwritten with "not found").
     */
    suspend fun refresh(category: CatalogCategory, force: Boolean = false): Boolean =
        refreshLocks.getOrPut(category.id) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                val cached = Database.catalogItemsNow(category.id).associateBy { it.key }
                val now = System.currentTimeMillis()

                fun isFresh(item: CatalogItem?) = item != null && !force &&
                    now - item.resolvedAt < if (item.browseId != null) FOUND_TTL_MS else NOT_FOUND_TTL_MS

                coroutineScope {
                    val albumJobs = category.albums.mapIndexedNotNull { index, entry ->
                        val key = key(category, CatalogItem.KIND_ALBUM, entry.title)
                        if (isFresh(cached[key])) return@mapIndexedNotNull null
                        async {
                            val result = searchPermits.withPermit { CatalogResolver.resolveAlbum(entry) }
                                ?: return@async false
                            val album = result.getOrNull()
                            Database.upsert(
                                CatalogItem(
                                    key = key,
                                    category = category.id,
                                    kind = CatalogItem.KIND_ALBUM,
                                    position = index,
                                    browseId = album?.key,
                                    title = album?.info?.name,
                                    subtitle = album?.authors
                                        ?.mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotEmpty() && n != "&" && n != "," } }
                                        ?.joinToString(", "),
                                    year = album?.year,
                                    thumbnailUrl = album?.thumbnail?.url,
                                    resolvedAt = now
                                )
                            )
                            true
                        }
                    }

                    val artistJobs = category.artists.mapIndexedNotNull { index, entry ->
                        val key = key(category, CatalogItem.KIND_ARTIST, entry.name)
                        if (isFresh(cached[key])) return@mapIndexedNotNull null
                        async {
                            val result = searchPermits.withPermit { CatalogResolver.resolveArtist(entry) }
                                ?: return@async false
                            val artist = result.getOrNull()
                            Database.upsert(
                                CatalogItem(
                                    key = key,
                                    category = category.id,
                                    kind = CatalogItem.KIND_ARTIST,
                                    position = index,
                                    browseId = artist?.key,
                                    title = artist?.info?.name,
                                    subtitle = artist?.subscribersCountText,
                                    year = null,
                                    thumbnailUrl = artist?.thumbnail?.url,
                                    resolvedAt = now
                                )
                            )
                            true
                        }
                    }

                    (albumJobs + artistJobs).awaitAll().all { it }
                }
            }
        }

    /** Popular songs for a category, cached in memory for a few hours. */
    suspend fun popularSongs(category: CatalogCategory, force: Boolean = false): Result<List<Innertube.SongItem>>? {
        val now = System.currentTimeMillis()
        songsCache[category.id]?.takeIf { !force && now - it.at < SONGS_TTL_MS }?.let {
            return Result.success(it.songs)
        }
        val result = withContext(Dispatchers.IO) { CatalogResolver.popularSongs(category) } ?: return null
        result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { songsCache[category.id] = CachedSongs(it, now) }
        // On failure fall back to whatever we had, however old.
        return if (result.isFailure) songsCache[category.id]?.let { Result.success(it.songs) } ?: result else result
    }

    private fun key(category: CatalogCategory, kind: String, name: String) =
        "${category.id}/$kind/${name.lowercase()}"
}
