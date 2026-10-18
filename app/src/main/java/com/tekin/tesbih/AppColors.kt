package com.tekin.tesbih

import android.graphics.Color

/**
 * Programatik olarak kurulan view'lar icin renk paleti.
 *
 * Ekranlarin cogu view'lari koddan olusturuyor ve renkleri
 * Color.parseColor("#...") ile veriyordu: 71 cagri, 26 farkli sabit deger,
 * ayrica Color.rgb(...) ile yazilmis 14 cagri daha. Ayni rol her dosyada
 * biraz farkli bir tona kayiyordu.
 *
 * Buradaki isimler values/colors.xml (koyu) ve values-notnight/colors.xml
 * (acik) ile ayni rolleri tasir. XML tarafi @color/ ile, kod tarafi bu
 * nesne ile ayni paleti kullanir; bir tonu degistirirken uc yere de
 * bakilmalidir.
 */
object AppColors {

    /**
     * Koyu (bugunku) ve acik tema degeri. Tema surec basinda secilir
     * (ThemeMode); degisince uygulama yeniden baslar. XML tarafinda ayni
     * roller values/colors.xml (koyu) ve values-notnight/colors.xml (acik).
     */
    private fun c(dark: String, light: String): Int = ThemeMode.pick(dark, light)

    // Zeminler
    val BG_APP = c("#12172B", "#F4F1EA")
    val BG_CARD = c("#151D32", "#FFFFFF")
    val BG_FIELD = c("#0E1526", "#EEF0F5")
    val BG_ELEVATED = c("#1A2440", "#FFFFFF")

    // Cizgiler
    val STROKE = c("#26314D", "#DCE0EA")
    val STROKE_STRONG = c("#333A55", "#C7CDDB")

    // Metin
    val TEXT_PRIMARY = c("#F2EFE6", "#1B2033")
    val TEXT_SECONDARY = c("#AEB5CF", "#4A5373")
    /** Soluk metin: koyu ve acik zeminde en az ~4.9:1 kontrast (kucuk yazi okunur kalsin). */
    val TEXT_MUTED = c("#8790AD", "#5F6785")
    val TEXT_DIM = c("#5A6284", "#8A91AA")
    val TEXT_FAINT = c("#4E587A", "#A3A9BE")

    // Vurgu
    val ACCENT = c("#E8B34A", "#A8750F")
    val ACCENT_LIGHT = c("#F3C765", "#C08A22")
    val ON_ACCENT = c("#111522", "#FFFFFF")

    // Durum
    val SUCCESS = c("#7FBF8C", "#2E8B4E")
    val SUCCESS_BRIGHT = c("#39FF8C", "#1E9E55")
    /** Secili kaynak kartinin soluk yesil yazisi. */
    val SUCCESS_PALE = c("#CFF3DC", "#1F6B3A")
    val DANGER = c("#E8746A", "#C8473C")
    val WARNING = c("#C79A46", "#9A6A12")
    val WARNING_DIM = c("#8E7A4E", "#8A7444")

    // Butonlar
    val BTN_SECONDARY = c("#293451", "#E6E9F1")
    val BTN_SECONDARY_ALT = c("#27304A", "#E9ECF3")
    /** Sonuc kartindaki kucuk baglanti butonu zemini. */
    val BTN_LINK = c("#1D2741", "#EEF0F5")

    // Bolum renkleri
    val SECTION_RESEARCH = c("#5B8DEF", "#3B6FD8")
    val SECTION_CRM = c("#A78BFA", "#7C5CE0")
    val SECTION_PUSH = c("#F0A94B", "#C97B12")
    val SECTION_EXCHANGE = c("#4FB6E8", "#1F8FC4")
    val SECTION_SCREENER = c("#72C98A", "#2E9A55")
    val SECTION_BOUNTY = c("#E39BD0", "#B4549C")
    val SECTION_NETWORK = c("#2DD4BF", "#0F9D8A")
    val SECTION_TLSCANNER = c("#818CF8", "#5B63E0")

    // ------------------------------------------------- Dialog (koyu zemin)
    //
    // Pencereler secili temada aciliyor (Theme.WorkPlanner.Dialog; koyu
    // temada bugunku koyu pencere).
    // Bu grup eskiden beyaz zemin icin koyu renklerdi; koyu pencerede koyu
    // yazi okunmuyordu (Engel kontrolu secenekleri, sablon satirlari vb.).
    // Isimler ayni kaldi, degerler koyu zemine gore.
    val DIALOG_TEXT = c("#F2EFE6", "#1B2033")
    val DIALOG_LABEL = c("#AEB5CF", "#4A5373")
    val DIALOG_MUTED = c("#8C94AE", "#6B7390")
    val DIALOG_OK = c("#7FBF8C", "#2E8B4E")
    val DIALOG_DANGER = c("#E08A8A", "#C8473C")
    val DIALOG_ACCENT = c("#E8B34A", "#A8750F")
    val DIALOG_INFO = c("#7FB2F0", "#2F6FCF")

    // Dialog icindeki bilgi kartlarinin zeminleri ve ayirici cizgiler.
    // Yazi renkleri yukaridakiler; bunlar yalnizca o yazinin arkasindaki
    // hafif renkli yuzey.
    val DIALOG_SURFACE = c("#26304C", "#EEF0F5")
    val DIALOG_SURFACE_AMBER = c("#3A2F17", "#FBF0D9")
    val DIALOG_SURFACE_OK = c("#17351F", "#E3F4E8")
    val DIALOG_SURFACE_DANGER = c("#3D1F22", "#FBE6E3")
    val DIALOG_SURFACE_INFO = c("#18284A", "#E3ECFB")

    // Dialog kartinin icindeki kucuk dugme ve ayirici: kartin uzerinde
    // hafif acik bir katman (eskiden beyazdi).
    val DIALOG_CHIP = if (ThemeMode.light) Color.argb(20, 0, 0, 0) else Color.argb(36, 255, 255, 255)
}
