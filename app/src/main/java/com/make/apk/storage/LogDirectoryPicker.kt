package com.make.apk.storage

import java.io.File

/**
 * 安装期日志根目录决策（全量接线核心，纯函数）。
 *
 * 优先级：
 * 1. **公共 `MMAFile/Log`**——用户可见、文件管理器可直接浏览（已授权且实测可写才算可用）；
 * 2. **debuggable 的 App 专属外部目录**（`getExternalFilesDir/mma-logs`）——免权限、
 *    USB / adb 可取，用于「未授权但需要拿到日志」的诊断场景；
 * 3. **私有兜底** `filesDir/logs`——最后保险，绝对可写。
 *
 * 之所以抽成**纯 JVM 对象**：目录优先级是本模块唯一的启动期正确性决策，
 * 必须能在 JVM 单测里逐分支钉死，不能靠真机试。
 */
object LogDirectoryPicker {

    /**
     * 按优先级挑选日志根目录。
     *
     * @param publicLogDir 已授权且实测可写的 `MMAFile/Log`；不可用传 `null`。
     * @param externalPrivateDir debuggable 下的 `getExternalFilesDir/mma-logs`；不可用传 `null`。
     * @param privateFallback 私有兜底目录（调用方保证非空）。
     * @return 非空的日志根目录。
     */
    fun pick(publicLogDir: File?, externalPrivateDir: File?, privateFallback: File): File =
        publicLogDir ?: externalPrivateDir ?: privateFallback
}
