# 故障库索引（Incident Index）

> 本文件**仅为索引**：每条故障的详细复盘请点开各自独立的文档（**一问题一文档**，详见 `../team/bug-handling-sop.md` §0）。
> 新增故障时：在 `docs/incidents/` 下新建 `INC-<YYYYMMDD>-<slug>.md`，并在此表追加一行。

| 事故编号 | 标题 | 严重级别 | 状态 | 文档 |
|----------|------|---------|------|------|
| INC-20261028-SQLCIPHER-ULE | Android 发布包崩溃：SQLCipher 原生库未加载导致 `UnsatisfiedLinkError`（影响项目保存） | P1 | In Remediation（rc6 待发） | [INC-20261028-sqlcipher-unsatisfiedlink.md](./INC-20261028-sqlcipher-unsatisfiedlink.md) |
