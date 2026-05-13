package com.github.damontecres.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.damontecres.wholphin.data.model.LibraryTvWatchedEpisode
import java.util.UUID

@Dao
interface LibraryTvWatchedEpisodeDao {
    @Query("SELECT itemId FROM library_tv_watched_episodes WHERE userId=:userId")
    suspend fun getWatchedEpisodeIds(userId: Int): List<UUID>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(watchedEpisode: LibraryTvWatchedEpisode)
}
