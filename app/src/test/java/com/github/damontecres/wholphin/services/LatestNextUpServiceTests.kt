@file:UseSerializers(
    UUIDSerializer::class,
    DateTimeSerializer::class,
)

package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.TvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import org.jellyfin.sdk.model.api.ScrollDirection
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.serializer.DateTimeSerializer
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class LatestNextUpServiceTests {
    private val testDispatcher = StandardTestDispatcher()

    private val userId = UUID.randomUUID()
    private val seriesId1 = UUID.randomUUID()
    private val seriesId2 = UUID.randomUUID()
    private val seriesId3 = UUID.randomUUID()

    private val mockApi = mockk<ApiClient>(relaxed = true)
    private val mockTvShowsApi = mockk<TvShowsApi>()
    private val mockItemsApi = mockk<ItemsApi>()
    private val mockDatePlayedService = mockk<DatePlayedService>()
    private val mockDisplayPreferencesService = mockk<DisplayPreferencesService>()
    private val mockFavoriteWatchManager = mockk<FavoriteWatchManager>(relaxed = true)

    private val latestNextUpService =
        LatestNextUpService(mockApi, mockDatePlayedService, mockDisplayPreferencesService, mockFavoriteWatchManager)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockApi.tvShowsApi } returns mockTvShowsApi
        every { mockApi.itemsApi } returns mockItemsApi
        coEvery { mockDisplayPreferencesService.getDisplayPreferences(any(), any(), any()) } returns
            buildRemoved()
        coEvery { mockDatePlayedService.getLastPlayed(any()) } coAnswers {
            (args[0] as BaseItem).data.userData?.lastPlayedDate
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Test nothing is filtered out`() =
        runTest {
            coEvery { mockTvShowsApi.getNextUp(any() as GetNextUpRequest) } returns mockResponse
            coEvery { mockDisplayPreferencesService.getDisplayPreferences(any(), any(), any()) } returns
                buildRemoved()
            val result = latestNextUpService.getNextUp(userId, 20, false, false, 0)
            Assert.assertEquals(3, result.size)
            val seriesIds = result.map { it.data.seriesId }
            Assert.assertTrue(seriesIds.containsAll(listOf(seriesId1, seriesId2, seriesId3)))
        }

    @Test
    fun `Test seriesId1 is filtered out`() =
        runTest {
            coEvery { mockTvShowsApi.getNextUp(any() as GetNextUpRequest) } returns mockResponse
            coEvery { mockDisplayPreferencesService.getDisplayPreferences(any()) } returns
                buildRemoved(seriesId1 to LocalDateTime.now().minusDays(1))
            val result = latestNextUpService.getNextUp(userId, 20, false, false, 0)
            Assert.assertEquals(2, result.size)
            val seriesIds = result.map { it.data.seriesId }
            Assert.assertTrue(seriesId1 !in seriesIds)
            Assert.assertTrue(seriesIds.containsAll(listOf(seriesId2, seriesId3)))
        }

    @Test
    fun `Test seriesId2 is filtered out`() =
        runTest {
            coEvery { mockTvShowsApi.getNextUp(any() as GetNextUpRequest) } returns mockResponse
            coEvery { mockDisplayPreferencesService.getDisplayPreferences(any()) } returns
                buildRemoved(seriesId2 to LocalDateTime.now().minusDays(1))
            val result = latestNextUpService.getNextUp(userId, 20, false, false, 0)
            Assert.assertEquals(2, result.size)
            val seriesIds = result.map { it.data.seriesId }
            Assert.assertTrue(seriesId2 !in seriesIds)
            Assert.assertTrue(seriesIds.containsAll(listOf(seriesId1, seriesId3)))
        }

    @Test
    fun `Test seriesId1 and seriesId2 are filtered out`() =
        runTest {
            coEvery { mockTvShowsApi.getNextUp(any() as GetNextUpRequest) } returns mockResponse
            coEvery { mockDisplayPreferencesService.getDisplayPreferences(any()) } returns
                buildRemoved(
                    seriesId1 to LocalDateTime.now().minusDays(1),
                    seriesId2 to LocalDateTime.now().minusDays(1),
                )
            val result = latestNextUpService.getNextUp(userId, 20, false, false, 0)
            Assert.assertEquals(1, result.size)
            val seriesIds = result.map { it.data.seriesId }
            Assert.assertTrue(seriesId1 !in seriesIds)
            Assert.assertTrue(seriesId2 !in seriesIds)
            Assert.assertTrue(seriesIds.containsAll(listOf(seriesId3)))
        }

    @Test
    fun `Combined row includes next up when resume already fills the page`() =
        runTest {
            val resumeItems =
                List(10) { index ->
                    mockItemDto(
                        lastPlayed = LocalDateTime.now().minusDays(index.toLong() + 5),
                    )
                }
            val nextUpItem =
                mockItemDto(
                    seriesId = UUID.randomUUID(),
                    lastPlayed = LocalDateTime.now(),
                )
            stubResumeAndNextUp(resumeItems, listOf(nextUpItem))

            val (items, hasMore) =
                latestNextUpService.fetchCombinedContinueWatching(
                    userId = userId,
                    limit = 10,
                    startIndex = 0,
                    includeEpisodes = true,
                    enableRewatching = false,
                    enableResumable = false,
                    maxDays = 0,
                )

            Assert.assertTrue(items.any { it.id == nextUpItem.id })
            Assert.assertEquals(nextUpItem.id, items.first().id)
            Assert.assertEquals(11, items.size)
            Assert.assertFalse(hasMore)
        }

    @Test
    fun `Combined row paginates merged next up instead of resume only`() =
        runTest {
            val resumeItems =
                List(10) { index ->
                    mockItemDto(
                        lastPlayed = LocalDateTime.now().minusHours(index.toLong() + 1),
                    )
                }
            val nextUpItems =
                List(5) { index ->
                    mockItemDto(
                        seriesId = UUID.randomUUID(),
                        lastPlayed = LocalDateTime.now().minusDays(index.toLong() + 2),
                    )
                }
            stubResumeAndNextUp(resumeItems, nextUpItems)

            val (firstPage, firstHasMore) =
                latestNextUpService.fetchCombinedContinueWatching(
                    userId = userId,
                    limit = 10,
                    startIndex = 0,
                    includeEpisodes = true,
                    enableRewatching = false,
                    enableResumable = false,
                    maxDays = 0,
                )
            val (secondPage, secondHasMore) =
                latestNextUpService.fetchCombinedContinueWatching(
                    userId = userId,
                    limit = 10,
                    startIndex = firstPage.size,
                    includeEpisodes = true,
                    enableRewatching = false,
                    enableResumable = false,
                    maxDays = 0,
                )

            Assert.assertFalse(firstHasMore)
            Assert.assertEquals(15, firstPage.size)
            Assert.assertTrue(firstPage.any { item -> nextUpItems.any { it.id == item.id } })
            Assert.assertTrue(firstPage.any { item -> resumeItems.any { it.id == item.id } })
            Assert.assertTrue(secondPage.isEmpty())
            Assert.assertFalse(secondHasMore)
        }

    @Test
    fun `Next up items already in resume are not duplicated`() {
        val sharedSeries = UUID.randomUUID()
        val resume =
            listOf(
                BaseItem(mockItemDto(seriesId = sharedSeries)),
                BaseItem(mockItemDto()),
            )
        val nextUp =
            listOf(
                BaseItem(mockItemDto(seriesId = sharedSeries)),
                BaseItem(mockItemDto(id = resume[1].id)),
                BaseItem(mockItemDto(seriesId = UUID.randomUUID())),
            )

        val unique = LatestNextUpService.nextUpItemsNotInResume(resume, nextUp)

        Assert.assertEquals(1, unique.size)
        Assert.assertEquals(nextUp[2].id, unique.single().id)
    }

    fun buildRemoved(vararg values: Pair<UUID, LocalDateTime>): DisplayPreferencesDto =
        testDisplayPreferencesDto.copy(
            customPrefs =
                mutableMapOf<String, String?>().apply {
                    val str = Json.encodeToString(RemovedSeriesIds(values.toMap()))
                    put(LatestNextUpService.REMOVED_KEY, str)
                },
        )

    private val testUserItemDataDto =
        UserItemDataDto(
            playbackPositionTicks = 0L,
            playCount = 0,
            isFavorite = false,
            lastPlayedDate = null,
            played = false,
            key = "",
            itemId = UUID.randomUUID(),
        )

    private fun mockItemDto(
        id: UUID = UUID.randomUUID(),
        seriesId: UUID? = null,
        lastPlayed: LocalDateTime? = null,
    ): BaseItemDto =
        mockk(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.seriesId } returns seriesId
            every { userData } returns testUserItemDataDto.copy(lastPlayedDate = lastPlayed, itemId = id)
        }

    private fun stubResumeAndNextUp(
        resumeItems: List<BaseItemDto>,
        nextUpItems: List<BaseItemDto>,
    ) {
        coEvery { mockItemsApi.getResumeItems(any() as GetResumeItemsRequest) } returns
            queryResponse(resumeItems)
        coEvery { mockTvShowsApi.getNextUp(any() as GetNextUpRequest) } returns
            queryResponse(nextUpItems)
    }

    private fun queryResponse(items: List<BaseItemDto>): Response<BaseItemDtoQueryResult> =
        Response(
            content =
                BaseItemDtoQueryResult(
                    items = items,
                    totalRecordCount = items.size,
                    startIndex = 0,
                ),
            status = 200,
            headers = mapOf(),
        )

    private val mockResponse: Response<BaseItemDtoQueryResult> =
        Response<BaseItemDtoQueryResult>(
            content =
                BaseItemDtoQueryResult(
                    items =
                        listOf(
                            mockk<BaseItemDto>(relaxed = true) {
                                every { seriesId } returns seriesId1
                                every { userData } returns testUserItemDataDto.copy(lastPlayedDate = LocalDateTime.now().minusDays(7))
                            },
                            mockk<BaseItemDto>(relaxed = true) {
                                every { seriesId } returns seriesId2
                                every { userData } returns testUserItemDataDto.copy(lastPlayedDate = null)
                            },
                            mockk<BaseItemDto>(relaxed = true) {
                                every { seriesId } returns seriesId3
                                every { userData } returns testUserItemDataDto.copy(lastPlayedDate = LocalDateTime.now().plusDays(7))
                            },
                        ),
                    totalRecordCount = 3,
                    startIndex = 0,
                ),
            status = 200,
            headers = mapOf(),
        )
}

val testDisplayPreferencesDto =
    DisplayPreferencesDto(
        id = "default",
        viewType = null,
        sortBy = null,
        indexBy = null,
        rememberIndexing = false,
        primaryImageHeight = 0,
        primaryImageWidth = 0,
        customPrefs = mapOf(),
        scrollDirection = ScrollDirection.VERTICAL,
        showBackdrop = false,
        rememberSorting = false,
        sortOrder = SortOrder.ASCENDING,
        showSidebar = false,
        client = null,
    )
