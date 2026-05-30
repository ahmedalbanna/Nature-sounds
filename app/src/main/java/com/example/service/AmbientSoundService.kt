package com.example.service

import android.app.*
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.util.*

class AmbientSoundService : Service() {
    private val TAG = "AmbientSoundService"
    private val NOTIFICATION_ID = 42
    private val CHANNEL_ID = "ambient_nature_channel"

    private lateinit var repository: AppRepository
    private val synth = NatureSoundSynth()
    
    // Concurrent map to store manual sound preview flags
    private val manualPreviews = java.util.concurrent.ConcurrentHashMap<NatureSoundSynth.SoundType, Boolean>()
    
    // Coroutine control
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var schedulerJob: Job? = null
    
    // Track history logging
    private var lastLoggedSession = ""

    companion object {
        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_FORCE_HOWL = "com.example.action.FORCE_HOWL"
        const val ACTION_REFRESH_PREF = "com.example.action.REFRESH_PREF"
        const val ACTION_TOGGLE_PREVIEW = "com.example.action.TOGGLE_PREVIEW"

        @Volatile
        private var activeInstance: AmbientSoundService? = null

        fun triggerRustle(intensity: Float) {
            activeInstance?.synth?.triggerRustle(intensity)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        activeInstance = this
        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database.appDao())
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                startForegroundServiceCompat()
                synth.start()
                startScheduler()
                serviceScope.launch(Dispatchers.IO) {
                    val pref = repository.getPreferencesDirect()
                    repository.savePreferences(pref.copy(isServiceRunning = true))
                    
                    val log = PlaybackLog(
                        sessionName = "بدء خدمة الخلفية",
                        activeSounds = "التهيئة والتشغيل",
                        hourOfDay = if (pref.useSimulatedTime) pref.simulatedHour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                        isUserTriggered = false
                    )
                    repository.insertLog(log)
                }
            }
            ACTION_STOP -> {
                stopScheduler()
                synth.stop()
                serviceScope.launch(Dispatchers.IO) {
                    val pref = repository.getPreferencesDirect()
                    repository.savePreferences(pref.copy(isServiceRunning = false))
                    
                    val log = PlaybackLog(
                        sessionName = "إيقاف خدمة الخلفية",
                        activeSounds = "صمت",
                        hourOfDay = if (pref.useSimulatedTime) pref.simulatedHour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                        isUserTriggered = false
                    )
                    repository.insertLog(log)
                    
                    withContext(Dispatchers.Main) {
                        stopForeground(true)
                        stopSelf()
                    }
                }
            }
            ACTION_FORCE_HOWL -> {
                synth.forceHowl()
                logManuallyTriggeredHowl()
            }
            ACTION_TOGGLE_PREVIEW -> {
                val soundTypeName = intent?.getStringExtra("EXTRA_SOUND_TYPE")
                val enable = intent?.getBooleanExtra("EXTRA_ENABLE", false) ?: false
                if (soundTypeName != null) {
                    try {
                        val type = NatureSoundSynth.SoundType.valueOf(soundTypeName)
                        manualPreviews[type] = enable
                        
                        // If howling preview is enabled, trigger it single-shot
                        if (type == NatureSoundSynth.SoundType.HOWL && enable) {
                            synth.forceHowl()
                        }
                        
                        // Log manual preview event
                        serviceScope.launch(Dispatchers.IO) {
                            val pref = repository.getPreferencesDirect()
                            val hour = if (pref.useSimulatedTime) pref.simulatedHour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            val actionAr = if (enable) "تشغيل معاينة وسماع" else "إيقاف معاينة وسماع"
                            val soundAr = when(type) {
                                NatureSoundSynth.SoundType.BIRDS -> "عصافير"
                                NatureSoundSynth.SoundType.WATERFALL -> "شلال"
                                NatureSoundSynth.SoundType.WIND -> "رياح"
                                NatureSoundSynth.SoundType.RAIN -> "مطر"
                                NatureSoundSynth.SoundType.HOWL -> "عواء"
                            }
                            repository.insertLog(PlaybackLog(
                                sessionName = "$actionAr: $soundAr",
                                activeSounds = "معاينة يدوية للتحقق الصوتي",
                                hourOfDay = hour,
                                isUserTriggered = true
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse preview sound type: $soundTypeName", e)
                    }
                }
            }
            ACTION_REFRESH_PREF -> {
                // Read preference immediately and apply master volume
                serviceScope.launch(Dispatchers.IO) {
                    val pref = repository.getPreferencesDirect()
                    synth.masterVolume = pref.masterVolume
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startScheduler() {
        if (schedulerJob != null) return

        schedulerJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    // Reread preference to know parameters (volume, time simulation, simulated hour)
                    val pref = repository.getPreferencesDirect()
                    
                    var fadeFactor = 1.0f
                    if (pref.isDeepSleepEnabled && pref.isDeepSleepTimerActive) {
                        val elapsedMillis = System.currentTimeMillis() - pref.deepSleepStartTimeMillis
                        val durationMillis = pref.deepSleepDurationMinutes * 60 * 1000L
                        if (elapsedMillis >= durationMillis) {
                            fadeFactor = 0.0f
                            
                            // Timer completed! Set active state to false, isServiceRunning to false, and send stop action
                            serviceScope.launch(Dispatchers.IO) {
                                val updatedPref = repository.getPreferencesDirect().copy(
                                    isDeepSleepTimerActive = false,
                                    isServiceRunning = false
                                )
                                repository.savePreferences(updatedPref)
                                repository.insertLog(
                                    PlaybackLog(
                                        sessionName = "اكتمل مؤقت النوم العميق 😴",
                                        activeSounds = "تم إطفاء وتلاشي الأصوات تماماً لمساعدتك على النوم العالي",
                                        hourOfDay = if (updatedPref.useSimulatedTime) updatedPref.simulatedHour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                                        isUserTriggered = false
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    val stopIntent = Intent(this@AmbientSoundService, AmbientSoundService::class.java).apply {
                                        action = ACTION_STOP
                                    }
                                    startService(stopIntent)
                                }
                            }
                        } else {
                            fadeFactor = 1.0f - (elapsedMillis.toFloat() / durationMillis.toFloat())
                        }
                    }

                    synth.masterVolume = pref.masterVolume * fadeFactor

                    // Get Current Hour and Minute
                    val hour: Int
                    val minute: Int
                    if (pref.useSimulatedTime) {
                        hour = pref.simulatedHour
                        minute = pref.simulatedMinute
                    } else {
                        val calendar = Calendar.getInstance()
                        hour = calendar.get(Calendar.HOUR_OF_DAY)
                        minute = calendar.get(Calendar.MINUTE)
                    }

                    // Determine schedule playbacks
                    val schedule = determineSchedule(hour, minute)
                    
                    // Mix in manual previews: if preview is on, force it to 0.8f, otherwise use schedule
                    val birdsTarget = if (manualPreviews[NatureSoundSynth.SoundType.BIRDS] == true) 0.85f else schedule.birdsVol
                    val waterfallTarget = if (manualPreviews[NatureSoundSynth.SoundType.WATERFALL] == true) 0.85f else schedule.waterfallVol
                    val windTarget = if (manualPreviews[NatureSoundSynth.SoundType.WIND] == true) 0.85f else schedule.windVol
                    val rainTarget = if (manualPreviews[NatureSoundSynth.SoundType.RAIN] == true) 0.85f else schedule.rainVol
                    
                    // Pre-defined night howl logic
                    val isNightHour = hour in 21..23 || hour in 0..5
                    val shouldIntroduceNightHowl = pref.introduceNightHowls && isNightHour && (minute == 0)
                    
                    val howlTarget = when {
                        manualPreviews[NatureSoundSynth.SoundType.HOWL] == true -> 0.85f
                        schedule.howlActive || shouldIntroduceNightHowl -> 0.70f
                        else -> 0.0f
                    }
                    
                    // Set target volumes accordingly
                    synth.setTargetVolume(NatureSoundSynth.SoundType.BIRDS, birdsTarget)
                    synth.setTargetVolume(NatureSoundSynth.SoundType.WATERFALL, waterfallTarget)
                    synth.setTargetVolume(NatureSoundSynth.SoundType.WIND, windTarget)
                    synth.setTargetVolume(NatureSoundSynth.SoundType.RAIN, rainTarget)
                    synth.setTargetVolume(NatureSoundSynth.SoundType.HOWL, howlTarget)
                    
                    // Trigger howl if requested and not playing
                    if (howlTarget > 0.05f && !synth.isHowlPlaying()) {
                        synth.forceHowl()
                        
                        serviceScope.launch(Dispatchers.IO) {
                            val logReason = if (shouldIntroduceNightHowl) "عواء بري دوري خافت (النوم العميق)" else "عواء دورة الليل المبرمجة"
                            val currentPref = repository.getPreferencesDirect()
                            val curHour = if (currentPref.useSimulatedTime) currentPref.simulatedHour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            repository.insertLog(
                                PlaybackLog(
                                    sessionName = "$logReason 🐺",
                                    activeSounds = "عواء ذئب هادئ متلاشي يرتفع ثم ينخمد بدقة لنمط نوم صحي",
                                    hourOfDay = curHour,
                                    isUserTriggered = false
                                )
                            )
                        }
                    }

                    // Log session transition to Room in a smart way
                    val activeSessionName = if (manualPreviews.values.any { it }) "جلسة تشغيل ومعاينة مخصصة" else if (pref.isDeepSleepTimerActive) "وضع النوم العميق الخافت المتلاشي" else schedule.sessionName
                    if (activeSessionName != lastLoggedSession && !activeSessionName.contains("معاينة")) {
                        lastLoggedSession = activeSessionName
                        val log = PlaybackLog(
                            sessionName = activeSessionName,
                            activeSounds = getActiveSoundsString(schedule),
                            hourOfDay = hour,
                            isUserTriggered = false
                        )
                        repository.insertLog(log)
                    }

                    // Update notification text dynamically
                    withContext(Dispatchers.Main) {
                        val sessionWithTimer = if (pref.isDeepSleepTimerActive) {
                            val elapsedSec = (System.currentTimeMillis() - pref.deepSleepStartTimeMillis) / 1000
                            val totalSec = pref.deepSleepDurationMinutes * 60
                            val remainingMin = java.lang.Math.max(0L, (totalSec - elapsedSec) / 60)
                            "وضع النوم العميق (متبقي $remainingMin د)"
                        } else {
                            activeSessionName
                        }
                        updateNotification(sessionWithTimer, hour, minute, pref.useSimulatedTime)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in scheduler loop", e)
                }

                delay(1000) // Sleep/delay 1 second
            }
        }
    }

    private fun logManuallyTriggeredHowl() {
        serviceScope.launch(Dispatchers.IO) {
            val pref = repository.getPreferencesDirect()
            val hour = if (pref.useSimulatedTime) pref.simulatedHour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val log = PlaybackLog(
                sessionName = "عواء يدوي تجريبي",
                activeSounds = "عواء ذئب منفرد",
                hourOfDay = hour,
                isUserTriggered = true
            )
            repository.insertLog(log)
        }
    }

    private fun stopScheduler() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    private fun getActiveSoundsString(s: ScheduleState): String {
        val list = mutableListOf<String>()
        if (s.birdsVol > 0.05f) list.add("عصافير")
        if (s.waterfallVol > 0.05f) list.add("شلال")
        if (s.windVol > 0.05f) list.add("رياح")
        if (s.rainVol > 0.05f) list.add("مطر")
        if (s.howlActive || synth.isHowlPlaying()) list.add("عواء حيوانات")
        if (list.isEmpty()) return "صمت مطبق"
        return list.joinToString("، ")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "أصوات الطبيعة الهادئة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة خلفية لتشغيل أصوات الطبيعة"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceCompat() {
        val notification = createNotification("جاري التحضير...", 0, 0, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(sessionName: String, hour: Int, minute: Int, isSimulated: Boolean) {
        val notification = createNotification(sessionName, hour, minute, isSimulated)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(sessionName: String, hour: Int, minute: Int, isSimulated: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/stop action button
        val stopIntent = Intent(this, AmbientSoundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        val timeLabel = if (isSimulated) "وقت افتراضي $timeString" else "الوقت الحالي $timeString"

        val titleText = "أصوات الطبيعة هادئة نشطة"
        val contentText = "$sessionName ($timeLabel)"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_audio_online) // System audio icon
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSubText("تطبيق أصوات هادئة")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف التشغيل", stopPendingIntent)

        return builder.build()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        if (activeInstance == this) {
            activeInstance = null
        }
        stopScheduler()
        synth.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Schedule matrix implementation matching natural cycle rules
    data class ScheduleState(
        val sessionName: String,
        val birdsVol: Float,
        val waterfallVol: Float,
        val windVol: Float,
        val rainVol: Float,
        val howlActive: Boolean
    )

    private fun determineSchedule(hour: Int, minute: Int): ScheduleState {
        // Core schedule timeline:
        // 06:00 - 07:00 -> Birds Forest Symphony (عصافير 100%، شلال 20%، رياح 30%)
        // 07:00 - 12:00 -> SILENCE GAP (فراغ هدوء تام)
        // 12:00 - 13:00 -> Deep Waterfall (شلال 100%، رياح 40%)
        // 13:00 - 17:00 -> SILENCE GAP (فراغ هدوء منتصف النهار)
        // 17:00 - 18:00 -> Sunset Rain (مطر 100%، رياح 50%)
        // 18:00 - 03:00 -> SILENCE GAP (فراغ هدوء الليل الأول)
        // 03:00 - 03:01 -> Wolf Howling for exactly 1 minute! (عواء 100%)
        // 03:01 - 06:00 -> SILENCE GAP (فراغ هدوء الفجر)

        val progress = minute.toFloat() / 60.0f
        val wave = kotlin.math.sin(progress * kotlin.math.PI).toFloat()

        return when {
            // 1. Birds Morning Symphony (6:00 AM - 7:00 AM)
            hour == 6 -> {
                ScheduleState(
                    sessionName = "جيل جديد من سيمفونية الصباح المترقية",
                    birdsVol = 0.20f + 0.70f * wave,
                    waterfallVol = 0.05f + 0.15f * wave,
                    windVol = 0.10f + 0.20f * wave,
                    rainVol = 0f,
                    howlActive = false
                )
            }
            // 2. Noon Waterfall Session (12:00 PM - 1:00 PM)
            hour == 12 -> {
                ScheduleState(
                    sessionName = "تدفق شلال منتصف النهار المتدرج",
                    birdsVol = 0f,
                    waterfallVol = 0.30f + 0.65f * wave,
                    windVol = 0.15f + 0.25f * wave,
                    rainVol = 0f,
                    howlActive = false
                )
            }
            // 3. Sunset Rain Breeze (5:00 PM - 6:00 PM / 17:00 - 18:00)
            hour == 17 -> {
                ScheduleState(
                    sessionName = "رياح ومطر الغروب المتنامي تدريجياً",
                    birdsVol = 0f,
                    waterfallVol = 0f,
                    windVol = 0.20f + 0.35f * wave,
                    rainVol = 0.15f + 0.80f * wave,
                    howlActive = false
                )
            }
            // 4. Midnight Wolf Howl Minute (3:00 AM - 3:01 AM)
            hour == 3 && minute == 0 -> {
                ScheduleState(
                    sessionName = "نداء الذئب في عمق سكون الليل",
                    birdsVol = 0f,
                    waterfallVol = 0f,
                    windVol = 0.15f,
                    rainVol = 0f,
                    howlActive = true
                )
            }
            // All other times are SILENCE GAPS
            else -> {
                val spaceName = when(hour) {
                    in 7..11 -> "فراغ الصباح (هدوء وسكينة)"
                    in 13..16 -> "فراغ الظهيرة (راحة وسلام)"
                    in 18..23 -> "فراغ الليل الهادئ للراحة والنوم"
                    in 0..2 -> "فراغ جوف الليل الساكن"
                    else -> "فراغ ما قبل الفجر الروحاني"
                }
                ScheduleState(
                    sessionName = spaceName,
                    birdsVol = 0f,
                    waterfallVol = 0f,
                    windVol = 0f,
                    rainVol = 0f,
                    howlActive = false
                )
            }
        }
    }
}
