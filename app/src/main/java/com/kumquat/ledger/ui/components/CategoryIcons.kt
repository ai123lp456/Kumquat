package com.kumquat.ledger.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AssignmentReturn
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PriceCheck
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Soap
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BreakfastDining
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kumquat.ledger.data.Category
import com.kumquat.ledger.domain.HuiWangSubtype
import com.kumquat.ledger.ui.theme.CandyPink
import com.kumquat.ledger.ui.theme.Exchange
import com.kumquat.ledger.ui.theme.Expense
import com.kumquat.ledger.ui.theme.Income
import com.kumquat.ledger.ui.theme.Refund

/** 图标库分组项：iconKey 稳定存库，label 只用于选择器展示。 */
data class IconOption(val key: String, val label: String)

/**
 * 内置 25 个分类图标，全部走 Material Icons，离线可用。
 * 新增图标只改这里，不动数据库。
 */
val IconLibraryGroups: List<Pair<String, List<IconOption>>> = listOf(
    "餐饮" to listOf(
        IconOption("restaurant", "餐饮"),
        IconOption("breakfast_dining", "早餐"),
        IconOption("cookie", "零食"),
        IconOption("local_cafe", "饮料"),
        IconOption("local_grocery_store", "水果/食材")
    ),
    "出行" to listOf(
        IconOption("directions_bus", "公交"),
        IconOption("local_taxi", "打车"),
        IconOption("train", "火车/地铁"),
        IconOption("directions_bike", "骑行")
    ),
    "购物学习" to listOf(
        IconOption("shopping_bag", "购物"),
        IconOption("checkroom", "服饰"),
        IconOption("devices", "数码"),
        IconOption("menu_book", "学习")
    ),
    "生活健康" to listOf(
        IconOption("medical_services", "医疗"),
        IconOption("sports_esports", "娱乐"),
        IconOption("fitness_center", "运动"),
        IconOption("flight", "旅行")
    ),
    "住房通讯" to listOf(
        IconOption("home", "住房"),
        IconOption("bolt", "水电"),
        IconOption("phone_iphone", "通讯")
    ),
    "人情理财" to listOf(
        IconOption("card_giftcard", "红包/礼物"),
        IconOption("payments", "工资/收款"),
        IconOption("savings", "理财/储蓄"),
        IconOption("pets", "宠物"),
        IconOption("category", "其他/默认")
    )
)

val IconLibrary: List<IconOption> = IconLibraryGroups.flatMap { it.second }

val DefaultIconKey = "category"

