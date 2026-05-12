package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.createNetworkDestination
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.setup.rememberIdColor
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

@HiltViewModel(assistedFactory = NetworkViewModel.Factory::class)
class NetworkViewModel
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
            ): NetworkViewModel
        }

        val item = MutableLiveData<BaseItem?>(null)
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val networks = MutableLiveData<List<Network>>(listOf())

        fun init() {
            loading.value = LoadingState.Loading
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Failed to fetch networks")) {
                val item =
                    api.userLibraryApi.getItem(itemId = itemId).content.let {
                        BaseItem(it, false)
                    }
                this@NetworkViewModel.item.setValueOnMain(item)
                withContext(Dispatchers.Main) {
                    this@NetworkViewModel.networks.value = PopularNetworks
                    loading.value = LoadingState.Success
                }
            }
        }

        suspend fun positionOfLetter(letter: Char): Int =
            withContext(Dispatchers.Default) {
                val target = letter.uppercaseChar()
                networks.value.orEmpty().count {
                    it.name.firstOrNull()?.uppercaseChar()?.let { first -> first < target } == true
                }
            }
    }

@Stable
data class Network(
    val id: UUID,
    val name: String,
    val aliases: List<String>,
    val imageUrl: String?,
) : CardGridItem {
    override val gridId: String get() = id.toString()
    override val playable: Boolean = false
    override val sortName: String get() = name
}

fun networkLogoAsset(fileName: String): String = "file:///android_asset/network_logos/$fileName"

fun networkLogoAssetForKey(key: String): String? =
    key
        .takeIf { it in NetworkLogoAssetKeys }
        ?.let { networkLogoAsset("$it.svg") }

private val NetworkLogoAssetKeys =
    setOf(
        "aande",
        "abc",
        "acorntv",
        "adultswim",
        "amc",
        "animalplanet",
        "anime",
        "appletv",
        "appletvplus",
        "atx",
        "bbc",
        "bet",
        "bravo",
        "britbox",
        "canalplus",
        "cartoonnetwork",
        "cbs",
        "cbsallaccess",
        "cbbc",
        "cbctelevision",
        "channel4",
        "channel5",
        "cinemax",
        "comedycentral",
        "crave",
        "crunchyroll",
        "dave",
        "dcuniverse",
        "discoveryplus",
        "disneychannel",
        "disneyplus",
        "disneyxd",
        "e",
        "foodnetwork",
        "fox",
        "freeform",
        "fujitv",
        "funimation",
        "fx",
        "fxx",
        "hallmarkchannel",
        "hbo",
        "hgtv",
        "hidive",
        "history",
        "hulu",
        "ifc",
        "itv",
        "itunesstore",
        "lifetime",
        "mbs",
        "mgmplus",
        "movieaction",
        "movieadventure",
        "movieanimation",
        "moviecomedy",
        "moviecrime",
        "moviedocumentary",
        "moviedrama",
        "moviefamily",
        "moviefantasy",
        "moviehorror",
        "moviemystery",
        "movieromance",
        "moviescifi",
        "moviethriller",
        "moviewar",
        "moviewestern",
        "mtv",
        "nationalgeographic",
        "nbc",
        "netflix",
        "nhk",
        "nickelodeon",
        "nickjr",
        "nippontv",
        "own",
        "oxygen",
        "paramountplus",
        "pbs",
        "pbskids",
        "peacock",
        "plutotv",
        "primevideo",
        "quibi",
        "rt",
        "showtime",
        "science",
        "sky",
        "skyatlantic",
        "skyarts",
        "smithsonianchannel",
        "space",
        "spike",
        "starz",
        "sundancetv",
        "syfy",
        "syndication",
        "tbs",
        "teletama",
        "thecw",
        "thewb",
        "therokuchannel",
        "tlc",
        "tnt",
        "tokyomx",
        "toonami",
        "travelchannel",
        "tubi",
        "tvasahi",
        "tvland",
        "tvtokyo",
        "tvn",
        "uktv",
        "usanetwork",
        "vh1",
        "virginmedia",
        "wetv",
        "wowow",
        "youtubepremium",
    )

