package com.make.apk.data.model

import com.make.apk.data.json.RuntimeJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Web 渲染配置（[WebSettings]）契约测试 —— 重点覆盖 W1 ⑪ 新增的
 * 「加载进度条色彩 / 粗细自定义」字段的**向后兼容**与往返无损。
 *
 * 核心契约：新增字段一律带默认值 → 老数据（无该字段）反序列化必须回落默认值且**不抛异常**；
 * 新数据往返后字段值保持不变。进度条颜色/粗细均存「令牌/枚举」而非色值字面量，
 * 保证预览与导出两侧从同一套设计令牌取色，且不引入硬编码色块（设计令牌铁律）。
 */
class WebSettingsContractTest {

    @Test
    fun defaults_usePrimaryColorAndMediumThickness() {
        val settings = WebSettings()
        assertEquals(ProgressColorToken.PRIMARY, settings.progressColorToken)
        assertEquals(ProgressThickness.MEDIUM, settings.progressThickness)
    }

    @Test
    fun oldJson_withoutProgressFields_fallsBackToDefaults_withoutThrowing() {
        // 老数据（无 progressColorToken / progressThickness）必须回落默认值，不炸。
        val decoded = RuntimeJson.decodeSettings("""{}""")
        assertEquals(ProgressColorToken.PRIMARY, decoded.progressColorToken)
        assertEquals(ProgressThickness.MEDIUM, decoded.progressThickness)
    }

    @Test
    fun customProgressFields_roundTripUnchanged() {
        val settings = WebSettings(
            showLoadingProgress = true,
            progressColorToken = ProgressColorToken.TERTIARY,
            progressThickness = ProgressThickness.THICK,
        )
        val decoded = RuntimeJson.decodeSettings(RuntimeJson.encodeSettings(settings))
        assertEquals(true, decoded.showLoadingProgress)
        assertEquals(ProgressColorToken.TERTIARY, decoded.progressColorToken)
        assertEquals(ProgressThickness.THICK, decoded.progressThickness)
    }

    @Test
    fun progressColorToken_serializesByName() {
        val json = RuntimeJson.encodeSettings(
            WebSettings(progressColorToken = ProgressColorToken.ON_SURFACE_VARIANT),
        )
        assertTrue("应序列化为枚举名 ON_SURFACE_VARIANT，而非色值字面量：\n$json", json.contains("ON_SURFACE_VARIANT"))
    }

    @Test
    fun progressThickness_serializesByName() {
        val json = RuntimeJson.encodeSettings(
            WebSettings(progressThickness = ProgressThickness.THIN),
        )
        assertTrue("应序列化为枚举名 THIN，而非 dp 字面量：\n$json", json.contains("THIN"))
    }

    @Test
    fun allProgressColorTokens_roundTrip() {
        ProgressColorToken.entries.forEach { token ->
            val decoded = RuntimeJson.decodeSettings(
                RuntimeJson.encodeSettings(WebSettings(progressColorToken = token)),
            )
            assertEquals(token, decoded.progressColorToken)
        }
    }

    @Test
    fun allProgressThickness_roundTrip() {
        ProgressThickness.entries.forEach { thickness ->
            val decoded = RuntimeJson.decodeSettings(
                RuntimeJson.encodeSettings(WebSettings(progressThickness = thickness)),
            )
            assertEquals(thickness, decoded.progressThickness)
        }
    }

    // ── W2 ④：外链开关（fitToApp）的默认值与往返 ─────────────────────────

    @Test
    fun fitToApp_defaultsToTrue_embeddedByDefault() {
        // 默认恒内嵌（与历史行为一致，零回归）：外链属于显式 opt-in。
        val settings = WebSettings()
        assertTrue(settings.fitToApp)
        assertEquals(
            true,
            RuntimeJson.decodeSettings(WebSettings().let(RuntimeJson::encodeSettings)).fitToApp,
        )
    }

    @Test
    fun fitToApp_oldJson_withoutField_fallsBackToTrue_withoutThrowing() {
        // 老数据（无 fitToApp 键）反序列化必须回落 true，不炸、行为不变。
        assertEquals(true, RuntimeJson.decodeSettings("""{}""").fitToApp)
    }

    @Test
    fun fitToApp_offRoundTripsUnchanged() {
        val settings = WebSettings(fitToApp = false)
        val decoded = RuntimeJson.decodeSettings(RuntimeJson.encodeSettings(settings))
        assertEquals(false, decoded.fitToApp)
        assertTrue("JSON 中必须显式落 false 键", RuntimeJson.encodeSettings(settings).contains("fitToApp"))
    }

    /**
     * 未知枚举值（未来版本新增的色档）按 kotlinx 约定**抛异常**而非静默回落——
     * 与 [com.make.apk.data.model.ShellSelectionStyle] 等壳枚举一致（注入一致性测试判定其合法性，
     * 而非在模型层兜底）。本断言锁定该契约，避免有人误以为枚举值可宽容解码。
     */
    @Test
    fun unknownProgressColorToken_throwsSerializationException() {
        val ex = runCatching {
            RuntimeJson.decodeSettings("""{"progressColorToken":"NEON_PINK"}""")
        }
        assertTrue("未知枚举值应抛 SerializationException（与壳枚举约定一致）", ex.isFailure)
        assertTrue(
            "异常应为 kotlinx SerializationException",
            ex.exceptionOrNull() is kotlinx.serialization.SerializationException,
        )
    }
}
