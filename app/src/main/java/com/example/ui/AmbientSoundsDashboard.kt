package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppPreference
import com.example.data.NatureSoundSynth
import com.example.data.PlaybackLog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientSoundsDashboard(
    viewModel: AmbientSoundsViewModel,
    modifier: Modifier = Modifier
) {
    val pref by viewModel.preferences.collectAsStateWithLifecycle()
    val logs by viewModel.playbackLogs.collectAsStateWithLifecycle()
    val playlists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    
    // Remember scroll state of the primary page since we represent everything in a single, flowing, gorgeous screen.
    val scrollState = rememberScrollState()

    // For manual previews toggle state (Birds, Waterfall, Wind, Rain, Howl)
    var birdsPreview by remember { mutableStateOf(false) }
    var waterfallPreview by remember { mutableStateOf(false) }
    var windPreview by remember { mutableStateOf(false) }
    var rainPreview by remember { mutableStateOf(false) }
    var howlPreview by remember { mutableStateOf(false) }

    // Synchronize UI switch states if service stops or custom previews change
    LaunchedEffect(pref.isServiceRunning) {
        if (!pref.isServiceRunning) {
            birdsPreview = false
            waterfallPreview = false
            windPreview = false
            rainPreview = false
            howlPreview = false
        }
    }

    // Trigger subtle leaf rustling sounds upon scrolling the gorgeous dashboard screen
    LaunchedEffect(scrollState.value) {
        if (pref.isServiceRunning && scrollState.isScrollInProgress) {
            com.example.service.AmbientSoundService.triggerRustle(0.12f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NaturalBg, NaturalNavBg)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. HEADER SECTION (Arabic Styling)
            TopBarSection(
                isServiceRunning = pref.isServiceRunning,
                onStart = { viewModel.startService() },
                onStop = { viewModel.stopService() }
            )

            // 2. ACTIVE SOUNDS MONITOR (Canvas wave representation)
            ActiveMonitorCard(
                pref = pref,
                isServiceRunning = pref.isServiceRunning,
                viewModel = viewModel
            )

            // 3. THE 24h TIME SIMULATOR CONTROLS
            TimeSimulatorCard(
                pref = pref,
                onTimeChanged = { hour, min -> viewModel.updateSimulatedTime(hour, min) },
                onToggleSimulation = { enable -> viewModel.toggleTimeSimulation(enable) }
            )

            // NEW: DEEP SLEEP MODE CONTROLS
            DeepSleepCard(
                pref = pref,
                onToggleEnabled = { viewModel.toggleDeepSleepEnabled(it) },
                onDurationChanged = { viewModel.updateDeepSleepDuration(it) },
                onToggleNightHowls = { viewModel.toggleIntroduceNightHowls(it) },
                onStartTimer = { viewModel.startDeepSleepTimer() },
                onCancelTimer = { viewModel.cancelDeepSleepTimer() }
            )

            // 4. MANUAL MIXER PREVIEW DECK (Direct action buttons)
            SoundMixerCard(
                pref = pref,
                birdsPreview = birdsPreview,
                waterfallPreview = waterfallPreview,
                windPreview = windPreview,
                rainPreview = rainPreview,
                howlPreview = howlPreview,
                onBirdsToggle = { active ->
                    birdsPreview = active
                    viewModel.toggleIndependentPreview("BIRDS", active)
                },
                onWaterfallToggle = { active ->
                    waterfallPreview = active
                    viewModel.toggleIndependentPreview("WATERFALL", active)
                },
                onWindToggle = { active ->
                    windPreview = active
                    viewModel.toggleIndependentPreview("WIND", active)
                },
                onRainToggle = { active ->
                    rainPreview = active
                    viewModel.toggleIndependentPreview("RAIN", active)
                },
                onHowlToggle = { active ->
                    howlPreview = active
                    viewModel.toggleIndependentPreview("HOWL", active)
                },
                onMasterVolumeChanged = { viewModel.updateMasterVolume(it) }
            )

            // Custom Playlists Section
            PlaylistsCard(
                pref = pref,
                playlists = playlists,
                onSelectPlaylist = { id -> viewModel.selectPlaylist(id) },
                onSavePlaylist = { name, birds, waterfall, wind, rain, howl ->
                    viewModel.savePlaylist(name, birds, waterfall, wind, rain, howl)
                },
                onDeletePlaylist = { playlist -> viewModel.deletePlaylist(playlist) },
                onUpdateVolumeFactor = { factor -> viewModel.updatePlaylistVolumeFactor(factor) }
            )

            // 5. THE 24-HOUR TIMELINE SCHEDULE PRESET (User requirement)
            TimelineScheduleCard(pref = pref)

            // 6. PERSISTENT WORKER LOG CONTAINER (Room logs database verification)
            WorkerLogsCard(
                logs = logs,
                onClear = { viewModel.clearLogs() }
            )
        }
    }
}

