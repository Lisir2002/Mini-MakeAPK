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
 * 接入时在本对象内实现 `fun sync(context: Context)`，并在 MmaApplication 启动链调用；
 * 新增配置字段/主题包同步扩展导出结构，无需改动目录层。
 */
internal object SettingMirror
