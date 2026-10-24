# 参与贡献

欢迎提交 Issue 与 Pull Request。提交前请先阅读以下约定。

## 开发环境

- JDK 17
- Android SDK Platform 35 + Build-Tools 35
- 使用仓库自带的 Gradle Wrapper，不要求本机安装 Gradle

## 本地构建与测试

```bash
./gradlew testDebugUnitTest    # JVM 单元测试（统计、退款、迁移、导入校验）
./gradlew assembleDebug        # 调试包
./gradlew assembleRelease      # 发布包（R8 + 资源压缩）
```

Windows 使用 `gradlew.bat`。提交 PR 前请确保 `testDebugUnitTest` 通过，且 `assembleDebug` 可成功构建。

## 编码约定

- Kotlin 官方代码风格（`kotlin.code.style=official`），单模块、MVVM + Repository，不做过度分层。
- 金额一律使用 `Long` 整数分，禁止浮点参与金额计算。
- 颜色、间距、字号从 `ui/theme/` 的 Token 取，页面内不硬编码色值。
- 业务规则改动必须补充或更新 `app/src/test/` 下的单元测试。
- 数据库结构变更必须：递增 Room 版本、提供 `Migration`、导出新的 `app/schemas/*.json`、补充迁移测试，**禁止** `fallbackToDestructiveMigration`。
- 导入导出格式只允许新增可缺省字段，保持旧文件可导入。

## 提交与 PR

- 一个 PR 聚焦一件事，说明「改了什么、为什么、如何验证」。
- 提交信息建议使用 `feat:` / `fix:` / `refactor:` / `docs:` / `test:` / `chore:` 前缀。
- 不要在 PR 中提交 `local.properties`、签名文件、构建产物或任何密钥。
- 涉及 UI 的改动请附截图或录屏；涉及统计口径的改动请说明对既有数据的影响。

## 报告问题

- 功能缺陷：使用 Issue 模板说明版本、设备、复现步骤与预期结果。
- 安全漏洞：请勿公开提交，按 [SECURITY.md](SECURITY.md) 的方式私密报告。
