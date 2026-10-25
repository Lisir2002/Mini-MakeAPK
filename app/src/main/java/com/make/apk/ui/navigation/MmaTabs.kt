package com.make.apk.ui.navigation

import com.make.apk.R

/**
 * 底栏 tab 描述（顶级导航的一项，见 [MmaBottomBar]）。
 *
 * 每个 tab 对应一个「主页」级路由（无参路由，精确匹配当前路由判定选中态）。
 */
data class MmaTab(
    val route: String,
    val label: String,
    val iconRes: Int,
)

/**
 * 默认主页级 tab 清单——底栏槽位内容（三栏目：项目 / 开发中 / 我的）。
 *
 * 可配置性：增删/重排本清单即改底栏，[com.make.apk.ui.components.MmaBottomBar] 不感知来源；
 * 后续用户级自定义（哪些 tab 作主页、何种顺序）可经 SharedPreferences 过滤/重排后
 * 注入同一列表，接口已解耦。
 */
val DefaultMmaTabs: List<MmaTab> = listOf(
    MmaTab(route = MmaRoute.Projects.route, label = "项目", iconRes = R.drawable.ic_projects),
    MmaTab(route = MmaRoute.Developing.route, label = "开发中", iconRes = R.drawable.ic_developing),
    MmaTab(route = MmaRoute.Profile.route, label = "我的", iconRes = R.drawable.ic_person),
)
