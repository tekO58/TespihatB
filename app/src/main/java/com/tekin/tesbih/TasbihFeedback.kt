package com.tekin.tesbih

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Dokunus titresimi ve sesi. Her cagri runCatching icinde: titresim
 * motoru ya da ses servisi yoksa/hata verirse sayma yine calisir.
 */
class TasbihFeedback(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private var tone: ToneGenerator? = null

    /** Her adet titresimi (ms); Ayarlar'dan. */
    var tickMs: Int = TasbihVibe.DEFAULT_TICK_MS

    /** Aralik titresimi (her 5/10/33...) (ms); Ayarlar'dan. */
    var pulseMs: Int = TasbihVibe.DEFAULT_PULSE_MS

    /**
     * Her adette titresimde kisa tik (tickMs), araliklarda belirgin
     * titresim (pulseMs). Sureler Ayarlar'dan milisaniye olarak geliyor.
     *
     * Her adet titresimi once sabit 14 ms / dusuk guctu: cogu telefonun
     * motoru o surede donmeye bile baslamadigi icin hissedilmiyordu.
     * Varsayilan artik 30 ms ve guc yuksek.
     */
    fun tick(strong: Boolean) {
        if (strong) oneShot(pulseMs.toLong(), 255)
        else oneShot(tickMs.toLong(), 220)
    }

    fun finish(kind: TasbihFinishVibe) {
        if (kind == TasbihFinishVibe.OFF) return
        val vib = vibrator ?: return
        runCatching {
            if (!vib.hasVibrator()) return
            vib.vibrate(VibrationEffect.createWaveform(kind.pattern, -1))
        }
    }

    /** STOP modunda hedefteyken dokunus: kisa cift titresim. */
    fun blocked() {
        val vib = vibrator ?: return
        runCatching { vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)) }
    }

    fun click(finish: Boolean = false) {
        runCatching {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 45).also { tone = it }
            generator.startTone(
                if (finish) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                if (finish) 220 else 35
            )
        }
    }

    fun release() {
        runCatching { tone?.release() }
        tone = null
    }

    private fun oneShot(ms: Long, amplitude: Int) {
        val vib = vibrator ?: return
        runCatching {
            if (!vib.hasVibrator()) return
            val amp = if (vib.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            vib.vibrate(VibrationEffect.createOneShot(ms, amp))
        }
    }
}
