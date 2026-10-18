package com.tekin.tesbih

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 33'luk tespihin dizilisi (Android'e bagli degil, test ediliyor).
 *
 * Ipteki sira: imame, 11 boncuk, nisane, 11 boncuk, nisane, 11 boncuk
 * (ve yine imame; ip halka). Imame de ipin parcasi, boncuklarla birlikte
 * doner: 33 boncuk cekilince imame yeniden cekme noktasina gelir. Halkanin
 * sag tarafinda sabit bir "cekme boslugu" var. Boncuklar saat yonunde
 * akiyor: bosluktan hemen onceki boncuk (siradaki) cekilince boslugun
 * altina gecer, diger her sey bir boncuk kadar kayar. Gercek tespihte
 * parmakla boncuk indirip halkayi elde dondurmek gibi.
 *
 * Birim: bir boncugun ip uzerinde kapladigi yer = 1.
 */
object TasbihRosaryLayout {

    const val BEADS = 33
    const val BEAD_W = 1f
    const val SEP_W = 0.6f
    const val IMAME_W = 1.1f
    const val GAP_W = 2.2f

    /** Ipteki eleman: boncuk (1..33), nisane (0 ve -1) ya da imame (-2). */
    data class Element(val bead: Int) {
        val isBead: Boolean get() = bead > 0
        val isImame: Boolean get() = bead == IMAME
        val width: Float get() = when {
            isBead -> BEAD_W
            isImame -> IMAME_W
            else -> SEP_W
        }
    }

    const val IMAME = -2

    /** Ip sirasi: imame, b1..b11, N1, b12..b22, N2, b23..b33 */
    val STRING: List<Element> = listOf(Element(IMAME)) + (1..11).map { Element(it) } + Element(0) +
        (12..22).map { Element(it) } + Element(-1) + (23..33).map { Element(it) }

    val WIDTH: Float = STRING.sumOf { it.width.toDouble() }.toFloat()
    val TOTAL: Float = WIDTH + GAP_W

    /**
     * k boncuk cekilmisken, boslugun alt ucundan saat yonunde dizilis:
     * en son cekilen en basta, siradaki (b(k+1)) en sonda, boslugun
     * ustunde.
     */
    fun order(k: Int): List<Element> {
        val pulled = ((k % BEADS) + BEADS) % BEADS
        val last = if (pulled == 0) BEADS else pulled
        val start = STRING.indexOfFirst { it.bead == last }
        // Ipte geriye dogru: son cekilen, ondan onceki... ve sarilarak.
        return (0 until STRING.size).map { STRING[((start - it) % STRING.size + STRING.size) % STRING.size] }
    }

    /**
     * Cekilecek parca: dizilisin sonundan geriye ilk boncuga kadar
     * (araya nisane ya da imame dusmusse o da boncukla birlikte gecer).
     */
    fun chunkSize(order: List<Element>): Int {
        var n = 0
        for (i in order.indices.reversed()) {
            n++
            if (order[i].isBead) break
        }
        return n
    }

    /**
     * k durumundan k+1'e gecisin t aninda (0..1) her elemanin merkez
     * konumu (bosluk sonundan birim). Sira: order(k) sirasi.
     */
    fun positions(k: Int, t: Float): List<Pair<Element, Float>> {
        val order = order(k)
        val chunk = chunkSize(order)
        val stay = order.size - chunk
        val chunkWidth = order.subList(stay, order.size).sumOf { it.width.toDouble() }.toFloat()

        val result = ArrayList<Pair<Element, Float>>(order.size)
        var u = 0f
        order.forEachIndexed { i, e ->
            val center = u + e.width / 2f
            u += e.width
            val pos = if (i < stay) {
                center + t * chunkWidth
            } else {
                // Parca boslugu atlayip basa gecer.
                val offsetInChunk = center - (WIDTH - chunkWidth)
                val target = offsetInChunk + TOTAL
                center + t * (target - center)
            }
            result += e to (pos % TOTAL)
        }
        return result
    }

