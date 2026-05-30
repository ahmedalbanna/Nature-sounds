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
