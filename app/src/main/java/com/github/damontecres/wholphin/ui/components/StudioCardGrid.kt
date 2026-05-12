package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.createStudioNameDestination
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.cards.StudioCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale
import java.util.UUID

@HiltViewModel(assistedFactory = StudioViewModel.Factory::class)
class StudioViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        val navigationManager: NavigationManager,
        @Assisted private val itemId: UUID,
        @Assisted private val includeItemTypes: List<BaseItemKind>?,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: UUID,
                includeItemTypes: List<BaseItemKind>?,
            ): StudioViewModel
        }

        val item = MutableLiveData<BaseItem?>(null)
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val studios = MutableLiveData<List<Studio>>(listOf())

        fun init() {
            loading.value = LoadingState.Loading
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Failed to fetch studios")) {
                val item =
                    api.userLibraryApi.getItem(itemId = itemId).content.let {
                        BaseItem(it, false)
                    }
                this@StudioViewModel.item.setValueOnMain(item)
                withContext(Dispatchers.Main) {
                    this@StudioViewModel.studios.value = PopularStudios
                    loading.value = LoadingState.Success
                }
            }
        }

        suspend fun positionOfLetter(letter: Char): Int =
            withContext(Dispatchers.Default) {
                val target = letter.uppercaseChar()
                studios.value.orEmpty().count {
                    it.name.firstOrNull()?.uppercaseChar()?.let { first -> first < target } == true
                }
            }
    }

@Stable
data class Studio(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
    val aliases: List<String> = listOf(name),
) : CardGridItem {
    override val gridId: String get() = id.toString()
    override val playable: Boolean = false
    override val sortName: String get() = name
}

private fun studio(
    name: String,
    vararg aliases: String,
    imageUrl: String? = null,
) = Studio(
    id = studioIdFor(name),
    name = name,
    imageUrl = imageUrl,
    aliases = (listOf(name) + aliases).distinctBy { it.normalizedStudioName() },
)

private fun String.normalizedStudioName(): String = lowercase(Locale.ROOT)

private fun studioIdFor(name: String): UUID =
    UUID.nameUUIDFromBytes("studio:${name.normalizedStudioName()}".toByteArray())

