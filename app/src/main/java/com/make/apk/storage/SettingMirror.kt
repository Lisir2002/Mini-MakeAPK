package com.make.apk.storage

/**
 * Phase-2 预留钩子：设置内容的用户向镜像。
 *
 * 语义（用户需求原文）：Setting 目录「只是用户向的数据展示，参数的根本都在程序数据库里，
 * 被删除后在每次应用重启的时候自动生成」。
 *
 * 当前设置系统尚未落地（data 层无设置实体），故本对象**暂不实现**：
 * - 目录本身由 [MmaStorage.ensureAll] 幂等创建（删除 → 重启重建已成立）；
 * - 内容镜像待 Phase-2 设置系统落地后实现：从数据库设置导出为用户向文件写入
 *   [MmaDir.SETTING]，导出时机挂在启动 ensure 之后。
 *
 * 接入扩展点（Phase-2 立项时的约定，⑥ 审计后细化；W3 补埋点契约）：
 * - **文件组织**：`MMAFile/Setting/settings.<节名>.json` 按节分组
 *   （如 `settings.appearance.json` / `settings.editor.json` / `settings.about.json`），
 *   一节一文件，新增配置节=新增一个文件，不破坏既有节；
 * - **接入函数**：`fun sync(context: Context)`——幂等全量重写（以数据库为唯一事实源，
 *   外部文件永远只是用户向展示，允许被用户随意改/删，下次 sync 覆盖回来）；
 * - **埋点契约（W3）**：sync 每次执行必须经 `MmaEventLogger.crud("settings_mirror", "synced", success, extra)`
 *   记一条 `settings_mirror_synced`（OPERATION 审计，fields 带 `LogFields.COUNT` = 写入文件数、
 *   `LogFields.SUCCESS`）；失败不得抛出（`MmaLogger.error("settings_mirror_sync_failed", …)`），
 *   埋点永不阻断业务、日志未安装时空操作；
 * - **调用时机**：挂 `MmaApplication` 启动链（`MmaStorage.ensureAll` 之后、首帧渲染之前，
 *   协程 Dispatchers.IO），与 ProjectWorkspaceStore 的 reconcileAll 同一启动批次；
 * - **扩展**：新增配置字段/主题包只改本对象的导出结构，目录层（MmaDir/MmaStorage）零改动。
 */
internal object SettingMirror
