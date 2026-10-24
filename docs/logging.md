# 日志层设计（Logging）

> 状态：已生效（2026-10-25）· 受 `docs/DOC_CHARTER.md` 约束
> 本文是日志层的**唯一事实来源**：类别划分、架构、脱敏红线、保留期与合规。

---

## 1. 决策速览

| 维度 | 决策 |
|------|------|
| 门面 | **自研 `MmaLogger`**（零第三方依赖），全类别覆盖 + 字段全量化 |
| 事件模型 | **结构化**（稳定事件名 + 类型化键值），非字符串拼接 |
| 载体 | **分级**：高价值结构化 → 加密 Room；高频诊断 → 轮转文件；调试 → 仅 Logcat |
| 崩溃 / ANR | **自研**：`UncaughtExceptionHandler` + 主线程看门狗（纯本地、离线可用） |
| 上报 | **当前不上报**（后端要等 Phase 2）；本地加密留存 + 用户主动导出诊断包 |
| 脱敏 | 门面层 `Sanitizer` **强制**执行，敏感值不出端 |

---

## 2. 九大类别（不止 bug）

| 类别 | 记什么 | 加密 DB | 文件 | 保留期 |
|------|--------|:------:|:----:|:------:|
| `CRASH` | Java 崩溃栈、ANR 主线程栈 | ✅ | ✅ | 90 天 |
| `ERROR` | 已捕获异常、操作失败 | ✅ | ✅ | 90 天 |
| `OPERATION` | **用户做了什么**：启动构建、导出、删除历史、权限、设置 | ✅ | — | **180 天**（合规 ≥6 个月） |
| `STARTUP` | 冷/温/热型、阶段耗时、首帧 | ✅ | ✅ | 90 天 |
| `PERFORMANCE` | 耗时、慢查询、采样埋点 | ✅ | — | 30 天 |
| `NETWORK` | 端点路径 / 状态码 / 耗时 / 重试（**不记 body 与 token**） | ✅ | — | 30 天 |
| `LIFECYCLE` | 前后台切换、Activity 状态 | — | — | 不落盘 |
| `TRACE` | 分支决策、状态转储（仅 debug） | — | — | 不落盘 |
| `SECURITY` | 口令生成、加解密异常、**谁在何时导出了日志** | ✅ | ✅ | **1095 天**（3 年） |

级别契约：`VERBOSE/DEBUG` 仅本地；`INFO` 业务结果；`WARN` 降级已自愈；`ERROR` 需人介入；`FATAL` 崩溃/ANR。
**判据：一条 "error" 若不值得人看一眼，它就是 WARN**——级别通胀会让告警彻底失效[^2]。

---

## 3. 架构

```
com.make.apk.logging/
├── MmaLogger.kt        # 统一门面：构造事件 → 强制脱敏 → 分发（含限流）
├── LogConfig.kt        # 按构建类型差异化配置
├── LogLevel / LogCategory / LogEvent / LogFields
├── Sanitizer.kt        # deny-list 脱敏（敏感键 / PII / URL / 邮箱 / key=value 凭据）
├── TraceContext.kt     # sessionId + traceId + 单调序号（不透明、临时）
├── RateLimiter.kt      # 按事件名滑动窗口限流，防日志风暴
├── AsyncLogDispatcher  # 单后台线程 + 有界队列（满则丢弃，绝不阻塞调用方）
├── JsonLite.kt         # 极简 JSON（避免 org.json 在 JVM 单测里"未打桩"）
├── StartupTracer.kt    # 冷/温/热分型 + 阶段耗时
└── sink/  LogcatSink · FileSink(+RotationPolicy) · DatabaseSink · CrashHandler · AnrWatchdog
```

**三条不可动摇的原则**：

1. **调用线程绝不碰磁盘**：只做一次 `queue.offer`；
2. **队列永远有界**：日志系统不能成为崩溃原因（无界队列在低端机会 OOM）；
3. **崩溃/安全审计不受限流影响**：前者是排查依据，后者是合规要求。

