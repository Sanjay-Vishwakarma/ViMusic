import it.vfsfitvnm.innertube.catalog.CatalogResolver
import it.vfsfitvnm.innertube.catalog.IndianMusicCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live network test: resolves every curated entry against YouTube Music and
 * prints what it matched, so wrong or missing matches are visible.
 */
class CatalogResolverTest {
    @Test
    fun resolvesWholeCatalog() = runBlocking {
        var albumsOk = 0
        var albumsTotal = 0
        var artistsOk = 0
        var artistsTotal = 0

        for (category in IndianMusicCatalog.categories) {
            println("\n===== ${category.title} =====")

            val songs = CatalogResolver.popularSongs(category)?.getOrNull().orEmpty()
            println("popular songs: ${songs.size} -> " +
                songs.take(5).joinToString(" | ") { "${it.info?.name} (${it.authors?.firstOrNull()?.name})" })
            assertTrue("no songs for ${category.title}", songs.isNotEmpty())

            for (entry in category.albums) {
                albumsTotal++
                val album = CatalogResolver.resolveAlbum(entry)?.getOrNull()
                if (album != null) albumsOk++
                println("ALBUM  %-28s -> %s".format(entry.title,
                    album?.let { "${it.info?.name} | ${it.authors?.joinToString { a -> a.name.orEmpty() }} | ${it.year} | ${it.key} | score=${CatalogResolver.scoreAlbum(entry, it)}" }
                        ?: "NOT FOUND"))
            }

            for (entry in category.artists) {
                artistsTotal++
                val artist = CatalogResolver.resolveArtist(entry)?.getOrNull()
                if (artist != null) artistsOk++
                println("ARTIST %-28s -> %s".format(entry.name,
                    artist?.let { "${it.info?.name} | ${it.subscribersCountText} | ${it.key} | thumb=${it.thumbnail?.url != null}" }
                        ?: "NOT FOUND"))
            }
        }
        println("\nalbums resolved $albumsOk/$albumsTotal, artists resolved $artistsOk/$artistsTotal")
    }
}
