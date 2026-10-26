package com.make.apk.data.security

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.make.apk.data.local.AppDatabase
import com.make.apk.logging.MmaLogger
import java.security.SecureRandom

/**
 * SQLCipher 数据库口令的**安全存储**。
 *
 * 设计要点：口令**绝不硬编码**、**不落明文**。
 * 首次启动用 [SecureRandom] 生成 32 字节随机口令，随后存入
 * `EncryptedSharedPreferences`——其内容由 Android Keystore 中的
 * AES-256-GCM 主密钥加密，密钥材料不出 Keystore（具备硬件-backed 时更好）。
 *
 * 后果与取舍：
 * - 卸载 App 或清除数据后，旧数据库**无法再解密**（密钥随之销毁），这是预期行为。
 * - 若设备无 Keystore（极老的模拟器），`EncryptedSharedPreferences` 会失败；
 *   真机（minSdk 24+）均支持。
 */
object SqlCipherPassphrase {

    private const val PREFS_FILE = "web2apk_db_keys"
    private const val KEY_PASSPHRASE = "sqlcipher_passphrase"
    private const val PASSPHRASE_BYTES = 32

    /**
     * 取得（必要时先生成）数据库口令。需在后台线程调用（首次生成涉及 Keystore 与文件 I/O）。
     *
     * **启动链防御（2026-10-26 rc3 装机仍闪退后的加固）**：本方法在
     * `MmaNavHost` 组合期经 `app.buildHistoryRepository` 被同步触发，
     * Keystore 故障（国产 ROM 上 `MasterKeys.getOrCreate` 偶发失败为已知案例）
     * 会直接杀死进程——表现为打开即闪退、无 MMAFile 目录、无外部可见日志。
     * 故安全路径失败时降级为**内存态随机口令**（不落盘）：口令丢失等价于
     * 历史库重置，应用保持可用，绝不因加密层故障拒绝启动。
     */
    fun obtain(context: Context, dbName: String = AppDatabase.DATABASE_NAME): ByteArray {
        runCatching { return obtainSecure(context) }
            .onFailure {
                // 降级：每次进程一个随机口令（不持久化）。此时无法解密旧库，
                // Room 首次查询会走「库损坏/打不开」路径，由 Repository 读 Flow 的
                // catch 兜底降级为空态——但旧库文件若残留会永远打不开、功能废掉。
                // 故此处主动删除无法解锁的旧库，让 Room 用新口令重建空库（自愈）。
                MmaLogger.error(
                    event = "db_passphrase_fallback",
                    message = "Keystore 口令获取失败，降级为内存态随机口令",
                    throwable = it,
                )
                runCatching {
                    val dbFile = context.getDatabasePath(dbName)
                    SQLiteDatabase.deleteDatabase(dbFile)
                }.onSuccess {
                    MmaLogger.error(
                        event = "db_passphrase_fallback_wiped",
                        message = "已删除无法用持久口令解锁的旧库，将用内存口令重建空库",
                    )
                }
            }
        return ByteArray(PASSPHRASE_BYTES).also { bytes -> SecureRandom().nextBytes(bytes) }
    }

    private fun obtainSecure(context: Context): ByteArray {
        val prefs = encryptedPreferences(context)

        val encoded = prefs.getString(KEY_PASSPHRASE, null)
        if (encoded != null) {
            val decoded = Base64.decode(encoded, Base64.DEFAULT)
            if (decoded.isNotEmpty()) return decoded
        }

        val generated = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_PASSPHRASE, Base64.encodeToString(generated, Base64.DEFAULT))
        }
        return generated
    }

    private fun encryptedPreferences(context: Context): SharedPreferences {
        // security-crypto 1.0.0 的 API：MasterKeys（复数）返回 Keystore 中的主密钥别名。
        // 注意 create(...) 的参数顺序是 (fileName, masterKeyAlias, context, ...)，
        // 与 1.1.0-alpha 之后的 create(context, fileName, masterKey, ...) 不同。
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
