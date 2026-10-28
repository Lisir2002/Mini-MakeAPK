# 事故复盘文档：Android 发布包崩溃 — SQLCipher 原生库未加载导致 UnsatisfiedLinkError

> **文档性质**：事故复盘（Postmortem） + 隐患审计（Risk Audit） + 预防策略（Prevention） + 经验教训（Lessons Learned）
> **遵循原则**：无指责（Blameless）— 聚焦系统、流程与工具链缺陷，而非个人责任。

---

## 0. 更新记录（Changelog · 实时维护）
> 同一 bug 每次修复/进展后**即时追加一行**，不积压到最后；版本号、状态、行动项随之同步（见团队 SOP `docs/team/bug-handling-sop.md` §2）。

| 日期 | 版本/commit | 更新内容 | 状态 |
|------|------------|---------|------|
| 2026-10-28 | `5302f03` | 初版复盘：根因（SQLCipher 未加载 + AGP 未打包）、隐患审计、预防方案、11 项行动项 | In Remediation（rc6 待发） |

---

## 1. 元信息（Meta / Header）

| 字段 | 值 |
|------|----|
| 事故编号 | `INC-20261028-SQLCIPHER-ULE` |
| 标题 | Android 发布包崩溃：SQLCipher 原生库未加载导致 `UnsatisfiedLinkError`（影响项目保存） |
| 严重级别 | **P1**（核心功能崩溃，影响所有 rc5 安装用户的项目保存） |
| 状态 | **In Remediation**（根因已定位、修复已开发但待 rc6 发布；rc5 生产包仍带病） |
| 作者 | 许清楚（产品经理） |
| 评审人 | 齐活林（主理人）/ 软件团队评审 |
| 日期 | 2026-10-28 |
| 影响版本 | `v0.0.0.1-rc5`（`versionCode 8`，`prerelease` tag `v0.0.0.1-rc5`） |
| 影响模块 | `:app` 加密数据库层（SQLCipher / Room） |
| 文档模板来源 | Google SRE / Atlassian / PagerDuty / 国内一线团队复盘实践 |

---

## 2. 摘要 TL;DR

2026-10-28，`v0.0.0.1-rc5` 经 `release.yml` 发布到 GitHub（prerelease）。由于升级 `sqlcipher-android:4.13.0` 后该版本**不再自动 `loadLibs`**，且 AGP 8 默认 `extractNativeLibs=false` 未设 `useLegacyPackaging`，导致 APK 内 `libsqlcipher.so` 既未被显式加载、也未被正确打包。用户在真机执行"新建/保存项目"时 100% 触发 `UnsatisfiedLinkError`，核心功能不可用。

根因不止于代码缺失，更在于**发布门禁在 Robolectric 下用 framework SQLite 跑单测，从不加载原生库，无法暴露 `.so` 缺失 → CI 假绿，带病包直接发到用户**（管道级系统根因）。

当前根因已定位、修复已开发但**仍在工作树未提交**；rc5 生产包带病，待发 rc6 闭环。本复盘从流程、技术、协作三个维度给出 11 项行动项（负责人/截止/验收/工单齐全），并形成可复用的团队故障库模板。

---

## 3. 时间线 Timeline（统一时区 GMT+8 / +08:00）

> 标注：**TTD**（Time To Detect，检测耗时）、**MTTR**（Mean Time To Repair / Resolve，缓解耗时）。

| 时间（GMT+8） | 事件 | 主体 |
|------|------|------|
| 2026-10-28 15:29 | rc5 经 `release.yml` 发布到 GitHub（prerelease），包内含 SQLCipher 缺陷（已带病） | 发布系统 |
| ~17:16 | 用户真机安装 rc5 后执行"新建/保存项目"，触发 `UnsatisfiedLinkError` 崩溃 | 用户设备 |
| ~17:xx | 用户将完整崩溃日志粘贴至对话反馈（系统无崩溃遥测，靠人工） | 用户 |
| 会话内 | 主理人协团队定位根因：SQLCipher 4.13.0 不自动 `loadLibs` + AGP 未打包 `.so`；并修复 rc5 发布期两处 CI 缺陷（测试硬编码 `/workspace` 路径、`verify.yml` 缺 Pillow） | 团队 |
| 会话内 | 开发修复：新增 `SqlCipherInitializer`（显式 `loadLibrary`，双入口）、`AndroidManifest extractNativeLibs=true`、`build.gradle useLegacyPackaging=true`、日志规范化（`LogContext`/`LogEvent`） | 工程师 |
| 当前 | 修复在工作树未提交；rc5 生产仍带病；待发 rc6 闭环 | 团队 |

