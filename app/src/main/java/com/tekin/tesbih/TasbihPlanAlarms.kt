package com.tekin.tesbih

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Haftalik plan hatirlatmalari.
 *
 * Her zaman yalnizca SIRADAKI tek alarm kurulur; calinca o saatteki
 * kalemlerin (tamamlanmamis olanlarin) bildirimi gosterilir ve sonraki
 * kurulur. Plan degisince, Tespih ekrani acilinca, telefon yeniden
 * baslayinca ve saat degisince yeniden hesaplanir. Hedefi zaten tamamlanmis
 * kalem icin bildirim gelmez. Istege bagli ikinci hatirlatma ve bildirimdeki
 * "10 dk sonra" da ayni yoldan.
 *
 * Kendi alicisi ve kendi bildirim kanali var; uygulamanin diger
 * alarmlarina (AlarmScheduler, BootReceiver, namaz) dokunmuyor.
 */
object TasbihPlanAlarms {

    private const val CHANNEL_ID = "tespih_plan"
    const val ACTION_FIRE = "com.tekin.tesbih.TASBIH_PLAN_REMINDER"
    const val ACTION_SNOOZE = "com.tekin.tesbih.TASBIH_PLAN_SNOOZE"
    const val ACTION_SNOOZE_FIRE = "com.tekin.tesbih.TASBIH_PLAN_SNOOZE_FIRE"
    private const val REQUEST = 7601
    private const val NOTIFICATION_BASE = 7700
    private const val SNOOZE_MIN = 10
    /** Cok gec calan (telefon kapaliydi) hatirlatma gosterilmez. */
    private const val LATE_LIMIT_MS = 30 * 60_000L

    private const val EXTRA_AT = "at"
    private const val EXTRA_DAY = "day"
    private const val EXTRA_ITEM = "item"

    /** Hatirlatma zamani: kalem + ana ya da ikinci hatirlatma. */
    data class Due(val at: Long, val day: String, val itemIds: List<String>)

    /**
     * Siradaki hatirlatma (saf; test ediliyor). Bugunden itibaren 8 gun
     * taranir; saati ve hatirlatmasi olan aktif kalemler.
     */
    fun next(
        now: Long,
        items: List<TasbihPlanItem>,
        names: Map<String, String>,
        secondMin: Int
    ): Due? {
        val candidates = mutableListOf<Triple<Long, String, String>>()   // at, day, item
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            val dayStart = c.timeInMillis
            val day = TasbihStore.dayKey(dayStart + 12 * 3_600_000L)
            val dow = TasbihPlanLogic.dayOfWeek(dayStart + 12 * 3_600_000L)
            TasbihPlanLogic.entriesFor(items, dow, names)
                .filter { it.reminder && it.minute >= 0 }
                .forEach { e ->
                    val at = dayStart + e.minute * 60_000L
                    candidates += Triple(at, day, e.itemId)
                    if (secondMin > 0) candidates += Triple(at + secondMin * 60_000L, day, e.itemId)
                }
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        val future = candidates.filter { it.first > now + 1_000L }
        val first = future.minByOrNull { it.first } ?: return null
        val same = future.filter { it.first == first.first }
        return Due(first.first, first.second, same.map { it.third }.distinct())
    }

