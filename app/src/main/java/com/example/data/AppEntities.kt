package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_logs")
data class PlaybackLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionName: String,
    val activeSounds: String,
    val hourOfDay: Int,
    val isUserTriggered: Boolean
)

@Entity(tableName = "app_preferences")
data class AppPreference(
    @PrimaryKey val id: Int = 1,
    val masterVolume: Float = 0.8f,
    val useSimulatedTime: Boolean = true,
    val simulatedHour: Int = 6, // Default at 6:00 AM (Birds morning peak)
    val simulatedMinute: Int = 0,
    val isServiceRunning: Boolean = false,
    val isDeepSleepEnabled: Boolean = false,
    val deepSleepDurationMinutes: Int = 30, // standard duration
    val deepSleepStartTimeMillis: Long = 0L,
    val isDeepSleepTimerActive: Boolean = false,
    val introduceNightHowls: Boolean = false,
    val activePlaylistId: Int? = null,
    val playlistVolumeFactor: Float = 1.0f
)

@Entity(tableName = "custom_playlists")
data class CustomPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val birdsVolume: Float = 0.0f,
    val waterfallVolume: Float = 0.0f,
    val windVolume: Float = 0.0f,
    val rainVolume: Float = 0.0f,
    val howlVolume: Float = 0.0f
)
