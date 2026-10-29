package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ProgressPosition
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaOption
import com.make.apk.ui.components.MmaSegmentedChoice
import com.make.apk.ui.components.MmaSwitchRow
import com.make.apk.ui.theme.Spacing

/**
 * 功能加载进度条卡（rc11 起挂在**进阶配置** Tab）。
 *
 * 开关 + 位置（顶部 / 底部）；导出壳与预览同源消费，开启即对生成 APK 生效。
 * 原挂在高级配置，按用户裁决移至进阶配置（与壳同屏：进度条是壳的可见部件）。
 */
@Composable
fun LoadingProgressCard(
    showLoadingProgress: Boolean,
    loadingProgressPosition: ProgressPosition,
    onShowLoadingProgressChanged: (Boolean) -> Unit,
    onLoadingProgressPositionChanged: (ProgressPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Text(
                text = "功能加载进度条",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MmaSwitchRow(
                label = "显示加载进度条",
                description = "网页加载时显示进度，加载完成自动隐藏",
                checked = showLoadingProgress,
                onCheckedChange = onShowLoadingProgressChanged,
            )
            if (showLoadingProgress) {
                MmaSegmentedChoice(
                    label = "进度条位置",
                    options = PROGRESS_POSITION_OPTIONS,
                    selectedValue = loadingProgressPosition,
                    onSelected = onLoadingProgressPositionChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 加载进度条位置选项（值即 [ProgressPosition]）。 */
private val PROGRESS_POSITION_OPTIONS: List<MmaOption<ProgressPosition>> = listOf(
    MmaOption(ProgressPosition.TOP, "顶部"),
    MmaOption(ProgressPosition.BOTTOM, "底部"),
)
