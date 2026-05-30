package com.example.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.sin

class NatureSoundSynth {
    private val TAG = "NatureSoundSynth"
    private val SAMPLE_RATE = 22050
    
    // Core engine thread
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var synthThread: Thread? = null

    // Volumes (smooth interpolation coefficients)
    var masterVolume = 0.8f
    val trackVolumes = mutableMapOf(
        SoundType.BIRDS to 0f,
        SoundType.WATERFALL to 0f,
        SoundType.WIND to 0f,
        SoundType.RAIN to 0f,
        SoundType.HOWL to 0f
    )
    
    // Target volumes for cross-fades
    private val targetVolumes = mutableMapOf(
        SoundType.BIRDS to 0f,
        SoundType.WATERFALL to 0f,
        SoundType.WIND to 0f,
        SoundType.RAIN to 0f,
        SoundType.HOWL to 0f
    )

    enum class SoundType {
        BIRDS, WATERFALL, WIND, RAIN, HOWL
    }

    // Individual Generators
    private val birdsGen = BirdsGenerator()
    private val waterfallGen = WaterfallGenerator()
    private val windGen = WindGenerator()
    private val rainGen = RainGenerator()
    private val howlGen = HowlGenerator()
    private val leavesGen = RustlingLeavesGenerator()

    fun start() {
        if (isPlaying) return
        isPlaying = true
        
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }
            
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
            isPlaying = false
            return
        }

        synthThread = Thread {
            renderLoop()
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        Log.d(TAG, "Synthesis engine started")
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        
        try {
            synthThread?.join(500)
        } catch (e: Exception) {
            Log.e(TAG, "Error joining thread", e)
        }
        
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        
        audioTrack = null
        synthThread = null
        Log.d(TAG, "Synthesis engine stopped")
    }

    fun setTargetVolume(type: SoundType, volume: Float) {
        synchronized(targetVolumes) {
            targetVolumes[type] = volume.coerceIn(0f, 1f)
        }
    }

    fun forceHowl() {
        howlGen.trigger()
    }

    fun isHowlPlaying(): Boolean {
        return howlGen.isPlaying()
    }

    fun triggerRustle(intensity: Float) {
        leavesGen.trigger(intensity)
    }

    // Single buffer synthesis loop
    private fun renderLoop() {
        val bufferCapacity = 512
        val buffer = ShortArray(bufferCapacity)
        
        while (isPlaying) {
            // Volume Interpolation (Smooth cross-fade)
            synchronized(targetVolumes) {
                for (type in SoundType.values()) {
                    val currentVal = trackVolumes[type] ?: 0f
                    val targetVal = targetVolumes[type] ?: 0f
                    if (currentVal != targetVal) {
                        val diff = targetVal - currentVal
                        // Interpolate slowly: 1% per buffer chunk
                        val step = 0.005f
                        val newVal = if (Math.abs(diff) < step) {
                            targetVal
                        } else {
                            currentVal + Math.copySign(step, diff)
                        }
                        trackVolumes[type] = newVal
                    }
                }
            }

            val bVol = trackVolumes[SoundType.BIRDS] ?: 0f
            val wVol = trackVolumes[SoundType.WATERFALL] ?: 0f
            val ndVol = trackVolumes[SoundType.WIND] ?: 0f
            val rVol = trackVolumes[SoundType.RAIN] ?: 0f
            val hVol = trackVolumes[SoundType.HOWL] ?: 0f

            for (i in 0 until bufferCapacity) {
                // Generate individual DSP samples
                val sBirds = if (bVol > 0.01f) birdsGen.nextSample() else 0f
                val sWaterfall = if (wVol > 0.01f) waterfallGen.nextSample() else 0f
                val sWind = if (ndVol > 0.01f) windGen.nextSample() else 0f
                val sRain = if (rVol > 0.01f) rainGen.nextSample() else 0f
                
                // Howl can run independently if forced/active
                val sHowl = howlGen.nextSample() // internal state handles active vs silent
                
                // Active interactive rustling leaves sound
                val sLeaves = leavesGen.nextSample()
                
                // Mix tracks with volume
                var mixed = (sBirds * bVol) +
                        (sWaterfall * wVol * 0.7f) +  // slightly dampen base rumble
                        (sWind * ndVol * 0.8f) +
                        (sRain * rVol * 0.8f) +
                        (sHowl * hVol) +
                        sLeaves

                // Apply master volume
                mixed *= masterVolume
                
                // Soft clipping limiter
                if (mixed > 1.0f) mixed = 1.0f
                if (mixed < -1.0f) mixed = -1.0f

                // Convert to PCM 16-bit
                buffer[i] = (mixed * 32767f).toInt().toShort()
            }

            audioTrack?.let {
                if (isPlaying) {
                    it.write(buffer, 0, bufferCapacity)
                }
            } ?: break
        }
    }

    // --- INNER DSP GENERATORS ---

    private class WaterfallGenerator {
        private var lastOut = 0f
        
        fun nextSample(): Float {
            // Brown Noise Approximated: Integrating white noise with a high leaking factor
            val white = ThreadLocalRandom.current().nextFloat() * 2f - 1f
            lastOut = 0.992f * lastOut + 0.008f * white
            return (lastOut * 12.0f).coerceIn(-1.0f, 1.0f)
        }
    }

    private class RainGenerator {
        private var lastOut = 0f
        private var clickTime = 0
        private var clickDecay = 0f
        private var clickAngle = 0f

        fun nextSample(): Float {
            // Shower backdrop (Leaky filtered noise)
            val white = ThreadLocalRandom.current().nextFloat() * 2f - 1f
            lastOut = 0.94f * lastOut + 0.06f * white
            val background = lastOut * 3f

            // Crackles of drops contacting leaves/ground
            if (clickTime <= 0) {
                // Occasional trigger
                if (ThreadLocalRandom.current().nextFloat() < 0.0006f) {
                    clickTime = ThreadLocalRandom.current().nextInt(40, 180)
                    clickDecay = 0.4f + ThreadLocalRandom.current().nextFloat() * 0.6f
                    clickAngle = 0f
                }
            }

            var click = 0f
            if (clickTime > 0) {
                clickAngle += 1.4f // high frequency chirp around 4.5kHz
                click = sin(clickAngle) * clickDecay * 0.35f
                clickDecay *= 0.96f
                clickTime--
            }

            return (background * 0.65f + click * 0.35f).coerceIn(-1.0f, 1.0f)
        }
    }

    private class WindGenerator {
        private var lastOut = 0f
        private var angle = 0f

        fun nextSample(): Float {
            val white = ThreadLocalRandom.current().nextFloat() * 2f - 1f
            
            // Sweep bandpass alpha very slowly (gust variation)
            angle += 0.00008f
            val alpha = 0.02f + 0.015f * sin(angle)
            
            // Wind strength envelope (gusts)
            val gusts = 0.5f + 0.5f * sin(angle * 0.65f)
            
            lastOut = (1f - alpha) * lastOut + alpha * white
            return (lastOut * 4.5f * gusts).coerceIn(-1.0f, 1.0f)
        }
    }

    private class BirdsGenerator {
        private val SAMPLE_RATE_F = 22050f
        private var timer = 0
        private var angle = 0f
        private var baseFreq = 2200f
        private var duration = 0
        private var elapsed = 0
        private var nextDelay = 0
        
        // Multi-mode chirp variations
        private var chirpType = 0

        fun nextSample(): Float {
            if (timer <= 0) {
                // Check if current idle is over
                if (timer < 0) {
                    timer++
                    return 0f
                }
                
                // Trigger next chirp session
                if (ThreadLocalRandom.current().nextFloat() < 0.0004f) {
                    duration = ThreadLocalRandom.current().nextInt(1800, 4500) // 80 - 200 ms
                    elapsed = 0
                    baseFreq = 2000f + ThreadLocalRandom.current().nextFloat() * 1200f
                    chirpType = ThreadLocalRandom.current().nextInt(0, 3)
                    timer = duration
                }
                return 0f
            }

            elapsed++
            val progress = elapsed.toFloat() / duration
            var currentFreq = baseFreq
            var amp = 0.15f

            when (chirpType) {
                0 -> {
                    // Sweeping up rapidly
                    currentFreq = baseFreq + (1500f * progress)
                    amp *= sin(progress * PI.toFloat()) // Bell envelope
                }
                1 -> {
                    // Sweeping down rapidly
                    currentFreq = baseFreq - (1000f * progress)
                    amp *= (1f - progress) // decay envelope
                }
                2 -> {
                    // Quick trill / double sweep
                    val vibrato = 250f * sin(elapsed * 0.015f)
                    currentFreq = baseFreq + vibrato
                    amp *= sin(progress * PI.toFloat())
                }
            }

            angle += (2f * PI.toFloat() * currentFreq) / SAMPLE_RATE_F
            if (angle > 2 * PI) angle -= (2 * PI.toFloat())

            timer--
            if (timer == 0) {
                // Rest period before next chirps
                timer = -ThreadLocalRandom.current().nextInt(12000, 35000)
            }

            return sin(angle) * amp
        }
    }

    private class HowlGenerator {
        private val SAMPLE_RATE_F = 22050f
        private var howlTimer = 0
        private var howlDuration = 0
        private var howlElapsed = 0
        private var howlAngle = 0f
        private var startFreq = 240f
        private var peakFreq = 540f

        fun trigger() {
            if (howlTimer <= 0) {
                howlDuration = 130000 // approx 6 seconds
                howlElapsed = 0
                startFreq = 230f + ThreadLocalRandom.current().nextFloat() * 40f
                peakFreq = 500f + ThreadLocalRandom.current().nextFloat() * 100f
                howlTimer = howlDuration
            }
        }

        fun isPlaying(): Boolean = howlTimer > 0

        fun nextSample(): Float {
            if (howlTimer <= 0) return 0f

            howlElapsed++
            val progress = howlElapsed.toFloat() / howlDuration
            var currentFreq = startFreq
            var envelope = 0f

            // Howl profile:
            // 0% -> 25%: steep rise
            // 25% -> 75%: vibrato peak crest
            // 75% -> 100%: long sliding descent and fade
            if (progress < 0.25f) {
                val p = progress / 0.25f
                currentFreq = startFreq + (peakFreq - startFreq) * p
                envelope = p * 0.25f
            } else if (progress < 0.75f) {
                val p = (progress - 0.25f) / 0.5f
                // Add warm animalistic vocal vibrato: rapid, small frequency adjustments
                val vibrato = 22f * sin(howlElapsed.toFloat() * 0.0035f)
                currentFreq = peakFreq + vibrato
                envelope = 0.25f
            } else {
                val p = (progress - 0.75f) / 0.25f
                currentFreq = peakFreq - (peakFreq - startFreq * 0.85f) * p
                envelope = (1f - p) * 0.25f
            }

            howlAngle += (2f * PI.toFloat() * currentFreq) / SAMPLE_RATE_F
            if (howlAngle > 2 * PI) howlAngle -= (2 * PI.toFloat())

            howlTimer--
            return sin(howlAngle) * envelope
        }
    }

    private class RustlingLeavesGenerator {
        private var decay = 0f
        private var lastOut = 0f
        private var clickTime = 0
        private var clickAngle = 0f

        fun trigger(intensity: Float) {
            decay = if (decay <= 0f) {
                intensity.coerceIn(0f, 1f)
            } else {
                (decay + intensity * 0.4f).coerceIn(0f, 1.0f)
            }
        }

        fun nextSample(): Float {
            if (decay < 0.001f) {
                decay = 0f
                return 0f
            }

            // High-pass filtered noise for dry leaves friction
            val white = ThreadLocalRandom.current().nextFloat() * 2f - 1f
            val highPass = white - lastOut
            lastOut = white

            // Rustle clicks representing crisp leaves touching each other
            if (clickTime <= 0) {
                if (ThreadLocalRandom.current().nextFloat() < 0.04f * decay) {
                    clickTime = ThreadLocalRandom.current().nextInt(15, 60)
                    clickAngle = 0f
                }
            }

            var click = 0f
            if (clickTime > 0) {
                clickAngle += 1.9f // high-frequency rustle sound chirp
                click = sin(clickAngle) * 0.3f
                clickTime--
            }

            // High decay rate since sample rate is 22050Hz (tapers down within ~500ms)
            decay *= 0.9996f

            return ((highPass * 0.08f) + (click * 0.04f)) * decay
        }
    }
}
