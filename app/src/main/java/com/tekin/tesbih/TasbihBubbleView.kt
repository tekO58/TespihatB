package com.tekin.tesbih

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Yuzen tespih balonunun gorunumu: koyu daire, materyal renginde ilerleme
 * halkasi, kenarda sade boncuk dizisi ve ortada sayi / hedef.
 *
 * Baska yazi ya da simge yok: hedefe ulasilsa da sayi gorunur (✓ yok),
 * her +1'de aninda guncellenir. Balon kucuk oldugu icin 12 iri boncukla
 * sadelestirildi.
 */
class TasbihBubbleView(context: Context) : View(context) {

    var material: TasbihMaterial = TasbihMaterial.ZUMRUT
        set(value) { field = value; invalidate() }
    var count: Int = 0
        set(value) { field = value; invalidate() }
    var target: Int = 0
        set(value) { field = value; invalidate() }
    /** Kapatma hedefinin ustundeyken balon kirmiziya doner. */
    var closing: Boolean = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val r = s / 2f - density * 2

        // Zemin ve golge hissi veren koyu kenar
        fill.color = if (closing) 0xFF5A1E22.toInt() else AppColors.BG_ELEVATED
        canvas.drawCircle(cx, cy, r, fill)
        ring.strokeWidth = density * 1.5f
        ring.color = TasbihUi.mix(material.accent, AppColors.BG_APP, 0.8f)
        canvas.drawCircle(cx, cy, r, ring)

        // Ilerleme halkasi
        val pr = r - density * 4
        ring.strokeWidth = density * 3.5f
        ring.color = AppColors.STROKE
        oval.set(cx - pr, cy - pr, cx + pr, cy + pr)
        canvas.drawOval(oval, ring)
        val progress = if (target > 0) (count.toFloat() / target).coerceIn(0f, 1f) else 0f
        if (progress > 0f) {
            ring.color = material.accent
            canvas.drawArc(oval, -90f, 360f * progress, false, ring)
        }

        // Sade boncuk dizisi: 12 boncuk ust ve yanlarda (240 derece);
        // alt kisim hedef yazisina kalir.
        val br = r - density * 12
        val beadD = (br * 0.28f).toInt().coerceAtLeast(4)
        val bead = TasbihBeadPainter.bead(material, beadD)
        val beads = 12
        val step = 240f / (beads - 1)
        for (i in 0 until beads) {
            val a = Math.toRadians((150f + step * i).toDouble())
            val x = cx + br * cos(a).toFloat()
            val y = cy + br * sin(a).toFloat()
            canvas.drawBitmap(bead, x - bead.width / 2f, y - bead.height / 2f, bmp)
        }

        // Sayi: buyuk ve kalin, her zaman; hedef altinda kucuk
        val main = TasbihUi.num(count)
        text.typeface = Typeface.DEFAULT_BOLD
        text.color = AppColors.TEXT_PRIMARY
        fit(main, s * 0.30f, br * 1.35f)
        canvas.drawText(main, cx, cy + text.textSize * 0.25f, text)
        if (target > 0) {
            val sub = "/ " + TasbihUi.num(target)
            text.typeface = Typeface.DEFAULT
            text.color = AppColors.TEXT_SECONDARY
            fit(sub, s * 0.13f, br * 0.95f)
            canvas.drawText(sub, cx, cy + s * 0.27f, text)
        }
    }

    /** Yazi genisligi asarsa kucultur. */
    private fun fit(value: String, size: Float, maxWidth: Float) {
        text.textSize = size
        val w = text.measureText(value)
        if (w > maxWidth) text.textSize = size * maxWidth / w
    }
}
