package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.catalog.IndianMusicCatalog
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.catalog.CatalogRepository
import it.vfsfitvnm.vimusic.models.CatalogItem
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.components.themed.TextPlaceholder
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.color
import it.vfsfitvnm.vimusic.utils.exploreCategoryKey
import it.vfsfitvnm.vimusic.utils.forcePlayAtIndex
import it.vfsfitvnm.vimusic.utils.forcePlayFromBeginning
import it.vfsfitvnm.vimusic.utils.isLandscape
import it.vfsfitvnm.vimusic.utils.rememberPreference
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.thumbnail

/**
 * Explore: curated Indian (and English) music by category. Albums and
 * artists open the existing album/artist screens; songs play through the
 * existing PlayerService. All metadata is fetched from YouTube Music via
 * [CatalogRepository] and cached in Room.
 */
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun Explore(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var categoryId by rememberPreference(exploreCategoryKey, IndianMusicCatalog.hindi.id)
    val category = IndianMusicCatalog.category(categoryId)

    // Bumped by "Retry"; restarts the loaders below.
    var reloadKey by remember { mutableStateOf(0) }

    val albums by remember(category.id) { CatalogRepository.albums(category) }
        .collectAsState(initial = null)
    val artists by remember(category.id) { CatalogRepository.artists(category) }
        .collectAsState(initial = null)

    // null = loading, true = up to date, false = a lookup failed (offline?)
    var refreshOk by remember(category.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(category.id, reloadKey) {
        refreshOk = null
        refreshOk = runCatching { CatalogRepository.refresh(category) }.getOrDefault(false)
    }

    var songsResult by remember(category.id) { mutableStateOf<Result<List<Innertube.SongItem>>?>(null) }
    LaunchedEffect(category.id, reloadKey) {
        if (songsResult?.isSuccess != true) songsResult = null
        songsResult = CatalogRepository.popularSongs(category) ?: songsResult
    }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px
    val artistThumbnailSizeDp = 92.dp
    val artistThumbnailSizePx = artistThumbnailSizeDp.px

    val scrollState = rememberScrollState()
    val songsGridState = rememberLazyGridState()
    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    // Same behaviour as the album screen: the whole list is queued, starting
    // at the tapped song; "Play all" starts from the top.
    fun playSongs(songs: List<Innertube.SongItem>, index: Int = 0) {
        val mediaItems = songs.map(Innertube.SongItem::asMediaItem)
        binder?.stopRadio()
        if (index <= 0) binder?.player?.forcePlayFromBeginning(mediaItems)
        else binder?.player?.forcePlayAtIndex(mediaItems, index)
    }

    BoxWithConstraints {
        val gridItemWidth = maxWidth * if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f

        Column(
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(windowInsets.only(WindowInsetsSides.Vertical).asPaddingValues())
        ) {
            Header(title = "Explore", modifier = Modifier.padding(endPaddingValues))

            // ---- Category chips ------------------------------------------
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(endPaddingValues)
            ) {
                items(items = IndianMusicCatalog.categories, key = { it.id }) { item ->
                    val selected = item.id == category.id
                    BasicText(
                        text = item.title,
                        style = typography.xs.semiBold.color(
                            if (selected) colorPalette.onAccent else colorPalette.text
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) colorPalette.accent else colorPalette.background1)
                            .clickable { categoryId = item.id }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            val nothingCached = albums.isNullOrEmpty() && artists.isNullOrEmpty()

            if (nothingCached && refreshOk == false && songsResult?.isSuccess != true) {
                // Offline (or YouTube unreachable) and nothing cached yet.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 32.dp)
                ) {
                    BasicText(
                        text = "Couldn't load ${category.title} music. Check your connection.",
                        style = typography.s.secondary.center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryTextButton(text = "Retry", onClick = { reloadKey++ })
                }
            }

            // ---- Popular songs ---------------------------------------------
            val songs = songsResult?.getOrNull().orEmpty()
            if (songs.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(endPaddingValues)
                ) {
                    BasicText(
                        text = "Popular songs",
                        style = typography.m.semiBold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 8.dp)
                            .weight(1f)
                    )
                    SecondaryTextButton(
                        text = "Play all",
                        onClick = { playSongs(songs) },
                        modifier = Modifier.padding(end = 16.dp, top = 16.dp)
                    )
                }

                LazyHorizontalGrid(
                    state = songsGridState,
                    rows = GridCells.Fixed(4),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((songThumbnailSizeDp + Dimensions.itemsVerticalPadding * 2) * 4)
                ) {
                    items(items = songs, key = Innertube.SongItem::key) { song ->
                        SongItem(
                            song = song,
                            thumbnailSizePx = songThumbnailSizePx,
                            thumbnailSizeDp = songThumbnailSizeDp,
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                onDismiss = menuState::hide,
                                                mediaItem = song.asMediaItem
                                            )
                                        }
                                    },
                                    onClick = { playSongs(songs, songs.indexOf(song)) }
                                )
                                .width(gridItemWidth)
                        )
                    }
                }
            } else if (songsResult == null) {
                ShimmerHost {
                    TextPlaceholder(modifier = sectionTextModifier)
                    repeat(4) { SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp) }
                }
            }

            // ---- Albums ----------------------------------------------------
            val albumItems = albums.orEmpty().distinctBy { it.browseId }
            if (albumItems.isNotEmpty()) {
                BasicText(text = "Albums", style = typography.m.semiBold, modifier = sectionTextModifier)
                LazyRow(contentPadding = endPaddingValues) {
                    items(items = albumItems, key = CatalogItem::key) { album ->
                        AlbumItem(
                            thumbnailUrl = album.thumbnailUrl?.thumbnail(albumThumbnailSizePx),
                            title = album.title,
                            authors = album.subtitle,
                            year = album.year,
                            thumbnailSizePx = albumThumbnailSizePx,
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true,
                            modifier = Modifier.clickable { album.browseId?.let(onAlbumClick) }
                        )
                    }
                }
            } else if (refreshOk == null && category.albums.isNotEmpty()) {
                ShimmerHost {
                    TextPlaceholder(modifier = sectionTextModifier)
                    Row {
                        repeat(2) { AlbumItemPlaceholder(thumbnailSizeDp = albumThumbnailSizeDp, alternative = true) }
                    }
                }
            }

            // ---- Artists ---------------------------------------------------
            val artistItems = artists.orEmpty().distinctBy { it.browseId }
            if (artistItems.isNotEmpty()) {
                BasicText(text = "Artists", style = typography.m.semiBold, modifier = sectionTextModifier)
                LazyRow(contentPadding = endPaddingValues) {
                    items(items = artistItems, key = CatalogItem::key) { artist ->
                        ArtistItem(
                            thumbnailUrl = artist.thumbnailUrl?.thumbnail(artistThumbnailSizePx),
                            name = artist.title,
                            subscribersCount = artist.subtitle,
                            thumbnailSizePx = artistThumbnailSizePx,
                            thumbnailSizeDp = artistThumbnailSizeDp,
                            alternative = true,
                            modifier = Modifier.clickable { artist.browseId?.let(onArtistClick) }
                        )
                    }
                }
            } else if (refreshOk == null) {
                ShimmerHost {
                    TextPlaceholder(modifier = sectionTextModifier)
                    Row {
                        repeat(2) { ArtistItemPlaceholder(thumbnailSizeDp = artistThumbnailSizeDp, alternative = true) }
                    }
                }
            }

            // Partial failure with some data cached: offer a quiet retry.
            if (!nothingCached && refreshOk == false) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    SecondaryTextButton(text = "Some items couldn't load — Retry", onClick = { reloadKey++ })
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.collapsedPlayer))
        }

        FloatingActionsContainerWithScrollToTop(
            scrollState = scrollState,
            iconId = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
