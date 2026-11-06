import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.requests.albumPage
import it.vfsfitvnm.innertube.requests.artistPage
import it.vfsfitvnm.innertube.requests.lyrics
import it.vfsfitvnm.innertube.requests.playlistPage
import it.vfsfitvnm.innertube.requests.relatedPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live network test of everything the Quick picks screen shows, plus the
 * pages its items open. Uses the same default seed the app falls back to
 * on a fresh install (J7p4bzqLvCw) and a played song.
 */
class QuickPicksTest {
    @Test
    fun quickPicksSectionsAreFilled() = runBlocking {
        for (seed in listOf("J7p4bzqLvCw", "hoNb6HuNmU0")) {
            val related = Innertube.relatedPage(NextBody(videoId = seed))!!.getOrThrow()
            println("[$seed] songs=${related?.songs?.size} playlists=${related?.playlists?.size} " +
                "albums=${related?.albums?.size} artists=${related?.artists?.size}")
            println("   first song: ${related?.songs?.firstOrNull()?.info?.name} - " +
                related?.songs?.firstOrNull()?.authors?.joinToString { it.name.orEmpty() })
            assertTrue("no songs for $seed", !related?.songs.isNullOrEmpty())
            assertTrue("no playlists for $seed", !related?.playlists.isNullOrEmpty())
            assertTrue("no albums for $seed", !related?.albums.isNullOrEmpty())
            assertTrue("no artists for $seed", !related?.artists.isNullOrEmpty())
        }
    }

    @Test
    fun pagesOpenedFromQuickPicksLoad() = runBlocking {
        val related = Innertube.relatedPage(NextBody(videoId = "hoNb6HuNmU0"))!!.getOrThrow()!!

        val albumId = related.albums!!.first().key
        val album = Innertube.albumPage(BrowseBody(browseId = albumId))?.getOrNull()
        println("album $albumId -> title=${album?.title} songs=${album?.songsPage?.items?.size}")

        val playlistId = related.playlists!!.first().key
        val playlist = Innertube.playlistPage(BrowseBody(browseId = playlistId))?.getOrNull()
        println("playlist $playlistId -> title=${playlist?.title} songs=${playlist?.songsPage?.items?.size}")

        val artistId = related.artists!!.first().key
        val artist = Innertube.artistPage(BrowseBody(browseId = artistId))?.getOrNull()
        println("artist $artistId -> name=${artist?.name} songs=${artist?.songs?.size}")

        val lyrics = Innertube.lyrics(NextBody(videoId = "hoNb6HuNmU0"))?.getOrNull()
        println("lyrics -> ${lyrics?.length} chars")

        println("album authors=${album?.authors?.map { it.name }} year=${album?.year} thumb=${album?.thumbnail?.url?.take(45)}")
        assertTrue("album title missing", !album?.title.isNullOrBlank())
        assertTrue("album songs missing", !album?.songsPage?.items.isNullOrEmpty())
        assertTrue("album authors missing", !album?.authors.isNullOrEmpty())
        assertTrue("playlist title missing", !playlist?.title.isNullOrBlank())
        assertTrue("playlist songs missing", !playlist?.songsPage?.items.isNullOrEmpty())
        assertTrue("artist songs missing", !artist?.songs.isNullOrEmpty())
        assertTrue("artist albums/singles missing", !(artist?.albums.isNullOrEmpty() && artist?.singles.isNullOrEmpty()))
        assertTrue("lyrics missing", !lyrics.isNullOrBlank())
    }

    @Test
    fun largePlaylistLoadsMore() = runBlocking {
        val first = Innertube.playlistPage(BrowseBody(browseId = "VLPLUOEf-vLOCSnKxLaqvfnziciUcPjRkj12"))!!.getOrThrow()
        val token = first.songsPage?.continuation
        println("large playlist first page=${first.songsPage?.items?.size} hasMore=${token != null}")
        assertTrue("no continuation token", token != null)
        val next = Innertube.playlistPage(
            it.vfsfitvnm.innertube.models.bodies.ContinuationBody(continuation = token!!)
        )!!.getOrThrow()
        println("large playlist second page=${next?.items?.size}")
        assertTrue("second page empty", !next?.items.isNullOrEmpty())
    }
}
