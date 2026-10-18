package com.tekin.tesbih

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * Tek tesbihin ayarlari; EXTRA_ID yoksa yeni tesbih olusturur.
 *
 * Degisiklikler "Kaydet"e basinca yaziliyor; vazgecip geri donmek hicbir
 * seyi degistirmez.
 */
class TasbihSettingsActivity : AppCompatActivity() {

    private lateinit var store: TasbihStore
    private lateinit var feedback: TasbihFeedback
    private var original: Tasbih? = null
    private lateinit var draft: Tasbih

    private lateinit var body: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var targetInput: EditText
    private lateinit var countInput: EditText

    private lateinit var previewBeads: TasbihBeadsView
    private lateinit var previewName: TextView
    private lateinit var previewCount: TextView
    private lateinit var previewBar: TasbihBarView

    private lateinit var everyValue: TextView
    private lateinit var finishValue: TextView
    private lateinit var onTargetValue: TextView
    private lateinit var materialValue: TextView
    private lateinit var materialBeads: TasbihBeadsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TasbihStore(this)
        feedback = TasbihFeedback(this)
        val id = intent.getStringExtra(TasbihActions.EXTRA_ID)
        original = id?.let { store.get(it) }
        if (id != null && original == null) {
            Toast.makeText(this, "Tesbih bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val now = System.currentTimeMillis()
        draft = original ?: Tasbih(
            id = TasbihStore.newId(now),
            name = "",
            target = 33,
            material = TasbihMaterial.forIndex(store.getAll().size),
            createdAt = now
        )
        buildUi()
        paint()
    }

    override fun onDestroy() {
        if (::feedback.isInitialized) feedback.release()
        super.onDestroy()
    }

    private val isNew: Boolean get() = original == null

    // ---------------------------------------------------------- arayuz

    private fun buildUi() {
        val root = TasbihUi.column(this).apply { setBackgroundColor(AppColors.BG_APP) }

        val head = TasbihUi.row(this).apply { setPadding(dp(8), dp(10), dp(16), dp(6)) }
        head.addView(TextView(this).apply {
            text = "‹"; textSize = 30f; gravity = Gravity.CENTER
            setTextColor(AppColors.TEXT_PRIMARY)
            setOnClickListener { finish() }
        }, TasbihUi.lp(dp(44), dp(44)))
        head.addView(TasbihUi.text(this, if (isNew) "Yeni Tesbih" else "Tesbih Ayarları", 20f, bold = true),
            TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        root.addView(head)

        val scroll = ScrollView(this)
        body = TasbihUi.column(this).apply { setPadding(dp(16), dp(6), dp(16), dp(28)) }
        scroll.addView(body)
        root.addView(scroll, TasbihUi.lp(TasbihUi.MATCH, 0, 1f))

        // Onizleme karti
        val preview = TasbihUi.row(this).apply {
            background = TasbihUi.card(this@TasbihSettingsActivity)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        previewBeads = TasbihBeadsView(this, draft.material)
        preview.addView(previewBeads, TasbihUi.lp(dp(72), dp(80)).apply { marginEnd = dp(14) })
        val pc = TasbihUi.column(this)
        previewName = TasbihUi.text(this, "", 17f, bold = true, maxLines = 1)
        pc.addView(previewName)
        previewCount = TasbihUi.text(this, "", 15f, AppColors.TEXT_SECONDARY)
        pc.addView(previewCount, TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { topMargin = dp(2) })
        previewBar = TasbihBarView(this)
        pc.addView(previewBar, TasbihUi.lp(TasbihUi.MATCH, dp(6)).apply { topMargin = dp(8) })
        preview.addView(pc, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        body.addView(preview)

        // Temel ayarlar
        section("Temel Ayarlar")
        val basic = group()
        nameInput = field(basic, "Tesbih adı", draft.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, "Sabah Tesbihatı")
        targetInput = field(basic, "Hedef sayı (en fazla ${TasbihUi.num(Tasbih.MAX_TARGET)})", draft.target.toString(), InputType.TYPE_CLASS_NUMBER, "33", divider = true)
        countInput = field(basic, if (isNew) "Başlangıç" else "Mevcut sayı", draft.count.toString(), InputType.TYPE_CLASS_NUMBER, "0", divider = true)
        body.addView(basic)
        listOf(nameInput, targetInput, countInput).forEach { it.addTextChangedListener(watcher) }

        // Titresim
        section("Titreşim Ayarları")
        val vib = group()
        everyValue = choiceRow(vib, "📳", "Titreşim") {
            TasbihActions.pickEvery(this, draft.vibrateEvery) { draft = draft.copy(vibrateEvery = it); paint() }
        }
        finishValue = choiceRow(vib, "🏁", "Bitiş titreşimi", divider = true) {
            TasbihActions.pickFinish(this, feedback, draft.finishVibe) { draft = draft.copy(finishVibe = it); paint() }
        }
        body.addView(vib)
        hint("Titreşim: her N adette bir titrer (ör. her 33 adette: 33, 66, 99…). Bitiş titreşimi hedefe ulaşınca gelir.")

        // Ses
        section("Ses Ayarları")
        val sound = group()
        val soundRow = TasbihUi.row(this).apply { setPadding(0, dp(8), 0, dp(8)) }
        soundRow.addView(TasbihUi.text(this, "🔊", 18f), TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { marginEnd = dp(12) })
        soundRow.addView(TasbihUi.text(this, "Her adette ses", 15f), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        soundRow.addView(SwitchCompat(this).apply {
            isChecked = draft.sound
            setOnCheckedChangeListener { _, on ->
                draft = draft.copy(sound = on)
                if (on) feedback.click()
            }
        })
        sound.addView(soundRow)
        body.addView(sound)

        // Sayac davranisi
        section("Sayaç Davranışı")
        val behave = group()
        onTargetValue = choiceRow(behave, "🎯", "Hedefe gelince") {
            val values = TasbihOnTarget.values()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hedefe gelince")
                .setSingleChoiceItems(values.map { it.label }.toTypedArray(), values.indexOf(draft.onTarget)) { d, which ->
                    draft = draft.copy(onTarget = values[which]); paint(); d.dismiss()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
        body.addView(behave)
        hint("Dur: hedefte kalır ve “Tamamlandı ✓” gösterir. Yeni tur: sayaç sıfırlanır, tur bir artar.")

        // Gorunum
        section("Görünüm")
        val look = group()
        val lookRow = TasbihUi.row(this).apply {
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            setOnClickListener {
                TasbihAppearancePicker.show(this@TasbihSettingsActivity, draft.material) { m ->
                    draft = draft.copy(material = m); paint()
                }
            }
        }
        materialBeads = TasbihBeadsView(this, draft.material)
        lookRow.addView(materialBeads, TasbihUi.lp(dp(44), dp(50)).apply { marginEnd = dp(12) })
        val lookTexts = TasbihUi.column(this)
        lookTexts.addView(TasbihUi.text(this, "Tespih Görünümü", 15f))
        materialValue = TasbihUi.text(this, "", 13f, AppColors.ACCENT, maxLines = 1)
        lookTexts.addView(materialValue)
        lookRow.addView(lookTexts, TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        lookRow.addView(TasbihUi.text(this, "  ›", 18f, AppColors.TEXT_MUTED))
        look.addView(lookRow)
        body.addView(look)
        hint("Renk ve materyal yalnızca bu tesbih için geçerli; diğer tesbihler etkilenmez.")

        // Kaydet / sil
        body.addView(TextView(this).apply {
            text = if (isNew) "Tesbihi Oluştur" else "Kaydet"
            textSize = 16f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(AppColors.ON_ACCENT)
            background = TasbihUi.card(this@TasbihSettingsActivity, stroke = AppColors.ACCENT_LIGHT, fill = AppColors.ACCENT, radiusDp = 16)
            setPadding(dp(12), dp(15), dp(12), dp(15))
            setOnClickListener { save() }
        }, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(26) })

        if (!isNew) {
            body.addView(TextView(this).apply {
                text = "🗑  Tesbihi Sil"
                textSize = 15.5f
                gravity = Gravity.CENTER
                setTextColor(AppColors.DANGER)
                background = TasbihUi.card(this@TasbihSettingsActivity, stroke = AppColors.DANGER, fill = AppColors.BG_CARD, radiusDp = 16)
                setPadding(dp(12), dp(14), dp(12), dp(14))
                setOnClickListener {
                    val t = original ?: return@setOnClickListener
                    TasbihActions.confirmDelete(this@TasbihSettingsActivity, store, t) { finish() }
                }
            }, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(12) })
        }

        setContentView(root)
    }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { paintPreview() }
    }

    private fun paint() {
        everyValue.text = Tasbih.everyLabel(draft.vibrateEvery)
        finishValue.text = draft.finishVibe.label
        onTargetValue.text = draft.onTarget.label
        materialBeads.material = draft.material
        materialValue.text = draft.material.title
        paintPreview()
    }

    /** Onizleme yazilan degerlerden; gecersizse eldeki taslak. */
    private fun paintPreview() {
        val color = draft.accent
        val name = nameInput.text.toString().trim().ifBlank { if (isNew) "Yeni tesbih" else draft.name }
        val target = TasbihLogic.parseInt(targetInput.text.toString(), 1, Tasbih.MAX_TARGET) ?: draft.target
        val count = TasbihLogic.parseInt(countInput.text.toString(), 0, Tasbih.MAX_TARGET)?.coerceAtMost(target) ?: 0
        previewBeads.material = draft.material
        previewName.text = name
        previewCount.text = "${TasbihUi.num(count)} / ${TasbihUi.num(target)}"
        previewBar.color = color
        previewBar.progress = count.toFloat() / target
    }

    private fun save() {
        val name = nameInput.text.toString().trim().take(Tasbih.MAX_NAME)
        if (name.isEmpty()) { toast("Tesbih adı boş olamaz"); nameInput.requestFocus(); return }
        val target = TasbihLogic.parseInt(targetInput.text.toString(), 1, Tasbih.MAX_TARGET)
        if (target == null) { toast("Hedef 1 ile ${TasbihUi.num(Tasbih.MAX_TARGET)} arasında olmalı"); targetInput.requestFocus(); return }
        val count = TasbihLogic.parseInt(countInput.text.toString().ifBlank { "0" }, 0, target)
        if (count == null) { toast("Sayı 0 ile ${TasbihUi.num(target)} arasında olmalı"); countInput.requestFocus(); return }

        val old = original
        // Mevcut sayi elle degistiyse toplam da ayni farkla guncellenir.
        val delta = count - (old?.count ?: 0)
        val saved = draft.copy(
            name = name,
            target = target,
            count = count,
            total = ((old?.total ?: 0L) + delta).coerceAtLeast(0L)
        )
        store.save(saved)
        store.setLastId(saved.id)
        toast(if (old == null) "Tesbih oluşturuldu" else "Kaydedildi")
        if (old == null) TasbihActions.openCounter(this, saved.id)
        finish()
    }

    // ------------------------------------------------------- parcalar

    private fun section(title: String) {
        body.addView(TasbihUi.text(this, title, 16f, bold = true),
            TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(22); bottomMargin = dp(8) })
    }

    private fun hint(text: String) {
        body.addView(TasbihUi.text(this, text, 12.5f, AppColors.TEXT_MUTED),
            TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP).apply { topMargin = dp(6) })
    }

    private fun group() = TasbihUi.column(this).apply {
        background = TasbihUi.card(this@TasbihSettingsActivity)
        setPadding(dp(14), dp(4), dp(14), dp(4))
    }

    private fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(AppColors.STROKE) }, TasbihUi.lp(TasbihUi.MATCH, 1))
    }

