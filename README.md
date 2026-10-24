# 小金桔 Kumquat

一款面向个人（尤其是大学生）的 Android 本地记账应用。四类账目（入账 / 出账 / 回往 / 转账）、二级分类、日历、统计、资产账户、账本合并拆分，以及可回导的 JSON 导入导出。**数据全部保存在本机，无网络权限、无账号、无统计 SDK。**

当前版本：**v1.1.1**（versionCode 3）· 包名 `com.kumquat.ledger` · 最低 Android 12（API 31）

## 功能

| 模块 | 内容 |
|---|---|
| 开屏 | Lottie 矢量动画，播放一次进入主页；动画缺失时自动降级，3 秒硬超时兜底 |
| 首页 | 账本切换、本月净收支概览、退款抵扣提示、按日分组的账单列表、类型筛选、长按删除 |
| 记一笔 | 入账 / 出账 / 回往 / 转账四类；回往 6 个方向（全额退款、部分退款、代付、借出、收回、还款）；退款关联原出账；底部常驻保存栏；分类末尾可直接新建 |
| 账单 / 日历 | 按日小计、当日列表、点击编辑；月视图、左右滑动切月、语义色圆点、点日期进当日账单 |
| 统计 | 本月 / 上月 / 自定义区间，收支汇总，分类排行（回往按净额单列），回往明细页可按方向筛选 |
| 资产 | 账户增删改、初始余额与日期、实时余额、软删除保留历史引用；账户间转账不计入收支 |
| 账本 | 创建 / 切换 / 改名 / 删除、纯色或渐变封面、合并、按时间或分类拆分（Room 事务，失败回滚） |
| 导入导出 | 严格可回导的 JSON；粘贴或选择文件；导入前校验预览（条数、账本、分类、资产、合计、错误、警告）；可生成给 AI 用的导入格式说明 |
| 分类 | 25 个内置图标；入账 / 出账分类可增删改；删除分类时账单移入「未分类」；可绑定默认资产账户 |
| 关于 | 版本、隐私说明、开源许可列表 |

## 架构

单模块 Kotlin + Jetpack Compose，MVVM + Repository，数据层用 Room（KSP）。

```text
app/src/main/java/com/kumquat/ledger/
├── data/        Room 实体与 DAO、迁移、仓库、JSON 编解码、导入校验、会话与提示词
├── domain/      业务规则：TxType、Money（整数分）、HuiWang（回往方向）、
│                RefundRules、StatsEngine、AssetLedger、LedgerMath
├── ui/          theme（设计 Token）、components（玻璃卡片 / 胶囊 / 键盘 / 图标库）、
│                screens（各页面）、nav（路由）
├── util/        日期工具
├── KumquatApp.kt / MainActivity.kt / SplashActivity.kt
```

UI → ViewModel（Compose state）→ Repository → Room；所有写操作在 Room 事务内完成，账本合并 / 拆分 / 删除失败整体回滚。导入流程为「解析 → 纯 JVM 校验 → 预览 → 事务写入」，校验不通过不写任何数据。

技术栈：

| 组件 | 版本 |
|---|---|
| Kotlin | 2.0.21 |
| AGP / Gradle | 8.6.0 / 8.7（Wrapper） |
| Compose BOM | 2024.10.00（Material 3、Navigation Compose 2.8.3） |
| Room | 2.6.1（KSP） |
| kotlinx.serialization | 1.7.3 |
| Lottie Compose | 6.4.0 |
| JDK | 17 |

## 环境要求

- JDK 17（`JAVA_HOME` 指向 JDK 17）
- Android SDK Platform 35 + Build-Tools 35（通过 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir` 指定）
- Gradle 使用仓库自带的 Wrapper，无需单独安装

## 快速开始

1. 在 [Releases](../../releases) 下载最新的 `Kumquat-v1.1.1.apk`。
2. 在 Android 12 及以上设备上安装（需要允许「安装未知来源应用」）。
3. 首次启动自动创建默认账本与内置分类，直接记一笔即可。
4. 换机或备份：在「我的 → 导入导出」中导出 JSON，到新设备导入。

## 从源码构建

```bash
# Linux / macOS
./gradlew assembleDebug          # 调试包：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # 发布包：app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # JVM 单元测试
```

```powershell
# Windows
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat testDebugUnitTest
```

说明：

- **签名**：仓库中的 `release` 构建类型当前复用 debug 签名，便于本地安装验收。自行发布前请配置正式签名，并删除 `app/build.gradle.kts` 中的 `signingConfig = signingConfigs.getByName("debug")`。
- **非 ASCII 路径**：`gradle.properties` 已设置 `android.overridePathCheck=true`，因此工程可以放在含中文的目录下；同时未强制 `sun.jnu.encoding`，以免 Windows GBK 环境下测试类找不到。
- **`local.properties`** 是本机配置，不提交，由 Android Studio 自动生成或手写 `sdk.dir=...`。
- 依赖仓库在 `settings.gradle.kts` 中同时配置了阿里云镜像与 Google / Maven Central，镜像不可用时会回退到官方源。
- **Gradle 下载源**：`gradle/wrapper/gradle-wrapper.properties` 指向腾讯云镜像（国内下载更快）。如无法访问，可改为官方地址 `https://services.gradle.org/distributions/gradle-8.7-bin.zip`，版本号保持一致即可。

## 数据与配置

