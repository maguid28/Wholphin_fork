package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.playback.PlaybackViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Top scrim configuration for text readability (clock, season tabs)
const val TOP_SCRIM_ALPHA = 0.55f
const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
    ) : ViewModel() {
        fun clearBackdrop() {
            viewModelScope.launchIO { backdropService.clearBackdrop() }
        }
    }

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer,
    user: JellyfinUser,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var keepDrawerOpenForPreview by remember { mutableStateOf(false) }
    val currentDestination = navigationManager.backStack.lastOrNull() ?: Destination.Home()
    val libraryTvPlaybackDestination =
        (currentDestination as? Destination.Playback)
            ?.takeIf { it.libraryTvChannelKey != null }
    val activeLibraryTvPlaybackViewModel =
        libraryTvPlaybackDestination?.let { destination ->
            hiltViewModel<PlaybackViewModel, PlaybackViewModel.Factory>(
                key = "library-tv-playback-${server.id}-${user.id}",
                creationCallback = { it.create(destination) },
            )
        }
    var libraryTvPipViewModel by remember(server.id, user.id) { mutableStateOf<PlaybackViewModel?>(null) }
    LaunchedEffect(activeLibraryTvPlaybackViewModel) {
        activeLibraryTvPlaybackViewModel?.let { nextViewModel ->
            if (libraryTvPipViewModel !== nextViewModel) {
                libraryTvPipViewModel?.release()
                libraryTvPipViewModel = nextViewModel
            }
        }
    }
    val keepLibraryTvPlayback =
        currentDestination == Destination.LibraryTv || libraryTvPlaybackDestination != null
    LaunchedEffect(keepLibraryTvPlayback) {
        if (!keepLibraryTvPlayback) {
            libraryTvPipViewModel?.release()
            libraryTvPipViewModel = null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        (activeLibraryTvPlaybackViewModel ?: libraryTvPipViewModel)?.release()
        libraryTvPipViewModel = null
    }
    Box(
        modifier = modifier,
    ) {
        val backdropStyle = preferences.appPreferences.interfacePreferences.backdropStyle
        Backdrop(
            drawerIsOpen = drawerState.isOpen,
            backdropStyle = backdropStyle,
            enableTopScrim = enableTopScrim,
            viewModel = viewModel,
        )
        val navDrawerListState = rememberLazyListState()
        if (currentDestination.fullScreen) {
            NavDisplay(
                backStack = navigationManager.backStack,
                onBack = { navigationManager.goBack() },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider = { key ->
                    val contentKey = "${key}_${server.id}_${user.id}"
                    NavEntry(key, contentKey = contentKey) {
                        DestinationContent(
                            destination = key,
                            preferences = preferences,
                            onClearBackdrop = viewModel::clearBackdrop,
                            libraryTvPlaybackViewModel = activeLibraryTvPlaybackViewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            )
        } else {
            NavDrawer(
                destination = currentDestination,
                preferences = preferences,
                user = user,
                server = server,
                drawerState = drawerState,
                navDrawerListState = navDrawerListState,
                keepOpenOnFocusNavigation = keepDrawerOpenForPreview,
                onFocusNavigation = { keepDrawerOpenForPreview = true },
                onManualNavigation = { keepDrawerOpenForPreview = false },
                onClearBackdrop = viewModel::clearBackdrop,
                modifier = Modifier.fillMaxSize(),
            ) { onHomeBannerShown, takeContentFocus, suppressContentScroll ->
                NavDisplay(
                    backStack = navigationManager.backStack,
                    onBack = { navigationManager.goBack() },
                    // Drawer destinations must not overlap while focus is handed from the drawer
                    // to content. The outgoing page can otherwise accept focus and then disappear,
                    // which makes Compose fall back to the first focusable item (the profile).
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider = { key ->
                        val contentKey = "${key}_${server.id}_${user.id}"
                        NavEntry(key, contentKey = contentKey) {
                            if (key.fullScreen) {
                                ErrorMessage("Trying to show full-screen $key in nav drawer", null)
                            } else {
                                DestinationContent(
                                    destination = key,
                                    preferences = preferences,
                                    onClearBackdrop = viewModel::clearBackdrop,
                                    onHomeBannerShown = onHomeBannerShown,
                                    takeContentFocus = takeContentFocus(key),
                                    suppressContentScroll = suppressContentScroll,
                                    libraryTvPlaybackViewModel = libraryTvPipViewModel,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
