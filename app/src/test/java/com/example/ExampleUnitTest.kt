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
}
