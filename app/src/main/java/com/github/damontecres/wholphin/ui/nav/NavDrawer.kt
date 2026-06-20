package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.TimeDisplay
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.spacedByWithFooter
import com.github.damontecres.wholphin.ui.theme.LocalTheme
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val navDrawerService: NavDrawerService,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val backdropService: BackdropService,
        private val musicService: MusicService,
    ) : ViewModel() {
        val state = navDrawerService.state

        val selectedIndex = MutableLiveData(-1)
        val moreExpanded = MutableLiveData(false)

        fun onClickDrawerItem(
            index: Int,
            item: NavDrawerItem,
        ) {
            if (item !is NavDrawerItem.More) setShowMore(false)
            when (item) {
                NavDrawerItem.Favorites -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Favorites,
                    )
                }

                NavDrawerItem.More -> {
                    setShowMore(!moreExpanded.value!!)
                }

                NavDrawerItem.Discover -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Discover,
                    )
                }

                NavDrawerItem.LibraryTv -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.LibraryTv,
                    )
                }

                is ServerNavDrawerItem -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(item.destination)
                }
            }
        }

        fun onPreviewDrawerItem(
            index: Int,
            item: NavDrawerItem,
        ) {
            if (item !is NavDrawerItem.More) setShowMore(false)
            when (item) {
                NavDrawerItem.Favorites -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Favorites,
                    )
                }

                NavDrawerItem.More -> {
                    setShowMore(!moreExpanded.value!!)
                }

                NavDrawerItem.Discover -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Discover,
                    )
                }

                NavDrawerItem.LibraryTv -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.LibraryTv,
                    )
                }

                is ServerNavDrawerItem -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(item.destination)
                }
            }
        }

        fun setIndex(index: Int) {
            selectedIndex.value = index
        }

        fun setShowMore(value: Boolean) {
            moreExpanded.value = value
        }

        fun getUserImage(user: JellyfinUser): String = api.imageApi.getUserImageUrl(user.id)

        /**
         * Determine which nav drawer item should be highlighted as currently selected
         */
        fun updateSelectedIndex() {
            viewModelScope.launchDefault {
                val asDestinations =
                    (
                        state.value.items +
                            listOf(
                                NavDrawerItem.More,
                                NavDrawerItem.Discover,
                            ) + state.value.moreItems
                    ).map {
                        if (it is ServerNavDrawerItem) {
                            it.destination
                        } else if (it is NavDrawerItem.Favorites) {
                            Destination.Favorites
                        } else if (it is NavDrawerItem.Discover) {
                            Destination.Discover
                        } else if (it is NavDrawerItem.LibraryTv) {
                            Destination.LibraryTv
                        } else {
                            null
                        }
                    }

                val backstack = navigationManager.backStack.toList().reversed()
                for (i in 0..<backstack.size) {
                    val key = backstack[i]
                    if (key is Destination) {
                        val index =
                            if (key is Destination.Home) {
                                HOME_INDEX
                            } else if (key is Destination.Search) {
                                SEARCH_INDEX
                            } else if (key is Destination.NowPlaying) {
                                NOW_PLAYING_INDEX
                            } else {
                                val idx = asDestinations.indexOf(key)
                                if (idx >= 0) {
                                    idx
                                } else {
                                    null
                                }
                            }
                        Timber.v("Found $index => $key")
                        if (index != null) {
                            selectedIndex.setValueOnMain(index)
                            break
                        }
                    }
                }
            }
        }

        fun navigateToSetup(userList: SetupDestination) {
            viewModelScope.launchDefault {
                musicService.stop()
                setupNavigationManager.navigateTo(userList)
            }
        }
    }

/**
 * An item that can be shown in the nav drawer
 *
 * Some are built-in such as Favorites. Others are created dynamically for libraries.
 */
sealed interface NavDrawerItem {
    val id: String

    fun name(context: Context): String

    object Favorites : NavDrawerItem {
        override val id: String
            get() = "a_favorites"

        override fun name(context: Context): String = context.getString(R.string.favorites)
    }

    object More : NavDrawerItem {
        override val id: String
            get() = "a_more"

        override fun name(context: Context): String = context.getString(R.string.more)
    }

    object Discover : NavDrawerItem {
        override val id: String
            get() = "a_discover"

