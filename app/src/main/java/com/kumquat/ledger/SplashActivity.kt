package com.kumquat.ledger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.kumquat.ledger.data.SessionStore
import com.kumquat.ledger.ui.screens.SplashContent
import com.kumquat.ledger.ui.theme.KumquatTheme
import kotlinx.coroutines.launch

/**
 * 开屏：Android 12 SplashScreen（米白）→ Lottie 播放一次 → 主页。
 * 数据库初始化（内置分类/默认账本）在开屏期间完成，避免主页闪现空态。
 */
class SplashActivity : ComponentActivity() {

    @Volatile
    private var dataReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !dataReady }
        super.onCreate(savedInstanceState)

        val repo = (application as KumquatApp).repository
        lifecycleScope.launch {
            val defaultId = repo.ensureSeeded()
            if (SessionStore.accountId.value <= 0L) SessionStore.select(defaultId)
            dataReady = true
        }

        setContent {
            KumquatTheme {
                SplashContent(onDone = ::goMain)
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