    /** Cekilen (hareket eden) boncugun ip numarasi. */
    fun movingBead(k: Int): Int = order(k).last { it.isBead }.bead
}

/**
 * Sayma ekranindaki tespih: ilerleme halkasi, 33 boncuk, ara halkalar,
 * nisaneler, imame ve puskul; ortada sayi.
 *
 * Asagi kaydirma parmagi takip eder: siradaki boncuk parmakla birlikte
 * bosluktan asagi iner. Yeterince inince onPull cagrilir (sayi +1). Kisa
 * dokunus da ayni sekilde bir boncuk ceker.
 */
class TasbihRosaryView(context: Context) : View(context) {

    /** Kullanici bir boncuk cekti; sayma burada yapilir. */
    var onPull: (() -> Unit)? = null

    var material: TasbihMaterial = TasbihMaterial.KUKA
        set(value) { if (field != value) { field = value; invalidate() } }

    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    var countText: String = "0"
        set(value) { field = value; invalidate() }
    var targetText: String = ""
        set(value) { field = value; invalidate() }
    var nameText: String = ""
        set(value) { field = value; invalidate() }
    var nameColor: Int = AppColors.TEXT_PRIMARY
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density

    /** Ekranda gorunen durum: shown boncuk cekilmis + phase kadar ilerleme. */
    private var shown = 0
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var hasState = false

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = AppColors.STROKE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val string = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()

    // --------------------------------------------------------- durum

    /**
     * Tespihi k cekilmis boncuga getirir. Bir ileri ise ceker gibi, bir
     * geri ise geri alir gibi canlandirir; baska her degisiklikte atlar.
     */
    fun setPulled(k: Int, animate: Boolean) {
        val target = ((k % TasbihRosaryLayout.BEADS) + TasbihRosaryLayout.BEADS) % TasbihRosaryLayout.BEADS
        if (!hasState || !animate) {
            finishAnimation()
            shown = target; phase = 0f; hasState = true
            invalidate()
            return
        }
        finishAnimation()
        val next = (shown + 1) % TasbihRosaryLayout.BEADS
        val prev = (shown + TasbihRosaryLayout.BEADS - 1) % TasbihRosaryLayout.BEADS
        when (target) {
            shown -> { if (phase != 0f) animatePhase(phase, 0f) {} }
            next -> animatePhase(phase, 1f) { shown = next; phase = 0f }
            prev -> { shown = prev; phase = 1f; animatePhase(1f, 0f) {} }
            else -> { shown = target; phase = 0f; invalidate() }
        }
    }