        override fun name(context: Context): String = context.getString(R.string.discover)
    }

    object LibraryTv : NavDrawerItem {
        override val id: String
            get() = "a_library_tv"

        override fun name(context: Context): String = context.getString(R.string.library_tv)
    }
}

/**
 * A server provided nav drawer item, typically a library
 */
data class ServerNavDrawerItem(
    val itemId: UUID,
    val name: String,
    val destination: Destination,
    val type: CollectionType,
) : NavDrawerItem {
    override val id: String = getId(itemId)

    override fun name(context: Context): String = name

    companion object {
        fun getId(itemId: UUID) = "s_" + itemId.toServerString()
    }
}

private const val HOME_INDEX = -1
private const val SEARCH_INDEX = -2
private const val NOW_PLAYING_INDEX = -3
private const val NavDrawerFocusNavigationDelayMillis = 500L
private const val NavDrawerIdleCloseDelayMillis = 3 * 60 * 1_000L
private val NavDrawerMainTextSize = 13.sp
private val NavDrawerMainLineHeight = 16.sp
private val NavDrawerSubTextSize = 11.sp
private val NavDrawerSubLineHeight = 13.sp

private suspend fun navigateAfterRetainingDrawerFocus(
    itemFocusRequester: FocusRequester,
    onFocusNavigation: (FocusRequester) -> Boolean,
    isFocusNavigationActive: (FocusRequester) -> Boolean,
    onAutoNavigate: () -> Unit,
) {
    if (onFocusNavigation(itemFocusRequester)) {
        withFrameNanos { }
        if (isFocusNavigationActive(itemFocusRequester)) {
            onAutoNavigate()
        }
    }
}

private fun Modifier.activateOnDrawerItemKey(
    enabled: Boolean,
    onActivate: () -> Unit,
): Modifier =
    if (enabled) {
        onPreviewKeyEvent { event ->
            val isActivationKey =
                when (event.key) {
                    Key.DirectionRight,
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter,
                    Key.ButtonSelect,
                    Key.ButtonA,
                    -> true

                    else -> false
                }

            if (isActivationKey) {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    onActivate()
                }
                true
            } else {
                false
            }
        }
    } else {
        this
    }

/**
 * Display the left side navigation drawer with [DestinationContent] on the right
 */
