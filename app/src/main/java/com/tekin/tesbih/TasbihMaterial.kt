package com.tekin.tesbih

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.random.Random

/**
 * Tespih materyalleri.
 *
 * Her biri yalnizca renk degil, bir "malzeme tarifi": parlaklik (gloss),
 * isik gecirgenligi (translucency), yuzey dokusu, ara parca metali ve
 * puskul rengi. Boncuk bu tarife gore TasbihBeadPainter'da bir kez
 * bitmap'e ciziliyor; ekranda yalnizca o bitmap kopyalaniyor.
 */
enum class TasbihMaterial(
    val label: String,
    val detail: String,
    val description: String,
    /** Govde: acik ton, ana ton, golge tonu. */
    val light: Int,
    val base: Int,
    val dark: Int,
    /** 0 mat .. 1 cam gibi parlak. */
    val gloss: Float,
    /** 0 opak .. 1 isigi iceri alan (kehribar gibi). */
    val translucency: Float,
    val texture: Texture,
    val metal: Metal,
    val tassel: Int,
    /** Halka, +1 dugmesi ve yazilar icin koyu zeminde okunan vurgu rengi. */
    val accent: Int
) {
    KUKA("Kuka", "doğal", "Doğal ahşap dokusu, mat ve sıcak.",
        0xFFB88458.toInt(), 0xFF6E4326.toInt(), 0xFF2E1A0D.toInt(),
        0.18f, 0f, Texture.WOOD, Metal.BRONZE, 0xFF8A5A33.toInt(), 0xFFC98E5A.toInt()),
    KEHRIBAR("Kehribar", "bal rengi", "Parlak, yarı saydam; ışığı içine alır.",
        0xFFFFE08A.toInt(), 0xFFE39A1E.toInt(), 0xFF7A3E05.toInt(),
        0.85f, 0.75f, Texture.CLOUD, Metal.GOLD, 0xFFC7861E.toInt(), 0xFFF2A93B.toInt()),
    TURUNCU_KEHRIBAR("Turuncu kehribar", "koyu bal", "Sıcak turuncu, yarı saydam ve parlak.",
        0xFFFFB27A.toInt(), 0xFFE0561C.toInt(), 0xFF6E1E05.toInt(),
        0.85f, 0.65f, Texture.CLOUD, Metal.GOLD, 0xFFC4521E.toInt(), 0xFFF07A2A.toInt()),
    ZUMRUT("Zümrüt", "altın işlemeli", "Koyu zümrüt yeşili, altın ara parçalar ve altın püskül.",
        0xFF6FD6A6.toInt(), 0xFF0E5A3C.toInt(), 0xFF02170F.toInt(),
        0.95f, 0.45f, Texture.CLOUD, Metal.GOLD, 0xFFC99A3A.toInt(), 0xFF3FCF8E.toInt()),
    YESIL("Yeşil akik", "doğal taş", "Doğal taş görünümü, parlak ve derin renk.",
        0xFF7FD9A0.toInt(), 0xFF1E7A45.toInt(), 0xFF0A2E19.toInt(),
        0.75f, 0.2f, Texture.VEINS, Metal.SILVER, 0xFF1F6B40.toInt(), 0xFF2FBF71.toInt()),
    SIYAH("Siyah", "oniks", "Derin siyah, ayna gibi parlak.",
        0xFF5A5F6B.toInt(), 0xFF15161A.toInt(), 0xFF000000.toInt(),
        1f, 0f, Texture.NONE, Metal.SILVER, 0xFF26282E.toInt(), 0xFFA9B1C2.toInt()),
    LACIVERT("Lacivert", "parlak", "Koyu mavi, cam gibi parlak.",
        0xFF7FA4FF.toInt(), 0xFF1C3FA8.toInt(), 0xFF07153F.toInt(),
        0.9f, 0.35f, Texture.NONE, Metal.SILVER, 0xFF1E3A8A.toInt(), 0xFF5B84F0.toInt()),
    MOR("Mor", "ametist", "Ametist moru, yarı saydam kristal.",
        0xFFD6B4FF.toInt(), 0xFF7A3FC4.toInt(), 0xFF2A0E4F.toInt(),
        0.85f, 0.5f, Texture.CLOUD, Metal.SILVER, 0xFF6A35A8.toInt(), 0xFFB08AF5.toInt()),
    KIRMIZI("Kırmızı", "akik", "Kırmızı akik, parlak ve derin.",
        0xFFFF9A8A.toInt(), 0xFFB5161E.toInt(), 0xFF45050A.toInt(),
        0.8f, 0.4f, Texture.CLOUD, Metal.GOLD, 0xFF9E1B22.toInt(), 0xFFE8535A.toInt()),
    BEYAZ("Beyaz / krem", "howlit", "Kırık beyaz taş, ince gri damarlı.",
        0xFFFFFFFF.toInt(), 0xFFE6E1D6.toInt(), 0xFF8E887C.toInt(),
        0.45f, 0f, Texture.VEINS, Metal.GOLD, 0xFFCFC8B8.toInt(), 0xFFEDE7DA.toInt()),
    TURKUAZ("Turkuaz", "doğal taş", "Doğal turkuaz, koyu damarlı, yarı mat.",
        0xFF9BF0EA.toInt(), 0xFF1AA6A0.toInt(), 0xFF064A48.toInt(),
        0.4f, 0f, Texture.MATRIX, Metal.SILVER, 0xFF148C87.toInt(), 0xFF2EC4C0.toInt());

    enum class Texture { NONE, WOOD, CLOUD, VEINS, MATRIX }
    enum class Metal(val light: Int, val base: Int, val dark: Int) {
        GOLD(0xFFFFF1B8.toInt(), 0xFFC99A3A.toInt(), 0xFF5E4210.toInt()),
        SILVER(0xFFFFFFFF.toInt(), 0xFFA7ADB8.toInt(), 0xFF3B4049.toInt()),
        BRONZE(0xFFF3C79A.toInt(), 0xFF9A6434.toInt(), 0xFF3E2410.toInt())
    }

    val title: String get() = if (detail.isBlank()) label else "$label ($detail)"

    companion object {
        /** Eski kayitlardaki renk sirasindan en yakin materyal. */
        fun fromLegacyColor(index: Int): TasbihMaterial = when (index) {
            1 -> YESIL
            2 -> MOR
            3 -> LACIVERT
            4 -> KIRMIZI
            else -> KEHRIBAR
        }

        /** Yeni tesbihlere sirayla verilen materyaller. */
        fun forIndex(index: Int): TasbihMaterial {
            val order = listOf(ZUMRUT, KUKA, YESIL, KEHRIBAR, SIYAH, LACIVERT, MOR, KIRMIZI, TURKUAZ, TURUNCU_KEHRIBAR, BEYAZ)
            return order[((index % order.size) + order.size) % order.size]
        }

        fun byName(name: String?): TasbihMaterial? = values().firstOrNull { it.name == name }
    }
}

