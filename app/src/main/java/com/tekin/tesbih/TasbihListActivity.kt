package com.tekin.tesbih

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tesbihlerim: tesbih modulunun giris ekrani.
 *
 * Alttaki uc sekme: Tesbihlerim (kartlar), Geçmiş (gunluk toplamlar) ve
 * Ayarlar (modulun genel ayarlari). Karta dokununca sayma ekrani kaldigi
 * yerden acilir; uzun basinca ya da ⋮ ile islemler.
 *
 * Ekran tamamen koddan kuruluyor ve uygulamanin baska ekranlarina
 * dayanmiyor; yalnizca AppColors paletini kullaniyor.
 */
class TasbihListActivity : AppCompatActivity() {

    private lateinit var store: TasbihStore
    /** Yalnizca Ayarlar'daki "Dene" dugmeleri icin. */
    private val feedback by lazy { TasbihFeedback(this) }
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var nav: LinearLayout
    private var tab = TAB_LIST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TasbihStore(this)
        tab = savedInstanceState?.getInt(STATE_TAB, TAB_LIST) ?: TAB_LIST

        val root = TasbihUi.column(this).apply { setBackgroundColor(AppColors.BG_APP) }
        scroll = ScrollView(this).apply { isFillViewport = true }
        body = TasbihUi.column(this).apply { setPadding(dp(18), dp(20), dp(18), dp(24)) }
        scroll.addView(body)
        root.addView(scroll, TasbihUi.lp(TasbihUi.MATCH, 0, 1f))

