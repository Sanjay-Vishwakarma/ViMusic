package it.sjmusic.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import it.sjmusic.compose.persist.PersistMapCleanup
import it.sjmusic.compose.routing.RouteHandler
import it.sjmusic.compose.routing.defaultStacking
import it.sjmusic.compose.routing.defaultStill
import it.sjmusic.compose.routing.defaultUnstacking
import it.sjmusic.compose.routing.isStacking
import it.sjmusic.compose.routing.isUnknown
import it.sjmusic.compose.routing.isUnstacking
import it.sjmusic.vimusic.Database
import it.sjmusic.vimusic.R
import it.sjmusic.vimusic.models.SearchQuery
import it.sjmusic.vimusic.query
import it.sjmusic.vimusic.ui.components.themed.Scaffold
import it.sjmusic.vimusic.ui.screens.albumRoute
import it.sjmusic.vimusic.ui.screens.artistRoute
import it.sjmusic.vimusic.ui.screens.builtInPlaylistRoute
import it.sjmusic.vimusic.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import it.sjmusic.vimusic.ui.screens.globalRoutes
import it.sjmusic.vimusic.ui.screens.localPlaylistRoute
import it.sjmusic.vimusic.ui.screens.localplaylist.LocalPlaylistScreen
import it.sjmusic.vimusic.ui.screens.playlistRoute
import it.sjmusic.vimusic.ui.screens.search.SearchScreen
import it.sjmusic.vimusic.ui.screens.searchResultRoute
import it.sjmusic.vimusic.ui.screens.searchRoute
import it.sjmusic.vimusic.ui.screens.searchresult.SearchResultScreen
import it.sjmusic.vimusic.ui.screens.settings.SettingsScreen
import it.sjmusic.vimusic.ui.screens.settingsRoute
import it.sjmusic.vimusic.utils.homeScreenTabIndexKey
import it.sjmusic.vimusic.utils.pauseSearchHistoryKey
import it.sjmusic.vimusic.utils.preferences
import it.sjmusic.vimusic.utils.rememberPreference

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeScreen(onPlaylistUrl: (String) -> Unit) {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    RouteHandler(
        listenToGlobalEmitter = true,
        transitionSpec = {
            when {
                isStacking -> defaultStacking
                isUnstacking -> defaultUnstacking
                isUnknown -> when {
                    initialState.route == searchRoute && targetState.route == searchResultRoute -> defaultStacking
                    initialState.route == searchResultRoute && targetState.route == searchRoute -> defaultUnstacking
                    else -> defaultStill
                }

                else -> defaultStill
            }
        }
    ) {
        globalRoutes()

        settingsRoute {
            SettingsScreen()
        }

        localPlaylistRoute { playlistId ->
            LocalPlaylistScreen(
                playlistId = playlistId ?: error("playlistId cannot be null")
            )
        }

        builtInPlaylistRoute { builtInPlaylist ->
            BuiltInPlaylistScreen(
                builtInPlaylist = builtInPlaylist
            )
        }

        searchResultRoute { query ->
            SearchResultScreen(
                query = query,
                onSearchAgain = {
                    searchRoute(query)
                }
            )
        }

        searchRoute { initialTextInput ->
            val context = LocalContext.current

            SearchScreen(
                initialTextInput = initialTextInput,
                onSearch = { query ->
                    pop()
                    searchResultRoute(query)

                    if (!context.preferences.getBoolean(pauseSearchHistoryKey, false)) {
                        query {
                            Database.insert(SearchQuery(query = query))
                        }
                    }
                },
                onViewPlaylist = onPlaylistUrl
            )
        }

        host {
            val (savedTabIndex, onTabChanged) = rememberPreference(
                homeScreenTabIndexKey,
                defaultValue = 0
            )
            // Tabs 3 (Artists) and 4 (Albums) were removed - they only listed
            // bookmarked items and stayed empty. A saved index pointing at
            // them falls back to Quick picks.
            val tabIndex = savedTabIndex.takeIf { it in setOf(0, 1, 2, 5, 6) } ?: 0

            Scaffold(
                topIconButtonId = R.drawable.equalizer,
                onTopIconButtonClick = { settingsRoute() },
                tabIndex = tabIndex,
                onTabChanged = onTabChanged,
                tabColumnContent = { Item ->
                    Item(0, "Quick picks", R.drawable.sparkles)
                    Item(1, "Songs", R.drawable.musical_notes)
                    Item(2, "Playlists", R.drawable.playlist)
                    Item(5, "Explore", R.drawable.globe)
                    Item(6, "Local", R.drawable.library)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> QuickPicks(
                            onAlbumClick = { albumRoute(it) },
                            onArtistClick = { artistRoute(it) },
                            onPlaylistClick = { playlistRoute(it) },
                            onSearchClick = { searchRoute("") }
                        )

                        1 -> HomeSongs(
                            onSearchClick = { searchRoute("") }
                        )

                        2 -> HomePlaylists(
                            onBuiltInPlaylist = { builtInPlaylistRoute(it) },
                            onPlaylistClick = { localPlaylistRoute(it.id) },
                            onSearchClick = { searchRoute("") }
                        )

                        5 -> Explore(
                            onAlbumClick = { albumRoute(it) },
                            onArtistClick = { artistRoute(it) },
                            onSearchClick = { searchRoute("") }
                        )

                        6 -> HomeLocal(
                            onSearchClick = { searchRoute("") }
                        )
                    }
                }
            }
        }
    }
}
