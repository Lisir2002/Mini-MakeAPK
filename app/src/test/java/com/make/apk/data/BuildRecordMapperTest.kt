package com.make.apk.data

import com.make.apk.data.local.Converters
import com.make.apk.data.local.entity.BuildRecordEntity
import com.make.apk.data.mapper.toDomain
import com.make.apk.data.mapper.toEntity
import com.make.apk.data.model.BuildEngine
import com.make.apk.data.model.BuildInputType
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据层纯逻辑测试（JVM，无需模拟器）。
 *
 * 覆盖：实体 ⇄ 领域模型映射的无损性、枚举类型转换器、以及未知值的稳健回退。
 */
class BuildRecordMapperTest {

    private val converters = Converters()

    private val sampleEntity = BuildRecordEntity(
        id = 7,
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_005_000,
        inputType = BuildInputType.URL,
        sourceLabel = "example.com",
        sourceRef = "https://example.com",
        engine = BuildEngine.DEVICE_WEBVIEW,
        status = BuildStatus.SUCCESS,
        appName = "Example",
        packageName = "com.example.app",
        outputApkPath = "/data/app/example.apk",
        apkSizeBytes = 1_234_567,
        durationMs = 5_000,
        errorMessage = null,
    )

    @Test
    fun entityToDomain_mapsEveryField() {
        val domain = sampleEntity.toDomain()

        assertEquals(7, domain.id)
        assertEquals(1_700_000_000_000, domain.createdAt)
        assertEquals(1_700_000_005_000, domain.updatedAt)
        assertEquals(BuildInputType.URL, domain.inputType)
        assertEquals("example.com", domain.sourceLabel)
        assertEquals("https://example.com", domain.sourceRef)
        assertEquals(BuildEngine.DEVICE_WEBVIEW, domain.engine)
        assertEquals(BuildStatus.SUCCESS, domain.status)
        assertEquals("Example", domain.appName)
        assertEquals("com.example.app", domain.packageName)
        assertEquals("/data/app/example.apk", domain.outputApkPath)
        // 注意：apkSizeBytes / durationMs 是 Long?（可空），必须写 Long 字面量，
        // 否则 Kotlin 会把 1_234_567 推断为 Int 并装箱，与 Long 比较失败。
        assertEquals(1_234_567L, domain.apkSizeBytes)
        assertEquals(5_000L, domain.durationMs)
        assertNull(domain.errorMessage)
    }

    @Test
    fun roundTrip_entityToDomainToEntity_isLossless() {
        assertEquals(sampleEntity, sampleEntity.toDomain().toEntity())
    }

    @Test
    fun domainToEntity_defaultsArePreserved() {
        val domain = BuildRecord(
            createdAt = 1L,
            updatedAt = 2L,
            inputType = BuildInputType.HTML,
            sourceLabel = "snippet",
            sourceRef = "<html></html>",
            engine = BuildEngine.CLOUD,
            status = BuildStatus.QUEUED,
        )
        val entity = domain.toEntity()

        assertEquals(0L, entity.id)
        assertEquals(BuildStatus.QUEUED, entity.status)
        assertNull(entity.outputApkPath)
        assertNull(entity.errorMessage)
    }

    @Test
    fun converters_enumRoundTrip() {
        BuildStatus.values().forEach { status ->
            assertEquals(status, converters.toBuildStatus(converters.fromBuildStatus(status)))
        }
        BuildInputType.values().forEach { type ->
            assertEquals(type, converters.toBuildInputType(converters.fromBuildInputType(type)))
        }
        BuildEngine.values().forEach { engine ->
            assertEquals(engine, converters.toBuildEngine(converters.fromBuildEngine(engine)))
        }
    }

    @Test
    fun converters_unknownTextFallsBackToSafeDefault() {
        // 旧数据或手工改库不应导致数据库打开失败
        assertEquals(BuildStatus.QUEUED, converters.toBuildStatus("NOT_A_STATUS"))
        assertEquals(BuildInputType.URL, converters.toBuildInputType("NOT_A_TYPE"))
        assertEquals(BuildEngine.DEVICE_WEBVIEW, converters.toBuildEngine("NOT_AN_ENGINE"))
        assertEquals(BuildStatus.QUEUED, converters.toBuildStatus(null))
    }

    @Test
    fun status_terminalFlagsAreCorrect() {
        assertTrue(BuildStatus.SUCCESS.isTerminal)
        assertTrue(BuildStatus.FAILED.isTerminal)
        assertTrue(BuildStatus.CANCELLED.isTerminal)
        assertFalse(BuildStatus.RUNNING.isTerminal)
        assertFalse(BuildStatus.QUEUED.isTerminal)

        assertTrue(BuildStatus.SUCCESS.let { BuildRecord(createdAt = 0, updatedAt = 0, inputType = BuildInputType.URL, sourceLabel = "", sourceRef = "", engine = BuildEngine.CLOUD, status = it) }.isFinished)
        assertFalse(BuildStatus.RUNNING.let { BuildRecord(createdAt = 0, updatedAt = 0, inputType = BuildInputType.URL, sourceLabel = "", sourceRef = "", engine = BuildEngine.CLOUD, status = it) }.isFinished)
    }
}
