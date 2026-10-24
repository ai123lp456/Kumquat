package com.kumquat.ledger.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 当前账本会话（本地偏好持久化）。 */
object SessionStore {

    private const val PREF = "kumquat_session"
    private const val KEY_ACCOUNT = "current_account_id"
    private const val KEY_LAST_ASSET = "last_asset_id"

    private var prefs: SharedPreferences? = null
    private val _accountId = MutableStateFlow(0L)
    val accountId: StateFlow<Long> = _accountId.asStateFlow()

    private val _lastAssetId = MutableStateFlow(0L)

    /** 最近一次使用的资产账户，用于分类未绑定默认账户时的回退。 */
    val lastAssetId: StateFlow<Long> = _lastAssetId.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        _accountId.value = p.getLong(KEY_ACCOUNT, 0L)
        _lastAssetId.value = p.getLong(KEY_LAST_ASSET, 0L)
    }

    fun select(id: Long) {
        _accountId.value = id
        prefs?.edit()?.putLong(KEY_ACCOUNT, id)?.apply()
    }

    /** 记账保存或手动选账户后调用；不存在的 id 直接忽略。 */
    fun rememberAsset(id: Long?) {
        if (id == null || id <= 0L) return
        _lastAssetId.value = id
        prefs?.edit()?.putLong(KEY_LAST_ASSET, id)?.apply()
    }
}