@Composable
fun TopBarSection(
    isServiceRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "الغلاف الصوتي للطبيعة",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = NaturalPrimary
                )
            )
            Text(
                text = "أصوات هادئة تعمل في الخلفية على مدار ٢٤ ساعة",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = NaturalSubtext,
                    fontSize = 12.sp
                )
            )
        }

        // Pulse Animation for Service State
        val pulseScale by rememberInfiniteTransition(label = "").animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = ""
        )

        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (isServiceRunning) NaturalPrimary else Color.Red.copy(alpha = 0.5f))
        ) {
            if (isServiceRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(NaturalPrimary.copy(alpha = 0.25f))
                )
            }
        }
    }
}

@Composable
fun ActiveMonitorCard(
    pref: AppPreference,
    isServiceRunning: Boolean,
    viewModel: AmbientSoundsViewModel
) {
    val activeSessionText = when {
        !isServiceRunning -> "الخدمة متوقفة حالياً. اضغط تشغيل أدناه للبدء السمعي."
        pref.useSimulatedTime -> getSessionNameByHour(pref.simulatedHour, pref.simulatedMinute)
        else -> {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val min = Calendar.getInstance().get(Calendar.MINUTE)
            getSessionNameByHour(hour, min)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_monitor_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NaturalBorder.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = NaturalCardBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الحالة الصوتية النشطة",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NaturalText
                    )
                )

                Button(
                    onClick = {
                        if (isServiceRunning) viewModel.stopService() else viewModel.startService()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) Color(0xFFBA1A1A) else NaturalPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("service_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "تحكم في الخدمة",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isServiceRunning) "إيقاف الخدمة" else "تشغيل الخدمة",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Wave Animation Block
            DynamicAudioWave(
                isServiceRunning = isServiceRunning,
                activeSessionName = activeSessionText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NaturalNavBg)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = activeSessionText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = NaturalPrimary,
                    fontSize = 15.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TimeSimulatorCard(
    pref: AppPreference,
    onTimeChanged: (hour: Int, minute: Int) -> Unit,
    onToggleSimulation: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NaturalBorder.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = NaturalCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "تعديل ومحاكاة دورة الزمن (٢٤ ساعة)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NaturalText
                )
            )
            Text(
                text = "اسحب شريط الساعات للتحكم واختبار تبدل الصوتيات تلقائياً فوراً دون انتظار اليوم الفعلي!",
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NaturalNavBg)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (pref.useSimulatedTime) "نظام المحاكاة اليدوي: فعال" else "التطابق مع ساعة الهاتف: فعال",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (pref.useSimulatedTime) NaturalPrimary else NaturalSubtext,
                        fontWeight = FontWeight.Bold
                    )
                )

                Switch(
                    checked = pref.useSimulatedTime,
                    onCheckedChange = { onToggleSimulation(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NaturalPrimary,
                        checkedTrackColor = NaturalAccentBright
                    ),
                    modifier = Modifier.testTag("simulation_switch")
                )
            }

            if (pref.useSimulatedTime) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الساعة الافتراضية للتشغيل:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = NaturalText)
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:00", pref.simulatedHour),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NaturalPrimary,
                            fontSize = 18.sp
                        )
                    )
                }

                Slider(
                    value = pref.simulatedHour.toFloat(),
                    onValueChange = { 
                        onTimeChanged(it.toInt(), 0)
                        if (pref.isServiceRunning) {
                            com.example.service.AmbientSoundService.triggerRustle(0.12f)
                        }
                    },
                    valueRange = 0f..23f,
                    steps = 22,
                    colors = SliderDefaults.colors(
                        thumbColor = NaturalPrimary,
                        activeTrackColor = NaturalPrimary,
                        inactiveTrackColor = NaturalBorder.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.testTag("time_slider")
                )
                
                // Explanatory subtext detailing what matches this hour
                val hourExplanation = getHourScheduleBriefExplanation(pref.simulatedHour)
                Text(
                    text = hourExplanation,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NaturalPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(NaturalAccentHighlight.copy(alpha = 0.4f))
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun SoundMixerCard(
    pref: AppPreference,
    birdsPreview: Boolean,
    waterfallPreview: Boolean,
    windPreview: Boolean,
    rainPreview: Boolean,
    howlPreview: Boolean,
    onBirdsToggle: (Boolean) -> Unit,
    onWaterfallToggle: (Boolean) -> Unit,
    onWindToggle: (Boolean) -> Unit,
    onRainToggle: (Boolean) -> Unit,
    onHowlToggle: (Boolean) -> Unit,
    onMasterVolumeChanged: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NaturalBorder.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = NaturalCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "خلاط الأصوات اليدوي والمعاينة",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NaturalText
                )
            )
            Text(
                text = "يمكنك تجربة وسماع كل فئة صوتية على حدة لتقييم التأثير، أو تعديل شدة الصوت العام:",
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Master Volume Controller
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "حجم الصوت",
                    tint = NaturalPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "الحجم العام للصوت: ${(pref.masterVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(color = NaturalText, fontWeight = FontWeight.Bold)
                )
            }
            Slider(
                value = pref.masterVolume,
                onValueChange = { 
                    onMasterVolumeChanged(it)
                    if (pref.isServiceRunning) {
                        com.example.service.AmbientSoundService.triggerRustle(0.12f)
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = NaturalPrimary,
                    activeTrackColor = NaturalPrimary
                ),
                modifier = Modifier.testTag("master_volume_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Nature Tracks Custom Grid Lists
            Text(
                text = "معاينة سماع القنوات والمسارات:",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = NaturalSubtext)
            )
            Spacer(modifier = Modifier.height(6.dp))

            MixerRowItem(
                name = "تغريد الطيور والعصافير",
                sub = "صوت طبيعي فائق الصفاء والراحة للصباح",
                isActive = birdsPreview,
                enabledService = pref.isServiceRunning,
                onToggle = onBirdsToggle
            )
            MixerRowItem(
                name = "هدوء مياه الشلال",
                sub = "ضجيج أبيض عميق للمساعدة على التركيز والدراسة",
                isActive = waterfallPreview,
                enabledService = pref.isServiceRunning,
                onToggle = onWaterfallToggle
            )
            MixerRowItem(
                name = "صفير رياح الغابات",
                sub = "نسيم رياح الخريف يمنح إحساس الاتساع التام",
                isActive = windPreview,
                enabledService = pref.isServiceRunning,
                onToggle = onWindToggle
            )
            MixerRowItem(
                name = "تساقط قطرات المطر",
                sub = "قطرات مطر دافئة على النوافذ للمساعدة على النوم العالي",
                isActive = rainPreview,
                enabledService = pref.isServiceRunning,
                onToggle = onRainToggle
            )
            MixerRowItem(
                name = "عواء الحيوانات (الذئب)",
                sub = "صدى عواء حيواني لمدة دقيقة في جوف الليل المظلم",
                isActive = howlPreview,
                enabledService = pref.isServiceRunning,
                onToggle = onHowlToggle
            )
        }
    }
}

