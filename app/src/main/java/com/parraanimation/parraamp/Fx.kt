package com.parraanimation.parraamp

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

// Ajustes de sonido globales: valen para todas las canciones y se guardan en el teléfono.
// La app y el servicio de reproducción viven en el mismo proceso, por eso comparten este objeto.
object FidelidacFx {
    @Volatile var bassDb: Float = 0f
        private set
    @Volatile var trebleDb: Float = 0f
        private set
    @Volatile var reverb: Float = 0f
        private set
    @Volatile var stereo: Float = 0f
        private set
    @Volatile var version: Int = 0
        private set

    private var prefs: SharedPreferences? = null
    val processor = FxAudioProcessor()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("fx", Context.MODE_PRIVATE)
        prefs = p
        bassDb = p.getFloat("bass", 0f)
        trebleDb = p.getFloat("treble", 0f)
        reverb = p.getFloat("reverb", 0f)
        stereo = p.getFloat("stereo", 0f)
        version += 1
    }

    fun setBass(v: Float) { bassDb = v.coerceIn(-10f, 10f); changed("bass", bassDb) }
    fun setTreble(v: Float) { trebleDb = v.coerceIn(-10f, 10f); changed("treble", trebleDb) }
    fun setReverb(v: Float) { reverb = v.coerceIn(0f, 1f); changed("reverb", reverb) }
    fun setStereo(v: Float) { stereo = v.coerceIn(0f, 1f); changed("stereo", stereo) }

    private fun changed(key: String, v: Float) {
        version += 1
        prefs?.edit()?.putFloat(key, v)?.apply()
    }
}

// ---- Piezas de DSP ----

private class Biquad {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    var z1 = 0.0; var z2 = 0.0
    fun set(nb0: Double, nb1: Double, nb2: Double, na1: Double, na2: Double) {
        b0 = nb0; b1 = nb1; b2 = nb2; a1 = na1; a2 = na2
    }
    fun clear() { z1 = 0.0; z2 = 0.0 }
    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}

