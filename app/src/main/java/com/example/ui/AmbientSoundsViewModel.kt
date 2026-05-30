package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.AmbientSoundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AmbientSoundsViewModel(
    private val application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    // Preferences flow with default fallback
    val preferences: StateFlow<AppPreference> = repository.preferences
        .map { it ?: AppPreference() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreference()
        )

    // History logs
    val playbackLogs: StateFlow<List<PlaybackLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startService() {
        viewModelScope.launch {
            // Update db state
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(isServiceRunning = true))
            
            // Start Foreground Service
            val context = application.applicationContext
            val intent = Intent(context, AmbientSoundService::class.java).apply {
                action = AmbientSoundService.ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopService() {
        viewModelScope.launch {
            // Update db state
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(isServiceRunning = false))
            
            // Stop service
            val context = application.applicationContext
            val intent = Intent(context, AmbientSoundService::class.java).apply {
                action = AmbientSoundService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    fun updateMasterVolume(volume: Float) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(masterVolume = volume))
            notifyPrefChanged()
        }
    }

    fun toggleTimeSimulation(useSimulated: Boolean) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(useSimulatedTime = useSimulated))
        }
    }

    fun updateSimulatedTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(simulatedHour = hour, simulatedMinute = minute))
        }
    }

    fun triggerHowl() {
        val context = application.applicationContext
        val intent = Intent(context, AmbientSoundService::class.java).apply {
            action = AmbientSoundService.ACTION_FORCE_HOWL
        }
        context.startService(intent)
    }

    fun toggleIndependentPreview(soundTypeName: String, enable: Boolean) {
        val context = application.applicationContext
        val intent = Intent(context, AmbientSoundService::class.java).apply {
            action = "com.example.action.TOGGLE_PREVIEW"
            putExtra("EXTRA_SOUND_TYPE", soundTypeName)
            putExtra("EXTRA_ENABLE", enable)
        }
        context.startService(intent)
    }

    fun toggleDeepSleepEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(isDeepSleepEnabled = enabled))
            notifyPrefChanged()
        }
    }

    fun updateDeepSleepDuration(minutes: Int) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(deepSleepDurationMinutes = minutes))
            notifyPrefChanged()
        }
    }

    fun toggleIntroduceNightHowls(enabled: Boolean) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(introduceNightHowls = enabled))
            notifyPrefChanged()
        }
    }

    fun startDeepSleepTimer() {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            val updated = currentPref.copy(
                isDeepSleepEnabled = true,
                isDeepSleepTimerActive = true,
                deepSleepStartTimeMillis = System.currentTimeMillis()
            )
            repository.savePreferences(updated)
            
            repository.insertLog(
                PlaybackLog(
                    sessionName = "تفعيل مؤقت النوم العميق 🛌",
                    activeSounds = "المدة المخصصة للتلاشي: ${currentPref.deepSleepDurationMinutes} دقيقة",
                    hourOfDay = if (updated.useSimulatedTime) updated.simulatedHour else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                    isUserTriggered = true
                )
            )

            if (!currentPref.isServiceRunning) {
                startService()
            } else {
                notifyPrefChanged()
            }
        }
    }

    fun cancelDeepSleepTimer() {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            val updated = currentPref.copy(
                isDeepSleepTimerActive = false
            )
            repository.savePreferences(updated)
            
            repository.insertLog(
                PlaybackLog(
                    sessionName = "إلغاء وضع النوم العميق 🛑",
                    activeSounds = "العودة لحجم الصوت الطبيعي وإيقاف تناقص الصوت المتوالي",
                    hourOfDay = if (updated.useSimulatedTime) updated.simulatedHour else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                    isUserTriggered = true
                )
            )
            notifyPrefChanged()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    private fun notifyPrefChanged() {
        val context = application.applicationContext
        val intent = Intent(context, AmbientSoundService::class.java).apply {
            action = AmbientSoundService.ACTION_REFRESH_PREF
        }
        context.startService(intent)
    }

    // Factory to instantiate ViewModel with repository parameters
    class Factory(
        private val application: Application,
        private val repository: AppRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AmbientSoundsViewModel::class.java)) {
                return AmbientSoundsViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
