package com.make.apk.data.local

import android.util.Log

/**
 * SQLCipher 原生库加载器。
 *
 * 新版 `net.zetetic:sqlcipher-android` 不再自动加载 `libsqlcipher.so`，
 * 必须在打开加密库前显式 `System.loadLibrary("sqlcipher")`，否则
 * `SQLiteConnection.nativeOpen` 会抛 `UnsatisfiedLinkError`。
 * 幂等 + try-catch：测试环境（无原生库）或极端缺失时记录但不崩溃，
 * 真正的失败由 Repository 的 catch 兜底降级。
 */
object SqlCipherInitializer {
    @Volatile
    private var loaded = false

    fun initOnce() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                System.loadLibrary("sqlcipher")
                loaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e("MMA-SQLCipher", "loadLibrary(\"sqlcipher\") failed", e)
            }
        }
    }
}
