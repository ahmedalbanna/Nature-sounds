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
    val isServiceRunning: Boolean = false
)
