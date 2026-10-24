# MMA（Mini Make APK）

> 状态：已生效（2026-10-25 由 Producer 评审）— 遵循 `docs/DOC_CHARTER.md`，对齐 `PROJECT_BRIEF.md` / `DESIGN_LANGUAGE.md`
> 纯安卓 App：把网页 / 前端代码（HTML、PHP、Vue、URL）一键打包生成可安装的 Android APK。
> 全年龄段可用，核心体验是「舒适」——可爱简约的 iOS 风格、色彩单调但不失可爱美观。

| 项 | 值 |
|----|-----|
| **应用名称** | `MMA` |
| **应用包名** | `com.make.apk` |
| **当前版本** | `0.0.0.0+1`（四段式 + 构建号，规范见 [`docs/VERSIONING.md`](docs/VERSIONING.md)） |
| **成品产出** | **只在 GitHub 打 tag 时产出**；本地仅做验证（见 [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)） |

![platform](https://img.shields.io/badge/platform-Android-3DDC84)
![ui](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20M3-FF6F61)
![package](https://img.shields.io/badge/package-com.make.apk-blueviolet)
![version](https://img.shields.io/badge/version-0.0.0.0%2B1-blue)
![license](https://img.shields.io/badge/license-专有/内部-orange)

---

## 为什么需要它（Why）

很多人/小团队手里有一段网页或前端代码（HTML 单页、Vue 项目、甚至一个 PHP 站点），但**不会配 Android 工程、不知道怎么出包**。MMA 把这事变成「粘贴代码 / 填网址 → 点一下 → 拿到 APK」，把打包复杂度藏到后台，前台只留最舒服的一步。

## 它是什么（What）

| 项 | 说明 |
|----|------|
| 形态 | 纯原生安卓应用（非跨端框架） |
| UI 技术栈 | Jetpack Compose + Material 3（已按设计语言改造） |
| 构建策略 | **混合路径**：sprint-1 走设备端 WebView 即时封装（URL/HTML 直接出包，零后端）；Vue/PHP/正式签名走**云端真构建**（Phase 2） |
| 设计语言 | 可爱简约 · iOS 风 · 色彩单调，详见 [`DESIGN_LANGUAGE.md`](DESIGN_LANGUAGE.md) |
| 团队宪法 | 产品/架构/角色决策见 [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) |

## 快速开始（Quick Start）

> 当前为 **0.0.0.0+1 原型阶段**。本地**只做验证**，成品 APK 从 GitHub Release 获取
> （见 [`docs/VERSIONING.md`](docs/VERSIONING.md)、[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)）。

```bash
# 1. 克隆仓库
git clone https://github.com/Lisir2002/Mini-MakeAPK.git MMA && cd MMA

# 2. 本地验证（不产出成品）
./gradlew testDebugUnitTest      # 数据层 + 逻辑单测
./gradlew verifyPaparazziDebug   # 截图回归，比对已提交基线

# 3. 成品：从 GitHub Release 下载（推送 v* tag 后自动构建）
#    https://github.com/Lisir2002/Mini-MakeAPK/releases
```

环境搭建（Android SDK 等）见 [`docs/SETUP.md`](docs/SETUP.md)。

## 功能特性（Features）

- **单一输入流**：URL、HTML 字符串、上传文件共用一个输入框，降低认知负担。
- **即时预览**：设备端 WebView 直接渲染，所见即所得。
- **一键封装**：URL/HTML 走 WebView 即时封装，秒级出包（sprint-1）。
- **可爱 UI**：珊瑚粉主色、圆润卡片、圆体字、弹簧回弹动效（对齐设计语言）。
- **亮/暗双主题**：基于 OKLCH 感知均匀色板，自动跟随系统。

## 项目结构（Project Structure）

```
MMA/  （仓库 Mini-MakeAPK）
├── README.md                  # 你正在看的文件（入口）
├── PROJECT_BRIEF.md           # 团队宪法：产品/架构/角色
├── DESIGN_LANGUAGE.md         # 设计语言：令牌/风格/反模式
├── CHANGELOG.md               # 版本日志（Keep a Changelog + 四段式版本号）
├── CONTRIBUTING.md            # 贡献指南
├── .github/workflows/         # release.yml（打 tag 出成品）/ verify.yml（PR 验证）
├── docs/
│   ├── DOC_CHARTER.md         # 文档规矩（本仓所有文档的总纲）
│   ├── INDEX.md               # 文档地图
│   ├── ARCHITECTURE.md        # 系统架构
│   ├── VERSIONING.md          # 版本规范（四段式 0.0.0.0+1）
│   ├── AGENT_COLLABORATION.md # AI Agent 协同协议
│   ├── CODE_STYLE.md          # 代码与提交规范
│   ├── SETUP.md               # 环境搭建
│   ├── DEPLOYMENT.md          # 构建与发布
│   ├── data-layer.md          # 数据层：Room + SQLCipher
│   ├── build-api-contract.md  # 云端构建 API 契约
│   ├── brainstorm/            # 决策记录
│   └── sprint-1/              # 迭代计划与进度
└── app/src/main/java/com/make/apk/
    ├── MmaApplication.kt      # 应用入口（进程级单例）
    ├── data/                  # 数据层：model / local / security / repository
    └── ui/
        ├── theme/             # 设计令牌（OKLCH→Compose）
        ├── components/        # 可爱组件
        ├── screens/           # 首屏 / WebView 屏
        └── MmaApp.kt          # 应用根
```

## 文档导航

| 你想了解 | 看这里 |
|----------|--------|
| 产品定位 / 架构决策 / 团队角色 | [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) |
| 颜色 / 字体 / 圆角 / 动效规范 | [`DESIGN_LANGUAGE.md`](DESIGN_LANGUAGE.md) |
| 系统架构与数据流 | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| AI Agent 如何协作 | [`docs/AGENT_COLLABORATION.md`](docs/AGENT_COLLABORATION.md) |
| 怎么搭环境 / 怎么发版 | [`docs/SETUP.md`](docs/SETUP.md) · [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) |
| 版本号怎么递增 / tag 怎么打 | [`docs/VERSIONING.md`](docs/VERSIONING.md) |
| 数据层（加密 SQLite） | [`docs/data-layer.md`](docs/data-layer.md) |
| 全部文档地图 | [`docs/INDEX.md`](docs/INDEX.md) |

## 贡献（Contributing）

欢迎人类与 AI Agent 共同贡献。开发规范、提交规范、PR 流程见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。
所有文档受 [`docs/DOC_CHARTER.md`](docs/DOC_CHARTER.md) 约束。

## 版本与许可

- 版本规范：四段式 `MAJOR.MINOR.PATCH.BUILD+构建号`（当前 `0.0.0.0+1`），见 [`docs/VERSIONING.md`](docs/VERSIONING.md)。
- 版本日志：[`CHANGELOG.md`](CHANGELOG.md)（遵循 Keep a Changelog）。
- 许可：当前为**专有 / 内部项目**，开源许可待定。如需使用请先联系维护者。

---

*本 README 受 `docs/DOC_CHARTER.md` 与 `PROJECT_BRIEF.md` 约束，内容随产品演进更新。*