- **TTD（检测耗时）**：无监控，约从 15:29 带病发布到 ~17:xx 用户反馈，约 **2 小时**，且完全依赖用户主动反馈。
- **MTTR（缓解耗时）**：**尚未闭环**（待 rc6）。

---

## 4. 影响面 Impact

| 维度 | 说明 |
|------|------|
| 受影响用户 | 所有安装 rc5 并尝试创建/保存项目的用户（rc5 `download_count` 初发为 0，外溢有限，但功能层面 **100% 复现**） |
| 核心功能不可用 | 项目保存 / 新建必崩 |
| 崩溃率 | 项目保存操作 **100%** |
| SLO | 无正式 SLO，但属 **P1** 功能崩溃 |
| 影响版本 / 模块 | `v0.0.0.1-rc5` · `:app` 加密数据库层（SQLCipher / Room） |
| 业务后果 | 用户无法保存工作成果，构成数据不可用风险；虽外溢面小，但故障模式为"发布即带病"的系统性风险 |

---

## 5. 检测与响应 Detection & Response

**检测方式（客观事实）**
- 系统无崩溃遥测 / 无自动化监控。
- 真实检测路径：**用户真机崩溃 → 用户手动粘贴完整日志至对话反馈**。
- 导致 TTD 约 2 小时，且为被动、依赖运气（见 §9 侥幸点）。

**响应动作**
- 主理人收到反馈后，在会话内编排团队定位根因（区分"触发"与"根本"）。
- 工程师审计代码与构建链，定位为 `loadLibrary` 缺失 + AGP 打包配置缺失。
- 同步修复 rc5 发布期两处 CI 缺陷（测试硬编码 `/workspace` 路径、`verify.yml` 缺 Pillow）。
- 开发修复：`SqlCipherInitializer` 双入口显式加载、`extractNativeLibs=true`、`useLegacyPackaging=true`、日志结构化。
- **现状**：修复在工作树未提交，rc5 生产仍带病，待 rc6 闭环。

**响应有效性与缺口**
- 人工响应有效但**不可规模化**；若无用户反馈或日志不全，定位将显著变慢。
- 缺口：缺少崩溃可回传通道、缺少"原生库加载/打包"发布前卡点。

---

## 6. 根因分析 Root Cause（5 Whys）

> 区分**触发（Trigger）**与**根本（Root）**：前 4 层为技术触发链；第 5 层为系统/流程根本因。

1. **为什么项目保存崩溃？**
   → `net.zetetic` SQLCipher 的 `nativeOpen` 抛 `UnsatisfiedLinkError`（`.so` 符号未链接）。
   *（触发）*

2. **为什么 `.so` 符号未链接？**
   → 运行时从未加载 `libsqlcipher.so`（未调用 `System.loadLibrary`）。
   *（触发）*

3. **为什么没调用 `loadLibrary`？**
   → 升级到 `sqlcipher-android:4.13.0` 后，新版**已移除旧版自动 `loadLibs` 的静态初始化**；团队未补显式加载。
   *（触发 + 技术根因）*

4. **为什么 AGP 也没打包好 / 仍缺库？**
   → 即便补了 `loadLibrary`，AGP 8 默认 `extractNativeLibs=false` 且未设 `useLegacyPackaging`，`.so` 未正确打包进 APK。
   *（触发 + 技术根因）*

