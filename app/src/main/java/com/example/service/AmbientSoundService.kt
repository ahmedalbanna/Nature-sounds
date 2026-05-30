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

    internal fun determineSchedule(hour: Int, minute: Int): ScheduleState {
        // We define 24 base location categories representing hourly natural backdrops
        val baseLocations = arrayOf(
            "بحيرة الهدوء وسديم النجوم",       // Hour 0
            "جوف الليل والضباب الفضي",          // Hour 1
            "عمق الوادي المظلم وهمس الجندب",       // Hour 2
            "مرتفعات الرياح وعقبان الصخر",        // Hour 3
            "سحر ما قبل الفجر وسكون الكنف",       // Hour 4
            "أول تنفس للغابة وسكينة الأيك",       // Hour 5
            "سيمفونية الطيور وتحيات الشروق",      // Hour 6
            "ندى الصباح الباكر فوق المراعي",      // Hour 7
            "استيقاظ البراري وغزلان الجبل",       // Hour 8
            "أشعة الضحى الذهبية فوق التلال",      // Hour 9
            "خرير جداول المرتفعات المترقرقة",      // Hour 10
            "جداول الغابة الكثيفة والظلال",       // Hour 11
            "ذروة هدير الشلالات الكونية العذبة",     // Hour 12
            "رذاذ المياه المنعش تحت الشمس",       // Hour 13
            "نسائم الظهيرة المعتدلة بين الربى",      // Hour 14
            "سكون القيلولة في الواحات المخضرة",    // Hour 15
            "سحب العصر وتجمع رياح تشرين",       // Hour 16
            "أمطار الغروب الهادئة والنسيم",       // Hour 17
            "رائحة الأرض المبتلة بعد المطر",       // Hour 18
            "ديمومة الأوراق وسكون ما بعد العاصفة",   // Hour 19
            "مساء الهضاب وبداية جلباب الليل",      // Hour 20
            "رياح الجبال الباردة والرياح العاتية",   // Hour 21
            "همهمات السكون وعواء الذئب القاصي",     // Hour 22
            "سكون الوادي الفسيح وسدول الليل"        // Hour 23
        )

        // We define 6 segment phases for every 10 minutes, generating 24 * 6 = 144 unique states/sounds
        val phases = arrayOf(
            "المرحلة الأولى: الصعود المعنوي والتمهيد التدريجي للأثير",
            "المرحلة الثانية: التناغم المتوازن وذروة التبلور الصوتي",
            "المرحلة الثالثة: الاندماج الكامل والتشبع الطبيعي للأصوات",
            "المرحلة الرابعة: الارتخاء الرقيق والتدرج نحو المسار التراكمي",
            "المرحلة الخامسة: النسمات الهامسة والانجلاء الهادئ للأجواء",
            "المرحلة السادسة: التحول السلس والتأهب للإيقاع الطبيعي القادم"
        )

        val locationIndex = hour.coerceIn(0, 23)
        val segIndex = (minute / 10).coerceIn(0, 5)

        val locationName = baseLocations[locationIndex]
        val phaseName = phases[segIndex]
        val totalSessionName = "$locationName ($phaseName)"

        // Mathematical wave calculations matching the segment progress to keep it alive
        val progress = minute.toFloat() / 60.0f
        val wave = kotlin.math.sin(progress * kotlin.math.PI).toFloat()

        // 1. Birds Volume Logic
        val birdsBase = when (hour) {
            5 -> 0.4f
            6 -> 0.8f
            7 -> 0.7f
            8 -> 0.5f
            9 -> 0.3f
            10, 11 -> 0.2f
            15, 16 -> 0.25f
            else -> 0.0f
        }
        val birdsVol = (birdsBase * (0.5f + 0.5f * wave)).coerceIn(0f, 1f)

        // 2. Waterfall Volume Logic
        val waterfallBase = when (hour) {
            11 -> 0.4f
            12 -> 0.8f
            13 -> 0.7f
            14 -> 0.5f
            15 -> 0.3f
            9, 10 -> 0.2f
            16, 17 -> 0.2f
            else -> 0.08f // Soft ambient background stream
        }
        val waterfallVol = (waterfallBase * (0.6f + 0.4f * wave)).coerceIn(0f, 1f)

        // 3. Wind Volume Logic
        val windBase = when (hour) {
            13, 14 -> 0.4f
            15 -> 0.3f
            16, 17, 18 -> 0.45f
            19, 20 -> 0.5f
            21, 22 -> 0.6f
            23, 0 -> 0.3f
            1, 2 -> 0.2f
            else -> 0.15f
        }
        val windVol = (windBase * (0.7f + 0.3f * wave)).coerceIn(0.05f, 1f)

        // 4. Rain Volume Logic
        val rainBase = when (hour) {
            16 -> 0.3f
            17 -> 0.75f
            18 -> 0.5f
            19 -> 0.25f
            21, 22 -> 0.15f
            else -> 0.0f
        }
        val rainVol = (rainBase * (0.5f + 0.5f * wave)).coerceIn(0f, 1f)

        // 5. Howl Active Logic (Midnight wolf howling moments)
        val isNightTime = hour in 21..23 || hour in 0..5
        // Howl triggers specifically at segment 0 (0-9 minutes) of night hours or hour 3 segment 0
        val howlActive = (isNightTime && segIndex == 0)

        return ScheduleState(
            sessionName = totalSessionName,
            birdsVol = birdsVol,
            waterfallVol = waterfallVol,
            windVol = windVol,
            rainVol = rainVol,
            howlActive = howlActive
        )
    }
}
