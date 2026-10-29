package com.make.apk.ui.navigation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
import com.make.apk.requireMmaApplication
import com.make.apk.storage.IconStore
import kotlinx.coroutines.launch
import com.make.apk.ui.components.MmaBottomBar
import com.make.apk.ui.components.MmaNoticeCategory
import com.make.apk.ui.components.MmaNoticeData
import com.make.apk.ui.components.MmaNoticeHost
import com.make.apk.ui.components.MmaNoticeHostState
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.screens.AddProjectScreen
import com.make.apk.ui.screens.DevelopingScreen
import com.make.apk.ui.screens.LogViewerScreen
import com.make.apk.ui.screens.ProfileScreen
import com.make.apk.ui.screens.ProjectEditScreen
import com.make.apk.ui.screens.ProjectFileEditorScreen
import com.make.apk.ui.screens.ProjectsScreen
import com.make.apk.ui.screens.ShellPreviewScreen
import com.make.apk.ui.viewmodel.AddProjectEvent
import com.make.apk.ui.viewmodel.AddProjectViewModel
import com.make.apk.ui.viewmodel.AddProjectViewModelFactory
import com.make.apk.ui.viewmodel.ProjectEditEvent
import com.make.apk.ui.viewmodel.ProjectEditViewModel
import com.make.apk.ui.viewmodel.ProjectEditViewModelFactory
import com.make.apk.ui.viewmodel.ProjectFileEditorViewModel
import com.make.apk.ui.viewmodel.ProjectFileEditorViewModelFactory
import com.make.apk.ui.viewmodel.ProjectListViewModel
import com.make.apk.ui.viewmodel.ProjectListViewModelFactory
import com.make.apk.ui.viewmodel.ShellPreviewEvent
import com.make.apk.ui.viewmodel.ShellPreviewViewModel
import com.make.apk.ui.viewmodel.ShellPreviewViewModelFactory

/**
 * 页面骨架的导航宿主。
 *
 * 分层约定：**路由负责取数，页面负责渲染**。
 * 所有页面都是无状态 Composable（参数进、事件出），数据源在路由里观察——
 * 这样截图测试（Paparazzi）可以直接渲染页面而不依赖数据库与导航。
 *
 * 三段式槽位布局：顶栏由各页面的 `MmaScreen` 恒显示；底栏是主页级页面
 * （项目/开发中/我的）共享的顶级导航，流程子页（新建/编辑）不显示。
 */
