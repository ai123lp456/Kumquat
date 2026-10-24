# 第三方组件与许可声明

本项目以 MIT 许可证发布（见 [LICENSE](LICENSE)）。下列第三方组件遵循各自的许可证，其版权归各自作者所有。本文件按**直接依赖**列出；传递依赖请以各组件发布物中的声明为准。

## 运行时依赖

| 组件 | 版本 | 许可证 | 版权 / 项目 |
|---|---|---|---|
| Kotlin Standard Library | 2.0.21 | Apache-2.0 | JetBrains |
| Kotlin Coroutines | 1.8.1 | Apache-2.0 | JetBrains |
| kotlinx.serialization | 1.7.3 | Apache-2.0 | JetBrains |
| AndroidX Core / Core Splashscreen / Activity Compose | 1.13.1 / 1.0.1 / 1.9.2 | Apache-2.0 | The Android Open Source Project |
| AndroidX Lifecycle | 2.8.6 | Apache-2.0 | The Android Open Source Project |
| AndroidX Navigation Compose | 2.8.3 | Apache-2.0 | The Android Open Source Project |
| Jetpack Compose（UI / Foundation / Material 3 / Material Icons Extended） | BOM 2024.10.00 | Apache-2.0 | The Android Open Source Project |
| Room | 2.6.1 | Apache-2.0 | The Android Open Source Project |
| Lottie for Android (Compose) | 6.4.0 | Apache-2.0 | Airbnb |

## 构建期依赖

| 组件 | 版本 | 许可证 |
|---|---|---|
| Android Gradle Plugin | 8.6.0 | Apache-2.0 |
| Kotlin Gradle Plugin | 2.0.21 | Apache-2.0 |
| KSP | 2.0.21-1.0.28 | Apache-2.0 |
| Gradle（含 Wrapper） | 8.7 | Apache-2.0 |

## 测试依赖

| 组件 | 版本 | 许可证 |
|---|---|---|
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| kotlinx-coroutines-test | 1.8.1 | Apache-2.0 |
| kotlinx-serialization-json | 1.7.3 | Apache-2.0 |
| sqlite-jdbc (Xerial) | 3.41.2.2 | Apache-2.0 |

## 素材资源

| 资源 | 位置 | 说明 |
|---|---|---|
| 开屏 Lottie 动画 `splash_kumquat.json` | `app/src/main/assets/` | 采用了公开共享的矢量动画资源，按「可自由使用」的方式引入；**原始作者与具体许可条款未在仓库中记录，待维护者补充确认** |
| 应用图标与开屏占位矢量图 | `app/src/main/res/drawable/`、`mipmap-anydpi-v26/` | 本项目自绘 |

## 说明与限制

- 上表的许可证归属依据各组件的公开发布声明整理，本次整理过程**未逐项解包核对每个依赖内的 LICENSE 文件**。
- 项目代码中不含从其他项目复制的源代码片段。
- 如需完整许可证文本，可在构建时通过 Gradle 的依赖报告或第三方许可插件导出。
- 若你认为某处归属有误或遗漏，请提交 Issue 或 PR 更正。
