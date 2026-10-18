package com.tekin.tesbih

/**
 * Tesbih modulu: veri modeli ve sayma kurallari.
 *
 * Bu dosya Android'e bagli degil; kurallar ekrandan bagimsiz test
 * edilebiliyor. Kayit TasbihStore'da, ekranlar Tasbih*Activity'lerde.
 * Uygulamanin geri kalanindan hicbir seye dayanmiyor.
 */

/** Hedefe ulasinca ne olacagi. */
enum class TasbihOnTarget(val label: String) {
    STOP("Dur"),
    NEW_ROUND("Yeni tur başlat")
}

/** Hedefe ulasinca gelen titresim. */
enum class TasbihFinishVibe(val label: String, val pattern: LongArray) {
    OFF("Kapalı", longArrayOf()),
    SHORT("Kısa", longArrayOf(0, 150)),
    MEDIUM("Orta", longArrayOf(0, 400)),
    LONG("Uzun", longArrayOf(0, 900)),
    LONG_REPEAT("Uzun + tekrar", longArrayOf(0, 700, 250, 700, 250, 700))
}

data class Tasbih(
    val id: String,
    val name: String,
    val target: Int,
    /** Turun icindeki sayi. STOP'ta hedefe gelince hedefte kalir. */
    val count: Int = 0,
    /** Tamamlanan tur sayisi. */
    val rounds: Int = 0,
    /** Bu tesbihte bugune kadar cekilen toplam. Sifirlamada korunur. */
    val total: Long = 0,
    /** Tespih gorunumu; yalnizca bu tesbihe ait. */
    val material: TasbihMaterial = TasbihMaterial.KUKA,
    /** Her N adette titresim; 0 kapali, 1 her adette. */
    val vibrateEvery: Int = 33,
    val finishVibe: TasbihFinishVibe = TasbihFinishVibe.LONG,
    val sound: Boolean = false,
    val onTarget: TasbihOnTarget = TasbihOnTarget.STOP,
    val createdAt: Long = 0,
    val lastUsedAt: Long = 0,
    /** Bu turda sayarak gecen sure (ms); duraklamalar dahil degil. */
    val roundMs: Long = 0,
    /** Son sayilan dokunusun zamani; sure bundan hesaplaniyor. */
    val lastTapAt: Long = 0,
    /** Son tamamlanan turun suresi (ms); 0 = henuz yok. */
    val lastRoundMs: Long = 0,
    /** En hizli tamamlanan tur (ms); 0 = henuz yok. */
    val bestRoundMs: Long = 0,
    /** Suresi olculen tamamlanmis tur sayisi ve toplam suresi (ortalama icin). */
    val timedRounds: Int = 0,
    val timedMs: Long = 0,
    /**
     * Son gecerli cekis araliklari (ms), en yenisi sonda; en fazla
     * TasbihTime.SPEED_WINDOW. Duraklamalar girmiyor. Hiz ve tahmini
     * bitis buradan hesaplaniyor; turlar arasinda korunur.
     */
    val intervals: List<Long> = emptyList()
) {
    /** Tamamlanan turlarin ortalama suresi (ms); tur yoksa 0. */
    val averageRoundMs: Long get() = if (timedRounds <= 0) 0 else timedMs / timedRounds

    /** STOP modunda hedefe gelmis: sayma durur, "Tamamlandı" gorunur. */
    val isDone: Boolean get() = onTarget == TasbihOnTarget.STOP && count >= target

    val progress: Float get() = if (target <= 0) 0f else (count.toFloat() / target).coerceIn(0f, 1f)

    /** Halka, cubuk ve +1 dugmesinin rengi materyalden gelir. */
    val accent: Int get() = material.accent

    /** Tespihte cekilmis boncuk: toplam her dokunusta bir artiyor. */
    val beadsPulled: Int get() = (total % TasbihRosaryLayout.BEADS).toInt()

    companion object {
        const val MAX_TARGET = 50_000
        const val MAX_NAME = 40
        const val MAX_EVERY = 50_000

        /** Titresim secicisindeki hazir araliklar; "Özel" ayrica soruluyor. */
        val EVERY_PRESETS = intArrayOf(0, 1, 5, 10, 33, 99)

        fun everyLabel(every: Int): String = when (every) {
            0 -> "Kapalı"
            1 -> "Her adette"
            else -> "Her $every adette"
        }
    }
}

/**
 * Cekme mesafesi. Kullaniciya dp degil ad gosteriliyor; ayar ekranindaki
 * kaydirici bu araliktadir.
 */
object TasbihPull {
    const val MIN_DP = 32
    const val MAX_DP = 96
    const val DEFAULT_DP = 56