5. **为什么带病包能发布到生产？**
   → 发布门禁（`testDebugUnitTest` / `verifyPaparazziDebug`）在 **Robolectric 下用 framework SQLite**，**从不加载原生库**，无法暴露 `.so` 缺失 → **系统/流程根因：发布前缺少"原生库加载/打包"校验卡点（CI 假绿）**。
   *（根本 / 系统根因）*

**结论**：技术触发链（第 1–4 层）可由代码修复闭合；但第 5 层"CI 假绿、带病发货"是本次事故能在生产暴露的**管道级根因**，必须靠 §8 的发布前校验卡点根治。

---

## 7. 隐患审计 Risk Audit

> 来源：工程师寇豆码（Kou）审计，`IS_PASS: YES`。按严重度分级；"是否已触发本次"与"是否可能再触发同类"用于排序处置优先级。

### 高（HIGH）

| 隐患 | 严重度 | 是否已触发本次 | 是否可能再触发同类 | 说明 |
|------|--------|----------------|--------------------|------|
| **HIGH-1** CI 门禁无法暴露"原生库缺失" | 高 | 是（掩盖问题） | 是 | `release`/`verify` 仅跑 `testDebugUnitTest`/`verifyPaparazziDebug`，用 framework SQLite 从不加载 `libsqlcipher.so`；`assembleRelease` 后无任何步骤校验 APK 内是否含 `libsqlcipher.so` → 带病 APK 直接发到用户（本次事故能在 CI 假绿发货的管道级根因）。 |
| **HIGH-2** `SqlCipherInitializer` 静默吞掉 `UnsatisfiedLinkError` | 高 | 否（放大定位难度） | 是 | 当前 `catch` 后什么都不做，`loaded` 保持 `false` → 代码继续走到 `Room.databaseBuilder().build()` → 在 `nativeOpen` 才抛深埋 Room 堆栈的 cryptic 崩溃，与本次事故一致且更难定位。 |

### 中（MEDIUM）

| 隐患 | 严重度 | 是否已触发本次 | 是否可能再触发同类 | 说明 |
|------|--------|----------------|--------------------|------|
| **HIGH-3**（类 `/workspace` bug 残余）测试依赖 `user.dir` + `parentFile` 向上找仓库根 | 中 | 否 | 是 | `ShellSpecInjectionConsistencyTest.kt:52-55`、`ShellHostExportParityTest.kt:59-63`、`TemplateShellSnapshotTest.kt:60-61` 用 `File(user.dir)` + `parentFile` 向上找 `settings.gradle.kts`；若 cwd 不在仓库内或路径更高层误命中另一 `settings.gradle.kts` → `NoSuchElementException` 假红 / 读错文件假绿。 |
| **1.2** `app/build.gradle.kts` 未声明 `ndk.abiFilters` | 中 | 否 | 是 | 当前隐式打全部 4 ABI 暂时安全；未来有人加 `abiFilters` 漏某架构（尤其 `arm64-v8a`），对应设备直接 `UnsatisfiedLinkError`，且 CI 单测（`x86_64`）仍绿。 |
| **3.2**（与 CI 假绿同源）Room 迁移测试用 framework SQLite 不加载 SQLCipher 原生库 | 中 | 否 | 是 | 迁移测试能验证 schema 但永不触发 `.so` 加载路径，正是 release 门禁无法发现本次事故的根因。 |
| **6.1** `SqlCipherInitializer.kt:26` 唯一的"裸日志逃逸点"且恰在原生加载失败路径上 | 中 | 否（放大定位难度） | 是 | 用 `android.util.Log.e` 绕过 `MmaLogger`，丢了机型/ABI/版本上下文且 tag 不符约定，事后聚合困难。 |

### 低 / 已缓解（LOW / MITIGATED）