// Filtro "shelf" (graves o agudos) con la fórmula estándar RBJ
private fun setShelf(b: Biquad, rate: Int, f0: Double, gainDb: Double, high: Boolean) {
    val f = minOf(f0, rate * 0.45)
    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2.0 * PI * f / rate
    val cosw = cos(w0)
    val sinw = sin(w0)
    val alpha = sinw / 2.0 * sqrt(2.0)
    val sA = 2.0 * sqrt(a) * alpha
    val b0: Double; val b1: Double; val b2: Double
    val a0: Double; val a1: Double; val a2: Double
    if (!high) {
        b0 = a * ((a + 1) - (a - 1) * cosw + sA)
        b1 = 2 * a * ((a - 1) - (a + 1) * cosw)
        b2 = a * ((a + 1) - (a - 1) * cosw - sA)
        a0 = (a + 1) + (a - 1) * cosw + sA
        a1 = -2 * ((a - 1) + (a + 1) * cosw)
        a2 = (a + 1) + (a - 1) * cosw - sA
    } else {
        b0 = a * ((a + 1) + (a - 1) * cosw + sA)
        b1 = -2 * a * ((a - 1) + (a + 1) * cosw)
        b2 = a * ((a + 1) + (a - 1) * cosw - sA)
        a0 = (a + 1) - (a - 1) * cosw + sA
        a1 = 2 * ((a - 1) - (a + 1) * cosw)
        a2 = (a + 1) - (a - 1) * cosw - sA
    }
    b.set(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
}

// Reverb tipo Freeverb: 8 filtros peine + 4 pasa-todo por canal
private class Comb(size: Int) {
    private val buf = FloatArray(size)
    private var idx = 0
    private var store = 0f
    fun process(input: Float, feedback: Float, damp: Float): Float {
        val out = buf[idx]
        store = out * (1f - damp) + store * damp
        buf[idx] = input + store * feedback
        idx += 1
        if (idx >= buf.size) idx = 0
        return out
    }
}

private class Allpass(size: Int) {
    private val buf = FloatArray(size)
    private var idx = 0
    fun process(input: Float): Float {
        val b = buf[idx]
        val out = -input + b
        buf[idx] = input + b * 0.5f
        idx += 1
        if (idx >= buf.size) idx = 0
        return out
    }
}

private fun softClip(x: Float): Float {
    val a = abs(x)
    if (a <= 0.9f) return x
    val y = 0.9f + 0.1f * tanh((a - 0.9f) / 0.1f)
    return if (x < 0f) -y else y
}

// Procesador de audio: graves, agudos, ensanchado estéreo y reverb.
// Trabaja con PCM de 16 bits y de coma flotante; otros formatos pasan sin tocarse.
// Si todos los ajustes están en cero, copia el audio tal cual (sin alterarlo).
class FxAudioProcessor : BaseAudioProcessor() {
    private var channels = 2
    private var rate = 44100
    private var isFloat = false
    private var seenVersion = -1
    private var bypass = true
    private var outGain = 1f
    private var width = 1f
    private var wetGain = 0f

    private val bassL = Biquad()
    private val bassR = Biquad()
    private val trebL = Biquad()
    private val trebR = Biquad()
    private var combsL = emptyArray<Comb>()
    private var combsR = emptyArray<Comb>()
    private var apL = emptyArray<Allpass>()
    private var apR = emptyArray<Allpass>()

    private companion object {
        val COMB = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val AP = intArrayOf(556, 441, 341, 225)
        const val FEEDBACK = 0.84f
        const val DAMP = 0.2f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        val f = inputAudioFormat
        channels = f.channelCount.coerceAtLeast(1)
        rate = f.sampleRate.coerceAtLeast(8000)
        isFloat = f.encoding == C.ENCODING_PCM_FLOAT
        val scale = rate / 44100.0
        fun size(n: Int, spread: Int) = max(1, ((n + spread) * scale).toInt())
        combsL = Array(8) { Comb(size(COMB[it], 0)) }
        combsR = Array(8) { Comb(size(COMB[it], 23)) }
        apL = Array(4) { Allpass(size(AP[it], 0)) }
        apR = Array(4) { Allpass(size(AP[it], 23)) }
        bassL.clear(); bassR.clear(); trebL.clear(); trebR.clear()
        seenVersion = -1
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        refreshParams()
        if (bypass) out.put(inputBuffer) else process(inputBuffer, out)
        out.flip()
    }

    private fun refreshParams() {
        val v = FidelidacFx.version
        if (v == seenVersion) return
        seenVersion = v
        val bass = FidelidacFx.bassDb
        val treble = FidelidacFx.trebleDb
        val rev = FidelidacFx.reverb
        val st = FidelidacFx.stereo
        bypass = abs(bass) < 0.05f && abs(treble) < 0.05f && rev < 0.005f && st < 0.005f
        setShelf(bassL, rate, 120.0, bass.toDouble(), false)
        setShelf(bassR, rate, 120.0, bass.toDouble(), false)
        setShelf(trebL, rate, 6000.0, treble.toDouble(), true)
        setShelf(trebR, rate, 6000.0, treble.toDouble(), true)
        width = 1f + 1.4f * st
        wetGain = 0.5f * rev
        // Margen para que subir graves o agudos no sature
        val boost = max(0f, max(bass, treble))
        outGain = 10.0.pow(-0.45 * boost / 20.0).toFloat() / (1f + 0.2f * st)
    }

    private fun process(inp: ByteBuffer, out: ByteBuffer) {
        val stereoIn = channels == 2
        val frameBytes = channels * (if (isFloat) 4 else 2)
        val frames = inp.remaining() / frameBytes
        val doReverb = wetGain > 0f
        for (n in 0 until frames) {
            var l: Float
            var r: Float
            if (isFloat) {
                l = inp.getFloat()
                r = if (stereoIn) inp.getFloat() else l
            } else {
                l = inp.getShort() / 32768f
                r = if (stereoIn) inp.getShort() / 32768f else l
            }
            // Graves y agudos
            l = trebL.process(bassL.process(l.toDouble())).toFloat()
            r = if (stereoIn) trebR.process(bassR.process(r.toDouble())).toFloat() else l
            // Ensanchado estéreo (más lados, centro igual)
            if (stereoIn && width != 1f) {
                val m = (l + r) * 0.5f
                val s = (l - r) * 0.5f * width
                l = m + s
                r = m - s
            }
            // Reverb
            if (doReverb) {
                val x = (l + r) * 0.015f
                var wl = 0f
                var wr = 0f
                for (c in combsL) wl += c.process(x, FEEDBACK, DAMP)
                for (c in combsR) wr += c.process(x, FEEDBACK, DAMP)
                for (a in apL) wl = a.process(wl)
                for (a in apR) wr = a.process(wr)
                l += wl * wetGain
                r = if (stereoIn) r + wr * wetGain else l
            }
            l = softClip(l * outGain)
            r = softClip(r * outGain)
            if (isFloat) {
                out.putFloat(l)
                if (stereoIn) out.putFloat(r)
            } else {
                out.putShort((l * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                if (stereoIn) out.putShort((r * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        // Por seguridad: si quedara algún byte suelto, se copia sin tocar
        while (inp.hasRemaining()) out.put(inp.get())
    }
}
