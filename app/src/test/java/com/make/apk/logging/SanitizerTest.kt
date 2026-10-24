package com.make.apk.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 脱敏测试：**日志合规的第一道闸门**，必须有测试守住。
 *
 * 覆盖：敏感键掩码、个人信息键掩码、URL 只留 host、邮箱/长数字掩码、
 * 超长截断、异常栈清洗。
 */
class SanitizerTest {

    @Test
    fun sensitiveKeys_areFullyMasked() {
        val input = mapOf(
            "token" to "abc123",
            "access_token" to "xyz",
            "password" to "hunter2",
            "authorization" to "Bearer x",
            "cookie" to "sid=1",
            "passphrase" to "secret",
        )
        val output = Sanitizer.sanitizeFields(input)

        output.values.forEach { value -> assertEquals(Sanitizer.MASK, value) }
    }

    @Test
    fun piiKeys_areFullyMasked() {
        val output = Sanitizer.sanitizeFields(
            mapOf(
                "phone" to "13800000000",
                "user_email" to "a@b.com",
                "id_card" to "110101199001011234",
                "latitude" to 39.9,
            ),
        )
        output.values.forEach { value -> assertEquals(Sanitizer.MASK, value) }
    }

    @Test
    fun numbersAndBooleans_surviveWhenKeyIsSafe() {
        val output = Sanitizer.sanitizeFields(
            mapOf("duration_ms" to 1234L, "success" to true, "attempt" to 2),
        )
        assertEquals(1234L, output["duration_ms"])
        assertEquals(true, output["success"])
        assertEquals(2, output["attempt"])
    }

    @Test
    fun fullUrl_isReducedToSchemeAndHost() {
        assertEquals(
            "https://api.example.com/***",
            Sanitizer.redactUrl("https://api.example.com/build?token=abc&secret=1"),
        )
    }

    @Test
    fun urlValuedKey_isRedacted() {
        val output = Sanitizer.sanitizeFields(
            mapOf("source_url" to "https://example.com/path?token=zzz"),
        )
        assertEquals("https://example.com/***", output["source_url"])
    }

    @Test
    fun emailAndLongDigits_inFreeTextAreMasked() {
        val out = Sanitizer.sanitizeMessage("联系 a@b.com 或 13800000000")
        assertTrue("邮箱应被掩码", out?.contains("a@b.com") == false)
        assertTrue("手机号应被掩码", out?.contains("13800000000") == false)
        assertTrue("应含掩码标记", out?.contains(Sanitizer.MASK) == true)
    }

    @Test
    fun overlongValue_isTruncated() {
        val long = "x".repeat(5000)
        val out = Sanitizer.sanitizeMessage(long)
        assertTrue("超长值应被截断", out!!.length < 1000)
        assertTrue(out.endsWith("…(truncated)"))
    }

    @Test
    fun nullValue_passesThrough() {
        assertNull(Sanitizer.sanitizeValue("note", null))
    }

    @Test
    fun stackTrace_isSanitizedAndBounded() {
        val stack = Sanitizer.sanitizeStackTrace(RuntimeException("boom token=abc"))
        assertTrue("栈应含异常类型", stack.contains("RuntimeException"))
        assertTrue("栈内敏感值应被掩码", !stack.contains("token=abc"))
        assertTrue("栈行数应有上限", stack.lines().size <= 60)
    }
}