        nav = TasbihUi.row(this, Gravity.CENTER).apply {
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = TasbihUi.card(this@TasbihListActivity, radiusDp = 0)
        }
        root.addView(nav, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP))
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, tab)
    }

    // Sayma ve ayar ekranindan donuste sayilar tazelensin.
    override fun onResume() {
        super.onResume()
        render()
        // Plan hatirlatmasi: plan ya da saat degismis olabilir.
        TasbihPlanAlarms.schedule(this)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (tab != TAB_LIST) switchTab(TAB_LIST) else super.onBackPressed()
    }

    private fun switchTab(index: Int) {
        tab = index
        render()
        scroll.scrollTo(0, 0)
    }

    private fun render() {
        body.removeAllViews()
        when (tab) {
            TAB_PLAN -> TasbihPlanScreen(this) { render() }.render(body)
            TAB_HISTORY -> renderHistory()
            TAB_SETTINGS -> renderSettings()
            else -> renderList()
        }
        renderNav()
    }

    // ------------------------------------------------------ Tesbihlerim

    private fun renderList() {
        val list = store.getAll()
        val lastId = store.getLastId()

        // Tek baslik; ayarlar alt sekmede (ayrica ⚙ yok).
        val head = TasbihUi.row(this)
        head.addView(TasbihUi.text(this, "Tesbihlerim", 26f, bold = true), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        head.addView(pill("＋  Tesbih Ekle") { TasbihActions.openSettings(this, null) })
        body.addView(head)

        body.addView(todayCard(), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(18); bottomMargin = dp(6) })

        if (list.isEmpty()) {
            body.addView(emptyState())
            return
        }
        list.forEach { t ->
            body.addView(tasbihCard(t, highlighted = t.id == lastId),
                TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(10) })
        }
    }

    private fun todayCard(): View {
        val card = TasbihUi.row(this).apply {
            background = TasbihUi.card(this@TasbihListActivity)
            setPadding(dp(16), dp(14), dp(14), dp(14))
            isClickable = true
            setOnClickListener { switchTab(TAB_HISTORY) }
        }
        card.addView(barsIcon(AppColors.ACCENT), TasbihUi.lp(dp(34), dp(30)).apply { marginEnd = dp(16) })
        val col = TasbihUi.column(this)
        col.addView(TasbihUi.text(this, "Bugün Toplam", 13.5f, AppColors.TEXT_SECONDARY))
        val value = TasbihUi.row(this, Gravity.BOTTOM)
        value.addView(TasbihUi.text(this, TasbihUi.num(store.todayTotal()), 30f, AppColors.ACCENT, bold = true))
        value.addView(TasbihUi.text(this, "  adet", 14f, AppColors.TEXT_SECONDARY).apply { setPadding(0, 0, 0, dp(5)) })
        col.addView(value)
        // Plan varsa bugunun plani ayni kartta (ayri kart eklenmiyor).
        val plan = runCatching { TasbihPlanReader(this).today() }.getOrDefault(emptyList())
        if (plan.isNotEmpty()) {
            val done = plan.count { it.complete }
            val sumDone = plan.sumOf { it.done.toLong() }
            val sumTarget = plan.sumOf { it.entry.target.toLong() }
            col.addView(TasbihUi.text(this,
                "📅 Plan: ${TasbihUi.num(sumDone)} / ${TasbihUi.num(sumTarget)}  ·  $done / ${plan.size} tamam  ›",
                13f, if (done == plan.size) AppColors.SUCCESS else AppColors.ACCENT, maxLines = 1).apply {
                setPadding(0, dp(4), 0, dp(2))
                isClickable = true
                setOnClickListener { switchTab(TAB_PLAN) }
            })
        }
        card.addView(col, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        card.addView(chevron())
        return card
    }

    private fun tasbihCard(t: Tasbih, highlighted: Boolean): View {
        val color = t.accent
        val card = TasbihUi.row(this).apply {
            background = TasbihUi.card(
                this@TasbihListActivity,
                stroke = if (highlighted) color else AppColors.STROKE,
                fill = if (highlighted) TasbihUi.mix(color, AppColors.BG_CARD, 0.07f) else AppColors.BG_CARD
            )
            setPadding(dp(12), dp(12), dp(6), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { TasbihActions.openCounter(this@TasbihListActivity, t.id) }
            setOnLongClickListener { menu(t); true }
        }

        card.addView(TasbihBeadsView(this, t.material), TasbihUi.lp(dp(64), dp(72)).apply { marginEnd = dp(12) })

        val col = TasbihUi.column(this)
        col.addView(TasbihUi.text(this, t.name, 16.5f, bold = true, maxLines = 1))
        val count = TasbihUi.row(this, Gravity.BOTTOM)
        count.addView(TasbihUi.text(this, TasbihUi.num(t.count), 17f, color, bold = true))
        count.addView(TasbihUi.text(this, " / " + TasbihUi.num(t.target), 15f, AppColors.TEXT_SECONDARY))
        if (t.rounds > 0 && t.onTarget == TasbihOnTarget.NEW_ROUND) {
            count.addView(TasbihUi.text(this, "   · ${TasbihUi.num(t.rounds)} tur", 13f, AppColors.TEXT_MUTED))
        }
        col.addView(count, TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(2) })
        col.addView(TasbihBarView(this).apply { this.color = color; progress = t.progress },
            TasbihUi.lp(TasbihUi.MATCH, dp(6)).apply { topMargin = dp(8); marginEnd = dp(8) })
        val (status, statusColor) = statusOf(t, highlighted)
        col.addView(TasbihUi.text(this, status, 12.5f, statusColor, maxLines = 1),
            TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(6) })
        TasbihTime.summary(t).takeIf { it.isNotBlank() }?.let { time ->
            col.addView(TasbihUi.text(this, "⏱ $time", 12.5f, AppColors.TEXT_MUTED, maxLines = 1),
                TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(2) })
        }
        card.addView(col, TasbihUi.lp(0, TasbihUi.WRAP, 1f))

        val side = TasbihUi.column(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        side.addView(iconButton("⋮", sizeDp = 34) { menu(t) })
        side.addView(chevron(), TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(6) })
        card.addView(side)
        return card
    }

    private fun statusOf(t: Tasbih, highlighted: Boolean): Pair<String, Int> {
        val now = System.currentTimeMillis()
        return when {
            t.isDone -> "Tamamlandı ✓" to AppColors.SUCCESS
            highlighted && (t.count > 0 || t.rounds > 0) -> "Kaldığın yerden devam et" to AppColors.TEXT_SECONDARY
            t.lastUsedAt == 0L -> "Henüz başlanmadı" to AppColors.TEXT_MUTED
            TasbihStore.dayKey(t.lastUsedAt) == TasbihStore.dayKey(now) ->
                "Son kullanım: " + SimpleDateFormat("HH:mm", Locale.forLanguageTag("tr")).format(Date(t.lastUsedAt)) to AppColors.TEXT_MUTED
            else -> "Bugün başlanmadı · son: " + TasbihStore.dayLabel(TasbihStore.dayKey(t.lastUsedAt), now) to AppColors.TEXT_MUTED
        }
    }

    private fun menu(t: Tasbih) {
        TasbihActions.showMenu(this, store, t, onChanged = { render() }, onDeleted = { render() })
    }

    /** Ilk acilista: bos liste ve hizli ekleme onerileri. */
    private fun emptyState(): View {
        val box = TasbihUi.column(this).apply {
            background = TasbihUi.card(this@TasbihListActivity)
            setPadding(dp(18), dp(20), dp(18), dp(18))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        box.addView(TasbihBeadsView(this, TasbihMaterial.KEHRIBAR), TasbihUi.lp(dp(84), dp(92)))
        box.addView(TasbihUi.text(this, "Henüz tesbihin yok", 16f, bold = true).apply { gravity = Gravity.CENTER },
            TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(10) })
        box.addView(TasbihUi.text(this, "“Tesbih Ekle” ile kendi tesbihini oluştur ya da aşağıdan hızlıca başla.", 13.5f, AppColors.TEXT_SECONDARY).apply { gravity = Gravity.CENTER },
            TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(4); bottomMargin = dp(14) })

        QUICK.forEachIndexed { index, (name, target) ->
            box.addView(TextView(this).apply {
                text = "＋  $name  ·  $target"
                textSize = 14f
                setTextColor(AppColors.TEXT_PRIMARY)
                gravity = Gravity.CENTER
                background = TasbihUi.card(this@TasbihListActivity, fill = AppColors.BG_FIELD, radiusDp = 12)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener {
                    val now = System.currentTimeMillis()
                    store.save(Tasbih(id = TasbihStore.newId(now), name = name, target = target,
                        material = TasbihMaterial.forIndex(index), vibrateEvery = if (target <= 100) target else 33,
                        createdAt = now))
                    render()
                }
            }, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })
        }
        return box
    }

    // ----------------------------------------------------------- Geçmiş

    private fun renderHistory() {
        body.addView(TasbihUi.text(this, "Geçmiş", 26f, bold = true))
        body.addView(TasbihUi.text(this, "Tüm tesbihlerin günlük toplamı. Tesbih silinse de burada kalır.", 13.5f, AppColors.TEXT_SECONDARY),
            TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(4) })

        val days = store.dailyTotals()
        val today = store.todayTotal()
        val todayKey = TasbihStore.dayKey(System.currentTimeMillis())
        val weekKeys = (0 until 7).map { TasbihStore.dayKey(System.currentTimeMillis() - it * DAY_MS) }.toSet()
        val week = days.filter { it.first in weekKeys }.sumOf { it.second.toLong() }
        val all = days.sumOf { it.second.toLong() }

        val stats = TasbihUi.row(this)
        stats.addView(statCard("Bugün", TasbihUi.num(today)), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        stats.addView(statCard("Son 7 gün", TasbihUi.num(week)), TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { marginStart = dp(10) })
        stats.addView(statCard("Kayıtlı", TasbihUi.num(all)), TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { marginStart = dp(10) })
        body.addView(stats, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(18) })

        // Bugun tesbihlere gore
        val perTasbih = store.getAll().map { it to store.todayOf(it.id) }.filter { it.second > 0 }
        if (perTasbih.isNotEmpty()) {
            body.addView(sectionTitle("Bugün tesbihlere göre"))
            val card = groupCard()
            perTasbih.sortedByDescending { it.second }.forEachIndexed { i, (t, count) ->
                card.addView(barRow(t.name, count, perTasbih.maxOf { it.second }, t.accent, divider = i > 0))
            }
            body.addView(card)
        }

        body.addView(sectionTitle("Günler"))
        if (days.isEmpty()) {
            body.addView(TasbihUi.text(this, "Henüz kayıt yok. Saydıkça günler burada birikir.", 14f, AppColors.TEXT_MUTED))
            return
        }
        val card = groupCard()
        val shown = days.take(60)
        val max = shown.maxOf { it.second }
        shown.forEachIndexed { i, (day, count) ->
            card.addView(barRow(TasbihStore.dayLabel(day), count, max,
                if (day == todayKey) AppColors.ACCENT else AppColors.TEXT_MUTED, divider = i > 0))
        }
        body.addView(card)
        if (days.size > shown.size) {
            body.addView(TasbihUi.text(this, "Son ${shown.size} gün gösteriliyor; en fazla ${TasbihStore.MAX_DAYS} gün saklanır.", 12.5f, AppColors.TEXT_MUTED),
                TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })
        }
    }

    private fun barRow(label: String, count: Int, max: Int, color: Int, divider: Boolean): View {
        val col = TasbihUi.column(this)
        if (divider) col.addView(View(this).apply { setBackgroundColor(AppColors.STROKE) }, TasbihUi.lp(TasbihUi.MATCH, 1))
        val row = TasbihUi.row(this).apply { setPadding(0, dp(10), 0, dp(6)) }
        row.addView(TasbihUi.text(this, label, 14f, maxLines = 1), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        row.addView(TasbihUi.text(this, TasbihUi.num(count), 14f, color, bold = true))
        col.addView(row)
        col.addView(TasbihBarView(this).apply { this.color = color; progress = if (max > 0) count.toFloat() / max else 0f },
            TasbihUi.lp(TasbihUi.MATCH, dp(4)).apply { bottomMargin = dp(8) })
        return col
    }

    private fun statCard(label: String, value: String): View {
        val card = TasbihUi.column(this).apply {
            background = TasbihUi.card(this@TasbihListActivity)
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        card.addView(TasbihUi.text(this, label.uppercase(Locale.forLanguageTag("tr")), 11f, AppColors.TEXT_MUTED, bold = true))
        card.addView(TasbihUi.text(this, value, 20f, bold = true, maxLines = 1))
        return card
    }

    // ---------------------------------------------------------- Ayarlar

    private fun renderSettings() {
        body.addView(TasbihUi.text(this, "Ayarlar", 26f, bold = true))
        body.addView(TasbihUi.text(this, "Tesbih modülünün genel ayarları. Her tesbihin kendi ayarı (hedef, titreşim, ses, renk) tesbihin içinde.", 13.5f, AppColors.TEXT_SECONDARY),
            TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(4) })

        body.addView(sectionTitle("Sayma ekranı"))
        val screen = groupCard()
        val row = TasbihUi.row(this).apply { setPadding(0, dp(6), 0, dp(6)) }
        val texts = TasbihUi.column(this)
        texts.addView(TasbihUi.text(this, "Ekran açık kalsın", 15f))
        texts.addView(TasbihUi.text(this, "Sayarken telefon kararıp kilitlenmez.", 12.5f, AppColors.TEXT_MUTED))
        row.addView(texts, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        row.addView(SwitchCompat(this).apply {
            isChecked = store.isKeepScreenOn()
            setOnCheckedChangeListener { _, on -> store.setKeepScreenOn(on) }
        })
        screen.addView(row)
        body.addView(screen)

        body.addView(sectionTitle("Çekme mesafesi"))
        body.addView(pullCard())

        body.addView(sectionTitle("Süre"))
        body.addView(idleCard())

        body.addView(sectionTitle("Titreşim süresi"))
        body.addView(vibrationCard())

        body.addView(sectionTitle("Kayıt"))
        body.addView(TasbihUi.text(this,
            "Sayılar her dokunuşta bu telefona kaydedilir; uygulamadan çıksan ya da telefon kapansa da kaldığın yerden devam edersin. " +
                "Tesbih verisi uygulamanın diğer bölümlerinden ayrı tutulur.",
            13.5f, AppColors.TEXT_SECONDARY))

        body.addView(sectionTitle("Temizle"))
        body.addView(dangerButton("Geçmişi temizle") {
            confirm("Geçmiş temizlensin mi?", "Günlük toplamlar silinir. Tesbihler, sayaçları ve toplamları kalır.") {
                store.clearHistory(); toast("Geçmiş temizlendi"); render()
            }
        })
        body.addView(dangerButton("Tüm tesbihleri ve geçmişi sil") {
            confirm("Her şey silinsin mi?", "Bütün tesbihler, sayaçlar ve geçmiş silinir. Geri alınamaz.") {
                store.deleteEverything(); toast("Tesbih verisi silindi"); switchTab(TAB_LIST)
            }
        }, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(10) })
    }

    /** Surenin kendiliginden duraklamasi icin bekleme siniri. */
    private fun idleCard(): View {
        val card = groupCard().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val head = TasbihUi.row(this)
        head.addView(TasbihUi.text(this, "Çekmezsen süre dursun", 15f), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        val value = TasbihUi.text(this, TasbihTime.idleLabel(store.getIdleMs()) + "  ›", 14f, AppColors.ACCENT, bold = true)
        head.addView(value)
        card.addView(head)
        card.addView(TasbihUi.text(this,
            "Süre ilk çekişte başlar. Bu kadar süre çekmezsen (ara verme, uygulamadan çıkma, ekran kilidi) " +
                "kendiliğinden durur ve kaldığı yerde kaydedilir; bekleme süreye eklenmez. Yeniden çekince devam eder.",
            12.5f, AppColors.TEXT_MUTED), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })
        card.setOnClickListener {
            val options = TasbihTime.IDLE_OPTIONS
            val labels = options.map { TasbihTime.idleLabel(it) }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Çekmezsen süre dursun")
                .setSingleChoiceItems(labels, options.indexOf(store.getIdleMs())) { d, which ->
                    store.setIdleMs(options[which])
                    value.text = labels[which] + "  ›"
                    d.dismiss()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
        return card
    }

    /**
     * Cekme mesafesi kaydiricisi: Kolay (kisa cekis) .. Zor (uzun cekis).
     * Ekranda dp degil ad gorunur. Birakinca kaydedilir.
     */
    private fun pullCard(): View {
        val card = groupCard().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val head = TasbihUi.row(this)
        head.addView(TasbihUi.text(this, "Çekme mesafesi", 15f), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        val value = TasbihUi.text(this, TasbihPull.label(store.getPullDp()), 14f, AppColors.ACCENT, bold = true)
        head.addView(value)
        card.addView(head)

        val seek = android.widget.SeekBar(this).apply {
            max = TasbihPull.MAX_DP - TasbihPull.MIN_DP
            progress = store.getPullDp() - TasbihPull.MIN_DP
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                    value.text = TasbihPull.label(p + TasbihPull.MIN_DP)
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {
                    store.setPullDp(bar.progress + TasbihPull.MIN_DP)
                }
            })
        }
        card.addView(seek, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })

        val ends = TasbihUi.row(this)
        ends.addView(TasbihUi.text(this, "Kolay", 12.5f, AppColors.TEXT_MUTED), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        ends.addView(TasbihUi.text(this, "Zor", 12.5f, AppColors.TEXT_MUTED))
        card.addView(ends, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { setMargins(dp(4), 0, dp(4), 0) })

        card.addView(TasbihUi.text(this, "Boncuğu saymak için ne kadar aşağı çekmek gerektiğini ayarlar. Dokunarak saymayı etkilemez.",
            12.5f, AppColors.TEXT_MUTED), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(8) })

        card.addView(TextView(this).apply {
            text = "Varsayılana dön (Normal)"
            textSize = 13f
            setTextColor(AppColors.TEXT_SECONDARY)
            setPadding(0, dp(10), 0, dp(4))
            setOnClickListener {
                store.setPullDp(TasbihPull.DEFAULT_DP)
                seek.progress = TasbihPull.DEFAULT_DP - TasbihPull.MIN_DP
            }
        })
        return card
    }

    /**
     * Titresim sureleri (ms): her adet ve aralik (her 5/10/33...) ayri.
     * Kaydirici birakilinca kaydedilir ve bir kez titretilir; "Dene" ayni
     * titresimi tekrar calar.
     */
    private fun vibrationCard(): View {
        val card = groupCard().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        msSlider(card, "Her adet titreşimi", store.getTickMs(), TasbihVibe.MIN_TICK_MS, TasbihVibe.MAX_TICK_MS,
            TasbihVibe.DEFAULT_TICK_MS,
            onSave = { store.setTickMs(it) },
            onTry = { feedback.tickMs = it; feedback.tick(strong = false) })
        card.addView(View(this).apply { setBackgroundColor(AppColors.STROKE) },
            TasbihUi.lp(TasbihUi.MATCH, 1).apply { topMargin = dp(12); bottomMargin = dp(12) })
        msSlider(card, "Aralık titreşimi (her 5, 10, 33…)", store.getPulseMs(), TasbihVibe.MIN_PULSE_MS, TasbihVibe.MAX_PULSE_MS,
            TasbihVibe.DEFAULT_PULSE_MS,
            onSave = { store.setPulseMs(it) },
            onTry = { feedback.pulseMs = it; feedback.tick(strong = true) })
        card.addView(TasbihUi.text(this,
            "Süre uzadıkça titreşim daha güçlü hissedilir. Çok kısa süreleri (15 ms altı) bazı telefonların motoru hiç hissettirmez. " +
                "Hangi aralıkta titreyeceğini her tesbihin kendi ayarı belirler.",
            12.5f, AppColors.TEXT_MUTED), TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(10) })
        return card
    }

    private fun msSlider(
        parent: LinearLayout, label: String, current: Int, min: Int, max: Int, default: Int,
        onSave: (Int) -> Unit, onTry: (Int) -> Unit
    ) {
        val head = TasbihUi.row(this)
        head.addView(TasbihUi.text(this, label, 15f), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        val value = TasbihUi.text(this, "$current ms", 14f, AppColors.ACCENT, bold = true)
        head.addView(value)
        parent.addView(head)

        val seek = android.widget.SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                    value.text = "${p + min} ms"
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {
                    val ms = bar.progress + min
                    onSave(ms)
                    onTry(ms)
                }
            })
        }
        parent.addView(seek, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(6) })

        val actions = TasbihUi.row(this)
        actions.addView(TextView(this).apply {
            text = "▶ Dene"
            textSize = 13.5f
            setTextColor(AppColors.TEXT_PRIMARY)
            background = TasbihUi.card(this@TasbihListActivity, fill = AppColors.BG_FIELD, radiusDp = 10)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnClickListener { onTry(seek.progress + min) }
        })
        actions.addView(View(this), TasbihUi.lp(0, 1, 1f))
        actions.addView(TextView(this).apply {
            text = "Varsayılan ($default ms)"
            textSize = 13f
            setTextColor(AppColors.TEXT_SECONDARY)
            setPadding(dp(8), dp(7), 0, dp(7))
            setOnClickListener {
                seek.progress = default - min
                onSave(default)
                onTry(default)
            }
        })
        parent.addView(actions, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(4) })
    }

    override fun onDestroy() {
        runCatching { feedback.release() }
        super.onDestroy()
    }

    // ---------------------------------------------------------- alt menu

    private fun renderNav() {
        nav.removeAllViews()
        listOf(Triple(TAB_LIST, "📿", "Tesbihlerim"), Triple(TAB_PLAN, "📅", "Plan"),
            Triple(TAB_HISTORY, "🕘", "Geçmiş"), Triple(TAB_SETTINGS, "⚙", "Ayarlar"))
            .forEach { (index, icon, label) ->
                val active = index == tab
                val item = TasbihUi.column(this).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    background = if (active) TasbihUi.card(this@TasbihListActivity,
                        stroke = TasbihUi.mix(AppColors.ACCENT, AppColors.BG_CARD, 0.35f),
                        fill = TasbihUi.mix(AppColors.ACCENT, AppColors.BG_CARD, 0.14f), radiusDp = 14) else null
                    isClickable = true
                    setOnClickListener { if (tab != index) switchTab(index) }
                }
                item.addView(TasbihUi.text(this, icon, 18f).apply { gravity = Gravity.CENTER })
                item.addView(TasbihUi.text(this, label, 12f, if (active) AppColors.ACCENT else AppColors.TEXT_MUTED, bold = active)
                    .apply { gravity = Gravity.CENTER })
                nav.addView(item, TasbihUi.lp(0, TasbihUi.WRAP, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            }
    }

    // ------------------------------------------------------- yardimcilar

    private fun sectionTitle(text: String) = TasbihUi.text(this, text, 16f, bold = true).apply {
        layoutParams = TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(22); bottomMargin = dp(8) }
    }

    private fun groupCard() = TasbihUi.column(this).apply {
        background = TasbihUi.card(this@TasbihListActivity)
        setPadding(dp(14), dp(6), dp(14), dp(6))
    }

    private fun iconButton(icon: String, sizeDp: Int = 40, onClick: () -> Unit) = TextView(this).apply {
        text = icon
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(AppColors.TEXT_SECONDARY)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        setOnClickListener { onClick() }
    }

    private fun chevron() = TasbihUi.text(this, "›", 24f, AppColors.TEXT_MUTED).apply {
        setPadding(dp(6), 0, dp(6), dp(2))
    }

    private fun pill(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(AppColors.ON_ACCENT)
        background = TasbihUi.card(this@TasbihListActivity, stroke = AppColors.ACCENT_LIGHT, fill = AppColors.ACCENT, radiusDp = 14)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    private fun dangerButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14.5f
        gravity = Gravity.CENTER
        setTextColor(AppColors.DANGER)
        background = TasbihUi.card(this@TasbihListActivity, stroke = AppColors.DANGER, fill = AppColors.BG_CARD, radiusDp = 14)
        setPadding(dp(12), dp(13), dp(12), dp(13))
        setOnClickListener { onClick() }
    }

    /** Uc cubuklu kucuk istatistik simgesi. */
    private fun barsIcon(color: Int): View {
        val box = TasbihUi.row(this, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        listOf(0.55f, 1f, 0.75f).forEachIndexed { i, h ->
            box.addView(View(this).apply {
                background = TasbihUi.card(this@TasbihListActivity, stroke = color, fill = color, radiusDp = 2)
            }, TasbihUi.lp(dp(7), (dp(28) * h).toInt()).apply { if (i > 0) marginStart = dp(4) })
        }
        return FrameLayout(this).apply { addView(box, FrameLayout.LayoutParams(TasbihUi.MATCH, TasbihUi.MATCH)) }
    }

    private fun confirm(title: String, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Evet") { _, _ -> onYes() }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = TasbihUi.dp(this, value)

    companion object {
        private const val TAB_LIST = 0
        private const val TAB_HISTORY = 1
        private const val TAB_SETTINGS = 2
        private const val TAB_PLAN = 3
        private const val STATE_TAB = "tab"
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** Bos listede hizli ekleme onerileri; eklenince siradan tesbih olurlar. */
        private val QUICK = listOf(
            "SubhanAllah" to 33,
            "Elhamdülillah" to 33,
            "Allahu Ekber" to 33,
            "Salavat" to 100
        )
    }
}
