package com.github.damontecres.wholphin.ui.detail.librarytv

import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class LibraryTvScheduleTest {
    @Test
    fun supplementalLibraryTvSeriesIds_includesExistingSeriesWithTooFewEpisodes() {
        val seriesId = uuid("rob-and-big")
        val sampledEpisode =
            episode(
                id = uuid("rob-and-big-s1e5"),
                seriesId = seriesId,
                name = "Rob & Big S1E5",
                network = "MTV",
            )

        val supplementalSeriesIds =
            supplementalLibraryTvSeriesIds(
                series = emptyList(),
                existingEpisodes = listOf(sampledEpisode),
                seriesNetworkNames = emptyMap(),
                windowStart = WindowStart,
                maxCandidates = 10,
            )

        assertTrue(supplementalSeriesIds.contains(seriesId))
    }

    @Test
    fun supplementalLibraryTvSeriesIds_addsNewSeriesWhenChannelNeedsMoreShows() {
        val existingSeriesId = uuid("existing-mtv-show")
        val newSeriesIds = listOf(uuid("new-mtv-show-1"), uuid("new-mtv-show-2"))
        val existingEpisodes =
            (1..4).map { episode(seriesId = existingSeriesId, episodeNumber = it, network = "MTV") }
        val series = newSeriesIds.map { series(id = it, network = "MTV") }

        val supplementalSeriesIds =
            supplementalLibraryTvSeriesIds(
                series = series,
                existingEpisodes = existingEpisodes,
                seriesNetworkNames = emptyMap(),
                windowStart = WindowStart,
                maxCandidates = 10,
            )

        assertEquals(newSeriesIds.toSet(), supplementalSeriesIds.toSet())
    }

    @Test
    fun buildNaturalScheduleCycle_keepsRotationWhenAvoidanceLeavesOneEpisode() {
        val seriesId = uuid("single-unwatched-show")
        val episodes =
            listOf(
                episode(id = uuid("single-unwatched-show-1"), seriesId = seriesId, episodeNumber = 1, network = "MTV"),
                episode(id = uuid("single-unwatched-show-2"), seriesId = seriesId, episodeNumber = 2, network = "MTV"),
            )

        val cycle =
            episodes.buildNaturalScheduleCycle(
                channelKey = "mtv",
                windowStart = WindowStart,
                avoidedEpisodeIds = setOf(episodes.first().id),
            )

        assertEquals(episodes.map { it.id }.toSet(), cycle.map { it.id }.toSet())
    }
}

private val WindowStart: Instant = Instant.parse("2026-05-19T00:00:00Z")

private fun episode(
    id: UUID = UUID.randomUUID(),
    seriesId: UUID,
    episodeNumber: Int = 1,
    name: String = "Episode $episodeNumber",
    network: String,
): BaseItem =
    BaseItem(
        BaseItemDto(
            id = id,
            type = BaseItemKind.EPISODE,
            name = name,
            sortName = name,
            seriesId = seriesId,
            seriesName = name.substringBefore(" S"),
            parentIndexNumber = 1,
            indexNumber = episodeNumber,
            seriesStudio = network,
        ),
        useSeriesForPrimary = true,
    )

private fun series(
    id: UUID,
    name: String = id.toString(),
    network: String,
): BaseItem =
    BaseItem(
        BaseItemDto(
            id = id,
            type = BaseItemKind.SERIES,
            name = name,
            sortName = name,
            seriesStudio = network,
        ),
    )

private fun uuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray())
