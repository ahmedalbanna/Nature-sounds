package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val appDao: AppDao) {
    val allLogs: Flow<List<PlaybackLog>> = appDao.getAllPlaybackLogs()
    
    val preferences: Flow<AppPreference?> = appDao.getPreferencesFlow()

    suspend fun insertLog(log: PlaybackLog) {
        appDao.insertPlaybackLog(log)
    }

    suspend fun clearLogs() {
        appDao.clearAllPlaybackLogs()
    }

    suspend fun getPreferencesDirect(): AppPreference {
        return appDao.getPreferencesDirect() ?: AppPreference()
    }

    suspend fun savePreferences(pref: AppPreference) {
        appDao.savePreferences(pref)
    }

    val allPlaylists: Flow<List<CustomPlaylist>> = appDao.getAllPlaylists()

    suspend fun getPlaylistById(id: Int): CustomPlaylist? {
        return appDao.getPlaylistById(id)
    }

    suspend fun insertPlaylist(playlist: CustomPlaylist): Long {
        return appDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: CustomPlaylist) {
        appDao.deletePlaylist(playlist)
    }
}
