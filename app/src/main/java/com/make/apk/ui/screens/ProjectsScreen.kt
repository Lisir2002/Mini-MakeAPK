package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.Project
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.editor.ProjectIcon
import com.make.apk.ui.editor.displayLabelOf
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Spacing

/**
 * 项目页（startDestination）——**纯项目列表**（统一内核 `Project` 模型）。
 *
 * 列表项即项目卡：整卡点击进入 `project/{id}` 编辑页，右侧「删除」**先弹二次确认**，
 * 确认后才真正移除（仓储 `delete` 会连隔离目录一起清掉，无撤销，故不能单击即删）。
 * 空态给出「点右上角 ＋ 新建」引导。
 *
 * **无状态约定**：列表 [projects] 由调用方（`MmaNavHost` 观察加密数据库）传入，
 * 事件全部出口化——截图测试可直接喂任意列表出图。
 *
 * 唯一的页面内状态是「待确认删除的项目 id」，属**交互态**而非业务态：初值恒为 null，
 * 故既有截图基线零影响；页面重建丢失它等价于用户取消，无副作用。
 *
 * 「＋」按钮：[onCreateProject] 非 null 时显示并跳新建流程页；
 * 为 null 时不显示（保持纯列表，供无新建入口的场景复用）。
 *
 * 「运行」按钮：[onRunProject] 非 null 时每个列表项显示一个「运行」图标按钮
 * （进入壳预览 `project/{id}/run`）；为 null 时不显示——
 * 既有截图基线直接渲染本函数且不传该回调，故基线画面零漂移。
 *
 * 主页级页面：顶栏仅标题（无返回），底栏为共享的顶级导航（见 MmaNavHost）。
 */
@Composable
fun ProjectsScreen(
    projects: List<Project>,
    onOpenProject: (Long) -> Unit,
    onDeleteProject: ((Long) -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    onCreateProject: (() -> Unit)? = null,
    onRunProject: ((Long) -> Unit)? = null,
) {
    // 交互态：当前待确认删除的项目 id（null = 无弹窗）。
    // 不提升到 ViewModel：删除确认是一次性交互，落库前不需要跨重建存活；
    // 留在页面里可让截图测试继续"喂列表即出图"，无需构造 ViewModel。
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    MmaScreen(
        title = "项目",
        bottomBar = bottomBar,
        modifier = modifier,
        actions = {
            if (onCreateProject != null) {
                IconButton(onClick = onCreateProject) {
                    MmaIcon(
                        iconRes = MmaIcons.Add,
                        contentDescription = "新建项目",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (projects.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = Spacing.space5,
                        vertical = Spacing.space2,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space3),
                ) {
                    items(items = projects, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            onClick = { onOpenProject(project.id) },
                            // 单击**只登记待删 id**，真正的删除等弹窗确认（见下方 DeleteConfirmDialog）
                            onDelete = onDeleteProject?.let { { pendingDeleteId = project.id } },
                            onRun = onRunProject?.let { { onRunProject(project.id) } },
                        )
                    }
                }
            }
        }
    }

    // ── 删除二次确认（破坏性操作的唯一闸门）────────────────────────────
    // 由 id 现查列表：若该项目在弹窗打开期间已被别处删掉，pending 自然解析为 null，
    // 弹窗随之消失——不会出现"确认删除一个已不存在的 id"的空转。
    val pendingProject = pendingDeleteId?.let { id -> projects.firstOrNull { it.id == id } }
    if (pendingProject != null) {
        DeleteConfirmDialog(
            apkName = pendingProject.apkName,
            onConfirm = {
                pendingDeleteId = null
                onDeleteProject?.invoke(pendingProject.id)
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

/**
 * 删除项目二次确认弹窗（§10.2 危险操作规格：主操作**保留文字** + `error` 色）。
 *
 * 与权限弹窗同构（都走 [BasicAlertDialog] + 纯内容函数），好处是截图测试可以直接
 * 渲染 [DeleteConfirmContent]——Paparazzi 无真实 window，Dialog 外壳渲染不出来。
 *
 * @param apkName 待删除项目名（提示里点名，降低误删近似项的概率）。
 * @param onConfirm 确认：由调用方执行真正的删除。
 * @param onDismiss 取消 / 点击弹窗外 / 返回键：只关闭，**不删**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmDialog(
    apkName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // 平台默认宽度会挤压我们自己的圆角容器；关掉后由内容自身 padding 控制
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DeleteConfirmContent(
            apkName = apkName,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * 删除确认弹窗内容（无 Dialog 外壳的纯内容 composable）。
 *
 * 与 [DeleteConfirmDialog] 同源：截图回归直接钉本函数，像素仅差一层 scrim 背景。
 */
@Composable
internal fun DeleteConfirmContent(
    apkName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = Spacing.space5),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space6),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Text(
                text = DELETE_CONFIRM_TITLE,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "「$apkName」将被删除，项目数据会一并清除，且无法恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space2, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = DELETE_DISMISS_TEXT, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onConfirm) {
                    Text(
                        text = DELETE_CONFIRM_TEXT,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 空态：图标 + 「还没有项目，点右上角 ＋ 新建一个」引导。 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            MmaIcon(
                iconRes = MmaIcons.Folder,
                contentDescription = null,
                size = EMPTY_STATE_ICON_SIZE,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "还没有项目，点右上角 ＋ 新建一个",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单个项目行：图标 + 名称/副行 + 运行/删除。 */
@Composable
private fun ProjectRow(
    project: Project,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRun: (() -> Unit)? = null,
) {
    CuteCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectIcon(
                apkName = project.apkName,
                themeColor = project.themeColor,
                iconRef = project.iconRef,
                size = ROW_ICON_SIZE,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.space1),
            ) {
                Text(
                    project.apkName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    projectSubtitle(project),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRun != null) {
                IconButton(onClick = onRun) {
                    MmaIcon(
                        iconRes = MmaIcons.Play,
                        contentDescription = "运行 ${project.apkName}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}

/** 列表副行：「类型 · 版本号 (内部版本号)」；类型标签与准星卡片共用 [displayLabelOf]。 */
private fun projectSubtitle(project: Project): String =
    "${displayLabelOf(project.projectType)} · ${project.versionName} (${project.versionCode})"

/** 空态图标尺寸。 */
private val EMPTY_STATE_ICON_SIZE: Dp = 36.dp

/** 列表行图标尺寸。 */
private val ROW_ICON_SIZE: Dp = 40.dp

/** 删除确认弹窗标题。 */
private const val DELETE_CONFIRM_TITLE: String = "删除项目"

/** 删除确认弹窗·确认（执行删除，走 `error` 色文案）。 */
private const val DELETE_CONFIRM_TEXT: String = "确认删除"

/** 删除确认弹窗·取消（关闭，不删）。 */
private const val DELETE_DISMISS_TEXT: String = "取消"