| 隐患 | 严重度 | 是否已触发本次 | 是否可能再触发同类 | 说明 |
|------|--------|----------------|--------------------|------|
| **2.1** ProGuard `-keep net.zetetic.database.**` 范围偏窄 | 低 | 否 | 可能 | R8 full mode 下若 Zetetic 升级调整包结构，可能误删 → release 才崩。已建议扩为 `net.zetetic.**`。 |
| **5.2/5.3** Pillow 缺失已缓解但仍有边角脆弱 | 低（已缓解） | 否（已缓解） | 低 | `verify.yml` 已装 Pillow，但 `\|\|` 兜底在双失败时不直观；且未用 `actions/setup-python` 钉版本。 |

### 已达标 / 正例（PASS / GOOD）

| 项目 | 说明 |
|------|------|
| **1.3** 双入口 `loadLibrary` 已正确 | `MmaApplication.attachBaseContext` 最前 + `AppDatabaseFactory.create` 双入口。 |
| **1.4** `extractNativeLibs=true` + `useLegacyPackaging=true` 已配置 | 原生库打包配置到位。 |
| **3.1** Room version/迁移链/schema 三对齐且有守门测试 | 最健康板块。 |
| **6.2** 无 `println`/`printStackTrace`/`System.out` 逃逸 | 日志出口统一。 |
| **6.3** `LogContext` 覆盖度与降级达标 | 但建议增 `supportedAbis` 诊断维度（见 §8）。 |

---

## 8. 预防策略与落地方案 Prevention

> 按 **预防（Prevent）/ 检测（Detect）/ 缓解（Mitigate）** 三桶组织。每条含：背景、落地方案（代码/配置）、收益、风险点、关联行动项（含负责人/截止/验收）。

### 8.1 检测（Detect）— 发布前卡点，把"用户真机崩溃"转为"PR 阶段红字"

#### D1 · HIGH-1（ROI 最高）CI 断言 release/debug APK 内含 `libsqlcipher.so`（按 ABI 矩阵）
**背景**：唯一能补偿"单测不加载原生库"盲区的控制手段。
**落地方案**：新增 `scripts/gitops/check_native_libs.py`：

```python
#!/usr/bin/env python3
"""校验 APK 内每个目标 ABI 都存在指定原生库（默认 libsqlcipher.so）。
退出码：0=全部存在；1=缺失（CI 应设为失败）。"""
import sys, zipfile, argparse

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--lib", default="sqlcipher")
    ap.add_argument("--abis", default="arm64-v8a,armeabi-v7a,x86,x86_64")
    a = ap.parse_args()
    abis = [x.strip() for x in a.abis.split(",") if x.strip()]
    so = f"lib{a.lib}.so"
    try:
        names = zipfile.ZipFile(a.apk).namelist()
    except Exception as e:
        sys.stderr.write(f"::error::无法读取 APK {a.apk}: {e}\n"); return 1
    missing = [f"lib/{abi}/{so}" for abi in abis if f"lib/{abi}/{so}" not in names]
    if missing:
        sys.stderr.write("::error::APK 缺失原生库: " + ", ".join(missing) + "\n"); return 1
    print(f"OK: {so} 存在于全部 {len(abis)} 个 ABI ({', '.join(abis)})"); return 0

if __name__ == "__main__":
    sys.exit(main())
```

`release.yml` 在"构建 Release APK"后、"校验 APK 签名"前插入：
```yaml
      - name: 校验原生库打包（防 UnsatisfiedLinkError 漏网）
        run: |
          set -euo pipefail
          APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
          python3 scripts/gitops/check_native_libs.py --apk "$APK" --lib sqlcipher \
            --abis arm64-v8a,armeabi-v7a,x86,x86_64
```

`verify.yml` 在 `verifyPaparazziDebug` 后新增（用 debug APK 早暴露）：
```yaml
      - name: 构建 Debug APK 并校验原生库打包
        run: |
          set -euo pipefail
          ./gradlew assembleDebug --no-daemon
          APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
          python3 scripts/gitops/check_native_libs.py --apk "$APK" --lib sqlcipher \
            --abis arm64-v8a,armeabi-v7a,x86,x86_64
```
**收益**：PR 阶段 30 秒红字 + 明确缺哪个 `.so`。
**风险点**：需与 `ndk.abiFilters` 保持单一事实来源（见 P3 / 行动项 ACT-4）。
**关联行动项**：ACT-1（负责人 寇豆码，截止 2026-11-04，验收：`.so` 缺失或 ABI 漏配时 release/verify 阻断）。