- 数据库：应用私有目录下的 `kumquat.db`（Room，schema v3）。
- 升级路径：v1 → v2 → v3，仅新增可空列与索引，**不使用**破坏性迁移；旧数据保留。
- 导出文件：用户通过系统文件选择器（SAF）选择保存位置，应用不申请存储权限。
- Android 自动备份：`allowBackup=true`，备份范围包含数据库与 SharedPreferences，由系统备份服务托管到用户自己的账号；不希望参与备份的用户可在系统设置中关闭该应用的备份。
- 无任何联网配置项、账号体系或云同步。

## 导入导出格式

JSON 顶层字段：

```json
{
  "version": 1,
  "exportedAt": "2026-10-22T12:00:00",
  "accounts": [{ "name": "日常", "coverType": "color", "coverValue": "#FFD1E8" }],
  "assets": [{ "name": "微信", "initialBalanceCents": 0, "initialDate": "2026-10-01", "deleted": false }],
  "categories": [{ "type": "EXPENSE", "level": 1, "name": "饮食", "icon": "restaurant", "defaultAssetName": null }],
  "transactions": [
    {
      "accountName": "日常",
      "type": "EXPENSE",
      "categoryL1": "饮食",
      "categoryL2": "正餐",
      "amountCents": 1500,
      "date": "2026-10-20",
      "note": "午饭",
      "linkedIndex": null,
      "assetName": "微信",
      "toAssetName": null,
      "huiwangSubtype": null
    }
  ]
}
```

- 金额一律为整数分（`amountCents`），避免浮点误差；日期为 `uuuu-MM-dd` 严格格式。
- `type` 取值：`INCOME` / `EXPENSE` / `REFUND` / `EXCHANGE` / `TRANSFER`。
- `linkedIndex` 是同一文件内 `transactions` 的下标，指向被关联的原出账（退款场景），导入时重映射为数据库 ID。
- 兼容策略：只新增可缺省字段，`version` 保持 1；旧文件缺少新字段按空处理，新文件中的未知字段在旧版本被忽略。

## 统计口径

- 金额用 `Long` 存整数分，展示时转元。
- 退款冲减原支出：原账单净额 = 原金额 − 已关联退款合计（最低 0）；退款不计入收入，也不参与回往净额。
- 回往净额 =（收回 + 还款）−（代付 + 借出）。
- 回往净额为正计入总收入，为负时绝对值计入总支出，为 0 不计入。
- 跨月退款归属原账单的日期与分类，退款账户余额在退款当天增加。
- 分类排行使用净额；净额为 0 时从排行隐藏，账单列表仍保留并显示退款标记。
- 未关联的退款标记「待关联」、无法判断方向的旧往来标记「待确认」，均不参与统计。
- 内部转账不计收支，只改变两个账户余额。
- 资产余额 = 初始余额 + 入账 − 出账 + 退款 +（收回 / 还款）−（代付 / 借出）± 转账。

## 安全模型与隐私边界

- 应用不声明 `INTERNET` 权限，不存在网络请求、埋点或远程配置。
- 无账号、无登录，数据不离开设备；导出由用户主动触发。
- 导入前做完整校验并展示预览，校验失败不写入。
- 已知边界：
  - 数据库为应用私有目录中的明文 SQLite，未额外加密；已 root 的设备或系统备份可能读取。
  - 仓库中的 `release` 构建类型使用 debug 签名，**不可直接用于商店发布**。
  - 依赖与自身代码均未做混淆审计之外的加固；如需更强防护请自行处理。

漏洞报告方式见 [SECURITY.md](SECURITY.md)。

## 已知限制

- 仅支持 Android 12+，仅中文界面，无深色模式。
- 分类为两级结构；资产账户是全局账户，不按账本隔离。
- 旧版本中无法判断方向的「借入 / 其他往来」记录标记为「待确认」，需在界面中手动补选方向，应用不做猜测。
- 迁移测试在 JVM 的真实 SQLite 上执行与线上相同的 SQL，尚未在真机上用 `MigrationTestHelper` 覆盖；升级前建议先导出备份。
- 历史性能数据基于单一中端机型，未做系统性性能基准测试。

## 目录结构

```text
.
├── app/
│   ├── build.gradle.kts              # 模块配置（依赖、版本号、R8）
│   ├── proguard-rules.pro            # R8 规则（序列化 / Room / Lottie）
│   ├── schemas/                      # Room 导出的结构（2.json、3.json）
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/splash_kumquat.json
│       │   ├── java/com/kumquat/ledger/
│       │   └── res/
│       └── test/java/com/kumquat/ledger/   # 统计 / 退款 / 迁移 / 导入单测
├── gradle/wrapper/                   # Gradle Wrapper（含 jar）
├── build.gradle.kts                  # 根构建脚本
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── LICENSE
├── CHANGELOG.md
├── SECURITY.md
├── THIRD_PARTY_NOTICES.md
└── CONTRIBUTING.md
```

## 第三方组件

详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。主要依赖：AndroidX / Jetpack Compose / Room / Navigation（Apache-2.0）、Kotlin 与 kotlinx 系列（Apache-2.0）、Lottie for Android（Apache-2.0）、Gradle（Apache-2.0）。

## 许可证

本项目以 [MIT](LICENSE) 许可证发布，Copyright (c) 2026 ai123lp456。第三方组件遵循各自的许可证。

版本变化见 [CHANGELOG.md](CHANGELOG.md)；参与开发见 [CONTRIBUTING.md](CONTRIBUTING.md)。
