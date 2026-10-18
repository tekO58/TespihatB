package com.tekin.tesbih

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tesbihlerin kalici kaydi.
 *
 * Kendi SharedPreferences dosyasinda ("tesbihlerim") duruyor; uygulamanin
 * baska hicbir verisine dokunmuyor, baska hicbir kod da bu dosyayi
 * okumuyor. Her dokunusta yaziliyor: uygulama kapansa ya da telefon
 * kapansa bile kalinan sayi korunur.
 *
 * Gecmis iki yerde tutuluyor: tesbih basina gunluk sayilar ve tum
 * tesbihlerin gunluk toplami. Ikincisi tesbih silinse de kalir.
 */
class TasbihStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ tesbihler

    fun getAll(): List<Tasbih> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { fromJson(it) } }
        }.getOrDefault(emptyList())
    }

    fun get(id: String): Tasbih? = getAll().firstOrNull { it.id == id }

    /** Yeniyse sona ekler, varsa yerinde gunceller (sira degismez). */
    fun save(t: Tasbih) {
        val list = getAll()
        val exists = list.any { it.id == t.id }
        writeAll(if (exists) list.map { if (it.id == t.id) t else it } else list + t)
    }

    fun delete(id: String) {
        writeAll(getAll().filter { it.id != id })
        prefs.edit().remove(KEY_HISTORY_PREFIX + id).remove(KEY_TIME_PREFIX + id).apply()
        if (getLastId() == id) prefs.edit().remove(KEY_LAST).apply()
    }

    /** Ayni ayarlarla, sifirdan baslayan kopya; listede asilin hemen altina. */
    fun duplicate(id: String, now: Long): Tasbih? {
        val list = getAll()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val source = list[index]
        val copy = source.copy(
            id = newId(now),
            name = (source.name + " (kopya)").take(Tasbih.MAX_NAME),
            count = 0, rounds = 0, total = 0,
            createdAt = now, lastUsedAt = 0,
            roundMs = 0, lastTapAt = 0, lastRoundMs = 0, bestRoundMs = 0, timedRounds = 0, timedMs = 0,
            intervals = emptyList()
        )
        writeAll(list.subList(0, index + 1) + copy + list.subList(index + 1, list.size))
        return copy
    }

    // ------------------------------------------------------------- sayma

    /**
     * Dokunusu uygular ve hemen kaydeder. Sayilan her dokunus bugunun
     * gecmisine de yaziliyor.
     */
    fun tap(id: String, now: Long = System.currentTimeMillis()): TasbihLogic.TapResult? {
        val current = get(id) ?: return null
        val idle = getIdleMs()
        val result = TasbihLogic.tap(current, now, idle)
        if (result.event != TasbihEvent.BLOCKED) {
            save(result.tasbih)
            setLastId(id)
            addHistory(id, dayKey(now), +1)
            // Sayarak gecen sure gun gun (haftalik istatistik ve plan icin).
            addTime(id, dayKey(now), TasbihTime.gap(current.lastTapAt, now, idle))
        }
        return result
    }

    fun undo(id: String, now: Long = System.currentTimeMillis()): Tasbih? {
        val current = get(id) ?: return null
        val undone = TasbihLogic.undo(current) ?: return null
        save(undone)
        addHistory(id, dayKey(now), -1)
        return undone
    }

    /** Turdaki sayi, tur ve bu turun suresi sifirlanir; toplam, sure istatistigi ve gecmis korunur. */
    fun resetCounter(id: String) {
        get(id)?.let { save(it.copy(count = 0, rounds = 0, roundMs = 0, lastTapAt = 0)) }
    }

    /** Sayac, tur ve bu tesbihin toplami sifirlanir; gunluk gecmis korunur. */
    fun resetAll(id: String) {
        get(id)?.let {
            save(it.copy(count = 0, rounds = 0, total = 0, roundMs = 0, lastTapAt = 0,
                lastRoundMs = 0, bestRoundMs = 0, timedRounds = 0, timedMs = 0, intervals = emptyList()))
        }
    }

    // ------------------------------------------------------------ gecmis

    /** Tesbihin gun -> adet kaydi, yeni gun ustte. */
    fun historyOf(id: String): List<Pair<String, Int>> = readDays(KEY_HISTORY_PREFIX + id)

    /** Tum tesbihlerin gun -> adet toplami, yeni gun ustte. */
    fun dailyTotals(): List<Pair<String, Int>> = readDays(KEY_DAYS)

    fun todayTotal(now: Long = System.currentTimeMillis()): Int =
        dailyTotals().firstOrNull { it.first == dayKey(now) }?.second ?: 0

    fun todayOf(id: String, now: Long = System.currentTimeMillis()): Int =
        historyOf(id).firstOrNull { it.first == dayKey(now) }?.second ?: 0

    fun clearHistory() {
        val editor = prefs.edit().remove(KEY_DAYS).remove(KEY_TIME_DAYS)
        prefs.all.keys.filter { it.startsWith(KEY_HISTORY_PREFIX) || it.startsWith(KEY_TIME_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    // --------------------------------------------------------- gunluk sure

    /** Tesbihin o gun sayarak gecen suresi (ms). */
    fun timeOf(id: String, day: String): Long = readTime(KEY_TIME_PREFIX + id, day)

    /** Tum tesbihlerin o gun sayarak gecen suresi (ms). */
    fun timeOfDay(day: String): Long = readTime(KEY_TIME_DAYS, day)

    private fun readTime(key: String, day: String): Long =
        runCatching { JSONObject(prefs.getString(key, null) ?: "{}").optLong(day, 0L) }.getOrDefault(0L)

    private fun addTime(id: String, day: String, ms: Long) {
        if (ms <= 0) return
        val editor = prefs.edit()
        listOf(KEY_TIME_PREFIX + id, KEY_TIME_DAYS).forEach { key ->
            val json = runCatching { JSONObject(prefs.getString(key, null) ?: "{}") }.getOrDefault(JSONObject())
            json.put(day, json.optLong(day, 0L) + ms)
            val keys = json.keys().asSequence().toList()
            val trimmed = if (keys.size <= MAX_DAYS) json else JSONObject().apply {
                keys.sortedDescending().take(MAX_DAYS).forEach { put(it, json.optLong(it, 0L)) }
            }
            editor.putString(key, trimmed.toString())
        }
        editor.apply()
    }

    private fun addHistory(id: String, day: String, delta: Int) {
        val editor = prefs.edit()
        listOf(KEY_HISTORY_PREFIX + id, KEY_DAYS).forEach { key ->
            val json = runCatching { JSONObject(prefs.getString(key, null) ?: "{}") }.getOrDefault(JSONObject())
            val value = (json.optInt(day, 0) + delta).coerceAtLeast(0)
            json.put(day, value)
            editor.putString(key, trimDays(json).toString())
        }
        editor.apply()
    }

    private fun readDays(key: String): List<Pair<String, Int>> {
        val json = runCatching { JSONObject(prefs.getString(key, null) ?: "{}") }.getOrDefault(JSONObject())
        return json.keys().asSequence()
            .map { it to json.optInt(it, 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.first }
            .toList()
    }

    /** Gecmis sinirsiz buyumesin: en yeni MAX_DAYS gun kalir. */
    private fun trimDays(json: JSONObject): JSONObject {
        val keys = json.keys().asSequence().toList()
        if (keys.size <= MAX_DAYS) return json
        val keep = keys.sortedDescending().take(MAX_DAYS).toSet()
        return JSONObject().apply { keep.forEach { put(it, json.optInt(it, 0)) } }
    }

    // ----------------------------------------------------------- ayarlar

    fun getLastId(): String? = prefs.getString(KEY_LAST, null)

    fun setLastId(id: String) {
        prefs.edit().putString(KEY_LAST, id).apply()
    }

    /** Son kullanilan tesbih; silinmisse null. */
    fun getLast(): Tasbih? = getLastId()?.let { get(it) }

    fun isKeepScreenOn(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN, true)

    fun setKeepScreenOn(on: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN, on).apply()
    }

    /** Boncugu saymak icin gereken asagi cekme mesafesi (dp). */
    fun getPullDp(): Int = prefs.getInt(KEY_PULL_DP, TasbihPull.DEFAULT_DP).coerceIn(TasbihPull.MIN_DP, TasbihPull.MAX_DP)

    fun setPullDp(dp: Int) {
        prefs.edit().putInt(KEY_PULL_DP, dp.coerceIn(TasbihPull.MIN_DP, TasbihPull.MAX_DP)).apply()
    }

    fun getTickMs(): Int = prefs.getInt(KEY_TICK_MS, TasbihVibe.DEFAULT_TICK_MS)
        .coerceIn(TasbihVibe.MIN_TICK_MS, TasbihVibe.MAX_TICK_MS)

    fun setTickMs(ms: Int) {
        prefs.edit().putInt(KEY_TICK_MS, ms.coerceIn(TasbihVibe.MIN_TICK_MS, TasbihVibe.MAX_TICK_MS)).apply()
    }

    fun getPulseMs(): Int = prefs.getInt(KEY_PULSE_MS, TasbihVibe.DEFAULT_PULSE_MS)
        .coerceIn(TasbihVibe.MIN_PULSE_MS, TasbihVibe.MAX_PULSE_MS)

    fun setPulseMs(ms: Int) {
        prefs.edit().putInt(KEY_PULSE_MS, ms.coerceIn(TasbihVibe.MIN_PULSE_MS, TasbihVibe.MAX_PULSE_MS)).apply()
    }

    /** Iki dokunus arasi bu sureyi asarsa sure duraklar (bkz. TasbihTime). */
    fun getIdleMs(): Long {
        val value = prefs.getLong(KEY_IDLE_MS, TasbihTime.DEFAULT_IDLE_MS)
        return if (value in TasbihTime.IDLE_OPTIONS) value else TasbihTime.DEFAULT_IDLE_MS
    }

    fun setIdleMs(ms: Long) {
        prefs.edit().putLong(KEY_IDLE_MS, ms).apply()
    }

    fun deleteEverything() {
        prefs.edit().clear().apply()
    }

    // --------------------------------------------------------------- json

    private fun writeAll(list: List<Tasbih>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    private fun toJson(t: Tasbih): JSONObject = JSONObject()
        .put("id", t.id)
        .put("name", t.name)
        .put("target", t.target)
        .put("count", t.count)
        .put("rounds", t.rounds)
        .put("total", t.total)
        .put("material", t.material.name)
        .put("every", t.vibrateEvery)
        .put("finish", t.finishVibe.name)
        .put("sound", t.sound)
        .put("onTarget", t.onTarget.name)
        .put("createdAt", t.createdAt)
        .put("lastUsedAt", t.lastUsedAt)
        .put("roundMs", t.roundMs)
        .put("lastTapAt", t.lastTapAt)
        .put("lastRoundMs", t.lastRoundMs)
        .put("bestRoundMs", t.bestRoundMs)
        .put("timedRounds", t.timedRounds)
        .put("timedMs", t.timedMs)
        .put("intervals", JSONArray().apply { t.intervals.forEach { put(it) } })

    private fun fromJson(o: JSONObject): Tasbih? {
        val id = o.optString("id")
        val name = o.optString("name")
        if (id.isBlank() || name.isBlank()) return null
        val target = o.optInt("target", 33).coerceIn(1, Tasbih.MAX_TARGET)
        return Tasbih(
            id = id,
            name = name.take(Tasbih.MAX_NAME),
            target = target,
            count = TasbihLogic.clampCount(o.optInt("count", 0), target),
            rounds = o.optInt("rounds", 0).coerceAtLeast(0),
            total = o.optLong("total", 0).coerceAtLeast(0),
            // Eski kayitlarda materyal yok, renk sirasi var.
            material = TasbihMaterial.byName(o.optString("material"))
                ?: TasbihMaterial.fromLegacyColor(o.optInt("color", 0)),
            vibrateEvery = o.optInt("every", 33).coerceIn(0, Tasbih.MAX_EVERY),
            finishVibe = enumOr(o.optString("finish"), TasbihFinishVibe.LONG),
            sound = o.optBoolean("sound", false),
            onTarget = enumOr(o.optString("onTarget"), TasbihOnTarget.STOP),
            createdAt = o.optLong("createdAt", 0),
            lastUsedAt = o.optLong("lastUsedAt", 0),
            roundMs = o.optLong("roundMs", 0).coerceAtLeast(0),
            lastTapAt = o.optLong("lastTapAt", 0).coerceAtLeast(0),
            lastRoundMs = o.optLong("lastRoundMs", 0).coerceAtLeast(0),
            bestRoundMs = o.optLong("bestRoundMs", 0).coerceAtLeast(0),
            timedRounds = o.optInt("timedRounds", 0).coerceAtLeast(0),
            timedMs = o.optLong("timedMs", 0).coerceAtLeast(0),
            intervals = o.optJSONArray("intervals")?.let { arr ->
                (0 until arr.length()).map { arr.optLong(it, 0L) }.filter { it > 0 }
                    .takeLast(TasbihTime.SPEED_WINDOW)
            } ?: emptyList()
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    companion object {
        const val PREFS_NAME = "tesbihlerim"

        private const val KEY_LIST = "list"
        private const val KEY_LAST = "last"
        private const val KEY_DAYS = "days"
        private const val KEY_HISTORY_PREFIX = "hist_"
        private const val KEY_KEEP_SCREEN = "keep_screen"
        private const val KEY_PULL_DP = "pull_dp"
        private const val KEY_TICK_MS = "tick_ms"
        private const val KEY_PULSE_MS = "pulse_ms"
        private const val KEY_IDLE_MS = "idle_ms"
        private const val KEY_TIME_PREFIX = "time_"
        private const val KEY_TIME_DAYS = "time_days"

        const val MAX_DAYS = 400

        fun newId(now: Long): String = "t" + now.toString(36) + (0..999).random().toString(36)

        fun dayKey(epochMs: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))

        /** "2026-09-25" -> "Bugün" / "Dün" / "23 Eylül". */
        fun dayLabel(day: String, now: Long = System.currentTimeMillis()): String {
            if (day == dayKey(now)) return "Bugün"
            val yesterday = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }
            if (day == dayKey(yesterday.timeInMillis)) return "Dün"
            val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day) }.getOrNull() ?: return day
            val sameYear = dayKey(now).take(4) == day.take(4)
            return SimpleDateFormat(if (sameYear) "d MMMM" else "d MMMM yyyy", Locale.forLanguageTag("tr")).format(date)
        }
    }
}
