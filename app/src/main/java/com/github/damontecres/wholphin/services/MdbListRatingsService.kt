package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MdbListRatingsService
    @Inject
    constructor(
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
        private val api: ApiClient,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
        private val cache = mutableMapOf<String, Map<String, Float>>()
        private val pending = mutableMapOf<String, CompletableDeferred<Map<String, Float>?>>()

        suspend fun getRottenTomatoesAudienceScore(item: BaseItem): Float? =
            getRatings(item)
                ?.let(::findAudienceScore)
                ?.let(::normalizePercent)

        suspend fun getRottenTomatoesCriticScore(item: BaseItem): Float? =
            getRatings(item)
                ?.let(::findCriticScore)
                ?.let(::normalizePercent)

        suspend fun loadAudienceScores(items: List<BaseItem>): Map<java.util.UUID, Float> =
            items
                .mapNotNull { item ->
                    getRottenTomatoesAudienceScore(item)
                        ?.takeIf { it > 0f }
                        ?.let { item.id to it }
                }.toMap()

        suspend fun loadCriticScores(items: List<BaseItem>): Map<java.util.UUID, Float> =
            items
                .mapNotNull { item ->
                    getRottenTomatoesCriticScore(item)
                        ?.takeIf { it > 0f }
                        ?.let { item.id to it }
                }.toMap()

        private suspend fun getRatings(item: BaseItem): Map<String, Float>? =
            withContext(Dispatchers.IO) {
                val tmdbId = item.data.providerIds?.get("Tmdb") ?: return@withContext null
                val type =
                    when (item.type) {
                        BaseItemKind.SERIES,
                        BaseItemKind.SEASON,
                        BaseItemKind.EPISODE,
                        -> "show"

                        else -> "movie"
                    }
                val cacheKey = "$type:$tmdbId"
                cache[cacheKey]?.let { return@withContext it }
                pending[cacheKey]?.let { return@withContext it.await() }

                val deferred = CompletableDeferred<Map<String, Float>?>()
                pending[cacheKey] = deferred
                try {
                    val baseUrl =
                        api.baseUrl?.trimEnd('/')
                            ?: run {
                                Timber.w("MDBList ratings request skipped: no server URL")
                                deferred.complete(null)
                                return@withContext null
                            }
                    val accessToken =
                        api.accessToken
                            ?: run {
                                Timber.w("MDBList ratings request skipped: no access token")
                                deferred.complete(null)
                                return@withContext null
                            }
                    val url = "$baseUrl/Moonfin/MdbList/Ratings?type=$type&tmdbId=$tmdbId"
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("Authorization", "MediaBrowser Token=\"$accessToken\"")
                            .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Timber.w("MDBList ratings request failed: ${response.code} ${response.message}")
                            deferred.complete(null)
                            return@withContext null
                        }
                        val body = response.body.string()
                        if (body.isNullOrBlank()) {
                            deferred.complete(null)
                            return@withContext null
                        }
                        val parsed = json.decodeFromString<MdbListResponse>(body)
                        if (!parsed.success || parsed.error != null) {
                            Timber.w("MDBList ratings returned error: ${parsed.error}")
                            deferred.complete(null)
                            return@withContext null
                        }
                        val ratings =
                            parsed.ratings
                                ?.mapNotNull { rating ->
                                    val source = rating.source?.trim()?.lowercase() ?: return@mapNotNull null
                                    val value = (rating.value ?: rating.score)?.takeIf { it > 0f }
                                    value?.let { source to it }
                                }?.toMap(LinkedHashMap())
                                ?: linkedMapOf()
                        Timber.d("MDBList ratings for ${item.id}: ${ratings.keys.joinToString()}")
                        cache[cacheKey] = ratings
                        deferred.complete(ratings)
                        ratings
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Failed to fetch MDBList ratings for ${item.id}")
                    deferred.complete(null)
                    null
                } finally {
                    pending.remove(cacheKey)
                }
            }

        private fun normalizePercent(value: Float): Float =
            if (value in 0f..1f) value * 100f else value

        private fun findAudienceScore(ratings: Map<String, Float>): Float? =
            ratings["popcorn"]
                ?: ratings["rt_audience"]
                ?: ratings["tomatoes_audience"]
                ?: ratings["rottentomatoes_audience"]
                ?: ratings.entries
                    .firstOrNull { (source, _) ->
                        source.contains("audience") || source.contains("popcorn")
                    }?.value

        private fun findCriticScore(ratings: Map<String, Float>): Float? =
            ratings["tomatoes"]
                ?: ratings["rt_critic"]
                ?: ratings["rottentomatoes"]
                ?: ratings["tomatoes_critic"]
                ?: ratings.entries
                    .firstOrNull { (source, _) ->
                        (source.contains("tomato") || source.contains("rt")) &&
                            !source.contains("audience") &&
                            !source.contains("popcorn")
                    }?.value
    }

@Serializable
private data class MdbListResponse(
    val success: Boolean = false,
    val error: String? = null,
    val ratings: List<MdbListRating>? = null,
)

@Serializable
private data class MdbListRating(
    val source: String? = null,
    val value: Float? = null,
    val score: Float? = null,
)
