package com.make.apk.storage

/**
 * MMAFile 目录清单——**保留接口：新增子目录只需在此加一个枚举值**。
 *
 * 扩展步骤：
 * 1. 加枚举值（[folderName] 为磁盘实际目录名，[label] 为用户向名称）；
 * 2. [MmaStorage.ensureDirs] 遍历 `entries` 自动创建新目录，**无需改其他代码**；
 * 3. 在 `docs/ARCHITECTURE.md` §3.3 目录树登记。
 *
 * 各目录语义（Phase-2 功能落地后填充内容）：
 * - [APK]：打包产出的安装包存放处；
 * - [LOG]：运行日志（授权后 FileSink 主目录）；
 * - [ITEMS]：用户项目数据；
 * - [SETTING]：设置的用户向展示——**数据根本在程序数据库**，此处文件被删后
 *   每次启动由幂等 ensure 重建（内容镜像钩子见 `SettingMirror`）；
 * - [BACKUP]：本地备份存放与备份导入检测走这里。
 */
enum class MmaDir(val folderName: String, val label: String) {
    /** 根目录（只建自身，不作为子目录拼接前缀）。 */
    ROOT("MMAFile", "MMA 文件"),

    APK("APK", "安装包"),

    LOG("Log", "日志"),

    ITEMS("Items", "项目"),

    SETTING("Setting", "设置"),

    BACKUP("Backup", "备份"),
}