@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    drawerState: DrawerState,
    navDrawerListState: LazyListState,
    keepOpenOnFocusNavigation: Boolean = false,
    onFocusNavigation: () -> Unit = {},
    onManualNavigation: () -> Unit = {},
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            key = "${server.id}_${user.id}", // Keyed to the server & user to ensure its reset when switching either
        ),
    content: @Composable (onHomeBannerShown: () -> Unit, takeHomeFocus: Boolean) -> Unit =
        { onHomeBannerShown, takeHomeFocus ->
            DestinationContent(
                destination = destination,
                preferences = preferences,
                onClearBackdrop = onClearBackdrop,
                onHomeBannerShown = onHomeBannerShown,
                takeHomeFocus = takeHomeFocus,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = CollapsedDrawerItemWidth + 8.dp, end = 16.dp),
            )
        },
) {
    LaunchedEffect(Unit) { viewModel.updateSelectedIndex() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

    val drawerFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun drawerFocusRequester(key: String): FocusRequester =
        drawerFocusRequesters.getOrPut(key) { FocusRequester() }
    val searchFocusRequester = drawerFocusRequester("search")
    val homeFocusRequester = drawerFocusRequester("home")
    val nowPlayingFocusRequester = drawerFocusRequester("now_playing")
    var previewFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var activeDrawerFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var enteringContent by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    val retainOpenForPreview = keepOpenOnFocusNavigation && !enteringContent
    val retainPreviewFocus: (FocusRequester) -> Boolean = retain@{
        if (enteringContent || !drawerState.isOpen || activeDrawerFocusRequester !== it) {
            return@retain false
        }
        previewFocusRequester = it
        onFocusNavigation()
        true
    }
    val isPreviewFocusActive: (FocusRequester) -> Boolean = {
        !enteringContent &&
            drawerState.isOpen &&
            activeDrawerFocusRequester === it &&
            previewFocusRequester === it
    }
    val updatePreviewFocus: (FocusRequester) -> Unit = {
        activeDrawerFocusRequester = it
        if (retainOpenForPreview && drawerState.isOpen) {
            previewFocusRequester = it
        }
    }
    val contentFocusJob = remember { arrayOfNulls<Job>(1) }
    fun closeDrawer() {
        contentFocusJob[0]?.cancel()
        enteringContent = false
        previewFocusRequester = null
        activeDrawerFocusRequester = null
        onManualNavigation()
        drawerState.setValue(DrawerValue.Closed)
    }
    val closeForManualNavigation = {
        if (!enteringContent) {
            enteringContent = true
            contentHasFocus = false
            previewFocusRequester = null
            activeDrawerFocusRequester = null
            onManualNavigation()
            drawerState.setValue(DrawerValue.Closed)
            contentFocusJob[0] =
                scope.launch {
                    try {
                        repeat(20) { attempt ->
                            delay(50)
                            if (contentHasFocus) {
                                return@launch
                            }
                            val moved = focusManager.moveFocus(FocusDirection.Right)
                            Timber.v(
                                "Content focus handoff destination=%s, attempt=%s, moved=%s, contentHasFocus=%s",
                                destination,
                                attempt + 1,
                                moved,
                                contentHasFocus,
                            )
                        }
                        if (!contentHasFocus) {
                            Timber.w("Timed out handing drawer focus to content")
                        }
                    } finally {
                        enteringContent = false
                    }
                }
        }
    }
    val closeForHomeBanner = {
        if (!retainOpenForPreview || !drawerState.isOpen) {
            closeDrawer()
        }
    }
    val idleCloseJob = remember { arrayOfNulls<Job>(1) }
    fun restartIdleCloseTimer() {
        idleCloseJob[0]?.cancel()
        idleCloseJob[0] =
            scope.launch {
                delay(NavDrawerIdleCloseDelayMillis)
                if (drawerState.isOpen) {
                    closeDrawer()
                }
            }
    }
    LaunchedEffect(Unit) {
        restartIdleCloseTimer()
    }
    DisposableEffect(Unit) {
        onDispose {
            idleCloseJob[0]?.cancel()
            contentFocusJob[0]?.cancel()
        }
    }

    val state by viewModel.state.collectAsState()
    val moreExpanded by viewModel.moreExpanded.observeAsState(false)
    // A negative index is a built-in page, >=0 is a library
    val selectedIndex by viewModel.selectedIndex.observeAsState(-1)
    val selectedDrawerFocusRequester =
        when (selectedIndex) {
            NOW_PLAYING_INDEX -> nowPlayingFocusRequester
            SEARCH_INDEX -> searchFocusRequester
            HOME_INDEX -> homeFocusRequester
            in state.items.indices -> drawerFocusRequester(state.items[selectedIndex].id)
            state.items.size -> drawerFocusRequester("more")
            else -> {
                val moreIndex = selectedIndex - state.items.size - 1
                state.moreItems.getOrNull(moreIndex)?.let {
                    drawerFocusRequester("more_${it.id}")
                }
            }
        }

    // If the user presses back while on the home page, open the nav drawer, another back press will quit the app
    BackHandler(enabled = (drawerState.currentValue == DrawerValue.Closed && destination is Destination.Home)) {
        scope.launch {
            drawerState.setValue(DrawerValue.Open)
            restartIdleCloseTimer()
            withFrameNanos { }
            (selectedDrawerFocusRequester ?: homeFocusRequester).requestFocus()
        }
    }
    BackHandler(enabled = moreExpanded && drawerState.currentValue == DrawerValue.Open) {
        viewModel.setShowMore(false)
    }

    val closedDrawerWidth = CollapsedDrawerItemWidth
    val openDrawerWidth = ExpandedDrawerItemWidth
    val offset by animateIntOffsetAsState(
        targetValue =
            IntOffset(
                x =
                    with(density) {
                        if (drawerState.isOpen) (openDrawerWidth - closedDrawerWidth).roundToPx() else 0
                    },
                y = 0,
            ),
        animationSpec =
            spring(
                stiffness = DrawerAnimationStiffness,
                visibilityThreshold = IntOffset.VisibilityThreshold,
            ),
    )

    ModalNavigationDrawer(
        modifier =
            modifier.onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) {
                    restartIdleCloseTimer()
                }
                false
        },
        drawerState = drawerState,
        retainOpenOnFocusLoss = retainOpenForPreview,
        drawerEntryFocusRequester =
            previewFocusRequester
                ?: selectedDrawerFocusRequester
                ?: activeDrawerFocusRequester
                ?: homeFocusRequester,
        drawerContent = { drawerValue ->
            val isOpen = drawerValue.isOpen
            val spacedBy = 2.dp

            ProvideTextStyle(
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = NavDrawerMainTextSize,
                    lineHeight = NavDrawerMainLineHeight,
                    fontWeight = FontWeight.Normal,
                ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacedBy),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    // Even though some must be clicked, focusing on it should clear other focused items
                    val interactionSource = remember { MutableInteractionSource() }
                    val userImageUrl = remember(user) { viewModel.getUserImage(user) }
                    ProfileIcon(
                        user = user,
                        imageUrl = userImageUrl,
                        serverName = server.name ?: server.url,
                        drawerOpen = isOpen,
                        interactionSource = interactionSource,
                        onClick = {
                            viewModel.navigateToSetup(
                                SetupDestination.UserList(server),
                            )
                        },
                        modifier = Modifier,
                    )
                    AnimatedVisibility(
                        visible = state.nowPlayingEnabled,
                        enter = expandVertically(expandFrom = Alignment.Top),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        IconNavItem(
                            text = stringResource(R.string.now_playing),
                            subtext = state.nowPlayingTitle,
                            icon = Icons.Default.PlayArrow,
                            selected = selectedIndex == NOW_PLAYING_INDEX,
                            drawerOpen = isOpen,
                            interactionSource = interactionSource,
                            onClick = {
                                viewModel.setIndex(NOW_PLAYING_INDEX)
                                viewModel.navigationManager.navigateTo(Destination.NowPlaying)
                            },
                            onAutoNavigate = {
                                viewModel.setIndex(NOW_PLAYING_INDEX)
                                viewModel.navigationManager.navigateTo(Destination.NowPlaying)
                            },
                            itemFocusRequester = nowPlayingFocusRequester,
                            onFocused = updatePreviewFocus,
                            onFocusNavigation = retainPreviewFocus,
                            isFocusNavigationActive = isPreviewFocusActive,
                            onManualNavigation = closeForManualNavigation,
                            modifier = Modifier,
                        )
                    }
                    LazyColumn(
                        state = navDrawerListState,
                        contentPadding = PaddingValues(0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedByWithFooter(spacedBy),
                        modifier =
                            Modifier
                                .focusGroup()
                                .focusRestorer(searchFocusRequester)
                                .focusProperties {
                                    onEnter = {
                                        if (requestedFocusDirection == FocusDirection.Down) {
                                            searchFocusRequester.tryRequestFocus()
                                        } else {
                                            (
                                                    previewFocusRequester
                                                    ?: selectedDrawerFocusRequester
                                                    ?: activeDrawerFocusRequester
                                                    ?: homeFocusRequester
                                            ).tryRequestFocus("nav_drawer_list_entry")
                                        }
                                    }
                                }.fillMaxHeight(),
                    ) {
                        item(key = "search") {
                            val interactionSource = remember { MutableInteractionSource() }
                            IconNavItem(
                                text = stringResource(R.string.search),
                                icon = Icons.Default.Search,
                                selected = selectedIndex == SEARCH_INDEX,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.setIndex(SEARCH_INDEX)
                                    viewModel.navigationManager.navigateToFromDrawer(Destination.Search)
                                },
                                onAutoNavigate = {
                                    viewModel.setIndex(SEARCH_INDEX)
                                    viewModel.navigationManager.navigateToFromDrawer(Destination.Search)
                                },
                                itemFocusRequester = searchFocusRequester,
                                onFocused = updatePreviewFocus,
                                onFocusNavigation = retainPreviewFocus,
                                isFocusNavigationActive = isPreviewFocusActive,
                                onManualNavigation = closeForManualNavigation,
                                modifier = Modifier,
                            )
                        }
                        item(key = "home") {
                            val interactionSource = remember { MutableInteractionSource() }
                            IconNavItem(
                                text = stringResource(R.string.home),
                                icon = Icons.Default.Home,
                                selected = selectedIndex == HOME_INDEX,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.setIndex(HOME_INDEX)
                                    if (destination !is Destination.Home) {
                                        viewModel.navigationManager.goToHome()
                                    }
                                },
                                onAutoNavigate = {
                                    viewModel.setIndex(HOME_INDEX)
                                    if (destination !is Destination.Home) {
                                        viewModel.navigationManager.goToHome()
                                    }
                                },
                                itemFocusRequester = homeFocusRequester,
                                onFocused = updatePreviewFocus,
                                onFocusNavigation = retainPreviewFocus,
                                isFocusNavigationActive = isPreviewFocusActive,
                                onManualNavigation = closeForManualNavigation,
                                modifier = Modifier,
                            )
                        }
                        itemsIndexed(
                            items = state.items,
                            key = { _, item -> item.id },
                        ) { index, it ->
                            if (it !is NavDrawerItem.Discover || state.discoverEnabled) {
                                val interactionSource = remember { MutableInteractionSource() }
                                NavItem(
                                    library = it,
                                    selected = selectedIndex == index,
                                    moreExpanded = moreExpanded,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.onClickDrawerItem(index, it)
                                    },
                                    onAutoNavigate = {
                                        viewModel.onPreviewDrawerItem(index, it)
                                    },
                                    itemFocusRequester = drawerFocusRequester(it.id),
                                    onFocused = updatePreviewFocus,
                                    onFocusNavigation = retainPreviewFocus,
                                    isFocusNavigationActive = isPreviewFocusActive,
                                    onManualNavigation = closeForManualNavigation,
                                    modifier = Modifier,
                                )
                            }
                        }
                        if (state.moreItems.isNotEmpty()) {
                            item(key = "more") {
                                val index = state.items.size
                                val interactionSource = remember { MutableInteractionSource() }
                                NavItem(
                                    library = NavDrawerItem.More,
                                    selected = selectedIndex == index,
                                    moreExpanded = moreExpanded,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.onClickDrawerItem(index, NavDrawerItem.More)
                                    },
                                    autoNavigateOnFocus = false,
                                    itemFocusRequester = drawerFocusRequester("more"),
                                    onFocused = updatePreviewFocus,
                                    modifier = Modifier,
                                )
                            }
                        }
                        if (moreExpanded) {
                            itemsIndexed(
                                items = state.moreItems,
                                key = { _, item -> "more_${item.id}" },
                            ) { index, it ->
                                val adjustedIndex =
                                    remember(state) { (index + state.items.size + 1) }
                                if (it !is NavDrawerItem.Discover || state.discoverEnabled) {
                                    val interactionSource = remember { MutableInteractionSource() }
                                    NavItem(
                                        library = it,
                                        selected = selectedIndex == adjustedIndex,
                                        moreExpanded = moreExpanded,
                                        drawerOpen = isOpen,
                                        onClick = {
                                            viewModel.onClickDrawerItem(
                                                adjustedIndex,
                                                it,
                                            )
                                        },
                                        onAutoNavigate = {
                                            viewModel.onPreviewDrawerItem(adjustedIndex, it)
                                        },
                                        itemFocusRequester = drawerFocusRequester("more_${it.id}"),
                                        onFocused = updatePreviewFocus,
                                        onFocusNavigation = retainPreviewFocus,
                                        isFocusNavigationActive = isPreviewFocusActive,
                                        onManualNavigation = closeForManualNavigation,
                                        containerColor =
                                            if (isOpen) {
                                                MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                                            } else {
                                                Color.Unspecified
                                            },
                                        interactionSource = interactionSource,
                                        modifier = Modifier,
                                    )
                                }
                            }
                        }
                        item(key = "settings") {
                            val interactionSource = remember { MutableInteractionSource() }
                            IconNavItem(
                                text = stringResource(R.string.settings),
                                icon = Icons.Default.Settings,
                                selected = false,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.navigationManager.navigateTo(
                                        Destination.Settings(
                                            PreferenceScreenOption.BASIC,
                                        ),
                                    )
                                },
                                onAutoNavigate = {
                                    viewModel.navigationManager.navigateTo(
                                        Destination.Settings(
                                            PreferenceScreenOption.BASIC,
                                        ),
                                    )
                                },
                                itemFocusRequester = drawerFocusRequester("settings"),
                                onFocused = updatePreviewFocus,
                                onFocusNavigation = retainPreviewFocus,
                                isFocusNavigationActive = isPreviewFocusActive,
                                autoNavigateOnFocus = false,
                                onManualNavigation = closeForManualNavigation,
                                modifier = Modifier,
                            )
                        }
                    }
                }
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Drawer content
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset { offset }
                        .padding(start = closedDrawerWidth + 8.dp, end = 16.dp)
                        .onFocusChanged {
                            contentHasFocus = it.hasFocus
                        }
                        .ifElse(
                            retainOpenForPreview && drawerState.isOpen,
                            Modifier
                                .focusProperties {
                                    canFocus = false
                                    onEnter = {
                                        cancelFocusChange()
                                    }
                                },
                        ).focusGroup(),
            ) {
                content(
                    closeForHomeBanner,
                    !drawerState.isOpen,
                )
            }
            if (preferences.appPreferences.interfacePreferences.showClock && destination != Destination.LibraryTv) {
                TimeDisplay()
            }
        }
    }
}

