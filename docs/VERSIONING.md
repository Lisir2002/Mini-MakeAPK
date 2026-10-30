# 版本规范（Versioning）

> 状态：已生效（2026-10-25 修订）· 受 `docs/DOC_CHARTER.md` 约束
> 本文件是**版本号与发版流程的唯一事实来源**。改版本前必读。

---

## 1. 版本格式

```
MAJOR.MINOR.PATCH.BUILD[-rcN]
└──── versionName ────┘
      （四段式，字符串）
```

- **版本号 = 纯四段式**，如 `0.0.0.1`；未获正式版授权时可带预发布后缀 `-rcN`，如 `0.0.0.1-rc1`。
- **`versionName`**（`app/build.gradle.kts`）：即上述版本号，是版本的唯一对外身份。
- **`versionCode`**（同文件）：Android 安装唯一性要求的内部整数，**单调递增、永不重置**，
  但**不进入版本串、tag、CHANGELOG**——它只是构建文件里的一个计数器。
- **发布 tag**：`v<versionName>`，如 **`v0.0.0.1-rc1`**、`v0.0.0.1`。

> **2026-10-25 起永久去除 `+N` 后缀**（用户确认：「后续都不要 +N 这个格式」）。
> 历史版本 `v0.0.0.0+1` 中的 `+1` 即当时的 versionCode 展示方式，该 tag 与其
> Release/CHANGELOG 记录保持原样作为史实，新版本一律不带。

当前（即将发布）：`versionName = "0.0.0.1-rc12"`，`versionCode = 15` → tag `v0.0.0.1-rc12`。

---

## 2. 递增规则（默认顺延 BUILD 段，禁止语义化跳段）

> 用户确认的规则原文：「上一个版本是 `0.0.0.3`，在没有特殊情况下顺延升版应该是 `0.0.0.4`」。

| 情形 | 规则 | 示例 |
|------|------|------|
| **正式版顺延（默认）** | BUILD 段 **+1**，前三段不动 | `0.0.0.3` → `0.0.0.4` |
| 前三段递增（特殊情况） | 仅限产品级节点，且**必须由用户明确指定**；递增时后级段归零 | 首个产品版 `1.0.0.0` |
| **`versionCode`（内部）** | **每次发版必 +1**（RC 也算一次发版），仅存在于 `build.gradle.kts` | `1` → `2` → `3` |

- **无特殊情况一律顺延 BUILD 段**。缺陷修复、依赖升级、性能优化、UI 调整**不**升
  PATCH/MINOR——那些只是普通顺延。MAJOR/MINOR/PATCH 的语义化跳段在本项目**默认禁用**。
- **硬性约束**：`versionCode` 只能增大。Android 以它判定新旧，回退会导致用户无法升级。
  即便回滚功能，也要顺延 BUILD 段发新版本，而非回退 versionCode。

---

## 3. 发版授权与 RC 机制（核心规则）

> 用户确认的规则原文：「正式版发版需要用户确认或者提前通知，否则按照顺延下一个版本＋rc，
> 就比如上一个版本 `0.0.0.7`，那用户没有批准正式版的时候，应该发 `0.0.0.8-rc1`，
> 顺延下去就是 `0.0.0.8-rc2`。正式版一定需要用户授权再升版。」

### 3.1 授权闸门

- **正式版（不带 `-rcN` 的版本）必须取得用户授权**——用户明确确认，或已提前通知并获认可。
- 未获授权时**不得**发正式版，唯一合规动作是发 **RC**。

### 3.2 RC（Release Candidate）

- RC 基版本 = 上一正式版顺延下一版（BUILD +1），后缀 `-rcN` 从 1 起编。
- 同一基版本的 RC 可顺延：`0.0.0.8-rc1` → `0.0.0.8-rc2` → …（N 递增，基版本不变）。
- 每个 RC 都是一次发版：`versionCode` 同样 +1，tag 同样强校验，CI 同样产出签名成品。
- **RC 获批**：去掉 `-rcN` 后缀成为正式版（`0.0.0.8-rc2` → `0.0.0.8`），
  `versionCode` 再 +1，CHANGELOG 以正式版号记录该版本的全部变更。

### 3.3 版本时间线示例