private val PopularStudios =
    listOf(
        studio(
            "Disney",
            "Walt Disney Pictures",
            "Walt Disney Animation Studios",
            "Disney Pictures",
            "Disney Television Animation",
            "Touchstone Pictures",
            "Hollywood Pictures",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/wdrCwmRnLFJhEoH8GSfymY85KHT.png",
        ),
        studio(
            "20th Century Studios",
            "20th Century Fox",
            "Twentieth Century Fox",
            "20th Century Fox Television",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/h0rjX5vjW5r8yEnUBStFarjcLT4.png",
        ),
        studio(
            "Sony Pictures",
            "Columbia Pictures",
            "TriStar Pictures",
            "Screen Gems",
            "Sony Pictures Animation",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/GagSvqWlyPdkFHMfQ3pNq6ix9P.png",
        ),
        studio(
            "Warner Bros.",
            "Warner Bros. Pictures",
            "Warner Bros",
            "Warner Bros. Television",
            "New Line Cinema",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ky0xOc5OrhzkZ1N6KyUxacfQsCk.png",
        ),
        studio(
            "Universal",
            "Universal Pictures",
            "Universal Television",
            "Universal Animation Studios",
            "Focus Features",
            "Working Title Films",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8lvHyhjr8oUKOOy2dKXoALWKdp0.png",
        ),
        studio(
            "Paramount",
            "Paramount Pictures",
            "Paramount Television Studios",
            "Paramount Animation",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/fycMZt242LVjagMByZOLUGbCvv3.png",
        ),
        studio(
            "Pixar",
            "Pixar Animation Studios",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1TjvGVDMYsj6JBxOAkUHpPEwLf7.png",
        ),
        studio(
            "DreamWorks",
            "DreamWorks Animation",
            "DreamWorks Pictures",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/kP7t6RwGz2AvvTkvnI1uteEwHet.png",
        ),
        studio(
            "Marvel Studios",
            "Marvel",
            "Marvel Entertainment",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/hUzeosd33nzE5MCNsZxCGEKTXaQ.png",
        ),
        studio(
            "DC",
            "DC Entertainment",
            "DC Films",
            "DC Studios",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/2Tc1P3Ac8M479naPp1kYT3izLS5.png",
        ),
        studio(
            "A24",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1ZXsGaFPgrgS6ZZGS37AqD5uU12.png",
        ),
        studio(
            "Lionsgate",
            "Lions Gate Films",
            "Lionsgate Films",
            imageUrl = "https://logo.clearbit.com/lionsgate.com",
        ),
        studio(
            "MGM",
            "Metro-Goldwyn-Mayer",
            "Metro Goldwyn Mayer",
            "Amazon MGM Studios",
            "Orion Pictures",
            "United Artists",
            imageUrl = "https://logo.clearbit.com/mgm.com",
        ),
        studio(
            "Searchlight Pictures",
            "Fox Searchlight Pictures",
            imageUrl = "https://logo.clearbit.com/searchlightpictures.com",
        ),
        studio(
            "Miramax",
            imageUrl = "https://logo.clearbit.com/miramax.com",
        ),
        studio(
            "Lucasfilm",
            imageUrl = "https://logo.clearbit.com/lucasfilm.com",
        ),
        studio(
            "Legendary",
            "Legendary Pictures",
            imageUrl = "https://logo.clearbit.com/legendary.com",
        ),
        studio(
            "Skydance",
            "Skydance Media",
            imageUrl = "https://logo.clearbit.com/skydance.com",
        ),
        studio(
            "Blumhouse",
            "Blumhouse Productions",
            imageUrl = "https://logo.clearbit.com/blumhouse.com",
        ),
        studio(
            "Nickelodeon Movies",
            "Nickelodeon Animation Studio",
            "Nickelodeon Studios",
            imageUrl = "https://logo.clearbit.com/nick.com",
        ),
        studio(
            "Studio Ghibli",
            "Ghibli",
            imageUrl = "https://logo.clearbit.com/ghibli.jp",
        ),
        studio(
            "LAIKA",
            "Laika",
            imageUrl = "https://logo.clearbit.com/laika.com",
        ),
        studio(
            "Cartoon Saloon",
            imageUrl = "https://logo.clearbit.com/cartoonsaloon.ie",
        ),
        studio(
            "Toho",
            "Toho Co.",
            "Toho Company",
            imageUrl = "https://logo.clearbit.com/toho.co.jp",
        ),
        studio(
            "Illumination",
            "Illumination Entertainment",
            imageUrl = "https://logo.clearbit.com/illumination.com",
        ),
    )

@Composable
fun StudioCardGrid(
    itemId: UUID,
    includeItemTypes: List<BaseItemKind>?,
    modifier: Modifier = Modifier,
    viewModel: StudioViewModel =
        hiltViewModel<StudioViewModel, StudioViewModel.Factory>(
            creationCallback = { it.create(itemId, includeItemTypes) },
        ),
) {
    val columns = 4
    val spacing = 16.dp
    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val studios by viewModel.studios.observeAsState(listOf())

    val gridFocusRequester = remember { FocusRequester() }
    when (val st = loading) {
        LoadingState.Pending,
        LoadingState.Loading,
        -> {
            LoadingPage(modifier.focusable())
        }

        is LoadingState.Error -> {
            ErrorMessage(st, modifier.focusable())
        }

        LoadingState.Success -> {
            Box(modifier = modifier) {
                LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                val item by viewModel.item.observeAsState(null)
                CardGrid(
                    pager = studios,
                    onClickItem = { _, studio ->
                        viewModel.navigationManager.navigateTo(
                            createStudioNameDestination(
                                studioName = studio.name,
                                studioAliases = studio.aliases,
                                parentId = itemId,
                                parentName = item?.title,
                                includeItemTypes = includeItemTypes,
                            ),
                        )
                    },
                    onLongClickItem = { _, _ -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { viewModel.positionOfLetter(it) },
                    gridFocusRequester = gridFocusRequester,
                    showJumpButtons = false,
                    showLetterButtons = false,
                    modifier = Modifier.fillMaxSize(),
                    initialPosition = 0,
                    positionCallback = { _, _ -> },
                    columns = columns,
                    spacing = spacing,
                    cardContent = { item: Studio?, onClick: () -> Unit, onLongClick: () -> Unit, mod: Modifier ->
                        StudioCard(
                            studio = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = mod,
                        )
                    },
                )
            }
        }
    }
}
