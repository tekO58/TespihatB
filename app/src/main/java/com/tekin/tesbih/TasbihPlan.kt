package com.tekin.tesbih

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Haftalik tespih plani.
 *
 * Plan "gun -> zikir" degil, PLAN OGELERI: her oge bir tespih (ya da
 * "Serbest" = tum tespihler), gun gun hedef/saat, hatirlatma ve aktif/pasif.
 * "Her gun" gibi secimler arayuzde kisayol; kayitta her gun ayri duruyor.
 *
 * Ilerleme AYRICA SAYILMIYOR: tespihin gunluk kaydindan (TasbihStore.todayOf)
 * hesaplaniyor. Plan disinda cekilen de sayilir, hafta bitince sifirlanacak
 * sayac yok. Gecmis gunler o gunun plan kaydindan (snapshot) degerlendirilir;
 * plani degistirmek yalnizca bugunu ve sonrasini etkiler.
 *
 * Tespih modulunun kendi kayit dosyasinda (TasbihStore.PREFS_NAME) duruyor;
 * uygulamanin baska hicbir verisine dokunmuyor.
 */

/** Bir gunun hedefi ve saati. minute: gun icindeki dakika (0..1439), -1 = saat yok. */
data class TasbihPlanDay(val target: Int, val minute: Int = -1)

data class TasbihPlanItem(
    val id: String,
    /** Tespih kimligi; bos = Serbest (o gun cekilen tum tespihler). */
    val tasbihId: String,
    /** Gun (1 = Pazartesi .. 7 = Pazar) -> hedef ve saat. */
    val days: Map<Int, TasbihPlanDay>,
    val reminder: Boolean = true,
    val active: Boolean = true,
    val createdAt: Long = 0
) {
    val isFree: Boolean get() = tasbihId.isBlank()
}

/** Bir gunun tek bir plan kalemi (oge + o gunun degerleri). */
data class TasbihPlanEntry(
    val itemId: String,
    val tasbihId: String,
    val name: String,
    val target: Int,
    val minute: Int,
    val reminder: Boolean
) {
    val isFree: Boolean get() = tasbihId.isBlank()
}

/** Kalem + o gun tamamlanan adet (hedefle sinirli). */
data class TasbihPlanProgress(val entry: TasbihPlanEntry, val done: Int) {
    val complete: Boolean get() = done >= entry.target
}

enum class TasbihDayStatus { NONE, EMPTY, PARTIAL, DONE }

object TasbihPlanLogic {

    const val FREE_NAME = "Serbest"
    val DAY_SHORT = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    val DAY_LONG = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    val ALL_DAYS = (1..7).toSet()
    val WEEKDAYS = (1..5).toSet()
    val WEEKEND = setOf(6, 7)

    fun dayShort(d: Int) = DAY_SHORT[(d - 1).coerceIn(0, 6)]
    fun dayLong(d: Int) = DAY_LONG[(d - 1).coerceIn(0, 6)]

    /** 1 = Pazartesi .. 7 = Pazar. */
    fun dayOfWeek(millis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val d = c.get(Calendar.DAY_OF_WEEK)   // Pazar = 1
        return if (d == Calendar.SUNDAY) 7 else d - 1
    }

    /** Bu haftanin gunleri (Pazartesi..Pazar) gun anahtarlari. */
    fun weekKeys(now: Long): List<String> {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        c.add(Calendar.DAY_OF_MONTH, -(dayOfWeek(now) - 1))
        return (0 until 7).map {
            val key = TasbihStore.dayKey(c.timeInMillis)
            c.add(Calendar.DAY_OF_MONTH, 1)
            key
        }
    }

    /** Hedef alani: 1..Tasbih.MAX_TARGET, yoksa null. */
    fun parseTarget(raw: String): Int? = TasbihLogic.parseInt(raw, 1, Tasbih.MAX_TARGET)

    fun hhmm(minute: Int): String = if (minute < 0) "" else "%02d:%02d".format(minute / 60, minute % 60)

    /**
     * O gunun kalemleri, saat sirasiyla (saati olmayan sonda). Pasif ogeler ve
     * silinmis tespihe bagli ogeler girmez.
     */
    fun entriesFor(items: List<TasbihPlanItem>, day: Int, names: Map<String, String>): List<TasbihPlanEntry> =
        items.asSequence()
            .filter { it.active && (it.isFree || names.containsKey(it.tasbihId)) }
            .mapNotNull { item ->
                val d = item.days[day] ?: return@mapNotNull null
                if (d.target <= 0) return@mapNotNull null
                TasbihPlanEntry(item.id, item.tasbihId,
                    if (item.isFree) FREE_NAME else names.getValue(item.tasbihId),
                    d.target, d.minute, item.reminder)
            }
            .sortedWith(compareBy<TasbihPlanEntry> { if (it.minute < 0) Int.MAX_VALUE else it.minute }.thenBy { it.itemId })
            .toList()

