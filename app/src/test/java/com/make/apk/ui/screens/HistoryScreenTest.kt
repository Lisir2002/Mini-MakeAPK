package com.make.apk.ui.screens

import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.BuildEngine
import com.make.apk.data.model.BuildInputType
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 构建历史页截图回归：有记录 / 空态。
 *
 * 空态是新用户看到的第一个"历史"界面，同样需要基线，
 * 否则很容易在迭代中退化成一片空白或错误占位。
 */
class HistoryScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val records = listOf(
        BuildRecord(
            id = 1,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_005_000L,
            inputType = BuildInputType.URL,
            sourceLabel = "example.com",
            sourceRef = "https://example.com",
            engine = BuildEngine.DEVICE_WEBVIEW,
            status = BuildStatus.SUCCESS,
            outputApkPath = "/data/local/tmp/mma/app.apk",
            durationMs = 1_200L,
        ),
        BuildRecord(
            id = 2,
            createdAt = 1_700_000_010_000L,
            updatedAt = 1_700_000_012_000L,
            inputType = BuildInputType.HTML,
            sourceLabel = "粘贴的 HTML",
            sourceRef = "<html></html>",
            engine = BuildEngine.CLOUD,
            status = BuildStatus.FAILED,
            durationMs = 2_000L,
            errorMessage = "云端构建超时",
        ),
    )

    @Test
    fun lightWithRecords() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            HistoryScreen(
                records = records,
                onOpen = {},
                onDelete = {},
                onBack = {},
            )
        }
    }

    @Test
    fun lightEmpty() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            HistoryScreen(
                records = emptyList(),
                onOpen = {},
                onDelete = {},
                onBack = {},
            )
        }
    }

    @Test
    fun darkWithRecords() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            HistoryScreen(
                records = records,
                onOpen = {},
                onDelete = {},
                onBack = {},
            )
        }
    }
}
