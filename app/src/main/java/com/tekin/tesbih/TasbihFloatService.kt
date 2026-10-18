package com.tekin.tesbih

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Kucuk ekranda tespih: yuzen balon ve bildirim.
 *
 * Sayac durumu tek: TasbihStore. Balon, bildirim ve sayma ekrani ayni
 * store.tap / store.undo'yu cagiriyor; sayma kurali TasbihLogic'te,
 * burada kopyasi yok. Store degisince (hangi yerden sayilirsa sayilsin)
 * balon ve bildirim tazelenir.
 *
 * Balon: dokun = +1, surukle = tasi (birakinca kenara yapisir), uzun bas
 * = uygulamaya don (balon acik kalir), asagidaki X'e birak = kapat. "Diger uygulamalarin
 * uzerinde goster" izni yoksa yalnizca bildirim gosterilir; sayma
 * bildirimdeki +1 ile surer. Kapaninca bildirim de kalkar, sayac kalir.
 */
class TasbihFloatService : Service() {

    private lateinit var store: TasbihStore
    private lateinit var feedback: TasbihFeedback
    private var tasbihId: String = ""

    private var windowManager: WindowManager? = null
    private var bubble: TasbihBubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var closeTarget: TextView? = null

    private val handler = Handler(Looper.getMainLooper())