private fun network(
    name: String,
    vararg aliases: String,
    imageUrl: String? = null,
) = Network(
    id = networkIdFor(name),
    name = name,
    aliases = (listOf(name) + aliases).distinctBy { it.normalizedNetworkName() },
    imageUrl = networkLogoAssetForKey(name.normalizedNetworkLogoKey()) ?: imageUrl,
)

private fun String.normalizedNetworkName(): String = lowercase(Locale.ROOT)

private fun String.normalizedNetworkLogoKey(): String =
    trim()
        .lowercase(Locale.ROOT)
        .replace("&", "and")
        .replace("+", "plus")
        .replace(Regex("[^a-z0-9]+"), "")

private fun networkIdFor(name: String): UUID =
    UUID.nameUUIDFromBytes("network:${name.normalizedNetworkName()}".toByteArray())

val PopularNetworks =
    listOf(
        network(
            "Netflix",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/wwemzKWzjKYJFfCeiB57q3r4Bcm.png",
        ),
        network(
            "Disney+",
            "Disney Plus",
            "DisneyPlus",
            "Disney",
            "Walt Disney Pictures",
            "Walt Disney Animation Studios",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/gJ8VX6JSu3ciXHuC2dDGAo2lvwM.png",
        ),
        network(
            "Prime Video",
            "Amazon Prime Video",
            "Amazon Prime",
            "Amazon",
            "Amazon Studios",
            "Prime",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ifhbNuuVnlwYy5oXA5VIb2YR8AZ.png",
        ),
        network(
            "Apple TV+",
            "Apple TV Plus",
            "Apple",
            "Apple Studios",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/4KAy34EHvRM25Ih8wb82AuGU7zJ.png",
        ),
        network(
            "Apple TV",
            "Apple TV Channels",
        ),
        network(
            "iTunes Store",
            "iTunes",
            "Apple iTunes",
        ),
        network(
            "Quibi",
        ),
        network(
            "Hulu",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/pqUTCleNUiTLAVlelGxUgWn1ELh.png",
        ),
        network(
            "HBO",
            "HBO Max",
            "Max",
            "Home Box Office",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/tuomPhY2UtuPTqqFnKMVHvSb724.png",
        ),
        network(
            "DC Universe",
            "DC",
            "DC Entertainment",
            "DC Comics",
            "DC Studios",
        ),
        network(
            "Paramount+",
            "Paramount Plus",
            "Paramount",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/fi83B1oztoS47xxcemFdPMhIzK.png",
        ),
        network(
            "Peacock",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/gIAcGTjKKr0KOHL5s4O36roJ8p7.png",
        ),
        network(
            "Discovery+",
            "Discovery Plus",
            "Discovery",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1D1bS3Dyw4ScYnFWTlBOvJXC3nb.png",
        ),
        network(
            "ABC",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ndAvF4JLsliGreX87jAc9GdjmJY.png",
        ),
        network(
            "NBC",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/o3OedEP0f9mfZr33jz2BfXOUK5.png",
        ),
        network(
            "CBS",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/nm8d7P7MJNiBLdgIzUK0gkuEA4r.png",
        ),
        network(
            "CBS All Access",
            "CBS All Access Originals",
        ),
        network(
            "FOX",
            "Fox",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1DSpHrWyOORkL9N2QHX7Adt31mQ.png",
        ),
        network(
            "FX",
            "FX Networks",
            "FX Productions",
            imageUrl = "https://logo.clearbit.com/fxnetworks.com",
        ),
        network(
            "AMC",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/pmvRmATOCaDykE6JrVoeYxlFHw3.png",
        ),
        network(
            "Showtime",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/Allse9kbjiP6ExaQrnSpIhkurEi.png",
        ),
        network(
            "Starz",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8GJjw3HHsAJYwIWKIPBPfqMxlEa.png",
        ),
        network(
            "Cinemax",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/6mSHSquNpfLgDdv6VnOOvC5Uz2h.png",
        ),
        network(
            "The CW",
            "CW",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ge9hzeaU7nMtQ4PjkFlc68dGAJ9.png",
        ),
        network(
            "The WB",
            "WB",
            "WB Network",
            "The WB Television Network",
        ),
        network(
            "Syndication",
            "Syndicated",
            "Broadcast Syndication",
            "First-run Syndication",
            "First Run Syndication",
            "Off-network Syndication",
        ),
        network(
            "BBC",
            "BBC One",
            "BBC Two",
            "BBC Three",
            "BBC America",
            imageUrl = "https://logo.clearbit.com/bbc.com",
        ),
        network(
            "CBBC",
            "Children's BBC",
        ),
        network(
            "CBC Television",
            "CBC",
            "CBC TV",
            "CBC Network",
            "Canadian Broadcasting Corporation",
        ),
        network(
            "ITV",
            "ITV1",
            "ITV2",
            "ITV Studios",
            imageUrl = "https://logo.clearbit.com/itv.com",
        ),
        network(
            "Channel 4",
            "Channel Four",
            "E4",
            "Film4",
            imageUrl = "https://logo.clearbit.com/channel4.com",
        ),
        network(
            "PBS",
            imageUrl = "https://logo.clearbit.com/pbs.org",
        ),
        network(
            "PBS Kids",
            "PBS KIDS",
        ),
        network(
            "Cartoon Network",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/c5OC6oVCg6QP4eqzW6XIq17CQjI.png",
        ),
        network(
            "Adult Swim",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/9AKyspxVzywuaMuZ1Bvilu8sXly.png",
        ),
        network(
            "Nickelodeon",
            "Nick",
            imageUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ikZXxg6GnwpzqiZbRPhJGaZapqB.png",
        ),
        network(
            "Comedy Central",
            imageUrl = "https://logo.clearbit.com/comedycentral.com",
        ),
        network(
            "MTV",
            imageUrl = "https://logo.clearbit.com/mtv.com",
        ),
        network(
            "Spike",
            "Spike TV",
        ),
        network(
            "Syfy",
            "Sci-Fi Channel",
            imageUrl = "https://logo.clearbit.com/syfy.com",
        ),
        network(
            "USA Network",
            "USA",
            imageUrl = "https://logo.clearbit.com/usanetwork.com",
        ),
        network(
            "National Geographic",
            "Nat Geo",
            imageUrl = "https://logo.clearbit.com/nationalgeographic.com",
        ),
        network(
            "Science",
            "Science Channel",
            "Discovery Science",
            "DScience",
            "DCIENCE",
        ),
        network(
            "History",
            "History Channel",
            imageUrl = "https://logo.clearbit.com/history.com",
        ),
        network(
            "TLC",
            imageUrl = "https://logo.clearbit.com/tlc.com",
        ),
        network(
            "A&E",
            "A and E",
            "A+E",
            "A&E Networks",
            imageUrl = "https://logo.clearbit.com/aetv.com",
        ),
        network(
            "Bravo",
            imageUrl = "https://logo.clearbit.com/bravotv.com",
        ),
        network(
            "E!",
            "E Entertainment",
            imageUrl = "https://logo.clearbit.com/eonline.com",
        ),
        network(
            "Freeform",
            "ABC Family",
            imageUrl = "https://logo.clearbit.com/freeform.com",
        ),
        network(
            "FXX",
            imageUrl = "https://logo.clearbit.com/fxx.com",
        ),
        network(
            "TNT",
            imageUrl = "https://logo.clearbit.com/tntdrama.com",
        ),
        network(
            "TBS",
            imageUrl = "https://logo.clearbit.com/tbs.com",
        ),
        network(
            "Lifetime",
            imageUrl = "https://logo.clearbit.com/lifetime.com",
        ),
        network(
            "Hallmark Channel",
            "Hallmark",
            imageUrl = "https://logo.clearbit.com/hallmarkchannel.com",
        ),
        network(
            "BET",
            imageUrl = "https://logo.clearbit.com/bet.com",
        ),
        network(
            "VH1",
            imageUrl = "https://logo.clearbit.com/vh1.com",
        ),
        network(
            "TV Land",
            imageUrl = "https://logo.clearbit.com/tvland.com",
        ),
        network(
            "IFC",
            imageUrl = "https://logo.clearbit.com/ifc.com",
        ),
        network(
            "SundanceTV",
            "Sundance TV",
            imageUrl = "https://logo.clearbit.com/sundancetv.com",
        ),
        network(
            "WE tv",
            "WE",
            imageUrl = "https://logo.clearbit.com/wetv.com",
        ),
        network(
            "Oxygen",
            imageUrl = "https://logo.clearbit.com/oxygen.com",
        ),
        network(
            "Food Network",
            imageUrl = "https://logo.clearbit.com/foodnetwork.com",
        ),
        network(
            "HGTV",
            imageUrl = "https://logo.clearbit.com/hgtv.com",
        ),
        network(
            "Travel Channel",
            imageUrl = "https://logo.clearbit.com/travelchannel.com",
        ),
        network(
            "Animal Planet",
            imageUrl = "https://logo.clearbit.com/animalplanet.com",
        ),
        network(
            "MGM+",
            "Epix",
            "MGM Plus",
            imageUrl = "https://logo.clearbit.com/mgmplus.com",
        ),
        network(
            "Smithsonian Channel",
            imageUrl = "https://logo.clearbit.com/smithsonianchannel.com",
        ),
        network(
            "OWN",
            "Oprah Winfrey Network",
            imageUrl = "https://logo.clearbit.com/oprah.com",
        ),
        network(
            "Disney Channel",
            imageUrl = "https://logo.clearbit.com/disneychannel.disney.com",
        ),
        network(
            "Disney XD",
            imageUrl = "https://logo.clearbit.com/disneyxd.disney.com",
        ),
        network(
            "Nick Jr.",
            "Nick Jr",
            imageUrl = "https://logo.clearbit.com/nickjr.com",
        ),
        network(
            "Acorn TV",
            imageUrl = "https://logo.clearbit.com/acorn.tv",
        ),
        network(
            "BritBox",
            imageUrl = "https://logo.clearbit.com/britbox.com",
        ),
        network(
            "Sky",
            "Sky One",
            "Sky Max",
            "Sky Showcase",
            imageUrl = "https://logo.clearbit.com/sky.com",
        ),
        network(
            "Sky Atlantic",
            imageUrl = "https://logo.clearbit.com/sky.com",
        ),
        network(
            "Sky Arts",
            "SkyArts",
        ),
        network(
            "Channel 5",
            "Five",
            "5",
            imageUrl = "https://logo.clearbit.com/channel5.com",
        ),
        network(
            "Dave",
            "UKTV Dave",
            imageUrl = "https://logo.clearbit.com/dave.uktv.co.uk",
        ),
        network(
            "UKTV",
            "Gold",
            "W",
            "Drama",
            imageUrl = "https://logo.clearbit.com/uktv.co.uk",
        ),
        network(
            "RTÉ",
            "RTE",
            "RTÉ One",
            "RTE One",
            "RTÉ2",
            "RTE2",
            imageUrl = "https://logo.clearbit.com/rte.ie",
        ),
        network(
            "Virgin Media",
            "Virgin Media One",
            "Virgin Media Two",
            "Virgin Media Three",
            imageUrl = "https://logo.clearbit.com/virginmediatelevision.ie",
        ),
        network(
            "Crunchyroll",
            imageUrl = "https://logo.clearbit.com/crunchyroll.com",
        ),
        network(
            "Anime",
            "Anime Channel",
        ),
        network(
            "Funimation",
        ),
        network(
            "HIDIVE",
        ),
        network(
            "Toonami",
        ),
        network(
            "TV Tokyo",
            "TV Tokyo Corporation",
            "TX Network",
            "TXN",
        ),
        network(
            "Tokyo MX",
            "TOKYO MX",
            "Tokyo Metropolitan Television",
        ),
        network(
            "Teletama",
            "Tele Tama",
            "TV Saitama",
            "Television Saitama",
            "Terebi Saitama",
        ),
        network(
            "Fuji TV",
            "Fuji Television",
            "Fuji Television Network",
            "Fuji TV Network",
        ),
        network(
            "TV Asahi",
            "Asahi TV",
            "TV Asahi Corporation",
            "ANN",
        ),
        network(
            "Nippon TV",
            "Nippon Television",
            "NTV",
        ),
        network(
            "NHK",
            "NHK General TV",
            "NHK Educational TV",
            "NHK E",
            "NHK G",
            "NHK BS",
            "NHK BS1",
            "NHK BS2",
            "NHK BS Premium",
            "NHK BSP",
            "NHK World",
            "NHK World-Japan",
            "NHK World Japan",
            "NHK World Premium",
            "NHK TV",
        ),
        network(
            "MBS",
            "Mainichi Broadcasting System",
            "MBS TV",
        ),
        network(
            "AT-X",
            "ATX",
        ),
        network(
            "WOWOW",
        ),
        network(
            "Canal+",
            "Canal",
            "CANAL+",
            "Canal Plus",
            "Canal Plus Original",
            "Canal+ Original",
        ),
        network(
            "Crave",
            "CraveTV",
            "Crave TV",
        ),
        network(
            "Space",
            "Space Channel",
            "CTV Sci-Fi",
            "CTV Sci-Fi Channel",
        ),
        network(
            "TVN",
        ),
        network(
            "YouTube Premium",
            "YouTube Originals",
            "YouTube",
            imageUrl = "https://logo.clearbit.com/youtube.com",
        ),
        network(
            "Tubi",
            imageUrl = "https://logo.clearbit.com/tubi.tv",
        ),
        network(
            "Pluto TV",
            imageUrl = "https://logo.clearbit.com/pluto.tv",
        ),
        network(
            "The Roku Channel",
            "Roku",
            imageUrl = "https://logo.clearbit.com/roku.com",
        ),
    )

