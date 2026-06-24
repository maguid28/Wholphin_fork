package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemLookupApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MovieInfo
import org.jellyfin.sdk.model.api.MovieInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.RemoteSearchResult
import org.jellyfin.sdk.model.api.SeriesInfo
import org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataRematchService
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        suspend fun search(
            item: BaseItem,
            query: String,
        ): List<RemoteSearchResult> =
            when (item.type) {
                BaseItemKind.MOVIE ->
                    api.itemLookupApi
                        .getMovieRemoteSearchResults(
                            MovieInfoRemoteSearchQuery(
                                searchInfo = item.toMovieInfo(query),
                                itemId = item.id,
                                searchProviderName = null,
                                includeDisabledProviders = true,
                            ),
                        ).content

                BaseItemKind.SERIES ->
                    api.itemLookupApi
                        .getSeriesRemoteSearchResults(
                            SeriesInfoRemoteSearchQuery(
                                searchInfo = item.toSeriesInfo(query),
                                itemId = item.id,
                                searchProviderName = null,
                                includeDisabledProviders = true,
                            ),
                        ).content

                else -> emptyList()
            }

        suspend fun apply(
            itemId: UUID,
            result: RemoteSearchResult,
        ) {
            api.itemLookupApi.applySearchCriteria(
                itemId = itemId,
                replaceAllImages = true,
                data = result,
            )
        }

        suspend fun waitForUpdatedItem(
            previous: BaseItem,
            attempts: Int = 8,
            delayMs: Long = 750L,
        ): BaseItem {
            var latest = getItem(previous.id)
            repeat(attempts) {
                if (latest.metadataSignature != previous.metadataSignature) {
                    return latest
                }
                delay(delayMs)
                latest = getItem(previous.id)
            }
            return latest
        }

        private suspend fun getItem(itemId: UUID): BaseItem =
            BaseItem(api.userLibraryApi.getItem(itemId).content)

        private val BaseItem.metadataSignature: MetadataSignature
            get() =
                MetadataSignature(
                    etag = data.etag,
                    name = data.name,
                    originalTitle = data.originalTitle,
                    productionYear = data.productionYear,
                    providerIds = data.providerIds.orEmpty(),
                    imageTags = data.imageTags.orEmpty(),
                    backdropImageTags = data.backdropImageTags.orEmpty(),
                )

        private fun BaseItem.toMovieInfo(query: String): MovieInfo =
            MovieInfo(
                name = query,
                originalTitle = null,
                path = data.path,
                metadataLanguage = data.preferredMetadataLanguage,
                metadataCountryCode = data.preferredMetadataCountryCode,
                providerIds = emptyMap(),
                year = null,
                indexNumber = null,
                parentIndexNumber = null,
                premiereDate = null,
                isAutomated = false,
            )

        private fun BaseItem.toSeriesInfo(query: String): SeriesInfo =
            SeriesInfo(
                name = query,
                originalTitle = null,
                path = data.path,
                metadataLanguage = data.preferredMetadataLanguage,
                metadataCountryCode = data.preferredMetadataCountryCode,
                providerIds = emptyMap(),
                year = null,
                indexNumber = null,
                parentIndexNumber = null,
                premiereDate = null,
                isAutomated = false,
            )

        private data class MetadataSignature(
            val etag: String?,
            val name: String?,
            val originalTitle: String?,
            val productionYear: Int?,
            val providerIds: Map<String, String?>,
            val imageTags: Map<org.jellyfin.sdk.model.api.ImageType, String?>,
            val backdropImageTags: List<String>,
        )
    }