```
0.0.0.7        ← 上一正式版（tag v0.0.0.7）
0.0.0.8-rc1    ← 未授权，发 RC（BUILD 顺延 + rc1）
0.0.0.8-rc2    ← RC 顺延
0.0.0.8        ← 用户授权：去掉 -rc，成为正式版
```

---

## 4. 发版流程（成品只在 GitHub 产出）

```
① 确认授权状态 ────────────────────────────────────────────
   ├─ 已获用户授权 → 正式版：versionName = 上一正式版顺延（BUILD +1）
   └─ 未授权       → RC：versionName = 顺延下一版本 + "-rcN"（N 顺延）
      │
② 改版本 ── app/build.gradle.kts（versionCode +1，versionName 按①）
      │
③ 更新 docs/Version Log/CHANGELOG.md ── [Unreleased] 晋级为 [<versionName>] - YYYY-MM-DD（条目格式见 docs/RELEASE_NOTES.md）
      │
④ 提交并推送到 main ── 本地仅运行验证（见 §5）
      │
⑤ 打 tag 并推送 ── git tag v<versionName> && git push origin v<versionName>
      │
⑥ GitHub Actions 产出成品 ── .github/workflows/release.yml
   └─ 校验 tag 与 versionName 一致 → 构建签名 APK → 创建 Release 并上传
```

**tag 名必须与 versionName 严格一致**，由 CI 强制校验（不一致直接失败）：

```bash
git tag "v$(versionName)"     # 例：v0.0.0.1-rc1
git push origin "v$(versionName)"
```

> 注：`-` 在 Git tag 名中是合法字符，GitHub Release 亦支持。

---

## 5. 本地职责：只验证，不产出成品

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

## 6. 与 SemVer / Keep a Changelog 的关系

- 本项目采用**四段式**而非标准三段 SemVer：默认迭代全部落在 BUILD 段，
  避免 PATCH 号在无 bug 修复时被无意义地抬高。
- `-rcN` 后缀即 SemVer pre-release 标识的等效物；获批后去掉后缀成为正式版，
  与 SemVer「预发布版本晋升为正式版本」的语义一致。
- `docs/Version Log/CHANGELOG.md` 仍遵循 Keep a Changelog：版本号写作 `[0.0.0.1-rc1] - YYYY-MM-DD`，
  发版时 `[Unreleased]` 整体晋级并保留新的空 `[Unreleased]`；条目**格式**（用户面向分类与小标题体例）
  以 `docs/RELEASE_NOTES.md` 为唯一事实来源。

---

## 7. 校验清单（打 tag 前）

- [ ] **授权状态已确认**：正式版有用户授权记录（确认或提前通知）；未授权则 `versionName` 必须以 `-rcN` 结尾
- [ ] BUILD 段较上一正式版 **+1**（前三段仅在用户明确指定的产品级节点才动，且后级归零）
- [ ] `app/build.gradle.kts` 中 `versionCode` 已 +1（大于历史上任何一次，含 RC）
- [ ] `docs/Version Log/CHANGELOG.md` 已把 `[Unreleased]` 晋级为 `[<versionName>] - 日期`，且条目符合 `docs/RELEASE_NOTES.md` 格式规约
- [ ] `docs/Version Log/release-titles.md` 已为本版本新增标题概括行（≤20 字，用户语言；Release 标题「— 概括」段的唯一来源）
- [ ] 本地 `testDebugUnitTest` + `verifyPaparazziDebug` 全绿
- [ ] tag 名 `v<versionName>` 与 `versionName` 完全一致（**不带 `+N`**）

---

## 8. 相关文档

| 文档 | 说明 |
|------|------|
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | 发版操作步骤与签名说明 |
| [`RELEASE_NOTES.md`](RELEASE_NOTES.md) | 发版日志格式规约（CHANGELOG / 独立文档 / Release 正文三处一致） |
| [`Version Log/CHANGELOG.md`](Version%20Log/CHANGELOG.md) | 版本日志总表 |
| [`qa/screenshot-testing.md`](qa/screenshot-testing.md) | 截图基线的记录与比对 |
| `.github/workflows/release.yml` | 打 tag 后产出成品的工作流 |