#### D2 · verify.yml 用 `actions/setup-python@v5` 钉版本 + Pillow 安装加固
**背景**：消除 Pillow 缺失边角脆弱（隐患 5.2/5.3），让 verify 在缺 Pillow 时明确红且提示清晰。
**落地方案**：在 `verify.yml` 中以 `actions/setup-python@v5` 钉 Python 3.11 并显式安装 Pillow（替换 `||` 兜底写法）。
**收益**：依赖可复现、失败可读。
**风险点**：需同步更新 `setup-python` 大版本跟踪。
**关联行动项**：ACT-6（负责人 寇豆码，截止 2026-11-11）。

#### D3 · `LogContext` 增加 `supportedAbis` 诊断维度
**背景**：原生事故诊断依赖设备期望 ABI，当前日志缺该维度（隐患 6.1/6.3）。
**落地方案**：在 `LogContext` 增 `supportedAbis`（设备支持 ABI 列表，`runCatching` 兜底 `"unknown"`）并接入 `format()`/`fields`。
**收益**：原生事故日志直接显示设备期望 ABI，定位效率提升。
**风险点**：需确认 `Build.SUPPORTED_ABIS` 在日志初始化期可用。
**关联行动项**：ACT-7（负责人 寇豆码，截止 2026-11-11）。

#### D4 · 引入轻量崩溃遥测/上报
**背景**：本次检测完全靠用户粘贴日志，TTD 小时级（§3/§5）。
**落地方案**：引入轻量崩溃遥测/上报，至少崩溃日志可回传。
**收益**：TTD 降至分钟级，消除"靠用户粘贴才发现"的检测盲区。
**风险点**：需评估隐私合规与上报通道稳定性。
**关联行动项**：ACT-9（负责人 齐活林，截止 2026-11-18）。

### 8.2 缓解（Mitigate）— 同类故障 MTTR 从天级降到分钟级

#### M1 · HIGH-2（ROI 第二）`SqlCipherInitializer` 加固：失败 fail-fast + 统一结构化日志
**背景**：当前静默吞异常放大定位难度（隐患 HIGH-2/6.1）。
**落地方案**：重写 `app/src/main/java/com/make/apk/data/local/SqlCipherInitializer.kt`：

```kotlin
package com.make.apk.data.local

import com.make.apk.logging.MmaLogger
import android.os.Build

object SqlCipherInitializer {
    @Volatile private var loaded = false
    val isLoaded: Boolean get() = loaded

    fun initOnce(isTestEnv: Boolean = isRunningUnderRobolectric()) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                System.loadLibrary("sqlcipher")
                loaded = true
                MmaLogger.security(event = "sqlcipher_native_loaded", message = "libsqlcipher.so 已加载",
                    fields = mapOf("abi" to (currentAbi() ?: "unknown"), "test_env" to isTestEnv))
            } catch (e: UnsatisfiedLinkError) {
                MmaLogger.error(event = "sqlcipher_native_load_failed",
                    message = "System.loadLibrary(\"sqlcipher\") 失败", throwable = e,
                    fields = mapOf("abi" to (currentAbi() ?: "unknown"), "test_env" to isTestEnv))
                if (!isTestEnv) {
                    throw NativeLibraryMissingException(
                        "libsqlcipher.so 未能加载（设备首选 ABI=${currentAbi()}）。检查：① packaging.jniLibs.useLegacyPackaging=true；" +
                        "② android:extractNativeLibs=true；③ 对应 ABI 的 libsqlcipher.so 已打进 APK。", e)
                }
            }
        }
    }
    private fun currentAbi(): String? = Build.SUPPORTED_ABIS.firstOrNull()
    private fun isRunningUnderRobolectric(): Boolean =
        runCatching { Class.forName("org.robolectric.Robolectric") != null }.getOrDefault(false)
}

class NativeLibraryMissingException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
```