    fun label(dp: Int): String = when {
        dp < 42 -> "Çok kısa"
        dp < 52 -> "Kısa"
        dp <= 62 -> "Normal"
        dp <= 78 -> "Uzun"
        else -> "Çok uzun"
    }
}

/** Titresim sureleri (ms). Kullanici Ayarlar'dan milisaniye ile ayarliyor. */
object TasbihVibe {
    const val MIN_TICK_MS = 5
    const val MAX_TICK_MS = 100
    const val DEFAULT_TICK_MS = 30

    const val MIN_PULSE_MS = 20
    const val MAX_PULSE_MS = 300
    const val DEFAULT_PULSE_MS = 45
}

/**
 * Tesbih suresi. Sure dokunuslar arasindan hesaplaniyor: iki dokunus
 * arasi bekleme siniri (idle) kadar ya da daha kisaysa sureye eklenir,
 * daha uzunsa o ara "duraklama" sayilir ve eklenmez. Boylece sayma
 * birakilinca, uygulamadan cikilinca ya da ekran kilitlenince sure
 * kendiliginden durur; ilk yeni dokunusta kaldigi yerden devam eder.
 * Ayri bir baslat/durdur dugmesi yok.
 */
object TasbihTime {
    const val DEFAULT_IDLE_MS = 30_000L

    /** Ayarlardaki secenekler. */
    val IDLE_OPTIONS = longArrayOf(15_000L, 30_000L, 60_000L, 120_000L)

    fun idleLabel(ms: Long): String = if (ms < 60_000L) "${ms / 1000} sn" else "${ms / 60_000L} dk"

    /** Son dokunustan bu yana eklenecek sure; duraklamadaysa 0. */
    fun gap(lastTapAt: Long, now: Long, idleMs: Long): Long {
        if (lastTapAt <= 0L || now <= lastTapAt) return 0L
        val gap = now - lastTapAt
        return if (gap <= idleMs) gap else 0L
    }

    /** Ekranda akan sure: kaydedilen + (duraklamada degilse) son dokunustan beri. */
    fun liveRoundMs(t: Tasbih, now: Long, idleMs: Long): Long = t.roundMs + gap(t.lastTapAt, now, idleMs)

    /** "1:48", "12:05", "1:02:33" */
    fun format(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0) + 500) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** Adet basina sure: "~1,7 sn". Hedef yoksa bos. */
    fun perBead(roundMs: Long, target: Int): String {
        if (roundMs <= 0 || target <= 0) return ""
        val sec = roundMs / 1000.0 / target
        return "~" + String.format(java.util.Locale.forLanguageTag("tr"), "%.1f", sec) + " sn"
    }

    /** Hiz icin tutulan son aralik sayisi. */
    const val SPEED_WINDOW = 20

    /** Tahminin gosterilmesi icin gereken en az aralik. */
    const val SPEED_MIN = 10

    /** Yeni araligi pencereye ekler; pencere SPEED_WINDOW'u asmaz. */
    fun addInterval(list: List<Long>, gapMs: Long): List<Long> =
        if (gapMs <= 0) list else (list + gapMs).takeLast(SPEED_WINDOW)

    /**
     * Tipik cekis araligi (ms): son araliklarin kirpilmis ortalamasi. En hizli
     * ve en yavas %10 atiliyor; tek bir dalginlik ya da cift dokunus tahmini
     * savurmuyor. SPEED_MIN'den az aralik varsa null.
     */
    fun typicalIntervalMs(list: List<Long>): Long? {
        if (list.size < SPEED_MIN) return null
        val sorted = list.sorted()
        val cut = sorted.size / 10
        val kept = sorted.subList(cut, sorted.size - cut)
        return kept.average().toLong().coerceAtLeast(1L)
    }

    /** Dakikada adet; hesaplanamiyorsa null. */
    fun perMinute(list: List<Long>): Int? = typicalIntervalMs(list)?.let { (60_000.0 / it).toInt().coerceAtLeast(1) }

    /** Kalan adet icin tahmini sure (ms); hesaplanamiyorsa null. */
    fun etaMs(list: List<Long>, remaining: Int): Long? {
        if (remaining <= 0) return 0L
        return typicalIntervalMs(list)?.let { it * remaining }
    }

    /**
     * Yaklasik sure: 1 dk alti 5 sn'ye, 10 dk alti yarim dakikaya, ustu
     * dakikaya yuvarlanir. "≈ 40 sn", "≈ 3,5 dk", "≈ 1 sa 12 dk".
     */
    fun approx(ms: Long): String {
        val sec = (ms.coerceAtLeast(0) + 500) / 1000
        return when {
            sec < 60 -> "≈ " + (((sec + 4) / 5) * 5).coerceAtLeast(5) + " sn"
            sec < 600 -> {
                val halves = (sec + 15) / 30
                val whole = halves / 2
                if (halves % 2 == 0L) "≈ $whole dk" else "≈ $whole,5 dk"
            }
            else -> {
                val min = (sec + 30) / 60
                if (min < 60) "≈ $min dk" else "≈ ${min / 60} sa ${min % 60} dk"
            }
        }
    }

    /** "1 sa 34 dk", "12 dk", "45 sn" (istatistik icin). */
    fun total(ms: Long): String {
        val sec = (ms.coerceAtLeast(0) + 500) / 1000
        val min = sec / 60
        return when {
            min >= 60 -> "${min / 60} sa ${min % 60} dk"
            min > 0 -> "$min dk"
            else -> "$sec sn"
        }
    }

    /** Kart satiri: "Son tur 2:45 · ort. 2:52 · en hızlı 2:31"; tur yoksa bos. */
    fun summary(t: Tasbih): String {
        if (t.timedRounds <= 0) return ""
        val parts = mutableListOf("Son tur " + format(t.lastRoundMs))
        if (t.timedRounds > 1) {
            parts.add("ort. " + format(t.averageRoundMs))
            parts.add("en hızlı " + format(t.bestRoundMs))
        }
        return parts.joinToString(" · ")
    }
}