@Composable
fun MixerRowItem(
    name: String,
    sub: String,
    isActive: Boolean,
    enabledService: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(NaturalNavBg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) NaturalPrimary else NaturalText
                )
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontSize = 10.sp)
            )
        }

        Button(
            onClick = { onToggle(!isActive) },
            enabled = enabledService,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) NaturalPrimary else NaturalAccentLight,
                contentColor = if (isActive) Color.White else NaturalText
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = if (!enabledService) "الخدمة مطفأة" else if (isActive) "نشط الآن" else "استماع معاينة",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                )
        }
    }
}

@Composable
fun TimelineScheduleCard(pref: AppPreference) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NaturalBorder.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = NaturalCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "الجدول الزمني للتشغيل (دورة ٢٤ ساعة)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NaturalText
                )
            )
            Text(
                text = "تم برمجته بأعلى المعايير وترك فضاءات صامتة كبيرة ومناسبة وفق الطلب:",
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val timelineItems = listOf(
                TimelineItemData("06:00 - 07:00", "سيمفونية الصباح وأصوات العصافير", "عصافير (مكتملة) + شلال خفيف + رياح هادئة", 6),
                TimelineItemData("07:00 - 12:00", "فراغ صامت كبير (فترة صمت)", "جميع القنوات متوقفة لمزيد من السكينة التامة", 7),
                TimelineItemData("12:00 - 13:00", "شلال منتصف النهار المنعش", "شلال (مكتمل) + هواء ورياح الغابة", 12),
                TimelineItemData("13:00 - 17:00", "فراغ صامت كبير (فترة صمت الثانية)", "هدوء تام لتجنب التشويش على المهام", 13),
                TimelineItemData("17:00 - 18:00", "مطر ورياح غروب الشمس", "مطر (مكتمل) + رياح المساء المنعشة", 17),
                TimelineItemData("18:00 - 03:00", "فراغ صمت المساء الهادئ (تجهيز النوم)", "صمت مطبق ونوم ساكن مريح جداً", 18),
                TimelineItemData("03:00 - 03:01", "عواء الذئب منتصف الليل (لمدة دقيقة)", "عواء الذئب المنفرد ينطلق تلقائياً بوسط الهدوء المطبق", 3),
                TimelineItemData("03:01 - 06:00", "فراغ السحر وهدوء ما قبل الفجر", "فترة هدوء خاشعة تسبق شروق شمس جديدة", 4)
            )

            timelineItems.forEach { item ->
                val isActiveHour = if (pref.useSimulatedTime) {
                    if (item.triggerHour == 3) {
                        pref.simulatedHour == 3 && pref.simulatedMinute == 0
                    } else if (item.triggerHour == 4) {
                        pref.simulatedHour == 3 && pref.simulatedMinute > 0 || pref.simulatedHour in 4..5
                    } else if (item.triggerHour == 7) {
                        pref.simulatedHour in 7..11
                    } else if (item.triggerHour == 13) {
                        pref.simulatedHour in 13..16
                    } else if (item.triggerHour == 18) {
                        pref.simulatedHour in 18..23 || pref.simulatedHour in 0..2
                    } else {
                        pref.simulatedHour == item.triggerHour
                    }
                } else {
                    val cal = Calendar.getInstance()
                    val hr = cal.get(Calendar.HOUR_OF_DAY)
                    val mn = cal.get(Calendar.MINUTE)
                    if (item.triggerHour == 3) {
                        hr == 3 && mn == 0
                    } else if (item.triggerHour == 4) {
                        hr == 3 && mn > 0 || hr in 4..5
                    } else if (item.triggerHour == 7) {
                        hr in 7..11
                    } else if (item.triggerHour == 13) {
                        hr in 13..16
                    } else if (item.triggerHour == 18) {
                        hr in 18..23 || hr in 0..2
                    } else {
                        hr == item.triggerHour
                    }
                }

                TimelineRow(item = item, isActiveNow = isActiveHour)
            }
        }
    }
}

