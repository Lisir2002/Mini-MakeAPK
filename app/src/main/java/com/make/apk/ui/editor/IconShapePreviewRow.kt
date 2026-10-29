package com.make.apk.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.storage.IconComposer
import com.make.apk.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动器形状预览行（rc9 应用图标自定义，联网调研 2026-10 自适应图标规范）。
 *
 * Android 启动器会用自家形状（圆形 / 方圆 / 圆角方…）套遮罩，**同一图标在不同
 * 启动器长得不一样**。本组件把当前图标（或主题色占位）按三种主流形状各渲染一枚，
 * 用户在保存前就能看到"我这款图标在圆形启动器上会不会切脚"。
 *
 * **只预览不烘焙**：形状由启动器决定，应用侧烘焙形状会在其他形状启动器上出现
 * 双裁边（见 [IconComposer] KDoc）。数据源与 [ProjectIcon] 同一解码路径，预览即所得。
 *
 * @param apkName 应用名（无图标时的占位首字母，与 [ProjectIcon] 一致）。
 * @param themeColor ARGB 主题色（占位底色）。
 * @param iconRef 图标文件路径；null = 还没选图，预览主题色占位。
 */
@Composable
fun IconShapePreviewRow(
    apkName: String,
    themeColor: Int,
    iconRef: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = iconRef) {
        value = withContext(Dispatchers.IO) { ProjectIconDecoder.decode(iconRef) }
    }
    val image = bitmap
    val placeholder = iconPlaceholderLabelOf(apkName)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "启动器形状预览：圆形、方圆、圆角方形三种遮罩下的图标效果"
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.space1),
    ) {
        Text(
            text = "启动器形状预览",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapePreview(image, themeColor, placeholder, CircleShape, "圆形")
            ShapePreview(image, themeColor, placeholder, MaterialTheme.shapes.extraLarge, "方圆")
            ShapePreview(image, themeColor, placeholder, MaterialTheme.shapes.large, "圆角方")
        }
        Text(
            text = "实际显示形状由手机启动器决定，预览供参考",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShapePreview(
    image: ImageBitmap?,
    themeColor: Int,
    placeholder: String,
    shape: Shape,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space1),
    ) {
        Box(
            modifier = Modifier
                .size(SHAPE_PREVIEW_SIZE)
                .clip(shape)
                .background(Color(themeColor)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.size(SHAPE_PREVIEW_SIZE),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White, // token-gate: allow: 占位字在任意主题色上需保证对比（WCAG 2.2 AA）
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 形状预览边长（列表行图标与身份区图标的中位档）。 */
private val SHAPE_PREVIEW_SIZE: Dp = 48.dp