    // Store'daki her degisiklikte (sayma ekrani dahil) balon ve bildirim tazelenir.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private var prefs: SharedPreferences? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = TasbihStore(this)
        feedback = TasbihFeedback(this)
        ensureChannel()
        prefs = getSharedPreferences(TasbihStore.PREFS_NAME, Context.MODE_PRIVATE).also {
            it.registerOnSharedPreferenceChangeListener(prefsListener)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(TasbihActions.EXTRA_ID)?.let { tasbihId = it }
        val tasbih = store.get(tasbihId)
        if (tasbih == null) {
            stopEverything()
            return START_NOT_STICKY
        }
        // Android, on plan servisinin bildirimini birkac saniye icinde ister.
        startInForeground(buildNotification(tasbih))

        when (intent?.action) {
            ACTION_COUNT -> count()
            ACTION_UNDO -> undo()
            ACTION_OPEN -> { openApp(); return START_NOT_STICKY }
            ACTION_CLOSE -> { stopEverything(); return START_NOT_STICKY }
            else -> showBubbleIfAllowed()
        }
        refresh()
        // Telefon servisi kapatirsa yeniden baslatmiyoruz: sayac zaten kayitli.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        prefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
        removeBubble()
        feedback.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------ sayma

    /** Sayma ekraniyla ayni kural ve ayni hissiyat. */
    private fun count() {
        val result = store.tap(tasbihId) ?: return
        val t = result.tasbih
        feedback.tickMs = store.getTickMs()
        feedback.pulseMs = store.getPulseMs()
        when (result.event) {
            TasbihEvent.BLOCKED -> feedback.blocked()
            TasbihEvent.COMPLETED -> {
                feedback.finish(t.finishVibe)
                if (t.sound) feedback.click(finish = true)
            }
            TasbihEvent.MILESTONE -> {
                feedback.tick(strong = t.vibrateEvery > 1)
                if (t.sound) feedback.click()
            }
            TasbihEvent.COUNTED -> if (t.sound) feedback.click()
        }
        refresh()   // sayi aninda; store dinleyicisini beklemeden
        bubble?.let { b ->
            b.animate().cancel()
            b.scaleX = 0.92f; b.scaleY = 0.92f
            b.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }
    }

    private fun undo() {
        if (store.undo(tasbihId) != null) {
            feedback.tickMs = store.getTickMs()
            feedback.tick(strong = false)
        }
        refresh()
    }

    private fun refresh() {
        val t = store.get(tasbihId) ?: return
        bubble?.apply {
            material = t.material
            count = t.count
            target = t.target
        }
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(t))
        }
    }

    private fun openApp() {
        val intent = Intent(this, TasbihCounterActivity::class.java)
            .putExtra(TasbihActions.EXTRA_ID, tasbihId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
        // Balon acik kaliyor; yalnizca X ya da bildirimdeki "Kapat" kapatir.
    }

    private fun stopEverything() {
        removeBubble()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    // ------------------------------------------------------------ balon

    private fun showBubbleIfAllowed() {
        if (bubble != null) return
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val size = dp(84)
        val pos = getSharedPreferences(POS_PREFS, Context.MODE_PRIVATE)
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pos.getInt("x", screenWidth() - size - dp(8))
            y = pos.getInt("y", dp(220))
        }
        val view = TasbihBubbleView(this).apply {
            elevation = dp(8).toFloat()
            contentDescription = "Tespih balonu. Dokun: bir say. Uzun bas: uygulamaya dön."
        }
        bindTouch(view, params)
        runCatching { wm.addView(view, params) }.onSuccess {
            bubble = view
            bubbleParams = params
        }
    }

    private fun removeBubble() {
        val wm = windowManager ?: return
        bubble?.let { runCatching { wm.removeView(it) } }
        closeTarget?.let { runCatching { wm.removeView(it) } }
        bubble = null
        closeTarget = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindTouch(view: TasbihBubbleView, params: WindowManager.LayoutParams) {
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragging = false
        var longPressed = false
        val slop = dp(8)
        val longPress = Runnable {
            if (!dragging) {
                longPressed = true
                feedback.tick(strong = true)
                openApp()
            }
        }

        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false; longPressed = false
                    handler.postDelayed(longPress, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                        showCloseTarget()
                    }
                    if (dragging) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt()
                        runCatching { windowManager?.updateViewLayout(view, params) }
                        view.closing = overCloseTarget(params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    when {
                        longPressed -> Unit
                        dragging -> {
                            val close = overCloseTarget(params)
                            hideCloseTarget()
                            if (close) stopEverything() else snapToEdge(view, params)
                        }
                        else -> count()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    hideCloseTarget()
                    view.closing = false
                }
            }
            true
        }
    }

    /** Birakilinca en yakin yan kenara yapisir; konum hatirlanir. */
    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val w = screenWidth()
        val size = params.width
        params.x = if (params.x + size / 2 < w / 2) dp(8) else w - size - dp(8)
        params.y = params.y.coerceIn(dp(24), screenHeight() - size - dp(24))
        runCatching { windowManager?.updateViewLayout(view, params) }
        getSharedPreferences(POS_PREFS, Context.MODE_PRIVATE).edit()
            .putInt("x", params.x).putInt("y", params.y).apply()
    }

    private fun showCloseTarget() {
        if (closeTarget != null) return
        val wm = windowManager ?: return
        val size = dp(64)
        val view = TextView(this).apply {
            text = "✕"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(AppColors.TEXT_PRIMARY)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC1A2440.toInt())
                setStroke(dp(2), AppColors.TEXT_SECONDARY)
            }
        }
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth() - size) / 2
            y = screenHeight() - size - dp(72)
        }
        runCatching { wm.addView(view, params) }.onSuccess { closeTarget = view }
    }

    private fun hideCloseTarget() {
        closeTarget?.let { runCatching { windowManager?.removeView(it) } }
        closeTarget = null
    }

    private fun overCloseTarget(params: WindowManager.LayoutParams): Boolean {
        if (closeTarget == null) return false
        val bx = params.x + params.width / 2f
        val by = params.y + params.height / 2f
        val tx = screenWidth() / 2f
        val ty = screenHeight() - dp(72) - dp(32).toFloat()
        return hypot(bx - tx, by - ty) < dp(70)
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun screenWidth() = resources.displayMetrics.widthPixels
    private fun screenHeight() = resources.displayMetrics.heightPixels
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // --------------------------------------------------------- bildirim

    private fun ensureChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tespih", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Tespih balonu açıkken sayaç ve +1 düğmesi"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun buildNotification(t: Tasbih): Notification {
        val today = store.todayTotal()
        val status = if (t.isDone) "Tamamlandı ✓" else "${TasbihUi.num(t.count)} / ${TasbihUi.num(t.target)}"
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t.name)
            .setContentText("$status · Bugün ${TasbihUi.num(today)}")
            .setProgress(t.target, t.count.coerceAtMost(t.target), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(pending(ACTION_OPEN, 1))
            .addAction(Notification.Action.Builder(null, "+1", pending(ACTION_COUNT, 2)).build())
            .addAction(Notification.Action.Builder(null, "↶ Geri al", pending(ACTION_UNDO, 3)).build())
            .addAction(Notification.Action.Builder(null, "Kapat", pending(ACTION_CLOSE, 4)).build())
            .build()
    }

    private fun pending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, TasbihFloatService::class.java).setAction(action).putExtra(TasbihActions.EXTRA_ID, tasbihId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun startInForeground(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE (API 34)
                startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "tesbih_balon"
        private const val NOTIFICATION_ID = 7301
        private const val POS_PREFS = "tesbih_balon_konum"
        private const val LONG_PRESS_MS = 550L
        private const val FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 1 shl 30

        const val ACTION_START = "tesbih.START"
        const val ACTION_COUNT = "tesbih.COUNT"
        const val ACTION_UNDO = "tesbih.UNDO"
        const val ACTION_OPEN = "tesbih.OPEN"
        const val ACTION_CLOSE = "tesbih.CLOSE"

        fun start(context: Context, tasbihId: String) {
            val intent = Intent(context, TasbihFloatService::class.java)
                .setAction(ACTION_START)
                .putExtra(TasbihActions.EXTRA_ID, tasbihId)
            runCatching { context.startForegroundService(intent) }
        }

        /** Balonu ve bildirimi kapatir (sayac kayitli kalir). */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TasbihFloatService::class.java)) }
        }
    }
}
