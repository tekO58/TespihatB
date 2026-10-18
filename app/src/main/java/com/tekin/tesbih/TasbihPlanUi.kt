package com.tekin.tesbih

import android.app.Activity
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat

/**
 * Tespih modulunun "Plan" sekmesi: haftanin ozeti, bugunun plani, 7 gun,
 * plan ogeleri ve plan ayarlari. Oge duzenleme penceresi de burada.
 *
 * Yalnizca Tespih kaydini okur/yazar (TasbihStore, TasbihPlanStore).
 */
class TasbihPlanScreen(private val activity: Activity, private val onChanged: () -> Unit) {

    private val store = TasbihStore(activity)
    private val plan = TasbihPlanStore(activity)

    private fun dp(v: Int) = TasbihUi.dp(activity, v)

    fun render(body: LinearLayout) {
        val reader = TasbihPlanReader(activity)
        val items = plan.items()
        val tasbihs = store.getAll()
        val names = tasbihs.associate { it.id to it.name }

        body.addView(TasbihUi.text(activity, "Haftalık Plan", 26f, bold = true))
        body.addView(TasbihUi.text(activity, "Hangi gün hangi zikri, kaç adet ve saat kaçta çekeceğini planla. İlerleme çektiğin sayılardan kendiliğinden hesaplanır.",
            13.5f, AppColors.TEXT_SECONDARY), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(4) })