`MmaApplication.kt` 的 `attachBaseContext` 重排（先装日志再加载原生库）：
```kotlin
override fun attachBaseContext(base: Context?) {
    super.attachBaseContext(base)
    StartupTracer.begin()
    installLogging()                // 先装日志：保证后续原生库加载失败能带上下文上报
    SqlCipherInitializer.initOnce() // 再加载原生库（DB 为 by lazy，此处远早于首次访问）
}
```
**收益**：同类故障 MTTR 从天级降到分钟级。
**风险点**：`initOnce` 必须在 `installLogging` 之后；生产抛异常需 `CrashReportUI` 接住（现有 `runCatching` 体系已具备）。
**关联行动项**：ACT-2（负责人 寇豆码，截止 2026-11-04，验收：单测证明测试环境不崩、生产路径报错带 ABI/版本上下文）。

#### M2 · 发布 rc6 闭环本次修复并真机冒烟
**背景**：rc5 生产仍带病（§1/§3）。
**落地方案**：提交工作树修复 → 发布 rc6 → 真机冒烟（新建/保存项目）。
**收益**：闭环本次事故。
**风险点**：需确保 ACT-1~ACT-7 相关修复一并随 rc6 发布。
**关联行动项**：ACT-10（负责人 齐活林，截止 2026-11-04，验收：rc6 真机项目保存 100% 成功）。

### 8.3 预防（Prevent）— 从流程与配置层面杜绝同类隐患

#### P1 · HIGH-3（ROI 第三）测试仓库根去 `user.dir` 耦合（Gradle 注入确定性来源）
**背景**：消除"只在某台机器红"的玄学失败（隐患 HIGH-3）。
**落地方案**：`app/build.gradle.kts` 的 `testOptions`：
```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
        allTests { systemProperty("mma.repo.root", rootDir.absolutePath) }
    }
}
```
新增 `app/src/test/java/com/make/apk/RepoRootLocator.kt`：
```kotlin
package com.make.apk
import java.io.File
object RepoRootLocator {
    fun locate(marker: String = "settings.gradle.kts"): File {
        val injected = System.getProperty("mma.repo.root")
        if (!injected.isNullOrBlank()) {
            val root = File(injected)
            if (File(root, marker).exists()) return root
        }
        val start = File(checkNotNull(System.getProperty("user.dir")) { "user.dir 未设置" })
        return generateSequence(start) { it.parentFile }
            .first { it.isDirectory && File(it, marker).exists() }
    }
}
```
三个测试把内联定位替换为 `RepoRootLocator.locate().resolve("template/src/main/res/raw/shell_spec.json")`（ShellSpecInjectionConsistencyTest / ShellHostExportParityTest / TemplateShellSnapshotTest）。
**收益**：测试与环境解耦，消除玄学失败。
**风险点**：需同步清理旧内联定位代码。
**关联行动项**：ACT-3（负责人 寇豆码，截止 2026-11-04，验收：三测试在任意 cwd 下稳定通过）。

#### P2 · `ndk.abiFilters` 显式声明（与 D1 期望值单一事实来源）
**落地方案**：`defaultConfig { ndk { abiFilters += listOf("armeabi-v7a","arm64-v8a","x86","x86_64") } }`
**收益**：构建产物 ABI 与 CI 断言一致，避免漏配架构（隐患 1.2）。
**风险点**：需与 `check_native_libs.py` 的 `--abis` 保持一致。
**关联行动项**：ACT-4（负责人 寇豆码，截止 2026-11-04）。

#### P3 · ProGuard 扩范围
**落地方案**：`app/proguard-rules.pro` 改为：
```
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**
-keep class androidx.sqlite.db.** { *; }
```
**收益**：R8 full mode 下 SQLCipher 不被误删（隐患 2.1）。
**风险点**：范围扩大需回归验证包体积。
**关联行动项**：ACT-5（负责人 寇豆码，截止 2026-11-11，验收：release 包 R8 后 SQLCipher 仍可加载）。

