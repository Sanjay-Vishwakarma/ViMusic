import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.catalog.CatalogResolver
import it.vfsfitvnm.innertube.catalog.IndianMusicCatalog
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.models.bodies.SearchSuggestionsBody
import it.vfsfitvnm.innertube.requests.searchSuggestions
import it.vfsfitvnm.innertube.requests.albumPage
import it.vfsfitvnm.innertube.requests.artistPage
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live network test of what the Explore screens open: every resolved album
 * must load its tracks (album screen) and every resolved artist must load
 * popular songs + albums (artist screen). Also guards the existing search.
 */
class ExploreNavigationTest {
    @Test
    fun everyResolvedAlbumAndArtistOpens() = runBlocking {
        val failures = mutableListOf<String>()
        for (category in IndianMusicCatalog.categories) {
            for (entry in category.albums) {
                val album = CatalogResolver.resolveAlbum(entry)?.getOrNull() ?: continue
                val page = Innertube.albumPage(BrowseBody(browseId = album.key))?.getOrNull()
                val tracks = page?.songsPage?.items?.size ?: 0
                println("[${category.title}] album ${entry.title}: tracks=$tracks title=${page?.title} year=${page?.year}")
                if (tracks == 0 || page?.title.isNullOrBlank()) failures += "album ${entry.title}"
            }
            for (entry in category.artists) {
                val artist = CatalogResolver.resolveArtist(entry)?.getOrNull() ?: continue
                val page = Innertube.artistPage(BrowseBody(browseId = artist.key))?.getOrNull()
                val songs = page?.songs?.size ?: 0
                val albums = (page?.albums?.size ?: 0) + (page?.singles?.size ?: 0)
                println("[${category.title}] artist ${entry.name}: songs=$songs albums+singles=$albums thumb=${page?.thumbnail != null}")
                if (songs == 0) failures += "artist ${entry.name}"
            }
        }
        println("\nFAILURES: $failures")
        assertTrue("pages that did not load: $failures", failures.isEmpty())
    }

    @Test
    fun searchSuggestionsWork() = runBlocking {
        for (input in listOf("arijit", "tum hi", "bhojpuri")) {
            val suggestions = Innertube.searchSuggestions(SearchSuggestionsBody(input = input))!!.getOrThrow().orEmpty()
            println("suggestions for '$input': ${suggestions.take(5)}")
            assertTrue("no suggestions for $input", suggestions.isNotEmpty())
        }
    }

    @Test
    fun existingSearchStillWorks() = runBlocking {
        val q = "arijit singh"
        val songs = Innertube.searchPage(SearchBody(query = q, params = Innertube.SearchFilter.Song.value), Innertube.SongItem.Companion::from)!!.getOrThrow()?.items.orEmpty()
        val videos = Innertube.searchPage(SearchBody(query = q, params = Innertube.SearchFilter.Video.value), Innertube.VideoItem.Companion::from)!!.getOrThrow()?.items.orEmpty()
        val albums = Innertube.searchPage(SearchBody(query = q, params = Innertube.SearchFilter.Album.value), Innertube.AlbumItem.Companion::from)!!.getOrThrow()?.items.orEmpty()
        val artists = Innertube.searchPage(SearchBody(query = q, params = Innertube.SearchFilter.Artist.value), Innertube.ArtistItem.Companion::from)!!.getOrThrow()?.items.orEmpty()
        val playlists = Innertube.searchPage(SearchBody(query = q, params = Innertube.SearchFilter.CommunityPlaylist.value), Innertube.PlaylistItem.Companion::from)!!.getOrThrow()?.items.orEmpty()

        println("search songs=${songs.size} videos=${videos.size} albums=${albums.size} artists=${artists.size} playlists=${playlists.size}")
        songs.take(3).forEach { println("  song: ${it.info?.name} | ${it.authors?.joinToString("") { a -> a.name.orEmpty() }} | ${it.durationText} | album=${it.album?.name}") }
        videos.take(2).forEach { println("  video: ${it.info?.name} | ${it.authors?.joinToString("") { a -> a.name.orEmpty() }} | ${it.viewsText} | ${it.durationText}") }

        assertTrue(songs.isNotEmpty() && videos.isNotEmpty() && albums.isNotEmpty() && artists.isNotEmpty() && playlists.isNotEmpty())
        assertTrue("song durations contain play counts", songs.none { it.durationText?.contains("plays") == true })
        assertTrue("song authors missing", songs.all { !it.authors.isNullOrEmpty() })
    }
}
