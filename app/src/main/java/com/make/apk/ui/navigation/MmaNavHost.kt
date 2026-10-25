package com.make.apk.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.make.apk.data.model.BuildRecord
import com.make.apk.logging.MmaLogger
import com.make.apk.requireMmaApplication
import com.make.apk.ui.components.MmaBottomBar
import com.make.apk.ui.screens.BuildingScreen
import com.make.apk.ui.screens.DevelopingScreen
import com.make.apk.ui.screens.PreviewScreen
import com.make.apk.ui.screens.ProfileScreen
import com.make.apk.ui.screens.ProjectsScreen
import com.make.apk.ui.screens.ResultScreen
import com.make.apk.ui.viewmodel.BuildViewModel
import com.make.apk.ui.viewmodel.BuildViewModelFactory
import com.make.apk.ui.viewmodel.HistoryViewModel
import com.make.apk.ui.viewmodel.HistoryViewModelFactory

/**
 * 页面骨架的导航宿主。
 *
 * 分层约定：**路由负责取数，页面负责渲染**。
 * 所有页面都是无状态 Composable（参数进、事件出），数据源在路由里观察——
 * 这样截图测试（Paparazzi）可以直接渲染页面而不依赖数据库与导航。
 *
 * 单一数据源是加密数据库：构建记录在登记时即落库，
 * 因此"构建中"页面被杀后重建仍能读到真实状态，不会出现假进度。
 *
 * 三段式槽位布局：顶栏由各页面的 `MmaScreen` 恒显示；底栏是主页级页面
 * （项目/开发中/我的）共享的顶级导航，流程子页（构建中/结果/预览）不显示。
 */
