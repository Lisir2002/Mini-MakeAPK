# 截图回归测试指南（Paparazzi / Roborazzi）

> 状态：已生效（2026-10-24 由 QA 评审）— 受 `../DOC_CHARTER.md` 约束，是 `PROJECT_BRIEF.md` §9「截图回归」质量闸门的自动化落地。
> 适用范围：Web2APK 纯安卓 Compose UI 的像素级视觉回归。
> 唯一事实来源：`../../PROJECT_BRIEF.md` · `../../DESIGN_LANGUAGE.md` · `../DOC_CHARTER.md`

## 1. 为什么需要截图回归

Sprint-1 已锁定「可爱简约·iOS 风」设计语言（单主色珊瑚粉、大圆角、圆体、弹簧动效）。纯靠人工评审无法在每次迭代中保证**像素不漂移**。截图回归用 baseline 图片做像素比对，把「视觉一致性」变成可自动化、可复现的闸门。

| 收益 | 说明 |
|------|------|
| 防令牌漂移 | 主色/圆角/间距任何无意改动立即被发现 |
| 防回归 | 重构主题或组件时不破坏已验收的视觉 |
| 无障碍间接守护 | 对比度/触控目标等可在截图评审中复核 |
| 文档即证据 | golden 图片随 PR 提交，评审可见前后差异 |

## 2. 选型：Paparazzi vs Roborazzi

| 维度 | Paparazzi | Roborazzi |
|------|-----------|-----------|
| 运行环境 | 纯 JVM（**无需模拟器**） | JVM + Robolectric |
| 速度 | 最快 | 较快（含 Robolectric 初始化） |
| Compose | ✅ 原生支持 | ✅ |
| 交互后截图 | ❌ 仅静态组合 | ✅ 点击/输入后截图 |
| WebView 真渲染 | ❌ | ❌（shadow 不渲染网页） |
| 本项目用途 | **首选**：首屏/组件/主题静态快照 | 备选：需交互态的截图 |

> 真实 WebView 渲染网页内容，二者都无法在 JVM 完成。完整网页视觉验证走**设备/模拟器插桩截图**（最权威），或人工评审。

## 3. 工程已就绪配置（无需重复搭建）

- `gradle/libs.versions.toml`：`paparazzi = "1.3.4"`、`agp = "8.5.2"`、`kotlin = "1.9.24"`、`composeCompiler = "1.5.14"`、`composeBom = "2024.09.00"`。
- `app/build.gradle.kts`：已 `alias(libs.plugins.paparazzi)`；`buildFeatures.compose = true`；`composeOptions.kotlinCompilerExtensionVersion` 对齐；`testOptions.unitTests.isIncludeAndroidResources = true`；`testImplementation(libs.paparazzi)`。
- Android SDK：`compileSdk = 34`，Paparazzi 运行需 `platforms;android-34` 框架桩（已从 `/root/Android/Sdk` 安装）。

## 4. 测试源码位置与现状

`app/src/test/java/com/web2apk/app/ui/`：

| 测试类 | 覆盖 | 基线张数 |
|--------|------|----------|
| `screens/HomeScreenTest.kt` | 首屏：亮色空态 / 暗色空态 / 亮色已填 URL | 3 |
| `components/CuteComponentsTest.kt` | 胶囊按钮（亮/暗）、柔和卡片、打包脉冲 | 4 |
| `screens/WebViewScreenTest.kt` | WebView 封装屏外壳（FrameLayout 桩） | 1 |

> `WebViewScreen` 通过 `webViewFactory: (Context) -> View` 注入轻量桩（默认 `WebView`），使 JVM 可渲染外壳；`HomeScreen` 通过 `initialInput` 参数支持预填态快照——二者均为最小可测性重构，未改变用户态行为。

## 5. 运行命令

```bash
# 前置：确保 ANDROID_SDK_ROOT 指向含 android-34 的 SDK
export ANDROID_SDK_ROOT=/root/Android/Sdk

# ① 首次运行（无基线）→ 记录并生成 golden，测试通过
./gradlew testDebugUnitTest --no-daemon

# ② UI 故意变更后，更新全部基线
./gradlew recordPaparazziDebug --no-daemon

# ③ CI 默认：仅比对已有基线，任一像素差异即失败
./gradlew verifyPaparazziDebug --no-daemon
```

基线图片位置：`app/src/test/snapshots/images/`。**golden 必须提交 Git**（是比对基准）。

## 6. 新增一个屏幕/组件时的标准动作

1. 在 `app/src/test/java/...` 对应包下新建 `XxxTest.kt`。
2. 至少覆盖「亮色 + 暗色」两张；有意义的输入态单独一张。
3. 本地先 `recordPaparazziDebug` 生成基线，目视确认截图符合设计语言。
4. 提交 `XxxTest.kt` + 生成的 `snapshots/images/*.png`。
5. PR 中 `verifyPaparazziDebug` 作为合并前闸门（见 `sprint-1-checklist.md`）。

## 7. CI 接入示例（GitHub Actions 片段）

```yaml
- name: Screenshot regression
  run: ./gradlew verifyPaparazziDebug --no-daemon
  env:
    ANDROID_SDK_ROOT: ${{ env.ANDROID_HOME }}
```

失败时在 Artifacts 下载 diff 图（Paparazzi 自动产出 `*-diff.png`）。

## 8. 常见排错

| 现象 | 原因/解法 |
|------|-----------|
| `Could not find platforms;android-34` | SDK 未装对应平台：`sdkmanager "platforms;android-34"` |
| 渲染全黑/空白 | 忘记 `isIncludeAndroidResources = true`，或 `theme` 未设置 |
| baseline 与实际总不一致（CI 抖动） | 固定 `deviceConfig`（分辨率/density/字体缩放），避免环境差异 |
| WebView 相关测试崩溃 | JVM 无法构造真实 WebView，改用 `webViewFactory` 注入桩（见 §4） |

## 9. 与质量闸门的关系

本指南是 `docs/qa/sprint-1-checklist.md` 的自动化补充：
- §1 设计一致性（令牌/圆角/字体）← 截图直接取证
- §2 安卓实现（主题绑定、单一输入流）← 首屏快照取证
- §3 可访问性 ← 截图可复核对比度/触控目标，辅以 `accessibility` skill

> 反模式：只写业务码不补截图测试 = 视觉回归缺口；故意改 UI 却不更新 baseline = 闸门失效。二者均禁止。
