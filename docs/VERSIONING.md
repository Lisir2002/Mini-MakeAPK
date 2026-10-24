# 版本规范（Versioning）

> 状态：已生效（2026-10-25）· 受 `docs/DOC_CHARTER.md` 约束
> 本文件是**版本号与发版流程的唯一事实来源**。改版本前必读。

---

## 1. 版本格式

```
MAJOR.MINOR.PATCH.BUILD + N
└──── versionName ────┘   └ versionCode
      （四段式，字符串）      （构建号，整数）
```

- **完整版本串**：`0.0.0.0+1`
- **`versionName`**（`app/build.gradle.kts`）：四段式 `MAJOR.MINOR.PATCH.BUILD`，如 `0.0.0.0`
- **`versionCode`**（同文件）：构建号 `N`，**单调递增、永不重置**（Android 强制要求）
- **发布 tag**：`v<versionName>+<versionCode>`，如 **`v0.0.0.0+1`**

当前：`versionName = "0.0.0.0"`，`versionCode = 1` → tag `v0.0.0.0+1`。

---

## 2. 各段递增规则

| 段 | 何时递增 | 归零规则 | 示例 |
|----|---------|---------|------|
| **MAJOR** | 不兼容的产品级变更 / 重大架构切换（如主路径改为云端构建、包名或数据格式破坏性变更） | 递增时 MINOR/PATCH/BUILD 归零 | `1.0.0.0` |
| **MINOR** | 新增可感知功能，向下兼容（如新增构建历史页、新增引擎） | 递增时 PATCH/BUILD 归零 | `0.1.0.0` |
| **PATCH** | 缺陷修复、安全补丁、依赖升级、性能修复 | 递增时 BUILD 归零 | `0.0.1.0` |
| **BUILD** | 同一 PATCH 内的迭代（UI 调整、文案、动效、内部重构） | 同 PATCH 内累加 | `0.0.0.1` |
| **+N**（versionCode） | **每次发版必增**，无论哪一段变化 | **永不归零、永不回退** | `+2` |

> **硬性约束**：`versionCode` 只能增大。Android 与各大商店以它判定新旧，
> 回退会导致用户无法升级。即便回滚功能，也要靠 BUILD/PATCH 前进而非回退 N。

---

## 3. 发版流程（成品只在 GitHub 产出）

```
① 改版本 ── app/build.gradle.kts（versionCode +1，按需递增四段）
      │
② 更新 CHANGELOG.md ── [Unreleased] 晋级为 [0.0.0.0+N] - YYYY-MM-DD
      │
③ 提交并推送到 main ── 本地仅运行验证（见 §4）
      │
④ 打 tag 并推送 ── git tag v0.0.0.0+1 && git push origin v0.0.0.0+1
      │
⑤ GitHub Actions 产出成品 ── .github/workflows/release.yml
      └─ 校验 tag 与版本一致 → 构建 APK → 创建 Release 并上传
```

**tag 名必须与版本严格一致**，由 CI 强制校验（不一致直接失败）：

```bash
git tag "v$(versionName)+$(versionCode)"     # 例：v0.0.0.0+1
git push origin "v$(versionName)+$(versionCode)"
```

> 注：`+` 在 Git tag 名中是合法字符，GitHub Release 亦支持。

---

## 4. 本地职责：只验证，不产出成品

**本地不做编译成品**。APK/AAB 这类交付物**只由 GitHub 在打 tag 时产出**
（见 `.github/workflows/release.yml`）。本地工作流仅限验证：

```bash
./gradlew testDebugUnitTest      # 数据层 + 逻辑单测
./gradlew verifyPaparazziDebug   # 截图回归：比对已提交基线，0 差异即通过
```

禁止把本地 `app/build/outputs/` 下的产物当作交付物（已被 `.gitignore` 排除）。

需要更新截图基线时（**UI 有意变更后**）：

```bash
./gradlew recordPaparazziDebug   # 重新生成基线 → 随代码提交
```

---

## 5. 与 SemVer / Keep a Changelog 的关系

- 本项目采用**四段式**而非标准三段 SemVer，多出的 `BUILD` 段用于
  同一 PATCH 内的短周期迭代，避免 PATCH 号在无 bug 修复时被无意义地抬高。
- `MAJOR`/`MINOR`/`PATCH` 三段的**语义与 SemVer 一致**，`+N` 承载构建号
  （语义上接近 SemVer 的 build metadata，但此处同时作为 Android `versionCode`）。
- `CHANGELOG.md` 仍遵循 Keep a Changelog：版本号写作 `[0.0.0.0+1] - YYYY-MM-DD`，
  发版时 `[Unreleased]` 整体晋级并保留新的空 `[Unreleased]`。

---

## 6. 校验清单（打 tag 前）

- [ ] `app/build.gradle.kts` 中 `versionCode` 已 +1（且大于历史上任何一次）
- [ ] 四段式 `versionName` 按 §2 规则正确递增
- [ ] `CHANGELOG.md` 已把 `[Unreleased]` 晋级为 `[x.y.z.w+N] - 日期`
- [ ] 本地 `testDebugUnitTest` + `verifyPaparazziDebug` 全绿
- [ ] tag 名 `v<x.y.z.w>+<N>` 与 `versionName`/`versionCode` 完全一致

---

## 7. 相关文档

| 文档 | 说明 |
|------|------|
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | 发版操作步骤与签名说明 |
| [`../CHANGELOG.md`](../CHANGELOG.md) | 版本日志 |
| [`qa/screenshot-testing.md`](qa/screenshot-testing.md) | 截图基线的记录与比对 |
| `.github/workflows/release.yml` | 打 tag 后产出成品的工作流 |
