# 环境搭建（Setup）

> 状态：已生效（2026-10-24 由安卓工程师评审）
> 遵循：[`docs/DOC_CHARTER.md`](DOC_CHARTER.md) · 对齐：[`PROJECT_BRIEF.md`](../PROJECT_BRIEF.md)

本指南帮助新成员或 AI Agent 在本地把 Web2APK 跑起来。

---

## 1. 先决条件（Prerequisites）

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | 编译 Kotlin/Android |
| Android SDK | API 34+（Android 14） | 设备/模拟器运行 |
| Android Studio | 最新稳定版 | IDE / Gradle 同步 |
| Gradle | 由 wrapper 提供 | 构建（无需手动装） |
| Git | 任意较新 | 版本控制 |

## 2. 步骤

```bash
# 1. 克隆
git clone <your-repo-url> Web2APK && cd Web2APK

# 2. 配置 Android SDK
#    - 安装 Android Studio，通过 SDK Manager 安装 SDK Platform 34 + Build-Tools
#    - 设置环境变量（写入 ~/.bashrc 或 ~/.zshrc）
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

# 3. 用 Android Studio 打开 /workspace（识别 Gradle 工程，自动同步）
#    或命令行：
./gradlew :app:tasks   # 验证 Gradle 可用

# 4. 连接设备（USB 调试）或创建模拟器（API 34）
adb devices            # 确认设备在线

# 5. 运行
./gradlew :app:installDebug  # 安装到设备
# 或在 Android Studio 点 Run 'app'
```

## 3. 运行首屏

启动后进入 `HomeScreen`：粘贴一个 URL 或 HTML，点击珊瑚粉「一键封装」按钮，进入 `WebViewScreen` 预览。

## 4. 本沙箱说明（重要）

当前开发沙箱**已安装 Android SDK（compileSdk 34，位于 `/root/Android/Sdk`）**，项目已补齐 Gradle 工程并可编译验证：

- 工程基座：`settings.gradle.kts` / `app/build.gradle.kts` / `gradle/libs.versions.toml` / `gradle-wrapper`（Gradle 8.9）。
- 可运行 `./gradlew :app:tasks` 验证 Gradle 可用；依赖经 `google()` + `mavenCentral()` 直连拉取（已验证可达）。
- **截图回归**（Paparazzi，纯 JVM 无需模拟器）已实跑并生成 golden 基线，详见 [`qa/screenshot-testing.md`](qa/screenshot-testing.md)：

```bash
export ANDROID_SDK_ROOT=/root/Android/Sdk
./gradlew testDebugUnitTest --no-daemon        # 首次记录基线
./gradlew verifyPaparazziDebug --no-daemon     # CI：仅比对已有基线
./gradlew recordPaparazziDebug --no-daemon     # UI 故意变更后更新基线
```

- 联网拉取依赖若遇 DNS 死节点，使用 `github-sandbox-dns-bypass` skill 经 codeload/DoH 通路。

## 5. 验证清单

- [ ] `./gradlew :app:tasks` 成功列出任务
- [ ] `adb devices` 看到设备/模拟器
- [ ] `:app:installDebug` 成功，App 可启动
- [ ] 首屏输入 URL 后 WebView 正常渲染

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| `SDK location not found` | 设置 `ANDROID_HOME` 并重开终端 |
| `Could not resolve dependency` | 检查网络；DNS 死节点用 dns-bypass |
| 模拟器启动黑屏 | 开启 BIOS 虚拟化（VT-x/AMD-V） |