/** 内置分类图标键 -> Material 图标；未知键回退到通用分类图标。 */
fun categoryIcon(key: String): ImageVector = when (key) {
    // 新 25 个标准 key
    "restaurant" -> Icons.Rounded.Restaurant
    "breakfast_dining" -> Icons.Rounded.BreakfastDining
    "cookie" -> Icons.Rounded.Cookie
    "local_cafe" -> Icons.Rounded.LocalCafe
    "local_grocery_store" -> Icons.Rounded.LocalGroceryStore
    "directions_bus" -> Icons.Rounded.DirectionsBus
    "local_taxi" -> Icons.Rounded.LocalTaxi
    "train" -> Icons.Rounded.Train
    "directions_bike" -> Icons.Rounded.DirectionsBike
    "shopping_bag" -> Icons.Rounded.ShoppingBag
    "checkroom" -> Icons.Rounded.Checkroom
    "devices" -> Icons.Rounded.Devices
    "menu_book" -> Icons.Rounded.MenuBook
    "medical_services" -> Icons.Rounded.MedicalServices
    "sports_esports" -> Icons.Rounded.SportsEsports
    "fitness_center" -> Icons.Rounded.FitnessCenter
    "flight" -> Icons.Rounded.Flight
    "home" -> Icons.Rounded.Home
    "bolt" -> Icons.Rounded.Bolt
    "phone_iphone" -> Icons.Rounded.PhoneIphone
    "card_giftcard" -> Icons.Rounded.CardGiftcard
    "payments" -> Icons.Rounded.Payments
    "savings" -> Icons.Rounded.Savings
    "pets" -> Icons.Rounded.Pets
    "category" -> Icons.Rounded.Category

    // v1.1 旧 key 兼容
    "food", "food_meal" -> Icons.Rounded.Restaurant
    "food_snack" -> Icons.Rounded.Fastfood
    "food_snack_healthy" -> Icons.Rounded.Eco
    "food_drink" -> Icons.Rounded.LocalDrink
    "food_drink_healthy" -> Icons.Rounded.LocalCafe
    "food_fruit" -> Icons.Rounded.Icecream
    "food_other" -> Icons.Rounded.MoreHoriz
    "shopping" -> Icons.Rounded.ShoppingBag
    "shop_clothes" -> Icons.Rounded.Checkroom
    "shop_device" -> Icons.Rounded.Devices
    "shop_study" -> Icons.Rounded.MenuBook
    "shop_daily" -> Icons.Rounded.ShoppingCart
    "shop_hygiene" -> Icons.Rounded.Soap
    "expense_other" -> Icons.Rounded.Category
    "income_regular" -> Icons.Rounded.Savings
    "income_task" -> Icons.Rounded.Assignment
    "income_redpacket" -> Icons.Rounded.CardGiftcard
    "income_subsidy" -> Icons.Rounded.Paid
    "income_reward" -> Icons.Rounded.Star
    "income_living" -> Icons.Rounded.AccountBalanceWallet
    "income_other" -> Icons.Rounded.MoreHoriz
    "refund_full" -> Icons.Rounded.Undo
    "refund_part" -> Icons.Rounded.Replay
    "refund_other" -> Icons.Rounded.MoreHoriz
    "ex_pay_for" -> Icons.Rounded.CallMade
    "ex_lend" -> Icons.Rounded.CallMade
    "ex_borrow" -> Icons.Rounded.CallReceived
    "ex_repay" -> Icons.Rounded.CallReceived
    "ex_collect" -> Icons.Rounded.CallReceived
    "ex_other" -> Icons.Rounded.SwapHoriz
    "transfer" -> Icons.Rounded.SyncAlt

    else -> Icons.Rounded.Category
}

/** 回往 6 个方向的固定图标（不参与分类图标选择器）。 */
fun huiWangIcon(subtype: HuiWangSubtype): ImageVector = when (subtype) {
    HuiWangSubtype.REFUND_FULL -> Icons.Rounded.CurrencyExchange
    HuiWangSubtype.REFUND_PARTIAL -> Icons.Rounded.PriceCheck
    HuiWangSubtype.ADVANCE_PAY -> Icons.Rounded.VolunteerActivism
    HuiWangSubtype.LEND -> Icons.Rounded.CallMade
    HuiWangSubtype.COLLECT -> Icons.Rounded.CallReceived
    HuiWangSubtype.REPAY -> Icons.Rounded.AssignmentReturn
}

/** 分类图标底板的装饰色（按 icon 键散列到五种糖色，保持活泼但只做点缀）。 */
fun categoryTint(key: String): Color {
    val palette = listOf(Income, Expense, Refund, Exchange, CandyPink)
    val idx = (key.hashCode().let { if (it < 0) -it else it }) % palette.size
    return palette[idx]
}

/** 根据账单的类型/一级/二级分类找到图标键；找不到返回空串（回退通用图标）。 */
fun categoryIconKey(categories: List<Category>, type: String, l1: String, l2: String): String {
    if (l2.isNotBlank()) {
        val parentId = categories.firstOrNull { it.type == type && it.level == 1 && it.name == l1 }?.id
        val child = categories.firstOrNull {
            it.type == type && it.level == 2 && it.name == l2 && (parentId == null || it.parentId == parentId)
        }
        if (child != null && child.icon.isNotBlank()) return child.icon
    }
    return categories.firstOrNull { it.type == type && it.level == 1 && it.name == l1 }?.icon ?: ""
}

/** 空态插画用小图标。 */
val EmptyStateIcon: ImageVector = Icons.Rounded.Favorite
