package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM playback_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllPlaybackLogs(): Flow<List<PlaybackLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackLog(log: PlaybackLog)

    @Query("DELETE FROM playback_logs")
    suspend fun clearAllPlaybackLogs()

    @Query("SELECT * FROM app_preferences WHERE id = 1")
    fun getPreferencesFlow(): Flow<AppPreference?>

    @Query("SELECT * FROM app_preferences WHERE id = 1")
    suspend fun getPreferencesDirect(): AppPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(pref: AppPreference)
}