/**
 * Boncuk, ara parca, nisane ve imameyi bitmap'e cizer.
 *
 * Her parca (materyal, boyut) basina yalnizca bir kez ciziliyor ve
 * onbellekte tutuluyor; animasyon sirasinda yalnizca drawBitmap
 * cagriliyor. Boylece 33 boncuklu tespih dusuk seviye cihazda da akici.
 */
object TasbihBeadPainter {

    private val cache = object : android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Capi diameterPx olan boncuk; bitmap golge icin biraz daha buyuk. */
    fun bead(m: TasbihMaterial, diameterPx: Int): Bitmap {
        val d = diameterPx.coerceAtLeast(4)
        val key = "b:${m.name}:$d"
        cache.get(key)?.let { return it }
        val pad = (d * 0.18f).toInt() + 1
        val bmp = Bitmap.createBitmap(d + pad * 2, d + pad * 2, Bitmap.Config.ARGB_8888)
        drawBead(Canvas(bmp), m, pad + d / 2f, pad + d / 2f, d / 2f, seed = m.ordinal * 31 + d)
        cache.put(key, bmp)
        return bmp
    }

    /** Iki boncuk arasindaki kucuk metal halka. */
    fun spacer(m: TasbihMaterial, diameterPx: Int): Bitmap {
        val d = diameterPx.coerceAtLeast(3)
        val key = "s:${m.metal.name}:$d"
        cache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(d + 2, d + 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = d / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(1 + r * 0.7f, 1 + r * 0.6f, r * 1.3f,
            intArrayOf(m.metal.light, m.metal.base, m.metal.dark), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(1 + r, 1 + r, r, p)
        cache.put(key, bmp)
        return bmp
    }

    /**
     * Nisane (ara parca): ipe dik duran metal silindir. Dik cizilir;
     * cizen taraf ipin yonune gore dondurur.
     */
    fun separator(m: TasbihMaterial, widthPx: Int, heightPx: Int): Bitmap {
        val w = widthPx.coerceAtLeast(3)
        val h = heightPx.coerceAtLeast(4)
        val key = "n:${m.metal.name}:$w:$h"
        cache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(w + 2, h + 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(1f, 1f, 1f + w, 1f + h)
        p.shader = LinearGradient(rect.left, 0f, rect.right, 0f,
            intArrayOf(m.metal.dark, m.metal.light, m.metal.base, m.metal.dark),
            floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, w * 0.35f, w * 0.35f, p)
        // Ortadaki oyma cizgileri
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m.metal.dark; alpha = 110; strokeWidth = 1f }
        for (k in 1..3) {
            val y = rect.top + h * k / 4f
            c.drawLine(rect.left + 1, y, rect.right - 1, y, line)
        }
        cache.put(key, bmp)
        return bmp
    }

    /**
     * Imame, baslik ve puskul; tespihin altinda sabit durur.
     * width: bitmap genisligi, height: toplam boy. Ust orta nokta ipin
     * baglandigi yer.
     */
    fun imame(m: TasbihMaterial, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(8)
        val h = height.coerceAtLeast(16)
        val key = "i:${m.name}:$w:$h"
        cache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val capH = h * 0.07f
        val bodyH = h * 0.30f
        val bodyW = w * 0.3f
        val neckH = h * 0.06f
        var y = 0f

        // Ust metal baslik
        metalCap(c, p, m, cx, y, w * 0.20f, capH)
        y += capH
        // Imame govdesi: uzun damla, boncukla ayni materyal
        val body = RectF(cx - bodyW / 2f, y, cx + bodyW / 2f, y + bodyH)
        p.shader = LinearGradient(body.left, 0f, body.right, 0f,
            intArrayOf(m.dark, m.light, m.base, m.dark), floatArrayOf(0f, 0.3f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(body, bodyW / 2f, bodyW / 2.2f, p)
        p.shader = null
        if (m.gloss > 0.3f) {
            p.color = Color.WHITE
            p.alpha = (m.gloss * 150).toInt()
            c.drawRoundRect(RectF(body.left + bodyW * 0.22f, body.top + bodyH * 0.12f,
                body.left + bodyW * 0.38f, body.bottom - bodyH * 0.2f), bodyW, bodyW, p)
            p.alpha = 255
        }
        y += bodyH
        // Alt metal boyun
        metalCap(c, p, m, cx, y, w * 0.24f, neckH)
        y += neckH

        // Puskul: ince ipler, ucu hafif dagilan
        val tasselTop = y
        val tasselH = h - y - 1
        val strands = 26
        val strand = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = (w * 0.018f).coerceAtLeast(1f) }
        val rnd = Random(m.ordinal * 7 + w)
        val path = Path()
        for (i in 0 until strands) {
            val f = i / (strands - 1f) - 0.5f
            val endX = cx + f * w * 0.55f + (rnd.nextFloat() - 0.5f) * w * 0.04f
            val endY = tasselTop + tasselH * (0.9f + rnd.nextFloat() * 0.1f)
            path.reset()
            path.moveTo(cx + f * w * 0.12f, tasselTop)
            path.quadTo(cx + f * w * 0.2f, tasselTop + tasselH * 0.5f, endX, endY)
            val shade = 0.55f + rnd.nextFloat() * 0.45f
            strand.color = TasbihUi.mix(m.tassel, Color.BLACK, shade)
            c.drawPath(path, strand)
        }
        // Puskulun ust dugumu
        metalCap(c, p, m, cx, tasselTop - 1, w * 0.16f, h * 0.035f)
        cache.put(key, bmp)
        return bmp
    }

    private fun metalCap(c: Canvas, p: Paint, m: TasbihMaterial, cx: Float, top: Float, width: Float, height: Float) {
        val r = RectF(cx - width / 2f, top, cx + width / 2f, top + height)
        p.shader = LinearGradient(r.left, 0f, r.right, 0f,
            intArrayOf(m.metal.dark, m.metal.light, m.metal.base, m.metal.dark),
            floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, height * 0.4f, height * 0.4f, p)
        p.shader = null
    }

    /** Tek boncuk: golge, govde, isik gecirgenligi, doku, yansima. */
    private fun drawBead(c: Canvas, m: TasbihMaterial, x: Float, y: Float, r: Float, seed: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1) Yere dusen yumusak golge
        p.shader = RadialGradient(x + r * 0.12f, y + r * 0.2f, r * 1.15f,
            intArrayOf(0x66000000, 0x00000000), floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x + r * 0.12f, y + r * 0.2f, r * 1.15f, p)

        // 2) Govde: sol ustten isik alan kure. Mat materyalde gecis yumusak.
        val lightStop = if (m.gloss < 0.3f) 0.05f else 0f
        p.shader = RadialGradient(x - r * 0.32f, y - r * 0.38f, r * 1.45f,
            intArrayOf(m.light, m.base, m.dark), floatArrayOf(lightStop, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)

        // 3) Isik gecirgenligi: isik tasin icinden gecip alt sagda parlar,
        //    kenar koyulasir (kehribar, ametist).
        if (m.translucency > 0f) {
            val glow = TasbihUi.mix(m.light, m.base, 0.7f)
            p.shader = RadialGradient(x + r * 0.28f, y + r * 0.34f, r * 0.75f,
                intArrayOf(withAlpha(glow, (m.translucency * 200).toInt()), withAlpha(glow, 0)),
                null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
            p.shader = RadialGradient(x, y, r,
                intArrayOf(withAlpha(m.dark, 0), withAlpha(m.dark, (m.translucency * 150).toInt())),
                floatArrayOf(0.72f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        p.shader = null

        // 4) Doku: yalnizca boncugun icine
        c.save()
        val clip = Path().apply { addCircle(x, y, r * 0.98f, Path.Direction.CW) }
        c.clipPath(clip)
        val rnd = Random(seed)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        when (m.texture) {
            TasbihMaterial.Texture.WOOD -> {
                // Ahsap lifleri: boncugun ekseni boyunca hafif egri cizgiler
                stroke.strokeWidth = (r * 0.07f).coerceAtLeast(0.8f)
                for (i in -4..4) {
                    stroke.color = withAlpha(m.dark, 45 + rnd.nextInt(40))
                    val ox = x + i * r * 0.24f + (rnd.nextFloat() - 0.5f) * r * 0.1f
                    c.drawArc(RectF(ox - r * 1.6f, y - r * 1.2f, ox + r * 0.2f, y + r * 1.2f), -40f, 80f, false, stroke)
                }
            }
            TasbihMaterial.Texture.CLOUD -> {
                // Icerdeki bulutsu yogunluk farki
                for (i in 0 until 3) {
                    val cx = x + (rnd.nextFloat() - 0.5f) * r
                    val cy = y + (rnd.nextFloat() - 0.5f) * r
                    p.shader = RadialGradient(cx, cy, r * 0.55f,
                        intArrayOf(withAlpha(m.dark, 60), withAlpha(m.dark, 0)), null, Shader.TileMode.CLAMP)
                    c.drawCircle(cx, cy, r * 0.55f, p)
                }
                p.shader = null
            }
            TasbihMaterial.Texture.VEINS -> {
                // Ince damarlar (akik, howlit)
                stroke.strokeWidth = (r * 0.05f).coerceAtLeast(0.7f)
                repeat(4) {
                    stroke.color = withAlpha(if (m == TasbihMaterial.BEYAZ) 0xFF7D776B.toInt() else m.light, 55 + rnd.nextInt(45))
                    val path = Path()
                    var px = x - r + rnd.nextFloat() * r * 0.4f
                    var py = y - r + rnd.nextFloat() * r * 2f
                    path.moveTo(px, py)
                    repeat(4) {
                        val nx = px + r * (0.35f + rnd.nextFloat() * 0.3f)
                        val ny = py + (rnd.nextFloat() - 0.5f) * r * 0.7f
                        path.quadTo((px + nx) / 2f, py + (rnd.nextFloat() - 0.5f) * r * 0.5f, nx, ny)
                        px = nx; py = ny
                    }
                    c.drawPath(path, stroke)
                }
            }
            TasbihMaterial.Texture.MATRIX -> {
                // Turkuazin koyu ag damarlari
                stroke.strokeWidth = (r * 0.06f).coerceAtLeast(0.8f)
                stroke.color = withAlpha(0xFF3A2A18.toInt(), 110)
                repeat(6) {
                    val sx = x + (rnd.nextFloat() - 0.5f) * r * 2f
                    val sy = y + (rnd.nextFloat() - 0.5f) * r * 2f
                    val path = Path().apply {
                        moveTo(sx, sy)
                        lineTo(sx + (rnd.nextFloat() - 0.5f) * r * 0.9f, sy + (rnd.nextFloat() - 0.5f) * r * 0.9f)
                        lineTo(sx + (rnd.nextFloat() - 0.5f) * r * 1.2f, sy + (rnd.nextFloat() - 0.5f) * r * 1.2f)
                    }
                    c.drawPath(path, stroke)
                }
            }
            TasbihMaterial.Texture.NONE -> Unit
        }
        c.restore()

        // 5) Yansima: parlak materyalde keskin, matta dagink
        val spec = Paint(Paint.ANTI_ALIAS_FLAG)
        val hx = x - r * 0.36f
        val hy = y - r * 0.42f
        val hr = r * (0.55f - m.gloss * 0.22f)
        spec.shader = RadialGradient(hx, hy, hr,
            intArrayOf(withAlpha(Color.WHITE, (70 + m.gloss * 185).toInt()), withAlpha(Color.WHITE, 0)),
            floatArrayOf(if (m.gloss > 0.6f) 0.25f else 0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(hx, hy, hr, spec)
        // Alt kenarda ince geri yansima
        if (m.gloss > 0.4f) {
            stroke.shader = null
            stroke.strokeWidth = r * 0.1f
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.color = withAlpha(Color.WHITE, (m.gloss * 70).toInt())
            c.drawArc(RectF(x - r * 0.78f, y - r * 0.78f, x + r * 0.78f, y + r * 0.78f), 30f, 60f, false, stroke)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
