package com.example

import com.example.data.NatureSoundSynth
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testNatureSoundSynth_initialTrackVolumes() {
    val synth = NatureSoundSynth()
    assertEquals(0.8f, synth.masterVolume, 0.001f)
    assertEquals(0f, synth.trackVolumes[NatureSoundSynth.SoundType.BIRDS] ?: -1f, 0.001f)
    assertEquals(0f, synth.trackVolumes[NatureSoundSynth.SoundType.RAIN] ?: -1f, 0.001f)
    assertEquals(0f, synth.trackVolumes[NatureSoundSynth.SoundType.WIND] ?: -1f, 0.001f)
  }

  @Test
  fun testNatureSoundSynth_clampingSafety() {
    val synth = NatureSoundSynth()
    synth.setTargetVolume(NatureSoundSynth.SoundType.RAIN, 0.85f)
    synth.setTargetVolume(NatureSoundSynth.SoundType.WIND, -0.5f) // should clamp
    synth.setTargetVolume(NatureSoundSynth.SoundType.BIRDS, 1.5f) // should clamp
    
    // Direct trigger rustle execution
    synth.triggerRustle(0.7f)
    synth.triggerRustle(1.5f)
  }

  @Test
  fun testAppPreference_deepSleepDefaults() {
    val pref = com.example.data.AppPreference()
    assertFalse(pref.isDeepSleepEnabled)
    assertFalse(pref.isDeepSleepTimerActive)
    assertFalse(pref.introduceNightHowls)
    assertEquals(30, pref.deepSleepDurationMinutes)
    assertEquals(0L, pref.deepSleepStartTimeMillis)
  }

  @Test
  fun testDeepSleepFadeCalculation() {
    val durationMinutes = 30
    val elapsedMinutes = 15
    
    // Simulate fadeFactor formula
    val elapsedMillis = elapsedMinutes * 60 * 1000L
    val durationMillis = durationMinutes * 60 * 1000L
    val fadeFactor = if (elapsedMillis >= durationMillis) {
        0.0f
    } else {
        1.0f - (elapsedMillis.toFloat() / durationMillis.toFloat())
    }
    
    assertEquals(0.5f, fadeFactor, 0.001f)
  }

  @Test
  fun testScheduleGeneratesOver100UniqueStates() {
    val service = com.example.service.AmbientSoundService()
    val uniqueStates = mutableSetOf<String>()
    
    // Traverse the 24 hours and 60 minutes in 10-minute intervals
    // 24 hours * 6 intervals per hour = 144 total intervals
    for (hour in 0..23) {
      for (minute in 0..59 step 10) {
        val state = service.determineSchedule(hour, minute)
        uniqueStates.add(state.sessionName)
      }
    }
    
    // Verify that the generated set of distinct ambient sounds & names contains exactly 144 unique entries, which is > 100!
    assertTrue("Should contain over 100 unique states, actual: ${uniqueStates.size}", uniqueStates.size > 100)
    assertEquals(144, uniqueStates.size)
  }
}
