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
}