        if (items.isEmpty()) {
            body.addView(emptyCard(), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(18) })
            return
        }

        if (plan.isPaused()) {
            val banner = TasbihUi.row(activity).apply {
                background = TasbihUi.card(activity, stroke = AppColors.ACCENT, fill = TasbihUi.mix(AppColors.ACCENT, AppColors.BG_CARD, 0.10f), radiusDp = 12)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            banner.addView(TasbihUi.text(activity, "⏸  Plan duraklatıldı · hatırlatma gelmez", 13.5f), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
            banner.addView(pill("Devam et") { plan.setPaused(false); TasbihPlanAlarms.schedule(activity); onChanged() })
            body.addView(banner, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(14) })
        }

        val week = reader.week()
        body.addView(weekCard(week), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(16) })
        body.addView(todayCard(reader), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(12) })
        body.addView(weekStrip(week), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(12) })

        val head = TasbihUi.row(activity)
        head.addView(TasbihUi.text(activity, "Plan öğeleri", 19f, bold = true), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        head.addView(pill("＋  Ekle") { edit(null, null) })
        body.addView(head, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(22); bottomMargin = dp(6) })
        items.forEach { item ->
            body.addView(itemCard(item, names), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })
        }

        body.addView(TasbihUi.text(activity, "Plan ayarları", 16f, bold = true),
            TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(22); bottomMargin = dp(8) })
        body.addView(settingsCard())
    }

    // ------------------------------------------------------------- kartlar

    private fun emptyCard(): View {
        val box = TasbihUi.column(activity).apply {
            background = TasbihUi.card(activity)
            setPadding(dp(18), dp(20), dp(18), dp(18))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        box.addView(TasbihUi.text(activity, "Henüz plan yok", 16f, bold = true).apply { gravity = Gravity.CENTER })
        box.addView(TasbihUi.text(activity, "Hazır bir şablonla başla ya da kendi öğeni ekle. Sonradan her günü ayrı ayrı değiştirebilirsin.",
            13.5f, AppColors.TEXT_SECONDARY).apply { gravity = Gravity.CENTER },
            TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(4); bottomMargin = dp(14) })
        box.addView(bigButton("Haftalık plan oluştur") { templates() })
        box.addView(TextView(activity).apply {
            text = "＋  Kendi öğemi ekle"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(AppColors.TEXT_SECONDARY)
            setPadding(dp(8), dp(12), dp(8), dp(4))
            setOnClickListener { edit(null, null) }
        })
        return box
    }

    private fun weekCard(week: TasbihPlanReader.Week): View {
        val card = TasbihUi.column(activity).apply {
            background = TasbihUi.card(activity)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(TasbihUi.text(activity, "BU HAFTA", 11.5f, AppColors.TEXT_MUTED, bold = true))
        val value = TasbihUi.row(activity, Gravity.BOTTOM)
        value.addView(TasbihUi.text(activity, TasbihUi.num(week.done), 26f, AppColors.ACCENT, bold = true))
        value.addView(TasbihUi.text(activity, " / " + TasbihUi.num(week.target), 16f, AppColors.TEXT_SECONDARY))
        value.addView(View(activity), TasbihUi.lp(0, 1, 1f))
        value.addView(TasbihUi.text(activity, "%${week.percent}", 18f, AppColors.ACCENT, bold = true))
        card.addView(value)
        card.addView(TasbihBarView(activity).apply {
            color = AppColors.ACCENT
            progress = if (week.target > 0) (week.done.toFloat() / week.target).coerceIn(0f, 1f) else 0f
        }, TasbihUi.lp(TasbihUi.MATCH, dp(6)).apply { topMargin = dp(8) })
        val parts = mutableListOf("✅ ${week.daysDone} / ${week.daysPlanned} gün")
        if (week.timeMs > 0) parts += "⏱ " + TasbihTime.total(week.timeMs)
        card.addView(TasbihUi.text(activity, parts.joinToString("  ·  "), 13f, AppColors.TEXT_SECONDARY),
            TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(8) })
        return card
    }

    private fun todayCard(reader: TasbihPlanReader): View {
        val now = System.currentTimeMillis()
        val card = TasbihUi.column(activity).apply {
            background = TasbihUi.card(activity, stroke = TasbihUi.mix(AppColors.ACCENT, AppColors.BG_CARD, 0.4f))
            setPadding(dp(16), dp(14), dp(12), dp(10))
        }
        card.addView(TasbihUi.text(activity, "BUGÜNÜN PLANI · " + TasbihPlanLogic.dayLong(TasbihPlanLogic.dayOfWeek(now)).uppercase(java.util.Locale.forLanguageTag("tr")),
            11.5f, AppColors.TEXT_MUTED, bold = true))
        val today = reader.today(now)
        if (today.isEmpty()) {
            card.addView(TasbihUi.text(activity, "Bugün için plan yok.", 14f, AppColors.TEXT_SECONDARY),
                TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(6); bottomMargin = dp(4) })
            return card
        }
        today.forEach { p -> card.addView(progressRow(p, reader)) }
        return card
    }

    private fun progressRow(p: TasbihPlanProgress, reader: TasbihPlanReader): View {
        val e = p.entry
        val t = if (e.isFree) null else reader.tasbih(e.tasbihId)
        val color = t?.accent ?: AppColors.ACCENT
        val row = TasbihUi.row(activity).apply { setPadding(0, dp(10), 0, dp(6)) }
        val col = TasbihUi.column(activity)
        col.addView(TasbihUi.text(activity, "📿 " + e.name, 15f, bold = true, maxLines = 1))
        val line = mutableListOf("${TasbihUi.num(p.done)} / ${TasbihUi.num(e.target)}")
        if (e.minute >= 0) line += "⏰ " + TasbihPlanLogic.hhmm(e.minute)
        if (!p.complete && t != null) TasbihTime.etaMs(t.intervals, e.target - p.done)?.let { line += TasbihTime.approx(it) }
        col.addView(TasbihUi.text(activity, (if (p.complete) "✅ " else "") + line.joinToString("  ·  "), 13f,
            if (p.complete) AppColors.SUCCESS else AppColors.TEXT_SECONDARY, maxLines = 1))
        col.addView(TasbihBarView(activity).apply { this.color = color; progress = (p.done.toFloat() / e.target).coerceIn(0f, 1f) },
            TasbihUi.lp(TasbihUi.MATCH, dp(5)).apply { topMargin = dp(6); marginEnd = dp(10) })
        row.addView(col, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        if (!p.complete) row.addView(pill("Başla →") { start(e) })
        return row
    }

    /** Kalemin tespihini ac; Serbest'te son kullanilan tespih. */
    private fun start(e: TasbihPlanEntry) {
        val id = if (e.isFree) (store.getLast() ?: store.getAll().firstOrNull())?.id else e.tasbihId
        if (id == null) {
            Toast.makeText(activity, "Önce bir tesbih ekle", Toast.LENGTH_SHORT).show()
            return
        }
        TasbihActions.openCounter(activity, id)
    }

    private fun weekStrip(week: TasbihPlanReader.Week): View {
        val todayKey = TasbihStore.dayKey(System.currentTimeMillis())
        val row = TasbihUi.row(activity).apply {
            background = TasbihUi.card(activity)
            setPadding(dp(6), dp(10), dp(6), dp(10))
        }
        week.days.forEachIndexed { i, (day, progress) ->
            val status = TasbihPlanLogic.status(progress)
            val isToday = day == todayKey
            val cell = TasbihUi.column(activity).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(4))
                if (isToday) background = TasbihUi.card(activity, stroke = AppColors.ACCENT,
                    fill = TasbihUi.mix(AppColors.ACCENT, AppColors.BG_CARD, 0.10f), radiusDp = 10)
                isClickable = true
                setOnClickListener { dayDetail(i + 1, day, progress) }
            }
            cell.addView(TasbihUi.text(activity, TasbihPlanLogic.dayShort(i + 1), 12f,
                if (isToday) AppColors.ACCENT else AppColors.TEXT_MUTED, bold = isToday).apply { gravity = Gravity.CENTER })
            val (mark, color) = when {
                status == TasbihDayStatus.DONE -> "✓" to AppColors.SUCCESS
                status == TasbihDayStatus.PARTIAL -> "◐" to AppColors.ACCENT
                status == TasbihDayStatus.NONE -> "–" to AppColors.TEXT_MUTED
                day > todayKey -> "○" to AppColors.TEXT_MUTED
                else -> "○" to AppColors.DANGER
            }
            cell.addView(TasbihUi.text(activity, mark, 17f, color, bold = true).apply { gravity = Gravity.CENTER })
            row.addView(cell, TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        return row
    }

    private fun dayDetail(dow: Int, day: String, progress: List<TasbihPlanProgress>) {
        val text = if (progress.isEmpty()) "Bu gün için plan yok." else progress.joinToString("\n") { p ->
            val e = p.entry
            (if (p.complete) "✅ " else "• ") + e.name + " — " + TasbihUi.num(p.done) + " / " + TasbihUi.num(e.target) +
                (if (e.minute >= 0) "  ⏰ " + TasbihPlanLogic.hhmm(e.minute) else "")
        }
        val time = store.timeOfDay(day)
        AlertDialog.Builder(activity)
            .setTitle(TasbihPlanLogic.dayLong(dow) + " · " + TasbihStore.dayLabel(day))
            .setMessage(text + if (time > 0) "\n\n⏱ " + TasbihTime.total(time) else "")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun itemCard(item: TasbihPlanItem, names: Map<String, String>): View {
        val missing = !item.isFree && !names.containsKey(item.tasbihId)
        val card = TasbihUi.column(activity).apply {
            background = TasbihUi.card(activity)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            alpha = if (item.active && !missing) 1f else 0.55f
            isClickable = true
            setOnClickListener { edit(item, null) }
        }
        val name = when {
            item.isFree -> TasbihPlanLogic.FREE_NAME + " (tüm tesbihler)"
            missing -> "Tespih silinmiş"
            else -> names.getValue(item.tasbihId)
        }
        val head = TasbihUi.row(activity)
        head.addView(TasbihUi.text(activity, "📿 $name", 15.5f, if (missing) AppColors.DANGER else AppColors.TEXT_PRIMARY, bold = true, maxLines = 1),
            TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        val badges = mutableListOf<String>()
        if (!item.active) badges += "pasif"
        if (!item.reminder) badges += "🔕"
        if (badges.isNotEmpty()) head.addView(TasbihUi.text(activity, badges.joinToString(" · "), 12.5f, AppColors.TEXT_MUTED))
        card.addView(head)
        val (common, exceptions) = TasbihPlanLogic.splitCommon(item.days)
        if (common != null) {
            val parts = mutableListOf("🎯 " + TasbihUi.num(common.target))
            if (common.minute >= 0) parts += "⏰ " + TasbihPlanLogic.hhmm(common.minute)
            parts += "🔁 " + TasbihPlanLogic.daysLabel(item.days.keys)
            card.addView(TasbihUi.text(activity, parts.joinToString("  ·  "), 13f, AppColors.TEXT_SECONDARY),
                TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(4) })
        }
        if (exceptions.isNotEmpty()) {
            card.addView(TasbihUi.text(activity, "Farklı: " + exceptions.toSortedMap().entries.joinToString(" · ") { (d, v) ->
                TasbihPlanLogic.dayShort(d) + " " + TasbihUi.num(v.target) + (if (v.minute >= 0) " " + TasbihPlanLogic.hhmm(v.minute) else "")
            }, 12.5f, AppColors.ACCENT), TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(2) })
        }
        return card
    }

    private fun settingsCard(): View {
        val card = TasbihUi.column(activity).apply {
            background = TasbihUi.card(activity)
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        val second = plan.secondReminderMin()
        val secondRow = settingRow("⏰", "İkinci hatırlatma", secondLabel(second),
            "Hedef tamamlanmadıysa ilk hatırlatmadan bu kadar sonra bir kez daha hatırlatır.") {
            val options = TasbihPlanStore.SECOND_OPTIONS
            AlertDialog.Builder(activity)
                .setTitle("İkinci hatırlatma")
                .setSingleChoiceItems(options.map { secondLabel(it) }.toTypedArray(), options.indexOf(plan.secondReminderMin())) { d, which ->
                    plan.setSecondReminderMin(options[which])
                    TasbihPlanAlarms.schedule(activity)
                    d.dismiss()
                    onChanged()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
        card.addView(secondRow)
        card.addView(View(activity).apply { setBackgroundColor(AppColors.STROKE) }, TasbihUi.lp(TasbihUi.MATCH, 1))
        val pause = TasbihUi.row(activity).apply { setPadding(0, dp(10), 0, dp(10)) }
        val texts = TasbihUi.column(activity)
        texts.addView(TasbihUi.text(activity, "⏸  Planı duraklat", 15f))
        texts.addView(TasbihUi.text(activity, "Seyahat, hastalık gibi dönemlerde hatırlatmalar gelmez. Plan silinmez.", 12.5f, AppColors.TEXT_MUTED))
        pause.addView(texts, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        pause.addView(SwitchCompat(activity).apply {
            isChecked = plan.isPaused()
            setOnCheckedChangeListener { _, on ->
                plan.setPaused(on)
                TasbihPlanAlarms.schedule(activity)
                onChanged()
            }
        })
        card.addView(pause)
        card.addView(View(activity).apply { setBackgroundColor(AppColors.STROKE) }, TasbihUi.lp(TasbihUi.MATCH, 1))
        card.addView(settingRow("📋", "Şablondan öğe ekle", "", "Her gün, hafta içi / hafta sonu, Cuma ağırlıklı.") { templates() })
        return card
    }

    private fun secondLabel(min: Int) = if (min <= 0) "Kapalı" else "${min / 60} sa sonra"

    // ------------------------------------------------------------ sablonlar

    private fun templates() {
        val all = TasbihPlanLogic.ALL_DAYS
        val options = listOf(
            "Her gün aynı zikir  (1.000 · 20:00)" to all.associateWith { TasbihPlanDay(1000, 20 * 60) },
            "Hafta içi 500 · hafta sonu 1.000" to all.associateWith {
                if (it in TasbihPlanLogic.WEEKEND) TasbihPlanDay(1000, 11 * 60) else TasbihPlanDay(500, 20 * 60)
            },
            "Cuma ağırlıklı  (her gün 1.000 · Cuma 3.000)" to all.associateWith {
                if (it == 5) TasbihPlanDay(3000, 18 * 60 + 30) else TasbihPlanDay(1000, 20 * 60)
            }
        )
        AlertDialog.Builder(activity)
            .setTitle("Haftalık plan oluştur")
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                val defaultTasbih = (store.getLast() ?: store.getAll().firstOrNull())?.id.orEmpty()
                edit(null, TasbihPlanItem("", defaultTasbih, options[which].second))
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // -------------------------------------------------------------- duzenle

    /** existing: duzenlenen oge; draft: yeni oge icin hazir degerler (sablon). */
    private fun edit(existing: TasbihPlanItem?, draft: TasbihPlanItem?) {
        val tasbihs = store.getAll()
        val source = existing ?: draft
        var tasbihId = source?.tasbihId ?: (store.getLast() ?: tasbihs.firstOrNull())?.id.orEmpty()
        val (common, initialExceptions) = TasbihPlanLogic.splitCommon(source?.days ?: emptyMap())
        var baseMinute = common?.minute ?: (20 * 60)
        val days = (source?.days?.keys ?: TasbihPlanLogic.ALL_DAYS).toMutableSet()
        val exceptions = initialExceptions.toMutableMap()
        var reminder = source?.reminder ?: true
        var active = source?.active ?: true

        val box = TasbihUi.column(activity).apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        fun label(text: String) = TasbihUi.text(activity, text, 11.5f, AppColors.DIALOG_LABEL, bold = true).also {
            box.addView(it, TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(14); bottomMargin = dp(4) })
        }
        fun rowButton(): TextView = TextView(activity).apply {
            textSize = 15f
            setTextColor(AppColors.DIALOG_TEXT)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        label("📿 TESPİH")
        val tasbihButton = rowButton()
        fun paintTasbih() {
            tasbihButton.text = (if (tasbihId.isBlank()) TasbihPlanLogic.FREE_NAME + " (tüm tesbihler)"
            else tasbihs.firstOrNull { it.id == tasbihId }?.name ?: "Tespih silinmiş") + "  ›"
        }
        tasbihButton.setOnClickListener {
            val names = listOf(TasbihPlanLogic.FREE_NAME + " (tüm tesbihler)") + tasbihs.map { it.name }
            val ids = listOf("") + tasbihs.map { it.id }
            AlertDialog.Builder(activity)
                .setTitle("Tespih")
                .setSingleChoiceItems(names.toTypedArray(), ids.indexOf(tasbihId)) { d, which ->
                    tasbihId = ids[which]; paintTasbih(); d.dismiss()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
        paintTasbih()
        box.addView(tasbihButton)

        label("🎯 HEDEF (ADET)")
        val targetInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((common?.target ?: 1000).toString())
            setSingleLine(true)
        }
        box.addView(targetInput)

        label("📅 GÜNLER")
        val quick = TasbihUi.row(activity)
        val dayRow = TasbihUi.row(activity)
        val dayViews = (1..7).map { d ->
            TextView(activity).apply {
                text = TasbihPlanLogic.dayShort(d)
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
            }
        }
        fun paintDays() {
            dayViews.forEachIndexed { i, v ->
                val on = (i + 1) in days
                v.setTextColor(if (on) AppColors.ON_ACCENT else AppColors.DIALOG_MUTED)
                v.background = TasbihUi.card(activity, stroke = if (on) AppColors.ACCENT else AppColors.DIALOG_MUTED,
                    fill = if (on) AppColors.ACCENT else android.graphics.Color.TRANSPARENT, radiusDp = 10)
            }
        }
        dayViews.forEachIndexed { i, v ->
            v.setOnClickListener { val d = i + 1; if (d in days) days.remove(d) else days.add(d); paintDays() }
            dayRow.addView(v, TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        listOf("Her gün" to TasbihPlanLogic.ALL_DAYS, "Hafta içi" to TasbihPlanLogic.WEEKDAYS, "Hafta sonu" to TasbihPlanLogic.WEEKEND)
            .forEach { (name, set) ->
                quick.addView(TextView(activity).apply {
                    text = name
                    textSize = 12.5f
                    gravity = Gravity.CENTER
                    setTextColor(AppColors.DIALOG_TEXT)
                    background = TasbihUi.card(activity, stroke = AppColors.DIALOG_MUTED, fill = android.graphics.Color.TRANSPARENT, radiusDp = 12)
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    setOnClickListener { days.clear(); days.addAll(set); paintDays() }
                }, TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
            }
        box.addView(quick)
        box.addView(dayRow, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })
        paintDays()

        label("⏰ SAAT")
        val timeButton = rowButton()
        fun paintTime() { timeButton.text = (if (baseMinute < 0) "Saat yok" else TasbihPlanLogic.hhmm(baseMinute)) + "  ›" }
        timeButton.setOnClickListener {
            pickTime(baseMinute) { baseMinute = it; paintTime() }
        }
        paintTime()
        box.addView(timeButton)

        label("FARKLI GÜNLER")
        val exceptionBox = TasbihUi.column(activity)
        box.addView(exceptionBox)
        fun paintExceptions() {
            exceptionBox.removeAllViews()
            exceptions.toSortedMap().forEach { (d, v) ->
                val r = TasbihUi.row(activity).apply { setPadding(0, dp(4), 0, dp(4)) }
                r.addView(TasbihUi.text(activity, TasbihPlanLogic.dayLong(d) + " · " + TasbihUi.num(v.target) +
                    (if (v.minute >= 0) " · " + TasbihPlanLogic.hhmm(v.minute) else " · saat yok"), 14f, AppColors.DIALOG_TEXT),
                    TasbihUi.lp(0, TasbihUi.WRAP, 1f))
                r.addView(TextView(activity).apply {
                    text = "✕"
                    textSize = 16f
                    setTextColor(AppColors.DIALOG_DANGER)
                    setPadding(dp(10), dp(2), dp(4), dp(2))
                    setOnClickListener { exceptions.remove(d); paintExceptions() }
                })
                exceptionBox.addView(r)
            }
            exceptionBox.addView(TextView(activity).apply {
                text = "＋  Bir güne farklı hedef / saat"
                textSize = 13.5f
                setTextColor(AppColors.DIALOG_ACCENT)
                setPadding(0, dp(6), 0, dp(4))
                setOnClickListener {
                    val choices = days.sorted()
                    if (choices.isEmpty()) {
                        Toast.makeText(activity, "Önce gün seç", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }
                    AlertDialog.Builder(activity)
                        .setTitle("Hangi gün?")
                        .setItems(choices.map { TasbihPlanLogic.dayLong(it) }.toTypedArray()) { _, which ->
                            val d = choices[which]
                            val base = TasbihPlanLogic.parseTarget(targetInput.text.toString()) ?: 1000
                            val current = exceptions[d] ?: TasbihPlanDay(base, baseMinute)
                            exceptionDialog(d, current) { exceptions[d] = it; paintExceptions() }
                        }
                        .setNegativeButton("Vazgeç", null)
                        .show()
                }
            })
        }
        paintExceptions()

        val reminderSwitch = SwitchCompat(activity).apply {
            text = "🔔 Hatırlatma"
            isChecked = reminder
            setTextColor(AppColors.DIALOG_TEXT)
            setOnCheckedChangeListener { _, on -> reminder = on }
        }
        box.addView(reminderSwitch, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(14) })
        val activeSwitch = SwitchCompat(activity).apply {
            text = "Aktif"
            isChecked = active
            setTextColor(AppColors.DIALOG_TEXT)
            setOnCheckedChangeListener { _, on -> active = on }
        }
        box.addView(activeSwitch, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(6) })

        val builder = AlertDialog.Builder(activity)
            .setTitle(if (existing == null) "Plan öğesi" else "Plan öğesini düzenle")
            .setView(ScrollView(activity).apply { addView(box) })
            .setPositiveButton("Kaydet", null)
            .setNegativeButton("Vazgeç", null)
        if (existing != null) builder.setNeutralButton("Sil") { _, _ ->
            AlertDialog.Builder(activity)
                .setTitle("Plan öğesi silinsin mi?")
                .setMessage("Geçmiş günlerin kaydı ve tespih sayıları kalır.")
                .setPositiveButton("Sil") { _, _ ->
                    plan.delete(existing.id)
                    TasbihPlanAlarms.schedule(activity)
                    onChanged()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val target = TasbihPlanLogic.parseTarget(targetInput.text.toString())
                when {
                    target == null -> toast("Hedef 1 ile ${TasbihUi.num(Tasbih.MAX_TARGET)} arasında olmalı")
                    days.isEmpty() -> toast("En az bir gün seç")
                    tasbihId.isNotBlank() && tasbihs.none { it.id == tasbihId } -> toast("Tespih seç")
                    else -> {
                        val base = TasbihPlanDay(target, baseMinute)
                        val item = TasbihPlanItem(
                            id = existing?.id ?: TasbihPlanStore.newId(System.currentTimeMillis()),
                            tasbihId = tasbihId,
                            days = days.associateWith { exceptions[it] ?: base },
                            reminder = reminder,
                            active = active,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                        plan.save(item)
                        TasbihPlanAlarms.schedule(activity)
                        if (reminder) askNotificationPermission()
                        dialog.dismiss()
                        onChanged()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun exceptionDialog(day: Int, current: TasbihPlanDay, onDone: (TasbihPlanDay) -> Unit) {
        var minute = current.minute
        val box = TasbihUi.column(activity).apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Hedef adet"
            setText(current.target.toString())
            setSingleLine(true)
        }
        box.addView(input)
        val time = TextView(activity).apply {
            textSize = 15f
            setTextColor(AppColors.DIALOG_TEXT)
            setPadding(dp(4), dp(12), dp(4), dp(8))
        }
        fun paint() { time.text = "⏰ " + (if (minute < 0) "Saat yok" else TasbihPlanLogic.hhmm(minute)) + "  ›" }
        time.setOnClickListener { pickTime(minute) { minute = it; paint() } }
        paint()
        box.addView(time)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(TasbihPlanLogic.dayLong(day))
            .setView(box)
            .setPositiveButton("Tamam", null)
            .setNegativeButton("Vazgeç", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val target = TasbihPlanLogic.parseTarget(input.text.toString())
                if (target == null) {
                    toast("Hedef 1 ile ${TasbihUi.num(Tasbih.MAX_TARGET)} arasında olmalı")
                } else {
                    onDone(TasbihPlanDay(target, minute))
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    /** Saat secici; "Saat yok" -1 verir. */
    private fun pickTime(current: Int, onPick: (Int) -> Unit) {
        val start = if (current >= 0) current else 20 * 60
        val picker = TimePickerDialog(activity, { _, h, m -> onPick(h * 60 + m) }, start / 60, start % 60, true)
        picker.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Saat yok") { _, _ -> onPick(-1) }
        picker.show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        if (activity.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(perm), REQUEST_NOTIFICATIONS)
        }
    }

    // --------------------------------------------------------- parcalar

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    private fun pill(label: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        textSize = 13.5f
        setTextColor(AppColors.ON_ACCENT)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = TasbihUi.card(activity, stroke = AppColors.ACCENT, fill = AppColors.ACCENT, radiusDp = 18)
        setPadding(dp(14), dp(7), dp(14), dp(7))
        setOnClickListener { onClick() }
    }

    private fun bigButton(label: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        textSize = 15.5f
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(AppColors.ON_ACCENT)
        background = TasbihUi.card(activity, stroke = AppColors.ACCENT_LIGHT, fill = AppColors.ACCENT, radiusDp = 16)
        setPadding(dp(12), dp(14), dp(12), dp(14))
        setOnClickListener { onClick() }
    }

    private fun settingRow(icon: String, title: String, value: String, note: String, onClick: () -> Unit): View {
        val row = TasbihUi.row(activity).apply {
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(TasbihUi.text(activity, icon, 17f), TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { marginEnd = dp(10) })
        val texts = TasbihUi.column(activity)
        texts.addView(TasbihUi.text(activity, title, 15f))
        if (note.isNotBlank()) texts.addView(TasbihUi.text(activity, note, 12.5f, AppColors.TEXT_MUTED))
        row.addView(texts, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        if (value.isNotBlank()) row.addView(TasbihUi.text(activity, value, 14f, AppColors.ACCENT, bold = true))
        row.addView(TasbihUi.text(activity, "  ›", 18f, AppColors.TEXT_MUTED))
        return row
    }

    companion object {
        const val REQUEST_NOTIFICATIONS = 7690
    }
}
