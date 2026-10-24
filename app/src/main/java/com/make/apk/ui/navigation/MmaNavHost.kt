package com.make.apk.ui.navigation

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
import androidx.navigation.navArgument
import com.make.apk.data.model.BuildRecord
import com.make.apk.logging.MmaLogger
import com.make.apk.requireMmaApplication
import com.make.apk.ui.screens.BuildingScreen
import com.make.apk.ui.screens.HistoryScreen
import com.make.apk.ui.screens.HomeScreen
import com.make.apk.ui.screens.PreviewScreen
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
 */
@Composable
fun MmaNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.requireMmaApplication()
    val repository = app.buildHistoryRepository

    NavHost(
        navController = navController,
        startDestination = MmaRoute.Home.route,
        modifier = modifier,
    ) {

        // ── 首页：输入 → 登记构建 ────────────────────────────────────
        composable(MmaRoute.Home.route) {
            val vm: BuildViewModel = viewModel(
                factory = remember(app) { BuildViewModelFactory(repository) },
            )
            val pendingId by vm.pendingId.collectAsStateWithLifecycle()

            LaunchedEffect(pendingId) {
                val id = pendingId ?: return@LaunchedEffect
                vm.consumePendingId()
                navController.navigate(MmaRoute.Building.create(id)) {
                    launchSingleTop = true
                }
            }

            HomeScreen(
                onBuild = { input -> vm.startBuild(input) },
                onOpenHistory = { navController.navigate(MmaRoute.History.route) },
            )
        }

        // ── 历史 ────────────────────────────────────────────────────
        composable(MmaRoute.History.route) {
            val vm: HistoryViewModel = viewModel(
                factory = remember(app) { HistoryViewModelFactory(repository) },
            )
            val records by vm.records.collectAsStateWithLifecycle()

            HistoryScreen(
                records = records,
                onOpen = { id -> navController.navigate(MmaRoute.Preview.create(id)) },
                onDelete = vm::delete,
                onBack = { navController.popBackStack() },
            )
        }

        // ── 构建中：观察记录，终态自动进入结果页 ──────────────────────
        composable(
            route = MmaRoute.Building.route,
            arguments = recordIdArgument(),
        ) { entry ->
            val id = entry.recordIdOrNull() ?: return@composable
            val record = entry.observeRecord(repository)

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
                onCancel = { navController.popBackStack() },
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
                onAgain = { navController.popBackStack(MmaRoute.Home.route, inclusive = false) },
                onRetry = { record?.sourceRef?.let { vm.startBuild(it) } },
                onBackHome = { navController.popBackStack(MmaRoute.Home.route, inclusive = false) },
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