    /**
     * Gunluk sayilari kalemlere dagitir. Ayni tespih ayni gun birden fazla
     * kalemdeyse saat sirasiyla doldurulur (sabah 500 + aksam 500, 700 cekildi:
     * 500 ✓, 200). Serbest kalemler gunun toplamindan beslenir.
     */
    fun progress(entries: List<TasbihPlanEntry>, counts: Map<String, Int>, dayTotal: Int): List<TasbihPlanProgress> {
        val pool = counts.toMutableMap()
        var free = dayTotal
        return entries.map { e ->
            if (e.isFree) {
                val done = minOf(free, e.target).coerceAtLeast(0)
                free -= done
                TasbihPlanProgress(e, done)
            } else {
                val have = pool[e.tasbihId] ?: 0
                val done = minOf(have, e.target).coerceAtLeast(0)
                pool[e.tasbihId] = have - done
                TasbihPlanProgress(e, done)
            }
        }
    }

    fun status(progress: List<TasbihPlanProgress>): TasbihDayStatus = when {
        progress.isEmpty() -> TasbihDayStatus.NONE
        progress.all { it.complete } -> TasbihDayStatus.DONE
        progress.any { it.done > 0 } -> TasbihDayStatus.PARTIAL
        else -> TasbihDayStatus.EMPTY
    }

    /**
     * Ogenin gun degerlerini "ortak + istisnalar" olarak ayirir: en cok
     * kullanilan (hedef, saat) ortak, digerleri istisna.
     */
    fun splitCommon(days: Map<Int, TasbihPlanDay>): Pair<TasbihPlanDay?, Map<Int, TasbihPlanDay>> {
        if (days.isEmpty()) return null to emptyMap()
        val common = days.values.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<TasbihPlanDay, Int>> { it.value }.thenBy { it.key.target })
            .first().key
        return common to days.filterValues { it != common }
    }

    /** "Her gün", "Hafta içi", "Hafta sonu" ya da "Pzt, Çar, Cum". */
    fun daysLabel(days: Set<Int>): String = when (days) {
        ALL_DAYS -> "Her gün"
        WEEKDAYS -> "Hafta içi"
        WEEKEND -> "Hafta sonu"
        else -> days.sorted().joinToString(", ") { dayShort(it) }
    }
}

/**
 * Plan kaydi. Tespih kayit dosyasinin icinde; ayri anahtarlar.
 */
class TasbihPlanStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(TasbihStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun items(): List<TasbihPlanItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { itemFrom(it) } }
        }.getOrDefault(emptyList())
    }

    fun save(item: TasbihPlanItem) {
        val list = items()
        val next = if (list.any { it.id == item.id }) list.map { if (it.id == item.id) item else it } else list + item
        write(next)
        if (!prefs.contains(KEY_START)) prefs.edit().putString(KEY_START, TasbihStore.dayKey(System.currentTimeMillis())).apply()
    }

    fun delete(id: String) = write(items().filter { it.id != id })

    private fun write(list: List<TasbihPlanItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(itemJson(it)) }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    /** Planin ilk kuruldugu gun; oncesindeki gunler plansiz sayilir. */
    fun startDay(): String? = prefs.getString(KEY_START, null)

    // ------------------------------------------------------- ayarlar

    fun isPaused(): Boolean = prefs.getBoolean(KEY_PAUSED, false)
    fun setPaused(on: Boolean) = prefs.edit().putBoolean(KEY_PAUSED, on).apply()

    /** Hedef tamamlanmadiysa ikinci hatirlatma: kac dakika sonra (0 = kapali). */
    fun secondReminderMin(): Int = prefs.getInt(KEY_SECOND, 0).let { if (it in SECOND_OPTIONS) it else 0 }
    fun setSecondReminderMin(min: Int) = prefs.edit().putInt(KEY_SECOND, min).apply()

    // ------------------------------------------------ gun kayitlari

    /**
     * Gunun kalemleri. Bugun: guncel plan (ve kaydi tazelenir). Gecmis gun:
     * o gunun kaydi; kayit yoksa plan kurulmadan onceki gunler bos, sonrakiler
     * guncel plana gore.
     */
    fun entriesForDay(day: String, dow: Int, names: Map<String, String>, today: String): List<TasbihPlanEntry> {
        if (day == today) {
            val entries = TasbihPlanLogic.entriesFor(items(), dow, names)
            saveSnapshot(day, entries)
            return entries
        }
        readSnapshot(day)?.let { return it }
        val start = startDay() ?: return emptyList()
        if (day < start) return emptyList()
        // Kaydi olmayan gun (o gun uygulama hic acilmadi) ve gelecek gunler: guncel plan.
        return TasbihPlanLogic.entriesFor(items(), dow, names)
    }

    private fun saveSnapshot(day: String, entries: List<TasbihPlanEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().put("item", it.itemId).put("tasbih", it.tasbihId).put("name", it.name)
                .put("target", it.target).put("minute", it.minute).put("reminder", it.reminder))
        }
        val raw = arr.toString()
        if (prefs.getString(KEY_SNAP_PREFIX + day, null) == raw) return
        val editor = prefs.edit().putString(KEY_SNAP_PREFIX + day, raw)
        // En fazla ~14 haftalik kayit kalsin.
        prefs.all.keys.filter { it.startsWith(KEY_SNAP_PREFIX) }.sortedDescending().drop(MAX_SNAPSHOTS)
            .forEach { editor.remove(it) }
        editor.apply()
    }

    private fun readSnapshot(day: String): List<TasbihPlanEntry>? {
        val raw = prefs.getString(KEY_SNAP_PREFIX + day, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                TasbihPlanEntry(o.optString("item"), o.optString("tasbih"), o.optString("name"),
                    o.optInt("target", 0), o.optInt("minute", -1), o.optBoolean("reminder", true))
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------- json

    private fun itemJson(i: TasbihPlanItem): JSONObject {
        val days = JSONObject()
        i.days.forEach { (d, v) -> days.put(d.toString(), JSONObject().put("target", v.target).put("minute", v.minute)) }
        return JSONObject().put("id", i.id).put("tasbih", i.tasbihId).put("days", days)
            .put("reminder", i.reminder).put("active", i.active).put("createdAt", i.createdAt)
    }

    private fun itemFrom(o: JSONObject): TasbihPlanItem? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        val daysJson = o.optJSONObject("days") ?: JSONObject()
        val days = daysJson.keys().asSequence().mapNotNull { k ->
            val d = k.toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
            val v = daysJson.optJSONObject(k) ?: return@mapNotNull null
            val target = v.optInt("target", 0).coerceIn(0, Tasbih.MAX_TARGET)
            if (target <= 0) return@mapNotNull null
            d to TasbihPlanDay(target, v.optInt("minute", -1).coerceIn(-1, 1439))
        }.toMap()
        return TasbihPlanItem(id, o.optString("tasbih"), days,
            o.optBoolean("reminder", true), o.optBoolean("active", true), o.optLong("createdAt", 0))
    }

    companion object {
        private const val KEY_ITEMS = "plan_items"
        private const val KEY_START = "plan_start"
        private const val KEY_PAUSED = "plan_paused"
        private const val KEY_SECOND = "plan_second_min"
        private const val KEY_SNAP_PREFIX = "plan_day_"
        private const val MAX_SNAPSHOTS = 100

        /** Ikinci hatirlatma secenekleri (dk): kapali, 1 sa, 2 sa, 3 sa. */
        val SECOND_OPTIONS = listOf(0, 60, 120, 180)

        fun newId(now: Long): String = "p" + now.toString(36) + (0..999).random().toString(36)
    }
}

