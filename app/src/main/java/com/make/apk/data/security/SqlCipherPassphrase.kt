package com.make.apk.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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
     * 取得（必要时先生成）数据库口令。
     * 需在后台线程调用（首次生成涉及 Keystore 与文件 I/O）。
     */
    fun obtain(context: Context): ByteArray {
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