@Composable
fun NavigationDrawerScope.ProfileIcon(
    user: JellyfinUser,
    imageUrl: String?,
    serverName: String,
    drawerOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val activate = { currentOnClick() }

    NavigationDrawerItem(
        modifier = modifier.activateOnDrawerItemKey(drawerOpen, activate),
        selected = false,
        onClick = activate,
        leadingContent = {
            UserIconCardImage(
                id = user.id,
                name = user.name,
                imageUrl = imageUrl,
                alpha = if (drawerOpen) 1f else .5f,
                modifier = Modifier.size(DrawerIconSize),
            )
        },
        supportingContent = {
            Text(
                text = serverName,
                fontSize = NavDrawerSubTextSize,
                lineHeight = NavDrawerSubLineHeight,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
        },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = user.name ?: user.id.toString(),
            fontSize = NavDrawerMainTextSize,
            lineHeight = NavDrawerMainLineHeight,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun NavigationDrawerScope.IconNavItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    selected: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    autoNavigateOnFocus: Boolean = true,
    onAutoNavigate: (() -> Unit)? = null,
    itemFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (FocusRequester) -> Unit = {},
    onFocusNavigation: (FocusRequester) -> Boolean = { true },
    isFocusNavigationActive: (FocusRequester) -> Boolean = { true },
    onManualNavigation: () -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnAutoNavigate by rememberUpdatedState(onAutoNavigate ?: onClick)
    LaunchedEffect(focused, drawerOpen) {
        if (drawerOpen && focused) {
            onFocused(itemFocusRequester)
        }
    }
    LaunchedEffect(focused, selected, drawerOpen, autoNavigateOnFocus) {
        if (autoNavigateOnFocus && drawerOpen && focused && !selected) {
            delay(NavDrawerFocusNavigationDelayMillis)
            navigateAfterRetainingDrawerFocus(
                itemFocusRequester = itemFocusRequester,
                onFocusNavigation = onFocusNavigation,
                isFocusNavigationActive = isFocusNavigationActive,
                onAutoNavigate = currentOnAutoNavigate,
            )
        }
    }
    val activate = {
        onManualNavigation()
        currentOnClick()
    }
    NavigationDrawerItem(
        modifier =
            modifier
                .activateOnDrawerItemKey(drawerOpen, activate)
                .focusRequester(itemFocusRequester),
        selected = false,
        onClick = activate,
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(DrawerIconSize),
            )
        },
        supportingContent =
            subtext?.let {
                {
                    Text(
                        text = it,
                        fontSize = NavDrawerSubTextSize,
                        lineHeight = NavDrawerSubLineHeight,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = text,
            fontSize = NavDrawerMainTextSize,
            lineHeight = NavDrawerMainLineHeight,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun NavigationDrawerScope.NavItem(
    library: NavDrawerItem,
    onClick: () -> Unit,
    selected: Boolean,
    moreExpanded: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    containerColor: Color = Color.Unspecified,
    autoNavigateOnFocus: Boolean = true,
    onAutoNavigate: (() -> Unit)? = null,
    itemFocusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (FocusRequester) -> Unit = {},
    onFocusNavigation: (FocusRequester) -> Boolean = { true },
    isFocusNavigationActive: (FocusRequester) -> Boolean = { true },
    onManualNavigation: () -> Unit = {},
) {
    val context = LocalContext.current
    val useFont = library !is ServerNavDrawerItem || library.type != CollectionType.LIVETV
    val icon =
        remember(library) {
            when (library) {
                NavDrawerItem.Favorites -> {
                    R.string.fa_heart
                }

                NavDrawerItem.More -> {
                    R.string.fa_ellipsis
                }

                NavDrawerItem.Discover -> {
                    R.string.fa_magnifying_glass_plus
                }

                NavDrawerItem.LibraryTv -> {
                    R.string.fa_tv
                }

                is ServerNavDrawerItem -> {
                    when (library.type) {
                        CollectionType.MOVIES -> R.string.fa_film
                        CollectionType.TVSHOWS -> R.string.fa_tv
                        CollectionType.HOMEVIDEOS -> R.string.fa_video
                        CollectionType.LIVETV -> R.drawable.gf_dvr
                        CollectionType.MUSIC -> R.string.fa_music
                        CollectionType.BOXSETS -> R.string.fa_open_folder
                        CollectionType.PLAYLISTS -> R.string.fa_list_ul
                        else -> R.string.fa_film
                    }
                }
            }
        }
    val focused by interactionSource.collectIsFocusedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnAutoNavigate by rememberUpdatedState(onAutoNavigate ?: onClick)
    LaunchedEffect(focused, drawerOpen) {
        if (drawerOpen && focused) {
            onFocused(itemFocusRequester)
        }
    }
    LaunchedEffect(focused, selected, drawerOpen, autoNavigateOnFocus) {
        if (autoNavigateOnFocus && drawerOpen && focused && !selected) {
            delay(NavDrawerFocusNavigationDelayMillis)
            navigateAfterRetainingDrawerFocus(
                itemFocusRequester = itemFocusRequester,
                onFocusNavigation = onFocusNavigation,
                isFocusNavigationActive = isFocusNavigationActive,
                onAutoNavigate = currentOnAutoNavigate,
            )
        }
    }
    val activate = {
        onManualNavigation()
        currentOnClick()
    }
    NavigationDrawerItem(
        modifier =
            modifier
                .activateOnDrawerItemKey(drawerOpen, activate)
                .focusRequester(itemFocusRequester),
        selected = false,
        onClick = activate,
        colors =
            NavigationDrawerItemDefaults.colors(
                containerColor = containerColor,
            ),
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (useFont) {
                    Text(
                        text = stringResource(icon),
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        fontFamily = FontAwesome,
                        color = color,
                        modifier = Modifier,
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(DrawerIconSize),
                    )
                }
            }
        },
        trailingContent =
            if (library is NavDrawerItem.More) {
                {
                    Icon(
                        imageVector = if (moreExpanded) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowLeft,
                        contentDescription = null,
                    )
                }
            } else {
                null
            },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = library.name(context),
            fontSize = NavDrawerMainTextSize,
            lineHeight = NavDrawerMainLineHeight,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
@ReadOnlyComposable
fun navItemColor(
    selected: Boolean,
    focused: Boolean,
    drawerOpen: Boolean,
): Color {
    val theme = LocalTheme.current
    if (theme == AppThemeColors.OLED_BLACK) {
        return when {
            selected && focused -> Color.Black
            selected && !drawerOpen -> Color.White.copy(alpha = .5f)
            selected && drawerOpen -> Color.White.copy(alpha = .85f)
            focused -> Color.Black.copy(alpha = .5f)
            drawerOpen -> Color(0xFF707070)
            else -> Color(0xFF505050).copy(alpha = .66f)
        }
    } else {
        val alpha =
            when {
                drawerOpen -> .85f
                selected && !drawerOpen -> .5f
                else -> .2f
            }
        return when {
            selected && focused -> LocalContentColor.current

            selected -> {
                MaterialTheme.colorScheme.border
            }

            focused -> {
                LocalContentColor.current
            }

            else -> {
                MaterialTheme.colorScheme.onSurface
            }
        }.copy(alpha = alpha)
    }
}

val DrawerState.isOpen: Boolean get() = this.currentValue.isOpen

val DrawerValue.isOpen: Boolean get() = this == DrawerValue.Open