/**
 * Plan ekranlarinin ve hatirlatmalarin ortak okumasi: bir gunun kalemleri
 * ve ilerlemesi, haftanin ozeti.
 */
class TasbihPlanReader(context: Context) {

    private val store = TasbihStore(context)
    private val plan = TasbihPlanStore(context)
    private val tasbihs = store.getAll()
    private val names = tasbihs.associate { it.id to it.name }

    fun tasbih(id: String): Tasbih? = tasbihs.firstOrNull { it.id == id }

    fun hasPlan(): Boolean = plan.items().any { it.active }

    fun dayProgress(day: String, dow: Int, now: Long = System.currentTimeMillis()): List<TasbihPlanProgress> {
        val today = TasbihStore.dayKey(now)
        val entries = plan.entriesForDay(day, dow, names, today)
        if (entries.isEmpty()) return emptyList()
        val counts = entries.filter { !it.isFree }.map { it.tasbihId }.distinct()
            .associateWith { id -> store.historyOf(id).firstOrNull { it.first == day }?.second ?: 0 }
        val total = store.dailyTotals().firstOrNull { it.first == day }?.second ?: 0
        return TasbihPlanLogic.progress(entries, counts, total)
    }

    fun today(now: Long = System.currentTimeMillis()): List<TasbihPlanProgress> =
        dayProgress(TasbihStore.dayKey(now), TasbihPlanLogic.dayOfWeek(now), now)

    data class Week(
        val target: Long,
        val done: Long,
        val daysDone: Int,
        val daysPlanned: Int,
        val timeMs: Long,
        val days: List<Pair<String, List<TasbihPlanProgress>>>
    ) {
        val percent: Int get() = if (target <= 0) 0 else ((done * 100) / target).toInt()
    }

    fun week(now: Long = System.currentTimeMillis()): Week {
        val keys = TasbihPlanLogic.weekKeys(now)
        val days = keys.mapIndexed { i, key -> key to dayProgress(key, i + 1, now) }
        val all = days.flatMap { it.second }
        return Week(
            target = all.sumOf { it.entry.target.toLong() },
            done = all.sumOf { it.done.toLong() },
            daysDone = days.count { TasbihPlanLogic.status(it.second) == TasbihDayStatus.DONE },
            daysPlanned = days.count { it.second.isNotEmpty() },
            timeMs = keys.filter { it <= TasbihStore.dayKey(now) }.sumOf { store.timeOfDay(it) },
            days = days
        )
    }

    /** Bugun bu tespihin plan kalemleri (sayac ekranindaki plan satiri icin). */
    fun todayFor(tasbihId: String, now: Long = System.currentTimeMillis()): List<TasbihPlanProgress> =
        today(now).filter { it.entry.tasbihId == tasbihId }
}