@Composable
private fun NetworkCard(
    network: Network?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val background = rememberIdColor(network?.id).copy(alpha = .72f)
    var imageError by remember(network?.imageUrl) { mutableStateOf(false) }
    Card(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        colors =
            CardDefaults.colors(
                containerColor = Color.Transparent,
            ),
        scale = CardDefaults.scale(focusedScale = 1.03f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .aspectRatio(AspectRatios.WIDE)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(background),
        ) {
            val imageUrl = network?.imageUrl
            if (imageUrl != null && !imageError) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                    contentScale = ContentScale.Fit,
                    contentDescription = null,
                    onError = {
                        imageError = true
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                            .alpha(.92f),
                )
            } else {
                Text(
                    text = network?.name.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier =
                        Modifier
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
fun NetworkCardGrid(
    itemId: UUID,
    includeItemTypes: List<BaseItemKind>?,
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel =
        hiltViewModel<NetworkViewModel, NetworkViewModel.Factory>(
            creationCallback = { it.create(itemId, includeItemTypes) },
        ),
) {
    val columns = 4
    val spacing = 16.dp
    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val networks by viewModel.networks.observeAsState(listOf())

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
                    pager = networks,
                    onClickItem = { _, network ->
                        viewModel.navigationManager.navigateTo(
                            createNetworkDestination(
                                networkName = network.name,
                                networkAliases = network.aliases,
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
                    cardContent = { item: Network?, onClick: () -> Unit, onLongClick: () -> Unit, mod: Modifier ->
                        NetworkCard(
                            network = item,
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
