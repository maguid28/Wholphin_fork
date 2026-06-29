package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.HOME_ROW_PAGE_SIZE
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetNextUpRequestHandler
import com.github.damontecres.wholphin.util.GetResumeItemsRequestHandler
import com.github.damontecres.wholphin.util.PaginatedRowKind
import com.github.damontecres.wholphin.util.RowPaging
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendedRowPagingService
    @Inject
    constructor(
        private val api: ApiClient,
        private val latestNextUpService: LatestNextUpService,
    ) {
        suspend fun fetchMovieRowPage(
            kind: PaginatedRowKind,
            parentId: UUID,
            userId: UUID?,
            startIndex: Int,
        ): Pair<List<BaseItem>, Boolean>? =
            when (kind) {
                PaginatedRowKind.RESUME -> {
                    val request =
                        GetResumeItemsRequest(
                            userId = userId,
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            enableUserData = true,
                            startIndex = startIndex,
                            limit = HOME_ROW_PAGE_SIZE,
                            enableTotalRecordCount = true,
                        )
                    val response = GetResumeItemsRequestHandler.execute(api, request)
                    val content = response.content
                    val items = response.toBaseItems(api, false)
                    items to
                        RowPaging.hasMore(
                            items.size,
                            HOME_ROW_PAGE_SIZE,
                            startIndex,
                            content.totalRecordCount,
                        )
                }

                PaginatedRowKind.RECENTLY_RELEASED ->
                    fetchLibraryItemsPage(
                        parentId = parentId,
                        itemKind = BaseItemKind.MOVIE,
                        sortBy = ItemSortBy.PREMIERE_DATE,
                        startIndex = startIndex,
                        useSeries = false,
                    )

                PaginatedRowKind.RECENTLY_ADDED ->
                    fetchLibraryItemsPage(
                        parentId = parentId,
                        itemKind = BaseItemKind.MOVIE,
                        sortBy = ItemSortBy.DATE_CREATED,
                        startIndex = startIndex,
                        useSeries = false,
                    )

                PaginatedRowKind.TOP_UNWATCHED ->
                    fetchLibraryItemsPage(
                        parentId = parentId,
                        itemKind = BaseItemKind.MOVIE,
                        sortBy = ItemSortBy.COMMUNITY_RATING,
                        startIndex = startIndex,
                        useSeries = false,
                        isPlayed = false,
                    )

                PaginatedRowKind.NEXT_UP,
                PaginatedRowKind.SUGGESTIONS,
                -> null

                else -> null
            }

        suspend fun fetchTvRowPage(
            kind: PaginatedRowKind,
            parentId: UUID,
            userId: UUID?,
            startIndex: Int,
            combineContinueNext: Boolean,
            enableRewatchingNextUp: Boolean,
            maxDaysNextUp: Int,
        ): Pair<List<BaseItem>, Boolean>? =
            when (kind) {
                PaginatedRowKind.RESUME -> {
                    if (combineContinueNext) return null
                    val request =
                        GetResumeItemsRequest(
                            userId = userId,
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            enableUserData = true,
                            startIndex = startIndex,
                            limit = HOME_ROW_PAGE_SIZE,
                            enableTotalRecordCount = true,
                        )
                    val response = GetResumeItemsRequestHandler.execute(api, request)
                    val content = response.content
                    val items = response.toBaseItems(api, true)
                    items to
                        RowPaging.hasMore(
                            items.size,
                            HOME_ROW_PAGE_SIZE,
                            startIndex,
                            content.totalRecordCount,
                        )
                }

                PaginatedRowKind.NEXT_UP -> {
                    if (combineContinueNext) return null
                    val request =
                        GetNextUpRequest(
                            userId = userId,
                            fields = SlimItemFields,
                            imageTypeLimit = 1,
                            parentId = parentId,
                            startIndex = startIndex,
                            limit = HOME_ROW_PAGE_SIZE,
                            enableTotalRecordCount = true,
                            enableResumable = false,
                            enableUserData = true,
                            enableRewatching = enableRewatchingNextUp,
                        )
                    val response = GetNextUpRequestHandler.execute(api, request)
                    val content = response.content
                    val items = response.toBaseItems(api, true)
                    items to
                        RowPaging.hasMore(
                            items.size,
                            HOME_ROW_PAGE_SIZE,
                            startIndex,
                            content.totalRecordCount,
                        )
                }

                PaginatedRowKind.RECENTLY_RELEASED ->
                    fetchLibraryItemsPage(
                        parentId = parentId,
                        itemKind = BaseItemKind.EPISODE,
                        sortBy = ItemSortBy.PREMIERE_DATE,
                        startIndex = startIndex,
                        useSeries = true,
                    )

                PaginatedRowKind.RECENTLY_ADDED ->
                    fetchLibraryItemsPage(
                        parentId = parentId,
                        itemKind = BaseItemKind.EPISODE,
                        sortBy = ItemSortBy.DATE_CREATED,
                        startIndex = startIndex,
                        useSeries = true,
                    )

                PaginatedRowKind.TOP_UNWATCHED ->
                    fetchLibraryItemsPage(
                        parentId = parentId,
                        itemKind = BaseItemKind.SERIES,
                        sortBy = ItemSortBy.COMMUNITY_RATING,
                        startIndex = startIndex,
                        useSeries = true,
                        isPlayed = false,
                    )

                PaginatedRowKind.SUGGESTIONS -> null

                else -> null
            }

        private suspend fun fetchLibraryItemsPage(
            parentId: UUID,
            itemKind: BaseItemKind,
            sortBy: ItemSortBy,
            startIndex: Int,
            useSeries: Boolean,
            isPlayed: Boolean? = null,
        ): Pair<List<BaseItem>, Boolean> {
            val request =
                GetItemsRequest(
                    parentId = parentId,
                    fields = SlimItemFields,
                    includeItemTypes = listOf(itemKind),
                    recursive = true,
                    enableUserData = true,
                    sortBy = listOf(sortBy),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    isPlayed = isPlayed,
                )
            return RowPaging.fetchGetItemsPage(
                api = api,
                request = request,
                startIndex = startIndex,
                useSeriesForPrimary = useSeries,
            )
        }
    }
