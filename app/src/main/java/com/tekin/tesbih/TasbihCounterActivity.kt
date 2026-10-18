package com.tekin.tesbih

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Sayma ekrani: tek tesbih, kaldigi sayidan.
 *
 * Ortada tesbihin kendi materyalinde 33'luk tespih. Sayma uc yoldan:
 * tespih uzerinde parmagi asagi kaydirmak (siradaki boncuk parmakla
 * birlikte iner), tespihe dokunmak ya da alttaki +1. Her sayim hemen
 * kaydediliyor. Hedefte "Dur" secili ise sayma durur ve Tamamlandı
 * gorunur; "Yeni tur" ise sayac sifirlanip tur artar.
 */
class TasbihCounterActivity : AppCompatActivity() {

    private lateinit var store: TasbihStore
    private lateinit var feedback: TasbihFeedback
    private lateinit var tasbihId: String
    private lateinit var t: Tasbih

    private lateinit var titleView: TextView
    private lateinit var roundValue: TextView
    private lateinit var roundSub: TextView
    private lateinit var totalValue: TextView
    private lateinit var rosary: TasbihRosaryView
    private lateinit var hint: TextView
    private lateinit var soundValue: TextView
    private lateinit var vibValue: TextView
    private lateinit var plusButton: LinearLayout
    private lateinit var flash: View
    private lateinit var timeView: TextView
    private lateinit var planView: TextView

    /** Bugun bu tespihin plan kalemleri; ekran acilinca okunur. */
    private var planEntries: List<TasbihPlanEntry> = emptyList()

    /** Sure satirini saniyede bir tazeler; yalnizca ekran onundeyken. */
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            paintTime()
            ticker.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TasbihStore(this)
        feedback = TasbihFeedback(this)
        tasbihId = intent.getStringExtra(TasbihActions.EXTRA_ID).orEmpty()
        val loaded = store.get(tasbihId)
        if (loaded == null) {
            Toast.makeText(this, "Tesbih bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        t = loaded
        store.setLastId(tasbihId)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (!::t.isInitialized) return
        // Ayarlardan donuste guncel hali; silindiyse kapan.
        val fresh = store.get(tasbihId)
        if (fresh == null) { finish(); return }
        t = fresh
        rosary.pullDp = store.getPullDp()
        feedback.tickMs = store.getTickMs()
        feedback.pulseMs = store.getPulseMs()
        if (store.isKeepScreenOn()) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        planEntries = runCatching { TasbihPlanReader(this).todayFor(tasbihId).map { it.entry } }.getOrDefault(emptyList())
        paint()
        ticker.removeCallbacks(tick)
        ticker.post(tick)

        // Izin ekranindan donuste kuculmeye devam. Balon acikken sayma
        // ekranina donulse de balon KAPANMIYOR: kullanici uygulamaya sik
        // girip cikiyor; balon yalnizca X'e surukleyince ya da bildirimdeki
        // "Kapat" ile kapanir. Sayac tek (TasbihStore), ikisi senkron.
        if (pendingMinimize) {
            pendingMinimize = false
            minimizeNow()
        }
    }

    // ------------------------------------------------------ kucuk ekran

    private var pendingMinimize = false

    /**
     * Tespihi yuzen balona kucultur. Balon icin "diger uygulamalarin
     * uzerinde goster" izni gerekiyor; verilmezse yalnizca bildirimle
     * sayma surer.
     */
    private fun minimize() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            minimizeNow()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Küçük ekranda devam")
            .setMessage("Tespih balonunun başka uygulamaların üzerinde durabilmesi için " +
                "\"Diğer uygulamaların üzerinde göster\" izni gerekiyor. İzin vermezsen " +
                "bildirimdeki +1 ile saymaya devam edebilirsin.")
            .setPositiveButton("İzin ver") { _, _ ->
                pendingMinimize = true
                val opened = runCatching {
                    startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                }.isSuccess
                if (!opened) { pendingMinimize = false; minimizeNow() }
            }
            .setNegativeButton("Sadece bildirim") { _, _ -> minimizeNow() }
            .setNeutralButton("Vazgeç", null)
            .show()
    }

