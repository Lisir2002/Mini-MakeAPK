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
 * 结果页截图回归：成功庆祝态 + 失败错误态。
 *
 * 失败态是**最容易被忽略却最影响留存**的界面，必须有基线守住：
 * 既要有人话原因，也要有明确出口（重试 / 返回首页）。
 */
class ResultScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val succeeded = BuildRecord(
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
    )

    private val failed = BuildRecord(
        id = 2,
        createdAt = 1_700_000_010_000L,
        updatedAt = 1_700_000_012_000L,
        inputType = BuildInputType.HTML,
        sourceLabel = "粘贴的 HTML",
        sourceRef = "<html></html>",
        engine = BuildEngine.DEVICE_WEBVIEW,
        status = BuildStatus.FAILED,
        durationMs = 2_000L,
        errorMessage = "网页资源加载失败，请检查网址是否可访问",
    )

    @Test
    fun lightSuccess() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            ResultScreen(
                record = succeeded,
                onPreview = {},
                onAgain = {},
                onRetry = {},
                onBackHome = {},
            )
        }
    }

    @Test
    fun lightFailure() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            ResultScreen(
                record = failed,
                onPreview = {},
                onAgain = {},
                onRetry = {},
                onBackHome = {},
            )
        }
    }

    @Test
    fun darkFailure() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            ResultScreen(
                record = failed,
                onPreview = {},
                onAgain = {},
                onRetry = {},
                onBackHome = {},
            )
        }
    }
}
