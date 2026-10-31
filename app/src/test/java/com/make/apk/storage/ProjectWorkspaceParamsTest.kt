package com.make.apk.storage

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 项目工作区结构参数与 manifest 版本字段（W3 ⑧）单测：
 * - `structureVersion` **读旧写新**：旧 manifest（无该键）反序列化缺省回落 1；新写入恒为 2；
 * - 参数常量与文件名安全铁律（export//backups/ 目录名不得含 `:` `/` 空格）。
 */
class ProjectWorkspaceParamsTest {

    /** 与 ProjectWorkspaceStore 内部同配置的解码器（ignoreUnknownKeys）。 */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** 一份 v1 形态（无 structureVersion 键）的最小合法 manifest。 */
    private val v1ManifestJson = """
        {
          "projectId": 1,
          "projectType": "URL",
          "apkName": "示例",
          "packageName": "com.example.demo",
          "versionScheme": "THREE_SEGMENT",
          "versionName": "1.0.0",
          "versionCode": 1,
          "iconRef": null,
          "themeColor": -65536,
          "entry": {"type": "url", "url": "https://example.com"},
          "webSettings": {},
          "shellSpec": {"specVersion": 1},
          "isolationId": "webapp_1",
          "createdAt": 1,
          "updatedAt": 1,
          "syncedAt": 1
        }
    """.trimIndent()

    @Test
    fun structureVersion_readOldManifest_defaultsToOne() {
        val manifest = json.decodeFromString(
            ProjectWorkspaceStore.Manifest.serializer(),
            v1ManifestJson,
        )
        assertEquals("旧 manifest（无 structureVersion 键）应回落 1", 1, manifest.structureVersion)
    }

    @Test
    fun structureVersion_newManifest_constantIsTwo() {
        assertEquals(
            "本轮起新写入恒为 2（writeWorkspace 引用同一常量）",
            2,
            ProjectWorkspaceParams.STRUCTURE_VERSION,
        )
        val manifest = json.decodeFromString(
            ProjectWorkspaceStore.Manifest.serializer(),
            v1ManifestJson,
        ).copy(structureVersion = ProjectWorkspaceParams.STRUCTURE_VERSION)
        val encoded = json.encodeToString(ProjectWorkspaceStore.Manifest.serializer(), manifest)
        assertTrue("新 manifest 应显式写出 structureVersion: 2", encoded.contains("\"structureVersion\":2"))
    }

    @Test
    fun structureVersion_unknownKeysAreIgnored() {
        // 前向兼容：未来版本的字段不得破坏当前读取。
        val manifest = json.decodeFromString(
            ProjectWorkspaceStore.Manifest.serializer(),
            v1ManifestJson.replace("\"syncedAt\": 1,", "\"syncedAt\": 1, \"futureField\": 123,"),
        )
        assertEquals(1, manifest.structureVersion)
    }

    @Test
    fun reservedDirNames_areFilenameSafe() {
        listOf(
            ProjectWorkspaceParams.EXPORT_DIR,
            ProjectWorkspaceParams.BACKUPS_DIR,
        ).forEach { name ->
            assertTrue("目录名不得为空", name.isNotEmpty())
            listOf(':', '/', '\\', ' ').forEach { banned ->
                assertFalse("目录名不得含 '${banned}'：$name", name.contains(banned))
            }
        }
    }

    @Test
    fun thresholds_areSane() {
        assertEquals(1L * 1024 * 1024, ProjectWorkspaceParams.MANIFEST_WARN_BYTES)
        assertEquals(8L * 1024 * 1024, ProjectWorkspaceParams.SRC_MAX_BYTES)
        assertEquals(32, ProjectWorkspaceParams.MAX_ASSET_COUNT)
        assertTrue(
            "manifest 告警阈值应小于 src 上限（前者是元数据、后者是源码本体）",
            ProjectWorkspaceParams.MANIFEST_WARN_BYTES < ProjectWorkspaceParams.SRC_MAX_BYTES,
        )
    }
}
