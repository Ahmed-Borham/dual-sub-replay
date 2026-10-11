package com.kienhoang.dualsubreplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class PronunciationVolumeTest {
    @Test
    fun aSavedVolumeStaysInsideTheSliderRange() {
        assertEquals(DEFAULT_PRONUNCIATION_VOLUME, storedPronunciationVolume(Float.NaN), 0f)
        assertEquals(MIN_PRONUNCIATION_VOLUME, storedPronunciationVolume(0f), 0f)
        assertEquals(MAX_PRONUNCIATION_VOLUME, storedPronunciationVolume(9f), 0f)
        assertEquals(1.5f, storedPronunciationVolume(1.5f), 0f)
        // Six steps between 25 % and 200 % land on every 25 %.
        assertEquals(0.25f, (MAX_PRONUNCIATION_VOLUME - MIN_PRONUNCIATION_VOLUME) / (PRONUNCIATION_VOLUME_STEPS + 1), 1e-6f)
    }

    @Test
    fun upTo100PercentOnlyThePlayerVolumeChanges() {
        assertEquals(0.5f, playerVolume(0.5f), 0f)
        assertEquals(1f, playerVolume(1f), 0f)
        assertEquals(1f, playerVolume(2f), 0f)
        assertEquals(0, extraGainMillibels(0.5f))
        assertEquals(0, extraGainMillibels(1f))
        // Twice as loud is about +6 dB.
        assertEquals(602, extraGainMillibels(2f))
        assertEquals(352, extraGainMillibels(1.5f))
    }

    @Test
    fun quietSpeechIsRaisedButNeverLoweredOrRaisedTooFar() {
        assertEquals(1f, speechNormalizingGain(32767), 0f)
        assertEquals(1f, speechNormalizingGain(0), 0f)
        assertEquals(0.89f * 32767 / 10_000, speechNormalizingGain(10_000), 1e-4f)
        assertEquals(4f, speechNormalizingGain(100), 0f)
    }

    @Test
    fun loudSamplesAreRoundedOffInsteadOfClipped() {
        assertEquals(0.4f, amplifiedSample(0.2f, 2f), 1e-6f)
        assertEquals(-0.4f, amplifiedSample(-0.2f, 2f), 1e-6f)
        val loud = amplifiedSample(0.45f, 2f)
        assertTrue("$loud", loud > 0.8f && loud < 0.9f)
        assertEquals(-loud, amplifiedSample(-0.45f, 2f), 1e-6f)
        assertTrue(amplifiedSample(0.9f, 4f) <= 1f)
    }

    @Test
    fun aQuietRecordingIsRaisedToTheTarget() {
        val wav = wav(shortArrayOf(0, 2000, -4000, 1000))
        assertTrue(normalizeSpeechWav(wav))
        val samples = samples(wav)
        // 4000 * 4 = 16000 stays under the soft limit, so the shape is unchanged.
        assertEquals(listOf<Short>(0, 8000, -16000, 4000), samples)
    }

    @Test
    fun aRecordingNearFullScaleIsLeftAsItIs() {
        val wav = wav(shortArrayOf(0, 31000, -30000))
        val before = wav.copyOf()
        assertTrue(normalizeSpeechWav(wav))
        assertTrue(before.contentEquals(wav))
    }

    @Test
    fun theLoudestSampleEndsNearTheTargetWithoutClipping() {
        val wav = wav(shortArrayOf(8000, -12000, 3000))
        normalizeSpeechWav(wav)
        val peak = samples(wav).maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 26_000..32_767)
    }

    @Test
    fun otherChunksAreSkippedAndAWrongDataSizeIsTolerated() {
        val wav = wav(shortArrayOf(1000, -1000), extraChunk = true, dataSize = Int.MAX_VALUE)
        val data = wavPcm16Data(wav)!!
        assertEquals(4, data.last - data.first + 1)
        assertTrue(normalizeSpeechWav(wav))
        assertEquals(listOf<Short>(4000, -4000), samples(wav))
    }

    @Test
    fun filesThatAreNot16BitPcmAreLeftAlone() {
        assertNull(wavPcm16Data(ByteArray(0)))
        assertNull(wavPcm16Data("not a wave file at all".toByteArray()))
        val float = wav(shortArrayOf(1000), format = 3)
        assertNull(wavPcm16Data(float))
        assertFalse(normalizeSpeechWav(float))
        assertNull(wavPcm16Data(wav(shortArrayOf(1000), bits = 8)))
    }

    private fun samples(wav: ByteArray): List<Short> {
        val data = wavPcm16Data(wav)!!
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        return (data step 2).map { buffer.getShort(it) }
    }

    /** A mono 16 kHz WAV file holding [samples]. */
    private fun wav(
        samples: ShortArray,
        format: Int = 1,
        bits: Int = 16,
        extraChunk: Boolean = false,
        dataSize: Int = samples.size * 2,
    ): ByteArray {
        val extra = if (extraChunk) 8 + 3 + 1 else 0
        val buffer = ByteBuffer.allocate(12 + 24 + extra + 8 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(buffer.capacity() - 8).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16)
        buffer
            .putShort(format.toShort())
            .putShort(1)
            .putInt(16_000)
            .putInt(32_000)
            .putShort(2)
            .putShort(bits.toShort())
        // An odd-sized chunk is followed by one padding byte.
        if (extraChunk) buffer.put("LIST".toByteArray()).putInt(3).put(byteArrayOf(1, 2, 3, 0))
        buffer.put("data".toByteArray()).putInt(dataSize)
        samples.forEach { buffer.putShort(it) }
        return buffer.array()
    }
}
