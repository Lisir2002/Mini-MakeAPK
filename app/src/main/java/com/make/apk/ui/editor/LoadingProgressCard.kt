package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ProgressColorToken
import com.make.apk.data.model.ProgressPosition
import com.make.apk.data.model.ProgressThickness
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaOption
import com.make.apk.ui.components.MmaSegmentedChoice
import com.make.apk.ui.components.MmaSwitchRow
import com.make.apk.ui.theme.Spacing

/**
 * 功能加载进度条卡（rc11 起挂在**进阶配置** Tab）。
 *
 * 开关 + 位置（顶部 / 底部）+ 色彩令牌 + 粗细三档；导出壳与预览同源消费，开启即对生成 APK 生效。
 * 原挂在高级配置，按用户裁决移至进阶配置（与壳同屏：进度条是壳的可见部件）。
 */
@Composable
fun LoadingProgressCard(
    showLoadingProgress: Boolean,
    loadingProgressPosition: ProgressPosition,
    progressColorToken: ProgressColorToken,
    progressThickness: ProgressThickness,
    onShowLoadingProgressChanged: (Boolean) -> Unit,
    onLoadingProgressPositionChanged: (ProgressPosition) -> Unit,
    onProgressColorTokenChanged: (ProgressColorToken) -> Unit,
    onProgressThicknessChanged: (ProgressThickness) -> Unit,
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
                MmaSegmentedChoice(
                    label = "进度条色彩",
                    options = PROGRESS_COLOR_OPTIONS,
                    selectedValue = progressColorToken,
                    onSelected = onProgressColorTokenChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
                MmaSegmentedChoice(
                    label = "进度条粗细",
                    options = PROGRESS_THICKNESS_OPTIONS,
                    selectedValue = progressThickness,
                    onSelected = onProgressThicknessChanged,
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

/** 加载进度条色彩选项（值即 [ProgressColorToken]，存令牌选择而非色值，跟随主题）。 */
private val PROGRESS_COLOR_OPTIONS: List<MmaOption<ProgressColorToken>> = listOf(
    MmaOption(ProgressColorToken.PRIMARY, "主色"),
    MmaOption(ProgressColorToken.ON_SURFACE_VARIANT, "次级"),
    MmaOption(ProgressColorToken.TERTIARY, "第三色"),
    MmaOption(ProgressColorToken.OUTLINE, "描边"),
)

/** 加载进度条粗细选项（值即 [ProgressThickness] 三档预设）。 */
private val PROGRESS_THICKNESS_OPTIONS: List<MmaOption<ProgressThickness>> = listOf(
    MmaOption(ProgressThickness.THIN, "细"),
    MmaOption(ProgressThickness.MEDIUM, "中"),
    MmaOption(ProgressThickness.THICK, "粗"),
)
