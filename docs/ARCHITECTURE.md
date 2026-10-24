# 系统架构（Architecture）

> 状态：已生效（2026-10-24 由安卓工程师评审）
> 遵循：[`docs/DOC_CHARTER.md`](DOC_CHARTER.md) · 对齐：[`PROJECT_BRIEF.md`](../PROJECT_BRIEF.md) §2 · [`DESIGN_LANGUAGE.md`](../DESIGN_LANGUAGE.md)

---

## 1. 总览

Web2APK 是一款**纯原生安卓应用**（Jetpack Compose + Material 3），核心能力是把网页/前端代码一键变成 APK。设计要点：

- **混合构建路径**：浏览器端能做的（URL/HTML 渲染与封装）在**设备端 WebView 即时完成**；需要真编译/真签名的（Vue 工程、PHP 站点、正式发布包）交给**云端构建服务**。
- **前端优先体验**：首屏极简，所有复杂度藏在「封装」这一步之后。
- **设计语言内建**：主题系统直接消费 `DESIGN_LANGUAGE.md` 的 OKLCH 令牌，UI 与安卓层共用唯一色板。

```mermaid
flowchart LR
    U[用户输入\nURL/HTML/文件] --> H[HomeScreen\n单一输入流]
    H --> V[WebViewScreen\n本地预览/封装]
    V -->|设备端即时封装| APK1[(WebView 封装 APK)]
    V -->|需真编译| C[云端构建服务\nPOST /build]
    C -->|轮询状态| APK2[(真构建 APK)]
    APK1 --> I[安装/分享]
    APK2 --> I
```

## 2. 模块划分

| 模块 | 路径 | 职责 |
|------|------|------|
| `ui/theme` | `app/.../ui/theme/` | 设计令牌（OKLCH→Compose）、主题、动效 |
| `ui/components` | `app/.../ui/components/` | 可爱组件：胶囊按钮、柔卡、打包脉冲 |
| `ui/screens` | `app/.../ui/screens/` | HomeScreen（输入）、BuildingScreen（进度）、ResultScreen（成功/失败）、HistoryScreen（历史）、PreviewScreen（预览） |
| `ui/navigation` | `app/.../ui/navigation/` | 页面骨架：路由清单 `MmaRoute` + 导航宿主 `MmaNavHost` |
| `ui/viewmodel` | `app/.../ui/viewmodel/` | `BuildViewModel`（构建流程）、`HistoryViewModel`（历史）；手工 DI 工厂 |
| `MainActivity` | `app/.../MainActivity.kt` | 唯一载体 Activity：`setContent` + `reportFullyDrawn()`（启动终点） |
| `ui/MmaApp` | `app/.../ui/MmaApp.kt` | 应用根：主题绑定 + 持有 NavController |
| `data/model` | `app/.../data/model/` | 领域模型 + 枚举（无持久化注解，UI 唯一可见） |
| `data/local` | `app/.../data/local/` | SQLite 持久化：实体、DAO、AppDatabase、类型转换器、SQLCipher 工厂 |
| `data/security` | `app/.../data/security/` | 数据库口令经 Android Keystore 加密存储 |
| `data/repository` | `app/.../data/repository/` | 构建历史仓储：上层唯一出口 |
| `build` (Phase 2) | 云端 | 真编译/签名/出包，契约见 [`build-api-contract.md`](build-api-contract.md) |

> 数据层细节（选型实证、加密方案、性能实践、迁移策略）见 [`data-layer.md`](data-layer.md)。

## 3. 关键架构决策（ADR 摘要）

1. **设备端 WebView 即时封装（sprint-1）**：URL/HTML 无需后端即可出包，零成本可演示，快速验证核心价值。
2. **云端真构建（Phase 2）**：Vue/PHP/正式签名需要 Android 工具链，放云端避免客户端体积与复杂度爆炸。
3. **Material 3 改造而非自定义体系**：复用 M3 的 `ColorScheme`/`Typography`/`Shape`，仅替换令牌值，兼顾无障碍与一致性。
4. **设计令牌单一来源**：色值只在 `DESIGN_LANGUAGE.md` 定义，Compose 侧是**派生**而非**复制**。
5. **SQLite 采用 Room + SQLCipher**：SQLCipher 自带 `SupportOpenHelperFactory`（实现 `androidx.sqlite.db` 接口）可**直连** Room 2.x，无需手写桥接；数据库全程加密，口令存于 Keystore。（对比评估过的 WCDB：其官方适配层仅实现旧版 `android.arch.persistence` 接口，与 AndroidX Room 不兼容，已否决。）

> 完整决策记录见 [`brainstorm/`](brainstorm/)。

## 3.1 页面骨架与导航

```
MainActivity ─ setContent ─▶ MmaApp（主题 + NavController）
                                └─▶ MmaNavHost
        home ────────┬──▶ history ──▶ preview
                     └──▶ building/{id} ──▶ result/{id} ──▶ preview/{id}
```

| 路由 | 页面 | 取数来源 |
|------|------|---------|
| `home` | HomeScreen | —（用户输入） |
| `building/{recordId}` | BuildingScreen | `BuildHistoryRepository.observeById` |
| `result/{recordId}` | ResultScreen | `BuildHistoryRepository.observeById` |
| `history` | HistoryScreen | `BuildHistoryRepository.observeAll`（Flow） |
| `preview/{recordId}` | PreviewScreen | `BuildHistoryRepository.observeById` |

**分层约定：路由负责取数，页面负责渲染。**
所有页面都是**无状态** Composable（参数进、事件出），
因此 Paparazzi 可以直接渲染页面做截图回归，不需要数据库与导航环境。

**单一数据源是数据库而非内存**：构建一经登记即落加密库，
"构建中"页面被杀后重建仍能读到真实状态，不会出现假进度或转圈卡死。

## 4. 数据流

```mermaid
sequenceDiagram
    participant U as 用户
    participant H as HomeScreen
    participant V as WebViewScreen
    participant W as WebView 引擎
    participant C as 云端构建(Phase2)
    U->>H: 输入 URL / HTML / 文件
    H->>V: 传递输入源
    V->>W: loadUrl / loadData
    W-->>V: 渲染完成
    V->>U: 即时预览
    opt 需真编译
        V->>C: POST /build (源码包)
        C-->>V: 轮询 buildId 状态
        C-->>V: 下载 APK
    end
```

## 5. 技术选型

| 关注点 | 选型 | 理由 |
|--------|------|------|
| UI 框架 | Jetpack Compose | 声明式、与 M3 深度整合 |
| 设计系统 | Material 3（改造） | 无障碍、主题、组件成熟 |
| 颜色空间 | OKLCH 定义 → sRGB hex | 感知均匀，亮暗主题一致 |
| 封装（sprint-1） | WebView + 本地打包 | 零后端、即时 |
| 构建（Phase 2） | 云端 Android 构建 | 真编译/签名，客户端轻量 |
| 文档 | Docs-as-Code（本仓） | 同仓版本化，不漂移 |

## 6. 已知限制（sprint-1）

- 设备端 WebView 封装产出的 APK 不含真编译优化，仅适合预览/轻量分发。
- 云端构建服务尚未落地，Phase 2 起接入（契约已定）。
- 本沙箱无 Android SDK，源码以可评审形式交付，待 Android Studio 编译。