    private fun minimizeNow() {
        TasbihFloatService.start(this, tasbihId)
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Balon izni yok: bildirimdeki +1 ile sayabilirsin", Toast.LENGTH_LONG).show()
        }
        moveTaskToBack(true)
    }

    override fun onPause() {
        ticker.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tick)
        if (::feedback.isInitialized) feedback.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------- sayma

    private fun count() {
        val planWasDone = planProgress()?.let { it.first >= it.second } ?: true
        val result = store.tap(tasbihId) ?: return
        t = result.tasbih
        // Bugunun plani bu cekisle tamamlandiysa: sure ve hiz.
        planProgress()?.let { (done, target) ->
            if (!planWasDone && done >= target) {
                feedback.finish(t.finishVibe)
                Toast.makeText(this, "✅ Bugünün planı tamamlandı · " + planDoneLine(done), Toast.LENGTH_LONG).show()
            }
        }
        when (result.event) {
            TasbihEvent.BLOCKED -> {
                // Yarim cekilmis boncuk yerine geri dönsün.
                rosary.setPulled(t.beadsPulled, animate = true)
                feedback.blocked()
                Toast.makeText(this, "Hedefe ulaşıldı. Yeniden başlamak için Sıfırla.", Toast.LENGTH_SHORT).show()
                return
            }
            TasbihEvent.COMPLETED -> {
                feedback.finish(t.finishVibe)
                if (t.sound) feedback.click(finish = true)
                flash()
                Toast.makeText(this, if (t.onTarget == TasbihOnTarget.STOP) "Tamamlandı ✓" else "Tur tamamlandı ✓", Toast.LENGTH_SHORT).show()
            }
            TasbihEvent.MILESTONE -> {
                feedback.tick(strong = t.vibrateEvery > 1)
                if (t.sound) feedback.click()
            }
            TasbihEvent.COUNTED -> {
                if (t.sound) feedback.click()
            }
        }
        paint(animate = true)
    }

    private fun undo() {
        val undone = store.undo(tasbihId)
        if (undone == null) {
            Toast.makeText(this, "Geri alınacak bir şey yok", Toast.LENGTH_SHORT).show()
            return
        }
        t = undone
        feedback.tick(strong = false)
        paint(animate = true)
    }

    // ------------------------------------------------------------- arayuz

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(AppColors.BG_APP) }
        val col = TasbihUi.column(this).apply { setPadding(dp(16), dp(10), dp(16), dp(16)) }
        root.addView(col, FrameLayout.LayoutParams(TasbihUi.MATCH, TasbihUi.MATCH))

        // Baslik: geri, ad, duzenle, menu
        val head = TasbihUi.row(this)
        head.addView(headButton("‹", 30f) { finish() })
        titleView = TasbihUi.text(this, "", 20f, bold = true, maxLines = 1).apply { gravity = Gravity.CENTER }
        head.addView(titleView, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        head.addView(headButton("⧉", 20f) { minimize() }.apply { contentDescription = "Küçült" })
        head.addView(headButton("✎", 20f) { TasbihActions.openSettings(this, tasbihId) })
        head.addView(headButton("⋮", 22f) { menu() })
        col.addView(head, TasbihUi.lp(TasbihUi.MATCH, dp(48)))

        // Tur ve toplam
        val stats = TasbihUi.row(this)
        val roundCard = statCard("TUR")
        roundValue = roundCard.second; roundSub = roundCard.third
        stats.addView(roundCard.first, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        val totalCard = statCard("TOPLAM")
        totalValue = totalCard.second
        totalCard.third.text = "adet"
        stats.addView(totalCard.first, TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { marginStart = dp(12) })
        col.addView(stats, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })

        // Sure: bu tur + tamamlanan turlarin ozeti.
        timeView = TasbihUi.text(this, "", 13f, AppColors.TEXT_SECONDARY, maxLines = 2).apply { gravity = Gravity.CENTER }
        col.addView(timeView, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })
        // Bugunun plani (bu tespih planda varsa).
        planView = TasbihUi.text(this, "", 13f, AppColors.ACCENT, bold = true, maxLines = 1).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        col.addView(planView, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(2) })

        // Tespih: kare alan, ekrana sigacak kadar buyuk.
        val stage = FrameLayout(this)
        rosary = TasbihRosaryView(this).apply {
            contentDescription = "Tespih. Aşağı kaydır ya da dokun: bir boncuk say"
            onPull = { count() }
        }
        stage.addView(rosary, FrameLayout.LayoutParams(0, 0, Gravity.CENTER))
        col.addView(stage, TasbihUi.lp(TasbihUi.MATCH, 0, 1f))
        sizeRosary(stage)

        hint = TasbihUi.text(this, "Aşağı kaydır: bir boncuk çek  ·  dokunmak da sayar", 12.5f, AppColors.TEXT_MUTED).apply {
            gravity = Gravity.CENTER
        }
        col.addView(hint, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { bottomMargin = dp(8) })

        // Yuvarlak dugmeler: geri al, sifirla, titresim
        val round = TasbihUi.row(this, Gravity.CENTER)
        round.addView(roundButton("↶", "Geri al") { undo() })
        round.addView(roundButton("⟲", "Sıfırla") {
            TasbihActions.confirmReset(this, store, t) { reload() }
        }, TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { marginStart = dp(18); marginEnd = dp(18) })
        // Titresim alttaki kartta; burada diger islemler (menu).
        round.addView(roundButton("⋮", "Diğer") { menu() })
        col.addView(round, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP))

        // Ses ve titresim ozetleri
        val quick = TasbihUi.row(this)
        val sound = quickCard("🔊", "Ses") {
            t = t.copy(sound = !t.sound); store.save(t); paint()
            if (t.sound) feedback.click()
        }
        soundValue = sound.second
        quick.addView(sound.first, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        val vib = quickCard("📳", "Titreşim") { pickVibration() }
        vibValue = vib.second
        quick.addView(vib.first, TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { marginStart = dp(12) })
        col.addView(quick, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(14) })

        // +1
        plusButton = TasbihUi.column(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { count() }
            setOnLongClickListener { menu(); true }
        }
        plusButton.addView(TasbihUi.text(this, "+1", 24f, AppColors.ON_ACCENT, bold = true).apply { gravity = Gravity.CENTER })
        plusButton.addView(TasbihUi.text(this, "Dokun, say  ·  Uzun bas: seçenekler", 12.5f, AppColors.ON_ACCENT).apply { gravity = Gravity.CENTER })
        col.addView(plusButton, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(14) })

        flash = View(this).apply { alpha = 0f; isClickable = false; isFocusable = false }
        root.addView(flash, FrameLayout.LayoutParams(TasbihUi.MATCH, TasbihUi.MATCH))

        setContentView(root)
    }

    /** Tespih alani kare; kisa kenar kadar, en fazla 440dp. */
    private fun sizeRosary(stage: FrameLayout) {
        stage.addOnLayoutChangeListener { _, l, tp, r, b, _, _, _, _ ->
            val size = minOf(r - l, b - tp, dp(440))
            if (size <= 0 || rosary.layoutParams.width == size) return@addOnLayoutChangeListener
            rosary.layoutParams = (rosary.layoutParams as FrameLayout.LayoutParams).apply { width = size; height = size }
            stage.post { stage.requestLayout() }
        }
    }

    private fun paint(animate: Boolean = false) {
        val color = t.accent
        titleView.text = t.name
        roundValue.text = TasbihUi.num(t.rounds)
        roundSub.text = "${TasbihUi.num(t.count)} / ${TasbihUi.num(t.target)}"
        totalValue.text = TasbihUi.num(t.total)

        rosary.material = t.material
        rosary.progress = t.progress
        rosary.countText = TasbihUi.num(t.count)
        rosary.targetText = "/ " + TasbihUi.num(t.target)
        if (t.isDone) {
            rosary.nameText = "Tamamlandı ✓"
            rosary.nameColor = AppColors.SUCCESS
        } else {
            rosary.nameText = t.name
            rosary.nameColor = color
        }
        rosary.setPulled(t.beadsPulled, animate)
        hint.visibility = if (t.total < 5) View.VISIBLE else View.GONE

        soundValue.text = if (t.sound) "Açık" else "Kapalı"
        vibValue.text = Tasbih.everyLabel(t.vibrateEvery)
        plusButton.background = TasbihUi.card(this, stroke = TasbihUi.mix(color, android.graphics.Color.WHITE, 0.7f), fill = color, radiusDp = 18)
        flash.setBackgroundColor(color)
        paintTime()
    }

    /**
     * "⏱ 1:48 · 33 / 99" ve altinda tamamlanan turlar. Sure son dokunustan
     * beri akar; bekleme sinirini gecince duraklar (bkz. TasbihTime).
     */
    private fun paintTime() {
        if (!::timeView.isInitialized) return
        val now = System.currentTimeMillis()
        val idle = store.getIdleMs()
        val live = TasbihTime.liveRoundMs(t, now, idle)
        val paused = t.lastTapAt > 0 && TasbihTime.gap(t.lastTapAt, now, idle) == 0L && now - t.lastTapAt > idle
        val first = when {
            t.isDone && t.lastRoundMs > 0 ->
                "⏱ Tur " + TasbihTime.format(t.lastRoundMs) + " · adet başına " + TasbihTime.perBead(t.lastRoundMs, t.target)
            live <= 0 && t.count == 0 && t.intervals.isEmpty() -> "⏱ İlk çekişte süre başlar"
            else -> "⏱ " + TasbihTime.format(live) + (if (paused) " · duraklatıldı" else "") + etaPart()
        }
        val summary = TasbihTime.summary(t)
        timeView.text = if (summary.isBlank()) first else first + "\n" + summary
        paintPlan()
    }

    /** " · ≈ 3 dk kaldı · ~34/dk" ya da hiz olculmediyse kisa not. */
    private fun etaPart(): String {
        if (t.isDone) return ""
        val remaining = t.target - t.count
        val eta = TasbihTime.etaMs(t.intervals, remaining)
            ?: return " · tahmin ${TasbihTime.SPEED_MIN} çekişten sonra"
        return " · " + TasbihTime.approx(eta) + " kaldı · ~" + TasbihTime.perMinute(t.intervals) + "/dk"
    }

    /** (bugun cekilen, bugunun plan hedefi) bu tespih icin; planda yoksa null. */
    private fun planProgress(): Pair<Int, Int>? {
        if (planEntries.isEmpty()) return null
        val progress = TasbihPlanLogic.progress(planEntries, mapOf(tasbihId to store.todayOf(tasbihId)), 0)
        return progress.sumOf { it.done } to progress.sumOf { it.entry.target }
    }

    /** "Süre 14:32 · ~69/dk" (bugun bu tespihte sayarak gecen sure). */
    private fun planDoneLine(done: Int): String {
        val ms = store.timeOf(tasbihId, TasbihStore.dayKey(System.currentTimeMillis()))
        if (ms <= 0) return TasbihUi.num(done) + " adet"
        val rate = (done * 60_000L / ms).coerceAtLeast(1)
        return "Süre " + TasbihTime.format(ms) + " · ~" + rate + "/dk"
    }

    private fun paintPlan() {
        if (!::planView.isInitialized) return
        val (done, target) = planProgress() ?: run { planView.visibility = View.GONE; return }
        planView.visibility = View.VISIBLE
        if (done >= target) {
            planView.setTextColor(AppColors.SUCCESS)
            planView.text = "✅ Bugünün planı tamamlandı · " + planDoneLine(done)
        } else {
            planView.setTextColor(AppColors.ACCENT)
            val eta = TasbihTime.etaMs(t.intervals, target - done)?.let { " · " + TasbihTime.approx(it) } ?: ""
            planView.text = "📅 Bugünün planı: ${TasbihUi.num(done)} / ${TasbihUi.num(target)}$eta"
        }
    }

    private fun flash() {
        flash.animate().cancel()
        flash.alpha = 0.3f
        flash.animate().alpha(0f).setDuration(550L).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun pickVibration() {
        TasbihActions.pickEvery(this, t.vibrateEvery) { every ->
            t = t.copy(vibrateEvery = every); store.save(t); paint()
            Toast.makeText(this, Tasbih.everyLabel(every), Toast.LENGTH_SHORT).show()
        }
    }

    private fun menu() {
        TasbihActions.showMenu(this, store, t, onChanged = { reload() }, onDeleted = { finish() })
    }

    private fun reload() {
        store.get(tasbihId)?.let { t = it; paint() } ?: finish()
    }

    // ------------------------------------------------------- parcalar

    private fun headButton(label: String, size: Float, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(AppColors.TEXT_PRIMARY)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    /** (kart, buyuk deger, alt satir) */
    private fun statCard(label: String): Triple<LinearLayout, TextView, TextView> {
        val card = TasbihUi.column(this).apply {
            background = TasbihUi.card(this@TasbihCounterActivity)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        card.addView(TasbihUi.text(this, label, 11.5f, AppColors.TEXT_MUTED, bold = true))
        val value = TasbihUi.text(this, "0", 24f, bold = true, maxLines = 1)
        card.addView(value)
        val sub = TasbihUi.text(this, "", 13f, AppColors.TEXT_SECONDARY, maxLines = 1)
        card.addView(sub)
        return Triple(card, value, sub)
    }

    private fun roundButton(icon: String, label: String, onClick: () -> Unit): View {
        val size = dp(78)
        val box = TasbihUi.column(this).apply {
            gravity = Gravity.CENTER
            background = TasbihUi.oval(AppColors.BG_CARD, AppColors.STROKE, dp(1))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        box.addView(TasbihUi.text(this, icon, 20f).apply { gravity = Gravity.CENTER })
        box.addView(TasbihUi.text(this, label, 12f, AppColors.TEXT_SECONDARY).apply { gravity = Gravity.CENTER })
        return box
    }

    /** (kart, deger metni) */
    private fun quickCard(icon: String, label: String, onClick: () -> Unit): Pair<View, TextView> {
        val card = TasbihUi.row(this).apply {
            background = TasbihUi.card(this@TasbihCounterActivity)
            setPadding(dp(12), dp(10), dp(8), dp(10))
            isClickable = true
            setOnClickListener { onClick() }
        }
        card.addView(TasbihUi.text(this, icon, 18f), TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { marginEnd = dp(10) })
        val texts = TasbihUi.column(this)
        texts.addView(TasbihUi.text(this, label, 14f, bold = true))
        val value = TasbihUi.text(this, "", 12.5f, AppColors.TEXT_SECONDARY, maxLines = 1)
        texts.addView(value)
        card.addView(texts, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        card.addView(TasbihUi.text(this, "›", 20f, AppColors.TEXT_MUTED))
        return card to value
    }

    private fun dp(value: Int) = TasbihUi.dp(this, value)
}