data class TimelineItemData(
    val time: String,
    val title: String,
    val sounds: String,
    val triggerHour: Int
)

@Composable
fun TimelineRow(item: TimelineItemData, isActiveNow: Boolean) {
    val ringColor by animateColorAsState(
        targetValue = if (isActiveNow) NaturalPrimary else NaturalBorder,
        label = "border"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = 1.dp,
                color = if (isActiveNow) NaturalPrimary.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                if (isActiveNow) NaturalAccentHighlight.copy(alpha = 0.4f) else Color.Transparent
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Active Indicator node
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(ringColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isActiveNow) NaturalPrimary else NaturalText
                    )
                )

                Text(
                    text = item.time,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isActiveNow) NaturalPrimary else NaturalSubtext
                    )
                )
            }

            Text(
                text = item.sounds,
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontSize = 11.sp)
            )
        }
    }
}

@Composable
fun WorkerLogsCard(
    logs: List<PlaybackLog>,
    onClear: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NaturalBorder.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = NaturalCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل التحقق والأنشطة بالخلفية (قاعدة بيانات Room)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NaturalText
                    )
                )

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "عرض",
                        tint = NaturalPrimary
                    )
                }
            }
            Text(
                text = "يتحقق هذا الجدار ويوثق استمرار عمل مشغل الصوتيات بالخلفية بدقة.",
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClear) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "مسح السجل", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "تفريغ السجل", style = MaterialTheme.typography.labelMedium.copy(color = Color.Red))
                    }
                }

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "السجل فارغ حالياً. سيوثق المشغل الأنشطة فور تفعيله.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = NaturalSubtext, textAlign = TextAlign.Center)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        logs.take(30).forEach { log ->
                            LogItemRow(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: PlaybackLog) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateString = timeFormat.format(Date(log.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(NaturalNavBg)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.sessionName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (log.isUserTriggered) NaturalPrimary else NaturalText
                )
            )
            Text(
                text = "${log.activeSounds} | ساعة التشغيل: ${log.hourOfDay}:00",
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontSize = 10.sp)
            )
        }

        Text(
            text = dateString,
            style = MaterialTheme.typography.bodySmall.copy(
                color = NaturalSubtext,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp
            )
        )
    }
}