#### P4 · 建立"第三方 SDK 引入检查清单"
**背景**：本次根因之一是升级带 native 依赖的库未补检查项。
**落地方案**：清单含 native / 反射 / 注解处理器必须补 `-keep` + smoke 测试；纳入 PR 模板，新 SDK PR 必勾。
**收益**：同类隐患一次性审计而非逐个救火。
**风险点**：需团队执行纪律保障。
**关联行动项**：ACT-8（负责人 许清楚，截止 2026-11-11，验收：清单纳入 PR 模板，新 SDK PR 必勾）。

#### P5 · 本经验文档评审归档 + 沉淀为团队故障库模板
**关联行动项**：ACT-11（负责人 许清楚，截止 2026-11-04，状态 进行中，验收：文档合入 main 且生成可复用模板）。

---

## 9. 经验教训 Lessons Learned

### 流程层
1. **发布门禁必须在"与生产同构"环境验证关键路径**：Robolectric 用 framework SQLite 不能替代原生库加载验证（假绿）。
2. **升级带 native 依赖的第三方库（尤其大版本 / 换包名）需把"原生库加载 + 打包"列为发布检查项**。
3. **无崩溃遥测导致检测完全依赖用户反馈，TTD 小时级** — 这是系统性可观测性缺口。

### 技术层
1. **新版 SQLCipher 不再自动 `loadLibs`**：必须显式 `System.loadLibrary` 且 `try-catch` 兜底 + 生产 fail-fast。
2. **AGP 8 下原生库需 `extractNativeLibs=true` + `useLegacyPackaging=true`**。
3. **日志需结构化携带机型/版本/ABI**：故障定位效率取决于上下文完整度。

### 协作层
1. **主理人编排 + 工程师审计 + PM 文档化分工有效**；但同类隐患应一次性审计而非逐个救火。
2. **经验需沉淀为可复用模板（故障库）**，避免重复踩坑。

### 侥幸点（Where We Got Lucky）
- **rc5 `download_count` 为 0**，外溢用户极少；若大规模分发后才暴露，影响面与排查成本大得多。
- **用户恰好粘贴了完整堆栈**（含 trace/seq/thread），否则定位更久。
- **设备为常见架构（`arm64-v8a`）**，小众 ABI 可能更早暴露但更易被忽略。

---

## 10. 行动项跟踪 Action Items

| # | 改进项 | 类型 | 负责人 | 截止 | 状态 | 验收标准 | 工单 |
|---|--------|------|--------|------|------|---------|------|
| 1 | CI 增加 APK 内 `libsqlcipher.so` 存在性 + ABI 矩阵断言（`check_native_libs.py` + release/verify 两条流水线） | 检测 | 寇豆码 | 2026-11-04 | 待实施 | `.so` 缺失或 ABI 漏配时 release/verify 阻断 | ACT-1 |
| 2 | `SqlCipherInitializer` 加固（fail-fast + 统一 `MmaLogger` 带上下文；生产抛 `NativeLibraryMissingException`） | 缓解 | 寇豆码 | 2026-11-04 | 待实施 | 单测证明测试环境不崩、生产路径报错带 ABI/版本上下文 | ACT-2 |
| 3 | 测试仓库根去 `user.dir` 耦合（Gradle 注入 `mma.repo.root` + `RepoRootLocator`，替换三处测试内联定位） | 预防 | 寇豆码 | 2026-11-04 | 待实施 | 三测试在任意 cwd 下稳定通过 | ACT-3 |
| 4 | `ndk.abiFilters` 显式声明（与 ACT-1 期望值单一事实来源） | 预防 | 寇豆码 | 2026-11-04 | 待实施 | 构建产物 ABI 与 CI 断言一致 | ACT-4 |
| 5 | ProGuard 扩 `-keep net.zetetic.**` 范围 + `androidx.sqlite.db.**` | 预防 | 寇豆码 | 2026-11-11 | 待实施 | release 包 R8 后 SQLCipher 仍可加载 | ACT-5 |
| 6 | `verify.yml` 用 `actions/setup-python` 钉版本 + Pillow 安装加固 | 检测 | 寇豆码 | 2026-11-11 | 待实施 | Pillow 缺失时 verify 明确红且提示清晰 | ACT-6 |
| 7 | `LogContext` 增加 `supportedAbis` 诊断维度并接入 `format()`/`fields` | 检测 | 寇豆码 | 2026-11-11 | 待实施 | 原生事故日志直接显示设备期望 ABI | ACT-7 |
| 8 | 建立"第三方 SDK 引入检查清单"（native/反射/注解处理器必须补 `-keep` + smoke 测试） | 预防 | 许清楚 | 2026-11-11 | 待实施 | 清单纳入 PR 模板，新 SDK PR 必勾 | ACT-8 |
| 9 | 引入轻量崩溃遥测/上报（崩溃日志可回传），消除检测盲区 | 检测 | 齐活林 | 2026-11-18 | 待实施 | 崩溃可被动采集，TTD 降至分钟级 | ACT-9 |
| 10 | 发布 rc6 闭环本次修复并真机冒烟（新建/保存项目不再 `UnsatisfiedLinkError`） | 缓解 | 齐活林 | 2026-11-04 | 待实施 | rc6 真机项目保存 100% 成功 | ACT-10 |
| 11 | 本经验文档评审归档 + 沉淀为团队故障库模板 | 预防 | 许清楚 | 2026-11-04 | 进行中 | 文档合入 main 且生成可复用模板 | ACT-11 |

