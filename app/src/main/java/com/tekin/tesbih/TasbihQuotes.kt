package com.tekin.tesbih

import java.util.Calendar

/**
 * Gunun sozu: her gun listeden bir ayet ya da hadis.
 *
 * Secim yalnizca tarihe bagli (yil * 1000 + yilin gunu), bu yuzden gun
 * boyunca ayni soz gorunur ve ertesi gun degisir; ag ya da kayit gerekmez.
 * Ayet mealleri kisaltilmis, sade Turkce ifadelerdir; kaynak her sozun
 * altinda gosterilir.
 */
object TasbihQuotes {

    data class Quote(val text: String, val source: String)

    private val all = listOf(
        Quote("Bilesiniz ki kalpler ancak Allah'ı anmakla huzur bulur.", "Ra'd, 13/28"),
        Quote("Beni anın ki ben de sizi anayım. Bana şükredin, nankörlük etmeyin.", "Bakara, 2/152"),
        Quote("Sabır ve namazla yardım dileyin. Şüphesiz Allah sabredenlerle beraberdir.", "Bakara, 2/153"),
        Quote("Kullarım sana beni sorarsa, şüphesiz ben çok yakınım. Bana dua edenin duasına karşılık veririm.", "Bakara, 2/186"),
        Quote("Allah hiç kimseye gücünün yetmeyeceği bir yük yüklemez.", "Bakara, 2/286"),
        Quote("Şüphesiz zorlukla beraber bir kolaylık vardır.", "İnşirah, 94/5-6"),
        Quote("Ey iman edenler! Allah'ı çokça anın ve O'nu sabah akşam tesbih edin.", "Ahzâb, 33/41-42"),
        Quote("Onlar ayakta, otururken ve yanları üzerine yatarken Allah'ı anarlar.", "Âl-i İmrân, 3/191"),
        Quote("Gevşemeyin, üzülmeyin; eğer inanıyorsanız üstün olan sizsiniz.", "Âl-i İmrân, 3/139"),
        Quote("Allah bize yeter; O ne güzel vekildir.", "Âl-i İmrân, 3/173"),
        Quote("Kim Allah'a tevekkül ederse, O ona yeter.", "Talâk, 65/3"),
        Quote("Kim Allah'tan sakınırsa, Allah ona bir çıkış yolu açar.", "Talâk, 65/2"),
        Quote("Allah'ın rahmetinden ümidinizi kesmeyin. Şüphesiz Allah bütün günahları bağışlar.", "Zümer, 39/53"),
        Quote("Andolsun, eğer şükrederseniz size nimetimi artırırım.", "İbrâhîm, 14/7"),
        Quote("Biz insana şah damarından daha yakınız.", "Kâf, 50/16"),
        Quote("Bana dua edin, size karşılık vereyim.", "Mü'min, 40/60"),
        Quote("Nerede olursanız olun, O sizinle beraberdir.", "Hadîd, 57/4"),
        Quote("Sabret; çünkü Allah iyilik yapanların ecrini zayi etmez.", "Hûd, 11/115"),
        Quote("Rabbini içinden, yalvararak ve saygıyla, sabah akşam an; gafillerden olma.", "A'râf, 7/205"),
        Quote("Allah'ın rahmeti iyilik yapanlara çok yakındır.", "A'râf, 7/56"),
        Quote("Allah'ı çokça anın ki kurtuluşa eresiniz.", "Cuma, 62/10"),
        Quote("Allah'ı anmak elbette en büyük ibadettir.", "Ankebût, 29/45"),
        Quote("Rabbini hamd ile tesbih et ve secde edenlerden ol.", "Hicr, 15/98"),
        Quote("Rabbini hamd ile tesbih et ve O'ndan bağışlanma dile; O tövbeleri çok kabul edendir.", "Nasr, 110/3"),
        Quote("O'nu hamd ile tesbih etmeyen hiçbir şey yoktur.", "İsrâ, 17/44"),
        Quote("Rabbin seni bırakmadı, sana darılmadı da.", "Duhâ, 93/3"),
        Quote("Rabbin sana verecek, sen de razı olacaksın.", "Duhâ, 93/5"),
        Quote("Bilin ki Allah'ın yardımı yakındır.", "Bakara, 2/214"),
        Quote("Rabbim! Gönlüme genişlik ver, işimi bana kolaylaştır.", "Tâhâ, 20/25-26"),
        Quote("Rabbim! İlmimi artır.", "Tâhâ, 20/114"),
        Quote("Kötülüğü en güzel olanla sav.", "Fussilet, 41/34"),
        Quote("İnsanlara güzel söz söyleyin.", "Bakara, 2/83"),
        Quote("İyilik ve takvada yardımlaşın.", "Mâide, 5/2"),
        Quote("Kim zerre kadar hayır işlerse onu görür.", "Zilzâl, 99/7"),
        Quote("Kim sabreder ve bağışlarsa, bu kararlılık gerektiren işlerdendir.", "Şûrâ, 42/43"),
        Quote("Allah, kendisine karşı gelmekten sakınanlarla ve iyilik yapanlarla beraberdir.", "Nahl, 16/128"),
        Quote("Dile hafif, mizanda ağır, Rahmân'a sevimli iki söz: Sübhânallâhi ve bi-hamdihî, sübhânallâhi'l-azîm.", "Hadis-i Şerif, Buhârî ve Müslim"),
        Quote("Ameller ancak niyetlere göredir.", "Hadis-i Şerif, Buhârî ve Müslim"),
        Quote("Kolaylaştırın, zorlaştırmayın; müjdeleyin, nefret ettirmeyin.", "Hadis-i Şerif, Buhârî"),
        Quote("Kardeşine gülümsemen sadakadır.", "Hadis-i Şerif, Tirmizî")
    )

    fun today(now: Long = System.currentTimeMillis()): Quote {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val key = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        // Ardisik gunlerde listede siradan gitmek yerine dagitarak sec.
        val index = ((key.toLong() * 17L) % all.size).toInt()
        return all[index]
    }
}