@Composable
fun MmaNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.requireMmaApplication()
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

        // ── 项目（startDestination）：项目列表（统一内核 Project 模型）──
        composable(MmaRoute.Projects.route) { entry ->
            val listVm: ProjectListViewModel = viewModel(
                factory = remember(app) { ProjectListViewModelFactory(app.projectRepository) },
            )
            val projects by listVm.projects.collectAsStateWithLifecycle()

            // 新建成功回传的 id：读一次即 remove（避免二次进入重复读到、重复消费）
            val createdProjectId by entry.savedStateHandle
                .getStateFlow<Long?>(MmaRoute.RESULT_CREATED_PROJECT_ID, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(createdProjectId) {
                val id = createdProjectId ?: return@LaunchedEffect
                entry.savedStateHandle.remove<Long?>(MmaRoute.RESULT_CREATED_PROJECT_ID)
                MmaLogger.operation(
                    event = "project_created_consumed",
                    message = "新建项目已回传列表",
                    fields = mapOf(LogFields.SUCCESS to true, "project_id" to id),
                )
            }

            ProjectsScreen(
                projects = projects,
                // 整卡点击进入项目编辑页（阶段 4 落地；路由已注册）
                onOpenProject = { id -> navController.navigate(MmaRoute.Project.create(id)) },
                onDeleteProject = listVm::delete,
                bottomBar = mainBottomBar,
                // 「＋」进入新建流程页。
                onCreateProject = {
                    navController.navigate(MmaRoute.Add.route) {
                        launchSingleTop = true
                    }
                },
                // 「运行」进入壳预览（阶段 6.2）。
                onRunProject = { id -> navController.navigate(MmaRoute.ProjectRun.create(id)) },
                // 下拉刷新（rc11）：重读项目流。
                onRefresh = { listVm.refresh() },
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
                onOpenLogs = { navController.navigate(MmaRoute.Logs.route) },
            )
        }

        // ── 运行日志（流程子页：无底栏，rc9 自研分级展示）────────────
        composable(MmaRoute.Logs.route) {
            val vm: com.make.apk.ui.viewmodel.LogViewerViewModel = viewModel(
                factory = remember(app) {
                    com.make.apk.ui.viewmodel.LogViewerViewModel.factory(app.logRepository)
                },
            )
            val logsState by vm.uiState.collectAsStateWithLifecycle()
            // 打开日志页是一次用户操作：只在首次进入时记一次（不能随重组重复记）。
            LaunchedEffect(Unit) {
                MmaLogger.operation("logs_opened", message = "查看运行日志")
            }
            LogViewerScreen(
                uiState = logsState,
                onBack = { navController.popBackStack() },
                onToggleLevel = vm::toggleLevel,
                onToggleGroup = vm::toggleGroup,
                onClearFilters = vm::clearFilters,
            )
        }

        // ── 新建项目（流程子页：无底栏）──────────────────────────────
        composable(MmaRoute.Add.route) {
            val vm: AddProjectViewModel = viewModel(
                factory = remember(app) { AddProjectViewModelFactory(app.projectRepository) },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            val iconStore = remember(app) { IconStore(app) }
            val scope = rememberCoroutineScope()
            // 保存通知宿主（顶栏居中气泡，3s 自动消失 / 点击即隐藏；§优化：保存不再强制退出页面）。
            val addNoticeHost = remember { MmaNoticeHostState() }

            // 图标选择：系统 Photo Picker（无需存储权限）。rc9：居中裁方 + 主题色烘焙
            // （透明源图不再在启动器渲染成黑块），原始副本留档供换底色重合成。
            val pickIconLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult // 用户取消，静默即可
                scope.launch {
                    val path = iconStore.saveFromPicker(uri, state.themeColor)
                    if (path != null) {
                        vm.onIconChanged(path)
                    } else {
                        MmaLogger.error("icon_pick_failed", message = "图标拷贝失败")
                        Toast.makeText(context, "图标读取失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            LaunchedEffect(Unit) {
                vm.events.collect { event ->
                    when (event) {
                        // 保存成功：顶栏居中气泡提示，**留驻编辑页**（用户手动返回）；
                        // 不再 popBackStack 强制离开（§优化：简化操作步骤）。
                        is AddProjectEvent.Saved -> addNoticeHost.show(
                            MmaNoticeData(MmaNoticeCategory.Success, "已保存项目"),
                        )
                        // 失败：字段级原因已由 errors 落在输入框下方；这里补一次轻提示
                        // （包名占用等非字段异常也要让用户看见"确实点了但没保存成功"）。
                        is AddProjectEvent.SaveFailed -> addNoticeHost.show(
                            MmaNoticeData(MmaNoticeCategory.Error, event.message),
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AddProjectScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onProjectTypeSelected = vm::onProjectTypeSelected,
                    onUrlChanged = vm::onUrlChanged,
                    onEntryFileChanged = vm::onEntryFileChanged,
                    onApkNameChanged = vm::onApkNameChanged,
                    onPackageNameChanged = vm::onPackageNameChanged,
                    onVersionSchemeChanged = vm::onVersionSchemeChanged,
                    onVersionNameChanged = vm::onVersionNameChanged,
                    onVersionCodeChanged = vm::onVersionCodeChanged,
                    onApplySuggestionApkName = vm::applySuggestionApkName,
                    onApplySuggestionPackageName = vm::applySuggestionPackageName,
                    onThemeColorChanged = vm::onThemeColorChanged,
                    onPickIcon = {
                        pickIconLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onSave = vm::save,
                    onPreview = { id -> navController.navigate(MmaRoute.ProjectRun.create(id)) },
                )
                MmaNoticeHost(
                    hostState = addNoticeHost,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = LocalMmaDesign.current.chrome.topBarHeight + Spacing.space2),
                )
            }
        }

        // ── 项目编辑（流程子页：无底栏）────────────────────────────────
        composable(
            route = MmaRoute.Project.route,
            arguments = listOf(
                navArgument(MmaRoute.ARG_PROJECT_ID) { type = NavType.LongType },
            ),
        ) { entry ->
            val projectId = entry.arguments?.getLong(MmaRoute.ARG_PROJECT_ID) ?: 0L
            if (projectId <= 0L) {
                // 非法 id：直接退回列表页，避免用户卡在空白占位页上。
                // popBackStack 放在 LaunchedEffect 里，避免在组合过程中改动导航栈。
                MmaLogger.error("project_invalid_id", message = "非法的项目 id")
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val vm: ProjectEditViewModel = viewModel(
                factory = remember(app, projectId) {
                    ProjectEditViewModelFactory(projectId, app.projectRepository, app.exportRunRepository)
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            // 保存通知宿主（顶栏居中气泡；§优化：保存成功留驻编辑页，不再强制离开）。
            val editNoticeHost = remember { MmaNoticeHostState() }
            // 图标选择：系统 Photo Picker（与新建页同源，无需存储权限）。
            val iconStore = remember(app) { IconStore(app) }
            val iconScope = rememberCoroutineScope()
            val pickIconLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                iconScope.launch {
                    val path = iconStore.saveFromPicker(uri, state.themeColor)
                    if (path != null) {
                        vm.onIconChanged(path)
                    } else {
                        MmaLogger.error("icon_pick_failed", message = "图标拷贝失败")
                        Toast.makeText(context, "图标读取失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            LaunchedEffect(Unit) {
                vm.events.collect { event ->
                    when (event) {
                        // 保存成功：顶栏居中气泡提示，**留驻编辑页**（用户手动返回）。
                        is ProjectEditEvent.Saved -> editNoticeHost.show(
                            MmaNoticeData(MmaNoticeCategory.Success, "已保存"),
                        )
                        // 保存失败：气泡提示（替代原 Snackbar）。
                        is ProjectEditEvent.SaveFailed -> editNoticeHost.show(
                            MmaNoticeData(MmaNoticeCategory.Error, event.message),
                        )
                        // 删除成功：离开编辑页回到列表。
                        is ProjectEditEvent.Deleted -> {
                            navController.popBackStack()
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                ProjectEditScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onApkNameChanged = vm::onApkNameChanged,
                    onPackageNameChanged = vm::onPackageNameChanged,
                    onVersionSchemeChanged = vm::onVersionSchemeChanged,
                    onVersionNameChanged = vm::onVersionNameChanged,
                    onVersionCodeChanged = vm::onVersionCodeChanged,
                    onEntryUrlChanged = vm::onEntryUrlChanged,
                    onEntryHtmlChanged = vm::onEntryHtmlChanged,
                    onEntryFileChanged = vm::onEntryFileChanged,
                    onThemeColorChanged = vm::onThemeColorChanged,
                    onPickIcon = {
                        pickIconLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onShellSpecChanged = vm::onShellSpecChanged,
                    onJavascriptEnabledChanged = vm::onJavascriptEnabledChanged,
                    onDomStorageEnabledChanged = vm::onDomStorageEnabledChanged,
                    onFitToAppChanged = vm::onFitToAppChanged,
                    onThirdPartyCookiesChanged = vm::onThirdPartyCookiesChanged,
                    onCacheModeChanged = vm::onCacheModeChanged,
                    onUserAgentChanged = vm::onUserAgentChanged,
                    onShowLoadingProgressChanged = vm::onShowLoadingProgressChanged,
                    onLoadingProgressPositionChanged = vm::onLoadingProgressPositionChanged,
                    // 文件目录树（rc11）：打开 / 新增 / 删除附加文件。
                    onOpenFile = { name ->
                        navController.navigate(MmaRoute.ProjectFileEditor.create(projectId, name))
                    },
                    onFileAdded = vm::onProjectFileAdded,
                    onFileDeleted = vm::onProjectFileDeleted,
                    onToggleCollapse = vm::onCollapsedChanged,
                    onTabSelected = vm::onTabSelected,
                    onDelete = vm::delete,
                    onSave = vm::save,
                    // 「预览」进入壳预览（阶段 6.2）。
                    onPreview = { navController.navigate(MmaRoute.ProjectRun.create(projectId)) },
                )
                MmaNoticeHost(
                    hostState = editNoticeHost,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = LocalMmaDesign.current.chrome.topBarHeight + Spacing.space2),
                )
            }
        }

        // ── 壳预览（流程子页：全屏壳，无底栏无表单）────────────────────
        composable(
            route = MmaRoute.ProjectRun.route,
            arguments = listOf(
                navArgument(MmaRoute.ARG_PROJECT_ID) { type = NavType.LongType },
            ),
        ) { entry ->
            val projectId = entry.arguments?.getLong(MmaRoute.ARG_PROJECT_ID) ?: 0L
            if (projectId <= 0L) {
                MmaLogger.error("shell_preview_invalid_id", message = "非法的项目 id")
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val vm: ShellPreviewViewModel = viewModel(
                factory = remember(app, projectId) {
                    ShellPreviewViewModelFactory(projectId, app.projectRepository)
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                vm.events.collect { event ->
                    when (event) {
                        is ShellPreviewEvent.Message ->
                            snackbarHostState.showSnackbar(event.text)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                ShellPreviewScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onTopTabSelected = vm::onTopTabSelected,
                    onBottomItemSelected = vm::onBottomItemSelected,
                    onDrawerItemSelected = vm::onDrawerItemSelected,
                    onDrawerOpenChanged = vm::onDrawerOpenChanged,
                    onShellAction = vm::onShellAction,
                    onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // ── 文件编辑器 / 查看器（流程子页：全屏无底栏，rc11）────────────
        composable(
            route = MmaRoute.ProjectFileEditor.route,
            arguments = listOf(
                navArgument(MmaRoute.ARG_PROJECT_ID) { type = NavType.LongType },
                navArgument(MmaRoute.ARG_FILE_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val projectId = entry.arguments?.getLong(MmaRoute.ARG_PROJECT_ID) ?: 0L
            val fileName = entry.arguments?.getString(MmaRoute.ARG_FILE_NAME).orEmpty()
            if (projectId <= 0L || fileName.isEmpty()) {
                MmaLogger.error("file_editor_invalid_args", message = "非法的文件编辑参数")
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val vm: ProjectFileEditorViewModel = viewModel(
                factory = remember(app, projectId, fileName) {
                    ProjectFileEditorViewModelFactory(projectId, fileName, app.projectRepository)
                },
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                vm.saved.collect {
                    // 保存成功：返回项目代码页（文件列表随库流自动刷新）。
                    navController.popBackStack()
                }
            }
            ProjectFileEditorScreen(
                fileName = fileName,
                content = state.content,
                loading = state.loading,
                notFound = state.notFound,
                onBack = { navController.popBackStack() },
                onSave = vm::save,
            )
        }

    }
}