    private fun animatePhase(from: Float, to: Float, onEnd: () -> Unit) {
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = (230 * abs(to - from)).toLong().coerceAtLeast(60)
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) { onEnd(); invalidate() }
                }
            })
            start()
        }
        pendingEnd = onEnd
    }

    private var pendingEnd: (() -> Unit)? = null

    /** Yarida kalan canlandirmayi hemen bitirir (hizli arka arkaya cekis). */
    private fun finishAnimation() {
        val running = animator
        if (running != null && running.isRunning) {
            running.cancel()
            pendingEnd?.invoke()
        }
        animator = null
        pendingEnd = null
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------ dokunus

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var moved = false
    private var pulledInGesture = false

    /** Boncugun sayilmasi icin gereken asagi cekme (dp); ayarlardan gelir. */
    var pullDp: Int = TasbihPull.DEFAULT_DP

    private val pullDistance get() = pullDp * density
    private val slop get() = 8f * density

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                finishAnimation()
                downX = event.x; downY = event.y
                dragging = false; moved = false; pulledInGesture = false
                isPressed = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                val dx = event.x - downX
                if (!dragging && !moved && (abs(dy) > slop || abs(dx) > slop)) {
                    // Yalnizca asagi dogru hareket ceker; yana ya da yukari
                    // kaydirma hic saymaz.
                    if (dy > 0 && abs(dy) > abs(dx)) dragging = true else moved = true
                }
                if (dragging && !pulledInGesture) {
                    finishAnimation()
                    val t = (dy / pullDistance).coerceIn(0f, 1f)
                    if (t >= 1f) {
                        // Tam cekildi: say. Bir cekiste en fazla bir sayi;
                        // parmak asagi inmeye devam etse de bu cekis bitti,
                        // sonraki boncuk icin parmak kaldirilip yeniden cekilir.
                        phase = 0.999f
                        pulledInGesture = true
                        onPull?.invoke()
                    } else {
                        phase = t
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                if (dragging && !pulledInGesture) {
                    if (phase >= 0.45f) { pulledInGesture = true; onPull?.invoke() }
                    else animatePhase(phase, 0f) {}
                } else if (!pulledInGesture && !moved) {
                    performClick()
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                if (dragging && !pulledInGesture && phase > 0f) animatePhase(phase, 0f) {}
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onPull?.invoke()
        return true
    }

    // ---------------------------------------------------------- cizim

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val ringR = s * 0.30f
        val progressR = s * 0.40f

        // Ilerleme halkasi
        val stroke = s * 0.022f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        arc.color = material.accent
        oval.set(cx - progressR, cy - progressR, cx + progressR, cy + progressR)
        canvas.drawOval(oval, track)
        if (progress > 0f) canvas.drawArc(oval, -90f, 360f * progress, false, arc)

        // Ip
        string.strokeWidth = (s * 0.004f).coerceAtLeast(1f)
        string.color = TasbihUi.mix(material.metal.base, AppColors.BG_APP, 0.5f)
        canvas.drawCircle(cx, cy, ringR, string)

        // Boncuklar
        val unitDeg = 360f / TasbihRosaryLayout.TOTAL
        val beadD = (2f * ringR * sin(Math.toRadians(unitDeg / 2.0)).toFloat() * 0.94f).toInt()
        val bead = TasbihBeadPainter.bead(material, beadD)
        val spacerD = (beadD * 0.28f).toInt()
        val spacer = TasbihBeadPainter.spacer(material, spacerD)
        val sep = TasbihBeadPainter.separator(material, (beadD * 0.5f).toInt(), (beadD * 1.05f).toInt())

        // Bosluk sagda, yataydan biraz asagida; bosluk sonu buradan baslar.
        val gapCenter = 18f
        val gapEndDeg = gapCenter + TasbihRosaryLayout.GAP_W * unitDeg / 2f

        val items = TasbihRosaryLayout.positions(shown, phase)
        val moving = TasbihRosaryLayout.movingBead(shown)
        val lift = sin(Math.PI * phase).toFloat()

        // Ara halkalar: yan yana duran iki boncugun arasinda.
        for (i in 0 until items.size - 1) {
            val a = items[i]; val b = items[i + 1]
            if (!a.first.isBead || !b.first.isBead) continue
            if (a.first.bead == moving || b.first.bead == moving) continue
            val mid = (a.second + b.second) / 2f
            if (abs(a.second - b.second) > 1.5f) continue
            drawAt(canvas, spacer, cx, cy, ringR, gapEndDeg + mid * unitDeg, 1f, 0f)
        }

        var movingAngle = 0f
        var imameAngle = Float.NaN
        for ((e, u) in items) {
            val deg = gapEndDeg + u * unitDeg
            if (e.isImame) { imameAngle = deg; continue }
            if (e.isBead) {
                if (e.bead == moving && phase > 0f) { movingAngle = deg; continue }
                drawAt(canvas, bead, cx, cy, ringR, deg, 1f, 0f)
            } else {
                drawRotated(canvas, sep, cx, cy, ringR, deg)
            }
        }

        // Imame ve puskul: ipin parcasi, halkayla doner; puskul disa bakar.
        if (!imameAngle.isNaN()) {
            val imW = (s * 0.17f).toInt()
            val imH = (s * 0.20f).toInt()
            val imame = TasbihBeadPainter.imame(material, imW, imH)
            val rad = Math.toRadians(imameAngle.toDouble())
            val x = cx + ringR * cos(rad).toFloat()
            val y = cy + ringR * sin(rad).toFloat()
            canvas.save()
            canvas.rotate(imameAngle - 90f, x, y)
            canvas.drawBitmap(imame, x - imW / 2f, y - beadD * 0.15f, bitmapPaint)
            canvas.restore()
        }

        // Cekilen boncuk: one ve disa dogru kalkar, etrafinda isik.
        if (phase > 0f) {
            val scale = 1f + 0.28f * lift
            val out = ringR * 0.07f * lift
            drawAt(canvas, bead, cx, cy, ringR, movingAngle, scale, out)
            val rad = Math.toRadians(movingAngle.toDouble())
            val bx = cx + (ringR + out) * cos(rad).toFloat()
            val by = cy + (ringR + out) * sin(rad).toFloat()
            glow.color = material.accent
            glow.alpha = (220 * lift).toInt()
            glow.strokeWidth = s * 0.008f
            canvas.drawCircle(bx, by, beadD * 0.62f * scale, glow)
        } else {
            // Bekarken siradaki boncuk hafifce isaretli.
            val next = items.last { it.first.isBead }
            val deg = gapEndDeg + next.second * unitDeg
            val rad = Math.toRadians(deg.toDouble())
            glow.color = material.accent
            glow.alpha = 110
            glow.strokeWidth = s * 0.005f
            canvas.drawCircle(cx + ringR * cos(rad).toFloat(), cy + ringR * sin(rad).toFloat(), beadD * 0.62f, glow)
        }

        // Ortadaki yazilar
        text.typeface = Typeface.DEFAULT_BOLD
        text.color = AppColors.TEXT_PRIMARY
        text.textSize = fitText(countText, s * 0.17f, ringR * 1.5f)
        canvas.drawText(countText, cx, cy + text.textSize * 0.2f, text)
        text.typeface = Typeface.DEFAULT
        text.color = AppColors.TEXT_SECONDARY
        text.textSize = s * 0.05f
        canvas.drawText(targetText, cx, cy + s * 0.09f, text)
        if (nameText.isNotBlank()) {
            text.typeface = Typeface.DEFAULT_BOLD
            text.color = nameColor
            text.textSize = fitText(nameText, s * 0.048f, ringR * 1.45f)
            canvas.drawText(nameText, cx, cy + s * 0.165f, text)
        }
    }

    private fun fitText(value: String, size: Float, maxWidth: Float): Float {
        text.textSize = size
        val w = text.measureText(value)
        return if (w > maxWidth) size * maxWidth / w else size
    }

    private fun drawAt(canvas: Canvas, bmp: android.graphics.Bitmap, cx: Float, cy: Float, r: Float, deg: Float, scale: Float, out: Float) {
        val rad = Math.toRadians(deg.toDouble())
        val x = cx + (r + out) * cos(rad).toFloat()
        val y = cy + (r + out) * sin(rad).toFloat()
        if (scale == 1f) {
            canvas.drawBitmap(bmp, x - bmp.width / 2f, y - bmp.height / 2f, bitmapPaint)
        } else {
            canvas.save()
            canvas.scale(scale, scale, x, y)
            canvas.drawBitmap(bmp, x - bmp.width / 2f, y - bmp.height / 2f, bitmapPaint)
            canvas.restore()
        }
    }

    /** Nisane ipe dik durur: ipin yonune gore dondurulur. */
    private fun drawRotated(canvas: Canvas, bmp: android.graphics.Bitmap, cx: Float, cy: Float, r: Float, deg: Float) {
        val rad = Math.toRadians(deg.toDouble())
        val x = cx + r * cos(rad).toFloat()
        val y = cy + r * sin(rad).toFloat()
        canvas.save()
        // Bitmap dik; uzun ekseni yaricap yonune (ipe dik) cevrilir.
        canvas.rotate(deg - 90f, x, y)
        canvas.drawBitmap(bmp, x - bmp.width / 2f, y - bmp.height / 2f, bitmapPaint)
        canvas.restore()
    }
}
