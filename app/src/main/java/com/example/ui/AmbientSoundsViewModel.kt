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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
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

    val weatherLoading = MutableStateFlow(false)
    val weatherError = MutableStateFlow<String?>(null)

    val customPlaylists: StateFlow<List<CustomPlaylist>> = repository.allPlaylists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            try {
                val list = repository.allPlaylists.first()
                if (list.isEmpty()) {
                    repository.insertPlaylist(
                        CustomPlaylist(
                            name = "ملاذ الغابة الممطرة 🌧️🌲",
                            birdsVolume = 0.40f,
                            waterfallVolume = 0.20f,
                            windVolume = 0.35f,
                            rainVolume = 0.70f,
                            howlVolume = 0.00f
                        )
                    )
                    repository.insertPlaylist(
                        CustomPlaylist(
                            name = "عاصفة البراري العاتية ⚡🐺",
                            birdsVolume = 0.00f,
                            waterfallVolume = 0.10f,
                            windVolume = 0.85f,
                            rainVolume = 0.60f,
                            howlVolume = 0.50f
                        )
                    )
                    repository.insertPlaylist(
                        CustomPlaylist(
                            name = "سكينة الشلال المتدفق 🏔️🌊",
                            birdsVolume = 0.15f,
                            waterfallVolume = 0.80f,
                            windVolume = 0.25f,
                            rainVolume = 0.00f,
                            howlVolume = 0.00f
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore initialization errors
            }
        }
    }

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

    fun savePlaylist(name: String, birds: Float, waterfall: Float, wind: Float, rain: Float, howl: Float) {
        viewModelScope.launch {
            val playlist = CustomPlaylist(
                name = name,
                birdsVolume = birds,
                waterfallVolume = waterfall,
                windVolume = wind,
                rainVolume = rain,
                howlVolume = howl
            )
            val playlistId = repository.insertPlaylist(playlist).toInt()
            
            // Log creation
            val currentPref = repository.getPreferencesDirect()
            repository.insertLog(
                PlaybackLog(
                    sessionName = "إنشاء قائمة تشغيل مخصصة ✨",
                    activeSounds = "الاسم: $name (تضم أصوات مخصصة بمستويات متباينة)",
                    hourOfDay = if (currentPref.useSimulatedTime) currentPref.simulatedHour else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                    isUserTriggered = true
                )
            )
            
            // Auto select newly created playlist
            selectPlaylist(playlistId)
        }
    }

    fun deletePlaylist(playlist: CustomPlaylist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            val currentPref = repository.getPreferencesDirect()
            if (currentPref.activePlaylistId == playlist.id) {
                selectPlaylist(null)
            }
        }
    }

    fun selectPlaylist(playlistId: Int?) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            val updated = currentPref.copy(activePlaylistId = playlistId)
            repository.savePreferences(updated)
            
            val logName: String
            val logDetail: String
            if (playlistId != null) {
                val plName = repository.getPlaylistById(playlistId)?.name ?: "جديدة"
                logName = "تنشيط قائمة تشغيل: $plName 🎵"
                logDetail = "تجاوز الجدول الزمني وبدء الاستماع لتوليفة الأصوات المصممة خصيصاً"
            } else {
                logName = "العودة للجدول الزمني التلقائي 🗓️"
                logDetail = "مزامنة الأصوات وحساب مستوياتها طبيعياً مع مرور الساعات"
            }
            
            repository.insertLog(
                PlaybackLog(
                    sessionName = logName,
                    activeSounds = logDetail,
                    hourOfDay = if (updated.useSimulatedTime) updated.simulatedHour else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                    isUserTriggered = true
                )
            )
            notifyPrefChanged()
        }
    }

    fun updatePlaylistVolumeFactor(factor: Float) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            repository.savePreferences(currentPref.copy(playlistVolumeFactor = factor))
            notifyPrefChanged()
        }
    }

    fun syncWeather(latitude: Double, longitude: Double, cityName: String) {
        viewModelScope.launch {
            weatherLoading.value = true
            weatherError.value = null
            try {
                val response = WeatherClient.api.getWeather(latitude, longitude)
                val current = response.current_weather
                if (current != null) {
                    val currentPref = repository.getPreferencesDirect()
                    val updated = currentPref.copy(
                        weatherCity = cityName,
                        weatherTemp = current.temperature,
                        weatherCode = current.weathercode,
                        weatherLat = latitude,
                        weatherLon = longitude,
                        isWeatherSyncEnabled = true
                    )
                    repository.savePreferences(updated)
                    
                    // Log the sync event
                    val wtDesc = WeatherUtils.getWeatherDescription(current.weathercode)
                    repository.insertLog(
                        PlaybackLog(
                            sessionName = "انسجام مع طقس $cityName 🌦️",
                            activeSounds = "درجة الحرارة: ${current.temperature}°م • حالة الطقس: $wtDesc",
                            hourOfDay = if (updated.useSimulatedTime) updated.simulatedHour else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                            isUserTriggered = true
                        )
                    )
                    
                    notifyPrefChanged()
                } else {
                    weatherError.value = "لم يتم الحصول على معلومات الطقس من الخادم."
                }
            } catch (e: Exception) {
                weatherError.value = "فشل تحديث الطقس: تأكد من الاتصال بالإنترنت."
            } finally {
                weatherLoading.value = false
            }
        }
    }

    fun toggleWeatherSync(enabled: Boolean) {
        viewModelScope.launch {
            val currentPref = repository.getPreferencesDirect()
            
            val updated = currentPref.copy(isWeatherSyncEnabled = enabled)
            if (enabled && currentPref.weatherCode == null) {
                // Fetch Riyadh by default initially so there's valid weather code instantly
                syncWeather(24.7136, 46.6753, "الرياض")
                return@launch
            }
            
            repository.savePreferences(updated)
            
            // Log turn on/off
            repository.insertLog(
                PlaybackLog(
                    sessionName = if (enabled) "تفعيل التناغم مع الطقس 🟢" else "إيقاف التناغم مع الطقس 🔴",
                    activeSounds = if (enabled) "يتم جلب حالة الجو ومطابقتها بأصوات الطبيعة تلقائياً" else "العودة للجدول الزمني التلقائي أو التوليفات اليدوية",
                    hourOfDay = if (updated.useSimulatedTime) updated.simulatedHour else java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                    isUserTriggered = true
                )
            )
            
            notifyPrefChanged()
        }
    }

    fun detectUserLocationAndSync(context: Context) {
        viewModelScope.launch {
            weatherLoading.value = true
            weatherError.value = null
            
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                if (locationManager == null) {
                    weatherError.value = "ميزة الموقع الجغرافي غير مدعومة على هذا الجهاز."
                    weatherLoading.value = false
                    return@launch
                }
                
                // Check permissions
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                if (!hasFine && !hasCoarse) {
                    weatherError.value = "الرجاء منح صلاحيات الموقع الجغرافي أولاً."
                    weatherLoading.value = false
                    return@launch
                }

                // Retrieve last known location
                val gpsLocation = try { locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) } catch (e: SecurityException) { null }
                val netLocation = try { locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) } catch (e: SecurityException) { null }
                
                val bestLocation = gpsLocation ?: netLocation
                
                if (bestLocation != null) {
                    val lat = bestLocation.latitude
                    val lon = bestLocation.longitude
                    
                    var resolvedCityName = "موقعي الحالي"
                    try {
                        val geoCoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                        val addresses = geoCoder.getFromLocation(lat, lon, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val locality = addresses[0].locality
                            val adminArea = addresses[0].adminArea
                            resolvedCityName = locality ?: adminArea ?: "موقعي"
                        }
                    } catch (e: Exception) {
                        // ignore geocoder issues
                    }
                    
                    syncWeather(lat, lon, resolvedCityName)
                } else {
                    // Try dynamic update
                    var locationFound = false
                    val locationListener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: android.location.Location) {
                            if (!locationFound) {
                                locationFound = true
                                syncWeather(location.latitude, location.longitude, "موقعي الدقيق")
                                try { locationManager.removeUpdates(this) } catch (e: Exception) {}
                            }
                        }
                    }
                    
                    val providerName = if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                        android.location.LocationManager.NETWORK_PROVIDER
                    } else if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                        android.location.LocationManager.GPS_PROVIDER
                    } else {
                        null
                    }
                    
                    if (providerName != null) {
                        try {
                            locationManager.requestLocationUpdates(providerName, 1000L, 1f, locationListener)
                            // timeout after 6 seconds to prevent infinitely waiting
                            kotlinx.coroutines.delay(6000)
                            if (!locationFound) {
                                locationManager.removeUpdates(locationListener)
                                syncWeather(24.7136, 46.6753, "الرياض (موقع افتراضي)")
                            }
                        } catch (e: SecurityException) {
                            weatherError.value = "صلاحيات الوصول للموقع مرفوضة."
                            weatherLoading.value = false
                        }
                    } else {
                        syncWeather(24.7136, 46.6753, "الرياض (تلقائي)")
                    }
                }
            } catch (e: Exception) {
                syncWeather(24.7136, 46.6753, "الرياض (تلقائي)")
            }
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
