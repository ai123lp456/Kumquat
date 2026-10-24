package com.kumquat.ledger.ui.nav

/** 全部导航路由（Navigation Compose）。 */
object Routes {
    const val BILLS = "bills"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val ME = "me"

    const val BOOKS = "books"
    const val ASSETS = "assets"
    const val IMPORT_EXPORT = "io"
    const val CATEGORIES = "categories"
    const val ABOUT = "about"

    /** 记一笔/编辑：txId = -1 表示新增。 */
    const val ADD = "add?txId={txId}&date={date}"
    fun add(txId: Long? = null, date: String? = null): String =
        "add?txId=${txId ?: -1L}&date=${date.orEmpty()}"

    const val DAY = "day/{date}"
    fun day(date: String): String = "day/$date"

    /** 回往明细：复用现有统计页入口，不加入底部导航。 */
    const val HUIWANG = "huiwang?start={start}&end={end}"
    fun huiwang(start: String, end: String): String = "huiwang?start=$start&end=$end"

    val tabs: List<String> = listOf(BILLS, CALENDAR, STATS, ME)
}
