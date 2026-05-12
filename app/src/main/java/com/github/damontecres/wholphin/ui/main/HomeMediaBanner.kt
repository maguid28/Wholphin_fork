package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.getDateFormatter
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.logCoilError
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.seriesProductionYears
import com.github.damontecres.wholphin.ui.timeRemaining
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration

private const val AutoAdvanceMillis = 12_000L
private const val BannerItemImageTransitionMillis = 720
private const val BannerItemTextTransitionMillis = 560
private val BannerHeight = 235.dp
private val BannerShape = RoundedCornerShape(8.dp)
private val CompactLogoHeight = 64.dp
private val CompactLogoMaxWidth = 360.dp
private val FullScreenLogoHeight = 128.dp
private val FullScreenLogoMaxWidth = 680.dp
private const val FullScreenBackdropMaxWidth = 1_280
private const val FullScreenBackdropMaxHeight = 720
private const val CompactLogoImageMaxWidth = 520
private const val CompactLogoImageMaxHeight = 112
private const val FullScreenLogoImageMaxWidth = 960
private const val FullScreenLogoImageMaxHeight = 192

@Composable
fun HomeMediaBanner(
    items: List<BaseItem>,
    audienceScores: Map<UUID, Float>,
    showLogo: Boolean,
    onFocusedItem: (BaseItem?) -> Unit,
    onClickItem: (BaseItem) -> Unit,
    onHoldItem: (BaseItem) -> Unit,
    onClickPlay: (BaseItem) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
    active: Boolean = true,
    fullScreenContentStartPadding: Dp = 48.dp,
    onMoveDown: () -> Boolean = { false },
) {
    if (items.isEmpty()) return
    if (!active) return

    var currentIndex by remember(items) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    var selectHoldHandled by remember { mutableStateOf(false) }
    val currentItem = items[currentIndex.coerceIn(items.indices)]
    val bannerShape = if (fullScreen) RoundedCornerShape(0.dp) else BannerShape
    val focusBorderModifier =
        if (fullScreen) {
            Modifier
        } else {
            Modifier.border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = bannerShape,
            )
        }
    val sizeModifier =
        if (fullScreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .height(BannerHeight)
        }

    LaunchedEffect(items.size) {
        if (currentIndex !in items.indices) currentIndex = 0
    }

    LaunchedEffect(items, currentIndex, active) {
        if (!active || items.size <= 1) return@LaunchedEffect

        delay(AutoAdvanceMillis)
        currentIndex = (currentIndex + 1) % items.size
    }

    LaunchedEffect(focused, currentItem, active) {
        if (active && focused) onFocusedItem(currentItem)
    }

    LaunchedEffect(currentItem) {
        selectHoldHandled = false
    }

    Box(
        modifier =
            modifier
                .then(sizeModifier)
                .clip(bannerShape)
                .background(Color.Black)
                .then(focusBorderModifier)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.hasFocus
                    if (!it.hasFocus) onFocusedItem(null)
                }.onKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (event.type == KeyEventType.KeyDown) {
                                currentIndex = if (currentIndex == 0) items.lastIndex else currentIndex - 1
                            }
                            true
                        }

                        Key.DirectionRight -> {
                            if (event.type == KeyEventType.KeyDown) {
                                currentIndex = (currentIndex + 1) % items.size
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown) onMoveDown() else true
                        }

                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.ButtonSelect,
                        Key.ButtonA,
                        -> {
                            when (event.type) {
                                KeyEventType.KeyDown -> {
                                    val isHold =
                                        event.nativeKeyEvent.isLongPress ||
                                            event.nativeKeyEvent.repeatCount > 0
                                    if (isHold && !selectHoldHandled) {
                                        selectHoldHandled = true
                                        onHoldItem(currentItem)
                                    }
                                }

                                KeyEventType.KeyUp -> {
                                    if (!selectHoldHandled) {
                                        onClickItem(currentItem)
                                    }
                                    selectHoldHandled = false
                                }

                                else -> {}
                            }
                            true
                        }

                        Key.MediaPlay,
                        Key.MediaPlayPause,
                        -> {
                            if (currentItem.type.playable) {
                                if (event.type == KeyEventType.KeyDown) onClickPlay(currentItem)
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                }.focusable(),
    ) {
        AnimatedContent(
            targetState = currentItem,
            label = "home_media_banner_backdrop",
            transitionSpec = {
                fadeIn(
                    animationSpec =
                        tween(
                            durationMillis = BannerItemImageTransitionMillis,
                            easing = FastOutSlowInEasing,
                        ),
                ).togetherWith(
                    fadeOut(
                        animationSpec =
                            tween(
                                durationMillis = BannerItemImageTransitionMillis,
                                easing = FastOutSlowInEasing,
                            ),
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { targetItem ->
            HomeMediaBannerBackdrop(
                item = targetItem,
                fullScreen = fullScreen,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = .86f),
                                    Color.Black.copy(alpha = .55f),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = .7f),
                                ),
                        ),
                    ),
        )

        AnimatedContent(
            targetState = currentItem,
            label = "home_media_banner_content",
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec =
                            tween(
                                durationMillis = BannerItemTextTransitionMillis,
                                delayMillis = 120,
                                easing = FastOutSlowInEasing,
                            ),
                    ) + slideInVertically(
                        animationSpec =
                            tween(
                                durationMillis = BannerItemTextTransitionMillis,
                                delayMillis = 80,
                                easing = FastOutSlowInEasing,
                            ),
                        initialOffsetY = { it / 10 },
                    )
                ).togetherWith(
                    fadeOut(
                        animationSpec =
                            tween(
                                durationMillis = 260,
                                easing = FastOutSlowInEasing,
                            ),
                    ) + slideOutVertically(
                        animationSpec =
                            tween(
                                durationMillis = 260,
                                easing = FastOutSlowInEasing,
                            ),
                        targetOffsetY = { -it / 14 },
                    ),
                )
            },
            modifier =
                Modifier
                    .align(if (fullScreen) Alignment.CenterStart else Alignment.BottomStart)
                    .fillMaxHeight(if (fullScreen) .84f else 1f)
                    .fillMaxWidth(if (fullScreen) .62f else .58f)
                    .padding(
                        start = if (fullScreen) fullScreenContentStartPadding else 32.dp,
                        top = if (fullScreen) 48.dp else 24.dp,
                        end = 20.dp,
                        bottom = if (fullScreen) 72.dp else 24.dp,
                    ),
        ) { targetItem ->
            HomeMediaBannerContent(
                item = targetItem,
                rottenTomatoesAudienceScore = audienceScores[targetItem.id],
                showLogo = showLogo,
                fullScreen = fullScreen,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HomeMediaBannerBackdrop(
    item: BaseItem,
    fullScreen: Boolean,
) {
    val imageUrlService = LocalImageUrlService.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val maxImageWidth = if (fullScreen) FullScreenBackdropMaxWidth else Int.MAX_VALUE
    val maxImageHeight = if (fullScreen) FullScreenBackdropMaxHeight else Int.MAX_VALUE
    val backdropUrl =
        remember(item, size) {
            val fillWidth = size.width.takeIf { it > 0 }?.coerceAtMost(maxImageWidth)
            val fillHeight = size.height.takeIf { it > 0 }?.coerceAtMost(maxImageHeight)
            imageUrlService.getItemImageUrl(
                item = item,
                imageType = ImageType.BACKDROP,
                fillWidth = fillWidth,
                fillHeight = fillHeight,
            ) ?: imageUrlService.getItemImageUrl(
                item = item,
                imageType = ImageType.THUMB,
                fillWidth = fillWidth,
                fillHeight = fillHeight,
            )
        }
    var imageError by remember(backdropUrl) { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { size = it },
    ) {
        if (!imageError && backdropUrl.isNotNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                onError = {
                    logCoilError(backdropUrl, it.result)
                    imageError = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HomeMediaBannerContent(
    item: BaseItem,
    rottenTomatoesAudienceScore: Float?,
    showLogo: Boolean,
    fullScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val bannerDetails =
        remember(item, rottenTomatoesAudienceScore) {
            item.bannerDetailsWithoutParentalRating(rottenTomatoesAudienceScore)
        }

    Column(
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier,
    ) {
        HomeMediaBannerTitle(
            item = item,
            showLogo = showLogo,
            fullScreen = fullScreen,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
        )
        if (bannerDetails.text.isNotBlank()) {
            QuickDetails(
                details = bannerDetails,
                timeRemaining = null,
                textStyle = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = .92f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item.data.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = .86f),
                maxLines = if (fullScreen) 9 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (fullScreen) 900.dp else 520.dp),
            )
        }
        if (item.type.playable) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = item.title ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeMediaBannerTitle(
    item: BaseItem,
    showLogo: Boolean,
    fullScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val logoUrl =
        rememberLogoUrl(
            item = item,
            maxWidth = if (fullScreen) FullScreenLogoImageMaxWidth else CompactLogoImageMaxWidth,
            maxHeight = if (fullScreen) FullScreenLogoImageMaxHeight else CompactLogoImageMaxHeight,
        )
    var imageError by remember(logoUrl) { mutableStateOf(false) }
    val logoHeight = if (fullScreen) FullScreenLogoHeight else CompactLogoHeight
    val logoMaxWidth = if (fullScreen) FullScreenLogoMaxWidth else CompactLogoMaxWidth

    if (showLogo && logoUrl != null && !imageError) {
        AsyncImage(
            model = logoUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            onError = { imageError = true },
            modifier =
                modifier
                    .height(logoHeight)
                    .widthIn(max = logoMaxWidth),
        )
    } else {
        Text(
            text = item.title ?: "",
            style = if (fullScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

private fun BaseItem.bannerDetailsWithoutParentalRating(rottenTomatoesAudienceScore: Float?): AnnotatedString =
    buildAnnotatedString {
        val details =
            buildList {
                if (type == BaseItemKind.EPISODE) {
                    data.seasonEpisode?.let(::add)
                    data.premiereDate?.let { add(getDateFormatter().format(it)) }
                } else if (type == BaseItemKind.SERIES) {
                    data.seriesProductionYears?.let(::add)
                } else if (type == BaseItemKind.PHOTO) {
                    data.productionYear?.let { add(it.toString()) }
                        ?: data.premiereDate?.let { add(it.toLocalDate().toString()) }
                } else if (type == BaseItemKind.BOX_SET) {
                    data.productionYear?.let { add(it.toString()) }
                    data.childCount?.let { add("$it items") }
                } else {
                    data.productionYear?.let { add(it.toString()) }
                }
                data.runTimeTicks
                    ?.ticks
                    ?.takeIf { it > Duration.ZERO }
                    ?.roundMinutes
                    ?.let { add(it.toString()) }
                data.timeRemaining
                    ?.takeIf { it > Duration.ZERO }
                    ?.roundMinutes
                    ?.let { add("$it left") }
            }
        details.forEachIndexed { index, detail ->
            append(detail)
            if (index != details.lastIndex) dot()
        }
        data.communityRating?.let {
            dot()
            append(String.format(Locale.getDefault(), "%.1f", it))
            appendInlineContent(id = "star")
        }
        rottenTomatoesAudienceScore?.takeIf { it > 0f }?.let {
            dot()
            append("RT Audience ${it.roundToInt()}%")
        }
    }