// --- LOGICAL HELPERS ---

fun getSessionNameByHour(hour: Int, minute: Int): String {
    return when {
        hour == 6 -> "سيمفونية الصباح وأصوات العصافير الهادئة"
        hour == 12 -> "شلال منتصف النهار المنعش والمريح"
        hour == 17 -> "مطر ورياح غروب الشمس الباعثة للاسترخاء"
        hour == 3 && minute == 0 -> "عواء ذئب حاد في عمق سكون الليل المطبق"
        else -> "صمت تام وفضاء هادئ للراحة والنوم (" + when(hour) {
            in 7..11 -> "فراغ الصباح"
            in 13..16 -> "فراغ الظهيرة"
            in 18..23 -> "فراغ الليل الهادئ للراحة والنوم"
            in 0..2 -> "فراغ جوف الليل الساكن"
            else -> "فراغ ما قبل الفجر الروحاني"
        } + ")"
    }
}

fun getHourScheduleBriefExplanation(hour: Int): String {
    return when (hour) {
        6 -> "الفترة النشطة لـ العصافير (٠٦:٠٠ - ٠٧:٠٠): يعمل صوت العصافير الممتاز بجرعة كاملة مع مسحة من خرير الشلال وهبوب الرياح المنعشة لحث الطاقة الصباحية."
        12 -> "الفترة النشطة لـ الشلال الرئيسي (١٢:٠٠ - ١٣:٠٠): تدفق مياه جارية ثقيلة ومستمرة مع نسمة هواء الخريف لتحقيق التركيز والهدوء."
        17 -> "الفترة النشطة لـ المطر والرياح الغروبية (١٧:٠٠ - ١٨:٠٠): صوت الرعد الخفيف وتساقط المطر على النوافذ يهدئ العقل بعد يوم عمل شاق."
        3 -> "الفترة الفاصلة لعواء الذئب المذهل! (٠٣:٠٠ - ٠٣:٠١): لثانية ودقيقة واحدة يعوي الذئب الغامض بنهاية الليل ثم يعود الصمت المطبق حامياً النوم والاستقرار."
        else -> "الفترة الخالية المتسعة (فراغ هادئ صامت): لا أصوات طبيعية نشطة هنا لتامكين الصمت المطبق والراحة من الضوضاء البيضاء."
    }
}

