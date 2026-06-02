package com.ghalbitnet.meshx2.call

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class CallToneDiagnosticLab(
    private val role: Role,
    private val callId: String,
    private val peerId: String,
    private val sampleRate: Int = 8000,
    private val frameMs: Int = 20
) {
    enum class Role {
        CALLER,
        CALLEE
    }

    data class OutgoingFrame(
        val frame: ByteArray,
        val toneInjected: Boolean,
        val frequencyHz: Double?,
        val slotLabel: String
    )

    data class IncomingDetection(
        val detected: Boolean,
        val expectedFrequencyHz: Double?,
        val dominantFrequencyHz: Double?,
        val slotLabel: String,
        val rms: Int,
        val expectedEnergy: Double,
        val alternateEnergy: Double
    )

    data class Summary(
        val txBursts: Int,
        val rxDetections: Int,
        val rxMisses: Int
    )

    private val startedAtMs = System.currentTimeMillis()
    private var txBursts = 0
    private var rxDetections = 0
    private var rxMisses = 0

    fun processOutgoing(frame: ByteArray, nowMs: Long = System.currentTimeMillis()): OutgoingFrame {
        val plan = currentPlan(nowMs)
        if (!plan.transmit) {
            return OutgoingFrame(frame = frame, toneInjected = false, frequencyHz = null, slotLabel = plan.label)
        }
        txBursts++
        val frequency = plan.frequencyHz ?: TONE_CALLER_HZ
        return OutgoingFrame(
            frame = generateToneFrame(frame.size, frequency),
            toneInjected = true,
            frequencyHz = frequency,
            slotLabel = plan.label
        )
    }

    fun analyzeIncoming(frame: ByteArray, nowMs: Long = System.currentTimeMillis()): IncomingDetection {
        val expected = expectedIncoming(nowMs)
        val primary = toneEnergy(frame, TONE_CALLER_HZ)
        val secondary = toneEnergy(frame, TONE_CALLEE_HZ)
        val dominant = if (primary >= secondary) TONE_CALLER_HZ else TONE_CALLEE_HZ
        val expectedEnergy =
            when (expected.frequencyHz) {
                TONE_CALLER_HZ -> primary
                TONE_CALLEE_HZ -> secondary
                else -> 0.0
            }
        val alternateEnergy =
            when (expected.frequencyHz) {
                TONE_CALLER_HZ -> secondary
                TONE_CALLEE_HZ -> primary
                else -> maxOf(primary, secondary)
            }
        val rms = frameRms(frame)
        val detected =
            expected.frequencyHz != null &&
                rms >= MIN_RMS &&
                expectedEnergy > MIN_TONE_ENERGY &&
                expectedEnergy > alternateEnergy * DETECTION_RATIO
        if (expected.frequencyHz != null) {
            if (detected) rxDetections++ else rxMisses++
        }
        return IncomingDetection(
            detected = detected,
            expectedFrequencyHz = expected.frequencyHz,
            dominantFrequencyHz = if (maxOf(primary, secondary) > MIN_TONE_ENERGY) dominant else null,
            slotLabel = expected.label,
            rms = rms,
            expectedEnergy = expectedEnergy,
            alternateEnergy = alternateEnergy
        )
    }

    fun summary(): Summary = Summary(txBursts = txBursts, rxDetections = rxDetections, rxMisses = rxMisses)

    private fun currentPlan(nowMs: Long): SlotPlan {
        val slotIndex = (((nowMs - startedAtMs).coerceAtLeast(0L) / SLOT_MS) % SLOT_COUNT).toInt()
        return when (role) {
            Role.CALLER ->
                when (slotIndex) {
                    0 -> SlotPlan(true, TONE_CALLER_HZ, "caller_tx_a")
                    1 -> SlotPlan(false, null, "caller_listen_a")
                    2 -> SlotPlan(false, null, "caller_listen_b")
                    else -> SlotPlan(true, TONE_CALLER_HZ, "caller_tx_b")
                }
            Role.CALLEE ->
                when (slotIndex) {
                    0 -> SlotPlan(false, null, "callee_listen_a")
                    1 -> SlotPlan(true, TONE_CALLEE_HZ, "callee_tx_a")
                    2 -> SlotPlan(true, TONE_CALLEE_HZ, "callee_tx_b")
                    else -> SlotPlan(false, null, "callee_listen_b")
                }
        }
    }

    private fun expectedIncoming(nowMs: Long): SlotPlan {
        val slotIndex = (((nowMs - startedAtMs).coerceAtLeast(0L) / SLOT_MS) % SLOT_COUNT).toInt()
        return when (role) {
            Role.CALLER ->
                when (slotIndex) {
                    1 -> SlotPlan(true, TONE_CALLEE_HZ, "expect_callee_a")
                    2 -> SlotPlan(true, TONE_CALLEE_HZ, "expect_callee_b")
                    else -> SlotPlan(false, null, "caller_no_expect")
                }
            Role.CALLEE ->
                when (slotIndex) {
                    0 -> SlotPlan(true, TONE_CALLER_HZ, "expect_caller_a")
                    3 -> SlotPlan(true, TONE_CALLER_HZ, "expect_caller_b")
                    else -> SlotPlan(false, null, "callee_no_expect")
                }
        }
    }

    private fun generateToneFrame(byteCount: Int, frequencyHz: Double): ByteArray {
        val sampleCount = byteCount / 2
        val output = ByteArray(byteCount)
        for (i in 0 until sampleCount) {
            val sample =
                (sin(2.0 * PI * frequencyHz * i / sampleRate) * SHORT_AMPLITUDE).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[i * 2] = (sample and 0xFF).toByte()
            output[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return output
    }

    private fun toneEnergy(frame: ByteArray, frequencyHz: Double): Double {
        val sampleCount = frame.size / 2
        if (sampleCount <= 0) return 0.0
        var real = 0.0
        var imag = 0.0
        for (i in 0 until sampleCount) {
            val lo = frame[i * 2].toInt() and 0xFF
            val hi = frame[i * 2 + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toDouble()
            val angle = 2.0 * PI * frequencyHz * i / sampleRate
            real += sample * cos(angle)
            imag += sample * sin(angle)
        }
        return sqrt(real * real + imag * imag) / sampleCount.toDouble()
    }

    private fun frameRms(frame: ByteArray): Int {
        val sampleCount = frame.size / 2
        if (sampleCount <= 0) return 0
        var sum = 0.0
        for (i in 0 until sampleCount) {
            val lo = frame[i * 2].toInt() and 0xFF
            val hi = frame[i * 2 + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toDouble()
            sum += sample * sample
        }
        return sqrt(sum / sampleCount.toDouble()).roundToInt()
    }

    private data class SlotPlan(
        val transmit: Boolean,
        val frequencyHz: Double?,
        val label: String
    )

    companion object {
        private const val SLOT_MS = 1500L
        private const val SLOT_COUNT = 4
        private const val TONE_CALLER_HZ = 440.0
        private const val TONE_CALLEE_HZ = 660.0
        private const val SHORT_AMPLITUDE = 8_000.0
        private const val MIN_TONE_ENERGY = 1200.0
        private const val DETECTION_RATIO = 1.35
        private const val MIN_RMS = 600
    }
}