    /** Siradaki hatirlatmayi kurar (varsa eskisini iptal eder). */
    fun schedule(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val base = pending(context, null)
            am.cancel(base)
            val plan = TasbihPlanStore(context)
            if (plan.isPaused()) return
            val names = TasbihStore(context).getAll().associate { it.id to it.name }
            val due = next(System.currentTimeMillis(), plan.items(), names, plan.secondReminderMin()) ?: return
            setAlarm(am, due.at, pending(context, due))
        }
    }

    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        // Tam zamanli alarm izni yoksa birkac dakika gecikebilir.
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pending(context: Context, due: Due?): PendingIntent {
        val intent = Intent(context, TasbihPlanReceiver::class.java).setAction(ACTION_FIRE)
        if (due != null) {
            intent.putExtra(EXTRA_AT, due.at).putExtra(EXTRA_DAY, due.day)
                .putExtra(EXTRA_ITEM, due.itemIds.joinToString(","))
        }
        return PendingIntent.getBroadcast(context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Alarm caldi: o saatin tamamlanmamis kalemleri icin bildirim, sonra siradaki. */
    internal fun fire(context: Context, intent: Intent) {
        runCatching {
            val at = intent.getLongExtra(EXTRA_AT, 0L)
            val day = intent.getStringExtra(EXTRA_DAY).orEmpty()
            val ids = intent.getStringExtra(EXTRA_ITEM).orEmpty().split(",").filter { it.isNotBlank() }.toSet()
            val now = System.currentTimeMillis()
            if (!TasbihPlanStore(context).isPaused() && day == TasbihStore.dayKey(now) && now - at < LATE_LIMIT_MS) {
                notifyOpen(context, ids)
            }
        }
        schedule(context)
    }

    /** Bildirimdeki "10 dk sonra": o kalem icin tek seferlik alarm. */
    internal fun snooze(context: Context, intent: Intent) {
        runCatching {
            val item = intent.getStringExtra(EXTRA_ITEM).orEmpty()
            if (item.isBlank()) return@runCatching
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId(item))
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val fireIntent = Intent(context, TasbihPlanReceiver::class.java).setAction(ACTION_SNOOZE_FIRE)
                .putExtra(EXTRA_ITEM, item)
            val pi = PendingIntent.getBroadcast(context, REQUEST + 100 + (item.hashCode() and 0x3FF), fireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setAlarm(am, System.currentTimeMillis() + SNOOZE_MIN * 60_000L, pi)
        }
    }

    internal fun snoozeFire(context: Context, intent: Intent) {
        runCatching {
            val item = intent.getStringExtra(EXTRA_ITEM).orEmpty()
            if (item.isNotBlank() && !TasbihPlanStore(context).isPaused()) notifyOpen(context, setOf(item))
        }
    }

    /** Verilen kalemlerden bugun tamamlanmamis olanlarin bildirimi. */
    private fun notifyOpen(context: Context, itemIds: Set<String>) {
        val progress = TasbihPlanReader(context).today()
        progress.filter { it.entry.itemId in itemIds && !it.complete }
            .distinctBy { it.entry.itemId }
            .forEach { notify(context, it) }
    }

    private fun notify(context: Context, p: TasbihPlanProgress) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val e = p.entry
        val openIntent = if (e.isFree) Intent(context, TasbihListActivity::class.java)
        else Intent(context, TasbihCounterActivity::class.java).putExtra(TasbihActions.EXTRA_ID, e.tasbihId)
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val id = notificationId(e.itemId)
        val open = PendingIntent.getActivity(context, id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(context, id,
            Intent(context, TasbihPlanReceiver::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_ITEM, e.itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val left = if (p.done > 0) " · kalan ${TasbihUi.num(e.target - p.done)}" else ""
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔔 Zikir zamanı")
            .setContentText("Bugünkü hedefin: ${TasbihUi.num(e.target)} ${e.name}$left · Başlamak için dokun")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "$SNOOZE_MIN dk sonra", snooze).build())
            .build()
        nm.notify(id, notification)
    }

    private fun notificationId(itemId: String) = NOTIFICATION_BASE + (itemId.hashCode() and 0x3FF)

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tespih planı", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Haftalık tespih planındaki zikir zamanı hatırlatmaları"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}

/**
 * Plan hatirlatmasi, erteleme, telefon acilisi ve saat degisikligi. Yalnizca
 * Tespih planinin alarmini kurar; uygulamanin diger alarmlarina dokunmuyor.
 */
class TasbihPlanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TasbihPlanAlarms.ACTION_FIRE -> TasbihPlanAlarms.fire(context, intent)
            TasbihPlanAlarms.ACTION_SNOOZE -> TasbihPlanAlarms.snooze(context, intent)
            TasbihPlanAlarms.ACTION_SNOOZE_FIRE -> TasbihPlanAlarms.snoozeFire(context, intent)
            else -> TasbihPlanAlarms.schedule(context)   // acilis, saat, saat dilimi, guncelleme
        }
    }
}