@Composable
fun DynamicAudioWave(
    isServiceRunning: Boolean,
    activeSessionName: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue =  0f,
        targetValue =  (2 * kotlin.math.PI).toFloat(),
        animationSpec =  infiniteRepeatable(
            animation =  tween(2000, easing =  LinearEasing),
            repeatMode =  RepeatMode.Restart
        ),
        label =  "phase"
    )

    Canvas(modifier =  modifier) {
        val width =  size.width
        val height =  size.height
        val centerY =  height / 2f

        if (isServiceRunning && !activeSessionName.contains("فراغ") && !activeSessionName.contains("صمت")) {
            // Active sound: Draw animated overlapping sine waves
            val waveCount =  3
            for (w in 0 until waveCount) {
                val path =  Path()
                path.moveTo(0f, centerY)

                val amplitude =  (height * 0.25f) / (w + 1)
                val frequency =  0.012f * (w + 1)
                val speedFactor =  if (w % 2 == 0) 1 else -1

                for (x in 0..width.toInt() step 6) {
                    val angle =  x * frequency + phase * speedFactor + (w * kotlin.math.PI.toFloat() / 2f)
                    val y =  centerY + kotlin.math.sin(angle) * amplitude
                    if (x == 0) path.moveTo(0f, y) else path.lineTo(x.toFloat(), y)
                }

                drawPath(
                    path =  path,
                    color =  NaturalPrimary.copy(alpha =  0.5f - (w * 0.15f)),
                    style =  Stroke(width =  2.dp.toPx())
                )
            }
        } else {
            // Sleep/Silence State: Draw calm gentle starlight pulsations
            // Horizontal dashed line
            drawLine(
                color =  NaturalSubtext.copy(alpha =  0.3f),
                start =  Offset(0f, centerY),
                end =  Offset(width, centerY),
                strokeWidth =  2.dp.toPx(),
                pathEffect =  PathEffect.dashPathEffect(floatArrayOf(15f, 15f), phase * 10f)
            )
        }
    }
}