---

## 4. 结构化事件与全量化字段

```kotlin
MmaLogger.performance("build_finished", durationMs = 4_200)
MmaLogger.operation("build_started", fields = mapOf(LogFields.SUCCESS to true))
MmaLogger.network("build_api_call", endpoint = "/build", statusCode = 200, durationMs = 300, success = true)
MmaLogger.error("build_failed", throwable, fields = mapOf(LogFields.REASON to "TIMEOUT"))
```

- 事件名用 `snake_case` 稳定动词（`build_failed`），像 API 一样演进；
- 量化维度走 [`LogFields`](../app/src/main/java/com/make/apk/logging/LogFields.kt) 约定词表
  （`duration_ms` / `attempt` / `size_bytes` / `success` / `status_code` …），
  避免同一含义出现 `duration` 与 `cost_ms` 两种写法导致日后无法聚合；
- `duration_ms` 与 `success` 会被**提升为数据库独立列**，让"耗时分布""失败率"可直接 SQL 聚合。

---

## 5. 脱敏（红线）

日志属于"以电子方式记录的个人信息"（《个人信息保护法》第 4 条）[^6]，落盘即产生义务。

`Sanitizer` 在**门面层强制**执行，调用方无法绕过：

| 输入 | 处理 |
|------|------|
| 键含 `token/password/authorization/cookie/passphrase…` | 值整体掩码 `***` |
| 键含 `phone/email/id_card/imei/location…` | 值整体掩码 |
| 键含 `url/uri/link` | 只留 `scheme://host/***`，丢弃查询串 |
| 自由文本含邮箱、9 位以上连续数字 | 就地掩码 |
| 自由文本含 `token=xxx` 形态 | 保留键名、掩码值（栈与第三方日志最常见泄漏形态） |
| 超长值（>512 字符） | 截断，防整页用户输入入日志 |

**哈希 ≠ 匿名化**：对邮箱/设备做稳定哈希仍可被字典还原并跨日志关联，
因此本项目只用**不透明、临时**的 traceId/sessionId，从不使用账号或设备标识[^1]。

---

## 6. 四大 Sink 要点

| Sink | 关键设计 |
|------|---------|
| `LogcatSink` | 仅 debug 启用；标签 `MMA-<类别>`；异常栈单独一行避免被截断 |
| `FileSink` | 单线程顺序追加；按大小滚动 + 按数量裁剪（默认 2MB×5）；目录用 `filesDir/logs`（免权限、卸载即清） |
| `DatabaseSink` | **解决"日志早于数据库就绪"**：DB 需 Keystore+SQLCipher 打开必然迟到，故 attach 前先入有界缓冲，attach 后回灌 |
| `CrashHandler` / `AnrWatchdog` | 见 §7 |

---

## 7. 崩溃与 ANR（自研，原理）

- **Java 崩溃**：`Thread.setDefaultUncaughtExceptionHandler`。
  必须在 `Application.attachBaseContext()` 里**尽可能早**安装，否则启动期崩溃会全部漏掉；
  捕获后**必须转发给原 handler**，否则系统会失去默认日志与杀进程行为（表现为崩溃后卡死不退出）[^4]。
- **ANR**：主线程标记消息超时法——子线程周期性向主线程 Looper 投递自增计数消息，
  一个周期后计数未变即判定阻塞，抓取主线程栈；抓到后休眠一个周期避免刷屏[^4]。
- **崩溃时同步刷盘**：进程即将终止，异步队列来不及写，故 `crash()` 会 `flushBlocking()` 尽力落盘。
- Native 崩溃需信号处理器（SIGSEGV/SIGABRT），本项目暂不涉及 NDK，**未实现**。

---

## 8. 启动监控

`StartupTracer` 用 `SystemClock.elapsedRealtime()`（不受改时间/NTP 校时影响，不会出现负耗时）：

```
attach_base_context → application_oncreate → first_activity → first_frame
```

