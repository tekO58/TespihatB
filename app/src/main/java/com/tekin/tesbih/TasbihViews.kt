package com.tekin.tesbih

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tesbih modulunun cizimleri. Resim dosyasi yok; boncuklar, halka ve
 * ilerleme cubugu koddan ciziliyor, renk tesbihin renginden geliyor.
 */

/**
 * Kucuk tespih simgesi (liste karti, ana ekran, ayarlar): halka seklinde
 * boncuklar, altta imame ve puskul. Sayma ekranindaki tespihle ayni
 * materyal cizicisini kullanir; bitmap'ler onbellekten gelir.
 */
class TasbihBeadsView(context: Context, material: TasbihMaterial) : View(context) {

    var material: TasbihMaterial = material
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = (height - s) / 2f + s * 0.36f
        val r = s * 0.27f
        val beads = 18
        val step = 360f / (beads + 1)
        val d = (2f * r * Math.sin(Math.toRadians(step / 2.0)).toFloat() * 0.95f).toInt()
        val bead = TasbihBeadPainter.bead(material, d)
        for (i in 0 until beads) {
            // Altta imamenin baglandigi yerde bir boncukluk bosluk
            val a = Math.toRadians((90f + step * (i + 1)).toDouble())
            val x = cx + r * Math.cos(a).toFloat()
            val y = cy + r * Math.sin(a).toFloat()
            canvas.drawBitmap(bead, x - bead.width / 2f, y - bead.height / 2f, paint)
        }
        val iw = (s * 0.2f).toInt()
        val ih = (s * 0.34f).toInt()
        val imame = TasbihBeadPainter.imame(material, iw, ih)
        canvas.drawBitmap(imame, cx - iw / 2f, cy + r - d * 0.2f, paint)
    }
}

/** Ince ilerleme cubugu. */
class TasbihBarView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var color: Int = AppColors.ACCENT
        set(value) { field = value; invalidate() }

    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        paint.color = AppColors.STROKE
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        if (progress > 0f) {
            rect.set(0f, 0f, maxOf(h, width * progress), h)
            paint.color = color
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        }
    }
}

/** Ortak yardimcilar: renk karistirma, kart zemini, metin. */
object TasbihUi {

    /** ratio 1: saf renk, 0: saf base. */
    fun mix(color: Int, base: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (color shr shift) and 0xFF
            val b = (base shr shift) and 0xFF
            return (a * r + b * (1 - r)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun card(context: Context, stroke: Int = AppColors.STROKE, fill: Int = AppColors.BG_CARD, radiusDp: Int = 16) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(context, 1), stroke)
        }

    fun oval(fill: Int, stroke: Int, strokePx: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(strokePx, stroke)
    }

    fun text(
        context: Context,
        value: CharSequence,
        sizeSp: Float,
        color: Int = AppColors.TEXT_PRIMARY,
        bold: Boolean = false,
        maxLines: Int = 0
    ) = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (maxLines > 0) {
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    fun row(context: Context, gravity: Int = Gravity.CENTER_VERTICAL) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
    }

    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun lp(width: Int, height: Int, weight: Float = 0f) = LinearLayout.LayoutParams(width, height, weight)

    const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    /** 1234567 -> "1.234.567" */
    fun num(value: Long): String = String.format(java.util.Locale.forLanguageTag("tr"), "%,d", value)
    fun num(value: Int): String = num(value.toLong())
}