@Composable
fun DeepSleepCard(
    pref: AppPreference,
    onToggleEnabled: (Boolean) -> Unit,
    onDurationChanged: (Int) -> Unit,
    onToggleNightHowls: (Boolean) -> Unit,
    onStartTimer: () -> Unit,
    onCancelTimer: () -> Unit
) {
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Update local time every second when timer is active to display precise countdown
    LaunchedEffect(pref.isDeepSleepTimerActive) {
        if (pref.isDeepSleepTimerActive) {
            while (true) {
                currentTimeMillis = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deep_sleep_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NaturalBorder.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = NaturalCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "وضع النوم العميق المتلاشي (Deep Sleep) 😴",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NaturalText
                        )
                    )
                    Text(
                        text = "خفت تدريجي للأصوات لمساعدتك على النوم العميق والهانئ تلقائياً.",
                        style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Toggle for Deep Sleep Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NaturalNavBg)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "تمكين وضع النوم العميق",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = NaturalText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "يتلاشى الصوت تدريجياً وبنعومة حتى الصمت الكامل.",
                        style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontSize = 10.sp)
                    )
                }

                Switch(
                    checked = pref.isDeepSleepEnabled,
                    onCheckedChange = { onToggleEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NaturalPrimary,
                        checkedTrackColor = NaturalAccentBright
                    ),
                    modifier = Modifier.testTag("deep_sleep_switch")
                )
            }

            if (pref.isDeepSleepEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Countdown Timer or Options Configuration
                if (pref.isDeepSleepTimerActive) {
                    // Running state countdown display
                    val elapsedMillis = currentTimeMillis - pref.deepSleepStartTimeMillis
                    val totalDurationMillis = pref.deepSleepDurationMinutes * 60 * 1000L
                    val remainingMillis = (totalDurationMillis - elapsedMillis).coerceAtLeast(0L)
                    val remainingMinutes = remainingMillis / 60000
                    val remainingSeconds = (remainingMillis % 60000) / 1000

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NaturalAccentHighlight.copy(alpha = 0.5f))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "جاري تلاشي الأصوات تدريجياً... 💤",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = NaturalPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%02d:%02d", remainingMinutes, remainingSeconds),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = NaturalPrimary,
                                letterSpacing = 2.sp
                            )
                        )
                        Text(
                            text = "الوقت المتبقي حتى الصمت والراحة التامة",
                            style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onCancelTimer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFBA1A1A),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("cancel_deep_sleep_button")
                        ) {
                            Text("إلغاء وإيقاف مؤقت النوم")
                        }
                    }
                } else {
                    // Config state
                    Text(
                        text = "اختر مدة التلاشي حتى الصمت (بالدقائق):",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NaturalSubtext
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val durations = listOf(1, 5, 15, 30, 60)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        durations.forEach { mins ->
                            val isSelected = pref.deepSleepDurationMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NaturalPrimary else NaturalNavBg)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) NaturalPrimary else NaturalBorder.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onDurationChanged(mins) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mins د",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else NaturalText
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animal Howls Toggle option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NaturalNavBg)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "إدخال عواء ذئاب دوري ليلاً 🐺",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = NaturalText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "تشغيل عواء دافئ خافت مع أصوات الليل بدلاً من الصمت التام لتعزيز شعور البراري الطمأنيني.",
                                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontSize = 10.sp)
                            )
                        }

                        Switch(
                            checked = pref.introduceNightHowls,
                            onCheckedChange = { onToggleNightHowls(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NaturalPrimary,
                                checkedTrackColor = NaturalAccentBright
                            ),
                            modifier = Modifier.testTag("night_howl_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onStartTimer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NaturalPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("start_deep_sleep_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "بدء مؤقت النوم", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ابدأ رحلة النوم الهادئ التدريجي",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsCard(
    pref: AppPreference,
    playlists: List<com.example.data.CustomPlaylist>,
    onSelectPlaylist: (Int?) -> Unit,
    onSavePlaylist: (String, Float, Float, Float, Float, Float) -> Unit,
    onDeletePlaylist: (com.example.data.CustomPlaylist) -> Unit,
    onUpdateVolumeFactor: (Float) -> Unit
) {
    var isFormExpanded by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var birdsVol by remember { mutableStateOf(0.4f) }
    var waterfallVol by remember { mutableStateOf(0.0f) }
    var windVol by remember { mutableStateOf(0.3f) }
    var rainVol by remember { mutableStateOf(0.5f) }
    var howlVol by remember { mutableStateOf(0.0f) }
    
    val activePlaylist = playlists.find { it.id == pref.activePlaylistId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playlists_card"),
        colors = CardDefaults.cardColors(containerColor = NaturalCardBg),
        border = BorderStroke(1.dp, NaturalBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "قوائم التشغيل المخصصة 🎵",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = NaturalPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "صمّم عوالمك البيئية بنفسك بدلاً من الجدول التلقائي",
                        style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontSize = 11.sp)
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = NaturalPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Mode selection: Auto Timeline vs custom playlists
            Text(
                text = "وضع التشغيل الحالي:",
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalText, fontWeight = FontWeight.SemiBold)
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto Timeline Mode Button
                val isAutoSelected = pref.activePlaylistId == null
                Button(
                    onClick = { onSelectPlaylist(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAutoSelected) NaturalPrimary else NaturalNavBg,
                        contentColor = if (isAutoSelected) Color.White else NaturalText
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("timeline_mode_button"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "الجدول التلقائي 🗓️",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Active Custom Playlist Button (just to show selection state)
                val isCustomSelected = pref.activePlaylistId != null
                Button(
                    onClick = { /* Just indicates style */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCustomSelected) NaturalAccentBright else NaturalNavBg,
                        contentColor = if (isCustomSelected) Color.Black else NaturalSubtext
                    ),
                    enabled = isCustomSelected,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (isCustomSelected) "قائمة مفعلة 🟢" else "لم يتم اختيار قائمة",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Global Vol for Playlist
            if (activePlaylist != null) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NaturalNavBg)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "التحكم بالصوت العام للقائمة: ${activePlaylist.name}",
                            style = MaterialTheme.typography.bodySmall.copy(color = NaturalText, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${(pref.playlistVolumeFactor * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(color = NaturalPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                    
                    Slider(
                        value = pref.playlistVolumeFactor,
                        onValueChange = onUpdateVolumeFactor,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = NaturalPrimary,
                            activeTrackColor = NaturalPrimary,
                            inactiveTrackColor = NaturalBorder
                        ),
                        modifier = Modifier.testTag("playlist_global_volume_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playlists List
            Text(
                text = "قوائم التشغيل المحفوظة:",
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontWeight = FontWeight.SemiBold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = "لا توجد قوائم تشغيل مخصصة حالياً. استخدم النموذج بالأسفل لإضافة أول توليفة أصوات مخصصة!",
                    style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    playlists.forEach { playlist ->
                        val isActive = playlist.id == pref.activePlaylistId
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (isActive) NaturalPrimary else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .background(if (isActive) NaturalNavBg else NaturalCardBg)
                                .clickable { onSelectPlaylist(playlist.id) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isActive) NaturalPrimary else NaturalText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                
                                // Show badge levels
                                val activeTones = mutableListOf<String>()
                                if (playlist.birdsVolume > 0.01f) activeTones.add("🐦 ${(playlist.birdsVolume * 100).toInt()}%")
                                if (playlist.waterfallVolume > 0.01f) activeTones.add("🌊 ${(playlist.waterfallVolume * 100).toInt()}%")
                                if (playlist.windVolume > 0.01f) activeTones.add("🍃 ${(playlist.windVolume * 100).toInt()}%")
                                if (playlist.rainVolume > 0.01f) activeTones.add("🌧️ ${(playlist.rainVolume * 100).toInt()}%")
                                if (playlist.howlVolume > 0.01f) activeTones.add("🐺 ${(playlist.howlVolume * 100).toInt()}%")
                                
                                Text(
                                    text = activeTones.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontSize = 10.sp),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isActive) {
                                    Text(
                                        text = "نشطة 🟢",
                                        style = MaterialTheme.typography.bodySmall.copy(color = NaturalAccentBright, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                } else {
                                    IconButton(
                                        onClick = { onSelectPlaylist(playlist.id) },
                                        modifier = Modifier.size(28.dp).testTag("activate_playlist_${playlist.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "تفعيل القائمة",
                                            tint = NaturalText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeletePlaylist(playlist) },
                                    modifier = Modifier.size(28.dp).testTag("delete_playlist_${playlist.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف القائمة",
                                        tint = Color.Red.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expandable Creation Form Button
            Button(
                onClick = { isFormExpanded = !isFormExpanded },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NaturalNavBg,
                    contentColor = NaturalPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toggle_create_playlist_button")
            ) {
                Icon(
                    imageVector = if (isFormExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFormExpanded) "إغلاق نافذة التصميم" else "صمّم تركيبتك الصوتية الخاصة 🛠️",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            AnimatedVisibility(visible = isFormExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = "المصمّم الصوتي المخصص:",
                        style = MaterialTheme.typography.bodySmall.copy(color = NaturalText, fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // TextField for Playlist Name
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("اسم قائمة التشغيل", color = NaturalSubtext) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NaturalText,
                            unfocusedTextColor = NaturalText,
                            focusedContainerColor = NaturalNavBg,
                            unfocusedContainerColor = NaturalNavBg,
                            focusedBorderColor = NaturalPrimary,
                            unfocusedBorderColor = NaturalBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlist_name_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mix Sliders:
                    // 1. Birds
                    MixSliderItem(
                        label = "عصافير الصباح الباكر 🐦",
                        value = birdsVol,
                        onValueChange = { birdsVol = it },
                        tag = "birds_slider"
                    )

                    // 2. Waterfall
                    MixSliderItem(
                        label = "هدير الشلالات المتدفقة 🌊",
                        value = waterfallVol,
                        onValueChange = { waterfallVol = it },
                        tag = "waterfall_slider"
                    )

                    // 3. Wind
                    MixSliderItem(
                        label = "نسيم رياح التلال 🍃",
                        value = windVol,
                        onValueChange = { windVol = it },
                        tag = "wind_slider"
                    )

                    // 4. Rain
                    MixSliderItem(
                        label = "أمطار الغروب الرقيقة 🌧️",
                        value = rainVol,
                        onValueChange = { rainVol = it },
                        tag = "rain_slider"
                    )

                    // 5. Howl
                    MixSliderItem(
                        label = "عواء ذئاب البراري 🐺",
                        value = howlVol,
                        onValueChange = { howlVol = it },
                        tag = "howl_slider"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save Button
                    Button(
                        onClick = {
                            if (playlistName.isNotBlank()) {
                                onSavePlaylist(playlistName, birdsVol, waterfallVol, windVol, rainVol, howlVol)
                                // Reset form values
                                playlistName = ""
                                birdsVol = 0.4f
                                waterfallVol = 0.0f
                                windVol = 0.3f
                                rainVol = 0.5f
                                howlVol = 0.0f
                                isFormExpanded = false
                            }
                        },
                        enabled = playlistName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NaturalPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_playlist_button")
                    ) {
                        Text(
                            text = "حفظ وتشغيل القائمة المخصصة ✨",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MixSliderItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalSubtext, fontSize = 11.sp)
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(color = NaturalPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = NaturalPrimary,
                activeTrackColor = NaturalPrimary,
                inactiveTrackColor = NaturalBorder
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
