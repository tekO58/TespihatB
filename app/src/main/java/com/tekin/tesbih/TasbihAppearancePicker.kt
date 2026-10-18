package com.tekin.tesbih

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Tespih Gorunumu: materyal secimi.
 *
 * Tam ekran bir pencere; ustte buyuk onizleme, altta 10 materyal. Secim
 * "Bu görünümü kullan" ile onPick'e verilir; kaydetmek cagiranin isi
 * (ayarlar ekrani taslaga yazar, sayma ekrani dogrudan kaydeder). Yalnizca
 * o tesbihin gorunumu degisir.
 */
object TasbihAppearancePicker {

    fun show(activity: Activity, current: TasbihMaterial, onPick: (TasbihMaterial) -> Unit) {
        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar)
        var selected = current
        fun dp(v: Int) = TasbihUi.dp(activity, v)

        val root = TasbihUi.column(activity).apply { setBackgroundColor(AppColors.BG_APP) }

        val head = TasbihUi.row(activity).apply { setPadding(dp(8), dp(10), dp(16), dp(6)) }
        head.addView(TextView(activity).apply {
            text = "‹"; textSize = 30f; gravity = Gravity.CENTER
            setTextColor(AppColors.TEXT_PRIMARY)
            setOnClickListener { dialog.dismiss() }
        }, TasbihUi.lp(dp(44), dp(44)))
        head.addView(TasbihUi.text(activity, "Tespih Görünümü", 20f, bold = true), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        root.addView(head)

        val scroll = ScrollView(activity)
        val body = TasbihUi.column(activity).apply { setPadding(dp(16), dp(4), dp(16), dp(16)) }
        scroll.addView(body)
        root.addView(scroll, TasbihUi.lp(TasbihUi.MATCH, 0, 1f))

        // Buyuk onizleme
        val preview = TasbihUi.row(activity).apply {
            background = TasbihUi.card(activity)
            setPadding(dp(12), dp(12), dp(16), dp(12))
        }
        val previewBeads = TasbihBeadsView(activity, selected)
        preview.addView(previewBeads, TasbihUi.lp(dp(132), dp(150)).apply { marginEnd = dp(14) })
        val info = TasbihUi.column(activity)
        val infoName = TasbihUi.text(activity, "", 19f, bold = true)
        val infoDesc = TasbihUi.text(activity, "", 14f, AppColors.TEXT_SECONDARY)
        info.addView(infoName)
        info.addView(infoDesc, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(6) })
        preview.addView(info, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        body.addView(preview)

        body.addView(TasbihUi.text(activity, "Tespih Türü (Renk ve Materyal)", 16f, bold = true),
            TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(20); bottomMargin = dp(10) })

        val grid = TasbihUi.column(activity)
        body.addView(grid)

        val useButton = TextView(activity).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(AppColors.ON_ACCENT)
            setPadding(dp(12), dp(15), dp(12), dp(15))
            text = "Bu görünümü kullan"
            setOnClickListener {
                onPick(selected)
                dialog.dismiss()
            }
        }
        root.addView(useButton, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply {
            setMargins(dp(16), dp(6), dp(16), dp(16))
        })

        lateinit var renderGrid: () -> Unit
        fun paint() {
            previewBeads.material = selected
            infoName.text = selected.title
            infoDesc.text = selected.description
            useButton.background = TasbihUi.card(activity, stroke = TasbihUi.mix(selected.accent, android.graphics.Color.WHITE, 0.7f),
                fill = selected.accent, radiusDp = 16)
            renderGrid()
        }
        renderGrid = {
            grid.removeAllViews()
            TasbihMaterial.values().toList().chunked(COLUMNS).forEachIndexed { rowIndex, items ->
                val row = TasbihUi.row(activity, Gravity.TOP)
                items.forEachIndexed { i, m ->
                    val isSel = m == selected
                    val tile = TasbihUi.column(activity).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(4), dp(8), dp(4), dp(8))
                        background = TasbihUi.card(activity,
                            stroke = if (isSel) m.accent else AppColors.STROKE,
                            fill = if (isSel) TasbihUi.mix(m.accent, AppColors.BG_CARD, 0.12f) else AppColors.BG_CARD,
                            radiusDp = 14)
                        contentDescription = m.title
                        setOnClickListener { selected = m; paint() }
                    }
                    val icon = FrameLayout(activity)
                    icon.addView(TasbihBeadsView(activity, m), FrameLayout.LayoutParams(dp(62), dp(70), Gravity.CENTER))
                    if (isSel) {
                        icon.addView(TasbihUi.text(activity, "✓", 12f, AppColors.ON_ACCENT, bold = true).apply {
                            gravity = Gravity.CENTER
                            background = TasbihUi.oval(m.accent, m.accent, 0)
                        }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END))
                    }
                    tile.addView(icon, TasbihUi.lp(TasbihUi.MATCH, dp(72)))
                    tile.addView(TasbihUi.text(activity, m.label, 12.5f, bold = isSel, maxLines = 2).apply { gravity = Gravity.CENTER },
                        TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(4) })
                    if (m.detail.isNotBlank()) {
                        tile.addView(TasbihUi.text(activity, "(${m.detail})", 11f, AppColors.TEXT_MUTED, maxLines = 1).apply { gravity = Gravity.CENTER })
                    }
                    row.addView(tile, TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { if (i > 0) marginStart = dp(8) })
                }
                // Son sirada eksik kalan hucreler bos yer tutar.
                repeat(COLUMNS - items.size) {
                    row.addView(FrameLayout(activity), TasbihUi.lp(0, 1, 1f).apply { marginStart = dp(8) })
                }
                grid.addView(row, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { if (rowIndex > 0) topMargin = dp(8) })
            }
        }
        paint()

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(AppColors.BG_APP))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
    }

    private const val COLUMNS = 4
}