/** Tek dokunusun sonucu; titresim, ses ve animasyon buna gore seciliyor. */
enum class TasbihEvent {
    /** Sayildi, ozel bir sey yok. */
    COUNTED,
    /** Titresim araligina denk geldi. */
    MILESTONE,
    /** Hedefe ulasildi. */
    COMPLETED,
    /** STOP modunda hedefteyken dokunuldu; sayilmadi. */
    BLOCKED
}

object TasbihLogic {

    data class TapResult(val tasbih: Tasbih, val event: TasbihEvent)

    fun tap(t: Tasbih, now: Long, idleMs: Long = TasbihTime.DEFAULT_IDLE_MS): TapResult {
        if (t.isDone) return TapResult(t, TasbihEvent.BLOCKED)
        val next = t.count + 1
        val gap = TasbihTime.gap(t.lastTapAt, now, idleMs)
        val elapsed = t.roundMs + gap
        val counted = t.copy(total = t.total + 1, lastUsedAt = now, lastTapAt = now, roundMs = elapsed,
            intervals = TasbihTime.addInterval(t.intervals, gap))
        if (next >= t.target) {
            // Tur bitti: suresi kaydedilir, yeni tur sifirdan olculur.
            val timed = if (elapsed > 0) counted.copy(
                lastRoundMs = elapsed,
                bestRoundMs = if (t.bestRoundMs <= 0) elapsed else minOf(t.bestRoundMs, elapsed),
                timedRounds = t.timedRounds + 1,
                timedMs = t.timedMs + elapsed,
                roundMs = 0
            ) else counted.copy(roundMs = 0)
            val done = if (t.onTarget == TasbihOnTarget.NEW_ROUND) {
                timed.copy(count = 0, rounds = t.rounds + 1)
            } else {
                timed.copy(count = t.target, rounds = t.rounds + 1)
            }
            return TapResult(done, TasbihEvent.COMPLETED)
        }
        val event = if (t.vibrateEvery > 0 && next % t.vibrateEvery == 0) TasbihEvent.MILESTONE
                    else TasbihEvent.COUNTED
        return TapResult(counted.copy(count = next), event)
    }

    /**
     * Son dokunusu geri alir. Yeni tur modunda tur basindaysa onceki turun
     * son tanesine doner; STOP'ta tamamlanmissa tamamlanmayi da geri alir.
     * Geri alinacak bir sey yoksa null.
     */
    fun undo(t: Tasbih): Tasbih? {
        if (t.total <= 0) return null
        return when {
            t.onTarget == TasbihOnTarget.STOP && t.count >= t.target ->
                t.copy(count = t.target - 1, rounds = (t.rounds - 1).coerceAtLeast(0), total = t.total - 1)
            t.count > 0 -> t.copy(count = t.count - 1, total = t.total - 1)
            t.rounds > 0 && t.onTarget == TasbihOnTarget.NEW_ROUND ->
                t.copy(count = t.target - 1, rounds = t.rounds - 1, total = t.total - 1)
            else -> null
        }
    }

    /** Hedef/mevcut sayi alanlari: bos ya da aralik disi ise null. */
    fun parseInt(raw: String, min: Int, max: Int): Int? {
        val value = raw.trim().toIntOrNull() ?: return null
        return if (value in min..max) value else null
    }

    /** Hedef degisince sayac hedefin disinda kalmasin. */
    fun clampCount(count: Int, target: Int): Int = count.coerceIn(0, target)
}
