package com.puremusic.player.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackEntity(val playlistId: Long, val trackId: Long, val position: Int)

@Dao interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY id DESC") fun playlists(): Flow<List<PlaylistEntity>>
    @Query("SELECT * FROM playlist_tracks WHERE playlistId=:id ORDER BY position") fun tracks(id: Long): Flow<List<PlaylistTrackEntity>>
    @Insert suspend fun addPlaylist(item: PlaylistEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addTrack(item: PlaylistTrackEntity)
    @Query("DELETE FROM playlists WHERE id=:id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_tracks WHERE playlistId=:id AND trackId=:trackId") suspend fun removeTrack(id: Long, trackId: Long)
}

@Database(entities = [PlaylistEntity::class, PlaylistTrackEntity::class], version = 1, exportSchema = false)
abstract class PlaylistDb : RoomDatabase() {
    abstract fun dao(): PlaylistDao
    companion object {
        @Volatile private var INSTANCE: PlaylistDb? = null
        fun get(context: Context): PlaylistDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, PlaylistDb::class.java, "pure_music.db").build().also { INSTANCE = it }
        }
    }
}
