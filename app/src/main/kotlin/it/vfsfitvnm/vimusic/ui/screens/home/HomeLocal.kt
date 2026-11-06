package it.vfsfitvnm.vimusic.ui.screens.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.local.LocalFolder
import it.vfsfitvnm.vimusic.local.LocalMusic
import it.vfsfitvnm.vimusic.local.LocalSong
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.MusicBars
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.onOverlay
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.DisposableListener
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.forcePlayAtIndex
import it.vfsfitvnm.vimusic.utils.forcePlayFromBeginning
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.shouldBePlaying
import it.vfsfitvnm.vimusic.utils.thumbnail

/**
 * Songs stored on the phone, folder by folder. Playback goes through the same
 * PlayerService as online songs (ids are "local:<id>").
 */
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeLocal(onSearchClick: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var hasPermission by remember { mutableStateOf(LocalMusic.hasPermission(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        askedOnce = true
    }
    // Permission may have been granted from system settings meanwhile.
    LaunchedEffect(Unit) { hasPermission = LocalMusic.hasPermission(context) }

    var reloadKey by remember { mutableStateOf(0) }
    var folders by remember { mutableStateOf<List<LocalFolder>?>(null) }
    LaunchedEffect(hasPermission, reloadKey) {
        if (hasPermission) {
            folders = runCatching { LocalMusic.loadFolders(context) }.getOrDefault(emptyList())
        }
    }

    var openFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    val openFolder = folders?.firstOrNull { it.path == openFolderPath }
    BackHandler(enabled = openFolder != null) { openFolderPath = null }

    // What the player is on right now, to animate that song's row.
    var nowPlayingId by remember { mutableStateOf(binder?.player?.currentMediaItem?.mediaId) }
    var isPlaying by remember { mutableStateOf(binder?.player?.shouldBePlaying == true) }
    binder?.player?.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nowPlayingId = mediaItem?.mediaId
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = binder.player.shouldBePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = binder.player.shouldBePlaying
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px

    fun play(songs: List<LocalSong>, index: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        binder?.stopRadio()
        val items = (if (shuffle) songs.shuffled() else songs).map { it.asMediaItem }
        if (index <= 0 || shuffle) binder?.player?.forcePlayFromBeginning(items)
        else binder?.player?.forcePlayAtIndex(items, index)
    }

    Box(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
        ) {
            item(key = "header", contentType = 0) {
                if (openFolder == null) {
                    Header(title = "Local") {
                        if (hasPermission) {
                            HeaderIconButton(
                                icon = R.drawable.sync,
                                color = colorPalette.text,
                                onClick = { folders = null; reloadKey++ }
                            )
                        }
                    }
                } else {
                    Header(title = openFolder.name) {
                        HeaderIconButton(
                            icon = R.drawable.chevron_back,
                            color = colorPalette.text,
                            onClick = { openFolderPath = null }
                        )
                    }
                }
            }

            when {
                !hasPermission -> item(key = "permission") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        BasicText(
                            text = "Phone mein rakhe gaane dikhane ke liye storage (music) permission chahiye.",
                            style = typography.s.secondary.center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SecondaryTextButton(
                            text = "Allow",
                            onClick = { permissionLauncher.launch(LocalMusic.permission) }
                        )
                        if (askedOnce) {
                            // "Don't ask again" makes the system dialog silent; offer settings.
                            Spacer(modifier = Modifier.height(8.dp))
                            SecondaryTextButton(
                                text = "Open settings",
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            )
                        }
                    }
                }

                folders == null -> item(key = "loading") {
                    ShimmerHost {
                        repeat(6) { SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }
                    }
                }

                folders!!.isEmpty() -> item(key = "empty") {
                    BasicText(
                        text = "Phone mein koi gaana nahi mila.",
                        style = typography.s.secondary.center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }

                openFolder == null -> {
                    item(key = "summary") {
                        BasicText(
                            text = "${folders!!.sumOf { it.songs.size }} songs · ${folders!!.size} folders",
                            style = typography.xs.secondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(items = folders!!, key = { "folder:${it.path}" }) { folder ->
                        FolderRow(
                            folder = folder,
                            playing = folder.songs.any { it.mediaId == nowPlayingId },
                            isPlaying = isPlaying,
                            onClick = { openFolderPath = folder.path }
                        )
                    }
                }

                else -> {
                    item(key = "actions") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            BasicText(
                                text = "${openFolder.songs.size} songs",
                                style = typography.xs.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryTextButton(text = "Play all", onClick = { play(openFolder.songs) })
                            SecondaryTextButton(text = "Shuffle", onClick = { play(openFolder.songs, shuffle = true) })
                        }
                    }
                    itemsIndexed(items = openFolder.songs, key = { _, song -> song.mediaId }) { index, song ->
                        val isCurrent = song.mediaId == nowPlayingId
                        SongItem(
                            title = song.title,
                            authors = song.artist ?: song.album,
                            duration = song.durationText,
                            thumbnailSizeDp = thumbnailSizeDp,
                            thumbnailContent = {
                                LocalArtwork(
                                    model = song.artworkUri.toString().thumbnail(thumbnailSizePx),
                                    showNowPlaying = isCurrent,
                                    isPlaying = isPlaying
                                )
                            },
                            modifier = Modifier
                                .background(if (isCurrent) colorPalette.background1 else Color.Transparent)
                                .combinedClickable(
                                onLongClick = {
                                    menuState.display {
                                        NonQueuedMediaItemMenu(
                                            onDismiss = menuState::hide,
                                            mediaItem = song.asMediaItem
                                        )
                                    }
                                },
                                onClick = { play(openFolder.songs, index) }
                            )
                        )
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            iconId = if (openFolder != null) R.drawable.shuffle else R.drawable.search,
            onClick = {
                if (openFolder != null) play(openFolder.songs, shuffle = true) else onSearchClick()
            }
        )
    }
}

@Composable
private fun FolderRow(folder: LocalFolder, playing: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = Dimensions.itemsVerticalPadding)
    ) {
        Box(modifier = Modifier.size(Dimensions.thumbnails.song)) {
            // Cover of the first song in the folder (falls back to a note icon).
            LocalArtwork(
                model = folder.songs.firstOrNull()?.artworkUri?.toString()
                    ?.thumbnail(Dimensions.thumbnails.song.px),
                showNowPlaying = playing,
                isPlaying = isPlaying
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = folder.name,
                style = typography.xs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = "${folder.songs.size} songs · ${folder.path}",
                style = typography.xs.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Song/folder artwork: a note icon underneath (visible when the file has no
 * embedded cover), the cover on top, and the "now playing" bars overlay.
 */
@Composable
private fun LocalArtwork(model: String?, showNowPlaying: Boolean, isPlaying: Boolean) {
    val (colorPalette, _, thumbnailShape) = LocalAppearance.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(thumbnailShape)
            .background(colorPalette.background1)
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(R.drawable.musical_notes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.textDisabled),
            modifier = Modifier.size(20.dp)
        )

        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(600)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f))
                    .fillMaxSize()
            ) {
                if (isPlaying) {
                    MusicBars(color = colorPalette.onOverlay, modifier = Modifier.height(24.dp))
                } else {
                    Image(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
