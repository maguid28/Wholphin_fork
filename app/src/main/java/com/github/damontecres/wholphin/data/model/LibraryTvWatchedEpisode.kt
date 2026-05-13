package com.github.damontecres.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "library_tv_watched_episodes",
    primaryKeys = ["userId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId"), Index("itemId")],
)
data class LibraryTvWatchedEpisode(
    val userId: Int,
    val itemId: UUID,
    val watchedAtEpochMs: Long,
)