---

## 附录 Appendix

### A. 关键报错原文（用户粘贴）
```
1793179000228 ERROR/ERROR [project_save_failed] UnsatisfiedLinkError {"success":false} trace=5da387cf01895321 seq=14 thread=main
java.lang.UnsatisfiedLinkError: No implementation found for long net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen(java.lang.String, int, java.lang.String, boolean, boolean) (tried Java_net_zetetic_database_sqlcipher_SQLiteConnection_nativeOpen and Java_net_zetetic_database_sqlcipher_SQLiteConnection_nativeOpen__Ljava_lang_String_2ILjava_lang_String_2ZZ) - is the library loaded, e.g. System.loadLibrary?
	at net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen(Native Method)
	at net.zetetic.database.sqlcipher.SQLiteConnection.open(SourceFile:5)
	at net.zetetic.database.sqlcipher.SQLiteConnection.open(SourceFile:2)
	at net.zetetic.database.sqlcipher.SQLiteConnectionPool.openConnectionLocked(SourceFile:7)
	at net.zetetic.database.sqlcipher.SQLiteConnectionPool.open(SourceFile:4)
	at net.zetetic.database.sqlcipher.SQLiteConnectionPool.open(SourceFile:2)
	at net.zetetic.database.sqlcipher.SQLiteDatabase.openInner(SourceFile:6)
	at net.zetetic.database.sqlcipher.SQLiteDatabase.open(SourceFile:1)
	at net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(SourceFile:7)
	at net.zetetic.database.sqlcipher.SQLiteOpenHelper.getDatabaseLocked(SourceFile:138)
	at net.zetetic.database.sqlcipher.SQLiteOpenHelper.getWritableDatabase(SourceFile:3)
	at net.zetetic.database.sqlcipher.SupportHelper.getWritableDatabase(SourceFile:3)
	at androidx.room.x.inTransaction(SourceFile:5)
	...
	at com.make.apk.data.local.dao.ProjectDao_Impl$11.call(SourceFile:2)
```

### B. 参考规范链接
- Google SRE Book — Postmortem Culture: https://sre.google/sre-book/postmortem-culture/
- Atlassian Incident Postmortem Handbook
- PagerDuty Postmortem Template
- 国内一线团队故障复盘实践

---

> **本复盘遵循无指责（Blameless）原则**：本报告聚焦系统、流程与工具链缺陷，所有结论用于改进而非归咎个人。感谢用户在 rc5 外溢极小阶段即提供完整日志，以及团队在会话内快速定位与修复。
