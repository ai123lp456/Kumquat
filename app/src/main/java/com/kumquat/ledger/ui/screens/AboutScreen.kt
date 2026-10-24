package com.kumquat.ledger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumquat.ledger.BuildConfig
import com.kumquat.ledger.ui.components.GlassCard
import com.kumquat.ledger.ui.components.KmTopBar
import com.kumquat.ledger.ui.theme.Ink
import com.kumquat.ledger.ui.theme.InkMuted
import com.kumquat.ledger.ui.theme.LabelEn

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        KmTopBar(title = "关于", onBack = onBack)

        GlassCard(Modifier.fillMaxWidth()) {
            Text("小金桔 · kumquat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text("版本 ${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）", fontSize = 12.sp, color = InkMuted)
            Spacer(Modifier.height(10.dp))
            Text(
                "大学生个人记账：四类账目（入账/出账/回往/转账）、二级分类、日历、统计、资产账户、账本合并拆分、JSON 导入导出。全部数据保存在本机。",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("PRIVACY · 隐私", style = LabelEn)
            Spacer(Modifier.height(6.dp))
            Text(
                "无网络权限、无账号、无统计 SDK。导入导出通过系统文件选择器完成，应用不申请存储权限。",
                fontSize = 12.sp,
                color = Ink
            )
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("LICENSES · 开源许可", style = LabelEn)
            Spacer(Modifier.height(8.dp))
            LicenseRow("Kotlin / Coroutines", "Apache-2.0")
            LicenseRow("Jetpack Compose / Material 3", "Apache-2.0")
            LicenseRow("AndroidX Navigation / Lifecycle", "Apache-2.0")
            LicenseRow("Room", "Apache-2.0")
            LicenseRow("kotlinx.serialization", "Apache-2.0")
            LicenseRow("Lottie for Android (Airbnb)", "Apache-2.0")
            Spacer(Modifier.height(8.dp))
            Text(
                "开屏动画：splash_kumquat.json 为开源共享的矢量动画资源，可自由使用（不受本应用许可限制）。",
                fontSize = 11.sp,
                color = InkMuted
            )
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("UI · Y2K CHROME BUBBLEGUM", style = LabelEn)
            Spacer(Modifier.height(6.dp))
            Text(
                "视觉规范：铬合金光泽、泡泡糖渐变、塑料立体感；金额统一黑色高对比，语义色只做点/条/标签；不做柱状图与饼图。",
                fontSize = 12.sp,
                color = Ink
            )
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun LicenseRow(name: String, license: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(name, fontSize = 12.sp, color = Ink, modifier = Modifier.weight(1f))
        Text(license, fontSize = 11.sp, color = InkMuted)
    }
}
