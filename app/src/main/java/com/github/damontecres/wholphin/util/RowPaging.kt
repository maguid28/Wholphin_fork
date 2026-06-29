package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.HOME_ROW_PAGE_SIZE
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.toBaseItems
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import java.util.UUID

enum class PaginatedRowKind {
    RESUME,
    NEXT_UP,
    RECENTLY_RELEASED,
    RECENTLY_ADDED,
    TOP_UNWATCHED,
    SUGGESTIONS,
    SIMILAR,
    SEASONS,
    COLLECTION_ITEMS,
    SEARCH,
}

object RowPaging {
    fun hasMore(
        itemCount: Int,
        limit: Int,
        startIndex: Int,
        totalRecordCount: Int?,
    ): Boolean =
        when {
            itemCount <= 0 -> false
            totalRecordCount != null -> totalRecordCount > startIndex + itemCount
            else -> itemCount >= limit
        }

    suspend fun fetchGetItemsPage(
        api: ApiClient,
        request: GetItemsRequest,
        startIndex: Int,
        limit: Int = HOME_ROW_PAGE_SIZE,
        useSeriesForPrimary: Boolean = false,
    ): Pair<List<BaseItem>, Boolean> {
        val pagedRequest =
            request.copy(
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = true,
            )
        val response = GetItemsRequestHandler.execute(api, pagedRequest)
        val content = response.content
        val items = response.toBaseItems(api, useSeriesForPrimary)
        return items to hasMore(items.size, limit, startIndex, content.totalRecordCount)
    }

    suspend fun fetchSimilarPage(
        api: ApiClient,
        itemId: UUID,
        userId: UUID?,
        startIndex: Int,
        useSeriesForPrimary: Boolean,
        limit: Int = HOME_ROW_PAGE_SIZE,
    ): Pair<List<BaseItem>, Boolean> {
        val fetchLimit = startIndex + limit
        val response =
            api.libraryApi
                .getSimilarItems(
                    GetSimilarItemsRequest(
                        userId = userId,
                        itemId = itemId,
                        fields = SlimItemFields,
                        limit = fetchLimit,
                    ),
                ).content
        val all = response.items.map { BaseItem.from(it, api, useSeriesForPrimary) }
        val page = all.drop(startIndex)
        return page to (all.size >= fetchLimit)
    }
}
