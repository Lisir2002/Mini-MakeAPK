# 版本规范（Versioning）

> 状态：已生效（2026-10-25 修订）· 受 `docs/DOC_CHARTER.md` 约束
> 本文件是**版本号与发版流程的唯一事实来源**。改版本前必读。

---

## 1. 版本格式

```
MAJOR.MINOR.PATCH.BUILD[-rcN] + M
└──────── versionName ────────┘   └ versionCode
      （四段式，字符串）              （构建号，整数）
```

- **完整版本串**：`0.0.0.0+1`；RC 形态：`0.0.0.1-rc1+2`
- **`versionName`**（`app/build.gradle.kts`）：四段式 `MAJOR.MINOR.PATCH.BUILD`，
  未获正式版授权时可带预发布后缀 `-rcN`（N 从 1 起）
- **`versionCode`**（同文件）：构建号 `M`，**单调递增、永不重置**（Android 强制要求）
- **发布 tag**：`v<versionName>+<versionCode>`，如 `v0.0.0.0+1`、`v0.0.0.1-rc1+2`

当前：`versionName = "0.0.0.0"`，`versionCode = 1` → tag `v0.0.0.0+1`。

---

## 2. 递增规则（默认顺延 BUILD 段，禁止语义化跳段）

> 用户确认的规则原文：「上一个版本是 `0.0.0.3`，在没有特殊情况下顺延升版应该是 `0.0.0.4`」。

| 情形 | 规则 | 示例 |
|------|------|------|
| **正式版顺延（默认）** | BUILD 段 **+1**，前三段不动 | `0.0.0.3` → `0.0.0.4` |
| 前三段递增（特殊情况） | 仅限产品级节点，且**必须由用户明确指定**；递增时后级段归零 | 首个产品版 `1.0.0.0` |
| **`+M`（versionCode）** | **每次发版必 +1**（RC 也算一次发版） | `+1` → `+2` → `+3` |

- **无特殊情况一律顺延 BUILD 段**。缺陷修复、依赖升级、性能优化、UI 调整**不**升
  PATCH/MINOR——那些只是普通顺延。MAJOR/MINOR/PATCH 的语义化跳段在本项目**默认禁用**。
- **硬性约束**：`versionCode` 只能增大。Android 与各大商店以它判定新旧，
  回退会导致用户无法升级。即便回滚功能，也要顺延 BUILD 段发新版本，而非回退 M。

---

## 3. 发版授权与 RC 机制（核心规则）

> 用户确认的规则原文：「正式版发版需要用户确认或者提前通知，否则按照顺延下一个版本＋rc，
> 就比如上一个版本 `0.0.0.7`，那用户没有批准正式版的时候，应该发 `0.0.0.8-rc1`，
> 顺延下去就是 `0.0.0.8-rc2`。正式版一定需要用户授权再升版。」

### 3.1 授权闸门

- **正式版（去掉 `-rcN` 的版本）必须取得用户授权**——用户明确确认，或已提前通知并获认可。
- 未获授权时**不得**发正式版，唯一合规动作是发 **RC**。

### 3.2 RC（Release Candidate）

- RC 基版本 = 上一正式版顺延下一版（BUILD +1），后缀 `-rcN` 从 1 起编。
- 同一基版本的 RC 可顺延：`0.0.0.8-rc1` → `0.0.0.8-rc2` → …（N 递增，基版本不变）。
- 每个 RC 都是一次发版：`versionCode` 同样 +1，tag 同样强校验，CI 同样产出签名成品。
- **RC 获批**：去掉 `-rcN` 后缀成为正式版（`0.0.0.8-rc2` → `0.0.0.8`），
  `versionCode` 再 +1，CHANGELOG 以正式版号记录该版本的全部变更。

### 3.3 版本时间线示例

```
0.0.0.7+7        ← 上一正式版
0.0.0.8-rc1+8    ← 未授权，发 RC（BUILD 顺延 + rc1）
0.0.0.8-rc2+9    ← RC 顺延
0.0.0.8+10       ← 用户授权：去掉 -rc，成为正式版
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
③ 更新 CHANGELOG.md ── [Unreleased] 晋级为 [<versionName>+M] - YYYY-MM-DD
      │
④ 提交并推送到 main ── 本地仅运行验证（见 §5）
      │
⑤ 打 tag 并推送 ── git tag v<versionName>+M && git push origin v<versionName>+M
      │
⑥ GitHub Actions 产出成品 ── .github/workflows/release.yml
   └─ 校验 tag 与版本一致 → 构建签名 APK → 创建 Release 并上传
```

**tag 名必须与版本严格一致**，由 CI 强制校验（不一致直接失败）：

```bash
git tag "v$(versionName)+$(versionCode)"     # 例：v0.0.0.1-rc1+2
git push origin "v$(versionName)+$(versionCode)"
```

> 注：`+` 与 `-` 在 Git tag 名中均为合法字符，GitHub Release 亦支持。

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
- `CHANGELOG.md` 仍遵循 Keep a Changelog：版本号写作 `[0.0.0.1-rc1+2] - YYYY-MM-DD`，
  发版时 `[Unreleased]` 整体晋级并保留新的空 `[Unreleased]`。

---

## 7. 校验清单（打 tag 前）

- [ ] **授权状态已确认**：正式版有用户授权记录（确认或提前通知）；未授权则 `versionName` 必须以 `-rcN` 结尾
- [ ] BUILD 段较上一正式版 **+1**（前三段仅在用户明确指定的产品级节点才动，且后级归零）
- [ ] `app/build.gradle.kts` 中 `versionCode` 已 +1（且大于历史上任何一次，含 RC）
- [ ] `CHANGELOG.md` 已把 `[Unreleased]` 晋级为 `[<versionName>+M] - 日期`
- [ ] 本地 `testDebugUnitTest` + `verifyPaparazziDebug` 全绿
- [ ] tag 名 `v<versionName>+<M>` 与 `versionName`/`versionCode` 完全一致

---

## 8. 相关文档

| 文档 | 说明 |
|------|------|
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | 发版操作步骤与签名说明 |
| [`../CHANGELOG.md`](../CHANGELOG.md) | 版本日志 |
| [`qa/screenshot-testing.md`](qa/screenshot-testing.md) | 截图基线的记录与比对 |
| `.github/workflows/release.yml` | 打 tag 后产出成品的工作流 |