@Composable
fun MmaNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.requireMmaApplication()
    val repository = app.buildHistoryRepository
    val context = LocalContext.current

    // 当前路由：驱动底栏选中态
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 主页级页面共享的底栏（tab 清单见 DefaultMmaTabs，支持自定义过滤/重排后注入）。
    // translucent 留 null 跟随 MmaDesign.scaffold 令牌（默认关，截图零影响）。
    val mainBottomBar: @Composable () -> Unit = {
        MmaBottomBar(
            tabs = DefaultMmaTabs,
            currentRoute = currentRoute,
            onTabSelected = { tab ->
                navController.navigate(tab.route) {
                    launchSingleTop = true
                    popUpTo(MmaRoute.Projects.route) { saveState = true }
                    restoreState = true
                }
            },
        )
    }

    NavHost(
        navController = navController,
        startDestination = MmaRoute.Projects.route,
        modifier = modifier,
    ) {

        // ── 项目（startDestination）：封装工作台（输入 + 列表）────────
        composable(MmaRoute.Projects.route) {
            val buildVm: BuildViewModel = viewModel(
                factory = remember(app) { BuildViewModelFactory(repository) },
            )
            val historyVm: HistoryViewModel = viewModel(
                factory = remember(app) { HistoryViewModelFactory(repository) },
            )
            val records by historyVm.records.collectAsStateWithLifecycle()
            val pendingId by buildVm.pendingId.collectAsStateWithLifecycle()

            LaunchedEffect(pendingId) {
                val id = pendingId ?: return@LaunchedEffect
                buildVm.consumePendingId()
                navController.navigate(MmaRoute.Building.create(id)) {
                    launchSingleTop = true
                }
            }

            ProjectsScreen(
                records = records,
                onBuild = { input -> buildVm.startBuild(input) },
                onOpen = { id -> navController.navigate(MmaRoute.Preview.create(id)) },
                onDelete = historyVm::delete,
                bottomBar = mainBottomBar,
            )
        }

        // ── 开发中（主页级：共享底栏，无返回箭头）─────────────────────
        composable(MmaRoute.Developing.route) {
            DevelopingScreen(bottomBar = mainBottomBar)
        }

        // ── 我的（主页级：共享底栏，无返回箭头）───────────────────────
        composable(MmaRoute.Profile.route) {
            ProfileScreen(
                bottomBar = mainBottomBar,
                onFeatureNotReady = { feature ->
                    // 用户点击 Phase-2 占位条目：走「操作/审计」类日志（合规保留期最长）
                    // + Toast 轻反馈（点击有回应，不误导功能已可用）
                    MmaLogger.operation(
                        "profile_feature_not_ready",
                        message = "占位条目被点击：$feature",
                    )
                    Toast.makeText(context, "$feature 还在开发中，敬请期待", Toast.LENGTH_SHORT).show()
                },
            )
        }

        // ── 构建中：观察记录，终态自动进入结果页 ──────────────────────
        composable(
            route = MmaRoute.Building.route,
            arguments = recordIdArgument(),
        ) { entry ->
            val id = entry.recordIdOrNull() ?: return@composable
            val record = entry.observeRecord(repository)
            val vm: BuildViewModel = viewModel(
                factory = remember(app) { BuildViewModelFactory(repository) },
            )

            LaunchedEffect(record?.status) {
                if (record?.status?.isTerminal == true) {
                    navController.navigate(MmaRoute.Result.create(id)) {
                        popUpTo(MmaRoute.Building.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            BuildingScreen(
                record = record,
                // 取消 = 停掉后台协程 + 落库「已取消」 + 返回；迟到写入被终态守卫拦截
                onCancel = {
                    vm.cancelBuild(id)
                    navController.popBackStack()
                },
            )
        }

        // ── 结果：成功庆祝 / 失败错误态 ──────────────────────────────
        composable(
            route = MmaRoute.Result.route,
            arguments = recordIdArgument(),
        ) { entry ->
            val id = entry.recordIdOrNull() ?: return@composable
            val record = entry.observeRecord(repository)
            val vm: BuildViewModel = viewModel(
                factory = remember(app) { BuildViewModelFactory(repository) },
            )
            val pendingId by vm.pendingId.collectAsStateWithLifecycle()

            // 重试：用原输入重新登记，登记成功后跳回构建中
            LaunchedEffect(pendingId) {
                val newId = pendingId ?: return@LaunchedEffect
                vm.consumePendingId()
                navController.navigate(MmaRoute.Building.create(newId)) {
                    popUpTo(MmaRoute.Result.route) { inclusive = true }
                    launchSingleTop = true
                }
            }

            ResultScreen(
                record = record,
                onPreview = { navController.navigate(MmaRoute.Preview.create(id)) },
                onAgain = { navController.popBackStack(MmaRoute.Projects.route, inclusive = false) },
                onRetry = { record?.sourceRef?.let { vm.startBuild(it) } },
                onBackHome = { navController.popBackStack(MmaRoute.Projects.route, inclusive = false) },
            )
        }

        // ── 预览 ────────────────────────────────────────────────────
        composable(
            route = MmaRoute.Preview.route,
            arguments = recordIdArgument(),
        ) { entry ->
            val id = entry.recordIdOrNull() ?: return@composable
            if (id <= 0L) {
                MmaLogger.error("preview_invalid_id", message = "非法的记录 id")
            }
            val record = entry.observeRecord(repository)

            PreviewScreen(
                record = record,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun recordIdArgument() = listOf(
    navArgument(MmaRoute.ARG_RECORD_ID) { type = NavType.LongType },
)

private fun androidx.navigation.NavBackStackEntry.recordIdOrNull(): Long? =
    arguments?.getLong(MmaRoute.ARG_RECORD_ID)

@Composable
private fun androidx.navigation.NavBackStackEntry.observeRecord(
    repository: com.make.apk.data.repository.BuildHistoryRepository,
): BuildRecord? {
    val id = recordIdOrNull() ?: return null
    val flow = remember(id) { repository.observeById(id) }
    return flow.collectAsStateWithLifecycle(initialValue = null).value
}