终点应以**首帧绘制完成**（`Activity.reportFullyDrawn()`）为准，这与用户主观感受一致，
也是官方推荐口径[^5]。当前工程尚无 Activity，暂以 `onCreate` 结束为终点（见 §11 待补）。

---

## 9. 保留期与合规

| 要求 | 依据 | 落地 |
|------|------|------|
| 数据处理活动日志 ≥ 6 个月 | 数据处理活动日志保护技术措施要求[^7] | `OPERATION` 保留 180 天 |
| 敏感个人信息操作日志 3 年 | GB/T 45574-2025[^8] | `SECURITY` 保留 1095 天 |
| 超期删除或匿名化 | 个人信息最小必要评估规范[^9] | `LogRepository.pruneExpired()` 按类别分别清理 |
| 日志加密存储 | 同上 | 落 SQLCipher 加密库 |
| 导出需留证 | 内部要求 | 导出前先记一条 `SECURITY` 审计日志 |

---

## 10. 使用示例

```kotlin
// 一次构建链路：统一 traceId 串联
TraceContext.withNewTrace {
    val id = repo.start(...)
    MmaLogger.operation("build_started", fields = mapOf(LogFields.SUCCESS to true))
    // ... 构建 ...
    MmaLogger.performance("build_finished", durationMs = elapsed)
}

// 查询与清理
logRepository.observeByCategory(LogCategory.CRASH)
logRepository.pruneExpired()

// 导出诊断包（会先记一条安全审计日志）
val text = logRepository.exportDiagnostics(sinceMs = System.currentTimeMillis() - 7 * 86400_000L)
```

---

## 11. 待补 / 已知限制

- **首帧终点**：接入首屏后用 `Activity.reportFullyDrawn()` 触发 `StartupTracer.complete()`。
- **Native 崩溃**：需信号处理器，涉及 NDK，暂不实现。
- **云端上报**：等 Phase 2 后端就绪，届时按批次 + 压缩 + 指数退避上报（当前纯本地）。
- **Instrumented 测试**：SQLCipher 真机加解密、崩溃文件落盘、ANR 触发均未覆盖（需 Android 运行环境）。

---

## 12. 参考

[^1]: [Structured logging and privacy-safe diagnostics](https://rakesh-lakshmipathy.github.io/android-master-to-architect/docs/observability/structured-logging)
[^2]: [Mobile Logging Best Practices](https://www.logtrics.io/blog/mobile-logging-best-practices)
[^3]: [Android 高性能日志写入方案](https://cloud.baidu.com/product/keyue_home/community?id=5281514)
[^4]: [xCrash — Crash Capturing Mechanisms](https://deepwiki.com/iqiyi/xCrash/1.2-crash-capturing-mechanisms)
[^5]: [Android Developers — App startup time / Macrobenchmark](https://developer.android.com/topic/performance/app-startup)
[^6]: [中华人民共和国个人信息保护法](https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm)
[^7]: [数据处理活动日志保护技术措施要求（司法部）](https://www.moj.gov.cn/pub/sfbgw/lfyjzj/lflfyjzj/202307/W020230724406429694386.doc)
[^8]: [GB/T 45574-2025 敏感个人信息处理安全要求](https://openstd.samr.gov.cn/bzgk/gb/newGbInfo?hcno=F9F3A2EBF49E9B4D73AD8C8912986D5A)
[^9]: [APP 收集使用个人信息最小必要评估规范 — 应用日志信息](http://www.taf.org.cn/upload/20201231/APP%E6%94%B6%E9%9B%86%E4%BD%BF%E7%94%A8%E4%B8%AA%E4%BA%BA%E4%BF%A1%E6%81%AF%E6%9C%80%E5%B0%8F%E5%BF%85%E8%A6%81%E8%AF%84%E4%BC%B0%E8%A7%84%E8%8C%83-%E5%BA%94%E7%94%A8%E6%97%A5%E5%BF%97%E4%BF%A1%E6%81%AF.pdf)