    private fun field(parent: LinearLayout, label: String, value: String, type: Int, hint: String, divider: Boolean = false): EditText {
        if (divider) divider(parent)
        val col = TasbihUi.column(this).apply { setPadding(0, dp(10), 0, dp(4)) }
        col.addView(TasbihUi.text(this, label, 12.5f, AppColors.TEXT_MUTED))
        val input = EditText(this).apply {
            setText(value)
            this.hint = hint
            inputType = type
            textSize = 16f
            setTextColor(AppColors.TEXT_PRIMARY)
            setHintTextColor(AppColors.TEXT_DIM)
            background = null
            setPadding(0, dp(4), 0, dp(6))
            if (type == InputType.TYPE_CLASS_NUMBER) filters = arrayOf(android.text.InputFilter.LengthFilter(5))
            else filters = arrayOf(android.text.InputFilter.LengthFilter(Tasbih.MAX_NAME))
        }
        col.addView(input, TasbihUi.lp(TasbihUi.MATCH, TasbihUi.WRAP))
        parent.addView(col)
        return input
    }

    /** Dokununca secici acan satir; sagdaki deger metnini dondurur. */
    private fun choiceRow(parent: LinearLayout, icon: String, label: String, divider: Boolean = false, onClick: () -> Unit): TextView {
        if (divider) divider(parent)
        val row = TasbihUi.row(this).apply {
            setPadding(0, dp(14), 0, dp(14))
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(TasbihUi.text(this, icon, 18f), TasbihUi.lp(TasbihUi.WRAP, TasbihUi.WRAP).apply { marginEnd = dp(12) })
        row.addView(TasbihUi.text(this, label, 15f), TasbihUi.lp(0, TasbihUi.WRAP, 1f))
        val value = TasbihUi.text(this, "", 14f, AppColors.ACCENT)
        row.addView(value)
        row.addView(TasbihUi.text(this, "  ›", 18f, AppColors.TEXT_MUTED))
        parent.addView(row)
        return value
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = TasbihUi.dp(this, value)
}
